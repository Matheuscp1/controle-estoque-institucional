package br.com.estoque.orders.infrastructure;

import br.com.estoque.items.infrastructure.ItemEntity;
import br.com.estoque.orders.domain.OrderLine;
import java.math.BigDecimal;
import javax.persistence.*;

@Entity
@Table(name="pedido_itens")
public class OrderItemEntity {
    @EmbeddedId private OrderItemId id;

    @MapsId("orderId")
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="pedido_id", nullable=false, updatable=false)
    private OrderEntity order;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="codigo_item", insertable=false, updatable=false)
    private ItemEntity item;

    @Column(name="quantidade", precision=19, scale=3, nullable=false, updatable=false)
    private BigDecimal quantity;

    protected OrderItemEntity() {}
    OrderItemEntity(OrderEntity order, OrderLine line) {
        this.order = order;
        this.id = new OrderItemId(order.id(), line.itemCode());
        this.quantity = line.quantity();
    }
    OrderLine toDomain() { return new OrderLine(id.itemCode(), quantity); }
}
