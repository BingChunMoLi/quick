package com.bingchunmoli.oauth.server.authorization.web;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.security.web.SecurityFilterChain;
/** Distinguishes Quick's narrow protocol chain from a host business chain. */
public final class AuthorizationProtocolChain implements SecurityFilterChain, Ordered {
    private final SecurityFilterChain delegate; private final int order;
    public AuthorizationProtocolChain(SecurityFilterChain delegate, int order) { this.delegate = delegate; this.order = order; }
    public boolean matches(HttpServletRequest request) { return delegate.matches(request); }
    public List<Filter> getFilters() { return delegate.getFilters(); }
    public int getOrder() { return order; }
}
