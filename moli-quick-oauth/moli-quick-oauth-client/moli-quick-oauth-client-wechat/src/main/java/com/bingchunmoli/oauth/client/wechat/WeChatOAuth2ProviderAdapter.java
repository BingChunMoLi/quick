package com.bingchunmoli.oauth.client.wechat;

import com.bingchunmoli.oauth.client.*;
import com.bingchunmoli.oauth.client.endpoint.OAuth2JsonEndpointClient;
import java.security.SecureRandom;
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

/** Website QR login and Official Account browser authorization; not Mini Program code2Session. */
public final class WeChatOAuth2ProviderAdapter implements OAuth2ProviderAdapter {
    private final boolean officialAccount;
    private final WeChatOAuth2Properties properties;
    private final OAuth2JsonEndpointClient http;
    private final SecureRandom random = new SecureRandom();
    public WeChatOAuth2ProviderAdapter(boolean officialAccount, WeChatOAuth2Properties properties, RestClient.Builder builder) {
        this.officialAccount = officialAccount; this.properties = properties; http = new OAuth2JsonEndpointClient(builder);
    }
    @Override public String id() { return officialAccount ? "wechat-mp" : "wechat"; }
    @Override public Map<String, String> defaults() {
        return Map.of("authorization-uri", officialAccount ? "https://open.weixin.qq.com/connect/oauth2/authorize" : "https://open.weixin.qq.com/connect/qrconnect",
                "token-uri", "https://api.weixin.qq.com/sns/oauth2/access_token",
                "user-info-uri", "https://api.weixin.qq.com/sns/userinfo", "user-name-attribute", "openid",
                "authorization-grant-type", "authorization_code", "client-authentication-method", "client_secret_post",
                "redirect-uri", "{baseUrl}/login/oauth2/code/{registrationId}", "scope", officialAccount ? "snsapi_userinfo" : "snsapi_login");
    }
    @Override public Set<AuthorizationGrantType> supportedGrantTypes() { return Set.of(AuthorizationGrantType.AUTHORIZATION_CODE); }
    @Override public void validateRegistration(ClientRegistration registration) {
        Set<String> allowed = officialAccount ? Set.of("snsapi_base", "snsapi_userinfo") : Set.of("snsapi_login");
        if (registration.getScopes().size() != 1 || !allowed.containsAll(registration.getScopes())) {
            throw new IllegalStateException("Unsupported scope for WeChat registration: " + registration.getRegistrationId());
        }
    }
    @Override public void customizeAuthorization(OAuth2AuthorizationRequest.Builder request) {
        byte[] stateBytes = new byte[32]; random.nextBytes(stateBytes);
        request.state(HexFormat.of().formatHex(stateBytes));
        request.parameters(parameters -> {
            // WeChat expects this parameter order; state validation remains in Spring Security.
            Object appId = parameters.get("client_id"), redirect = parameters.get("redirect_uri");
            Object scope = parameters.get("scope"), state = parameters.get("state");
            parameters.clear(); parameters.put("appid", appId); parameters.put("redirect_uri", redirect);
            parameters.put("response_type", "code"); parameters.put("scope", scope); parameters.put("state", state);
        });
        request.authorizationRequestUri(uri -> uri.fragment("wechat_redirect").build());
    }
    private Map<String, Object> checked(Map<String, Object> data) {
        if (data == null) throw failure("invalid_provider_response");
        Object error = data.get("errcode");
        if (error != null && !"0".equals(String.valueOf(error))) {
            String code = Set.of("40029", "40163", "40030", "42002").contains(String.valueOf(error)) ? "invalid_grant" : "invalid_provider_response";
            throw failure(code);
        }
        return data;
    }
    @Override public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> authorizationCodeClient() {
        return request -> {
            var registration = request.getClientRegistration();
            var exchange = request.getAuthorizationExchange();
            var data = checked(http.get(UriComponentsBuilder.fromUriString(registration.getProviderDetails().getTokenUri())
                    .queryParam("appid", "{id}").queryParam("secret", "{secret}").queryParam("code", "{code}")
                    .queryParam("grant_type", "authorization_code").encode()
                    .buildAndExpand(registration.getClientId(), registration.getClientSecret(), exchange.getAuthorizationResponse().getCode()).toUri()));
            required(data, "openid");
            return token(data, exchange.getAuthorizationRequest().getScopes());
        };
    }
    @Override public OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshTokenClient() {
        return request -> token(checked(http.get(UriComponentsBuilder.fromUriString(properties.getRefreshTokenUri())
                .queryParam("appid", "{id}").queryParam("grant_type", "refresh_token").queryParam("refresh_token", "{refresh}")
                .encode().buildAndExpand(request.getClientRegistration().getClientId(), request.getRefreshToken().getTokenValue()).toUri())),
                request.getAccessToken().getScopes());
    }
    @Override public OAuth2UserService<OAuth2UserRequest, OAuth2User> userService() {
        return request -> {
            try {
                String openId = required(request.getAdditionalParameters(), "openid");
                Set<String> scopes = request.getAccessToken().getScopes();
                Map<String, Object> data;
                if (officialAccount && scopes.equals(Set.of("snsapi_base"))) {
                    data = new LinkedHashMap<>(); data.put("openid", openId);
                } else {
                    data = new LinkedHashMap<>(checked(http.get(UriComponentsBuilder.fromUriString(request.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri())
                            .queryParam("access_token", "{token}").queryParam("openid", "{openid}").queryParam("lang", "{language}")
                            .encode().buildAndExpand(request.getAccessToken().getTokenValue(), openId, properties.getLanguage()).toUri())));
                    if (!openId.equals(required(data, "openid"))) throw failure("invalid_user_info_response");
                }
                if (!data.containsKey("unionid") && request.getAdditionalParameters().get("unionid") instanceof String unionId) data.put("unionid", unionId);
                data.put("_quick_subject", request.getClientRegistration().getClientId() + "|" + openId);
                return new DefaultOAuth2User(scopes.stream().map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope)).toList(), data, "_quick_subject");
            } catch (RuntimeException ex) {
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info_response"), "WeChat user information unavailable");
            }
        };
    }
    @Override public OAuth2Identity identity(ClientRegistration registration, OAuth2User user) {
        return new OAuth2Identity("wechat", registration.getClientId() + "|" + required(user.getAttributes(), "openid"),
                user.getAttribute("nickname"), user.getAttribute("headimgurl"), null, user.getAttributes());
    }
}
