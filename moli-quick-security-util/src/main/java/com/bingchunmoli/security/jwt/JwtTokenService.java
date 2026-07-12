package com.bingchunmoli.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Issues, validates and revokes JWT tokens in SIMPLE, MIXIN and STATELESS modes.
 *
 * @author MoLi
 */
public class JwtTokenService {

    private static final String CLAIM_MODE = "mode";

    private static final String CLAIM_SESSION_ID = "sid";

    private static final String CLAIM_USERNAME = "username";

    private static final String CLAIM_AUTHORITIES = "authorities";

    private static final String CLAIM_ATTRIBUTES = "attributes";

    private final JwtTokenProperties properties;

    private final JwtSessionStore sessionStore;

    private final SecretKey secretKey;

    public JwtTokenService(JwtTokenProperties properties, JwtSessionStore sessionStore) {
        this.properties = properties;
        this.sessionStore = sessionStore;
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public JwtToken createToken(JwtUser user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.getTimeout());
        String tokenId = UUID.randomUUID().toString();
        String sessionId = properties.getMode() == JwtMode.STATELESS ? null : UUID.randomUUID().toString();

        var builder = Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(user.getUserId())
                .id(tokenId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_MODE, properties.getMode().name());

        if (sessionId != null) {
            builder.claim(CLAIM_SESSION_ID, sessionId);
        }
        if (properties.getMode() != JwtMode.SIMPLE) {
            builder.claim(CLAIM_USERNAME, user.getUsername())
                    .claim(CLAIM_AUTHORITIES, user.getAuthorities())
                    .claim(CLAIM_ATTRIBUTES, user.getAttributes());
        }

        String tokenValue = builder.signWith(secretKey).compact();
        if (properties.getMode() != JwtMode.STATELESS) {
            sessionStore.save(createSession(user, tokenId, sessionId, issuedAt, expiresAt));
        }

        JwtToken token = new JwtToken();
        token.setToken(tokenValue);
        token.setTokenId(tokenId);
        token.setSessionId(sessionId);
        token.setMode(properties.getMode());
        token.setIssuedAt(issuedAt);
        token.setExpiresAt(expiresAt);
        return token;
    }

    public JwtAuthenticationToken authenticate(String token) {
        Claims claims = parseClaims(token);
        String tokenId = claims.getId();
        if (sessionStore.isTokenBlacklisted(tokenId)) {
            throw new JwtTokenException("token has been revoked");
        }
        JwtMode tokenMode = resolveMode(claims);
        JwtUser user = tokenMode == JwtMode.SIMPLE ? resolveUserFromSession(claims) : resolveUserFromClaims(claims);
        if (tokenMode == JwtMode.MIXIN) {
            validateSession(claims);
        }
        return new JwtAuthenticationToken(user, token);
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception ex) {
            throw new JwtTokenException("invalid jwt token", ex);
        }
    }

    public int forceLogoutByUserId(String userId) {
        return sessionStore.expireByUserId(userId);
    }

    public boolean forceLogoutSession(String sessionId) {
        return sessionStore.expireSession(sessionId);
    }

    public void forceLogoutToken(String token) {
        Claims claims = parseClaims(token);
        Date expiration = claims.getExpiration();
        sessionStore.blacklistToken(claims.getId(), expiration == null ? Instant.now().plus(properties.getTimeout())
                : expiration.toInstant());
        Object sessionId = claims.get(CLAIM_SESSION_ID);
        if (sessionId instanceof String value) {
            sessionStore.expireSession(value);
        }
    }

    private JwtSession createSession(JwtUser user, String tokenId, String sessionId, Instant issuedAt, Instant expiresAt) {
        JwtSession session = new JwtSession();
        session.setSessionId(sessionId);
        session.setTokenId(tokenId);
        session.setUser(user);
        session.setCreatedAt(issuedAt);
        session.setLastAccessAt(issuedAt);
        session.setExpiresAt(expiresAt);
        session.setActiveExpiresAt(activeExpiresAt(issuedAt));
        return session;
    }

    private JwtUser resolveUserFromSession(Claims claims) {
        JwtSession session = validateSession(claims);
        return session.getUser();
    }

    private JwtSession validateSession(Claims claims) {
        String sessionId = claims.get(CLAIM_SESSION_ID, String.class);
        JwtSession session = sessionStore.findBySessionId(sessionId)
                .orElseThrow(() -> new JwtTokenException("session does not exist"));
        Instant now = Instant.now();
        if (session.isExpired() || session.getExpiresAt().isBefore(now)) {
            throw new JwtTokenException("session has expired");
        }
        if (session.getActiveExpiresAt() != null && session.getActiveExpiresAt().isBefore(now)) {
            sessionStore.expireSession(session.getSessionId());
            throw new JwtTokenException("session active timeout has expired");
        }
        sessionStore.refresh(session.getSessionId(), now, activeExpiresAt(now));
        return session;
    }

    @SuppressWarnings("unchecked")
    private JwtUser resolveUserFromClaims(Claims claims) {
        JwtUser user = new JwtUser();
        user.setUserId(claims.getSubject());
        user.setUsername(claims.get(CLAIM_USERNAME, String.class));
        Object authorities = claims.get(CLAIM_AUTHORITIES);
        if (authorities instanceof List<?> list) {
            user.setAuthorities(list.stream().map(String::valueOf).toList());
        }
        Object attributes = claims.get(CLAIM_ATTRIBUTES);
        if (attributes instanceof Map<?, ?> map) {
            user.setAttributes((Map<String, Object>) map);
        }
        return user;
    }

    private JwtMode resolveMode(Claims claims) {
        String mode = claims.get(CLAIM_MODE, String.class);
        return mode == null ? properties.getMode() : JwtMode.valueOf(mode);
    }

    private Instant activeExpiresAt(Instant now) {
        return properties.getActiveTimeout().isZero() || properties.getActiveTimeout().isNegative()
                ? null
                : now.plus(properties.getActiveTimeout());
    }
}
