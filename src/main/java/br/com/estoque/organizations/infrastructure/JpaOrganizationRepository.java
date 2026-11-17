package br.com.estoque.organizations.infrastructure;

import br.com.estoque.organizations.application.OrganizationRepository;
import br.com.estoque.organizations.domain.*;
import java.util.*;
import javax.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JpaOrganizationRepository implements OrganizationRepository {
    private final SpringDataOrganizationRepository repository;
    private final EntityManager em;
    private final TransactionTemplate transactions;
    public JpaOrganizationRepository(SpringDataOrganizationRepository repository,EntityManager em,PlatformTransactionManager manager) {
        this.repository=repository;this.em=em;this.transactions=new TransactionTemplate(manager);
    }
    @Override public Organization cadastrar(Organization organization) {
        try {return transactions.execute(status->repository.saveAndFlush(OrganizationEntity.from(organization)).toDomain());}
        catch(DataIntegrityViolationException e){throw new OrganizationErrors.DuplicateCode(organization.codigo());}
    }
    @Override @Transactional(readOnly=true)
    public Optional<Organization> buscar(String codigo) {return repository.findById(codigo).map(OrganizationEntity::toDomain);}
    @Override @Transactional(readOnly=true)
    public List<Organization> listar(String depois,int limite) {
        var query=em.createQuery("SELECT o FROM OrganizationEntity o WHERE o.codigo>:cursor ORDER BY o.codigo",OrganizationEntity.class)
                .setParameter("cursor",depois==null?"":depois).setMaxResults(limite);
        return query.getResultList().stream().map(OrganizationEntity::toDomain).toList();
    }
}
