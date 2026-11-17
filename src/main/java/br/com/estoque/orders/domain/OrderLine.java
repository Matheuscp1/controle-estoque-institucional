package br.com.estoque.orders.domain;

import java.math.BigDecimal;

public record OrderLine(String itemCode, BigDecimal quantity) {
    public OrderLine {
        NewOrder.required(itemCode, "Código do item", 64);
        if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 3
                || quantity.compareTo(new BigDecimal("10000000000000000")) >= 0) {
            throw new IllegalArgumentException("Quantidade deve ser positiva, com até 16 inteiros e 3 casas decimais.");
        }
    }
}
