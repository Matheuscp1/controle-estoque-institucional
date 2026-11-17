package br.com.estoque.items.application;

import br.com.estoque.items.domain.Item;
import java.math.BigDecimal;

public record FiltroItens(String descricao, String unidade, BigDecimal saldoMin, BigDecimal saldoMax,
                          String depois, int limite) {
    public FiltroItens {
        if (limite < 1 || limite > 100) throw new IllegalArgumentException("Limite deve estar entre 1 e 100.");
        if (unidade != null) unidade = Item.normalizarUnidade(unidade);
        if (saldoMin != null) Item.validarSaldo(saldoMin);
        if (saldoMax != null) Item.validarSaldo(saldoMax);
        if (saldoMin != null && saldoMax != null && saldoMin.compareTo(saldoMax) > 0)
            throw new IllegalArgumentException("Faixa de saldo inválida.");
        if (descricao != null && descricao.length() > 500)
            throw new IllegalArgumentException("Descrição do filtro deve ter até 500 caracteres.");
        if (depois != null && depois.length() > 64)
            throw new IllegalArgumentException("Cursor inválido.");
    }
}
