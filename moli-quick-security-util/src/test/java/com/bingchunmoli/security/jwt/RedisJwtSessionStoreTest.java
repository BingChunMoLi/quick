package com.bingchunmoli.security.jwt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.IdentityHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisJwtSessionStoreTest {

    private static final Instant NOW = Instant.parse("2026-10-20T00:00:00.750Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private final Map<RedisTemplate<String, Object>, FakeJwtRedis> backends = new IdentityHashMap<>();

    @ParameterizedTest
    @EnumSource(value = JwtMode.class, names = {"SIMPLE", "MIXIN"})
    @SuppressWarnings("unchecked")
    void defaultJdkSerializationSupportsIssueRefreshAndRevoke(JwtMode mode) {
        var defaultTemplate = new RedisTemplate<String, Object>();
        defaultTemplate.setConnectionFactory(mock(RedisConnectionFactory.class));
        defaultTemplate.afterPropertiesSet();
        assertThat(defaultTemplate.getValueSerializer()).isInstanceOf(JdkSerializationRedisSerializer.class);
        var backend = new FakeJwtRedis(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(), (RedisSerializer<Object>) defaultTemplate.getValueSerializer());
        var properties = properties();
        properties.setSecret("jdk-redis-jwt-regression-test-secret-20261022");
        properties.setMode(mode);
        var store = new RedisJwtSessionStore(backend.template, properties);
        var tokens = new JwtTokenService(properties, store);
        var user = new JwtUser("user-1", "alice", List.of("ROLE_USER"),
                Map.of("tenantId", 7, "labels", List.of("one", "two")));

        var first = tokens.createToken(user);
        var second = tokens.createToken(user);
        assertThat(tokens.authenticate(first.getToken()).getPrincipal()).usingRecursiveComparison().isEqualTo(user);
        assertThat(store.findByUserId("user-1")).hasSize(2);
        assertThat(store.findBySessionId(first.getSessionId())).get().satisfies(session ->
                assertThat(session.getLastAccessAt()).isAfterOrEqualTo(first.getIssuedAt()));

        tokens.forceLogoutToken(first.getToken());
        assertThat(store.isTokenBlacklisted(first.getTokenId())).isTrue();
        assertThatThrownBy(() -> tokens.authenticate(first.getToken())).isInstanceOf(JwtTokenException.class);
        assertThat(tokens.authenticate(second.getToken()).getPrincipal()).usingRecursiveComparison().isEqualTo(user);
        tokens.forceLogoutByUserId("user-1");
        assertThatThrownBy(() -> tokens.authenticate(second.getToken())).isInstanceOf(JwtTokenException.class);
    }

    @Test
    void shouldStoreSessionAndBlacklistTokenWithRedisTemplate() {
        Map<String, Object> values = new ConcurrentHashMap<>();
        Map<String, Set<Object>> sets = new ConcurrentHashMap<>();
        RedisJwtSessionStore store = new RedisJwtSessionStore(redisTemplate(values, sets), properties());
        JwtSession session = session();

        store.save(session);

        assertThat(store.findBySessionId("session-1")).get().usingRecursiveComparison().isEqualTo(session);
        assertThat(store.findByTokenId("token-1")).get().usingRecursiveComparison().isEqualTo(session);
        assertThat(store.findByUserId("1001")).singleElement().usingRecursiveComparison().isEqualTo(session);

        assertThat(store.expireByUserId("1001")).isEqualTo(1);
        assertThat(store.findBySessionId("session-1")).get().extracting(JwtSession::isExpired).isEqualTo(true);

        store.blacklistToken("token-1", Instant.now().plusSeconds(60));
        assertThat(store.isTokenBlacklisted("token-1")).isTrue();
    }

    @Test
    void shouldNotReviveSessionWhenRefreshOverlapsSessionLogout() {
        assertRefreshCannotUndoLogout(false);
    }

    @Test
    void shouldNotReviveSessionWhenRefreshOverlapsUserLogout() {
        assertRefreshCannotUndoLogout(true);
    }

    private void assertRefreshCannotUndoLogout(boolean byUser) {
        Map<String, Object> values = new ConcurrentHashMap<>();
        RedisTemplate<String, Object> template = redisTemplate(values, new ConcurrentHashMap<>());
        RedisJwtSessionStore store = new RedisJwtSessionStore(template, properties());
        store.save(session());
        AtomicBoolean logoutPending = new AtomicBoolean(true);
        // The refresh has already read its own independent, non-revoked Redis snapshot.
        backends.get(template).beforeCompareAndSet = () -> {
            if (logoutPending.compareAndSet(true, false)) {
                if (byUser) {
                    assertThat(store.expireByUserId("1001")).isEqualTo(1);
                } else {
                    assertThat(store.expireSession("session-1")).isTrue();
                }
            }
        };

        store.refresh("session-1", Instant.now(), Instant.now().plusSeconds(120));

        assertThat(logoutPending).isFalse();

        assertThat(store.findBySessionId("session-1")).get().extracting(JwtSession::isExpired).isEqualTo(true);
        assertThat(store.findByTokenId("token-1")).get().extracting(JwtSession::isExpired).isEqualTo(true);
        assertThat(store.findByUserId("1001")).singleElement().extracting(JwtSession::isExpired).isEqualTo(true);
        store.refresh("session-1", Instant.now(), Instant.now().plusSeconds(180));
        assertThat(store.findBySessionId("session-1")).get().extracting(JwtSession::isExpired).isEqualTo(true);
    }

    @Test
    void shouldRetainRevocationUntilSessionExpires() {
        Map<String, Object> values = new ConcurrentHashMap<>();
        RedisTemplate<String, Object> template = redisTemplate(values, new ConcurrentHashMap<>());
        RedisJwtSessionStore store = new RedisJwtSessionStore(template, properties(), CLOCK);
        JwtSession session = session();
        session.setExpiresAt(NOW.plusMillis(1500));
        store.save(session);
        store.expireSession("session-1");
        org.mockito.Mockito.verify(template.opsForValue()).set(
                "test:jwt:session:session-1:revoked", true, 2L, TimeUnit.SECONDS);
    }

    @Test
    void shortSessionMustNotShortenUserIndexLifetimeOrHideLongSessionFromLogout() {
        Map<String, Object> values = new ConcurrentHashMap<>();
        Map<String, Set<Object>> sets = new ConcurrentHashMap<>();
        Map<String, Long> ttls = new ConcurrentHashMap<>();
        RedisJwtSessionStore store = new RedisJwtSessionStore(redisTemplate(values, sets, ttls), properties(), CLOCK);
        store.save(session("long", 7200));
        store.save(session("short", 300));
        String index = "test:jwt:user:1001:sessions";

        assertThat(ttls.get(index)).isEqualTo(7_200_000L);
        // Redis expires the short session after five minutes; the index and long session must remain.
        values.remove("test:jwt:session:short");
        if (ttls.get(index) <= 301_000) {
            sets.remove(index);
        }
        assertThat(store.expireByUserId("1001")).isEqualTo(1);
        assertThat(store.findBySessionId("long")).get().extracting(JwtSession::isExpired).isEqualTo(true);
    }

    @Test
    void concurrentSavesMustKeepTheLongestUserIndexLifetime() throws Exception {
        Map<String, Set<Object>> sets = new ConcurrentHashMap<>();
        Map<String, Long> ttls = new ConcurrentHashMap<>();
        RedisJwtSessionStore store = new RedisJwtSessionStore(
                redisTemplate(new ConcurrentHashMap<>(), sets, ttls), properties(), CLOCK);
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<CompletableFuture<Void>> saves = new java.util.ArrayList<>();
            for (int i = 0; i < 40; i++) {
                JwtSession session = session("session-" + i, i % 2 == 0 ? 7200 : 300);
                saves.add(CompletableFuture.runAsync(() -> store.save(session), executor));
            }
            CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            assertThat(ttls.get("test:jwt:user:1001:sessions")).isEqualTo(7_200_000L);
            assertThat(store.findByUserId("1001")).hasSize(40);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void preservesAnExistingPersistentIndexInsteadOfExpiringUnknownMembers() {
        Map<String, Set<Object>> sets = new ConcurrentHashMap<>();
        sets.put("test:jwt:user:1001:sessions", ConcurrentHashMap.newKeySet());
        sets.get("test:jwt:user:1001:sessions").add("old-session");
        Map<String, Long> ttls = new ConcurrentHashMap<>();
        Map<String, Object> values = new ConcurrentHashMap<>();
        values.put("test:jwt:session:old-session", session("old-session", 7200));
        RedisJwtSessionStore store = new RedisJwtSessionStore(
                redisTemplate(values, sets, ttls), properties(), CLOCK);
        store.save(session("short", 300));
        assertThat(ttls).doesNotContainKey("test:jwt:user:1001:sessions");
        assertThat(sets.get("test:jwt:user:1001:sessions")).hasSize(2).contains("old-session");
        assertThat(store.findByUserId("1001")).extracting(JwtSession::getSessionId)
                .containsExactlyInAnyOrder("old-session", "short");
    }

    @Test
    void blacklistTtlCoversFractionalSecondsThroughTokenExpiration() {
        Map<String, Long> ttls = new ConcurrentHashMap<>();
        RedisJwtSessionStore store = new RedisJwtSessionStore(
                redisTemplate(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), ttls), properties(), CLOCK);
        // JWT exp is an integer second; the wall clock is already 750 ms into the current second.
        store.blacklistToken("token", Instant.parse("2026-10-20T00:00:10Z"));
        assertThat(ttls.get("test:jwt:blacklist:token")).isEqualTo(10_000L);
        store.blacklistToken("near-expiry", NOW.plusMillis(1));
        assertThat(ttls.get("test:jwt:blacklist:near-expiry")).isEqualTo(1000L);
    }

    @Test
    void olderRefreshCannotOverwriteAConcurrentNewerSnapshot() {
        Map<String, Object> values = new ConcurrentHashMap<>();
        Map<String, Long> ttls = new ConcurrentHashMap<>();
        RedisTemplate<String, Object> template = redisTemplate(values, new ConcurrentHashMap<>(), ttls);
        RedisJwtSessionStore store = new RedisJwtSessionStore(template, properties(), CLOCK);
        JwtSession session = session("session", 300);
        session.setLastAccessAt(NOW.minusSeconds(2));
        store.save(session);
        backends.get(template).beforeCompareAndSet = () -> store.refresh("session", NOW, NOW.plusSeconds(30));
        store.refresh("session", NOW.minusSeconds(1), NOW.plusSeconds(29));
        JwtSession refreshed = store.findBySessionId("session").orElseThrow();
        assertThat(refreshed.getLastAccessAt()).isEqualTo(NOW);
        assertThat(refreshed.getActiveExpiresAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(ttls.get("test:jwt:session:session")).isEqualTo(300_000L);
        store.refresh("session", NOW.plusSeconds(1), null);
        store.refresh("session", NOW, NOW.plusSeconds(30));
        assertThat(store.findBySessionId("session")).get().extracting(JwtSession::getActiveExpiresAt).isNull();
    }

    @Test
    void refreshDoesNotRecreateAKeyThatExpiredDuringCompareAndSet() {
        Map<String, Object> values = new ConcurrentHashMap<>();
        RedisTemplate<String, Object> template = redisTemplate(values, new ConcurrentHashMap<>());
        RedisJwtSessionStore store = new RedisJwtSessionStore(template, properties(), CLOCK);
        store.save(session("session", 300));
        backends.get(template).beforeCompareAndSet = () -> values.remove("test:jwt:session:session");
        store.refresh("session", NOW, NOW.plusSeconds(30));
        assertThat(store.findBySessionId("session")).isEmpty();
    }

    @Test
    void validationRetriesAnOldIdleDeadlineAfterAConcurrentRefresh() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);
        Map<String, Object> values = new ConcurrentHashMap<>();
        RedisTemplate<String, Object> template = redisTemplate(values, new ConcurrentHashMap<>());
        RedisJwtSessionStore store = new RedisJwtSessionStore(template, properties(), clock);
        JwtSession session = session("session", 300);
        session.setActiveExpiresAt(NOW.plusSeconds(10));
        store.save(session);
        backends.get(template).afterSnapshotRead = () -> {
            when(clock.instant()).thenReturn(NOW.plusSeconds(5));
            assertThat(store.refreshIfValid("session", NOW.plusSeconds(5), NOW.plusSeconds(20))).isPresent();
            when(clock.instant()).thenReturn(NOW.plusSeconds(11));
        };

        assertThat(store.refreshIfValid("session", NOW, NOW.plusSeconds(10))).get()
                .extracting(JwtSession::getActiveExpiresAt).isEqualTo(NOW.plusSeconds(20));
        assertThat(values).doesNotContainKey("test:jwt:session:session:revoked");
        assertThat(store.findBySessionId("session")).get().extracting(JwtSession::isExpired).isEqualTo(false);
    }

    @Test
    void validationRejectsAnUnchangedExpiredSnapshotWithoutWritingRevocation() {
        Map<String, Object> values = new ConcurrentHashMap<>();
        RedisJwtSessionStore store = new RedisJwtSessionStore(redisTemplate(values, new ConcurrentHashMap<>()), properties(), CLOCK);
        JwtSession session = session("session", 300);
        session.setActiveExpiresAt(NOW);
        store.save(session);
        assertThat(store.refreshIfValid("session", NOW, NOW.plusSeconds(30))).isEmpty();
        assertThat(values).doesNotContainKey("test:jwt:session:session:revoked");
        assertThat(store.findBySessionId("session")).get().extracting(JwtSession::getActiveExpiresAt).isEqualTo(NOW);
        assertThat(store.refreshIfValid("missing", NOW, null)).isEmpty();
    }

    @Test
    void validationRejectsLogoutRacingTheSuccessfulRefreshCas() {
        Map<String, Object> values = new ConcurrentHashMap<>();
        RedisTemplate<String, Object> template = redisTemplate(values, new ConcurrentHashMap<>());
        RedisJwtSessionStore store = new RedisJwtSessionStore(template, properties(), CLOCK);
        store.save(session("session", 300));
        backends.get(template).beforeCompareAndSet = () -> store.expireSession("session");
        assertThat(store.refreshIfValid("session", NOW, NOW.plusSeconds(30))).isEmpty();
        assertThat(store.findBySessionId("session")).get().extracting(JwtSession::isExpired).isEqualTo(true);
    }

    @Test
    void expiredIndexMembersAreRemovedOnReadAndOnSaveWithoutDroppingLiveMembers() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);
        Map<String, Set<Object>> sets = new ConcurrentHashMap<>();
        RedisTemplate<String, Object> template = redisTemplate(new ConcurrentHashMap<>(), sets);
        RedisJwtSessionStore store = new RedisJwtSessionStore(template, properties(), clock);
        store.save(session("old", 1));
        store.save(session("live", 300));
        when(clock.instant()).thenReturn(NOW.plusSeconds(2));
        assertThat(store.findByUserId("1001")).extracting(JwtSession::getSessionId).containsExactly("live");
        assertThat(sets.get("test:jwt:user:1001:sessions")).hasSize(1);
        store.save(session("second-old", 3));
        when(clock.instant()).thenReturn(NOW.plusSeconds(4));
        store.save(session("new", 300));
        assertThat(sets.get("test:jwt:user:1001:sessions")).hasSize(2);
        assertThat(store.findByUserId("1001")).extracting(JwtSession::getSessionId)
                .containsExactlyInAnyOrder("live", "new");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void removingMissingIndexMemberCannotDeleteAConcurrentSaveOfTheSameSession(boolean legacy) {
        Map<String, Object> values = new ConcurrentHashMap<>();
        Map<String, Set<Object>> sets = new ConcurrentHashMap<>();
        RedisTemplate<String, Object> template = redisTemplate(values, sets);
        RedisJwtSessionStore store = new RedisJwtSessionStore(template, properties(), CLOCK);
        if (legacy) {
            sets.put("test:jwt:user:1001:sessions", ConcurrentHashMap.newKeySet());
            sets.get("test:jwt:user:1001:sessions").add("session");
        } else {
            store.save(session("session", 300));
            values.remove("test:jwt:session:session");
        }
        backends.get(template).beforeIndexRemoval = () -> store.save(session("session", 300));
        store.findByUserId("1001");
        assertThat(store.findByUserId("1001")).extracting(JwtSession::getSessionId).containsExactly("session");
        assertThat(sets.get("test:jwt:user:1001:sessions")).hasSize(1);
    }

    @Test
    void repeatedSavesReplaceOldIndexVersions() {
        Map<String, Set<Object>> sets = new ConcurrentHashMap<>();
        RedisJwtSessionStore store = new RedisJwtSessionStore(
                redisTemplate(new ConcurrentHashMap<>(), sets), properties(), CLOCK);
        for (int i = 0; i < 20; i++) {
            store.save(session("session", 300));
        }
        assertThat(sets.get("test:jwt:user:1001:sessions")).hasSize(1);
        assertThat(store.findByUserId("1001")).hasSize(1);
    }

    private RedisTemplate<String, Object> redisTemplate(Map<String, Object> values, Map<String, Set<Object>> sets) {
        return redisTemplate(values, sets, new ConcurrentHashMap<>());
    }

    private RedisTemplate<String, Object> redisTemplate(Map<String, Object> values, Map<String, Set<Object>> sets,
                                                      Map<String, Long> ttls) {
        FakeJwtRedis backend = new FakeJwtRedis(values, sets, ttls);
        backends.put(backend.template, backend);
        return backend.template;
    }

    private JwtSession session(String id, long lifetimeSeconds) {
        JwtSession session = session();
        session.setSessionId(id);
        session.setTokenId(id + "-token");
        session.setCreatedAt(NOW.minusSeconds(1));
        session.setLastAccessAt(NOW.minusSeconds(1));
        session.setExpiresAt(NOW.plusSeconds(lifetimeSeconds));
        return session;
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
