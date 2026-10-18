package com.bingchunmoli.oauth.client.web;

import com.bingchunmoli.oauth.client.TokenResponseMetadata;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/** Keeps protocol metadata scoped to a callback, including unexpected host/storage failures. */
final class OAuth2CallbackMetadataFilter extends OncePerRequestFilter {
    private final RequestMatcher callback;

    OAuth2CallbackMetadataFilter(String callbackBaseUri) {
        callback = PathPatternRequestMatcher.withDefaults().matcher(callbackBaseUri);
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !callback.matches(request); }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try (var scope = TokenResponseMetadata.scope()) { chain.doFilter(request, response); }
    }
}
