package br.com.estoque.orders.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface SpringDataOrderRepository extends JpaRepository<OrderEntity, Long> {
    @EntityGraph(attributePaths="items")
    Optional<OrderEntity> findByExternalId(String externalId);
}
