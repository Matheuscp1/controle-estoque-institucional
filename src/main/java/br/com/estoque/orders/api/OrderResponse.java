package br.com.estoque.orders.api;

import br.com.estoque.orders.domain.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(long id, String idExterno, String codigoOrgao, String solicitante,
                            OrderSource origem, OffsetDateTime dataHora, OrderStatus situacao,
                            OffsetDateTime dataCancelamento, String motivoCancelamento, List<Line> itens) {
    public record Line(String codigoItem, BigDecimal quantidade) {}
    static OrderResponse from(Order order) {
        var request = order.request();
        return new OrderResponse(order.id(), request.externalId(), request.organizationCode(), request.requester(),
                request.source(), order.createdAt(), order.status(), order.cancelledAt(), order.cancellationReason(),
                request.items().stream().map(line -> new Line(line.itemCode(), line.quantity())).toList());
    }
}
