package br.com.estoque.orders.application;

import br.com.estoque.orders.domain.*;
import java.time.OffsetDateTime;
import java.util.*;

/** Operações executadas dentro da unidade de trabalho do caso de uso. */
public interface OrderRepository {
    Optional<Order> findById(long id);
    Optional<Order> findByExternalId(String externalId);
    Optional<Order> lockById(long id);
    Order insertHeader(NewOrder request, OffsetDateTime createdAt);
    void storeItems(Order order);
    void saveCancellation(Order order);
    List<Order> list(OrderFilter filter);
}
