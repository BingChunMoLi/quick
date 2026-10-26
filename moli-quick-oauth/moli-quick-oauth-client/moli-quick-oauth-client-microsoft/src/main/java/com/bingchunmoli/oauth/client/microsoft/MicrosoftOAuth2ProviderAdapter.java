package com.bingchunmoli.oauth.client.microsoft;

import com.bingchunmoli.oauth.client.OAuth2Identity;
import com.bingchunmoli.oauth.client.oidc.OidcProviderAdapter;
import java.util.*;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.*;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestClient;

/** Microsoft identity platform v2: personal accounts and Entra work/school accounts. */
public final class MicrosoftOAuth2ProviderAdapter implements OidcProviderAdapter {
    static final String CONSUMER_TENANT = "9188040d-6c67-4c5b-b112-36a304b66dad";
    static final String AUTHORITY = "https://login.microsoftonline.com/";
    private final MicrosoftOAuth2Properties properties;
    private final RestClient http;
    public MicrosoftOAuth2ProviderAdapter(MicrosoftOAuth2Properties properties, RestClient.Builder builder) {
        this(properties, builder, MicrosoftJwkHttp.requestFactory(properties));
    }
    public MicrosoftOAuth2ProviderAdapter(MicrosoftOAuth2Properties properties, RestClient.Builder builder,
            ClientHttpRequestFactory jwkRequestFactory) {
        properties.validateTimeouts();
        this.properties = properties; this.http = builder.clone().requestFactory(jwkRequestFactory).build();
        String tenant = properties.getTenant();
        if (!Set.of("common", "organizations", "consumers").contains(tenant) && !guid(tenant))
            throw new IllegalArgumentException("Microsoft tenant must be common, organizations, consumers or a directory GUID");
        properties.setTenant(tenant.toLowerCase(Locale.ROOT));
        if (properties.getAllowedTenants() == null || properties.getAllowedTenants().stream().anyMatch(value -> !guid(value)))
            throw new IllegalArgumentException("Microsoft allowed-tenants must contain directory GUIDs");
    }
    static boolean guid(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
    @Override public String id() { return "microsoft"; }
    @Override public Map<String, String> defaults() {
        String tenant = properties.getTenant(), base = AUTHORITY + tenant;
        String issuer = Set.of("common", "organizations").contains(tenant) ? AUTHORITY + "{tenantid}/v2.0"
                : AUTHORITY + ("consumers".equals(tenant) ? CONSUMER_TENANT : tenant) + "/v2.0";
        return Map.of("authorization-uri", base + "/oauth2/v2.0/authorize",
                "token-uri", base + "/oauth2/v2.0/token", "user-info-uri", "https://graph.microsoft.com/oidc/userinfo",
                "user-name-attribute", "sub", "jwk-set-uri", base + "/discovery/v2.0/keys", "issuer-uri", issuer,
                "authorization-grant-type", "authorization_code", "client-authentication-method", "client_secret_post",
                "redirect-uri", "{baseUrl}/login/oauth2/code/{registrationId}",
                "scope", properties.isOffline() ? "openid,profile,email,offline_access" : "openid,profile,email");
    }
    @Override public Set<AuthorizationGrantType> supportedGrantTypes() { return Set.of(AuthorizationGrantType.AUTHORIZATION_CODE); }
    @Override public void validateRegistration(ClientRegistration registration) {
        if (!registration.getScopes().contains("openid") || registration.getProviderDetails().getIssuerUri() == null ||
                registration.getProviderDetails().getJwkSetUri() == null)
            throw new IllegalStateException("Microsoft login requires openid scope, issuer and JWK configuration");
    }
    @Override public void customizeAuthorization(OAuth2AuthorizationRequest.Builder request) {
        OAuth2AuthorizationRequestCustomizers.withPkce().accept(request);
        if (properties.getPrompt() != null && !properties.getPrompt().isBlank())
            request.additionalParameters(values -> values.put("prompt", properties.getPrompt()));
    }
    @Override public JwtDecoder idTokenDecoder(ClientRegistration registration) {
        return MicrosoftIdTokenDecoder.create(registration, properties, http);
    }
    @Override public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        var nativeService = new OidcUserService();
        return request -> {
            OidcUser user = nativeService.loadUser(request);
            Map<String, Object> claims = new LinkedHashMap<>(user.getClaims());
            // Authorized-client storage must separate equal subjects issued by different directories.
            claims.put("_quick_subject", user.getIdToken().getIssuer() + "|" + user.getIdToken().getSubject());
            return new DefaultOidcUser(user.getAuthorities(), user.getIdToken(), new OidcUserInfo(claims), "_quick_subject");
        };
    }
    @Override public OAuth2Identity identity(ClientRegistration registration, OAuth2User user) {
        if (!(user instanceof OidcUser oidc)) throw new IllegalStateException("Microsoft login requires a validated OIDC user");
        return new OAuth2Identity(id(), oidc.getIdToken().getIssuer() + "|" + oidc.getIdToken().getSubject(), oidc.getFullName(), null,
                oidc.getEmail(), oidc.getAttributes());
    }
}
