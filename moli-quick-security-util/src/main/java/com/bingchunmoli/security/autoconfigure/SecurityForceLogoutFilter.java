package com.bingchunmoli.security.autoconfigure;

import com.bingchunmoli.security.util.SecurityUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * Registers authenticated sessions and rejects sessions expired by SecurityLogoutService.
 *
 * @author MoLi
 */
public class SecurityForceLogoutFilter extends OncePerRequestFilter {

    private final SessionRegistry sessionRegistry;
    private final LogoutHandler logoutHandler;
    private final int expiredStatus;

    public SecurityForceLogoutFilter(SessionRegistry sessionRegistry, LogoutHandler logoutHandler, int expiredStatus) {
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry, "sessionRegistry must not be null");
        this.logoutHandler = Objects.requireNonNull(logoutHandler, "logoutHandler must not be null");
        this.expiredStatus = expiredStatus;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Authentication authentication = authentication(session);

        if (session != null) {
            SessionInformation sessionInformation = sessionRegistry.getSessionInformation(session.getId());
            if (sessionInformation != null && sessionInformation.isExpired()) {
                logoutHandler.logout(request, response, authentication);
                response.setStatus(expiredStatus);
                return;
            }
            registerOrRefreshSession(session, authentication);
        }

        filterChain.doFilter(request, response);

        // A preceding servlet filter may see neither a session nor authentication before form login.
        // The security proxy may also have cleared its thread context before returning here.
        HttpSession authenticatedSession = request.getSession(false);
        if (authenticatedSession != null) {
            registerOrRefreshSession(authenticatedSession, authentication(authenticatedSession));
        }
    }

    private Authentication authentication(HttpSession session) {
        Authentication current = SecurityUtil.getAuthentication().orElse(null);
        if (current != null || session == null) {
            return current;
        }
        try {
            Object stored = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (stored instanceof SecurityContext context) {
                Authentication saved = context.getAuthentication();
                if (saved != null && saved.isAuthenticated() && !(saved instanceof AnonymousAuthenticationToken)) {
                    return saved;
                }
            }
        } catch (IllegalStateException invalidated) {
            // The security chain or another request may have invalidated this session.
        }
        return null;
    }

    private void registerOrRefreshSession(HttpSession session, Authentication authentication) {
        SecuritySessionRegistrationListener.registerOrRefreshSession(sessionRegistry, session, authentication);
    }
}
