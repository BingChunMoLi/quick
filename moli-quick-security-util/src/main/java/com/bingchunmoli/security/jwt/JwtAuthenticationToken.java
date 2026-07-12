package com.bingchunmoli.security.jwt;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Spring Security authentication produced by JWT validation.
 *
 * @author MoLi
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final JwtUser principal;

    private final String token;

    public JwtAuthenticationToken(JwtUser principal, String token) {
        super(principal.getAuthorities().stream().map(SimpleGrantedAuthority::new).toList());
        this.principal = principal;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public JwtUser getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.getUsername();
    }
}
