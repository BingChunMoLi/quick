package com.bingchunmoli.security.logout;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityLogoutServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldForceLogoutSessionsByUsername() {
        SessionRegistryImpl sessionRegistry = new SessionRegistryImpl();
        User moli = new User("moli", "password", AuthorityUtils.createAuthorityList("ROLE_USER"));
        User other = new User("other", "password", AuthorityUtils.createAuthorityList("ROLE_USER"));
        sessionRegistry.registerNewSession("session-1", moli);
        sessionRegistry.registerNewSession("session-2", moli);
        sessionRegistry.registerNewSession("session-3", other);
        SecurityLogoutService service = new SecurityLogoutService(sessionRegistry, new SecurityContextLogoutHandler());

        int expiredCount = service.forceLogoutByUsername("moli");

        assertThat(expiredCount).isEqualTo(2);
        assertThat(sessionRegistry.getSessionInformation("session-1").isExpired()).isTrue();
        assertThat(sessionRegistry.getSessionInformation("session-2").isExpired()).isTrue();
        assertThat(sessionRegistry.getSessionInformation("session-3").isExpired()).isFalse();
        assertThat(service.getSessionsByUsername("moli", true)).hasSize(2);
        assertThat(service.getSessionsByUsername("moli", false)).isEmpty();
    }

    @Test
    void shouldForceLogoutSingleSession() {
        SessionRegistryImpl sessionRegistry = new SessionRegistryImpl();
        sessionRegistry.registerNewSession("session-1", "moli");
        SecurityLogoutService service = new SecurityLogoutService(sessionRegistry, new SecurityContextLogoutHandler());

        assertThat(service.forceLogoutSession("session-1")).isTrue();
        assertThat(service.forceLogoutSession("session-1")).isFalse();
        assertThat(service.forceLogoutSession("missing")).isFalse();
    }

    @Test
    void shouldLogoutCurrentRequestAndClearSecurityContext() {
        User principal = new User("moli", "password", AuthorityUtils.createAuthorityList("ROLE_USER"));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, "password", principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        SecurityLogoutService service = new SecurityLogoutService(
                new SessionRegistryImpl(), new SecurityContextLogoutHandler());

        service.logoutCurrent(request, new MockHttpServletResponse());

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldResolveCommonPrincipalNames() {
        SecurityLogoutService service = new SecurityLogoutService(
                new SessionRegistryImpl(), new SecurityContextLogoutHandler());
        Principal principal = () -> "principal-user";

        assertThat(service.resolvePrincipalName("text-user")).contains("text-user");
        assertThat(service.resolvePrincipalName(principal)).contains("principal-user");
    }
}
