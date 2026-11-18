package br.com.estoque.shared.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class RedisReadCacheIntegrationTest {
    @Container static GenericContainer<?> redis=new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    static LettuceConnectionFactory connection;
    static StringRedisTemplate template;
    @BeforeAll static void connect() {
        connection=new LettuceConnectionFactory(redis.getHost(),redis.getMappedPort(6379));
        connection.afterPropertiesSet();template=new StringRedisTemplate(connection);
    }
    @AfterAll static void close() {connection.destroy();}
    private ReadResponseCache cache(long ttl) {return new ReadResponseCache(template,new ObjectMapper(),true,ttl,512);}
    @Test void sharesEntriesAndInvalidationBetweenInstances() {
        var first=cache(2000);var second=cache(2000);first.invalidate();
        String old=first.generation();
        first.put("/api/itens?depois=A",old,new byte[]{1},"application/json");
        assertThat(second.get("/api/itens?depois=A",second.generation()).body()).containsExactly((byte)1);
        assertThat(second.get("/api/itens?depois=B",second.generation())).isNull();
        second.invalidate();
        first.put("/api/itens?depois=A",old,new byte[]{2},"application/json");
        assertThat(first.get("/api/itens?depois=A",first.generation())).isNull();
        assertThat(first.get("/api/itens?depois=A",old)).isNull();
    }
    @Test void expiresAndRejectsLargeResponses() {
        var cache=cache(300);cache.invalidate();String version=cache.generation();
        cache.put("expires",version,new byte[]{1},"application/json");
        assertThat(cache.get("expires",version)).isNotNull();
        await().atMost(Duration.ofSeconds(3)).untilAsserted(()->assertThat(cache.get("expires",version)).isNull());
        cache.put("large",version,new byte[513],"application/json");
        assertThat(cache.get("large",version)).isNull();
    }

    @Test void httpReadsHitCacheWritesInvalidateAndInitialImportBypassesIt() throws Exception {
        var cache=cache(2000);cache.invalidate();
        var store=org.mockito.Mockito.mock(br.com.estoque.imports.infrastructure.ImportStore.class);
        var provider=org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(store);
        org.mockito.Mockito.when(store.ready()).thenReturn(true);
        var filter=new ReadCacheFilter(cache,provider,true);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        javax.servlet.FilterChain handler=(req,res)-> {
            res.setContentType("application/json");res.getWriter().write("{\"value\":"+calls.incrementAndGet()+"}");
        };
        var first=new org.springframework.mock.web.MockHttpServletResponse();
        filter.doFilter(new org.springframework.mock.web.MockHttpServletRequest("GET","/api/itens"),first,handler);
        var hit=new org.springframework.mock.web.MockHttpServletResponse();
        filter.doFilter(new org.springframework.mock.web.MockHttpServletRequest("GET","/api/itens"),hit,handler);
        assertThat(hit.getHeader("X-Cache")).isEqualTo("HIT");
        assertThat(hit.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(calls.get()).isEqualTo(1);
        filter.doFilter(new org.springframework.mock.web.MockHttpServletRequest("POST","/api/pedidos"),new org.springframework.mock.web.MockHttpServletResponse(),(req,res)->{});
        var changed=new org.springframework.mock.web.MockHttpServletResponse();
        filter.doFilter(new org.springframework.mock.web.MockHttpServletRequest("GET","/api/itens"),changed,handler);
        assertThat(changed.getHeader("X-Cache")).isEqualTo("MISS");
        assertThat(calls.get()).isEqualTo(2);
        org.mockito.Mockito.when(store.ready()).thenReturn(false);
        var loading=new org.springframework.mock.web.MockHttpServletResponse();
        filter.doFilter(new org.springframework.mock.web.MockHttpServletRequest("GET","/api/itens"),loading,handler);
        assertThat(loading.getHeader("X-Cache")).isEqualTo("BYPASS");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test void invalidatesOnlyAfterTransactionCommit() {
        var cache=cache(2000);String version=cache.generation();
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            cache.invalidateAfterCommit();
            assertThat(cache.generation()).isEqualTo(version);
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCommit());
            assertThat(cache.generation()).isNotEqualTo(version);
        } finally {org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();}
    }
}
