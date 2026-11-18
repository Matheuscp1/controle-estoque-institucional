package br.com.estoque.shared.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

/** Shared, disposable HTTP views. Never used to authorize a stock movement. */
@Component
public class ReadResponseCache {
    private static final Logger LOG=LoggerFactory.getLogger(ReadResponseCache.class);
    private static final String PREFIX="estoque:{http-v1}:";
    private static final String GENERATION=PREFIX+"generation";
    private static final DefaultRedisScript<String> INIT=new DefaultRedisScript<>(
            "local v=redis.call('GET',KEYS[1]); if v then return v end; redis.call('SET',KEYS[1],ARGV[1]); return ARGV[1]",String.class);
    private static final DefaultRedisScript<String> GET=new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('GET',KEYS[2]) end; return nil",String.class);
    private static final DefaultRedisScript<Long> PUT=new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1])==ARGV[1] then redis.call('PSETEX',KEYS[2],ARGV[2],ARGV[3]); return 1 end; return 0",Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final boolean enabled;
    private final long ttlMs;
    private final int maxResponseBytes;
    private final AtomicLong retryAt=new AtomicLong();

    public ReadResponseCache(StringRedisTemplate redis,ObjectMapper json,
                             @Value("${app.read-cache.enabled:true}") boolean enabled,
                             @Value("${app.read-cache.ttl-ms:2000}") long ttlMs,
                             @Value("${app.read-cache.max-response-bytes:262144}") int maxResponseBytes) {
        if(ttlMs<=0||maxResponseBytes<=0)throw new IllegalArgumentException("Cache limits must be positive");
        this.redis=redis;this.json=json;this.enabled=enabled;this.ttlMs=ttlMs;this.maxResponseBytes=maxResponseBytes;
    }
    public String generation() {
        if(!available())return null;
        try {return redis.execute(INIT,List.of(GENERATION),UUID.randomUUID().toString());}
        catch(RuntimeException e) {unavailable(e);return null;}
    }
    public Snapshot get(String key,String expected) {
        if(expected==null||!available())return null;
        try {
            String value=redis.execute(GET,List.of(GENERATION,key(key,expected)),expected);
            return value==null?null:json.readValue(value,Snapshot.class);
        } catch(Exception e) {unavailable(e);return null;}
    }
    public void put(String key,String expected,byte[] body,String contentType) {
        if(expected==null||!available()||body.length>maxResponseBytes)return;
        try {
            redis.execute(PUT,List.of(GENERATION,key(key,expected)),expected,Long.toString(ttlMs),
                    json.writeValueAsString(new Snapshot(body,contentType)));
        } catch(Exception e) {unavailable(e);}
    }
    public void invalidate() {
        if(!available())return;
        try {redis.opsForValue().set(GENERATION,UUID.randomUUID().toString());}
        catch(RuntimeException e) {unavailable(e);}
    }
    public void invalidateAfterCommit() {
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {invalidate();}
            });
        } else invalidate();
    }
    private boolean available() {return enabled&&System.nanoTime()>=retryAt.get();}
    private void unavailable(Exception failure) {
        long now=System.nanoTime(),old=retryAt.get();
        if(now>=old&&retryAt.compareAndSet(old,now+5_000_000_000L))
            LOG.warn("read_cache unavailable; using database; retryInMs=5000 cause={}",failure.getClass().getSimpleName());
    }
    private String key(String key,String version) {
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return PREFIX+version+":"+HexFormat.of().formatHex(hash);
        } catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    public record Snapshot(byte[] body,String contentType) {
        public Snapshot {body=body.clone();}
        @Override public byte[] body() {return body.clone();}
    }
}
