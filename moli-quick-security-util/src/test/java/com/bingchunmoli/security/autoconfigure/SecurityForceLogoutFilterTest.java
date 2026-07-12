package com.bingchunmoli.security.autoconfigure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityForceLogoutFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRegisterAuthenticatedSession() throws ServletException, IOException {
        SessionRegistryImpl sessionRegistry = new SessionRegistryImpl();
        SecurityForceLogoutFilter filter = new SecurityForceLogoutFilter(
                sessionRegistry, new SecurityContextLogoutHandler(), 401);
        User principal = new User("moli", "password", AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "password", principal.getAuthorities()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, proceedingChain());

        assertThat(sessionRegistry.getSessionInformation(session.getId())).isNotNull();
        assertThat(sessionRegistry.getSessionInformation(session.getId()).getPrincipal()).isSameAs(principal);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldRejectExpiredSessionAndLogoutCurrentContext() throws ServletException, IOException {
        SessionRegistryImpl sessionRegistry = new SessionRegistryImpl();
        SecurityForceLogoutFilter filter = new SecurityForceLogoutFilter(
                sessionRegistry, new SecurityContextLogoutHandler(), 401);
        User principal = new User("moli", "password", AuthorityUtils.createAuthorityList("ROLE_USER"));
        MockHttpSession session = new MockHttpSession();
        sessionRegistry.registerNewSession(session.getId(), principal);
        sessionRegistry.getSessionInformation(session.getId()).expireNow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "password", principal.getAuthorities()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> proceeded.set(true));

        assertThat(proceeded).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private FilterChain proceedingChain() {
        return (request, response) -> {
        };
    }
}
