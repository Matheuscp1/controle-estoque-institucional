package br.com.estoque.orders.application;

import java.util.function.Supplier;

public interface UnitOfWork {
    <T> T execute(Supplier<T> work);
}
