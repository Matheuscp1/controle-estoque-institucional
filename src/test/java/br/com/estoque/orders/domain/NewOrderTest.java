package br.com.estoque.orders.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NewOrderTest {
    private OrderLine line(String code,String quantity) {return new OrderLine(code,new BigDecimal(quantity));}
    private NewOrder request(List<OrderLine> lines) {return new NewOrder("P","O","Maria",OrderSource.INTEGRACAO,lines);}

    @Test void assinaturaIndependeDaOrdemEDasCasasDecimais() {
        assertThat(request(List.of(line("A","1"),line("B","2"))).signature())
                .isEqualTo(request(List.of(line("B","2.000"),line("A","1.0"))).signature());
    }
    @Test void assinaturaMudaQuandoConteudoMuda() {
        assertThat(request(List.of(line("A","1"))).signature()).isNotEqualTo(request(List.of(line("A","2"))).signature());
    }
    @Test void rejeitaPedidoVazioEItensRepetidos() {
        assertThatThrownBy(()->request(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->request(List.of(line("A","1"),line("A","2")))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejeitaQuantidadeInvalida() {
        for(String value:List.of("0","-1","0.0001","10000000000000000"))
            assertThatThrownBy(()->line("A",value)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void cancelamentoExigeMotivoENaoPodeSerRepetido() {
        var now=OffsetDateTime.parse("2026-01-01T10:00:00Z");
        var order=Order.confirmed(1,request(List.of(line("A","1"))),now);
        assertThatThrownBy(()->order.cancel(" ",now)).isInstanceOf(IllegalArgumentException.class);
        var cancelled=order.cancel("Desistencia",now.plusMinutes(1));
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELADO);
        assertThatThrownBy(()->cancelled.cancel("De novo",now.plusMinutes(2))).isInstanceOf(OrderErrors.Conflict.class);
        assertThatThrownBy(()->order.cancel("Erro",now.minusMinutes(1))).isInstanceOf(OrderErrors.Conflict.class);
    }
}
