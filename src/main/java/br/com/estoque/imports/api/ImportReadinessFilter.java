package br.com.estoque.imports.api;

import br.com.estoque.imports.infrastructure.ImportStore;
import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component @ConditionalOnProperty(name="imports.enabled",havingValue="true")
public class ImportReadinessFilter extends OncePerRequestFilter {
    private final ImportStore store;
    public ImportReadinessFilter(ImportStore store) {this.store=store;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getRequestURI();
        if(path.startsWith("/api/") && !path.equals("/api/imports") && !path.startsWith("/api/imports/") && !store.ready()) {
            response.setStatus(503);response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"mensagem\":\"Carga inicial ainda não concluída e validada.\"}");return;
        }
        chain.doFilter(request,response);
    }
}
