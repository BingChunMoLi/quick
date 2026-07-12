package com.bingchunmoli.security.context;

import com.bingchunmoli.security.util.SecurityUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Optional;

/**
 * Bean facade for reading the current Spring Security user.
 *
 * @author MoLi
 */
public class SecurityUserContext {

    public Optional<Authentication> getAuthentication() {
        return SecurityUtil.getAuthentication();
    }

    public Optional<Object> getPrincipal() {
        return SecurityUtil.getPrincipal();
    }

    public <T> Optional<T> getPrincipal(Class<T> principalType) {
        return SecurityUtil.getPrincipal(principalType);
    }

    public Optional<String> getUsername() {
        return SecurityUtil.getUsername();
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return SecurityUtil.getAuthorities();
    }

    public boolean isAuthenticated() {
        return SecurityUtil.isAuthenticated();
    }

    public boolean hasAuthority(String authority) {
        return SecurityUtil.hasAuthority(authority);
    }

    public boolean hasRole(String role) {
        return SecurityUtil.hasRole(role);
    }
}
