package com.bingchunmoli.security.logout;

import com.bingchunmoli.security.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Logout operations based on Spring Security's session registry.
 *
 * @author MoLi
 */
public class SecurityLogoutService {

    private final SessionRegistry sessionRegistry;
    private final SecurityContextLogoutHandler logoutHandler;

    public SecurityLogoutService(SessionRegistry sessionRegistry, SecurityContextLogoutHandler logoutHandler) {
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry, "sessionRegistry must not be null");
        this.logoutHandler = Objects.requireNonNull(logoutHandler, "logoutHandler must not be null");
    }

    public void logoutCurrent(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityUtil.getAuthentication().orElse(null);
        logoutHandler.logout(request, response, authentication);
    }

    public int forceLogoutByUsername(String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        return sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> usernameEqualsPrincipal(username, principal))
                .mapToInt(this::forceLogout)
                .sum();
    }

    public int forceLogout(Object principal) {
        if (principal == null) {
            return 0;
        }
        List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
        sessions.forEach(SessionInformation::expireNow);
        return sessions.size();
    }

    public boolean forceLogoutSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        SessionInformation sessionInformation = sessionRegistry.getSessionInformation(sessionId);
        if (sessionInformation == null || sessionInformation.isExpired()) {
            return false;
        }
        sessionInformation.expireNow();
        return true;
    }

    public List<SessionInformation> getSessionsByUsername(String username, boolean includeExpiredSessions) {
        if (username == null || username.isBlank()) {
            return List.of();
        }
        return sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> usernameEqualsPrincipal(username, principal))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, includeExpiredSessions).stream())
                .toList();
    }

    public Optional<String> resolvePrincipalName(Object principal) {
        if (principal instanceof Authentication authentication) {
            return Optional.ofNullable(authentication.getName());
        }
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return Optional.ofNullable(userDetails.getUsername());
        }
        if (principal instanceof Principal javaPrincipal) {
            return Optional.ofNullable(javaPrincipal.getName());
        }
        if (principal instanceof CharSequence charSequence) {
            return Optional.of(charSequence.toString());
        }
        return Optional.ofNullable(principal)
                .map(Object::toString);
    }

    private boolean usernameEqualsPrincipal(String username, Object principal) {
        return resolvePrincipalName(principal)
                .filter(username::equals)
                .isPresent();
    }
}
