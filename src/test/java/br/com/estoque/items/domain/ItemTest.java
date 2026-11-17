package br.com.estoque.items.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ItemTest {
    private final OffsetDateTime agora = OffsetDateTime.parse("2026-11-17T12:00:00Z");

    @Test void cadastroNormalizaUnidadeEPreservaSaldoInicial() {
        var item = Item.novo("A", "Papel", " un ", new BigDecimal("10.125"), agora);
        assertThat(item.unidade()).isEqualTo("UN");
        assertThat(item.saldoInicial()).isEqualByComparingTo("10.125");
        assertThat(item.ativo()).isTrue();
        assertThat(item.versao()).isZero();
    }

    @Test void rejeitaSaldoNegativoPrecisaoExcessivaECamposVazios() {
        for (String saldo : new String[]{"-1", "0.0001", "10000000000000000"}) {
            assertThatThrownBy(() -> Item.novo("A", "Papel", "UN", new BigDecimal(saldo), agora))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> Item.novo("A", " ", "UN", BigDecimal.ZERO, agora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Item.novo("A", "Papel", null, BigDecimal.ZERO, agora))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void edicaoPreservaIdentidadeSaldoInicialEData() {
        var inicial = Item.novo("A", "Papel", "UN", BigDecimal.TEN, agora);
        var editado = inicial.editar("Papel A4", "cx", BigDecimal.ONE, true, 0);
        assertThat(editado.codigo()).isEqualTo("A");
        assertThat(editado.saldoInicial()).isEqualByComparingTo("10");
        assertThat(editado.saldo()).isEqualByComparingTo("1");
        assertThat(editado.dataCadastro()).isEqualTo(agora);
        assertThat(editado.versao()).isEqualTo(1);
    }

    @Test void edicaoDesatualizadaEInativacaoNaoPodemIgnorarVersao() {
        var item = Item.novo("A", "Papel", "UN", BigDecimal.TEN, agora).inativar(0);
        assertThat(item.ativo()).isFalse();
        assertThat(item.saldo()).isEqualByComparingTo("10");
        assertThatThrownBy(() -> item.editar("Outro", "UN", BigDecimal.ONE, true, 0))
                .isInstanceOf(ErroItem.Conflito.class);
        assertThatThrownBy(() -> item.inativar(0)).isInstanceOf(ErroItem.Conflito.class);
    }
}
