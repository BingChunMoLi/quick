package com.bingchunmoli.storage.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Amazon S3 and S3-compatible connection settings.
 */
@ConfigurationProperties(prefix = "moli.object-storage.s3")
public class S3StorageProperties {

    private boolean enabled;
    private String region = "us-east-1";
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String sessionToken;
    private boolean pathStyleAccess;

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

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
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

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }
}
