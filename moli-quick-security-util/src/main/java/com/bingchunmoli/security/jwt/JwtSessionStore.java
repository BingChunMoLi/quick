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

    int expireByUserId(String userId);

    boolean expireSession(String sessionId);

    void blacklistToken(String tokenId, Instant expiresAt);

    boolean isTokenBlacklisted(String tokenId);
}
