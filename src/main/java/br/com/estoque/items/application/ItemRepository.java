package br.com.estoque.items.application;

import br.com.estoque.items.domain.Item;
import java.util.List;
import java.util.Optional;

public interface ItemRepository {
    Optional<Item> buscar(String codigo);
    List<Item> listar(FiltroItens filtro);
    Item inserir(Item item);
    /** Atualização atômica: false quando a versão esperada não corresponde à persistida. */
    boolean atualizar(Item item, long versaoEsperada);
}
