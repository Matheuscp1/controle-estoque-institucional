package br.com.estoque.items.infrastructure;

import br.com.estoque.items.application.*;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods=false)
@EntityScan(basePackageClasses = ItemEntity.class)
@EnableJpaRepositories(basePackageClasses = SpringDataItemRepository.class)
public class ItemConfiguration {
    @Bean
    ItemService itemService(ItemRepository repository) {
        return new ItemService(repository, Clock.systemUTC());
    }
}
