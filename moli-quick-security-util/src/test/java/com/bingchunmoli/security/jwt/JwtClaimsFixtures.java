package com.bingchunmoli.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.params.provider.Arguments;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

public final class JwtClaimsFixtures {
    private JwtClaimsFixtures() {
    }

    public static Map<String, Object> claims() {
        return new LinkedHashMap<>(Map.of("iss", "moli-quick", "sub", "user", "jti", "token",
                "exp", Instant.now().plusSeconds(300).getEpochSecond(), "mode", "SIMPLE", "sid", "session"));
    }

    public static Stream<Arguments> invalidClaims() {
        List<Arguments> cases = new ArrayList<>();
        for (String field : List.of("jti", "sub", "sid", "exp")) {
            Map<String, Object> missing = claims();
            missing.remove(field);
            cases.add(Arguments.of("missing " + field, missing));
        }
        for (String field : List.of("jti", "sub", "sid", "mode")) {
            for (Object value : List.of(" ", 42, List.of("unexpected"))) {
                Map<String, Object> invalid = claims();
                invalid.put(field, value);
                cases.add(Arguments.of("invalid " + field + ": " + value, invalid));
            }
        }
        Map<String, Object> unknownMode = claims();
        unknownMode.put("mode", "UNKNOWN");
        cases.add(Arguments.of("unknown mode", unknownMode));
        for (String field : List.of("username", "authorities", "attributes")) {
            Map<String, Object> invalid = claims();
            invalid.put("mode", "STATELESS");
            invalid.put(field, 42);
            cases.add(Arguments.of("invalid " + field, invalid));
        }
        for (List<?> authorities : List.of(List.of(42), List.of(" "))) {
            Map<String, Object> invalid = claims();
            invalid.put("mode", "MIXIN");
            invalid.put("authorities", authorities);
            cases.add(Arguments.of("invalid authority entry " + authorities, invalid));
        }
        return cases.stream();
    }

    public static String sign(Map<String, Object> claims, String secret) throws Exception {
        // Raw JSON preserves invalid standard-claim types instead of the builder coercing them.
        return Jwts.builder().content(new ObjectMapper().writeValueAsBytes(claims))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
