package br.com.estoque.orders.api;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes=OrderContractTest.Context.class, properties={
    "spring.datasource.url=jdbc:h2:mem:orders;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'"})
@AutoConfigureMockMvc
class OrdersH2Test extends OrderContractTest {}
