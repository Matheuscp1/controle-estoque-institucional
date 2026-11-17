package br.com.estoque.items.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ItemStockTest {
    private Item item() {return Item.novo("A","Papel","UN",BigDecimal.TEN,OffsetDateTime.now());}
    @Test void reservaNaoPermiteSaldoNegativoOuItemInativo() {
        assertThatThrownBy(()->item().reservar(new BigDecimal("11"))).isInstanceOf(ErroItem.StockConflict.class);
        assertThatThrownBy(()->item().inativar(0).reservar(BigDecimal.ONE)).isInstanceOf(ErroItem.StockConflict.class);
    }
    @Test void devolucaoAceitaItemInativoEPreservaSaldoInicial() {
        var result=item().reservar(new BigDecimal("3")).inativar(1).devolver(new BigDecimal("3"));
        assertThat(result.saldo()).isEqualByComparingTo("10");
        assertThat(result.saldoInicial()).isEqualByComparingTo("10");
        assertThat(result.versao()).isEqualTo(3);
        assertThat(result.ativo()).isFalse();
    }
    @Test void devolucaoNaoPodeExcederCapacidadeDaColuna() {
        var max=item().editar("Papel","UN",new BigDecimal("9999999999999999.999"),true,0);
        assertThatThrownBy(()->max.devolver(BigDecimal.ONE)).isInstanceOf(ErroItem.StockConflict.class);
    }
}
