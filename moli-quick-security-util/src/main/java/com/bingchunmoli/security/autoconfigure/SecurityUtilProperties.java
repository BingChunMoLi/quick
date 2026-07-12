package com.bingchunmoli.security.autoconfigure;

import com.bingchunmoli.security.jwt.JwtTokenProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.List;

/**
 * Properties for security utility integration.
 *
 * @author MoLi
 */
@ConfigurationProperties(prefix = "moli.security")
public class SecurityUtilProperties {

    private boolean enabled = true;

    private boolean defaultFilterChainEnabled = true;

    private boolean csrfEnabled = false;

    private boolean formLoginEnabled = false;

    private boolean httpBasicEnabled = false;

    private boolean forceLogoutFilterEnabled = true;

    private JwtTokenProperties jwt = new JwtTokenProperties();

    private int filterOrder = Ordered.LOWEST_PRECEDENCE - 100;

    private int expiredStatus = 401;

    private List<String> permitAll = new ArrayList<>(List.of("/error"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDefaultFilterChainEnabled() {
        return defaultFilterChainEnabled;
    }

    public void setDefaultFilterChainEnabled(boolean defaultFilterChainEnabled) {
        this.defaultFilterChainEnabled = defaultFilterChainEnabled;
    }

    public boolean isCsrfEnabled() {
        return csrfEnabled;
    }

    public void setCsrfEnabled(boolean csrfEnabled) {
        this.csrfEnabled = csrfEnabled;
    }

    public boolean isFormLoginEnabled() {
        return formLoginEnabled;
    }

    public void setFormLoginEnabled(boolean formLoginEnabled) {
        this.formLoginEnabled = formLoginEnabled;
    }

    public boolean isHttpBasicEnabled() {
        return httpBasicEnabled;
    }

    public void setHttpBasicEnabled(boolean httpBasicEnabled) {
        this.httpBasicEnabled = httpBasicEnabled;
    }

    public JwtTokenProperties getJwt() {
        return jwt;
    }

    public void setJwt(JwtTokenProperties jwt) {
        this.jwt = jwt == null ? new JwtTokenProperties() : jwt;
    }

    public boolean isForceLogoutFilterEnabled() {
        return forceLogoutFilterEnabled;
    }

    public void setForceLogoutFilterEnabled(boolean forceLogoutFilterEnabled) {
        this.forceLogoutFilterEnabled = forceLogoutFilterEnabled;
    }

    public int getFilterOrder() {
        return filterOrder;
    }

    public void setFilterOrder(int filterOrder) {
        this.filterOrder = filterOrder;
    }

    public int getExpiredStatus() {
        return expiredStatus;
    }

    public void setExpiredStatus(int expiredStatus) {
        this.expiredStatus = expiredStatus;
    }

    public List<String> getPermitAll() {
        return permitAll;
    }

    public void setPermitAll(List<String> permitAll) {
        this.permitAll = permitAll == null ? new ArrayList<>() : permitAll;
    }
}
