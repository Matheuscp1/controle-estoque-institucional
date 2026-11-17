package br.com.estoque.imports.domain;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LegacyRowTest {
    @Test void normalizaUnidadeSemDebitarSaldoHistorico() {
        var result = LegacyRow.parse("itens.csv", Map.of("codigo","A","descricao","Papel","unidade"," un ",
                "saldo_inicial","10","saldo_atual","7","ativo","S","data_cadastro","2020-01-01"));
        assertThat(result.get("unidade")).isEqualTo("UN");
        assertThat(result.get("saldo_atual")).isEqualTo("7");
    }

    @Test void rejeitaQuantidadeNegativaSemAproveitarParteDoPedido() {
        assertThatThrownBy(() -> LegacyRow.parse("pedido_itens.csv", Map.of(
                "id_externo_pedido","P","codigo_item","A","quantidade","-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void naoInventaDadosDeCancelamento() {
        assertThatThrownBy(() -> LegacyRow.parse("pedidos.csv", Map.of("id_externo","P","codigo_orgao","O",
                "solicitante","Maria","origem","PORTAL","data_hora","2020-01-01T10:00:00",
                "situacao","CANCELADO","data_cancelamento","","motivo_cancelamento","")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void datasLegadasRecebemFusoExplicito() {
        var parsed = LegacyRow.parse("pedidos.csv", Map.of("id_externo","P","codigo_orgao","O",
                "solicitante","Maria","origem","PORTAL","data_hora","2020-01-01T10:00:00",
                "situacao","CONFIRMADO","data_cancelamento","","motivo_cancelamento",""));
        assertThat(parsed.get("data_hora")).isEqualTo("2020-01-01T10:00-03:00");
    }
}
