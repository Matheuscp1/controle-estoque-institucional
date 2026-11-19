package br.com.estoque.imports.infrastructure;

import br.com.estoque.imports.api.*;
import br.com.estoque.imports.application.BatchProcessor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(classes=ImportQueueIntegrationTest.Context.class,properties={
    "imports.enabled=true","imports.initial-delay-ms=3600000","spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.properties.hibernate.jdbc.batch_size=50","spring.rabbitmq.publisher-confirm-type=correlated",
    "spring.rabbitmq.publisher-returns=true","spring.rabbitmq.listener.simple.prefetch=2"})
@AutoConfigureMockMvc
class ImportQueueIntegrationTest {
    @TestConfiguration @EnableAutoConfiguration
    @Import({ImportConfiguration.class,ImportController.class,ImportErrors.class,ImportReadinessFilter.class})
    static class Context {}
    @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:15-alpine").withUrlParam("reWriteBatchedInserts","true");
    @Container static RabbitMQContainer rabbit=new RabbitMQContainer("rabbitmq:4-management");
    static final String DIRECTORY;
    static {try {DIRECTORY=Files.createTempDirectory("estoque-import-test-").toString();}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",pg::getJdbcUrl);r.add("spring.datasource.username",pg::getUsername);
        r.add("spring.datasource.password",pg::getPassword);r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        r.add("spring.rabbitmq.host",rabbit::getHost);r.add("spring.rabbitmq.port",rabbit::getAmqpPort);
        r.add("spring.rabbitmq.username",rabbit::getAdminUsername);r.add("spring.rabbitmq.password",rabbit::getAdminPassword);
        r.add("imports.directory",()->DIRECTORY);
    }
    @Autowired ImportStore store;
    @Autowired ArchiveStorage archives;
    @Autowired ArchivePreparer preparer;
    @Autowired RabbitOutboxPublisher publisher;
    @Autowired ImportFinalizer finalizer;
    @Autowired IncrementalImportPublisher incremental;
    @Autowired IncrementalPublicationScheduler incrementalScheduler;
    @Autowired ImportReview review;
    @Autowired BatchProcessor processor;
    @Autowired JdbcTemplate db;
    @Autowired RabbitTemplate messages;
    @Autowired MockMvc mvc;
    @Autowired br.com.estoque.orders.application.OrderService orders;

    @BeforeEach void reset() {
        db.execute("TRUNCATE pedido_itens,pedidos,itens,orgaos,import_issues,import_rows,import_batches,import_jobs CASCADE");
        db.update("UPDATE import_control SET ready=false,active_job=null WHERE id=1");
    }

