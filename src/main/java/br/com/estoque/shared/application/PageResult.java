package br.com.estoque.shared.application;

import java.util.List;
import java.util.function.Function;

/** Total matches the search filters, excluding the page cursor and size. */
public record PageResult<T>(List<T> registros,long total) {
    public PageResult {registros=List.copyOf(registros);}
    public <R> PageResult<R> map(Function<T,R> mapper) {
        return new PageResult<>(registros.stream().map(mapper).toList(),total);
    }
}
