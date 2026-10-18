package com.bingchunmoli.oauth.client.qq;

import com.bingchunmoli.oauth.client.*;
import com.bingchunmoli.oauth.client.endpoint.OAuth2JsonEndpointClient;
import java.util.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.*;
import org.springframework.security.oauth2.core.user.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import static com.bingchunmoli.oauth.client.endpoint.OAuth2JsonEndpointClient.*;

/** QQ Connect website login. Requests JSON explicitly instead of executing JSONP. */
public final class QqOAuth2ProviderAdapter implements OAuth2ProviderAdapter {
    private final QqOAuth2Properties properties;
    private final OAuth2JsonEndpointClient http;
    public QqOAuth2ProviderAdapter(QqOAuth2Properties properties, RestClient.Builder builder) {
        this.properties = properties; http = new OAuth2JsonEndpointClient(builder);
    }
    @Override public String id() { return "qq"; }
    @Override public Map<String, String> defaults() {
        return Map.of("authorization-uri", "https://graph.qq.com/oauth2.0/authorize",
                "token-uri", "https://graph.qq.com/oauth2.0/token",
                "user-info-uri", "https://graph.qq.com/user/get_user_info", "user-name-attribute", "openid",
                "authorization-grant-type", "authorization_code", "client-authentication-method", "client_secret_post",
                "redirect-uri", "{baseUrl}/login/oauth2/code/{registrationId}", "scope", "get_user_info");
    }
    @Override public Set<AuthorizationGrantType> supportedGrantTypes() { return Set.of(AuthorizationGrantType.AUTHORIZATION_CODE); }
    @Override public void customizeAuthorization(OAuth2AuthorizationRequest.Builder request) {
        request.parameters(parameters -> {
            if (parameters.get("scope") instanceof String scope) parameters.put("scope", scope.replace(' ', ','));
        });
    }
    private Map<String, Object> checked(Map<String, Object> data) {
        if (data != null && data.containsKey("error")) {
            String error = String.valueOf(data.get("error"));
            throw failure(Set.of("100020", "100036", "100037", "100038", "100042", "100045", "100067", "100068")
                    .contains(error) ? "invalid_grant" : "invalid_provider_response");
        }
        if (data == null || data.containsKey("ret") && !"0".equals(String.valueOf(data.get("ret")))) {
            throw failure("invalid_provider_response");
        }
        return data;
    }
    @Override public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> authorizationCodeClient() {
        return request -> {
            var registration = request.getClientRegistration();
            var response = request.getAuthorizationExchange();
            return token(checked(http.get(UriComponentsBuilder.fromUriString(registration.getProviderDetails().getTokenUri())
                    .queryParam("grant_type", "authorization_code").queryParam("client_id", "{id}").queryParam("client_secret", "{secret}")
                    .queryParam("code", "{code}").queryParam("redirect_uri", "{redirect}").queryParam("fmt", "json")
                    .encode().buildAndExpand(registration.getClientId(), registration.getClientSecret(), response.getAuthorizationResponse().getCode(),
                            response.getAuthorizationRequest().getRedirectUri()).toUri())), response.getAuthorizationRequest().getScopes());
        };
    }
    @Override public OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshTokenClient() {
        return request -> {
            var registration = request.getClientRegistration();
            return token(checked(http.get(UriComponentsBuilder.fromUriString(registration.getProviderDetails().getTokenUri())
                    .queryParam("grant_type", "refresh_token").queryParam("client_id", "{id}").queryParam("client_secret", "{secret}")
                    .queryParam("refresh_token", "{refresh}").queryParam("fmt", "json").encode()
                    .buildAndExpand(registration.getClientId(), registration.getClientSecret(), request.getRefreshToken().getTokenValue()).toUri())),
                    request.getAccessToken().getScopes());
        };
    }
    @Override public OAuth2UserService<OAuth2UserRequest, OAuth2User> userService() {
        return request -> {
            try {
                var registration = request.getClientRegistration();
                var me = checked(http.get(UriComponentsBuilder.fromUriString(properties.getOpenIdUri())
                        .queryParam("access_token", "{token}").queryParam("fmt", "json").encode()
                        .buildAndExpand(request.getAccessToken().getTokenValue()).toUri()));
                if (!registration.getClientId().equals(required(me, "client_id"))) throw failure("invalid_user_info_response");
                String openId = required(me, "openid");
                var data = new LinkedHashMap<>(checked(http.get(UriComponentsBuilder.fromUriString(registration.getProviderDetails().getUserInfoEndpoint().getUri())
                        .queryParam("access_token", "{token}").queryParam("oauth_consumer_key", "{id}").queryParam("openid", "{openid}")
                        .encode().buildAndExpand(request.getAccessToken().getTokenValue(), registration.getClientId(), openId).toUri())));
                if (!"0".equals(String.valueOf(data.get("ret")))) throw failure("invalid_user_info_response");
                data.put("openid", openId); data.put("_quick_subject", registration.getClientId() + "|" + openId);
                if (me.get("unionid") != null) data.put("unionid", me.get("unionid"));
                return new DefaultOAuth2User(request.getAccessToken().getScopes().stream()
                        .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope)).toList(), data, "_quick_subject");
            } catch (RuntimeException ex) {
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info_response"), "QQ user information unavailable");
            }
        };
    }
    @Override public OAuth2Identity identity(ClientRegistration registration, OAuth2User user) {
        String avatar = user.getAttribute("figureurl_qq_2");
        if (avatar == null || avatar.isBlank()) avatar = user.getAttribute("figureurl_qq_1");
        return new OAuth2Identity(id(), registration.getClientId() + "|" + required(user.getAttributes(), "openid"),
                user.getAttribute("nickname"), avatar, null, user.getAttributes());
    }
}
