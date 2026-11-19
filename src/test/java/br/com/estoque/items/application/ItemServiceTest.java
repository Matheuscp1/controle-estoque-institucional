package br.com.estoque.items.application;

import br.com.estoque.items.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemServiceTest {
    private final ItemRepository repository = mock(ItemRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);
    private final ItemService service = new ItemService(repository, clock);

    @Test void dadosInvalidosNaoChegamAoBanco() {
        assertThatThrownBy(() -> service.cadastrar("A", "Papel", "UN", new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test void cadastroUsaRelogioInjetado() {
        when(repository.inserir(any())).thenAnswer(inv -> inv.getArgument(0));
        var item = service.cadastrar("A", "Papel", "UN", BigDecimal.TEN);
        assertThat(item.dataCadastro().toInstant()).isEqualTo(clock.instant());
    }

    @Test void conflitoEntreLeituraEGravacaoNaoEhIgnorado() {
        var item = Item.novo("A", "Papel", "UN", BigDecimal.TEN, OffsetDateTime.now(clock));
        when(repository.buscar("A")).thenReturn(Optional.of(item));
        when(repository.atualizar(any(), eq(0L))).thenReturn(false);
        assertThatThrownBy(() -> service.editar("A", "Outro", "UN", BigDecimal.ONE, true, 0))
                .isInstanceOf(ErroItem.Conflito.class);
    }

    @Test void itemAusenteNaoDisparaAtualizacao() {
        when(repository.buscar("A")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.inativar("A", 0)).isInstanceOf(ErroItem.NaoEncontrado.class);
        verify(repository, never()).atualizar(any(), anyLong());
    }
}
