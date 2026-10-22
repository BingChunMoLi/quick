package com.bingchunmoli.oauth.client.qq;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("moli.oauth.client.qq")
public class QqOAuth2Properties {
    /** QQ endpoint used to retrieve the authorized user's OpenID. */
    private String openIdUri = "https://graph.qq.com/oauth2.0/me";
    public String getOpenIdUri() { return openIdUri; }
    public void setOpenIdUri(String value) { openIdUri = value; }
}
