package br.com.estoque.organizations.application;

import br.com.estoque.organizations.domain.Organization;
import java.util.List;
import java.util.Optional;

public interface OrganizationRepository {
    Organization cadastrar(Organization organization);
    Optional<Organization> buscar(String codigo);
    List<Organization> listar(String depois,int limite);
}
