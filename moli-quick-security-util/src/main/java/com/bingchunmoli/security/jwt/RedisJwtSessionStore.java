package com.bingchunmoli.security.jwt;

import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis backed JWT session store.
 *
 * @author MoLi
 */
public class RedisJwtSessionStore implements JwtSessionStore {

    private final RedisTemplate<String, Object> redisTemplate;

    private final JwtTokenProperties properties;

    private final String keyPrefix;

    public RedisJwtSessionStore(RedisTemplate<String, Object> redisTemplate, JwtTokenProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.keyPrefix = normalizePrefix(properties.getRedisKeyPrefix());
    }

    @Override
    public void save(JwtSession session) {
        setObject(sessionKey(session.getSessionId()), session, sessionTtlSeconds(session));
        setObject(tokenKey(session.getTokenId()), session.getSessionId(), sessionTtlSeconds(session));
        redisTemplate.opsForSet().add(userSessionsKey(session.getUser().getUserId()), session.getSessionId());
        redisTemplate.expire(userSessionsKey(session.getUser().getUserId()), sessionTtlSeconds(session), TimeUnit.SECONDS);
    }

    @Override
    public Optional<JwtSession> findBySessionId(String sessionId) {
        return Optional.ofNullable((JwtSession) redisTemplate.opsForValue().get(sessionKey(sessionId)));
    }

    @Override
    public Optional<JwtSession> findByTokenId(String tokenId) {
        String sessionId = (String) redisTemplate.opsForValue().get(tokenKey(tokenId));
        return sessionId == null ? Optional.empty() : findBySessionId(sessionId);
    }

    @Override
    public List<JwtSession> findByUserId(String userId) {
        Collection<Object> sessionIds = redisTemplate.opsForSet().members(userSessionsKey(userId));
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        return sessionIds.stream()
                .map(String::valueOf)
                .map(this::findBySessionId)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public void refresh(String sessionId, Instant lastAccessAt, Instant activeExpiresAt) {
        findBySessionId(sessionId).ifPresent(session -> {
            session.setLastAccessAt(lastAccessAt);
            session.setActiveExpiresAt(activeExpiresAt);
            setObject(sessionKey(sessionId), session, sessionTtlSeconds(session));
        });
    }

    @Override
    public int expireByUserId(String userId) {
        List<JwtSession> sessions = findByUserId(userId);
        sessions.forEach(session -> expireSession(session.getSessionId()));
        return sessions.size();
    }

    @Override
    public boolean expireSession(String sessionId) {
        Optional<JwtSession> optionalSession = findBySessionId(sessionId);
        if (optionalSession.isEmpty()) {
            return false;
        }
        JwtSession session = optionalSession.get();
        session.setExpired(true);
        setObject(sessionKey(sessionId), session, sessionTtlSeconds(session));
        return true;
    }

    @Override
    public void blacklistToken(String tokenId, Instant expiresAt) {
        long ttlSeconds = Math.max(Duration.between(Instant.now(), expiresAt).getSeconds(), 1);
        setObject(blacklistKey(tokenId), Boolean.TRUE, ttlSeconds);
    }

    @Override
    public boolean isTokenBlacklisted(String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().get(blacklistKey(tokenId)));
    }

    private void setObject(String key, Object value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, Math.min(ttlSeconds, Integer.MAX_VALUE), TimeUnit.SECONDS);
    }

    private long sessionTtlSeconds(JwtSession session) {
        Instant expiresAt = session.getExpiresAt() == null ? Instant.now().plus(properties.getTimeout()) : session.getExpiresAt();
        return Math.max(Duration.between(Instant.now(), expiresAt).getSeconds(), 1);
    }

    private String sessionKey(String sessionId) {
        return keyPrefix + ":session:" + sessionId;
    }

    private String tokenKey(String tokenId) {
        return keyPrefix + ":token:" + tokenId;
    }

    private String userSessionsKey(String userId) {
        return keyPrefix + ":user:" + userId + ":sessions";
    }

    private String blacklistKey(String tokenId) {
        return keyPrefix + ":blacklist:" + tokenId;
    }

    private String normalizePrefix(String prefix) {
        return prefix == null || prefix.isBlank() ? "moli:security:jwt" : prefix;
    }
}
