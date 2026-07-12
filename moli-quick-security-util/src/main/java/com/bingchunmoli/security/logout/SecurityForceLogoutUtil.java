package com.bingchunmoli.security.logout;

import com.bingchunmoli.security.jwt.JwtTokenService;

/**
 * Lightweight static helpers for force logout operations.
 *
 * @author MoLi
 */
public final class SecurityForceLogoutUtil {

    private SecurityForceLogoutUtil() {
    }

    public static int forceLogoutByUserId(JwtTokenService tokenService, String userId) {
        return tokenService.forceLogoutByUserId(userId);
    }

    public static boolean forceLogoutSession(JwtTokenService tokenService, String sessionId) {
        return tokenService.forceLogoutSession(sessionId);
    }

    public static void forceLogoutToken(JwtTokenService tokenService, String token) {
        tokenService.forceLogoutToken(token);
    }

    public static int forceLogoutSpringSecurityUser(SecurityLogoutService logoutService, String username) {
        return logoutService.forceLogoutByUsername(username);
    }
}
