package com.bingchunmoli.security.jwt;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Local memory session store for single-node use and tests.
 * Close manually created stores to stop their expiry task; Spring manages bean destruction.
 *
 * @author MoLi
 */
public class InMemoryJwtSessionStore implements JwtSessionStore, AutoCloseable {

    private final Map<String, JwtSession> sessions = new ConcurrentHashMap<>();

    private final Map<String, Instant> tokenBlacklist = new ConcurrentHashMap<>();

    private final Clock clock;

    private final ScheduledExecutorService cleanup;

    public InMemoryJwtSessionStore() {
        this(Clock.systemUTC(), Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "quick-jwt-memory-expiry");
            thread.setDaemon(true);
            return thread;
        }));
    }

    InMemoryJwtSessionStore(Clock clock, ScheduledExecutorService cleanup) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
        cleanup.scheduleWithFixedDelay(this::cleanupExpired, 60, 60, TimeUnit.SECONDS);
    }

    @Override
    public void save(JwtSession session) {
        sessions.put(session.getSessionId(), session);
    }

    @Override
    public Optional<JwtSession> findBySessionId(String sessionId) {
        Instant now = clock.instant();
        return Optional.ofNullable(sessions.computeIfPresent(sessionId, (id, session) ->
                expired(session, now) ? null : session));
    }

    @Override
    public Optional<JwtSession> findByTokenId(String tokenId) {
        cleanupExpired();
        return sessions.values().stream()
                .filter(session -> tokenId.equals(session.getTokenId()))
                .findFirst();
    }

    @Override
    public List<JwtSession> findByUserId(String userId) {
        cleanupExpired();
        return sessions.values().stream()
                .filter(session -> session.getUser() != null)
                .filter(session -> userId.equals(session.getUser().getUserId()))
                .toList();
    }

    @Override
    public void refresh(String sessionId, Instant lastAccessAt, Instant activeExpiresAt) {
        refreshIfValid(sessionId, lastAccessAt, activeExpiresAt);
    }

    @Override
    public Optional<JwtSession> refreshIfValid(String sessionId, Instant lastAccessAt, Instant activeExpiresAt) {
        Objects.requireNonNull(lastAccessAt, "lastAccessAt");
        AtomicReference<JwtSession> refreshed = new AtomicReference<>();
        sessions.computeIfPresent(sessionId, (id, session) -> {
            if (expired(session, clock.instant())) {
                return null;
            }
            if (session.getLastAccessAt() == null || lastAccessAt.isAfter(session.getLastAccessAt())) {
                session.setLastAccessAt(lastAccessAt);
                session.setActiveExpiresAt(activeExpiresAt);
            }
            refreshed.set(session);
            return session;
        });
        return Optional.ofNullable(refreshed.get());
    }

    @Override
    public int expireByUserId(String userId) {
        return (int) findByUserId(userId).stream()
                .filter(session -> expireSession(session.getSessionId()))
                .count();
    }

    @Override
    public boolean expireSession(String sessionId) {
        AtomicBoolean expired = new AtomicBoolean();
        sessions.computeIfPresent(sessionId, (id, session) -> {
            session.setExpired(true);
            expired.set(true);
            return null;
        });
        return expired.get();
    }

    @Override
    public void blacklistToken(String tokenId, Instant expiresAt) {
        if (expiresAt.isAfter(clock.instant())) {
            tokenBlacklist.put(tokenId, expiresAt);
        }
    }

    @Override
    public boolean isTokenBlacklisted(String tokenId) {
        Instant expiresAt = tokenBlacklist.get(tokenId);
        if (expiresAt == null) {
            return false;
        }
        if (!expiresAt.isAfter(clock.instant())) {
            tokenBlacklist.remove(tokenId, expiresAt);
            return false;
        }
        return true;
    }

    private void cleanupExpired() {
        Instant now = clock.instant();
        sessions.keySet().forEach(id -> sessions.computeIfPresent(id, (key, session) ->
                expired(session, now) ? null : session));
        tokenBlacklist.forEach((id, expiresAt) -> {
            if (!expiresAt.isAfter(now)) {
                tokenBlacklist.remove(id, expiresAt);
            }
        });
    }

    private boolean expired(JwtSession session, Instant now) {
        return session.isExpired()
                || session.getExpiresAt() != null && !session.getExpiresAt().isAfter(now)
                || session.getActiveExpiresAt() != null && !session.getActiveExpiresAt().isAfter(now);
    }

    @Override
    public void close() {
        cleanup.shutdownNow();
        sessions.clear();
        tokenBlacklist.clear();
    }
}
