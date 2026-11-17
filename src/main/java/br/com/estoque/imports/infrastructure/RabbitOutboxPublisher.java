package br.com.estoque.imports.infrastructure;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

public class RabbitOutboxPublisher {
    private static final Logger LOG=LoggerFactory.getLogger(RabbitOutboxPublisher.class);
    private final ImportStore store;
    private final RabbitTemplate rabbit;
    public RabbitOutboxPublisher(ImportStore store,RabbitTemplate rabbit) {this.store=store;this.rabbit=rabbit;}

    @Scheduled(fixedDelayString="${imports.publish-delay-ms:1000}",initialDelayString="${imports.initial-delay-ms:0}")
    public void publish() {
        for(String id:store.claimDispatch()) {
            try {
                var correlation=new CorrelationData(id+":"+UUID.randomUUID());
                rabbit.convertAndSend("imports.exchange","batch",id,message->{
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setMessageId(id);return message;
                },correlation);
                var confirm=correlation.getFuture().get(5,TimeUnit.SECONDS);
                if(!confirm.isAck()||correlation.getReturned()!=null) throw new IllegalStateException("Mensagem não roteada ou não confirmada.");
            } catch(Exception e) {
                // O lote continua pendente no banco e será publicado novamente após a reserva expirar.
                LOG.warn("Publicação do lote {} será repetida: {}",id,e.getMessage());
            }
        }
    }
}
