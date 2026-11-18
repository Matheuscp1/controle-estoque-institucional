package br.com.estoque.orders.infrastructure;

import br.com.estoque.items.domain.ErroItem;
import br.com.estoque.items.infrastructure.ItemEntity;
import br.com.estoque.orders.application.StockMovement;
import br.com.estoque.orders.domain.*;
import java.util.*;
import javax.persistence.*;
import org.springframework.stereotype.Repository;

@Repository
public class JpaStockMovement implements StockMovement {
    private final EntityManager entityManager;
    public JpaStockMovement(EntityManager entityManager) { this.entityManager = entityManager; }
    @Override public void reserve(List<OrderLine> lines) { move(lines, true); }
    @Override public void release(List<OrderLine> lines) { move(lines, false); }

    private void move(List<OrderLine> lines, boolean reserve) {
        // Todos os fluxos bloqueiam em ordem de código, independentemente da ordem do JSON.
        for (var line : lines.stream().sorted(Comparator.comparing(OrderLine::itemCode)).toList()) {
            var entity = entityManager.find(ItemEntity.class, line.itemCode(), LockModeType.PESSIMISTIC_WRITE);
            if (entity == null) throw new OrderErrors.NotFound("Item não encontrado: " + line.itemCode());
            if (br.com.estoque.imports.infrastructure.ImportQuarantine.contains(entityManager,"ITEM",line.itemCode()))
                throw new OrderErrors.Conflict("Item com pendência na importação: "+line.itemCode());
            try {
                var item = entity.toDomain();
                entity.alterar(reserve ? item.reservar(line.quantity()) : item.devolver(line.quantity()));
            } catch (ErroItem.StockConflict e) { throw new OrderErrors.Conflict(e.getMessage()); }
        }
        entityManager.flush(); // Hibernate incrementa @Version junto com o saldo.
    }
}
