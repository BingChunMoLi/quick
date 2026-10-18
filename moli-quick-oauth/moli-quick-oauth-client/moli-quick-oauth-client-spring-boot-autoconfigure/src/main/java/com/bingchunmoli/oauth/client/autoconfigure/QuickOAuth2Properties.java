package com.bingchunmoli.oauth.client.autoconfigure;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("moli.oauth.client")
public class QuickOAuth2Properties {
    private boolean enabled;
    private final Login login = new Login();
    private final AuthorizationState authorizationState = new AuthorizationState();
    private final AuthorizedClient authorizedClient = new AuthorizedClient();
    private final Refresh refresh = new Refresh();
    private final Redis redis = new Redis();
    private final Map<String, String> providerMappings = new HashMap<>();
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Login getLogin() { return login; }
    public AuthorizationState getAuthorizationState() { return authorizationState; }
    public AuthorizedClient getAuthorizedClient() { return authorizedClient; }
    public Refresh getRefresh() { return refresh; }
    public Redis getRedis() { return redis; }
    public Map<String, String> getProviderMappings() { return providerMappings; }
    public static class Redis {
        private String keyPrefix = "moli:oauth2";
        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String value) {
            if (value == null || value.isBlank() || value.contains("{") || value.contains("}")) {
                throw new IllegalArgumentException("OAuth2 Redis key prefix must be nonblank and contain no hash tags");
            }
            keyPrefix = value;
        }
    }
    public static class Login {
        private boolean enabled;
        private String authorizationBaseUri = "/oauth2/authorization";
        private String callbackBaseUri = "/login/oauth2/code/*";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public String getAuthorizationBaseUri() { return authorizationBaseUri; }
        public void setAuthorizationBaseUri(String value) { authorizationBaseUri = value; }
        public String getCallbackBaseUri() { return callbackBaseUri; }
        public void setCallbackBaseUri(String value) { callbackBaseUri = value; }
    }
    public static class AuthorizationState {
        private String store = "session";
        private Duration ttl = Duration.ofMinutes(10);
        private boolean cookieSecure = true;
        public String getStore() { return store; }
        public void setStore(String value) { store = value; }
        public Duration getTtl() { return ttl; }
        public void setTtl(Duration value) { ttl = value; }
        public boolean isCookieSecure() { return cookieSecure; }
        public void setCookieSecure(boolean value) { cookieSecure = value; }
    }
    public static class AuthorizedClient {
        private String store = "memory";
        public String getStore() { return store; }
        public void setStore(String value) { store = value; }
    }
    public static class Refresh {
        private String coordination = "auto";
        private Duration waitTimeout = Duration.ofSeconds(15);
        private Duration lease = Duration.ofSeconds(30);
        public String getCoordination() { return coordination; }
        public void setCoordination(String value) { coordination = value; }
        public Duration getWaitTimeout() { return waitTimeout; }
        public void setWaitTimeout(Duration value) { waitTimeout = value; }
        public Duration getLease() { return lease; }
        public void setLease(Duration value) { lease = value; }
    }
}
