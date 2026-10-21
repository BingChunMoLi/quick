package com.bingchunmoli.security.jwt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Server side token/session state store. Replace this interface with a Redis backed bean in distributed deployments.
 *
 * @author MoLi
 */
public interface JwtSessionStore {

    void save(JwtSession session);

    Optional<JwtSession> findBySessionId(String sessionId);

    Optional<JwtSession> findByTokenId(String tokenId);

    List<JwtSession> findByUserId(String userId);

    void refresh(String sessionId, Instant lastAccessAt, Instant activeExpiresAt);

    /**
     * Validates the current session and refreshes it, returning only a live session.
     * Distributed stores should override this with an atomic check/update. The default keeps
     * existing custom implementations compatible and rechecks their state after refresh.
     * Natural expiry must never unconditionally revoke a session from an earlier snapshot.
     */
    default Optional<JwtSession> refreshIfValid(String sessionId, Instant lastAccessAt, Instant activeExpiresAt) {
        Optional<JwtSession> current = findBySessionId(sessionId).filter(session -> isLive(session, Instant.now()));
        if (current.isEmpty()) {
            return Optional.empty();
        }
        refresh(sessionId, lastAccessAt, activeExpiresAt);
        return findBySessionId(sessionId).filter(session -> isLive(session, Instant.now()));
    }

    private static boolean isLive(JwtSession session, Instant now) {
        return !session.isExpired()
                && (session.getExpiresAt() == null || session.getExpiresAt().isAfter(now))
                && (session.getActiveExpiresAt() == null || session.getActiveExpiresAt().isAfter(now));
    }

    int expireByUserId(String userId);

    boolean expireSession(String sessionId);

    void blacklistToken(String tokenId, Instant expiresAt);

    boolean isTokenBlacklisted(String tokenId);
}
