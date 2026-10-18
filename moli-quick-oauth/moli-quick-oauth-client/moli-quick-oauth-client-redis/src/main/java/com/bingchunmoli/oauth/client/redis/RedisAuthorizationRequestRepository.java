package com.bingchunmoli.oauth.client.redis;

import com.bingchunmoli.oauth.client.*;
import com.bingchunmoli.oauth.client.web.AuthorizationRequestCodec;
import jakarta.servlet.http.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public final class RedisAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private final String prefix;
    private static final DefaultRedisScript<String> LOAD = new DefaultRedisScript<>(
            "local v = redis.call('get',KEYS[1]); if not v or string.sub(v,1,64) ~= ARGV[1] then return nil end return string.sub(v,66)", String.class);
    private static final DefaultRedisScript<String> CONSUME = new DefaultRedisScript<>(
            "local v = redis.call('get',KEYS[1]); if not v or string.sub(v,1,64) ~= ARGV[1] then return nil end redis.call('del',KEYS[1]); return string.sub(v,66)", String.class);
    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final boolean secure;
    private final AuthorizationRequestCodec codec = new AuthorizationRequestCodec();
    private final SecureRandom random = new SecureRandom();
    public RedisAuthorizationRequestRepository(StringRedisTemplate redis, Duration ttl, boolean secure) {
        this(redis, ttl, secure, "moli:oauth2");
    }
    public RedisAuthorizationRequestRepository(StringRedisTemplate redis, Duration ttl, boolean secure, String prefix) {
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("Positive OAuth2 state TTL required");
        this.redis = redis; this.ttl = ttl; this.secure = secure; this.prefix = prefix + ":state:";
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private String cookieName(String state) { return "moli_oauth2_" + hash(state).substring(0, 24); }
    private String secret(HttpServletRequest request, String state) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (cookieName(state).equals(cookie.getName())) return cookie.getValue();
        return null;
    }
    private void cookie(HttpServletRequest request, HttpServletResponse response, String state, String value, Duration maxAge) {
        String path = request.getContextPath().isEmpty() ? "/" : request.getContextPath();
        response.addHeader("Set-Cookie", ResponseCookie.from(cookieName(state), value).path(path).httpOnly(true)
                .secure(secure).sameSite("Lax").maxAge(maxAge).build().toString());
    }
    private OAuth2AuthorizationRequest read(HttpServletRequest request, boolean consume) {
        String state = request.getParameter("state");
        if (state == null || state.length() > 1024) return null;
        String secret = secret(request, state);
        if (secret == null || secret.length() > 256) return null;
        String data = redis.execute(consume ? CONSUME : LOAD, List.of(prefix + hash(state)), hash(secret));
        return data == null ? null : codec.decode(data);
    }
    @Override public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) { return read(request, false); }
    @Override public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorization, HttpServletRequest request,
                                                   HttpServletResponse response) {
        if (authorization == null) { removeAuthorizationRequest(request, response); return; }
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String state = authorization.getState();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(prefix + hash(state), hash(value) + ":" + codec.encode(authorization), ttl))) {
            throw new IllegalStateException("OAuth2 state collision");
        }
        cookie(request, response, state, value, ttl);
    }
    @Override public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authorization = read(request, true);
        String state = request.getParameter("state");
        if (state != null && state.length() <= 1024) cookie(request, response, state, "", Duration.ZERO);
        return authorization;
    }
}
