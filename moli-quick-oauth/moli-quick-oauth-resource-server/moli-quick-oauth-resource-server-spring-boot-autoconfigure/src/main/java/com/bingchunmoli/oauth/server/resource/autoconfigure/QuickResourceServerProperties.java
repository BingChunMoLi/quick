package com.bingchunmoli.oauth.server.resource.autoconfigure;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("moli.oauth.resource-server")
public class QuickResourceServerProperties {
    private boolean enabled, defaultFilterChainEnabled = true, methodSecurityEnabled = true;
    private String mode = "jwt", principalClaim = "sub", scopeClaim = "scope", scopePrefix = "SCOPE_", rolesClaim, rolesPrefix = "ROLE_";
    private List<String> permitAll = new ArrayList<>();
    private java.time.Duration connectTimeout=java.time.Duration.ofSeconds(3),readTimeout=java.time.Duration.ofSeconds(5);
    public java.time.Duration getConnectTimeout(){return connectTimeout;} public void setConnectTimeout(java.time.Duration value){connectTimeout=value;}
    public java.time.Duration getReadTimeout(){return readTimeout;} public void setReadTimeout(java.time.Duration value){readTimeout=value;}
    public boolean isEnabled() { return enabled; } public void setEnabled(boolean v) { enabled=v; }
    public boolean isDefaultFilterChainEnabled() { return defaultFilterChainEnabled; } public void setDefaultFilterChainEnabled(boolean v) { defaultFilterChainEnabled=v; }
    public boolean isMethodSecurityEnabled() { return methodSecurityEnabled; } public void setMethodSecurityEnabled(boolean v) { methodSecurityEnabled=v; }
    public String getMode() { return mode; } public void setMode(String v) { mode=v.toLowerCase(Locale.ROOT); }
    public String getPrincipalClaim() { return principalClaim; } public void setPrincipalClaim(String v) { principalClaim=v; }
    public String getScopeClaim() { return scopeClaim; } public void setScopeClaim(String v) { scopeClaim=v; }
    public String getScopePrefix() { return scopePrefix; } public void setScopePrefix(String v) { scopePrefix=v; }
    public String getRolesClaim() { return rolesClaim; } public void setRolesClaim(String v) { rolesClaim=v; }
    public String getRolesPrefix() { return rolesPrefix; } public void setRolesPrefix(String v) { rolesPrefix=v; }
    public List<String> getPermitAll() { return permitAll; } public void setPermitAll(List<String> v) { permitAll=v; }
}
