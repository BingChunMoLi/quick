package com.bingchunmoli.oauth.client.web;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import jakarta.servlet.http.*;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** Per-state entries allow concurrent tabs; no session is created by a callback. */
public final class SessionAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private static final String ATTRIBUTE = SessionAuthorizationRequestRepository.class.getName();
    private final Duration ttl;
    public SessionAuthorizationRequestRepository(Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("Positive OAuth2 state TTL required");
        this.ttl = ttl;
    }
    private record Entry(OAuth2AuthorizationRequest request, long expiresAt) implements Serializable { }
    @SuppressWarnings("unchecked")
    private Map<String, Entry> entries(HttpSession session) {
        var entries = (Map<String, Entry>) session.getAttribute(ATTRIBUTE);
        if (entries == null) entries = new HashMap<>();
        long now = System.currentTimeMillis();
        entries.values().removeIf(entry -> entry.expiresAt() <= now);
        return entries;
    }
    @Override public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        synchronized (session) {
            var entries = entries(session);
            Entry entry = entries.get(request.getParameter("state"));
            session.setAttribute(ATTRIBUTE, entries);
            return entry == null ? null : entry.request();
        }
    }
    @Override public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorization, HttpServletRequest request,
                                                   HttpServletResponse response) {
        if (authorization == null) { removeAuthorizationRequest(request, response); return; }
        HttpSession session = request.getSession();
        synchronized (session) {
            var entries = entries(session);
            if (entries.size() >= 20) throw new IllegalStateException("Too many pending OAuth2 authorizations");
            entries.put(authorization.getState(), new Entry(authorization, System.currentTimeMillis() + ttl.toMillis()));
            session.setAttribute(ATTRIBUTE, entries);
        }
    }
    @Override public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        synchronized (session) {
            var entries = entries(session);
            Entry removed = entries.remove(request.getParameter("state"));
            session.setAttribute(ATTRIBUTE, entries);
            return removed == null ? null : removed.request();
        }
    }
}
