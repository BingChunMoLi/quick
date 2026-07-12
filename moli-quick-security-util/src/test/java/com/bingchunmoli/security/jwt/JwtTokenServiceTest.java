package com.bingchunmoli.security.jwt;

import com.bingchunmoli.security.logout.SecurityForceLogoutUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    @Test
    void shouldAuthenticateSimpleModeWithServerSideSession() {
        InMemoryJwtSessionStore sessionStore = new InMemoryJwtSessionStore();
        JwtTokenService tokenService = tokenService(JwtMode.SIMPLE, sessionStore);

        JwtToken token = tokenService.createToken(user());

        JwtAuthenticationToken authentication = tokenService.authenticate(token.getToken());
        assertThat(authentication.getName()).isEqualTo("moli");
        assertThat(token.getSessionId()).isNotBlank();
        assertThat(sessionStore.findBySessionId(token.getSessionId())).isPresent();
    }

    @Test
    void shouldAuthenticateMixinModeAndRejectAfterUserForceLogout() {
        InMemoryJwtSessionStore sessionStore = new InMemoryJwtSessionStore();
        JwtTokenService tokenService = tokenService(JwtMode.MIXIN, sessionStore);
        JwtToken token = tokenService.createToken(user());

        assertThat(tokenService.authenticate(token.getToken()).getName()).isEqualTo("moli");
        assertThat(SecurityForceLogoutUtil.forceLogoutByUserId(tokenService, "1001")).isEqualTo(1);

        assertThatThrownBy(() -> tokenService.authenticate(token.getToken()))
                .isInstanceOf(JwtTokenException.class)
                .hasMessageContaining("session");
    }

    @Test
    void shouldAuthenticateStatelessModeWithoutServerSideSession() {
        InMemoryJwtSessionStore sessionStore = new InMemoryJwtSessionStore();
        JwtTokenService tokenService = tokenService(JwtMode.STATELESS, sessionStore);

        JwtToken token = tokenService.createToken(user());

        assertThat(token.getSessionId()).isNull();
        assertThat(tokenService.authenticate(token.getToken()).getName()).isEqualTo("moli");
        assertThat(sessionStore.findByUserId("1001")).isEmpty();
    }

    @Test
    void shouldRejectStatelessTokenAfterTokenForceLogout() {
        JwtTokenService tokenService = tokenService(JwtMode.STATELESS, new InMemoryJwtSessionStore());
        JwtToken token = tokenService.createToken(user());

        SecurityForceLogoutUtil.forceLogoutToken(tokenService, token.getToken());

        assertThatThrownBy(() -> tokenService.authenticate(token.getToken()))
                .isInstanceOf(JwtTokenException.class)
                .hasMessageContaining("revoked");
    }

    private JwtTokenService tokenService(JwtMode mode, JwtSessionStore sessionStore) {
        JwtTokenProperties properties = new JwtTokenProperties();
        properties.setMode(mode);
        properties.setSecret("moli-quick-security-test-secret-key-123456");
        return new JwtTokenService(properties, sessionStore);
    }

    private JwtUser user() {
        return new JwtUser("1001", "moli", List.of("ROLE_ADMIN", "system:read"), Map.of("tenant", "quick"));
    }
}
