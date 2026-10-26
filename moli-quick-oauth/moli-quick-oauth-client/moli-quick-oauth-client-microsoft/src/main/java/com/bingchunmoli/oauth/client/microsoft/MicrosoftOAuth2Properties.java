package com.bingchunmoli.oauth.client.microsoft;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("moli.oauth.client.microsoft")
public class MicrosoftOAuth2Properties {
    /** common, organizations, consumers, or a directory GUID; applies to Azure public cloud. */
    private String tenant = "common";
    /** Optional directory GUID allowlist, in addition to the selected tenant audience. */
    private Set<String> allowedTenants = new LinkedHashSet<>();
    /** Include offline_access in the default scopes. Explicit registration scopes take precedence. */
    private boolean offline;
    private String prompt;
    /** Connection timeout for the default dedicated JWKS HTTP transport. */
    private Duration jwkConnectTimeout = Duration.ofSeconds(5);
    /** Response/read timeout for the default dedicated JWKS HTTP transport. */
    private Duration jwkReadTimeout = Duration.ofSeconds(5);
    /** Maximum wait for another request refreshing the same decoder's keys. */
    private Duration jwkRefreshWaitTimeout = Duration.ofSeconds(5);
    public String getTenant() { return tenant; }
    public void setTenant(String value) { tenant = value; }
    public Set<String> getAllowedTenants() { return allowedTenants; }
    public void setAllowedTenants(Set<String> value) { allowedTenants = value; }
    public boolean isOffline() { return offline; }
    public void setOffline(boolean value) { offline = value; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String value) { prompt = value; }
    public Duration getJwkConnectTimeout() { return jwkConnectTimeout; }
    public void setJwkConnectTimeout(Duration value) { jwkConnectTimeout = value; }
    public Duration getJwkReadTimeout() { return jwkReadTimeout; }
    public void setJwkReadTimeout(Duration value) { jwkReadTimeout = value; }
    public Duration getJwkRefreshWaitTimeout() { return jwkRefreshWaitTimeout; }
    public void setJwkRefreshWaitTimeout(Duration value) { jwkRefreshWaitTimeout = value; }

    void validateTimeouts() {
        positiveMillis(jwkConnectTimeout, "jwk-connect-timeout");
        positiveMillis(jwkReadTimeout, "jwk-read-timeout");
        positiveMillis(jwkRefreshWaitTimeout, "jwk-refresh-wait-timeout");
    }
    private static void positiveMillis(Duration value, String name) {
        try {
            if (value != null && !value.isNegative() && value.toMillis() > 0 && value.toNanos() > 0) return;
        } catch (ArithmeticException invalid) { /* Reject values that cannot be used as finite waits. */ }
        throw new IllegalArgumentException("Microsoft " + name + " must be a positive duration of at least 1ms");
    }
}
