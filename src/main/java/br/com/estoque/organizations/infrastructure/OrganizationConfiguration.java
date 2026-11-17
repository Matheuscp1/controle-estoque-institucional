package br.com.estoque.organizations.infrastructure;

import br.com.estoque.organizations.application.*;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods=false)
@EntityScan(basePackageClasses=OrganizationEntity.class)
@EnableJpaRepositories(basePackageClasses=SpringDataOrganizationRepository.class)
public class OrganizationConfiguration {
    @Bean OrganizationService organizationService(OrganizationRepository repository) {return new OrganizationService(repository);}
}
