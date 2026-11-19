package br.com.estoque.orders.application;

import br.com.estoque.orders.domain.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import br.com.estoque.shared.application.PageResult;

public class OrderService {
    private final OrderRepository repository;
    private final StockMovement stock;
    private final OrganizationLookup organizations;
    private final UnitOfWork transactions;
    private final Clock clock;

    public OrderService(OrderRepository repository, StockMovement stock, OrganizationLookup organizations,
                        UnitOfWork transactions, Clock clock) {
        this.repository = repository;
        this.stock = stock;
        this.organizations = organizations;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Order create(NewOrder request) {
        try {
            return transactions.execute(() -> {
                var existing = repository.findByExternalId(request.externalId());
                if (existing.isPresent()) return replay(existing.get(), request);
                requireOrganization(request.organizationCode());
                // A unicidade é disputada antes do estoque: reenvios não reservam duas vezes.
                var order = repository.insertHeader(request, now());
                stock.reserve(request.items());
                repository.storeItems(order);
                return order;
            });
        } catch (DuplicateExternalId e) {
            // A transação que perdeu a corrida já foi revertida, inclusive no PostgreSQL.
            return transactions.execute(() -> replay(repository.findByExternalId(request.externalId())
                    .orElseThrow(() -> new OrderErrors.Conflict("Conflito ao registrar pedido. Tente novamente.")), request));
        }
    }

    public Order get(long id) {
        return transactions.execute(() -> repository.findById(id).orElseThrow(() -> missing(id)));
    }

    public Order cancel(long id, String reason) {
        return transactions.execute(() -> {
            var order = repository.lockById(id).orElseThrow(() -> missing(id));
            var cancelled = order.cancel(reason, now());
            stock.release(order.request().items());
            repository.saveCancellation(cancelled);
            return cancelled;
        });
    }

    public PageResult<Order> list(OrderFilter filter) {
        return transactions.execute(() -> {
            requireOrganization(filter.organizationCode());
            return repository.list(filter);
        });
    }

    private Order replay(Order existing, NewOrder request) {
        if (!existing.request().signature().equals(request.signature()))
            throw new OrderErrors.Conflict("Identificador externo já usado com outro conteúdo.");
        return existing;
    }

    private void requireOrganization(String code) {
        if (!organizations.exists(code)) throw new OrderErrors.NotFound("Órgão não encontrado: " + code);
    }

    private OrderErrors.NotFound missing(long id) { return new OrderErrors.NotFound("Pedido não encontrado: " + id); }

    private OffsetDateTime now() { return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS); }
}
