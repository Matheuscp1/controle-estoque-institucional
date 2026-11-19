package br.com.estoque.items.application;

import br.com.estoque.items.domain.Item;
import br.com.estoque.shared.application.PageResult;
import java.util.Optional;

public interface ItemRepository {
    Optional<Item> buscar(String codigo);
    PageResult<Item> listar(FiltroItens filtro);
    Item inserir(Item item);
    /** Atualização atômica: false quando a versão esperada não corresponde à persistida. */
    boolean atualizar(Item item, long versaoEsperada);
}
