package com.bingchunmoli.oauth.client;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Bounded striped locks avoid a permanent lock entry for every external user. */
public final class LocalRefreshCoordinator implements RefreshCoordinator {
    private final ReentrantLock[] locks = new ReentrantLock[256];
    private final Duration timeout;
    public LocalRefreshCoordinator(Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Positive refresh timeout required");
        this.timeout = timeout;
        for (int i = 0; i < locks.length; i++) locks[i] = new ReentrantLock();
    }
    @Override public Lease acquire(AuthorizedClientRef ref) {
        ReentrantLock lock = locks[(ref.hashCode() & Integer.MAX_VALUE) % locks.length];
        try {
            if (!lock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("OAuth2 refresh coordination timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OAuth2 refresh coordination interrupted");
        }
        return new Lease() {
            @Override public void checkOwned() {
                if (!lock.isHeldByCurrentThread()) throw new IllegalStateException("OAuth2 refresh ownership lost");
            }
            @Override public void close() { lock.unlock(); }
        };
    }
}
