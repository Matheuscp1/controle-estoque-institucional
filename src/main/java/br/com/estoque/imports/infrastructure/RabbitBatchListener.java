package br.com.estoque.imports.infrastructure;

import br.com.estoque.imports.application.BatchProcessor;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class RabbitBatchListener {
    private final BatchProcessor processor;
    private final ImportStore store;
    public RabbitBatchListener(BatchProcessor processor,ImportStore store) {this.processor=processor;this.store=store;}

    @RabbitListener(queues="imports.batches",ackMode="MANUAL",concurrency="${imports.consumers:2}")
    public void receive(Message message,Channel channel) throws IOException {
        String id=new String(message.getBody(),StandardCharsets.UTF_8);
        long tag=message.getMessageProperties().getDeliveryTag();
        if(!id.matches("[a-f0-9]{64}")) {channel.basicReject(tag,false);return;}
        try {processor.process(id);}
        catch(Exception e) {
            // Se o registro da falha também falhar, a exceção mantém a entrega sem confirmação.
            store.failedAttempt(id,e.getMessage());
            channel.basicReject(tag,false);return;
        }
        // O proxy transacional de process() já realizou o commit neste ponto.
        channel.basicAck(tag,false);
    }
}
