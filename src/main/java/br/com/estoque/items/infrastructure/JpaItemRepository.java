package br.com.estoque.items.infrastructure;

import br.com.estoque.items.application.*;
import br.com.estoque.items.domain.*;
import java.util.*;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JpaItemRepository implements ItemRepository {
    private final SpringDataItemRepository repository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactions;

    public JpaItemRepository(SpringDataItemRepository repository, EntityManager entityManager,
                             PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Item> buscar(String codigo) {
        return repository.findById(codigo).map(ItemEntity::toDomain);
    }

    @Override
    public Item inserir(Item item) {
        try {
            return transactions.execute(status -> repository.saveAndFlush(ItemEntity.nova(item)).toDomain());
        } catch (DataIntegrityViolationException e) {
            throw new ErroItem.CodigoDuplicado(item.codigo());
        }
    }

    @Override
    public boolean atualizar(Item item, long versaoEsperada) {
        try {
            return Boolean.TRUE.equals(transactions.execute(status -> {
                var existente = repository.findById(item.codigo());
                if (existente.isEmpty() || existente.get().toDomain().versao() != versaoEsperada) return false;
                var entity = existente.get();
                if (!entity.alterar(item)) {
                    // Mantém a regra de avançar a versão mesmo numa gravação sem mudanças.
                    entityManager.lock(entity, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
                }
                repository.flush();
                return true;
            }));
        } catch (OptimisticLockingFailureException e) {
            // A tradução ocorre depois do rollback, inclusive se a falha surgir no commit.
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Item> listar(FiltroItens filtro) {
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(ItemEntity.class);
        var item = query.from(ItemEntity.class);
        List<Predicate> predicates = new ArrayList<>();
        if (filtro.descricao() != null) {
            String descricao = filtro.descricao().toLowerCase(Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_");
            predicates.add(cb.like(cb.lower(item.get("descricao")), "%" + descricao + "%", '!'));
        }
        if (filtro.unidade() != null) predicates.add(cb.equal(item.get("unidade"), filtro.unidade()));
        if (filtro.saldoMin() != null) predicates.add(cb.greaterThanOrEqualTo(item.get("saldo"), filtro.saldoMin()));
        if (filtro.saldoMax() != null) predicates.add(cb.lessThanOrEqualTo(item.get("saldo"), filtro.saldoMax()));
        if (filtro.depois() != null) predicates.add(cb.greaterThan(item.get("codigo"), filtro.depois()));
        query.where(predicates.toArray(Predicate[]::new)).orderBy(cb.asc(item.get("codigo")));
        // Consulta limitada sem COUNT(*) de toda a base em cada página.
        return entityManager.createQuery(query).setMaxResults(filtro.limite()).getResultList().stream()
                .map(ItemEntity::toDomain).toList();
    }
}
