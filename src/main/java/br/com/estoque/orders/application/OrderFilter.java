package br.com.estoque.orders.application;

import br.com.estoque.orders.domain.OrderStatus;
import java.time.OffsetDateTime;

public record OrderFilter(String organizationCode, OffsetDateTime from, OffsetDateTime to,
                          OrderStatus status, Long beforeId, int limit) {
    public OrderFilter {
        if (organizationCode == null || organizationCode.isBlank() || organizationCode.length() > 64)
            throw new IllegalArgumentException("Código do órgão inválido.");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Limite deve estar entre 1 e 100.");
        if (beforeId != null && beforeId <= 0) throw new IllegalArgumentException("Cursor deve ser positivo.");
        if (from != null && to != null && !from.isBefore(to)) throw new IllegalArgumentException("Período inválido.");
    }
}
