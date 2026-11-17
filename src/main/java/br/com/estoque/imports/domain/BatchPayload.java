package br.com.estoque.imports.domain;

import java.util.List;
import java.util.Map;

public record BatchPayload(List<Row> rows) {
    public record Row(long line, Map<String,String> fields) {}
}
