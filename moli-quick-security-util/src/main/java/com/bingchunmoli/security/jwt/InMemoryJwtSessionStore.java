package com.bingchunmoli.security.jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local memory session store for single-node use and tests.
 *
 * @author MoLi
 */
public class InMemoryJwtSessionStore implements JwtSessionStore {

    private final Map<String, JwtSession> sessions = new ConcurrentHashMap<>();

    private final Map<String, Instant> tokenBlacklist = new ConcurrentHashMap<>();

    @Override
    public void save(JwtSession session) {
        sessions.put(session.getSessionId(), session);
    }

    @Override
    public Optional<JwtSession> findBySessionId(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public Optional<JwtSession> findByTokenId(String tokenId) {
        return sessions.values().stream()
                .filter(session -> tokenId.equals(session.getTokenId()))
                .findFirst();
    }

    @Override
    public List<JwtSession> findByUserId(String userId) {
        return sessions.values().stream()
                .filter(session -> session.getUser() != null)
                .filter(session -> userId.equals(session.getUser().getUserId()))
                .toList();
    }

    @Override
    public void refresh(String sessionId, Instant lastAccessAt, Instant activeExpiresAt) {
        findBySessionId(sessionId).ifPresent(session -> {
            session.setLastAccessAt(lastAccessAt);
            session.setActiveExpiresAt(activeExpiresAt);
        });
    }

    @Override
    public int expireByUserId(String userId) {
        List<JwtSession> userSessions = findByUserId(userId);
        userSessions.forEach(session -> session.setExpired(true));
        return userSessions.size();
    }

    @Override
    public boolean expireSession(String sessionId) {
        return findBySessionId(sessionId)
                .map(session -> {
                    session.setExpired(true);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public void blacklistToken(String tokenId, Instant expiresAt) {
        tokenBlacklist.put(tokenId, expiresAt);
    }

    @Override
    public boolean isTokenBlacklisted(String tokenId) {
        Instant expiresAt = tokenBlacklist.get(tokenId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            tokenBlacklist.remove(tokenId);
            return false;
        }
        return true;
    }
}
