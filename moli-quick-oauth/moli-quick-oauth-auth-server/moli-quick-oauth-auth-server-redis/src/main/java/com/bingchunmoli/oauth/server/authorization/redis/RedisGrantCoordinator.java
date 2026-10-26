package com.bingchunmoli.oauth.server.authorization.redis;
import com.bingchunmoli.oauth.server.authorization.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
public final class RedisGrantCoordinator implements GrantCoordinator,AutoCloseable {
    private static final DefaultRedisScript<Long> RENEW=new DefaultRedisScript<>("if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('pexpire',KEYS[1],ARGV[2]) end return 0",Long.class);
    private static final DefaultRedisScript<Long> RELEASE=new DefaultRedisScript<>("if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) end return 0",Long.class);
    private record Lease(String id,String owner,AtomicBoolean lost) {}
    private final ThreadLocal<Lease> current=new ThreadLocal<>();
    private final StringRedisTemplate redis;
    private final String prefix;
    private final Duration wait,lease;
    private final AtomicBoolean closed=new AtomicBoolean();
    private final ScheduledExecutorService renewals=Executors.newSingleThreadScheduledExecutor(r -> {Thread t=new Thread(r,"quick-oauth-grant-renewal");t.setDaemon(true);return t;});
    public RedisGrantCoordinator(StringRedisTemplate redis,String prefix,Duration wait,Duration lease) {
        if(wait==null || wait.isNegative() || wait.isZero() || lease==null || lease.toMillis()<100) throw new IllegalArgumentException("Invalid grant coordination durations");
        this.redis=redis;this.prefix=prefix;this.wait=wait;this.lease=lease;
    }
    String lockKey(String id) { return prefix+":lock:"+id; }
    String owner(String id) {
        Lease active=current.get();
        if(active==null) return "";
        if(!active.id().equals(id)) throw LocalGrantCoordinator.unavailable();
        checkOwned();return active.owner();
    }
    private void checkOpen() {
        if(closed.get()) throw LocalGrantCoordinator.unavailable();
    }
    @Override public void checkOwned() {
        checkOpen();
        Lease active=current.get();
        if(active!=null && (active.lost().get() || !active.owner().equals(redis.opsForValue().get(lockKey(active.id())))))
            throw LocalGrantCoordinator.unavailable();
    }
    @Override public <T> T execute(String id,Supplier<T> work) {
        checkOpen();
        if(current.get()!=null) {
            if(!current.get().id().equals(id)) throw LocalGrantCoordinator.unavailable();
            checkOwned();return work.get();
        }
        String key=lockKey(id),owner=UUID.randomUUID().toString();
        long started=System.nanoTime(),timeout=wait.toNanos();
        try {
            while(true) {
                checkOpen();
                if(System.nanoTime()-started>=timeout) throw LocalGrantCoordinator.unavailable();
                if(Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key,owner,lease))) break;
                if(System.nanoTime()-started>=timeout) throw LocalGrantCoordinator.unavailable();
                Thread.sleep(20);
            }
        } catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw LocalGrantCoordinator.unavailable();}
        Lease acquired=new Lease(id,owner,new AtomicBoolean());
        ScheduledFuture<?> renewal=null;
        try {
            // Shutdown or a slow SET NX may race acquisition; clean up even before renewal is scheduled.
            checkOpen();
            if(System.nanoTime()-started>=timeout) throw LocalGrantCoordinator.unavailable();
            current.set(acquired);
            try {
                renewal=renewals.scheduleAtFixedRate(() -> {
                    try { if(!Long.valueOf(1).equals(redis.execute(RENEW,List.of(key),owner,Long.toString(lease.toMillis())))) acquired.lost().set(true); }
                    catch(RuntimeException failure) {acquired.lost().set(true);}
                },Math.max(25,lease.toMillis()/3),Math.max(25,lease.toMillis()/3),TimeUnit.MILLISECONDS);
            } catch(RejectedExecutionException shutdown) {
                throw LocalGrantCoordinator.unavailable();
            }
            checkOwned();T result=work.get();checkOwned();return result;
        }
        finally {
            if(renewal!=null) renewal.cancel(false);
            current.remove();
            try {redis.execute(RELEASE,List.of(key),owner);} catch(RuntimeException ignored) { /* expiration releases inaccessible leases */ }
        }
    }
    @Override public void close() {if(closed.compareAndSet(false,true)) renewals.shutdownNow();}
}
