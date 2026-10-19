package com.bingchunmoli.oauth.server.authorization.autoconfigure;
import com.bingchunmoli.oauth.server.authorization.*;
import com.bingchunmoli.oauth.server.authorization.web.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.authentication.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.*;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.http.MediaType;

@AutoConfiguration(beforeName = {
    "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
    "com.bingchunmoli.security.autoconfigure.SecurityUtilAutoConfiguration",
    "com.bingchunmoli.oauth.server.resource.autoconfigure.QuickResourceServerAutoConfiguration"})
@ConditionalOnProperty(prefix="moli.oauth.auth-server", name="enabled", havingValue="true")
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({QuickAuthorizationServerProperties.class, OAuth2AuthorizationServerProperties.class})
@EnableWebSecurity
public class QuickAuthorizationServerAutoConfiguration {
    @Bean @ConditionalOnMissingBean
    AuthorizationServerSettings quickAuthorizationServerSettings(OAuth2AuthorizationServerProperties properties) {
        if (properties.isMultipleIssuersAllowed()) throw new IllegalStateException("Quick authorization server requires one stable issuer");
        String issuer = properties.getIssuer();
        if (issuer == null || issuer.isBlank()) throw new IllegalStateException("Configure an explicit authorization server issuer");
        validateIssuer(issuer);
        return new AuthorizationPropertiesMapper(properties).asAuthorizationServerSettings();
    }
    @Bean @ConditionalOnMissingBean(RegisteredClientRepository.class)
    RegisteredClientRepository quickRegisteredClients(OAuth2AuthorizationServerProperties properties, Environment environment,
            QuickAuthorizationServerProperties quick) {
        requireMemory(quick);
        var clients=configuredClients(properties, environment);
        clients.forEach(client->audience(quick,client));
        return new InMemoryRegisteredClientRepository(clients);
    }
    public static List<RegisteredClient> configuredClients(OAuth2AuthorizationServerProperties properties, Environment environment) {
        properties.getClient().forEach((name, client) -> {
            if (!environment.containsProperty("spring.security.oauth2.authorizationserver.client."+name+".token.reuse-refresh-tokens"))
                client.getToken().setReuseRefreshTokens(false);
        });
        List<RegisteredClient> result = new AuthorizationPropertiesMapper(properties).asRegisteredClients();
        if (result.isEmpty()) throw new IllegalStateException("At least one registered OAuth client is required");
        Set<String> ids = new HashSet<>();
        for (RegisteredClient client : result) {
            if (!ids.add(client.getClientId())) throw new IllegalStateException("Duplicate OAuth client id");
            if (!Set.of(AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.CLIENT_CREDENTIALS, AuthorizationGrantType.REFRESH_TOKEN)
                    .containsAll(client.getAuthorizationGrantTypes())) throw new IllegalStateException("Unsupported authorization grant type");
            String secret = client.getClientSecret();
            if (secret != null && (!secret.startsWith("{") || secret.startsWith("{noop}")))
                throw new IllegalStateException("OAuth client secret must use an encoded PasswordEncoder format");
            if (!client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE) && (secret == null || secret.isBlank()) &&
                    client.getClientAuthenticationMethods().stream().anyMatch(method -> method.equals(ClientAuthenticationMethod.CLIENT_SECRET_BASIC) || method.equals(ClientAuthenticationMethod.CLIENT_SECRET_POST)))
                throw new IllegalStateException("Confidential OAuth clients require an encoded secret");
        }
        return result;
    }
    private static void requireMemory(QuickAuthorizationServerProperties quick) {
        if (!"memory".equals(quick.getStorage().getType())) throw new IllegalStateException("Selected authorization store requires its module and connection bean");
    }
    @Bean @ConditionalOnMissingBean(OAuth2AuthorizationService.class)
    OAuth2AuthorizationService quickAuthorizations(QuickAuthorizationServerProperties quick) { requireMemory(quick); return new ExpiringMemoryAuthorizationService(); }
    @Bean @ConditionalOnMissingBean(OAuth2AuthorizationConsentService.class)
    OAuth2AuthorizationConsentService quickConsents(QuickAuthorizationServerProperties quick) { requireMemory(quick); return new InMemoryOAuth2AuthorizationConsentService(); }
    @Bean @ConditionalOnMissingBean(GrantCoordinator.class)
    GrantCoordinator quickGrantCoordinator(QuickAuthorizationServerProperties quick) {
        requireMemory(quick); return new LocalGrantCoordinator(quick.getStorage().getWaitTimeout());
    }
    @Bean @ConditionalOnMissingBean(AuthorizationAttributeCodec.class)
    AuthorizationAttributeCodec quickAuthorizationAttributeCodec() { return new SecurityAuthorizationAttributeCodec(); }
    @Bean @ConditionalOnMissingBean(JWKSource.class)
    JWKSource<SecurityContext> quickSigningKeys(QuickAuthorizationServerProperties quick, ResourceLoader resources) {
        return SigningKeys.load(quick.getSigning(), resources);
    }
    @Bean @ConditionalOnMissingBean(JwtEncoder.class)
    JwtEncoder quickJwtEncoder(JWKSource<SecurityContext> keys, QuickAuthorizationServerProperties quick) {
        var encoder = new NimbusJwtEncoder(keys);
        encoder.setJwkSelector(candidates -> {
            var signing = candidates.stream().filter(JWK::isPrivate).filter(key -> quick.getSigning().getActiveKeyId() == null ||
                    quick.getSigning().getActiveKeyId().equals(key.getKeyID())).toList();
            if (signing.size() != 1) throw new JwtEncodingException("One active OAuth signing key is required");
            return signing.get(0);
        });
        return encoder;
    }
    @Bean(name="quickAuthorizationServerJwtDecoder") @ConditionalOnMissingBean(name="quickAuthorizationServerJwtDecoder")
    JwtDecoder quickAuthorizationServerJwtDecoder(JWKSource<SecurityContext> keys) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(keys);
    }
    @Bean @ConditionalOnMissingBean(OAuth2TokenGenerator.class)
    OAuth2TokenGenerator<?> quickTokenGenerator(JwtEncoder encoder, QuickAuthorizationServerProperties quick,
            ObjectProvider<OAuth2TokenCustomizer<JwtEncodingContext>> jwtCustomizers,
            ObjectProvider<OAuth2TokenCustomizer<OAuth2TokenClaimsContext>> opaqueCustomizers) {
        var jwt = new JwtGenerator(encoder);
        jwt.setJwtCustomizer(context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) context.getClaims().audience(audience(quick, context.getRegisteredClient()));
            jwtCustomizers.orderedStream().forEach(customizer -> customizer.customize(context));
        });
        var opaque = new OAuth2AccessTokenGenerator();
        opaque.setAccessTokenCustomizer(context -> {
            context.getClaims().audience(audience(quick, context.getRegisteredClient()));
            opaqueCustomizers.orderedStream().forEach(customizer -> customizer.customize(context));
        });
        return new DelegatingOAuth2TokenGenerator(jwt, opaque, new OAuth2RefreshTokenGenerator());
    }
    public static List<String> audience(QuickAuthorizationServerProperties quick, RegisteredClient client) {
        var audience = quick.getAudiences().get(client.getClientId());
        if (audience == null || audience.isEmpty() || audience.stream().anyMatch(value -> value == null || value.isBlank()))
            throw new IllegalStateException("Configure audiences for the OAuth client");
        return List.copyOf(audience);
    }
    @Bean @ConditionalOnMissingBean
    QuickAuthorizationServerWebSupport quickAuthorizationServerWebSupport(RegisteredClientRepository clients,
            OAuth2AuthorizationService authorizations, OAuth2AuthorizationConsentService consents, AuthorizationServerSettings settings,
            OAuth2TokenGenerator<?> generator, GrantCoordinator coordinator,
            @org.springframework.beans.factory.annotation.Qualifier("quickAuthorizationServerJwtDecoder") JwtDecoder decoder) {
        return new QuickAuthorizationServerWebSupport(clients, authorizations, consents, settings, generator, coordinator,decoder);
    }
    @Bean @ConditionalOnProperty(prefix="moli.oauth.auth-server", name="default-filter-chain-enabled", havingValue="true", matchIfMissing=true)
    @ConditionalOnMissingBean(AuthorizationProtocolChain.class)
    AuthorizationProtocolChain quickAuthorizationProtocolChain(HttpSecurity http, QuickAuthorizationServerWebSupport support,
            QuickAuthorizationServerProperties quick) {
        var configurer = support.configurer();
        http.securityMatcher(configurer.endpointsMatcher()).authorizeHttpRequests(requests -> requests.anyRequest().authenticated());
        http.exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        http.with(configurer, Customizer.withDefaults());
        return new AuthorizationProtocolChain(http.build(), quick.getOrder());
    }
    @Bean @Order(Ordered.LOWEST_PRECEDENCE)
    @ConditionalOnProperty(prefix="moli.oauth.auth-server", name="default-filter-chain-enabled", havingValue="true", matchIfMissing=true)
    @ConditionalOnMissingBean(value=SecurityFilterChain.class, ignored=AuthorizationProtocolChain.class)
    SecurityFilterChain quickAuthorizationBusinessChain(HttpSecurity http, QuickAuthorizationServerProperties quick) {
        if (quick.getLogin().isEnabled()) {
            http.authorizeHttpRequests(requests -> requests.requestMatchers("/login", "/error").permitAll().anyRequest().authenticated())
                .formLogin(Customizer.withDefaults());
        } else http.authorizeHttpRequests(requests -> requests.anyRequest().denyAll());
        return http.build();
    }
    @Bean SmartInitializingSingleton quickAuthorizationServerValidation(QuickAuthorizationServerWebSupport support,
            QuickAuthorizationServerProperties quick, ListableBeanFactory beans, AuthorizationServerSettings settings,
            OAuth2AuthorizationServerProperties properties, Environment environment) {
        return () -> {
            validateIssuer(settings.getIssuer());
            if (!support.isApplied()) throw new IllegalStateException("Apply QuickAuthorizationServerWebSupport.configurer() to a protocol chain");
            if (quick.getLogin().isEnabled() && beans.getBeanNamesForType(UserDetailsService.class).length == 0 &&
                    beans.getBeanNamesForType(AuthenticationProvider.class).length == 0 && beans.getBeanNamesForType(AuthenticationManager.class).length == 0)
                throw new IllegalStateException("OAuth form login requires host user authentication");
        };
    }
    private static void validateIssuer(String issuer) {
        try {
            URI uri=URI.create(issuer);
            if(!uri.isAbsolute() || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null ||
                !Set.of("http","https").contains(uri.getScheme()))throw new IllegalArgumentException();
        } catch(RuntimeException invalid) {throw new IllegalStateException("Explicit valid OAuth issuer URL required");}
    }
}
