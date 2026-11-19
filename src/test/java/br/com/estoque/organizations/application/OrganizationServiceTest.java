package br.com.estoque.organizations.application;

import br.com.estoque.organizations.domain.OrganizationErrors;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrganizationServiceTest {
    private final OrganizationRepository repository=mock(OrganizationRepository.class);
    private final OrganizationService service=new OrganizationService(repository);

    @Test void validaAntesDePersistir() {
        assertThatThrownBy(()->service.cadastrar("O","SEC","Secretaria","XX")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }
    @Test void retornaErroDeDominioParaCodigoAusente() {
        when(repository.buscar("O")).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.buscar("O")).isInstanceOf(OrganizationErrors.NotFound.class);
    }
    @Test void rejeitaPaginacaoSemLimiteValido() {
        assertThatThrownBy(()->service.listar(null,101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.listar(null,0)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }
}
