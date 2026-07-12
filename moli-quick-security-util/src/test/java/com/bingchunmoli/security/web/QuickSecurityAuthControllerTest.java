package com.bingchunmoli.security.web;

import com.bingchunmoli.security.jwt.InMemoryJwtSessionStore;
import com.bingchunmoli.security.jwt.JwtSessionStore;
import com.bingchunmoli.security.jwt.JwtTokenProperties;
import com.bingchunmoli.security.jwt.JwtTokenService;
import com.bingchunmoli.security.logout.SecurityLogoutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

import static org.assertj.core.api.Assertions.assertThat;

class QuickSecurityAuthControllerTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldLoginAndReturnJwtToken() {
        SessionRegistryImpl sessionRegistry = new SessionRegistryImpl();
        JwtSessionStore jwtSessionStore = new InMemoryJwtSessionStore();
        User principal = new User("moli", "password", AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        QuickSecurityAuthController controller = new QuickSecurityAuthController(
                successAuthenticationManager(principal),
                sessionRegistry,
                new SecurityLogoutService(sessionRegistry, new SecurityContextLogoutHandler()),
                jwtTokenService(jwtSessionStore));
        LoginRequest loginRequest = loginRequest("moli", "password");
        MockHttpServletRequest request = new MockHttpServletRequest();

        var response = controller.login(loginRequest, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getUsername()).isEqualTo("moli");
        assertThat(response.getBody().getData().getToken()).isNotBlank();
        assertThat(response.getBody().getData().getSessionId()).isNotBlank();
        assertThat(jwtSessionStore.findBySessionId(response.getBody().getData().getSessionId())).isPresent();
    }

    @Test
    void shouldReturnUnauthorizedWhenLoginFails() {
        QuickSecurityAuthController controller = new QuickSecurityAuthController(
                authentication -> {
                    throw new BadCredentialsException("bad credentials");
                },
                new SessionRegistryImpl(),
                new SecurityLogoutService(new SessionRegistryImpl(), new SecurityContextLogoutHandler()),
                jwtTokenService(new InMemoryJwtSessionStore()));

        var response = controller.login(loginRequest("moli", "bad"), new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    void shouldReturnCurrentUserFromSecurityContext() {
        User principal = new User("moli", "password", AuthorityUtils.createAuthorityList("ROLE_USER"));
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, "password", principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        QuickSecurityAuthController controller = new QuickSecurityAuthController(
                successAuthenticationManager(principal),
                new SessionRegistryImpl(),
                new SecurityLogoutService(new SessionRegistryImpl(), new SecurityContextLogoutHandler()),
                jwtTokenService(new InMemoryJwtSessionStore()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());

        var response = controller.currentUser(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getUsername()).isEqualTo("moli");
    }

    @Test
    void shouldForceLogoutByUsername() {
        SessionRegistryImpl sessionRegistry = new SessionRegistryImpl();
        User principal = new User("moli", "password", AuthorityUtils.createAuthorityList("ROLE_USER"));
        sessionRegistry.registerNewSession("session-1", principal);
        QuickSecurityAuthController controller = new QuickSecurityAuthController(
                successAuthenticationManager(principal),
                sessionRegistry,
                new SecurityLogoutService(sessionRegistry, new SecurityContextLogoutHandler()),
                jwtTokenService(new InMemoryJwtSessionStore()));

        QuickSecurityResponse<ForceLogoutResult> response = controller.forceLogoutByUsername("moli");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getExpiredSessions()).isEqualTo(1);
        assertThat(sessionRegistry.getSessionInformation("session-1").isExpired()).isTrue();
    }

    private AuthenticationManager successAuthenticationManager(User principal) {
        return authentication -> UsernamePasswordAuthenticationToken.authenticated(
                principal, authentication.getCredentials(), principal.getAuthorities());
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);
        return loginRequest;
    }

    private JwtTokenService jwtTokenService(JwtSessionStore sessionStore) {
        return new JwtTokenService(new JwtTokenProperties(), sessionStore);
    }
}
