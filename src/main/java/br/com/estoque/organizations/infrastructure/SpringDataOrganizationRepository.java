package br.com.estoque.organizations.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOrganizationRepository extends JpaRepository<OrganizationEntity,String> {}
