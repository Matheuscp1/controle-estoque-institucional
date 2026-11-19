package br.com.estoque.imports.api;

import br.com.estoque.imports.infrastructure.ImportStore;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImportReadinessFilterTest {
    @Test void leituraProvisoriaPassaEAlteracoesAguardamValidacao() throws Exception {
        var store=mock(ImportStore.class);var filter=new ImportReadinessFilter(store);
        for(String method:new String[]{"GET","HEAD","OPTIONS"}) {
            var response=new MockHttpServletResponse();var chain=new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest(method,"/api/itens"),response,chain);
            assertThat(chain.getRequest()).isNotNull();
            assertThat(response.getHeader("X-Import-Ready")).isEqualTo("false");
        }
        for(String method:new String[]{"POST","PUT","PATCH","DELETE"}) {
            var response=new MockHttpServletResponse();var chain=new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest(method,"/api/pedidos"),response,chain);
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(chain.getRequest()).isNull();
        }
    }
    @Test void importacaoContinuaDisponivelEValidacaoLiberaEscritas() throws Exception {
        var store=mock(ImportStore.class);var filter=new ImportReadinessFilter(store);
        var chain=new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("POST","/api/imports/abc/reprocess"),new MockHttpServletResponse(),chain);
        assertThat(chain.getRequest()).isNotNull();
        when(store.ready()).thenReturn(true);
        var response=new MockHttpServletResponse();chain=new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("POST","/api/pedidos"),response,chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getHeader("X-Import-Ready")).isEqualTo("true");
    }
}
