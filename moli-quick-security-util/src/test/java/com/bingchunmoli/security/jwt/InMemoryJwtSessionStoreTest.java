package com.bingchunmoli.security.jwt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InMemoryJwtSessionStoreTest {
    private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");
    private final Clock clock = mock(Clock.class);
    private final ScheduledExecutorService cleanup = mock(ScheduledExecutorService.class);
    private final InMemoryJwtSessionStore store = new InMemoryJwtSessionStore(clock, cleanup);

    @AfterEach
    void close() {
        store.close();
    }

    @Test
    void scheduledCleanupReclaimsExpiredDataWithoutAnyLookups() {
        when(clock.instant()).thenReturn(NOW);
        store.save(session("expired", NOW.plusSeconds(10), null));
        store.save(session("idle", NOW.plusSeconds(300), NOW.plusSeconds(10)));
        store.save(session("live", NOW.plusSeconds(300), NOW.plusSeconds(300)));
        store.blacklistToken("expired", NOW.plusSeconds(10));
        store.blacklistToken("live", NOW.plusSeconds(300));
        when(clock.instant()).thenReturn(NOW.plusSeconds(10));

        scheduledCleanup().run();

        assertThat(entries("sessions")).containsOnlyKeys("live");
        assertThat(entries("tokenBlacklist")).containsOnlyKeys("live");
        assertThat(store.findBySessionId("live")).isPresent();
        assertThat(store.isTokenBlacklisted("live")).isTrue();
    }

    @Test
    void rejectsExpiredSessionAtLookupBeforeNextScheduledCleanup() {
        when(clock.instant()).thenReturn(NOW);
        store.save(session("session", NOW.plusSeconds(10), null));
        when(clock.instant()).thenReturn(NOW.plusSeconds(10));

        assertThat(store.findBySessionId("session")).isEmpty();
        assertThat(entries("sessions")).isEmpty();
    }

    @Test
    void refreshExtendsOnlyLiveSessionsAndCleanupKeepsTheExtension() {
        when(clock.instant()).thenReturn(NOW);
        store.save(session("session", NOW.plusSeconds(300), NOW.plusSeconds(10)));
        when(clock.instant()).thenReturn(NOW.plusSeconds(5));
        store.refresh("session", NOW.plusSeconds(5), NOW.plusSeconds(20));
        when(clock.instant()).thenReturn(NOW.plusSeconds(10));
        scheduledCleanup().run();
        assertThat(store.findBySessionId("session")).isPresent();

        when(clock.instant()).thenReturn(NOW.plusSeconds(20));
        store.refresh("session", NOW.plusSeconds(20), NOW.plusSeconds(30));
        assertThat(store.findBySessionId("session")).isEmpty();
    }

    @Test
    void refreshDoesNotResurrectRevokedSessions() {
        when(clock.instant()).thenReturn(NOW);
        store.save(session("session", NOW.plusSeconds(300), null));
        assertThat(store.expireByUserId("user")).isEqualTo(1);
        store.refresh("session", NOW, NOW.plusSeconds(300));
        assertThat(store.findBySessionId("session")).isEmpty();
        assertThat(store.findByTokenId("session-token")).isEmpty();
        assertThat(store.findByUserId("user")).isEmpty();
    }

    @Test
    void olderRefreshCannotRewindActivityOrRestoreAnOldTimeoutPolicy() {
        when(clock.instant()).thenReturn(NOW);
        store.save(session("session", NOW.plusSeconds(300), NOW.plusSeconds(20)));
        store.refresh("session", NOW, NOW.plusSeconds(30));
        store.refresh("session", NOW.minusSeconds(1), NOW.plusSeconds(29));
        assertThat(store.findBySessionId("session")).get().extracting(JwtSession::getLastAccessAt).isEqualTo(NOW);
        assertThat(store.findBySessionId("session")).get().extracting(JwtSession::getActiveExpiresAt).isEqualTo(NOW.plusSeconds(30));
        store.refresh("session", NOW.plusSeconds(1), null);
        store.refresh("session", NOW, NOW.plusSeconds(30));
        assertThat(store.findBySessionId("session")).get().extracting(JwtSession::getActiveExpiresAt).isNull();
    }

    @Test
    void validationUsesTheLatestActivityAndRejectsExpiryWithoutRevivingIt() {
        when(clock.instant()).thenReturn(NOW);
        store.save(session("session", NOW.plusSeconds(300), NOW.plusSeconds(10)));
        when(clock.instant()).thenReturn(NOW.plusSeconds(5));
        assertThat(store.refreshIfValid("session", NOW.plusSeconds(5), NOW.plusSeconds(20))).isPresent();
        when(clock.instant()).thenReturn(NOW.plusSeconds(11));
        assertThat(store.refreshIfValid("session", NOW, NOW.plusSeconds(10))).isPresent();
        assertThat(store.findBySessionId("session")).get().extracting(JwtSession::getActiveExpiresAt)
                .isEqualTo(NOW.plusSeconds(20));
        when(clock.instant()).thenReturn(NOW.plusSeconds(20));
        assertThat(store.refreshIfValid("session", NOW.plusSeconds(20), NOW.plusSeconds(30))).isEmpty();
        assertThat(store.findBySessionId("session")).isEmpty();
        assertThat(store.refreshIfValid("missing", NOW, null)).isEmpty();
    }

    @Test
    void blacklistExpiryUsesInclusiveBoundaryAndDoesNotStoreExpiredEntries() {
        when(clock.instant()).thenReturn(NOW);
        store.blacklistToken("past", NOW.minusSeconds(1));
        store.blacklistToken("current", NOW);
        store.blacklistToken("future", NOW.plusSeconds(1));
        assertThat(entries("tokenBlacklist")).containsOnlyKeys("future");
        when(clock.instant()).thenReturn(NOW.plusSeconds(1));
        assertThat(store.isTokenBlacklisted("future")).isFalse();
        assertThat(entries("tokenBlacklist")).isEmpty();
    }

    @Test
    void closeStopsCleanupAndReleasesStoredData() {
        when(clock.instant()).thenReturn(NOW);
        store.save(session("session", NOW.plusSeconds(300), null));
        store.blacklistToken("token", NOW.plusSeconds(300));
        store.close();
        verify(cleanup).shutdownNow();
        assertThat(entries("sessions")).isEmpty();
        assertThat(entries("tokenBlacklist")).isEmpty();
    }

    private Runnable scheduledCleanup() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(cleanup).scheduleWithFixedDelay(task.capture(), eq(60L), eq(60L), eq(TimeUnit.SECONDS));
        return task.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> entries(String field) {
        return (Map<String, ?>) ReflectionTestUtils.getField(store, field);
    }

    private JwtSession session(String id, Instant expiresAt, Instant activeExpiresAt) {
        JwtSession session = new JwtSession();
        session.setSessionId(id);
        session.setTokenId(id + "-token");
        session.setUser(new JwtUser("user", "user", List.of(), Map.of()));
        session.setExpiresAt(expiresAt);
        session.setActiveExpiresAt(activeExpiresAt);
        return session;
    }
}
