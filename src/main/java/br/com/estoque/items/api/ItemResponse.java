package br.com.estoque.items.api;

import br.com.estoque.items.domain.Item;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ItemResponse(String codigo, String descricao, String unidade, BigDecimal saldoInicial,
                           BigDecimal saldo, boolean ativo, long versao, OffsetDateTime dataCadastro) {
    static ItemResponse de(Item item) {
        return new ItemResponse(item.codigo(), item.descricao(), item.unidade(), item.saldoInicial(),
                item.saldo(), item.ativo(), item.versao(), item.dataCadastro());
    }
}
