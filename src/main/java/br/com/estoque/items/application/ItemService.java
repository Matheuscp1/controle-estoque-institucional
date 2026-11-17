package br.com.estoque.items.application;

import br.com.estoque.items.domain.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

public class ItemService {
    private final ItemRepository repository;
    private final Clock clock;

    public ItemService(ItemRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Item cadastrar(String codigo, String descricao, String unidade, BigDecimal saldo) {
        return repository.inserir(Item.novo(codigo, descricao, unidade, saldo, OffsetDateTime.now(clock)));
    }

    public Item buscar(String codigo) {
        return repository.buscar(codigo).orElseThrow(() -> new ErroItem.NaoEncontrado(codigo));
    }

    public List<Item> listar(FiltroItens filtro) { return repository.listar(filtro); }

    public Item editar(String codigo, String descricao, String unidade, BigDecimal saldo, boolean ativo, long versao) {
        return salvar(buscar(codigo).editar(descricao, unidade, saldo, ativo, versao), versao);
    }

    public Item inativar(String codigo, long versao) {
        return salvar(buscar(codigo).inativar(versao), versao);
    }

    private Item salvar(Item item, long versaoEsperada) {
        if (!repository.atualizar(item, versaoEsperada)) throw new ErroItem.Conflito();
        return item;
    }
}
