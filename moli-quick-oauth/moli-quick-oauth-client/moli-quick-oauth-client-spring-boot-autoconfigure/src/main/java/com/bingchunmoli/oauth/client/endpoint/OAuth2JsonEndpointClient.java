package com.bingchunmoli.oauth.client.endpoint;

import java.net.URI;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** Data-only JSON transport for providers whose token endpoints do not use standard OAuth POST. */
public final class OAuth2JsonEndpointClient {
    private final RestClient http;
    private final JsonMapper json = JsonMapper.builder().build();
    public OAuth2JsonEndpointClient(RestClient.Builder builder) { http = builder.clone().build(); }

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(URI uri) {
        try {
            // Decode JSON bytes as JSON, even when the provider labels UTF-8 text as text/plain.
            byte[] body = http.get().uri(uri).accept(MediaType.APPLICATION_JSON).retrieve().body(byte[].class);
            if (body == null || body.length > 1_000_000) throw new IllegalArgumentException();
            return json.readValue(body, Map.class);
        } catch (RuntimeException ex) {
            // Transport exceptions may include a URI containing client secrets or tokens.
            throw failure("invalid_provider_response");
        }
    }
    public static OAuth2AuthorizationException failure(String code) {
        return new OAuth2AuthorizationException(new OAuth2Error(code), "OAuth2 provider request failed");
    }
    public static String required(Map<String, Object> data, String key) {
        if (!(data.get(key) instanceof String value) || value.isBlank()) throw failure("invalid_provider_response");
        return value;
    }
    public static OAuth2AccessTokenResponse token(Map<String, Object> data, Set<String> fallbackScopes) {
        try {
            long expires = Long.parseLong(String.valueOf(data.get("expires_in")));
            if (expires <= 0) throw new IllegalArgumentException();
            Set<String> scopes = fallbackScopes;
            if (data.get("scope") instanceof String value) {
                scopes = new LinkedHashSet<>();
                for (String scope : value.split("[,\\s]+")) if (!scope.isBlank()) scopes.add(scope);
            }
            Map<String, Object> extra = new LinkedHashMap<>();
            for (String key : List.of("openid", "unionid", "refresh_token_expires_in")) {
                if (data.get(key) != null) extra.put(key, data.get(key));
            }
            return OAuth2AccessTokenResponse.withToken(required(data, "access_token"))
                    .tokenType(OAuth2AccessToken.TokenType.BEARER).expiresIn(expires).scopes(scopes)
                    .refreshToken((String) data.get("refresh_token")).additionalParameters(extra).build();
        } catch (RuntimeException ex) { throw failure("invalid_token_response"); }
    }
}
