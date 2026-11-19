package br.com.estoque.shared.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.*;
import static org.mockito.Mockito.*;

class ReadCacheFilterTest {
    @Test void doesNotStoreErrorsOrReadImportEndpointsOrAuthenticatedResponses() throws Exception {
        var cache=mock(ReadResponseCache.class);
        var imports=mock(ObjectProvider.class);
        when(cache.generation()).thenReturn("version");
        var filter=new ReadCacheFilter(cache,imports,true);
        filter.doFilter(new MockHttpServletRequest("GET","/api/itens"),new MockHttpServletResponse(),(req,res)-> {
            ((javax.servlet.http.HttpServletResponse)res).setStatus(404);res.setContentType("application/json");
            res.getWriter().write("{}");
        });
        verify(cache,never()).put(anyString(),anyString(),any(byte[].class),anyString());
        clearInvocations(cache);
        filter.doFilter(new MockHttpServletRequest("GET","/api/imports/abc"),new MockHttpServletResponse(),(req,res)->{});
        var authenticated=new MockHttpServletRequest("GET","/api/itens");
        authenticated.addHeader("Authorization","Bearer test");
        filter.doFilter(authenticated,new MockHttpServletResponse(),(req,res)->{});
        verifyNoInteractions(cache);
    }
}
