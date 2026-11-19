package br.com.estoque.shared.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReadResponseCacheTest {
    @Test void unavailableRedisFallsBackWithoutFailingRequest() {
        var redis=mock(StringRedisTemplate.class);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),anyString()))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("offline"));
        var cache=new ReadResponseCache(redis,new ObjectMapper(),true,2000,262144);
        assertThat(cache.generation()).isNull();
        assertThat(cache.get("/api/itens",null)).isNull();
        cache.put("/api/itens",null,new byte[]{1},"application/json");
        cache.invalidate();
        verify(redis,times(1)).execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),anyString());
        verifyNoMoreInteractions(redis);
    }
    @Test void disabledCacheDoesNotConnectToRedis() {
        var redis=mock(StringRedisTemplate.class);
        var cache=new ReadResponseCache(redis,new ObjectMapper(),false,2000,262144);
        assertThat(cache.generation()).isNull();cache.invalidate();verifyNoInteractions(redis);
    }
}
