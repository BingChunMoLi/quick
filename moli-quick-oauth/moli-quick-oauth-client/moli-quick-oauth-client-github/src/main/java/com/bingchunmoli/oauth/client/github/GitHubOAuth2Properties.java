package com.bingchunmoli.oauth.client.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("moli.oauth.client.github")
public class GitHubOAuth2Properties {
    /** Whether to request supplementary email information from GitHub. */
    private boolean fetchEmail;
    /** GitHub endpoint used to retrieve email addresses. */
    private String emailUri = "https://api.github.com/user/emails";
    public boolean isFetchEmail() { return fetchEmail; }
    public void setFetchEmail(boolean value) { fetchEmail = value; }
    public String getEmailUri() { return emailUri; }
    public void setEmailUri(String value) { emailUri = value; }
}
