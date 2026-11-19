package br.com.estoque.orders.application;

import br.com.estoque.orders.domain.OrderLine;
import java.util.List;

public interface StockMovement {
    void reserve(List<OrderLine> lines);
    void release(List<OrderLine> lines);
}
