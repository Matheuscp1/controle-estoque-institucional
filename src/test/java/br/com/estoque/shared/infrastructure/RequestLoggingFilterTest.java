package br.com.estoque.shared.infrastructure;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.*;
import org.springframework.mock.web.*;
import org.springframework.web.servlet.HandlerMapping;
import static org.assertj.core.api.Assertions.*;

class RequestLoggingFilterTest {
    @Test void logsRouteStatusAndDurationWithoutPayloadAndRestoresContext() throws Exception {
        var logger=(Logger)LoggerFactory.getLogger(RequestLoggingFilter.class);
        var events=new ListAppender<ILoggingEvent>();events.start();logger.addAppender(events);
        MDC.put("requestId","outer");
        try {
            var request=new MockHttpServletRequest("GET","/api/itens/SECRET");
            request.setQueryString("secret=PRIVATE");request.setContent("PRIVATE BODY".getBytes());
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/api/itens/{codigo}");
            var response=new MockHttpServletResponse();
            new RequestLoggingFilter().doFilter(request,response,(req,res)-> {
                assertThat(MDC.get("requestId")).isEqualTo(response.getHeader("X-Request-ID"));
                ((javax.servlet.http.HttpServletResponse)res).setStatus(404);
            });
            assertThat(response.getHeader("X-Request-ID")).isNotBlank();
            assertThat(MDC.get("requestId")).isEqualTo("outer");
            assertThat(events.list).hasSize(1);
            assertThat(events.list.get(0).getFormattedMessage()).contains("route=/api/itens/{codigo}","status=404","durationMs=")
                    .doesNotContain("SECRET","PRIVATE");
        } finally {MDC.clear();logger.detachAppender(events);events.stop();}
    }
    @Test void cleansContextAfterUnexpectedException() {
        MDC.clear();
        assertThatThrownBy(()->new RequestLoggingFilter().doFilter(new MockHttpServletRequest("GET","/api/itens"),
                new MockHttpServletResponse(),(req,res)->{throw new IllegalStateException("failure");}))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get("requestId")).isNull();
    }
}
