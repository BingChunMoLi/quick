package com.bingchunmoli.storage.cos;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tencent Cloud COS connection settings.
 */
@ConfigurationProperties(prefix = "moli.object-storage.cos")
public class CosStorageProperties {

    private boolean enabled;
    private String region;
    private String secretId;
    private String secretKey;
    private String sessionToken;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }
}
