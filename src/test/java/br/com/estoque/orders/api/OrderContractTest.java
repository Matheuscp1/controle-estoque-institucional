package br.com.estoque.orders.api;

import br.com.estoque.items.application.ItemService;
import br.com.estoque.items.domain.ErroItem;
import br.com.estoque.items.infrastructure.*;
import br.com.estoque.organizations.application.OrganizationService;
import br.com.estoque.organizations.infrastructure.*;
import br.com.estoque.orders.application.*;
import br.com.estoque.orders.domain.*;
import br.com.estoque.orders.infrastructure.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public abstract class OrderContractTest {
    @TestConfiguration @EnableAutoConfiguration
    @Import({ItemConfiguration.class, JpaItemRepository.class, OrganizationConfiguration.class,
            JpaOrganizationRepository.class, OrderConfiguration.class, JpaOrderRepository.class,
            JpaStockMovement.class, JpaOrganizationLookup.class, SpringUnitOfWork.class,
            OrderController.class, OrderApiErrors.class})
    public static class Context {}

    @Autowired OrderService service;
    @Autowired ItemService items;
    @Autowired OrganizationService organizations;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired OrderRepository repository;
    @Autowired StockMovement stock;
    @Autowired OrganizationLookup lookup;
    @Autowired UnitOfWork transactions;

    @BeforeEach void prepare() {
        db.update("DELETE FROM pedido_itens"); db.update("DELETE FROM pedidos");
        db.update("DELETE FROM itens"); db.update("DELETE FROM orgaos");
        organizations.cadastrar("O", "SEC", "Secretaria", "SP");
        organizations.cadastrar("X", "OUT", "Outro órgão", "RJ");
        items.cadastrar("A", "Papel", "UN", BigDecimal.TEN);
        items.cadastrar("B", "Caneta", "UN", new BigDecimal("2"));
    }

    NewOrder request(String key, OrderLine... lines) {
        return new NewOrder(key, "O", "Maria", OrderSource.INTEGRACAO, List.of(lines));
    }
    OrderLine line(String code, String quantity) { return new OrderLine(code, new BigDecimal(quantity)); }
    BigDecimal balance(String code) { return items.buscar(code).saldo(); }

    @Test void criacaoEConsultaRetornamOMesmoInstanteComPrecisaoDoBanco() {
        var fixed = Clock.fixed(Instant.parse("2026-01-01T10:00:00.123456789Z"), ZoneOffset.UTC);
        var withClock = new OrderService(repository, stock, lookup, transactions, fixed);
        var created = withClock.create(request("P", line("A","1")));
        assertThat(service.get(created.id()).createdAt().toInstant()).isEqualTo(created.createdAt().toInstant());
    }

    @Test void falhaNoSegundoItemDesfazPedidoELinhasETodosOsSaldos() {
        assertThatThrownBy(() -> service.create(request("P", line("A","3"), line("B","3"))))
                .isInstanceOf(OrderErrors.Conflict.class);
        assertThat(balance("A")).isEqualByComparingTo("10");
        assertThat(items.buscar("A").versao()).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedidos", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedido_itens", Integer.class)).isZero();
    }

    @Test void itemAusenteOuInativoNaoDeixaReservaParcial() {
        assertThatThrownBy(() -> service.create(request("P", line("A","1"), line("Z","1"))))
                .isInstanceOf(OrderErrors.NotFound.class);
        items.inativar("B", 0);
        assertThatThrownBy(() -> service.create(request("P", line("A","1"), line("B","1"))))
                .isInstanceOf(OrderErrors.Conflict.class);
        assertThat(balance("A")).isEqualByComparingTo("10");
    }

    @Test void reenvioNormalizadoRetornaMesmoPedidoEConteudoDiferenteConflita() {
        var order = service.create(request("P", line("A","3"), line("B","1")));
        assertThat(service.create(request("P", line("B","1.000"), line("A","3.0"))).id()).isEqualTo(order.id());
        assertThatThrownBy(() -> service.create(request("P", line("A","4")))).isInstanceOf(OrderErrors.Conflict.class);
        assertThat(balance("A")).isEqualByComparingTo("7");
    }

    @Test void cancelaUmaVezMesmoComItemInativoEReenvioNaoReativaPedido() {
        var request = request("P", line("A","3"));
        var order = service.create(request);
        items.inativar("A", 1);
        service.cancel(order.id(), "Desistência");
        assertThatThrownBy(() -> service.cancel(order.id(), "Repetido")).isInstanceOf(OrderErrors.Conflict.class);
        assertThat(service.create(request).status()).isEqualTo(OrderStatus.CANCELADO);
        assertThat(balance("A")).isEqualByComparingTo("10");
        assertThat(items.buscar("A").ativo()).isFalse();
    }

    @Test void devolucaoQueExcedeColunaDesfazCancelamentoInteiro() {
        var order = service.create(request("P", line("A","1"), line("B","1")));
        items.editar("B", "Caneta", "UN", new BigDecimal("9999999999999999.999"), true, 1);
        assertThatThrownBy(() -> service.cancel(order.id(), "Erro")).isInstanceOf(OrderErrors.Conflict.class);
        assertThat(balance("A")).isEqualByComparingTo("9");
        assertThat(service.get(order.id()).status()).isEqualTo(OrderStatus.CONFIRMADO);
    }

    @Test void movimentacaoInvalidaEdicaoComVersaoAntiga() {
        service.create(request("P", line("A","3")));
        assertThatThrownBy(() -> items.editar("A", "Outro", "UN", BigDecimal.TEN, true, 0))
                .isInstanceOf(ErroItem.Conflito.class);
        assertThat(balance("A")).isEqualByComparingTo("7");
    }

    @Test void pedidosConcorrentesDisputamSaldoSemNegativar() throws Exception {
        assertThat(parallel(() -> attemptCreate("P"), () -> attemptCreate("Q")))
                .containsExactlyInAnyOrder(true, false);
        assertThat(balance("A")).isEqualByComparingTo("3");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedidos", Integer.class)).isEqualTo(1);
    }

    boolean attemptCreate(String key) {
        try { service.create(request(key, line("A","7"))); return true; }
        catch (OrderErrors.Conflict e) { return false; }
    }

    @Test void reenviosConcorrentesRecebemMesmoIdMesmoSemSaldoParaDoisPedidos() throws Exception {
        Callable<Long> create = () -> service.create(request("P", line("A","7"))).id();
        var ids = parallel(create, create);
        assertThat(ids.get(0)).isEqualTo(ids.get(1));
        assertThat(balance("A")).isEqualByComparingTo("3");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pedidos", Integer.class)).isEqualTo(1);
    }

    @Test void cancelamentosConcorrentesDevolvemUmaVez() throws Exception {
        long id = service.create(request("P", line("A","3"))).id();
        Callable<Boolean> cancel = () -> {
            try { service.cancel(id, "Erro"); return true; }
            catch (OrderErrors.Conflict e) { return false; }
        };
        assertThat(parallel(cancel, cancel)).containsExactlyInAnyOrder(true, false);
        assertThat(balance("A")).isEqualByComparingTo("10");
        assertThat(items.buscar("A").versao()).isEqualTo(2);
    }

    @Test void filtrosEPaginacaoIncluemLinhasSemMisturarOrgaos() {
        var first = service.create(request("P", line("A","1")));
        var second = service.create(request("Q", line("B","1")));
        service.create(new NewOrder("R", "X", "João", OrderSource.PORTAL, List.of(line("A","1"))));
        var page = service.list(new OrderFilter("O", null, null, OrderStatus.CONFIRMADO, null, 1)).registros();
        assertThat(page).extracting(Order::id).containsExactly(second.id());
        assertThat(page.get(0).request().items()).extracting(OrderLine::itemCode).containsExactly("B");
        assertThat(service.list(new OrderFilter("O", null, null, null, second.id(), 1)).registros())
                .extracting(Order::id).containsExactly(first.id());
        service.cancel(first.id(), "Erro");
        assertThat(service.list(new OrderFilter("O", null, null, OrderStatus.CANCELADO, null, 50)).registros())
                .extracting(Order::id).containsExactly(first.id());
        assertThat(service.list(new OrderFilter("O", first.createdAt(), first.createdAt().plusSeconds(1), null, null, 50)).registros())
                .extracting(Order::id).contains(first.id());
        assertThat(service.list(new OrderFilter("O", null, first.createdAt(), null, null, 50)).registros()).isEmpty();
    }

    @Test void totalDePedidosRespeitaOrgaoSituacaoPeriodoEIgnoraCursor() throws Exception {
        var first=service.create(request("P",line("A","1"),line("B","1")));
        var second=service.create(request("Q",line("A","1")));
        service.create(new NewOrder("R","X","Maria",OrderSource.PORTAL,List.of(line("A","1"))));
        mvc.perform(get("/api/orgaos/O/pedidos").param("antes",Long.toString(second.id())).param("limite","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.registros.length()").value(1)).andExpect(jsonPath("$.registros[0].id").value(first.id()));
        mvc.perform(get("/api/orgaos/O/pedidos").param("antes",Long.toString(first.id())))
                .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.registros").isEmpty());
        service.cancel(first.id(),"Revisão");
        mvc.perform(get("/api/orgaos/O/pedidos?situacao=CANCELADO"))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/orgaos/O/pedidos").param("fim",first.createdAt().toString()))
                .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.registros").isEmpty());
    }

    @Test void httpCriaConsultaCancelaEValidaEntradas() throws Exception {
        var result = mvc.perform(post("/api/pedidos").contentType("application/json").content("""
            {"idExterno":"P","codigoOrgao":"O","solicitante":"Maria","origem":"PORTAL",
             "itens":[{"codigoItem":"A","quantidade":3}]}
            """)).andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("CONFIRMADO"))
                .andExpect(jsonPath("$.itens[0].codigoItem").value("A")).andReturn();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/pedidos/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.assinatura").doesNotExist());
        mvc.perform(post("/api/pedidos/" + id + "/cancelamento").contentType("application/json").content("{\"motivo\":\"Erro\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("CANCELADO"));
        mvc.perform(post("/api/pedidos/" + id + "/cancelamento").contentType("application/json").content("{\"motivo\":\"Erro\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/orgaos/O/pedidos?situacao=CANCELADO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.registros[0].id").value(id));
        mvc.perform(get("/api/pedidos/99999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/orgaos/O/pedidos?limite=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orgaos/O/pedidos?situacao=INVALIDA")).andExpect(status().isBadRequest());
        for (String json : List.of("{}", "{", """
            {"idExterno":"P2","codigoOrgao":"O","solicitante":"Maria","origem":"PORTAL",
             "itens":[{"codigoItem":"A","quantidade":-1}]}
            """)) mvc.perform(post("/api/pedidos").contentType("application/json").content(json)).andExpect(status().isBadRequest());
    }

    private <T> List<T> parallel(Callable<T> first, Callable<T> second) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { start.await(); return first.call(); });
            var b = pool.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
}
