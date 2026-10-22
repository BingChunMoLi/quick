package com.bingchunmoli.oauth.client.apple;

import com.bingchunmoli.oauth.client.*;
import java.util.*;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** Sign in with Apple web authorization-code login; ID-token verification remains native OIDC. */
public final class AppleOAuth2ProviderAdapter implements OAuth2ProviderAdapter {
    private final AppleClientSecrets secrets;
    private final RestClientAuthorizationCodeTokenResponseClient code = new RestClientAuthorizationCodeTokenResponseClient();
    private final RestClientRefreshTokenTokenResponseClient refresh = new RestClientRefreshTokenTokenResponseClient();
    private final JsonMapper json = JsonMapper.builder().build();
    public AppleOAuth2ProviderAdapter(AppleOAuth2Properties properties, RestClient.Builder builder) {
        secrets = new AppleClientSecrets(properties);
        RestClient token = builder.clone().configureMessageConverters(converters -> converters.disableDefaults()
                .addCustomConverter(new FormHttpMessageConverter()).addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler()).build();
        code.setRestClient(token); refresh.setRestClient(token);
        code.addParametersConverter(request -> parameters(request.getClientRegistration()));
        refresh.addParametersConverter(request -> parameters(request.getClientRegistration()));
    }
    private LinkedMultiValueMap<String, String> parameters(ClientRegistration registration) {
        var values = new LinkedMultiValueMap<String, String>();
        values.set("client_secret", secrets.secret(registration));
        return values;
    }
    @Override public String id() { return "apple"; }
    @Override public Map<String, String> defaults() {
        return Map.of("authorization-uri", "https://appleid.apple.com/auth/authorize",
                "token-uri", "https://appleid.apple.com/auth/token", "jwk-set-uri", "https://appleid.apple.com/auth/keys",
                "issuer-uri", AppleClientSecrets.ISSUER, "user-name-attribute", "sub",
                "authorization-grant-type", "authorization_code", "client-authentication-method", "client_secret_post",
                "redirect-uri", "{baseUrl}/login/oauth2/code/{registrationId}", "scope", "openid,name,email");
    }
    @Override public Set<AuthorizationGrantType> supportedGrantTypes() { return Set.of(AuthorizationGrantType.AUTHORIZATION_CODE); }
    @Override public void validateRegistration(ClientRegistration registration) {
        if (!registration.getScopes().contains("openid") || !Set.of("openid", "name", "email").containsAll(registration.getScopes()) ||
                registration.getProviderDetails().getIssuerUri() == null || registration.getProviderDetails().getJwkSetUri() == null ||
                !ClientAuthenticationMethod.CLIENT_SECRET_POST.equals(registration.getClientAuthenticationMethod()))
            throw new IllegalStateException("Apple login requires openid/name/email scopes, issuer, JWK and client_secret_post");
        secrets.validate(registration);
    }
    @Override public void customizeAuthorization(OAuth2AuthorizationRequest.Builder request) {
        var snapshot = request.build();
        request.additionalParameters(values -> values.put("response_mode", "form_post"));
        // Spring needs openid internally to select OIDC. Apple's wire protocol accepts name/email only.
        request.parameters(values -> {
            String scope = snapshot.getScopes().stream().filter(value -> !"openid".equals(value)).sorted()
                    .collect(java.util.stream.Collectors.joining(" "));
            if (scope.isBlank()) values.remove("scope"); else values.put("scope", scope);
        });
    }
    @Override public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> authorizationCodeClient() { return code; }
    @Override public OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshTokenClient() { return refresh; }
    @Override public OAuth2Identity identity(ClientRegistration registration, OAuth2User user) {
        return identity(registration, user, Map.of());
    }
    @Override public OAuth2Identity identity(ClientRegistration registration, OAuth2User user, Map<String, String[]> callback) {
        if (!(user instanceof OidcUser oidc)) throw new IllegalStateException("Apple login requires a validated OIDC user");
        // Only signed ID-token claims supply identity/email; the one-time user field supplies display name only.
        return new OAuth2Identity(id(), oidc.getIdToken().getIssuer() + "|" + oidc.getIdToken().getSubject(), displayName(callback), null,
                Boolean.TRUE.equals(oidc.getIdToken().getEmailVerified()) ? oidc.getIdToken().getEmail() : null, oidc.getAttributes());
    }
    @SuppressWarnings("unchecked")
    private String displayName(Map<String, String[]> callback) {
        String[] values = callback.get("user");
        if (values == null || values.length != 1 || values[0] == null || values[0].length() > 8192) return null;
        try {
            Map<String, Object> value = json.readValue(values[0], Map.class);
            if (!(value.get("name") instanceof Map<?, ?> name)) return null;
            String first = part(name.get("firstName")), last = part(name.get("lastName"));
            String full = (first + " " + last).strip();
            return full.isEmpty() ? null : full;
        } catch (RuntimeException invalid) { return null; }
    }
    private String part(Object value) {
        return value instanceof String text && text.length() <= 256
                && text.chars().noneMatch(c -> Character.isISOControl(c) || c == '<' || c == '>') ? text.strip() : "";
    }
}
