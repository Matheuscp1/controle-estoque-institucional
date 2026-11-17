package br.com.estoque.items.infrastructure;

import br.com.estoque.items.domain.Item;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import javax.persistence.*;

@Entity
@Table(name = "itens")
public class ItemEntity {
    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String codigo;

    @Column(length = 500, nullable = false)
    private String descricao;

    @Column(length = 30, nullable = false)
    private String unidade;

    @Column(name = "saldo_inicial", precision = 19, scale = 3, nullable = false, updatable = false)
    private BigDecimal saldoInicial;

    @Column(precision = 19, scale = 3, nullable = false)
    private BigDecimal saldo;

    @Column(nullable = false)
    private boolean ativo;

    @Version
    @Column(nullable = false)
    private Long versao;

    @Column(name = "data_cadastro", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime dataCadastro;

    protected ItemEntity() {}

    static ItemEntity nova(Item item) {
        var entity = new ItemEntity();
        entity.codigo = item.codigo();
        entity.descricao = item.descricao();
        entity.unidade = item.unidade();
        entity.saldoInicial = item.saldoInicial();
        entity.saldo = item.saldo();
        entity.ativo = item.ativo();
        entity.dataCadastro = item.dataCadastro();
        // Versão nula identifica uma entidade nova, mesmo com código atribuído.
        // O Hibernate inicializa @Version em zero ao inserir.
        return entity;
    }

    public Item toDomain() {
        return new Item(codigo, descricao, unidade, saldoInicial, saldo, ativo,
                versao == null ? 0 : versao, dataCadastro);
    }

    /** Atualiza apenas campos editáveis; a versão pertence ao Hibernate. */
    public boolean alterar(Item item) {
        boolean mudou = !descricao.equals(item.descricao()) || !unidade.equals(item.unidade())
                || saldo.compareTo(item.saldo()) != 0 || ativo != item.ativo();
        descricao = item.descricao();
        unidade = item.unidade();
        saldo = item.saldo();
        ativo = item.ativo();
        return mudou;
    }
}
