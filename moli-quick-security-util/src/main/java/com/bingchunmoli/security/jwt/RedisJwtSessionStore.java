package com.bingchunmoli.security.jwt;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis backed JWT session store.
 *
 * @author MoLi
 */
public class RedisJwtSessionStore implements JwtSessionStore {

    private static final String INDEX_MEMBER_PREFIX = "!quick-jwt-v2!";
    private static final int REFRESH_ATTEMPTS = 16;
    private record IndexMember(String sessionId, Instant expiresAt) { }

    private static final RedisScript<Long> REFRESH_SESSION = RedisScript.of(
            "if redis.call('get', KEYS[1]) ~= ARGV[1] then return 0 end "
                    + "local ttl = redis.call('pttl', KEYS[1]) "
                    + "if ttl == -2 then return 0 end "
                    + "if ttl >= 0 then redis.call('psetex', KEYS[1], math.max(ttl, 1), ARGV[2]) "
                    + "else redis.call('set', KEYS[1], ARGV[2]) end return 1", Long.class);

    private static final RedisScript<Long> INDEX_SESSION = RedisScript.of(
            "local existed = redis.call('exists', KEYS[1]) "
                    + "redis.call('sadd', KEYS[1], ARGV[1]) "
                    + "local ttl = redis.call('pttl', KEYS[1]) "
                    + "local requested = tonumber(ARGV[2]) "
                    + "if existed == 0 or (ttl >= 0 and ttl < requested) then "
                    + "redis.call('pexpire', KEYS[1], requested) end return 1", Long.class);

    private final RedisTemplate<String, Object> redisTemplate;

    private final JwtTokenProperties properties;

    private final String keyPrefix;

    private final Clock clock;

    public RedisJwtSessionStore(RedisTemplate<String, Object> redisTemplate, JwtTokenProperties properties) {
        this(redisTemplate, properties, Clock.systemUTC());
    }

    RedisJwtSessionStore(RedisTemplate<String, Object> redisTemplate, JwtTokenProperties properties, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.keyPrefix = normalizePrefix(properties.getRedisKeyPrefix());
        this.clock = clock;
    }

    @Override
    public void save(JwtSession session) {
        String userKey = userSessionsKey(session.getUser().getUserId());
        Set<Object> previousMembers = redisTemplate.opsForSet().members(userKey);
        setObject(sessionKey(session.getSessionId()), session, sessionTtlSeconds(session));
        setObject(tokenKey(session.getTokenId()), session.getSessionId(), sessionTtlSeconds(session));
        indexSession(session);
        pruneIndex(userKey, previousMembers, session.getSessionId());
    }

    @SuppressWarnings("unchecked")
    private void indexSession(JwtSession session) {
        // Keep set members in the host template's existing format, including non-JSON serializers.
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();
        Instant expiration = session.getExpiresAt() == null ? clock.instant().plus(properties.getTimeout()) : session.getExpiresAt();
        String memberValue = INDEX_MEMBER_PREFIX
                + Base64.getUrlEncoder().withoutPadding().encodeToString(session.getSessionId().getBytes(StandardCharsets.UTF_8))
                + "|" + expiration + "|" + UUID.randomUUID();
        byte[] member = serializer.serialize(memberValue);
        byte[] ttl = Long.toString(sessionTtlSeconds(session) * 1000).getBytes(StandardCharsets.UTF_8);
        Long result = redisTemplate.execute(INDEX_SESSION, RedisSerializer.byteArray(), null,
                List.of(userSessionsKey(session.getUser().getUserId())), member, ttl);
        if (!Long.valueOf(1).equals(result)) {
            throw new IllegalStateException("Redis JWT session indexing returned no result");
        }
    }

    @Override
    public Optional<JwtSession> findBySessionId(String sessionId) {
        JwtSession session = (JwtSession) redisTemplate.opsForValue().get(sessionKey(sessionId));
        if (session != null && Boolean.TRUE.equals(redisTemplate.opsForValue().get(revocationKey(sessionId)))) {
            session.setExpired(true);
        }
        return Optional.ofNullable(session);
    }

    @Override
    public Optional<JwtSession> findByTokenId(String tokenId) {
        String sessionId = (String) redisTemplate.opsForValue().get(tokenKey(tokenId));
        return sessionId == null ? Optional.empty() : findBySessionId(sessionId);
    }

    @Override
    public List<JwtSession> findByUserId(String userId) {
        String userKey = userSessionsKey(userId);
        Set<Object> members = redisTemplate.opsForSet().members(userKey);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        Map<String, Optional<JwtSession>> loaded = new HashMap<>();
        Map<String, JwtSession> found = new LinkedHashMap<>();
        List<Object> obsolete = new ArrayList<>();
        Instant now = clock.instant();
        for (Object member : members) {
            IndexMember entry = indexMember(member);
            if (entry.expiresAt() != null && !entry.expiresAt().isAfter(now)) {
                obsolete.add(member);
                continue;
            }
            Optional<JwtSession> session = loaded.computeIfAbsent(entry.sessionId(), this::findBySessionId);
            if (session.isPresent()) {
                found.put(entry.sessionId(), session.get());
            } else {
                obsolete.add(member);
            }
        }
        removeIndexMembers(userKey, obsolete);
        return List.copyOf(found.values());
    }

