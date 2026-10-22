package com.bingchunmoli.oauth.client.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("moli.oauth.client.google")
public class GoogleOAuth2Properties {
    /** Whether to request offline access for refresh-token support. */
    private boolean offline;
    /** Optional Google authorization prompt parameter. */
    private String prompt;
    public boolean isOffline() { return offline; }
    public void setOffline(boolean value) { offline = value; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String value) { prompt = value; }
}
