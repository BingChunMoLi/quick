package com.bingchunmoli.oauth.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Normalized third-party identity. Email is optional and never an account-linking key. */
public record OAuth2Identity(String provider, String subject, String name, String avatar,
                             String email, Map<String, Object> attributes) {
    public OAuth2Identity {
        if (provider == null || provider.isBlank() || subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Provider and stable subject are required");
        }
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    @Override public String toString() { return "OAuth2Identity[provider=" + provider + "]"; }
}