    @Test void filaImportaHistoricoSemNovoDebitoEReenviosNaoDuplicam() throws Exception {
        byte[] zip=archive(false,false,false);
        String id=start(zip);preparer.prepareNext();publisher.publish();
        await().atMost(Duration.ofSeconds(30)).until(()->store.status(id).completedBatches()==4);
        publishAll(id);
        finalizer.finish(id);
        assertThat(store.status(id).status()).isEqualTo("COMPLETED");
        assertThat(store.status(id).duplicateHeaders()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT saldo FROM itens WHERE codigo='A'",java.math.BigDecimal.class)).isEqualByComparingTo("7");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedidos",Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedido_itens",Integer.class)).isEqualTo(2);
        assertThat(start(zip)).isEqualTo(id);
        String batch=db.queryForObject("SELECT id FROM import_batches WHERE source='itens.csv'",String.class);
        messages.convertAndSend("imports.exchange","batch",batch);
        processor.process(batch); // Mesmo contrato da reentrega, depois do commit.
        assertThat(db.queryForObject("SELECT COUNT(*) FROM import_rows WHERE source='itens.csv'",Integer.class)).isEqualTo(1);
        assertThat(store.ready()).isTrue();
        var replay=orders.create(new br.com.estoque.orders.domain.NewOrder("P1","O","Maria",
                br.com.estoque.orders.domain.OrderSource.PORTAL,
                List.of(new br.com.estoque.orders.domain.OrderLine("A",new java.math.BigDecimal("3")))));
        assertThat(db.queryForObject("SELECT saldo FROM itens WHERE codigo='A'",java.math.BigDecimal.class)).isEqualByComparingTo("7");
        orders.cancel(replay.id(),"Cancelamento após migração");
        assertThat(db.queryForObject("SELECT saldo FROM itens WHERE codigo='A'",java.math.BigDecimal.class)).isEqualByComparingTo("10");
    }

    @Test void saldoIncoerenteBloqueiaUsoMesmoComInsertsProvisorios() throws Exception {
        String id=start(archive(true,false,false));stageDirectly(id);finalizer.finish(id);
        assertThat(store.status(id).status()).isEqualTo("COMPLETED_WITH_ISSUES");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM itens",Integer.class)).isEqualTo(1);
        assertThat(store.ready()).isTrue();
        assertThat(store.issues(id,0,100)).anySatisfy(issue->{
            assertThat(issue.get("motivo").toString()).startsWith("BALANCE_MISMATCH");
            assertThat(issue.get("linha")).isNotNull();
            assertThat(issue.get("original")).isNotNull();
        });
        mvc.perform(get("/api/imports/"+id)).andExpect(status().isOk());
    }

    @Test void revisaoCorrigeSaldoSemApagarOrigemOuMovimentosDeItensLiberados() throws Exception {
        String id=start(archive(true,false,true));stageDirectly(id);finalizer.finish(id);
        assertThat(store.status(id).status()).isEqualTo("COMPLETED_WITH_ISSUES");
        var requestA=new br.com.estoque.orders.domain.NewOrder("ONLINE-A","O","Maria",br.com.estoque.orders.domain.OrderSource.PORTAL,
                List.of(new br.com.estoque.orders.domain.OrderLine("A",new java.math.BigDecimal("1"))));
        assertThatThrownBy(()->orders.create(requestA)).isInstanceOf(br.com.estoque.orders.domain.OrderErrors.Conflict.class);
        orders.create(new br.com.estoque.orders.domain.NewOrder("ONLINE-B","O","Maria",br.com.estoque.orders.domain.OrderSource.PORTAL,
                List.of(new br.com.estoque.orders.domain.OrderLine("B",new java.math.BigDecimal("1")))));
        String original=db.queryForObject("SELECT raw_payload FROM import_rows WHERE source='itens.csv' AND record_key='A'",String.class);
        var fields=new com.fasterxml.jackson.databind.ObjectMapper().readValue(original,Map.class);
        fields.put("saldo_atual","7");
        String correction=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("fields",fields,"revision",0,"justification","Conferido no histórico"));
        mvc.perform(put("/api/imports/"+id+"/rows/itens.csv/2").contentType("application/json").content(correction)).andExpect(status().isOk());
        mvc.perform(put("/api/imports/"+id+"/rows/itens.csv/2").contentType("application/json").content(correction)).andExpect(status().isConflict());
        mvc.perform(post("/api/imports/"+id+"/reprocess")).andExpect(status().isAccepted());
        db.execute("ALTER TABLE itens ADD CONSTRAINT test_review_rollback CHECK(saldo<>7)");
        try {
            assertThatThrownBy(()->finalizer.finish(id)).isInstanceOf(RuntimeException.class);
            assertThat(store.status(id).status()).isEqualTo("REVIEW_PENDING");
            assertThat(store.status(id).issues()).isPositive();
            assertThat(db.queryForObject("SELECT saldo FROM itens WHERE codigo='A'",java.math.BigDecimal.class)).isEqualByComparingTo("8");
            store.validationFailed(id,"Falha técnica simulada");
            assertThat(store.status(id).status()).isEqualTo("COMPLETED_WITH_ISSUES");
            assertThat(store.status(id).error()).isEqualTo("Falha técnica simulada");
        } finally {db.execute("ALTER TABLE itens DROP CONSTRAINT test_review_rollback");}
        review.reprocess(id);
        finalizer.finish(id);
        assertThat(store.status(id).status()).isEqualTo("COMPLETED");
        assertThat(store.status(id).issues()).isZero();
        assertThat(db.queryForObject("SELECT raw_payload FROM import_rows WHERE source='itens.csv' AND record_key='A'",String.class)).isEqualTo(original);
        assertThat(db.queryForObject("SELECT count(*) FROM import_corrections",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT saldo FROM itens WHERE codigo='B'",java.math.BigDecimal.class)).isEqualByComparingTo("9");
        orders.create(requestA);
        assertThat(db.queryForObject("SELECT saldo FROM itens WHERE codigo='A'",java.math.BigDecimal.class)).isEqualByComparingTo("6");
    }

    @Test void cadastroManualDeItemAusentePublicaPedidoInteiroMesmoComOutraPendencia() throws Exception {
        String id=start(archive(false,true,false));stageDirectly(id);finalizer.finish(id);
        String body=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("revision",0,"justification","Cadastro recuperado do legado",
                "fields",Map.of("codigo","INEXISTENTE","descricao","Recuperado","unidade","UN","saldo_inicial","13","saldo_atual","10","ativo","S","data_cadastro","2020-01-01")));
        mvc.perform(post("/api/imports/"+id+"/rows/itens.csv").contentType("application/json").content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/imports/"+id+"/reprocess")).andExpect(status().isAccepted());
        finalizer.finish(id);
        assertThat(store.status(id).status()).isEqualTo("COMPLETED_WITH_ISSUES");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedidos WHERE id_externo='P1'",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedido_itens l JOIN pedidos p ON p.id=l.pedido_id WHERE p.id_externo='P1'",Integer.class)).isEqualTo(1);
        assertThat(store.issues(id,0,100)).allSatisfy(issue->assertThat(issue.get("motivo").toString()).startsWith("BALANCE_MISMATCH"));
        orders.create(new br.com.estoque.orders.domain.NewOrder("ONLINE-RECUPERADO","O","Maria",br.com.estoque.orders.domain.OrderSource.PORTAL,
                List.of(new br.com.estoque.orders.domain.OrderLine("INEXISTENTE",new java.math.BigDecimal("1")))));
        assertThat(db.queryForObject("SELECT saldo FROM itens WHERE codigo='INEXISTENTE'",java.math.BigDecimal.class)).isEqualByComparingTo("9");
    }

