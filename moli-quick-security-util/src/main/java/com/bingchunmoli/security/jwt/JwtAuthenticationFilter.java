package com.bingchunmoli.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;
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

    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(JwtTokenResolver tokenResolver, JwtTokenService tokenService) {
        this(tokenResolver, tokenService, new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
    }

    public JwtAuthenticationFilter(JwtTokenResolver tokenResolver, JwtTokenService tokenService,
                                   AuthenticationEntryPoint authenticationEntryPoint) {
        this.tokenResolver = tokenResolver;
        this.tokenService = tokenService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            tokenResolver.resolve(request)
                    .filter(token -> SecurityContextHolder.getContext().getAuthentication() == null)
                    .ifPresent(token -> SecurityContextHolder.getContext().setAuthentication(tokenService.authenticate(token)));
        } catch (JwtTokenException failure) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("Invalid bearer token", failure));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
