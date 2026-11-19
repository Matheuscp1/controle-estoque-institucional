package br.com.estoque.items.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataItemRepository extends JpaRepository<ItemEntity, String> {}
