package com.bingchunmoli.oauth.client.redis;

import com.bingchunmoli.oauth.client.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Renewable owner-checked lease. No uncoordinated fallback on timeout or Redis failure. */
public final class RedisRefreshCoordinator implements RefreshCoordinator {
    static String lockKey(AuthorizedClientRef ref) { return "moli:oauth2:{" + TokenSnapshotCodec.key(ref) + "}:refresh"; }
    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('pexpire',KEYS[1],ARGV[2]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end", Long.class);
    private final StringRedisTemplate redis;
    private final String prefix;
    private final Duration wait;
    private final Duration ttl;
    private final ScheduledExecutorService renewals = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "quick-oauth2-refresh-lease"); thread.setDaemon(true); return thread;
    });
    private final ThreadLocal<Ownership> current = new ThreadLocal<>();
    record Ownership(String key, String owner, AtomicBoolean lost) { }
    public RedisRefreshCoordinator(StringRedisTemplate redis, Duration wait, Duration ttl) {
        this(redis, wait, ttl, "moli:oauth2");
    }
    public RedisRefreshCoordinator(StringRedisTemplate redis, Duration wait, Duration ttl, String prefix) {
        if (wait.isNegative() || wait.isZero() || ttl.toMillis() < 300) throw new IllegalArgumentException("Invalid OAuth2 refresh timeouts");
        this.redis = redis; this.wait = wait; this.ttl = ttl; this.prefix = prefix;
    }
    Ownership ownership() { return current.get(); }
    @Override public Lease acquire(AuthorizedClientRef ref) {
        if (current.get() != null) throw new IllegalStateException("Nested OAuth2 refresh coordination is unsupported");
        String key = prefix + ":{" + TokenSnapshotCodec.key(ref) + "}:refresh", owner = UUID.randomUUID().toString();
        long end = System.nanoTime() + wait.toNanos();
        while (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, owner, ttl))) {
            if (System.nanoTime() >= end) throw new IllegalStateException("OAuth2 refresh coordination timed out");
            try { Thread.sleep(20); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("OAuth2 refresh coordination interrupted"); }
        }
        Ownership ownership = new Ownership(key, owner, new AtomicBoolean());
        current.set(ownership);
        ScheduledFuture<?> renewal = renewals.scheduleAtFixedRate(() -> {
            try {
                Long result = redis.execute(RENEW, List.of(key), owner, String.valueOf(ttl.toMillis()));
                if (!Long.valueOf(1).equals(result)) ownership.lost().set(true);
            } catch (RuntimeException failure) { ownership.lost().set(true); }
        }, ttl.toMillis() / 3, ttl.toMillis() / 3, TimeUnit.MILLISECONDS);
        return new Lease() {
            @Override public void checkOwned() {
                if (ownership.lost().get() || !owner.equals(redis.opsForValue().get(key))) {
                    ownership.lost().set(true);
                    throw new IllegalStateException("OAuth2 refresh ownership lost");
                }
            }
            @Override public void close() {
                renewal.cancel(false);
                current.remove();
                redis.execute(RELEASE, List.of(key), owner);
            }
        };
    }
    @Override public void close() { renewals.shutdownNow(); }
}