    @Test void duplicataIdenticaPodeSerDesconsideradaComAuditoriaSemRemoverUltimaLinha() throws Exception {
        String id=start(archive(false,false,false));stageDirectly(id);
        Long originalId=db.queryForObject("SELECT id FROM pedidos WHERE id_externo='P1'",Long.class);
        db.update("INSERT INTO import_rows(job_id,source,line_number,record_key,reference_key,raw_payload,payload) SELECT job_id,source,99,record_key,reference_key,raw_payload,payload FROM import_rows WHERE source='pedido_itens.csv' AND line_number=2");
        finalizer.finish(id);
        String body="{\"revision\":0,\"justification\":\"Duplicação confirmada na origem\"}";
        mvc.perform(post("/api/imports/"+id+"/rows/pedido_itens.csv/3/exclude-duplicate").contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/imports/"+id+"/rows/pedido_itens.csv/99/exclude-duplicate").contentType("application/json").content(body)).andExpect(status().isNoContent());
        mvc.perform(post("/api/imports/"+id+"/reprocess")).andExpect(status().isAccepted());
        finalizer.finish(id);
        assertThat(store.status(id).status()).isEqualTo("COMPLETED");
        assertThat(db.queryForObject("SELECT id FROM pedidos WHERE id_externo='P1'",Long.class)).isEqualTo(originalId);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedido_itens",Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT excluded FROM import_rows WHERE line_number=99",Boolean.class)).isTrue();
        assertThat(store.issues(id,0,100,true)).isNotEmpty();
        assertThat(store.issues(id,0,100)).isEmpty();
    }

    @Test void cargaAntigaConcluidaEmRevisaoRecebeLiberacaoParcial() throws Exception {
        String id=start(archive(true,false,true));stageDirectly(id);
        db.update("UPDATE import_jobs SET status='NEEDS_REVIEW' WHERE id=?",id);
        assertThat(store.awaitingValidation()).contains(id);
        finalizer.finish(id);
        assertThat(store.status(id).status()).isEqualTo("COMPLETED_WITH_ISSUES");
        assertThat(store.ready()).isTrue();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM import_quarantine WHERE entity_type='ITEM' AND record_key='B'",Integer.class)).isZero();
    }

    @Test void correcoesConcorrentesExigemRevisaoENaoAlteramItensJaLiberados() throws Exception {
        String id=start(archive(true,false,true));stageDirectly(id);finalizer.finish(id);
        var fields=Map.of("codigo","A","descricao","Papel","unidade","UN","saldo_inicial","10","saldo_atual","7","ativo","S","data_cadastro","2020-01-01");
        var correction=new ImportReview.Correction(fields,0,"Conferência do saldo");
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        Callable<Boolean> change=()->{start.await();try{review.correct(id,"itens.csv",2,correction);return true;}catch(IllegalStateException conflict){return false;}};
        try {
            var a=pool.submit(change);var b=pool.submit(change);start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }finally{pool.shutdownNow();}
        assertThat(db.queryForObject("SELECT COUNT(*) FROM import_corrections",Integer.class)).isEqualTo(1);
        var released=new HashMap<>(fields);released.put("codigo","B");released.put("saldo_atual","10");
        assertThatThrownBy(()->review.correct(id,"itens.csv",3,new ImportReview.Correction(released,0,"Não pode alterar histórico liberado")))
                .isInstanceOf(IllegalStateException.class);
        review.reprocess(id);review.reprocess(id);
        assertThatThrownBy(()->review.correct(id,"itens.csv",2,new ImportReview.Correction(fields,1,"Durante reprocessamento")))
                .isInstanceOf(IllegalStateException.class);
        finalizer.finish(id);
        assertThat(store.status(id).status()).isEqualTo("COMPLETED");
        assertThat(store.status(id).issues()).isZero();
    }

    @Test void cancelamentoLegadoSemMotivoEntraSemOcorrenciaENaoDevolveSaldoNovamente() throws Exception {
        String id=start(archive(false,false,false));preparer.prepareNext();
        db.update("UPDATE import_batches SET payload=replace(payload,'Desistencia','   ') WHERE source='pedidos.csv'");
        for(String batch:db.queryForList("SELECT id FROM import_batches WHERE job_id=?",String.class,id))processor.process(batch);
        publishAll(id);finalizer.finish(id);
        assertThat(store.status(id).status()).isEqualTo("COMPLETED");
        assertThat(store.status(id).issues()).isZero();
        assertThat(db.queryForObject("SELECT motivo_cancelamento FROM pedidos WHERE id_externo='P2'",String.class)).isNull();
        Long orderId=db.queryForObject("SELECT id FROM pedidos WHERE id_externo='P2'",Long.class);
        assertThat(orders.get(orderId).cancellationReason()).isNull();
        assertThatThrownBy(()->orders.cancel(orderId,"Outra tentativa")).isInstanceOf(br.com.estoque.orders.domain.OrderErrors.Conflict.class);
        assertThat(db.queryForObject("SELECT saldo FROM itens WHERE codigo='A'",java.math.BigDecimal.class)).isEqualByComparingTo("7");
    }

    @Test void referenciaInexistenteNaoImportaParteDoPedido() throws Exception {
        String id=start(archive(false,true,false));stageDirectly(id);finalizer.finish(id);
        assertThat(store.status(id).status()).isEqualTo("COMPLETED_WITH_ISSUES");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedidos WHERE id_externo='P1'",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedidos WHERE id_externo='P2'",Integer.class)).isEqualTo(1);
        assertThat(store.issues(id,0,100)).anySatisfy(issue->assertThat(issue.get("motivo").toString()).startsWith("UNKNOWN_ITEM"));
    }

    @Test void consumidoresConcorrentesNaoDuplicamMesmoLote() throws Exception {
        String id=start(archive(false,false,false));preparer.prepareNext();
        String batch=db.queryForObject("SELECT id FROM import_batches WHERE job_id=? AND source='itens.csv'",String.class,id);
        var pool=Executors.newFixedThreadPool(2);var go=new CountDownLatch(1);
        Callable<Void> call=()->{go.await();processor.process(batch);return null;};
        try {var a=pool.submit(call);var b=pool.submit(call);go.countDown();a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);}
        finally {pool.shutdownNow();}
        assertThat(db.queryForObject("SELECT COUNT(*) FROM import_rows WHERE source='itens.csv'",Integer.class)).isEqualTo(1);
        assertThat(store.status(id).completedBatches()).isEqualTo(1);
    }

    @Test void falhaNoLoteDesfazLinhasEMarcadorPermitindoRetomada() throws Exception {
        String id=start(archive(false,false,true));preparer.prepareNext();
        String batch=db.queryForObject("SELECT id FROM import_batches WHERE source='itens.csv'",String.class);
        db.execute("ALTER TABLE import_rows ADD CONSTRAINT test_reject_b CHECK(record_key<>'B')");
        try {
            assertThatThrownBy(()->processor.process(batch)).isInstanceOf(RuntimeException.class);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM import_rows",Integer.class)).isZero();
            assertThat(store.status(id).completedBatches()).isZero();
        } finally {db.execute("ALTER TABLE import_rows DROP CONSTRAINT test_reject_b");}
        processor.process(batch);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM import_rows",Integer.class)).isEqualTo(2);
        assertThat(store.status(id).completedBatches()).isEqualTo(1);
    }

    @Test void dadosInvalidosFicamPreservadosParaRevisao() throws Exception {
        byte[] zip=archive(false,false,false);
        String id=start(zip);preparer.prepareNext();
        db.update("UPDATE import_batches SET payload=replace(payload,'Papel','') WHERE source='itens.csv'");
        for(String batch:db.queryForList("SELECT id FROM import_batches",String.class))processor.process(batch);
        publishAll(id);
        finalizer.finish(id);
        assertThat(store.status(id).status()).isEqualTo("COMPLETED_WITH_ISSUES");
        assertThat(store.issues(id,0,100)).anySatisfy(issue->{assertThat(issue.get("arquivo")).isEqualTo("itens.csv");assertThat(issue.get("original")).isNotNull();});
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="mass.file",matches=".+")
    void massaCompletaPassaPelaFilaEPreservaInconsistencias() throws Exception {
        long started=System.nanoTime();
        String id;
        try(var input=Files.newInputStream(java.nio.file.Path.of(System.getProperty("mass.file")))) {
            id=store.register(archives.store(input));
        }
        var workers=Executors.newFixedThreadPool(2);
        var stopped=new java.util.concurrent.atomic.AtomicBoolean();
        var preparedAt=new java.util.concurrent.atomic.AtomicLong();
        var firstItemsAt=new java.util.concurrent.atomic.AtomicLong();
        var firstOrdersAt=new java.util.concurrent.atomic.AtomicLong();
        var reading=workers.submit(()->{preparer.prepareNext();preparedAt.set(System.nanoTime());});
        var dispatch=workers.submit(()->{
            while(!stopped.get()) {
                publisher.publish();
                try {Thread.sleep(250);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
            }
        });
        var lastLog=new java.util.concurrent.atomic.AtomicLong();
        try {
          await().atMost(Duration.ofMinutes(Long.getLong("mass.timeout.minutes",120L))).pollInterval(Duration.ofMillis(100)).until(()->{
            if(reading.isDone())reading.get();
            if(dispatch.isDone())dispatch.get();
            incrementalScheduler.publish();
            var progress=store.status(id);
            assertThat(progress.status()).isIn("PREPARING","STAGING");
            if(progress.publishedItems()>0)firstItemsAt.compareAndSet(0,System.nanoTime());
            if(progress.publishedOrders()>0)firstOrdersAt.compareAndSet(0,System.nanoTime());
            if(System.nanoTime()-lastLog.get()>Duration.ofSeconds(20).toNanos()) {
                System.out.println("MASS_PROGRESS staged="+progress.completedBatches()+" prepared="+progress.preparedBatches()+" items="+progress.publishedItems()+" orders="+progress.publishedOrders());
                lastLog.set(System.nanoTime());
            }
            return progress.status().equals("STAGING")&&progress.totalBatches()>0&&progress.completedBatches()==progress.totalBatches()&&incremental.pendingBatches(id).isEmpty();
          });
        }finally{stopped.set(true);workers.shutdownNow();workers.awaitTermination(30,TimeUnit.SECONDS);}
        long staged=System.nanoTime();
        assertThat(store.status(id).totalBatches()).isEqualTo(6766);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM import_rows WHERE job_id=?",Long.class,id)).isEqualTo(3382295L);
        finalizer.finish(id);
        var result=store.status(id);
        assertThat(result.status()).isEqualTo("COMPLETED_WITH_ISSUES");
        assertThat(store.ready()).isTrue();
        assertThat(result.publishedOrders()).isPositive();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedidos p WHERE NOT EXISTS (SELECT 1 FROM pedido_itens l WHERE l.pedido_id=p.id)",Long.class)).isZero();
        var report=new LinkedHashMap<String,Object>();
        report.put("sha256",id);report.put("status",result.status());report.put("rows",3382295);
        report.put("batches",result.totalBatches());report.put("issues",result.issues());
        report.put("prepareSeconds",(preparedAt.get()-started)/1_000_000_000.0);
        report.put("pipelineSeconds",(staged-started)/1_000_000_000.0);
        report.put("firstItemsPublishedSeconds",(firstItemsAt.get()-started)/1_000_000_000.0);
        report.put("firstOrdersPublishedSeconds",(firstOrdersAt.get()-started)/1_000_000_000.0);
        report.put("publishedOrganizations",result.publishedOrganizations());
        report.put("publishedItems",result.publishedItems());
        report.put("publishedOrders",result.publishedOrders());
        report.put("publishedOrderLines",result.publishedOrderLines());
        report.put("validationSeconds",(System.nanoTime()-staged)/1_000_000_000.0);
        report.put("issuesByReason",db.queryForList("SELECT split_part(reason,':',1) AS reason,COUNT(*) AS count FROM import_issues WHERE job_id=? GROUP BY 1 ORDER BY 1",id));
        Files.createDirectories(java.nio.file.Path.of("target"));
        new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValue(java.nio.file.Path.of("target/import-incremental-report.json").toFile(),report);
        System.out.println("MASS_RESULT "+report);
    }

    @Test void retomadaPreservaLotesConcluidosERedefineTentativas() throws Exception {
        String id=start(archive(false,false,false));preparer.prepareNext();
        String completed=db.queryForObject("SELECT id FROM import_batches WHERE source='orgaos.csv'",String.class);
        String failed=db.queryForObject("SELECT id FROM import_batches WHERE source='itens.csv'",String.class);
        processor.process(completed);
        for(int attempt=0;attempt<8;attempt++)store.failedAttempt(failed,"Falha técnica simulada");
        assertThat(store.status(id).status()).isEqualTo("FAILED");
        store.retry(id);
        assertThat(store.status(id).status()).isEqualTo("STAGING");
        assertThat(store.status(id).completedBatches()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT attempts FROM import_batches WHERE id=?",Integer.class,failed)).isZero();
        for(String batch:db.queryForList("SELECT id FROM import_batches WHERE job_id=?",String.class,id))processor.process(batch);
        publishAll(id);
        finalizer.finish(id);
        assertThat(store.status(id).status()).isEqualTo("COMPLETED");
    }

    @Test void preparacaoRetomadaNaoDuplicaLotes() throws Exception {
        String id=start(archive(false,false,false));preparer.prepareNext();
        db.update("UPDATE import_jobs SET status='PREPARING',lease_until=NULL,total_batches=0 WHERE id=?",id);
        preparer.prepareNext();
        assertThat(store.status(id).totalBatches()).isEqualTo(4);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM import_batches WHERE job_id=?",Integer.class,id)).isEqualTo(4);
    }

    @Test void apiRecebeArquivoEConsultaProgresso() throws Exception {
        var file=new org.springframework.mock.web.MockMultipartFile("file","massa.zip","application/zip",archive(false,false,false));
        var response=mvc.perform(multipart("/api/imports").file(file)).andExpect(status().isAccepted()).andReturn();
        String id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/imports/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PREPARING"));
        mvc.perform(get("/api/imports/"+id+"/issues?limit=101")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/imports/"+id+"/retry")).andExpect(status().isConflict());
        mvc.perform(get("/api/imports/inexistente")).andExpect(status().isNotFound());
    }

    @Test void apiDescobreCargaAtualSemInformarIdEPreservaResultadoFinal() throws Exception {
        mvc.perform(get("/api/imports/active")).andExpect(status().isNoContent()).andExpect(content().string(""));
        String id=start(archive(false,false,false));
        mvc.perform(get("/api/imports/active")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.completedBatches").value(0));
        stageDirectly(id);finalizer.finish(id);
        mvc.perform(get("/api/imports/active")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.publishedItems").value(1)).andExpect(jsonPath("$.publishedOrders").value(2));
    }

    @Test void apiDescobreCargaSubstitutaMesmoComHistoricoAnterior() throws Exception {
        String old=start(archive(false,false,false));
        db.update("UPDATE import_jobs SET status='FAILED' WHERE id=?",old);
        mvc.perform(get("/api/imports/active")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(old)).andExpect(jsonPath("$.status").value("FAILED"));
        String current=start(archive(false,false,false));
        mvc.perform(get("/api/imports/active")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(current)).andExpect(jsonPath("$.status").value("PREPARING"));
        mvc.perform(get("/api/imports/"+old)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(old));
    }

    @Test void zipCorrompidoRetornaErroDeEntrada() throws Exception {
        var file=new org.springframework.mock.web.MockMultipartFile("file","massa.zip","application/zip","invalid".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/imports").file(file)).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM import_jobs",Integer.class)).isZero();
    }

    @Test void falhaNaPublicacaoDesfazPedidosDoLoteEMarcador() throws Exception {
        String id=start(archive(false,false,false));preparer.prepareNext();
        for(String batch:db.queryForList("SELECT id FROM import_batches WHERE job_id=?",String.class,id))processor.process(batch);
        incremental.publishCatalogs(id);incremental.prepareManifest(id);
        String batch=db.queryForObject("SELECT id FROM import_batches WHERE source='pedido_itens.csv'",String.class);
        db.execute("ALTER TABLE pedidos ADD CONSTRAINT test_reject_p2 CHECK(id_externo<>'P2')");
        try {
            assertThatThrownBy(()->incremental.publishBatch(batch)).isInstanceOf(RuntimeException.class);
            for(String table:List.of("pedidos","pedido_itens"))
                assertThat(db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)).isZero();
            assertThat(db.queryForObject("SELECT COUNT(*) FROM itens",Integer.class)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT publication_done FROM import_batches WHERE id=?",Boolean.class,batch)).isFalse();
            assertThat(store.status(id).publishedOrders()).isZero();
            assertThat(store.ready()).isFalse();
            assertThat(store.status(id).status()).isEqualTo("STAGING");
        } finally {db.execute("ALTER TABLE pedidos DROP CONSTRAINT test_reject_p2");}
        incremental.publishBatch(batch);
        finalizer.finish(id);
        assertThat(store.status(id).status()).isEqualTo("COMPLETED");
    }

    @Test void falhaNoGrupoDesfazTodosOsPedidosContadoresEMarcadores() throws Exception {
        String id=start(archive(false,false,false));preparer.prepareNext();
        db.update("UPDATE import_jobs SET status='PREPARING' WHERE id=?",id);
        store.addBatch(id,"pedido_itens.csv",1,new br.com.estoque.imports.domain.BatchPayload(List.of(
                new br.com.estoque.imports.domain.BatchPayload.Row(20,Map.of("id_externo_pedido","AUSENTE","codigo_item","A","quantidade","1")))));
        store.sourcePrepared(id,"pedido_itens.csv",2);store.preparationDone(id,5);
        for(String batch:db.queryForList("SELECT id FROM import_batches WHERE job_id=?",String.class,id))processor.process(batch);
        incremental.publishCatalogs(id);incremental.prepareManifest(id);
        var batches=incremental.pendingBatches(id);
        assertThat(batches).hasSize(2);
        db.execute("ALTER TABLE pedidos ADD CONSTRAINT test_reject_group CHECK(id_externo<>'P2')");
        try {
            assertThatThrownBy(()->incremental.publishBatches(batches)).isInstanceOf(RuntimeException.class);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM pedidos",Integer.class)).isZero();
            assertThat(db.queryForObject("SELECT COUNT(*) FROM pedido_itens",Integer.class)).isZero();
            assertThat(db.queryForObject("SELECT COUNT(*) FROM import_batches WHERE publication_done=true",Integer.class)).isZero();
            assertThat(store.status(id).publishedOrders()).isZero();
            assertThat(store.status(id).publishedOrderLines()).isZero();
        }finally {db.execute("ALTER TABLE pedidos DROP CONSTRAINT test_reject_group");}
        incremental.publishBatches(batches);
        // Force a replay through SQL, rather than just the completed-batch guard.
        db.update("UPDATE import_batches SET publication_done=false WHERE source='pedido_itens.csv'");
        incremental.publishBatches(batches);
        assertThat(store.status(id).publishedOrders()).isEqualTo(2);
        assertThat(store.status(id).publishedOrderLines()).isEqualTo(2);
    }

    @Test void catalogosEntramAntesDeTodosOsLotesTerminarem() throws Exception {
        String id=start(archive(false,false,false));preparer.prepareNext();
        db.update("UPDATE import_jobs SET status='PREPARING' WHERE id=?",id);
        for(String batch:db.queryForList("SELECT id FROM import_batches WHERE source IN ('orgaos.csv','itens.csv')",String.class))processor.process(batch);
        incremental.publishCatalogs(id);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM orgaos",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM itens",Integer.class)).isEqualTo(1);
        assertThat(store.status(id).completedBatches()).isEqualTo(2);
        assertThat(store.status(id).publishedItems()).isEqualTo(1);
        assertThat(store.ready()).isFalse();
        mvc.perform(get("/api/itens")).andExpect(status().isOk())
                .andExpect(header().string("X-Import-Ready","false"))
                .andExpect(jsonPath("$.registros[0].codigo").value("A"));
        mvc.perform(post("/api/pedidos").contentType("application/json").content("{}"))
                .andExpect(status().isServiceUnavailable());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void pedidoEsperaTodasAsLinhasEReentregasConcorrentesNaoDuplicam(boolean grouped) throws Exception {
        String id=start(archive(false,false,true));preparer.prepareNext();
        String first=db.queryForObject("SELECT id FROM import_batches WHERE source='pedido_itens.csv'",String.class);
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var one=new br.com.estoque.imports.domain.BatchPayload(List.of(new br.com.estoque.imports.domain.BatchPayload.Row(2,
                Map.of("id_externo_pedido","P1","codigo_item","A","quantidade","3"))));
        db.update("UPDATE import_batches SET payload=?,order_counts=NULL WHERE id=?",json.writeValueAsString(one),first);
        db.update("UPDATE import_jobs SET status='PREPARING' WHERE id=?",id);
        store.addBatch(id,"pedido_itens.csv",1,new br.com.estoque.imports.domain.BatchPayload(List.of(
                new br.com.estoque.imports.domain.BatchPayload.Row(3,Map.of("id_externo_pedido","P1","codigo_item","B","quantidade","1")),
                new br.com.estoque.imports.domain.BatchPayload.Row(4,Map.of("id_externo_pedido","P2","codigo_item","A","quantidade","2")))));
        store.sourcePrepared(id,"pedido_itens.csv",2);store.preparationDone(id,5);
        for(String batch:db.queryForList("SELECT id FROM import_batches WHERE source<>'pedido_itens.csv'",String.class))processor.process(batch);
        incremental.publishCatalogs(id);incremental.prepareManifest(id);
        processor.process(first);incremental.publishBatch(first);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedidos",Integer.class)).isZero();
        String last=db.queryForObject("SELECT id FROM import_batches WHERE source='pedido_itens.csv' AND batch_number=1",String.class);
        processor.process(last);
        db.update("UPDATE import_batches SET publication_done=false WHERE id=?",first);
        var pool=Executors.newFixedThreadPool(2);var go=new CountDownLatch(1);
        Callable<Void> publishFirst=()->{go.await();if(grouped)incremental.publishBatches(List.of(first,last,first));else incremental.publishBatch(first);return null;};
        Callable<Void> publishLast=()->{go.await();if(grouped)incremental.publishBatches(List.of(last,first));else incremental.publishBatch(last);return null;};
        try {var a=pool.submit(publishFirst);var b=pool.submit(publishLast);go.countDown();a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);}
        finally {pool.shutdownNow();}
        incremental.publishBatch(last);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedidos",Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedido_itens",Integer.class)).isEqualTo(3);
        assertThat(store.status(id).publishedOrders()).isEqualTo(2);
        assertThat(store.status(id).publishedOrderLines()).isEqualTo(3);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM import_batches WHERE source='pedido_itens.csv' AND publication_done=true",Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT saldo FROM itens WHERE codigo='A'",java.math.BigDecimal.class)).isEqualByComparingTo("7");
        assertThat(store.ready()).isFalse();
    }

    private String start(byte[] data) throws IOException {return store.register(archives.store(new ByteArrayInputStream(data)));}
    @Test void cargaAntigaNaoLiberadaPodeSerSubstituidaPreservandoOrigem() throws Exception {
        String old=start(archive(true,false,false));stageDirectly(old);finalizer.finish(old);
        assertThat(store.status(old).status()).isEqualTo("COMPLETED_WITH_ISSUES");
        assertThat(store.status(old).publishedOrders()).isEqualTo(2);
        db.update("UPDATE import_jobs SET status='NEEDS_REVIEW' WHERE id=?",old);
        db.update("UPDATE import_control SET ready=false WHERE id=1");
        String next=start(archive(false,false,false));
        assertThat(next).isNotEqualTo(old);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedidos",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM import_rows WHERE job_id=?",Integer.class,old)).isPositive();
        stageDirectly(next);finalizer.finish(next);
        assertThat(store.status(next).status()).isEqualTo("COMPLETED");
        orders.create(new br.com.estoque.orders.domain.NewOrder("ONLINE-NOVA-CARGA","O","Maria",br.com.estoque.orders.domain.OrderSource.PORTAL,
                List.of(new br.com.estoque.orders.domain.OrderLine("A",new java.math.BigDecimal("1")))));
    }

    @Test void substituicaoNaoApagaDadosDeForaDaCarga() throws Exception {
        String id=start(archive(true,false,false));stageDirectly(id);finalizer.finish(id);
        db.update("INSERT INTO orgaos(codigo,sigla,nome,uf) VALUES ('MANUAL','MAN','Manual','SP')");
        assertThatThrownBy(()->start(archive(false,false,false))).isInstanceOf(IllegalStateException.class);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM orgaos WHERE codigo='MANUAL'",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedidos",Integer.class)).isEqualTo(2);
    }

    @Test void conflitoDeCabecalhoNaoPublicaPedidoMesmoComLinhasCompletas() throws Exception {
        String id=start(archive(false,false,false));preparer.prepareNext();
        for(String batch:db.queryForList("SELECT id FROM import_batches",String.class))processor.process(batch);
        db.update("UPDATE import_rows SET payload=replace(payload,'Maria','Outra pessoa') WHERE source='pedidos.csv' AND line_number=3");
        publishAll(id);finalizer.finish(id);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedidos WHERE id_externo='P1'",Integer.class)).isZero();
        assertThat(store.status(id).status()).isEqualTo("COMPLETED_WITH_ISSUES");
    }

    @Test void cargaAntigaEmRevisaoPodeRetomarSomenteAsInsercoesIncrementais() throws Exception {
        String id=start(archive(true,false,false));preparer.prepareNext();
        for(String batch:db.queryForList("SELECT id FROM import_batches",String.class))processor.process(batch);
        db.update("DELETE FROM import_sources WHERE job_id=?",id);
        db.update("UPDATE import_jobs SET status='NEEDS_REVIEW' WHERE id=?",id);
        store.retry(id);
        assertThat(store.status(id).completedBatches()).isEqualTo(4);
        publishAll(id);finalizer.finish(id);
        assertThat(store.status(id).publishedOrders()).isEqualTo(2);
        assertThat(store.status(id).status()).isEqualTo("COMPLETED_WITH_ISSUES");
        assertThatThrownBy(()->store.retry(id)).isInstanceOf(IllegalStateException.class);
    }

    @Test void preparacaoNaoBloqueiaReferenciaDaCargaUsadaPelosConsumidores() throws Exception {
        String id=start(archive(false,false,false));
        var pool=Executors.newSingleThreadExecutor();
        try(var connection=Objects.requireNonNull(db.getDataSource()).getConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("SELECT id FROM import_jobs WHERE id=? FOR KEY SHARE")) {
                statement.setString(1,id);statement.executeQuery().close();
            }
            try {
                var future=pool.submit(()->store.addBatch(id,"orgaos.csv",0,
                        new br.com.estoque.imports.domain.BatchPayload(List.of(
                                new br.com.estoque.imports.domain.BatchPayload.Row(2,Map.of("codigo","O","sigla","ORG","nome","Orgao","uf","SP"))))));
                future.get(3,TimeUnit.SECONDS);
            }finally{connection.rollback();}
        }finally{pool.shutdownNow();}
        assertThat(store.status(id).preparedBatches()).isEqualTo(1);
    }
    private void stageDirectly(String id) {preparer.prepareNext();for(String batch:db.queryForList("SELECT id FROM import_batches WHERE job_id=?",String.class,id))processor.process(batch);publishAll(id);}
    private void publishAll(String id) {
        incremental.publishCatalogs(id);incremental.prepareManifest(id);
        List<String> batches;
        while(!(batches=incremental.pendingBatches(id)).isEmpty())for(String batch:batches)incremental.publishBatch(batch);
    }
    private byte[] archive(boolean badBalance,boolean badReference,boolean secondItem) throws IOException {
        var files=new LinkedHashMap<String,String>();
        files.put("orgaos.csv","codigo;sigla;nome;uf\nO;ORG;Orgao;SP\n");
        files.put("itens.csv","codigo;descricao;unidade;saldo_inicial;saldo_atual;ativo;data_cadastro\nA;Papel; un ;10;"+(badBalance?"8":"7")+";S;2020-01-01\n"+(secondItem?"B;Caneta;UN;10;10;S;2020-01-01\n":""));
        files.put("pedidos.csv","id_externo;codigo_orgao;solicitante;origem;data_hora;situacao;data_cancelamento;motivo_cancelamento\nP1;O;Maria;PORTAL;2020-01-02T10:00:00;CONFIRMADO;;\nP1;O;Maria;PORTAL;2020-01-02T10:00:00;CONFIRMADO;;\nP2;O;Maria;PORTAL;2020-01-03T10:00:00;CANCELADO;2020-01-04T10:00:00;Desistencia\n");
        files.put("pedido_itens.csv","id_externo_pedido;codigo_item;quantidade\nP1;"+(badReference?"INEXISTENTE":"A")+";3\nP2;A;2\n");
        var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes)){zip.setComment(UUID.randomUUID().toString());for(var entry:files.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}
        return bytes.toByteArray();
    }
}
