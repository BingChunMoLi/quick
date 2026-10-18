package com.bingchunmoli.oauth.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.*;

/** Versioned data-only encoding; never serializes Java objects or client credentials. */
public final class TokenSnapshotCodec {
    private TokenSnapshotCodec() { }
    public static String key(AuthorizedClientRef ref) {
        try {
            byte[] a = ref.registrationId().getBytes(StandardCharsets.UTF_8);
            byte[] c = ref.principalName().getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(a.length); out.write(a); out.write(c);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (Exception ex) { throw new IllegalStateException("Unable to derive OAuth2 key"); }
    }
    public static String encode(OAuth2AuthorizedClient client) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(1);
            text(out, client.getClientRegistration().getRegistrationId());
            text(out, client.getPrincipalName());
            OAuth2AccessToken token = client.getAccessToken();
            text(out, token.getTokenValue()); instant(out, token.getIssuedAt()); instant(out, token.getExpiresAt());
            out.writeInt(token.getScopes().size());
            for (String scope : new TreeSet<>(token.getScopes())) text(out, scope);
            OAuth2RefreshToken refresh = client.getRefreshToken();
            out.writeBoolean(refresh != null);
            if (refresh != null) { text(out, refresh.getTokenValue()); instant(out, refresh.getIssuedAt()); instant(out, refresh.getExpiresAt()); }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException ex) { throw new IllegalStateException("Cannot encode OAuth2 authorization"); }
    }
    public static OAuth2AuthorizedClient decode(String encoded, ClientRegistrationRepository registrations) {
        try {
            if (encoded.length() > 4_000_000) throw new IOException();
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
            if (in.readInt() != 1) throw new IOException();
            var registration = registrations.findByRegistrationId(text(in));
            if (registration == null) throw new IOException();
            String principal = text(in), value = text(in);
            Instant issued = instant(in), expires = instant(in);
            int count = in.readInt();
            if (count < 0 || count > 1000) throw new IOException();
            Set<String> scopes = new LinkedHashSet<>();
            for (int i = 0; i < count; i++) scopes.add(text(in));
            OAuth2AccessToken access = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, value, issued, expires, scopes);
            OAuth2RefreshToken refresh = in.readBoolean() ? new OAuth2RefreshToken(text(in), instant(in), instant(in)) : null;
            if (in.available() != 0) throw new IOException();
            return new OAuth2AuthorizedClient(registration, principal, access, refresh);
        } catch (Exception ex) { throw new IllegalStateException("Invalid OAuth2 authorization snapshot"); }
    }
    public static Instant effectiveExpiration(OAuth2AuthorizedClient client) {
        return client.getRefreshToken() != null ? client.getRefreshToken().getExpiresAt() : client.getAccessToken().getExpiresAt();
    }
    public static void text(DataOutputStream out, String value) throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        if (data.length > 1_000_000) throw new IOException();
        out.writeInt(data.length); out.write(data);
    }
    public static String text(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > 1_000_000 || length > in.available()) throw new IOException();
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }
    private static void instant(DataOutputStream out, Instant value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) { out.writeLong(value.getEpochSecond()); out.writeInt(value.getNano()); }
    }
    private static Instant instant(DataInputStream in) throws IOException {
        return in.readBoolean() ? Instant.ofEpochSecond(in.readLong(), in.readInt()) : null;
    }
}
