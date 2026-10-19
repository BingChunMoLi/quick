package com.bingchunmoli.oauth.server.resource.web;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.*;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.web.*;
import org.springframework.security.web.access.AccessDeniedHandler;
public final class QuickResourceServerWebSupport {
    private final JwtDecoder decoder;
    private final OpaqueTokenIntrospector introspector;
    private final Converter<Jwt, ? extends AbstractAuthenticationToken> converter;
    private final OpaqueTokenAuthenticationConverter opaqueConverter;
    private final BearerTokenResolver resolver;
    private final AuthenticationEntryPoint entryPoint;
    private final AccessDeniedHandler denied;
    private final AtomicBoolean applied=new AtomicBoolean();
    public QuickResourceServerWebSupport(JwtDecoder decoder, OpaqueTokenIntrospector introspector,
            Converter<Jwt, ? extends AbstractAuthenticationToken> converter, OpaqueTokenAuthenticationConverter opaqueConverter,
            BearerTokenResolver resolver, AuthenticationEntryPoint entryPoint, AccessDeniedHandler denied) {
        this.decoder=decoder; this.introspector=introspector; this.converter=converter; this.opaqueConverter=opaqueConverter;
        this.resolver=resolver;this.entryPoint=entryPoint;this.denied=denied;
    }
    public boolean isApplied() { return applied.get(); }
    public Configurer configurer() { return new Configurer(); }
    public final class Configurer extends AbstractHttpConfigurer<Configurer,HttpSecurity> {
        @Override public void init(HttpSecurity http) {
            applied.set(true);
            org.springframework.security.authentication.AuthenticationProvider provider;
            if(decoder!=null) {
                var jwt=new JwtAuthenticationProvider(decoder);jwt.setJwtAuthenticationConverter(converter);provider=jwt;
            } else {
                var opaque=new OpaqueTokenAuthenticationProvider(introspector);opaque.setAuthenticationConverter(opaqueConverter);provider=opaque;
            }
            // Native resource configurer changes CsrfConfigurer even on host chains. Compose the native filter/providers
            // directly so Quick's configurer preserves the host's CSRF, Session and request-cache decisions.
            var filter=new BearerTokenAuthenticationFilter(new org.springframework.security.authentication.ProviderManager(provider));
            filter.setBearerTokenResolver(resolver);filter.setAuthenticationEntryPoint(entryPoint);
            filter.setSecurityContextRepository(new org.springframework.security.web.context.RequestAttributeSecurityContextRepository());
            http.addFilterBefore(filter,org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
            http.exceptionHandling(errors -> errors
                .defaultAuthenticationEntryPointFor(entryPoint,org.springframework.security.web.util.matcher.AnyRequestMatcher.INSTANCE)
                .defaultAccessDeniedHandlerFor(denied,org.springframework.security.web.util.matcher.AnyRequestMatcher.INSTANCE));
        }
    }
}
