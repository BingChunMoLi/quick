package com.bingchunmoli.oauth.server.authorization;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.security.oauth2.core.*;
public final class LocalGrantCoordinator implements GrantCoordinator {
    private static final class Entry { final ReentrantLock lock = new ReentrantLock(); int users; }
    private final ConcurrentHashMap<String, Entry> locks = new ConcurrentHashMap<>();
    private final Duration wait;
    public LocalGrantCoordinator(Duration wait) {
        if (wait == null || wait.isZero() || wait.isNegative()) throw new IllegalArgumentException("Positive grant wait required");
        this.wait = wait;
    }
    public static OAuth2AuthenticationException unavailable() {
        return new OAuth2AuthenticationException(new OAuth2Error("temporarily_unavailable"), "Grant processing unavailable");
    }
    @Override public <T> T execute(String id, Supplier<T> work) {
        Entry entry = locks.compute(id, (key, old) -> { var value = old == null ? new Entry() : old; value.users++; return value; });
        boolean acquired = false;
        try {
            acquired = entry.lock.tryLock(wait.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) throw unavailable();
            return work.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw unavailable();
        } finally {
            if (acquired) entry.lock.unlock();
            locks.computeIfPresent(id, (key, old) -> --old.users == 0 ? null : old);
        }
    }
}
