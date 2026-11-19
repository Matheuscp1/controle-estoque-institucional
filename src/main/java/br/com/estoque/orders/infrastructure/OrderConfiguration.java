package br.com.estoque.orders.infrastructure;

import br.com.estoque.orders.application.*;
import java.time.Clock;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods=false)
@EntityScan(basePackageClasses=OrderEntity.class)
@EnableJpaRepositories(basePackageClasses=SpringDataOrderRepository.class)
public class OrderConfiguration {
    @Bean OrderService orderService(OrderRepository repository, StockMovement stock,
            OrganizationLookup organizations, UnitOfWork transactions) {
        return new OrderService(repository, stock, organizations, transactions, Clock.systemUTC());
    }
}
