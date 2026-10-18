package com.bingchunmoli.oauth.client.web;

import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** JSON contains data only: no polymorphic type metadata, credentials or Java serialization. */
public final class AuthorizationRequestCodec {
    private final JsonMapper mapper = JsonMapper.builder().build();
    public String encode(OAuth2AuthorizationRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", 1); data.put("authorizationUri", request.getAuthorizationUri());
        data.put("clientId", request.getClientId()); data.put("redirectUri", request.getRedirectUri());
        data.put("scopes", request.getScopes()); data.put("state", request.getState());
        data.put("attributes", request.getAttributes()); data.put("additional", request.getAdditionalParameters());
        data.put("requestUri", request.getAuthorizationRequestUri());
        return mapper.writeValueAsString(data);
    }
    @SuppressWarnings("unchecked")
    public OAuth2AuthorizationRequest decode(String json) {
        try {
            if (json.length() > 1_000_000) throw new IllegalArgumentException();
            Map<String, Object> data = mapper.readValue(json, Map.class);
            if (!Integer.valueOf(1).equals(data.get("version"))) throw new IllegalArgumentException();
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri((String) data.get("authorizationUri")).clientId((String) data.get("clientId"))
                    .redirectUri((String) data.get("redirectUri")).scopes(new LinkedHashSet<>((List<String>) data.get("scopes")))
                    .state((String) data.get("state")).attributes(a -> a.putAll((Map<String, Object>) data.get("attributes")))
                    .additionalParameters((Map<String, Object>) data.get("additional"))
                    .authorizationRequestUri((String) data.get("requestUri")).build();
        } catch (RuntimeException ex) { throw new IllegalStateException("Invalid OAuth2 authorization state"); }
    }
}
