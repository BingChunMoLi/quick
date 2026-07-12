package com.bingchunmoli.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisJwtSessionStoreTest {

    @Test
    void shouldStoreSessionAndBlacklistTokenWithRedisTemplate() {
        Map<String, Object> values = new ConcurrentHashMap<>();
        Map<String, Set<Object>> sets = new ConcurrentHashMap<>();
        RedisJwtSessionStore store = new RedisJwtSessionStore(redisTemplate(values, sets), properties());
        JwtSession session = session();

        store.save(session);

        assertThat(store.findBySessionId("session-1")).containsSame(session);
        assertThat(store.findByTokenId("token-1")).containsSame(session);
        assertThat(store.findByUserId("1001")).containsExactly(session);

        assertThat(store.expireByUserId("1001")).isEqualTo(1);
        assertThat(store.findBySessionId("session-1")).get().extracting(JwtSession::isExpired).isEqualTo(true);

        store.blacklistToken("token-1", Instant.now().plusSeconds(60));
        assertThat(store.isTokenBlacklisted("token-1")).isTrue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private RedisTemplate<String, Object> redisTemplate(Map<String, Object> values, Map<String, Set<Object>> sets) {
        RedisTemplate redisTemplate = mock(RedisTemplate.class);
        ValueOperations valueOperations = mock(ValueOperations.class);
        SetOperations setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.expire(any(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(valueOperations.get(any())).thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(any(), any(), anyLong(), any(TimeUnit.class));
        when(setOperations.add(any(), any())).thenAnswer(invocation -> {
            sets.computeIfAbsent(invocation.getArgument(0), key -> ConcurrentHashMap.newKeySet())
                    .add(invocation.getArgument(1));
            return 1L;
        });
        when(setOperations.members(any())).thenAnswer(invocation -> sets.get(invocation.getArgument(0)));
        return redisTemplate;
    }

    private JwtTokenProperties properties() {
        JwtTokenProperties properties = new JwtTokenProperties();
        properties.setRedisKeyPrefix("test:jwt");
        return properties;
    }

    private JwtSession session() {
        JwtSession session = new JwtSession();
        session.setSessionId("session-1");
        session.setTokenId("token-1");
        session.setUser(new JwtUser("1001", "moli", List.of("ROLE_ADMIN"), Map.of()));
        session.setCreatedAt(Instant.now());
        session.setLastAccessAt(Instant.now());
        session.setExpiresAt(Instant.now().plusSeconds(300));
        return session;
    }
}
