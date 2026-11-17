package br.com.estoque.orders.infrastructure;

import br.com.estoque.orders.application.OrganizationLookup;
import br.com.estoque.organizations.infrastructure.OrganizationEntity;
import javax.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class JpaOrganizationLookup implements OrganizationLookup {
    private final EntityManager entityManager;
    public JpaOrganizationLookup(EntityManager entityManager) { this.entityManager = entityManager; }
    @Override public boolean exists(String code) { return entityManager.find(OrganizationEntity.class, code) != null; }
}
