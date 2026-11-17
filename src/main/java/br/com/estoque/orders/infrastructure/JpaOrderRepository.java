package br.com.estoque.orders.infrastructure;

import br.com.estoque.orders.application.*;
import br.com.estoque.orders.domain.*;
import java.time.OffsetDateTime;
import java.util.*;
import javax.persistence.*;
import javax.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class JpaOrderRepository implements OrderRepository {
    private final SpringDataOrderRepository repository;
    private final EntityManager entityManager;
    public JpaOrderRepository(SpringDataOrderRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override public Optional<Order> findById(long id) {
        return repository.findById(id).map(OrderEntity::toDomain);
    }
    @Override public Optional<Order> findByExternalId(String externalId) {
        return repository.findByExternalId(externalId).map(OrderEntity::toDomain);
    }
    @Override public Optional<Order> lockById(long id) {
        // Não faz fetch join sob FOR UPDATE: bloqueia apenas o cabeçalho antes de ler as linhas.
        return Optional.ofNullable(entityManager.find(OrderEntity.class, id, LockModeType.PESSIMISTIC_WRITE))
                .map(OrderEntity::toDomain);
    }
    @Override public Order insertHeader(NewOrder request, OffsetDateTime createdAt) {
        try {
            var entity = repository.saveAndFlush(OrderEntity.header(request, createdAt));
            return Order.confirmed(entity.id(), request, createdAt);
        } catch (DataIntegrityViolationException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState()))
                    throw new DuplicateExternalId(e);
            }
            throw e;
        }
    }
    @Override public void storeItems(Order order) {
        entityManager.find(OrderEntity.class, order.id()).addItems(order.request().items());
        entityManager.flush();
    }
    @Override public void saveCancellation(Order order) {
        entityManager.find(OrderEntity.class, order.id()).cancel(order);
        entityManager.flush();
    }
    @Override public List<Order> list(OrderFilter filter) {
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(OrderEntity.class);
        var root = query.from(OrderEntity.class);
        var predicates = new ArrayList<Predicate>();
        predicates.add(cb.equal(root.get("organizationCode"), filter.organizationCode()));
        if (filter.from() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
        if (filter.to() != null) predicates.add(cb.lessThan(root.get("createdAt"), filter.to()));
        if (filter.status() != null) predicates.add(cb.equal(root.get("status"), filter.status()));
        if (filter.beforeId() != null) predicates.add(cb.lessThan(root.get("id"), filter.beforeId()));
        query.where(predicates.toArray(Predicate[]::new)).orderBy(cb.desc(root.get("id")));
        var page = entityManager.createQuery(query).setMaxResults(filter.limit()).getResultList();
        if (page.isEmpty()) return List.of();
        // Busca as linhas de toda a página em uma consulta, sem paginar uma coleção em memória.
        entityManager.createQuery("select distinct o from OrderEntity o join fetch o.items where o.id in :ids", OrderEntity.class)
                .setParameter("ids", page.stream().map(OrderEntity::id).toList()).getResultList();
        return page.stream().map(OrderEntity::toDomain).toList();
    }
}
