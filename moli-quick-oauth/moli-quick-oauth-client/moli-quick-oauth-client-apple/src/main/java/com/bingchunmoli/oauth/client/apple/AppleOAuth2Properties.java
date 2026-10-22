package com.bingchunmoli.oauth.client.apple;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("moli.oauth.client.apple")
public class AppleOAuth2Properties {
    private String teamId;
    private String keyId;
    /** PKCS#8 PEM .p8 key, normally file:/run/secrets/AuthKey_....p8. Never commit the private key. */
    private Resource privateKey;
    /** Lifetime of each generated ES256 client secret; at most 15777000 seconds. */
    private Duration clientSecretTtl = Duration.ofMinutes(5);
    public String getTeamId() { return teamId; }
    public void setTeamId(String value) { teamId = value; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String value) { keyId = value; }
    public Resource getPrivateKey() { return privateKey; }
    public void setPrivateKey(Resource value) { privateKey = value; }
    public Duration getClientSecretTtl() { return clientSecretTtl; }
    public void setClientSecretTtl(Duration value) { clientSecretTtl = value; }
}
