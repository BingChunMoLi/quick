package com.bingchunmoli.oauth.server.authorization.autoconfigure;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;
class SigningKeysTest {
    @TempDir Path directory;
    @Test void missingKeyDoesNotSilentlyCreateEphemeralSigningMaterial() {
        assertThatThrownBy(()->SigningKeys.load(new QuickAuthorizationServerProperties.Signing(),new DefaultResourceLoader())).isInstanceOf(IllegalStateException.class);
    }
    @Test void persistentJwkSupportsActiveAndRetiredKeys() throws Exception {
        var active=new RSAKeyGenerator(2048).keyID("active").generate();var retired=new RSAKeyGenerator(2048).keyID("retired").generate();
        Path file=directory.resolve("keys.json");Files.writeString(file,new JWKSet(List.of(active,retired.toPublicJWK())).toString(false));
        var options=new QuickAuthorizationServerProperties.Signing();options.setJwkSetLocation(file.toUri().toString());options.setActiveKeyId("active");
        var source=SigningKeys.load(options,new DefaultResourceLoader());
        var encoder=new NimbusJwtEncoder(source);encoder.setJwkSelector(keys->keys.stream().filter(JWK::isPrivate).findFirst().orElseThrow());
        var token=encoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder().subject("alice").build()));
        assertThat(token.getHeaders()).containsEntry("kid","active");
        var oldEncoder=new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new JWKSet(retired)));
        var oldToken=oldEncoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder().subject("retired-user").build()));
        var decoder=org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration.jwtDecoder(source);
        assertThat(decoder.decode(oldToken.getTokenValue()).getSubject()).isEqualTo("retired-user");
        assertThat(decoder.decode(token.getTokenValue()).getSubject()).isEqualTo("alice");
        assertThat(source.get(new com.nimbusds.jose.jwk.JWKSelector(new com.nimbusds.jose.jwk.JWKMatcher.Builder().build()),null)).hasSize(2);
    }
    @Test void duplicateKidAndWeakRsaAreRejected() throws Exception {
        var key=new RSAKeyGenerator(2048).keyID("duplicate").generate();
        Path file=directory.resolve("keys.json");Files.writeString(file,new JWKSet(List.of(key,key.toPublicJWK())).toString(false));
        var options=new QuickAuthorizationServerProperties.Signing();options.setJwkSetLocation(file.toUri().toString());
        assertThatThrownBy(()->SigningKeys.load(options,new DefaultResourceLoader())).isInstanceOf(IllegalStateException.class);
        var generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(1024);var pair=generator.generateKeyPair();
        var weak=new RSAKey.Builder((java.security.interfaces.RSAPublicKey)pair.getPublic()).privateKey(pair.getPrivate()).keyID("weak").build();
        Files.writeString(file,new JWKSet(weak).toString(false));
        assertThatThrownBy(()->SigningKeys.load(options,new DefaultResourceLoader())).isInstanceOf(IllegalStateException.class);
    }
    @Test void pkcs12CanBeLoadedUsingConfiguredAlias() throws Exception {
        Path file=directory.resolve("keys.p12");
        String keytool=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool").toString();
        Process command=new ProcessBuilder(keytool,"-genkeypair","-alias","active","-keyalg","RSA","-keysize","2048","-validity","2",
                "-dname","CN=Quick Test","-storetype","PKCS12","-keystore",file.toString(),"-storepass","test-only-password").redirectErrorStream(true).start();
        command.getInputStream().readAllBytes();assertThat(command.waitFor()).isEqualTo(0);
        var options=new QuickAuthorizationServerProperties.Signing();options.setKeyStoreLocation(file.toUri().toString());
        options.setKeyStorePassword("test-only-password");options.setKeyAlias("active");
        assertThat(SigningKeys.load(options,new DefaultResourceLoader())).isNotNull();
    }
}
