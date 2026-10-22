package com.bingchunmoli.oauth.client.wechat;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("moli.oauth.client.wechat")
public class WeChatOAuth2Properties {
    /** WeChat endpoint used to refresh an authorized user's access token. */
    private String refreshTokenUri = "https://api.weixin.qq.com/sns/oauth2/refresh_token";
    /** Preferred language for user information. */
    private String language = "zh_CN";
    public String getRefreshTokenUri() { return refreshTokenUri; }
    public void setRefreshTokenUri(String value) { refreshTokenUri = value; }
    public String getLanguage() { return language; }
    public void setLanguage(String value) { language = value; }
}
