package br.com.estoque.imports.api;

import br.com.estoque.imports.infrastructure.ImportStore;
import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component @ConditionalOnProperty(name="imports.enabled",havingValue="true")
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE+20)
public class ImportReadinessFilter extends OncePerRequestFilter {
    private final ImportStore store;
    public ImportReadinessFilter(ImportStore store) {this.store=store;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        if(path.startsWith("/api/") && !path.equals("/api/imports") && !path.startsWith("/api/imports/")) {
            boolean ready=store.ready();
            response.setHeader("X-Import-Ready",Boolean.toString(ready));
            boolean readOnly=java.util.Set.of("GET","HEAD","OPTIONS").contains(request.getMethod());
            if(!ready&&!readOnly) {
                response.setStatus(503);response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"mensagem\":\"Dados em validação. Consultas disponíveis; alterações aguardam a validação inicial.\"}");
                return;
            }
        }
        chain.doFilter(request,response);
    }
}
