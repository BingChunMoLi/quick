package com.bingchunmoli.oauth.server.resource.autoconfigure;
import com.bingchunmoli.oauth.server.resource.ClaimAuthorities;
import com.bingchunmoli.oauth.server.resource.web.QuickResourceServerWebSupport;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.oauth2.server.resource.introspection.*;
import org.springframework.security.oauth2.server.resource.web.*;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.web.*;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
@AutoConfiguration(beforeName={"org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
    "com.bingchunmoli.security.autoconfigure.SecurityUtilAutoConfiguration"},
    afterName="com.bingchunmoli.oauth.server.authorization.autoconfigure.QuickAuthorizationServerAutoConfiguration")
@ConditionalOnProperty(prefix="moli.oauth.resource-server",name="enabled",havingValue="true")
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({QuickResourceServerProperties.class,OAuth2ResourceServerProperties.class})
@EnableWebSecurity
public class QuickResourceServerAutoConfiguration {
    @Bean(name="quickResourceJwtDecoder")
    @ConditionalOnProperty(prefix="moli.oauth.resource-server",name="mode",havingValue="jwt",matchIfMissing=true)
    @ConditionalOnMissingBean(name="quickResourceJwtDecoder")
    JwtDecoder quickResourceJwtDecoder(OAuth2ResourceServerProperties nativeProperties, ListableBeanFactory beans) throws Exception {
        var custom=Arrays.stream(beans.getBeanNamesForType(JwtDecoder.class)).filter(name -> !Set.of("quickResourceJwtDecoder","quickAuthorizationServerJwtDecoder").contains(name)).toList();
        if(custom.size()>1) throw new IllegalStateException("Name the resource decoder quickResourceJwtDecoder when multiple decoders exist");
        if(custom.size()==1) return beans.getBean(custom.get(0),JwtDecoder.class);
        var jwt=nativeProperties.getJwt();
        if(jwt.getIssuerUri()==null || jwt.getIssuerUri().isBlank() || jwt.getAudiences()==null || jwt.getAudiences().isEmpty())
            throw new IllegalStateException("JWT resources require issuer-uri and audiences");
        NimbusJwtDecoder decoder;
        if(jwt.getPublicKeyLocation()!=null) {
            try(var in=jwt.getPublicKeyLocation().getInputStream()) {
                var builder=NimbusJwtDecoder.withPublicKey(RsaKeyConverters.x509().convert(in));
                if(jwt.getJwsAlgorithms()!=null && !jwt.getJwsAlgorithms().isEmpty()) {
                    if(jwt.getJwsAlgorithms().size()!=1 || SignatureAlgorithm.from(jwt.getJwsAlgorithms().get(0))==null)
                        throw new IllegalStateException("Public-key JWT verification requires one supported signature algorithm");
                    builder.signatureAlgorithm(SignatureAlgorithm.from(jwt.getJwsAlgorithms().get(0)));
                }
                decoder=builder.build();
            }
        } else {
            var builder=jwt.getJwkSetUri()!=null ? NimbusJwtDecoder.withJwkSetUri(jwt.getJwkSetUri()) : NimbusJwtDecoder.withIssuerLocation(jwt.getIssuerUri());
            builder.jwsAlgorithms(algorithms -> {
                algorithms.clear();
                var names=jwt.getJwsAlgorithms();
                if(names==null || names.isEmpty()) algorithms.add(SignatureAlgorithm.RS256);
                else for(String name:names) { var alg=SignatureAlgorithm.from(name); if(alg==null) throw new IllegalStateException("Unsupported JWT algorithm"); algorithms.add(alg); }
            });
            decoder=builder.build();
        }
        OAuth2TokenValidator<Jwt> audience=token -> token.getAudience()!=null && token.getAudience().stream().anyMatch(jwt.getAudiences()::contains) ?
                OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Required audience missing",null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(jwt.getIssuerUri()),audience));
        return decoder;
    }
    @Bean @ConditionalOnMissingBean(OpaqueTokenIntrospector.class)
    @ConditionalOnProperty(prefix="moli.oauth.resource-server",name="mode",havingValue="opaque")
    OpaqueTokenIntrospector quickOpaqueIntrospector(OAuth2ResourceServerProperties properties,QuickResourceServerProperties quick) {
        var token=properties.getOpaquetoken();
        if(token.getIntrospectionUri()==null || token.getClientId()==null || token.getClientSecret()==null)
            throw new IllegalStateException("Opaque resources require introspection-uri and credentials");
        if(quick.getConnectTimeout()==null || quick.getConnectTimeout().isNegative() || quick.getConnectTimeout().isZero() ||
                quick.getReadTimeout()==null || quick.getReadTimeout().isNegative() || quick.getReadTimeout().isZero())
            throw new IllegalStateException("Positive introspection timeouts required");
        var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(quick.getConnectTimeout()).build());
        factory.setReadTimeout(quick.getReadTimeout());
        var rest=new org.springframework.web.client.RestTemplate(factory);
        rest.getInterceptors().add(new org.springframework.http.client.support.BasicAuthenticationInterceptor(token.getClientId(),token.getClientSecret()));
        var delegate=new SpringOpaqueTokenIntrospector(token.getIntrospectionUri(),rest);
        return value -> {
            try {return delegate.introspect(value);}
            catch(RuntimeException unavailable) {throw new BadOpaqueTokenException("Bearer token verification failed");}
        };
    }
    @Bean(name="quickResourceJwtAuthenticationConverter") @ConditionalOnMissingBean(name="quickResourceJwtAuthenticationConverter")
    Converter<Jwt,? extends AbstractAuthenticationToken> quickResourceJwtConverter(QuickResourceServerProperties quick) {
        return jwt -> new JwtAuthenticationToken(jwt,ClaimAuthorities.map(jwt.getClaims(),quick.getScopeClaim(),quick.getScopePrefix(),
                quick.getRolesClaim(),quick.getRolesPrefix()),principalName(jwt.getClaims().get(quick.getPrincipalClaim())));
    }
    @Bean @ConditionalOnMissingBean(OpaqueTokenAuthenticationConverter.class)
    OpaqueTokenAuthenticationConverter quickOpaqueConverter(QuickResourceServerProperties quick) {
        return (value,principal) -> {
            var authorities=ClaimAuthorities.map(principal.getAttributes(),quick.getScopeClaim(),quick.getScopePrefix(),quick.getRolesClaim(),quick.getRolesPrefix());
            Object name=principal.getAttribute(quick.getPrincipalClaim());
            // Preserve native opaque principal naming when the default sub claim is absent.
            if(name==null && "sub".equals(quick.getPrincipalClaim()))name=principal.getName();
            var mapped=new DefaultOAuth2AuthenticatedPrincipal(principalName(name),principal.getAttributes(),authorities);
            return new BearerTokenAuthentication(mapped,new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,value,
                    principal.getAttribute("iat"),principal.getAttribute("exp")),authorities);
        };
    }
    private static String principalName(Object value) {
        if(!(value instanceof String) && !(value instanceof Number))
            throw new org.springframework.security.oauth2.server.resource.InvalidBearerTokenException("Invalid principal claim");
        String name=value.toString();
        if(name.isBlank())throw new org.springframework.security.oauth2.server.resource.InvalidBearerTokenException("Invalid principal claim");
        return name;
    }
    @Bean @ConditionalOnMissingBean(BearerTokenResolver.class)
    BearerTokenResolver quickBearerResolver() { return new DefaultBearerTokenResolver(); }
    @Bean(name="quickResourceAuthenticationEntryPoint") @ConditionalOnMissingBean(name="quickResourceAuthenticationEntryPoint")
    AuthenticationEntryPoint quickEntryPoint() { return new BearerTokenAuthenticationEntryPoint(); }
    @Bean(name="quickResourceAccessDeniedHandler") @ConditionalOnMissingBean(name="quickResourceAccessDeniedHandler")
    AccessDeniedHandler quickDeniedHandler() { return new BearerTokenAccessDeniedHandler(); }
    @Bean @ConditionalOnMissingBean
    QuickResourceServerWebSupport quickResourceServerWebSupport(QuickResourceServerProperties quick, ListableBeanFactory beans,
            @Qualifier("quickResourceJwtAuthenticationConverter") Converter<Jwt,? extends AbstractAuthenticationToken> converter,
            ObjectProvider<OpaqueTokenIntrospector> introspector, OpaqueTokenAuthenticationConverter opaqueConverter,
            BearerTokenResolver resolver, @Qualifier("quickResourceAuthenticationEntryPoint") AuthenticationEntryPoint entry,
            @Qualifier("quickResourceAccessDeniedHandler") AccessDeniedHandler denied) {
        if(!Set.of("jwt","opaque").contains(quick.getMode())) throw new IllegalStateException("Resource mode must be jwt or opaque");
        return new QuickResourceServerWebSupport("jwt".equals(quick.getMode())?beans.getBean("quickResourceJwtDecoder",JwtDecoder.class):null,
                "opaque".equals(quick.getMode())?introspector.getObject():null,converter,opaqueConverter,resolver,entry,denied);
    }
    @Bean @ConditionalOnMissingBean(SecurityFilterChain.class)
    @ConditionalOnProperty(prefix="moli.oauth.resource-server",name="default-filter-chain-enabled",havingValue="true",matchIfMissing=true)
    SecurityFilterChain quickResourceChain(HttpSecurity http,QuickResourceServerWebSupport support,QuickResourceServerProperties quick) {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.requestCache(cache -> cache.requestCache(new NullRequestCache()));
        http.csrf(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable);
        http.authorizeHttpRequests(requests -> {
            for(String path:quick.getPermitAll()) requests.requestMatchers(path).permitAll();
            requests.anyRequest().authenticated();
        });
        http.with(support.configurer(),org.springframework.security.config.Customizer.withDefaults());
        return http.build();
    }
    @Bean SmartInitializingSingleton quickResourceValidation(QuickResourceServerWebSupport support) {
        return () -> { if(!support.isApplied()) throw new IllegalStateException("Apply QuickResourceServerWebSupport.configurer() to the host API chain"); };
    }
    @Configuration(proxyBeanMethods=false) @EnableMethodSecurity
    @ConditionalOnProperty(prefix="moli.oauth.resource-server",name="method-security-enabled",havingValue="true",matchIfMissing=true)
    static class MethodSecurity { }
}
