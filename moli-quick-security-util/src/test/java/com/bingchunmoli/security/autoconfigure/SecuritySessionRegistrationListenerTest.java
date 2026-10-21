package com.bingchunmoli.security.autoconfigure;

import jakarta.servlet.http.HttpSessionBindingEvent;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import static org.assertj.core.api.Assertions.assertThat;

class SecuritySessionRegistrationListenerTest {
    private static final String CONTEXT = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    private final SessionRegistryImpl registry = new SessionRegistryImpl();
    private final SecuritySessionRegistrationListener listener = new SecuritySessionRegistrationListener(registry);
    private final MockHttpSession session = new MockHttpSession();

    @Test
    void registersSavedAuthenticationWithoutAThreadContext() {
        var context = context("user");
        session.setAttribute(CONTEXT, context);
        listener.attributeAdded(new HttpSessionBindingEvent(session, CONTEXT, context));
        assertThat(registry.getAllPrincipals()).containsExactly("user");
    }

    @Test
    void replacementUsesTheNewContextInsteadOfTheOldEventValue() {
        registry.registerNewSession(session.getId(), "old");
        session.setAttribute(CONTEXT, context("new"));
        listener.attributeReplaced(new HttpSessionBindingEvent(session, CONTEXT, context("old")));
        assertThat(registry.getAllPrincipals()).containsExactly("new");
    }

    @Test
    void ignoresUnrelatedAttributesAnonymousAndUnauthenticatedContexts() {
        session.setAttribute(CONTEXT, context("user"));
        listener.attributeAdded(new HttpSessionBindingEvent(session, "other", context("user")));
        session.setAttribute(CONTEXT, new SecurityContextImpl(new AnonymousAuthenticationToken(
                "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))));
        listener.attributeReplaced(new HttpSessionBindingEvent(session, CONTEXT));
        session.setAttribute(CONTEXT, new SecurityContextImpl(UsernamePasswordAuthenticationToken.unauthenticated("user", "password")));
        listener.attributeReplaced(new HttpSessionBindingEvent(session, CONTEXT));
        assertThat(registry.getAllPrincipals()).isEmpty();
    }

    @Test
    void cannotReviveAnAlreadyRevokedSession() {
        registry.registerNewSession(session.getId(), "user");
        registry.getSessionInformation(session.getId()).expireNow();
        session.setAttribute(CONTEXT, context("user"));
        listener.attributeReplaced(new HttpSessionBindingEvent(session, CONTEXT));
        assertThat(registry.getSessionInformation(session.getId()).isExpired()).isTrue();
    }

    @Test
    void ignoresConcurrentSessionInvalidation() {
        session.invalidate();
        listener.attributeAdded(new HttpSessionBindingEvent(session, CONTEXT, context("user")));
        assertThat(registry.getAllPrincipals()).isEmpty();
    }

    private SecurityContextImpl context(String user) {
        return new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                user, null, AuthorityUtils.createAuthorityList("ROLE_USER")));
    }
}
