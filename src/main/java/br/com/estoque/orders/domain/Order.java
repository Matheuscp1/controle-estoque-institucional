package br.com.estoque.orders.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

public record Order(long id, NewOrder request, OffsetDateTime createdAt, OrderStatus status,
                    OffsetDateTime cancelledAt, String cancellationReason) {
    public Order {
        if (id <= 0) throw new IllegalArgumentException("Identificador deve ser positivo.");
        Objects.requireNonNull(request);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(status);
        if (status == OrderStatus.CONFIRMADO && (cancelledAt != null || cancellationReason != null))
            throw new IllegalArgumentException("Pedido confirmado não possui cancelamento.");
        if (status == OrderStatus.CANCELADO) {
            NewOrder.required(cancellationReason, "Motivo", 500);
            if (cancelledAt == null || cancelledAt.isBefore(createdAt))
                throw new IllegalArgumentException("Data de cancelamento inválida.");
        }
    }

    public static Order confirmed(long id, NewOrder request, OffsetDateTime createdAt) {
        return new Order(id, request, createdAt, OrderStatus.CONFIRMADO, null, null);
    }

    public Order cancel(String reason, OffsetDateTime when) {
        NewOrder.required(reason, "Motivo", 500);
        if (status == OrderStatus.CANCELADO) throw new OrderErrors.Conflict("Pedido já cancelado.");
        if (when == null || when.isBefore(createdAt))
            throw new OrderErrors.Conflict("Cancelamento não pode anteceder o pedido.");
        return new Order(id, request, createdAt, OrderStatus.CANCELADO, when, reason);
    }
}
