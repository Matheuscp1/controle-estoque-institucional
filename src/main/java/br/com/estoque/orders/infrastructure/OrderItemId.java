package br.com.estoque.orders.infrastructure;

import java.io.Serializable;
import java.util.Objects;
import javax.persistence.*;

@Embeddable
public class OrderItemId implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name="pedido_id")
    private Long orderId;
    @Column(name="codigo_item", length=64)
    private String itemCode;

    protected OrderItemId() {}
    OrderItemId(Long orderId, String itemCode) { this.orderId = orderId; this.itemCode = itemCode; }
    String itemCode() { return itemCode; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OrderItemId that)) return false;
        return Objects.equals(orderId, that.orderId) && Objects.equals(itemCode, that.itemCode);
    }
    @Override public int hashCode() { return Objects.hash(orderId, itemCode); }
}
