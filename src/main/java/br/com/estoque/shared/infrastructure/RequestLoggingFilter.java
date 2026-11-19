package br.com.estoque.shared.infrastructure;

import java.io.IOException;
import java.util.UUID;
import javax.servlet.*;
import javax.servlet.http.*;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+10)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger LOG=LoggerFactory.getLogger(RequestLoggingFilter.class);
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String previous=MDC.get("requestId");
        String id=UUID.randomUUID().toString();
        MDC.put("requestId",id);response.setHeader("X-Request-ID",id);
        long started=System.nanoTime();boolean failed=false;
        try {chain.doFilter(request,response);}
        catch(IOException|ServletException|RuntimeException e) {failed=true;throw e;}
        finally {
            int status=failed?500:response.getStatus();
            Object route=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String pattern=route==null?"[unmapped-or-cached]":route.toString();
            String format="http method={} route={} status={} durationMs={} cache={}";
            Object[] args={request.getMethod(),pattern,status,(System.nanoTime()-started)/1_000_000,response.getHeader("X-Cache")};
            try {
                if(status>=500) LOG.error(format,args);
                else if(status>=400) LOG.warn(format,args);
                else LOG.info(format,args);
            } finally {
                if(previous==null)MDC.remove("requestId");else MDC.put("requestId",previous);
            }
        }
    }
}