    private void pruneIndex(String userKey, Collection<Object> members, String replacedSessionId) {
        if (members == null || members.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        List<Object> obsolete = new ArrayList<>();
        for (Object member : members) {
            IndexMember entry = indexMember(member);
            if (entry.sessionId().equals(replacedSessionId)
                    || entry.expiresAt() != null && !entry.expiresAt().isAfter(now)
                    || redisTemplate.opsForValue().get(sessionKey(entry.sessionId())) == null) {
                obsolete.add(member);
            }
        }
        removeIndexMembers(userKey, obsolete);
    }

    private void removeIndexMembers(String userKey, List<Object> obsolete) {
        if (!obsolete.isEmpty()) {
            // Each new save has a fresh member version; removing this snapshot cannot remove a concurrent save.
            redisTemplate.opsForSet().remove(userKey, obsolete.toArray());
        }
    }

    private IndexMember indexMember(Object value) {
        String member = String.valueOf(value);
        if (member.startsWith(INDEX_MEMBER_PREFIX)) {
            String[] parts = member.substring(INDEX_MEMBER_PREFIX.length()).split("\\|", -1);
            if (parts.length == 3) {
                try {
                    UUID.fromString(parts[2]);
                    return new IndexMember(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8),
                            Instant.parse(parts[1]));
                } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
                    // Earlier versions stored plain session IDs; retain that read path.
                }
            }
        }
        return new IndexMember(member, null);
    }

    @Override
    public void refresh(String sessionId, Instant lastAccessAt, Instant activeExpiresAt) {
        refreshIfValid(sessionId, lastAccessAt, activeExpiresAt);
    }

    @Override
    public Optional<JwtSession> refreshIfValid(String sessionId, Instant lastAccessAt, Instant activeExpiresAt) {
        Objects.requireNonNull(lastAccessAt, "lastAccessAt");
        byte[] key = keySerializer().serialize(sessionKey(sessionId));
        RedisSerializer<Object> serializer = valueSerializer();
        for (int attempt = 0; attempt < REFRESH_ATTEMPTS; attempt++) {
            byte[] previous = redisTemplate.execute((RedisCallback<byte[]>) connection -> connection.stringCommands().get(key));
            if (previous == null) {
                return Optional.empty();
            }
            JwtSession session = (JwtSession) serializer.deserialize(previous);
            Instant now = clock.instant();
            if (session == null || session.isExpired()
                    || session.getExpiresAt() != null && !session.getExpiresAt().isAfter(now)
                    || Boolean.TRUE.equals(redisTemplate.opsForValue().get(revocationKey(sessionId)))) {
                return Optional.empty();
            }
            if (session.getActiveExpiresAt() != null && !session.getActiveExpiresAt().isAfter(now)) {
                // A paused read may hold an idle deadline which another request has already extended.
                byte[] latest = redisTemplate.execute((RedisCallback<byte[]>) connection -> connection.stringCommands().get(key));
                if (!Arrays.equals(previous, latest)) {
                    continue;
                }
                return Optional.empty();
            }
            if (session.getLastAccessAt() != null && !lastAccessAt.isAfter(session.getLastAccessAt())) {
                return Optional.of(session);
            }
            session.setLastAccessAt(lastAccessAt);
            session.setActiveExpiresAt(activeExpiresAt);
            Long result = redisTemplate.execute(REFRESH_SESSION, RedisSerializer.byteArray(), null,
                    List.of(sessionKey(sessionId)), previous, serializer.serialize(session));
            if (Long.valueOf(1).equals(result)) {
                // Revocation uses a separate key and may have raced the CAS.
                return Boolean.TRUE.equals(redisTemplate.opsForValue().get(revocationKey(sessionId)))
                        ? Optional.empty() : Optional.of(session);
            }
            if (!Long.valueOf(0).equals(result)) {
                throw new IllegalStateException("Redis JWT refresh returned no result");
            }
        }
        throw new IllegalStateException("Redis JWT session changed repeatedly during refresh");
    }

    @SuppressWarnings("unchecked")
    private RedisSerializer<Object> valueSerializer() {
        return (RedisSerializer<Object>) redisTemplate.getValueSerializer();
    }

    @SuppressWarnings("unchecked")
    private RedisSerializer<String> keySerializer() {
        return (RedisSerializer<String>) redisTemplate.getKeySerializer();
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
        // Refreshes may overwrite a stale session snapshot, but never this revocation marker.
        setObject(revocationKey(sessionId), Boolean.TRUE, sessionTtlSeconds(optionalSession.get()));
        return true;
    }

    @Override
    public void blacklistToken(String tokenId, Instant expiresAt) {
        setObject(blacklistKey(tokenId), Boolean.TRUE, ttlSeconds(expiresAt));
    }

    @Override
    public boolean isTokenBlacklisted(String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().get(blacklistKey(tokenId)));
    }

    private void setObject(String key, Object value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, Math.min(ttlSeconds, Integer.MAX_VALUE), TimeUnit.SECONDS);
    }

    private long sessionTtlSeconds(JwtSession session) {
        Instant expiresAt = session.getExpiresAt() == null ? clock.instant().plus(properties.getTimeout()) : session.getExpiresAt();
        return ttlSeconds(expiresAt);
    }

    private long ttlSeconds(Instant expiresAt) {
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        long seconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
        return Math.min(Math.max(seconds, 1), Integer.MAX_VALUE);
    }

    private String sessionKey(String sessionId) {
        return keyPrefix + ":session:" + sessionId;
    }

    private String tokenKey(String tokenId) {
        return keyPrefix + ":token:" + tokenId;
    }

    private String revocationKey(String sessionId) {
        return sessionKey(sessionId) + ":revoked";
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
