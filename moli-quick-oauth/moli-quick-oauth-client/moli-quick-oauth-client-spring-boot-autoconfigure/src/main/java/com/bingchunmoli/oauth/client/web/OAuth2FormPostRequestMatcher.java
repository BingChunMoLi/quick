package com.bingchunmoli.oauth.client.web;

import java.net.URI;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Exempts only browser-bound, pending form_post callbacks; business POSTs retain CSRF protection. */
final class OAuth2FormPostRequestMatcher implements RequestMatcher {
    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> states;
    private final RequestMatcher callback;
    OAuth2FormPostRequestMatcher(AuthorizationRequestRepository<OAuth2AuthorizationRequest> states, String callbackUri) {
        this.states = states;
        callback = PathPatternRequestMatcher.withDefaults().matcher(callbackUri);
    }
    @Override public boolean matches(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod()) || !callback.matches(request) || !single(request, "state")) return false;
        if (single(request, "code") == single(request, "error")) return false;
        try {
            if (request.getContentType() == null || !MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(
                    MediaType.parseMediaType(request.getContentType()))) return false;
            var saved = states.loadAuthorizationRequest(request);
            return saved != null && "form_post".equals(saved.getAdditionalParameters().get("response_mode"))
                    && saved.getState().equals(request.getParameter("state"))
                    && URI.create(saved.getRedirectUri()).getRawPath().equals(request.getRequestURI());
        } catch (IllegalArgumentException invalid) { return false; }
    }
    private static boolean single(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        return values != null && values.length == 1 && values[0] != null && !values[0].isBlank();
    }
}
