package com.bingchunmoli.oauth.server.authorization;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Versioned outer documents with explicitly allowlisted native attributes in a separate codec. */
public final class AuthorizationSnapshotCodec {
    private final JsonMapper json=JsonMapper.builder().build();
    private final AuthorizationAttributeCodec attributes;
    public AuthorizationSnapshotCodec(AuthorizationAttributeCodec attributes) { this.attributes=attributes; }
    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public record TokenIndex(String value,Instant expiration) { }
    public static List<TokenIndex> indexes(OAuth2Authorization authorization) {
        List<TokenIndex> result=new ArrayList<>();
        for(var token:tokens(authorization)) result.add(new TokenIndex(token.getToken().getTokenValue(),token.getToken().getExpiresAt()));
        String state=authorization.getAttribute("state");
        if(state!=null) result.add(new TokenIndex(state,Instant.now().plusSeconds(600)));
        return result;
    }
    private static List<OAuth2Authorization.Token<?>> tokens(OAuth2Authorization authorization) {
        List<OAuth2Authorization.Token<?>> result=new ArrayList<>();
        for(Class<? extends OAuth2Token> type:List.of(OAuth2AuthorizationCode.class,OAuth2AccessToken.class,OAuth2RefreshToken.class,OidcIdToken.class)) {
            var token=authorization.getToken(type); if(token!=null) result.add(token);
        }
        return result;
    }
    public String authorization(OAuth2Authorization value) {
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("version",1); data.put("id",value.getId());data.put("client",value.getRegisteredClientId());
        data.put("principal",value.getPrincipalName());data.put("grant",value.getAuthorizationGrantType().getValue());
        data.put("scopes",value.getAuthorizedScopes());data.put("attributes",attributes.encode(value.getAttributes()));
        List<Map<String,Object>> encoded=new ArrayList<>();
        for(var entry:tokens(value)) {
            OAuth2Token token=entry.getToken(); Map<String,Object> item=new LinkedHashMap<>();
            item.put("type",token instanceof OAuth2AuthorizationCode?"code":token instanceof OAuth2AccessToken?"access_token":token instanceof OAuth2RefreshToken?"refresh_token":"id_token");
            item.put("value",token.getTokenValue()); item.put("issued",text(token.getIssuedAt()));item.put("expires",text(token.getExpiresAt()));
            item.put("metadata",attributes.encode(entry.getMetadata()));
            if(token instanceof OAuth2AccessToken access) item.put("scopes",access.getScopes());
            if(token instanceof OidcIdToken oidc) item.put("claims",attributes.encode(oidc.getClaims()));
            encoded.add(item);
        }
        data.put("tokens",encoded);return json.writeValueAsString(data);
    }
    @SuppressWarnings("unchecked")
    public OAuth2Authorization authorization(String data,RegisteredClientRepository clients) {
        var value=read(data); RegisteredClient client=clients.findById(string(value,"client"));
        if(client==null) throw new IllegalStateException("Authorization references an unknown registered client");
        var builder=OAuth2Authorization.withRegisteredClient(client).id(string(value,"id")).principalName(string(value,"principal"))
                .authorizationGrantType(new AuthorizationGrantType(string(value,"grant"))).authorizedScopes(strings(value.get("scopes")))
                .attributes(map->map.putAll(attributes.decode(string(value,"attributes"))));
        for(var item:(List<Map<String,Object>>)value.get("tokens")) {
            String token=string(item,"value"); Instant issued=instant(item.get("issued")),expires=instant(item.get("expires"));
            OAuth2Token decoded=switch(string(item,"type")) {
                case "code" -> new OAuth2AuthorizationCode(token,issued,expires);
                case "access_token" -> new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,token,issued,expires,strings(item.get("scopes")));
                case "refresh_token" -> new OAuth2RefreshToken(token,issued,expires);
                case "id_token" -> new OidcIdToken(token,issued,expires,attributes.decode(string(item,"claims")));
                default -> throw new IllegalStateException("Unknown authorization token type");
            };
            builder.token(decoded,metadata->metadata.putAll(attributes.decode(string(item,"metadata"))));
        }
        return builder.build();
    }
    public String client(RegisteredClient client) {
        Map<String,Object> value=new LinkedHashMap<>();
        value.put("version",1);value.put("id",client.getId());value.put("clientId",client.getClientId());
        value.put("issued",text(client.getClientIdIssuedAt()));value.put("secret",client.getClientSecret());
        value.put("secretExpires",text(client.getClientSecretExpiresAt()));value.put("name",client.getClientName());
        value.put("methods",client.getClientAuthenticationMethods().stream().map(ClientAuthenticationMethod::getValue).toList());
        value.put("grants",client.getAuthorizationGrantTypes().stream().map(AuthorizationGrantType::getValue).toList());
        value.put("redirects",client.getRedirectUris());value.put("logoutRedirects",client.getPostLogoutRedirectUris());value.put("scopes",client.getScopes());
        value.put("clientSettings",attributes.encode(client.getClientSettings().getSettings()));value.put("tokenSettings",attributes.encode(client.getTokenSettings().getSettings()));
        return json.writeValueAsString(value);
    }
    public RegisteredClient client(String data) {
        var value=read(data);
        var builder=RegisteredClient.withId(string(value,"id")).clientId(string(value,"clientId")).clientName(string(value,"name"))
                .clientIdIssuedAt(instant(value.get("issued"))).clientSecret((String)value.get("secret")).clientSecretExpiresAt(instant(value.get("secretExpires")));
        strings(value.get("methods")).forEach(method->builder.clientAuthenticationMethod(new ClientAuthenticationMethod(method)));
        strings(value.get("grants")).forEach(grant->builder.authorizationGrantType(new AuthorizationGrantType(grant)));
        strings(value.get("redirects")).forEach(builder::redirectUri); strings(value.get("logoutRedirects")).forEach(builder::postLogoutRedirectUri);
        strings(value.get("scopes")).forEach(builder::scope);
        builder.clientSettings(ClientSettings.withSettings(attributes.decode(string(value,"clientSettings"))).build());
        builder.tokenSettings(TokenSettings.withSettings(attributes.decode(string(value,"tokenSettings"))).build());
        return builder.build();
    }
    public String consent(OAuth2AuthorizationConsent consent) {
        return json.writeValueAsString(Map.of("version",1,"client",consent.getRegisteredClientId(),"principal",consent.getPrincipalName(),
                "authorities",consent.getAuthorities().stream().map(authority->authority.getAuthority()).toList()));
    }
    public OAuth2AuthorizationConsent consent(String data) {
        var value=read(data);var builder=OAuth2AuthorizationConsent.withId(string(value,"client"),string(value,"principal"));
        strings(value.get("authorities")).forEach(authority->builder.authority(new SimpleGrantedAuthority(authority)));return builder.build();
    }
    private Map<String,Object> read(String data) {
        try {
            Map<String,Object> value=json.readValue(data,new TypeReference<Map<String,Object>>() {});
            if(!Integer.valueOf(1).equals(value.get("version"))) throw new IllegalStateException();
            return value;
        } catch(RuntimeException invalid) { throw new IllegalStateException("Invalid authorization snapshot version or data"); }
    }
    private static String string(Map<String,Object> value,String key) { return (String)Objects.requireNonNull(value.get(key)); }
    private static String text(Instant value) { return value==null?null:value.toString(); }
    private static Instant instant(Object value) { return value==null?null:Instant.parse((String)value); }
    private static Set<String> strings(Object value) {
        if(!(value instanceof Collection<?> items)) throw new IllegalStateException("Invalid authorization collection");
        Set<String> result=new LinkedHashSet<>();for(Object item:items) result.add((String)item);return result;
    }
}
