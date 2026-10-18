package com.bingchunmoli.oauth.client.github;

import com.bingchunmoli.oauth.client.*;
import java.util.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.*;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.user.*;
import org.springframework.web.client.RestClient;

/** GitHub.com OAuth Apps. GitHub App installation tokens are a different protocol. */
public final class GitHubOAuth2ProviderAdapter implements OAuth2ProviderAdapter {
    private final GitHubOAuth2Properties properties;
    private final RestClient api;
    private final RestClientAuthorizationCodeTokenResponseClient code = new RestClientAuthorizationCodeTokenResponseClient();
    private final RestClientRefreshTokenTokenResponseClient refresh = new RestClientRefreshTokenTokenResponseClient();

    public GitHubOAuth2ProviderAdapter(GitHubOAuth2Properties properties, RestClient.Builder builder) {
        this.properties = properties;
        api = builder.clone().build();
        var converter = new OAuth2AccessTokenResponseHttpMessageConverter();
        var nativeConverter = new DefaultMapOAuth2AccessTokenResponseConverter();
        converter.setAccessTokenResponseConverter(parameters -> {
            Map<String, Object> normalized = new LinkedHashMap<>(parameters);
            if (parameters.get(OAuth2ParameterNames.SCOPE) instanceof String scope) {
                // GitHub returns comma-separated scopes; Spring expects the OAuth space delimiter.
                normalized.put(OAuth2ParameterNames.SCOPE, scope.replace(',', ' '));
            }
            var response = nativeConverter.convert(normalized);
            Map<String, Object> extras = new LinkedHashMap<>(response.getAdditionalParameters());
            if (!parameters.containsKey("expires_in")) extras.put("quick_non_expiring_access", true);
            return OAuth2AccessTokenResponse.withResponse(response).additionalParameters(extras).build();
        });
        RestClient token = builder.clone().configureMessageConverters(converters -> converters.disableDefaults()
                .addCustomConverter(new FormHttpMessageConverter()).addCustomConverter(converter))
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler()).build();
        code.setRestClient(token);
        refresh.setRestClient(token);
    }
    @Override public String id() { return "github"; }
    @Override public Map<String, String> defaults() {
        return Map.of("authorization-uri", "https://github.com/login/oauth/authorize",
                "token-uri", "https://github.com/login/oauth/access_token",
                "user-info-uri", "https://api.github.com/user",
                "user-name-attribute", "id", "authorization-grant-type", "authorization_code",
                "client-authentication-method", "client_secret_post",
                "redirect-uri", "{baseUrl}/login/oauth2/code/{registrationId}",
                "scope", properties.isFetchEmail() ? "read:user,user:email" : "read:user");
    }
    @Override public Set<AuthorizationGrantType> supportedGrantTypes() { return Set.of(AuthorizationGrantType.AUTHORIZATION_CODE); }
    @Override public void customizeAuthorization(OAuth2AuthorizationRequest.Builder request) {
        OAuth2AuthorizationRequestCustomizers.withPkce().accept(request);
    }
    @Override public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> authorizationCodeClient() { return code; }
    @Override public OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshTokenClient() { return refresh; }

    @Override public OAuth2UserService<OAuth2UserRequest, OAuth2User> userService() {
        return request -> {
            try {
                Map<String, Object> data = api.get().uri(request.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri())
                        .headers(headers -> headers.setBearerAuth(request.getAccessToken().getTokenValue()))
                        .accept(MediaType.APPLICATION_JSON).retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
                if (data == null || data.get("id") == null) throw new IllegalStateException();
                data = new LinkedHashMap<>(data);
                if (properties.isFetchEmail() && (request.getAccessToken().getScopes().contains("user:email") ||
                        request.getAccessToken().getScopes().contains("user"))) {
                    List<Map<String, Object>> emails = api.get().uri(properties.getEmailUri())
                            .headers(headers -> headers.setBearerAuth(request.getAccessToken().getTokenValue()))
                            .accept(MediaType.APPLICATION_JSON).retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() { });
                    var selected = emails == null ? Optional.<Map<String, Object>>empty() : emails.stream().filter(email -> Boolean.TRUE.equals(email.get("verified")))
                            .sorted(Comparator.comparing(email -> !Boolean.TRUE.equals(email.get("primary"))))
                            .findFirst();
                    data.remove("email");
                    if (selected.isPresent()) data.put("email", selected.get().get("email"));
                }
                var authorities = request.getAccessToken().getScopes().stream()
                        .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope)).toList();
                return new DefaultOAuth2User(authorities, data, "id");
            } catch (RuntimeException failure) {
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info_response"), "GitHub user information unavailable");
            }
        };
    }
    @Override public OAuth2Identity identity(ClientRegistration registration, OAuth2User user) {
        return new OAuth2Identity(id(), String.valueOf((Object) user.getAttribute("id")),
                user.getAttribute("name") == null ? user.getAttribute("login") : user.getAttribute("name"),
                user.getAttribute("avatar_url"), user.getAttribute("email"), user.getAttributes());
    }
}
