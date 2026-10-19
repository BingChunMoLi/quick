package com.bingchunmoli.oauth.server.authorization.autoconfigure;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import org.springframework.core.io.ResourceLoader;
import org.apache.commons.logging.LogFactory;
final class SigningKeys {
    static JWKSource<SecurityContext> load(QuickAuthorizationServerProperties.Signing options, ResourceLoader resources) {
        try {
            List<JWK> keys;
            if (options.getJwkSetLocation() != null) {
                if (options.getKeyStoreLocation() != null || options.isDevelopmentEphemeral()) throw new IllegalStateException();
                try (var input = resources.getResource(options.getJwkSetLocation()).getInputStream()) {
                    keys = JWKSet.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getKeys();
                }
            } else if (options.getKeyStoreLocation() != null) {
                if (options.isDevelopmentEphemeral()) throw new IllegalStateException();
                var store = KeyStore.getInstance("PKCS12");
                char[] password = Objects.requireNonNull(options.getKeyStorePassword()).toCharArray();
                try (var input = resources.getResource(options.getKeyStoreLocation()).getInputStream()) { store.load(input, password); }
                List<JWK> loaded = new ArrayList<>();
                var aliases = store.aliases();
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    RSAKey key = RSAKey.load(store, alias, password);
                    if (key != null) loaded.add(alias.equals(options.getKeyAlias()) ? key : key.toPublicJWK());
                }
                keys = loaded;
            } else if (options.isDevelopmentEphemeral()) {
                LogFactory.getLog(SigningKeys.class).warn("Using ephemeral OAuth signing key; configure persistent signing keys for production");
                keys = List.of(new RSAKeyGenerator(2048).keyID("development-" + UUID.randomUUID()).generate());
            } else throw new IllegalStateException();
            Set<String> ids = new HashSet<>();
            for (JWK key : keys) {
                if (!(key instanceof RSAKey rsa) || rsa.size() < 2048 || key.getKeyID() == null || key.getKeyID().isBlank() || !ids.add(key.getKeyID()))
                    throw new IllegalStateException();
            }
            String active = options.getActiveKeyId();
            List<JWK> signing = keys.stream().filter(JWK::isPrivate).filter(k -> active == null || active.equals(k.getKeyID())).toList();
            if (signing.size() != 1) throw new IllegalStateException();
            String selected = signing.get(0).getKeyID();
            JWKSet set = new JWKSet(keys.stream().map(k -> k.getKeyID().equals(selected) ? k : k.toPublicJWK()).toList());
            return (selector, context) -> {
                List<JWK> matches = selector.select(set);
                // Signing selectors require private keys. Public selectors keep all retired verification keys.
                if (Boolean.TRUE.equals(selector.getMatcher().isPrivateOnly())) return matches.stream().filter(k -> k.getKeyID().equals(selected)).toList();
                return matches;
            };
        } catch (Exception failure) {
            throw new IllegalStateException("OAuth signing requires valid persistent RSA keys, stable kid and one active private key");
        }
    }
}
