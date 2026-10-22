package com.bingchunmoli.oauth.client.microsoft;

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
    public String getTenant() { return tenant; }
    public void setTenant(String value) { tenant = value; }
    public Set<String> getAllowedTenants() { return allowedTenants; }
    public void setAllowedTenants(Set<String> value) { allowedTenants = value; }
    public boolean isOffline() { return offline; }
    public void setOffline(boolean value) { offline = value; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String value) { prompt = value; }
}
