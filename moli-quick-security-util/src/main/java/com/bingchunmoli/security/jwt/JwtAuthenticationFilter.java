package com.bingchunmoli.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates requests with a Bearer JWT token.
 *
 * @author MoLi
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenResolver tokenResolver;

    private final JwtTokenService tokenService;

    public JwtAuthenticationFilter(JwtTokenResolver tokenResolver, JwtTokenService tokenService) {
        this.tokenResolver = tokenResolver;
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        tokenResolver.resolve(request)
                .filter(token -> SecurityContextHolder.getContext().getAuthentication() == null)
                .ifPresent(token -> SecurityContextHolder.getContext().setAuthentication(tokenService.authenticate(token)));
        filterChain.doFilter(request, response);
    }
}
