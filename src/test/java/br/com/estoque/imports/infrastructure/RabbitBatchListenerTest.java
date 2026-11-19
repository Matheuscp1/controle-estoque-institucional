package br.com.estoque.imports.infrastructure;

import br.com.estoque.imports.application.BatchProcessor;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RabbitBatchListenerTest {
    final BatchProcessor processor=mock(BatchProcessor.class);
    final ImportStore store=mock(ImportStore.class);
    final Channel channel=mock(Channel.class);
    final RabbitBatchListener listener=new RabbitBatchListener(processor,store);
    final String id="a".repeat(64);
    Message message() {
        var properties=new MessageProperties();properties.setDeliveryTag(42);
        return new Message(id.getBytes(StandardCharsets.UTF_8),properties);
    }
    @Test void confirmaSomenteDepoisDeProcessar() throws Exception {
        listener.receive(message(),channel);
        var order=inOrder(processor,channel);
        order.verify(processor).process(id);order.verify(channel).basicAck(42,false);
        verifyNoInteractions(store);
    }
    @Test void falhaRegistraTentativaERejeitaSemConfirmar() throws Exception {
        doThrow(new IllegalStateException("Banco indisponível")).when(processor).process(id);
        listener.receive(message(),channel);
        var order=inOrder(processor,store,channel);
        order.verify(processor).process(id);order.verify(store).failedAttempt(id,"Banco indisponível");
        order.verify(channel).basicReject(42,false);
        verify(channel,never()).basicAck(anyLong(),anyBoolean());
    }
    @Test void falhaAoRegistrarTentativaNaoConfirmaMensagem() {
        doThrow(new IllegalStateException("Falha")).when(processor).process(id);
        doThrow(new IllegalStateException("Banco indisponível")).when(store).failedAttempt(id,"Falha");
        assertThatThrownBy(()->listener.receive(message(),channel)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(channel);
    }
}
