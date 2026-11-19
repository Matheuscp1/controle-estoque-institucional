package br.com.estoque.orders.infrastructure;

import br.com.estoque.orders.application.UnitOfWork;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringUnitOfWork implements UnitOfWork {
    private final TransactionTemplate transaction;
    public SpringUnitOfWork(PlatformTransactionManager manager) {
        transaction = new TransactionTemplate(manager);
        // Cada caso de uso controla seu commit/rollback, inclusive antes do replay.
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(30);
    }
    @Override public <T> T execute(Supplier<T> work) { return transaction.execute(status -> work.get()); }
}
