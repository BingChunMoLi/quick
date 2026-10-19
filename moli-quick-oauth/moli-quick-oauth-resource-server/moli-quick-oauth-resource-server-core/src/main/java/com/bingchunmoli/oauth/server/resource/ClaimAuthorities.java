package com.bingchunmoli.oauth.server.resource;
import java.util.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
/** Maps only configured, explicitly named claims. */
public final class ClaimAuthorities {
    private ClaimAuthorities() { }
    public static Collection<GrantedAuthority> map(Map<String,Object> claims, String scopeClaim, String scopePrefix, String roleClaim, String rolePrefix) {
        Set<GrantedAuthority> result = new LinkedHashSet<>();
        Object scope = claims.get(scopeClaim);
        if (scope == null && "scope".equals(scopeClaim)) scope = claims.get("scp");
        add(result, scope, scopePrefix);
        if (roleClaim != null && !roleClaim.isBlank()) add(result, claims.get(roleClaim), rolePrefix);
        return List.copyOf(result);
    }
    private static void add(Set<GrantedAuthority> result, Object value, String prefix) {
        if (value instanceof String text) for (String entry : text.trim().split("\\s+")) {
            if (!entry.isBlank()) result.add(new SimpleGrantedAuthority(prefix + entry));
        } else if (value instanceof Collection<?> entries) for (Object entry : entries) {
            if (entry instanceof String text && !text.isBlank()) result.add(new SimpleGrantedAuthority(prefix + text));
        }
    }
}
