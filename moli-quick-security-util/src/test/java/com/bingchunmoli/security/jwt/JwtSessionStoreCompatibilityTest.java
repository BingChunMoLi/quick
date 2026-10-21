package com.bingchunmoli.security.jwt;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtSessionStoreCompatibilityTest {
    @Test
    void defaultMethodSupportsCustomStoresImplementingOnlyTheOriginalApi() {
        JwtSessionStore legacy = mock(JwtSessionStore.class, CALLS_REAL_METHODS);
        JwtSession session = new JwtSession();
        Instant now = Instant.now();
        session.setExpiresAt(now.plusSeconds(300));
        session.setActiveExpiresAt(now.plusSeconds(30));
        when(legacy.findBySessionId("session")).thenReturn(Optional.of(session));
        assertThat(legacy.refreshIfValid("session", now, now.plusSeconds(60))).contains(session);
        verify(legacy).refresh("session", now, now.plusSeconds(60));
        verify(legacy, times(2)).findBySessionId("session");
        verify(legacy, never()).expireSession(any());
    }

    @Test
    void defaultMethodRejectsAConcurrentLogoutAfterTheLegacyRefresh() {
        JwtSessionStore legacy = mock(JwtSessionStore.class, CALLS_REAL_METHODS);
        JwtSession session = new JwtSession();
        Instant now = Instant.now();
        session.setExpiresAt(now.plusSeconds(300));
        when(legacy.findBySessionId("session")).thenReturn(Optional.of(session), Optional.empty());
        assertThat(legacy.refreshIfValid("session", now, null)).isEmpty();
        verify(legacy, never()).expireSession(any());
    }
}
