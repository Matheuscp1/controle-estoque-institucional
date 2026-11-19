package br.com.estoque.orders.application;

import br.com.estoque.orders.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {
    final OrderRepository repository = mock(OrderRepository.class);
    final StockMovement stock = mock(StockMovement.class);
    final OrganizationLookup organizations = mock(OrganizationLookup.class);
    final UnitOfWork transactions = new UnitOfWork() {
        public <T> T execute(Supplier<T> work) { return work.get(); }
    };
    final Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
    final OrderService service = new OrderService(repository, stock, organizations, transactions, clock);
    final NewOrder request = new NewOrder("P", "O", "Maria", OrderSource.INTEGRACAO,
            List.of(new OrderLine("A", BigDecimal.ONE)));

    @Test void reenvioDePedidoCanceladoNaoReservaNovamente() {
        var order = Order.confirmed(1, request, OffsetDateTime.now(clock)).cancel("Erro", OffsetDateTime.now(clock));
        when(repository.findByExternalId("P")).thenReturn(Optional.of(order));
        assertThat(service.create(request)).isEqualTo(order);
        verifyNoInteractions(stock, organizations);
    }

    @Test void idReutilizadoComOutroConteudoRetornaConflito() {
        when(repository.findByExternalId("P")).thenReturn(Optional.of(Order.confirmed(1, request, OffsetDateTime.now(clock))));
        var other = new NewOrder("P", "O", "Outra pessoa", request.source(), request.items());
        assertThatThrownBy(() -> service.create(other)).isInstanceOf(OrderErrors.Conflict.class);
        verifyNoInteractions(stock);
    }

    @Test void orgaoAusenteNaoGravaPedidoNemAlteraSaldo() {
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(OrderErrors.NotFound.class);
        verify(repository, never()).insertHeader(any(), any());
        verifyNoInteractions(stock);
    }

    @Test void conflitoDeInsercaoReconsultaPedidoDepoisDoRollback() {
        var order = Order.confirmed(1, request, OffsetDateTime.now(clock));
        when(repository.findByExternalId("P")).thenReturn(Optional.empty(), Optional.of(order));
        when(organizations.exists("O")).thenReturn(true);
        when(repository.insertHeader(any(), any())).thenThrow(new DuplicateExternalId(new RuntimeException()));
        assertThat(service.create(request)).isEqualTo(order);
        verifyNoInteractions(stock);
    }
}
