package com.bingchunmoli.oauth.client;

import org.springframework.util.Assert;

/** Identifies a provider authorization; the principal is not a local business user id. */
public record AuthorizedClientRef(String registrationId, String principalName) {
    public AuthorizedClientRef {
        Assert.hasText(registrationId, "registrationId is required");
        Assert.hasText(principalName, "principalName is required");
    }
}
