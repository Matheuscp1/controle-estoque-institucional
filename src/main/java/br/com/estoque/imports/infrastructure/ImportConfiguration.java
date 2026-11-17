package br.com.estoque.imports.infrastructure;

import br.com.estoque.imports.application.BatchProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import javax.persistence.EntityManager;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="imports.enabled",havingValue="true")
@EntityScan(basePackageClasses=ImportJobEntity.class)
@EnableScheduling
public class ImportConfiguration {
    @Bean ImportStore importStore(EntityManager em,ObjectMapper json) {return new ImportStore(em,json);}
    @Bean BatchProcessor batchProcessor(EntityManager em,ObjectMapper json) {return new JpaBatchProcessor(em,json);}
    @Bean ArchiveStorage archiveStorage(@Value("${imports.directory:./import-data}") String directory) {return new ArchiveStorage(Path.of(directory));}
    @Bean ArchivePreparer archivePreparer(ImportStore store,ArchiveStorage archives) {return new ArchivePreparer(store,archives);}
    @Bean RabbitOutboxPublisher rabbitOutboxPublisher(ImportStore store,RabbitTemplate rabbit) {rabbit.setMandatory(true);return new RabbitOutboxPublisher(store,rabbit);}
    @Bean RabbitBatchListener rabbitBatchListener(BatchProcessor processor,ImportStore store) {return new RabbitBatchListener(processor,store);}
    @Bean ImportFinalizer importFinalizer(EntityManager em) {return new ImportFinalizer(em);}
    @Bean ValidationScheduler validationScheduler(ImportStore store,ImportFinalizer finalizer) {return new ValidationScheduler(store,finalizer);}
    @Bean ThreadPoolTaskScheduler taskScheduler() {var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(3);scheduler.setThreadNamePrefix("imports-");return scheduler;}
    @Bean Declarables importQueues() {
        var exchange=new DirectExchange("imports.exchange",true,false);
        var deadExchange=new DirectExchange("imports.dead",true,false);
        var queue=QueueBuilder.durable("imports.batches").withArgument("x-dead-letter-exchange","imports.dead")
                .withArgument("x-dead-letter-routing-key","failed").build();
        var dead=QueueBuilder.durable("imports.failed").build();
        return new Declarables(exchange,deadExchange,queue,dead,
                BindingBuilder.bind(queue).to(exchange).with("batch"),BindingBuilder.bind(dead).to(deadExchange).with("failed"));
    }
}
