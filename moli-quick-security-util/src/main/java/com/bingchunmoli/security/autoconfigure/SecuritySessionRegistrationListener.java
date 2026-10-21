package com.bingchunmoli.security.autoconfigure;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionBindingEvent;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.Objects;

/** Registers a login synchronously when its security context is saved, before the success response. */
public class SecuritySessionRegistrationListener implements HttpSessionAttributeListener {

    private final SessionRegistry sessionRegistry;

    public SecuritySessionRegistrationListener(SessionRegistry sessionRegistry) {
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry, "sessionRegistry");
    }

    @Override
    public void attributeAdded(HttpSessionBindingEvent event) {
        registerSavedContext(event);
    }

    @Override
    public void attributeReplaced(HttpSessionBindingEvent event) {
        registerSavedContext(event);
    }

    private void registerSavedContext(HttpSessionBindingEvent event) {
        if (!HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY.equals(event.getName())) {
            return;
        }
        try {
            // Replacement events contain the previous value; read the newly saved context instead.
            Object saved = event.getSession().getAttribute(event.getName());
            if (saved instanceof SecurityContext context) {
                registerOrRefreshSession(sessionRegistry, event.getSession(), context.getAuthentication());
            }
        } catch (IllegalStateException invalidated) {
            // A concurrent logout may have invalidated the session during notification.
        }
    }

    static void registerOrRefreshSession(SessionRegistry registry, HttpSession session, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return;
        }
        String sessionId = session.getId();
        // Share the registry monitor with SecurityLogoutService: a stale registration must
        // never replace a SessionInformation that a concurrent logout has already expired.
        synchronized (registry) {
            SessionInformation existing = registry.getSessionInformation(sessionId);
            if (existing != null && existing.isExpired()) {
                return;
            }
            if (existing == null || !Objects.equals(existing.getPrincipal(), authentication.getPrincipal())) {
                registry.registerNewSession(sessionId, authentication.getPrincipal());
            } else {
                existing.refreshLastRequest();
            }
        }
    }
}
