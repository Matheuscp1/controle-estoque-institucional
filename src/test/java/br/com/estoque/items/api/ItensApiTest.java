package br.com.estoque.items.api;

import br.com.estoque.items.application.ItemService;
import br.com.estoque.items.domain.ErroItem;
import br.com.estoque.items.infrastructure.ItemConfiguration;
import br.com.estoque.items.infrastructure.JpaItemRepository;
import br.com.estoque.items.infrastructure.ItemEntity;
import javax.persistence.EntityManagerFactory;
import javax.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=ItensApiTest.Contexto.class, properties={
        "spring.datasource.url=jdbc:h2:mem:api_itens;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"})
@AutoConfigureMockMvc
class ItensApiTest {
    @TestConfiguration
    @EnableAutoConfiguration
    @Import({ItemController.class, ItemErrors.class, ItemConfiguration.class, JpaItemRepository.class})
    static class Contexto {}

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ItemService service;
    @Autowired EntityManagerFactory entityManagerFactory;

    @BeforeEach void preparar() { db.update("DELETE FROM itens"); }

    @Test void cadastroNormalizaUnidadeEDevolveRepresentacaoTipada() throws Exception {
        mvc.perform(post("/api/itens").contentType("application/json").content("""
            {"codigo":"A","descricao":"Papel","unidade":" un ","saldo":10.125}
            """))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.unidade").value("UN"))
            .andExpect(jsonPath("$.saldoInicial").value(10.125)).andExpect(jsonPath("$.versao").value(0));
        mvc.perform(get("/api/itens/A")).andExpect(status().isOk()).andExpect(jsonPath("$.saldo").value(10.125));
    }

    @Test void codigoDuplicadoDevolveConflito() throws Exception {
        service.cadastrar("A","Papel","UN",BigDecimal.TEN);
        mvc.perform(post("/api/itens").contentType("application/json").content("""
            {"codigo":"A","descricao":"Outro","unidade":"UN","saldo":2}
            """)).andExpect(status().isConflict());
        assertThat(service.buscar("A").saldo()).isEqualByComparingTo("10");
    }

    @Test void editaComVersaoERejeitaFormularioDesatualizado() throws Exception {
        service.cadastrar("A","Papel","UN",BigDecimal.TEN);
        String corpo = """
            {"descricao":"Papel A4","unidade":"CX","saldo":5,"ativo":true,"versao":0}
            """;
        mvc.perform(put("/api/itens/A").contentType("application/json").content(corpo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versao").value(1))
                .andExpect(jsonPath("$.saldoInicial").value(10));
        mvc.perform(put("/api/itens/A").contentType("application/json").content(corpo))
                .andExpect(status().isConflict());
    }

    @Test void inativacaoPreservaCadastroESaldo() throws Exception {
        service.cadastrar("A","Papel","UN",BigDecimal.TEN);
        mvc.perform(delete("/api/itens/A?versao=0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false)).andExpect(jsonPath("$.saldo").value(10));
        mvc.perform(delete("/api/itens/A?versao=0")).andExpect(status().isConflict());
        mvc.perform(get("/api/itens/A")).andExpect(status().isOk());
    }

    @Test void gravacaoSemAlterarCamposTambemAvancaVersao() {
        service.cadastrar("A","Papel","UN",BigDecimal.TEN);
        var resultado = service.editar("A","Papel","UN",BigDecimal.TEN,true,0);
        assertThat(resultado.versao()).isEqualTo(1);
        assertThat(service.buscar("A").versao()).isEqualTo(1);
        assertThatThrownBy(() -> service.inativar("A",0)).isInstanceOf(ErroItem.Conflito.class);
    }

    @Test void filtrosNormalizamUnidadeEPaginamPorCodigo() throws Exception {
        service.cadastrar("A","Papel branco","UN",BigDecimal.TEN);
        service.cadastrar("B","Papel reciclado","UN",BigDecimal.ONE);
        service.cadastrar("C","Caneta","CX",BigDecimal.TEN);
        mvc.perform(get("/api/itens").param("descricao","PAPEL").param("unidade"," un ")
                .param("saldoMin","1").param("saldoMax","10").param("limite","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigo").value("A"));
        mvc.perform(get("/api/itens?descricao=papel&depois=A&limite=1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].codigo").value("B"));
    }

    @Test void buscaTrataCuringasComoTextoLiteral() throws Exception {
        service.cadastrar("A","Álcool 70%_especial","UN",BigDecimal.ONE);
        service.cadastrar("B","Álcool comum","UN",BigDecimal.ONE);
        mvc.perform(get("/api/itens").param("descricao","%_"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigo").value("A"));
    }

    @Test void validaEntradasENaoExpoeDetalhesSql() throws Exception {
        mvc.perform(post("/api/itens").contentType("application/json").content("""
            {"codigo":"A","descricao":"Papel","unidade":"UN","saldo":-1}
            """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem").exists());
        mvc.perform(put("/api/itens/A").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/itens?saldoMin=10&saldoMax=1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/itens?limite=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/itens/inexistente")).andExpect(status().isNotFound());
    }

    @Test void duasEdicoesConcorrentesSomenteUmaVence() throws Exception {
        service.cadastrar("A","Papel","UN",BigDecimal.TEN);
        var inicio = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> editar = () -> {
            inicio.await();
            try { service.editar("A","Outro","UN",BigDecimal.ONE,true,0); return true; }
            catch (ErroItem.Conflito e) { return false; }
        };
        try {
            var a = pool.submit(editar);
            var b = pool.submit(editar);
            inicio.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
            assertThat(service.buscar("A").versao()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void jpaDetectaEntidadeDesatualizadaMesmoSemValidacaoDoServico() {
        var original = service.cadastrar("A","Papel","UN",BigDecimal.TEN);
        var primeiro = entityManagerFactory.createEntityManager();
        var segundo = entityManagerFactory.createEntityManager();
        try {
            primeiro.getTransaction().begin();
            segundo.getTransaction().begin();
            var a = primeiro.find(ItemEntity.class,"A");
            var b = segundo.find(ItemEntity.class,"A");
            a.alterar(original.editar("Primeiro","UN",BigDecimal.ONE,true,0));
            b.alterar(original.editar("Segundo","UN",BigDecimal.TEN,true,0));
            primeiro.getTransaction().commit();
            assertThatThrownBy(segundo::flush).isInstanceOf(OptimisticLockException.class);
        } finally {
            if (primeiro.getTransaction().isActive()) primeiro.getTransaction().rollback();
            if (segundo.getTransaction().isActive()) segundo.getTransaction().rollback();
            primeiro.close();
            segundo.close();
        }
        assertThat(service.buscar("A").descricao()).isEqualTo("Primeiro");
        assertThat(service.buscar("A").versao()).isEqualTo(1);
    }

    @Test void cadastrosConcorrentesNaoSobrescrevemMesmoCodigo() throws Exception {
        var inicio = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> cadastrar = () -> {
            inicio.await();
            try { service.cadastrar("A","Papel","UN",BigDecimal.TEN); return true; }
            catch (ErroItem.CodigoDuplicado e) { return false; }
        };
        try {
            var a = pool.submit(cadastrar);
            var b = pool.submit(cadastrar);
            inicio.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
            assertThat(service.buscar("A").versao()).isZero();
        } finally { pool.shutdownNow(); }
    }
}
