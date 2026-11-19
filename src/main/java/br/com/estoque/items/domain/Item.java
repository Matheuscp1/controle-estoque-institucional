package br.com.estoque.items.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Locale;

public record Item(String codigo, String descricao, String unidade, BigDecimal saldoInicial,
                   BigDecimal saldo, boolean ativo, long versao, OffsetDateTime dataCadastro) {
    public Item {
        codigo = texto(codigo, "Código", 64);
        descricao = texto(descricao, "Descrição", 500);
        unidade = normalizarUnidade(unidade);
        validarSaldo(saldoInicial);
        validarSaldo(saldo);
        if (versao < 0) throw new IllegalArgumentException("Versão não pode ser negativa.");
        if (dataCadastro == null) throw new IllegalArgumentException("Data de cadastro obrigatória.");
    }

    public static Item novo(String codigo, String descricao, String unidade, BigDecimal saldo, OffsetDateTime data) {
        return new Item(codigo, descricao, unidade, saldo, saldo, true, 0, data);
    }

    public Item editar(String descricao, String unidade, BigDecimal saldo, boolean ativo, long versaoEsperada) {
        conferirVersao(versaoEsperada);
        return new Item(codigo, descricao, unidade, saldoInicial, saldo, ativo, versao + 1, dataCadastro);
    }

    public Item inativar(long versaoEsperada) {
        return editar(descricao, unidade, saldo, false, versaoEsperada);
    }

    public Item reservar(BigDecimal quantidade) {
        validarQuantidade(quantidade);
        if (!ativo) throw new ErroItem.StockConflict("Item inativo: " + codigo);
        if (saldo.compareTo(quantidade) < 0) throw new ErroItem.StockConflict("Saldo insuficiente: " + codigo);
        return editar(descricao, unidade, saldo.subtract(quantidade), ativo, versao);
    }

    public Item devolver(BigDecimal quantidade) {
        validarQuantidade(quantidade);
        var novoSaldo = saldo.add(quantidade);
        try { validarSaldo(novoSaldo); }
        catch (IllegalArgumentException e) { throw new ErroItem.StockConflict("Devolução excede o saldo máximo: " + codigo); }
        return editar(descricao, unidade, novoSaldo, ativo, versao);
    }

    private static void validarQuantidade(BigDecimal quantidade) {
        validarSaldo(quantidade);
        if (quantidade.signum() == 0) throw new IllegalArgumentException("Quantidade deve ser positiva.");
    }

    private void conferirVersao(long esperada) {
        if (esperada < 0) throw new IllegalArgumentException("Versão não pode ser negativa.");
        if (esperada != versao) throw new ErroItem.Conflito();
    }

    public static String normalizarUnidade(String unidade) {
        return texto(unidade == null ? null : unidade.strip().toUpperCase(Locale.ROOT), "Unidade", 30);
    }

    public static void validarSaldo(BigDecimal saldo) {
        if (saldo == null || saldo.signum() < 0 || saldo.stripTrailingZeros().scale() > 3
                || saldo.compareTo(new BigDecimal("10000000000000000")) >= 0) {
            throw new IllegalArgumentException("Saldo deve ser não negativo, com até 16 inteiros e 3 casas decimais.");
        }
    }

    private static String texto(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank() || valor.length() > limite) {
            throw new IllegalArgumentException(campo + " é obrigatório e deve ter até " + limite + " caracteres.");
        }
        return valor;
    }
}
