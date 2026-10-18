package com.bingchunmoli.oauth.client;

/** Safe to report without copying provider error descriptions. */
public final class AuthorizationRequiredException extends IllegalStateException {
    public AuthorizationRequiredException() { super("OAuth2 authorization is required"); }
}
