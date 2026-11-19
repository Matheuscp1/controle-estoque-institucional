package br.com.estoque.shared.infrastructure;

import br.com.estoque.imports.infrastructure.ImportStore;
import java.io.IOException;
import java.util.Set;
import javax.servlet.*;
import javax.servlet.http.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+30)
public class ReadCacheFilter extends OncePerRequestFilter {
    private final ReadResponseCache cache;
    private final ObjectProvider<ImportStore> imports;
    private final boolean enabled;
    public ReadCacheFilter(ReadResponseCache cache,ObjectProvider<ImportStore> imports,
                           @Value("${app.read-cache.enabled:true}") boolean enabled) {
        this.cache=cache;this.imports=imports;this.enabled=enabled;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        if(path.startsWith("/api/")&&!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())) {
            try {chain.doFilter(request,response);} finally {if(enabled)cache.invalidate();}
            return;
        }
        if(!enabled||!request.getMethod().equals("GET")||!cacheable(path)
                ||request.getHeader("Authorization")!=null||request.getUserPrincipal()!=null
                ||request.getHeader("Cookie")!=null) {
            chain.doFilter(request,response);return;
        }
        var store=imports.getIfAvailable();
        if(store!=null&&!store.ready()) {
            response.setHeader("X-Cache","BYPASS");chain.doFilter(request,response);return;
        }
        String key=path+"?"+request.getQueryString()+"\n"+request.getHeader("Accept");
        String generation=cache.generation();
        if(generation==null) {
            response.setHeader("X-Cache","BYPASS");chain.doFilter(request,response);return;
        }
        var hit=cache.get(key,generation);
        if(hit!=null) {
            response.setHeader("X-Cache","HIT");response.setContentType(hit.contentType());
            response.getOutputStream().write(hit.body());return;
        }
        response.setHeader("X-Cache","MISS");
        var wrapped=new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request,wrapped);
            if(wrapped.getStatus()==200&&wrapped.getContentType()!=null
                    &&wrapped.getContentType().startsWith("application/json")
                    &&wrapped.getHeader("Set-Cookie")==null)
                cache.put(key,generation,wrapped.getContentAsByteArray(),wrapped.getContentType());
        } finally {wrapped.copyBodyToResponse();}
    }
    private boolean cacheable(String path) {
        return path.matches("/api/itens(?:/[^/]+)?")||path.matches("/api/orgaos(?:/[^/]+(?:/pedidos)?)?")
                ||path.matches("/api/pedidos/[^/]+");
    }
}
