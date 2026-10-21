package com.bingchunmoli.util;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.exception.SignConfigException;
import com.bingchunmoli.exception.SignParamIllegalArgumentException;
import com.bingchunmoli.filter.CacheFilter;
import com.bingchunmoli.interceptor.SignInterceptor;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class SignatureProtocolRegressionTest {
    private static KeyPair keys;

    @BeforeAll
    static void keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
    }

    static Stream<Arguments> vectors() throws Exception {
        try (var input = SignatureProtocolRegressionTest.class.getResourceAsStream("/signature-canonicalization.json")) {
            JsonNode values = new ObjectMapper().readTree(input);
            var arguments = new ArrayList<Arguments>();
            values.forEach(value -> arguments.add(Arguments.of(value.get("name").asText(), value)));
            return arguments.stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    void javaAndTheVueClientShareTheSameSignatureVectors(String name, JsonNode vector) {
        var request = request(vector.get("method").asText(), vector.get("path").asText());
        vector.get("parameters").properties().forEach(field -> request.addParameter(field.getKey(), field.getValue().asText()));
        assertThat(verifier(settings(), null).getSignParam(request).getUnsignedStr()).isEqualTo(vector.get("expected").asText());
    }

    @Test
    void aLiteralBusinessValueCannotBecomeAnAdditionalParameterUnderTheOriginalRsaSignature() throws Exception {
        var properties = settings();
        var verifier = verifier(properties, null);
        var original = request("GET", "/transfer");
        original.addParameter("title", "invoice&transfer=true");
        var changed = request("GET", "/transfer");
        changed.addParameter("title", "invoice");
        changed.addParameter("transfer", "true");
        String originalInput = verifier.getSignParam(original).getUnsignedStr();
        assertThat(verifier.getSignParam(changed).getUnsignedStr()).isNotEqualTo(originalInput);
        String signature = sign(originalInput);
        var endpoint = new Endpoint();
        var mvc = MockMvcBuilders.standaloneSetup(endpoint).addInterceptors(new SignInterceptor(properties, verifier)).build();
        var accepted = mvc.perform(get("/transfer").param("title", "invoice&transfer=true").header("sign", signature))
                .andReturn().getResponse();
        assertThat(accepted.getStatus()).isEqualTo(200);
        assertThat(accepted.getContentAsString()).isEqualTo("invoice&transfer=true|transfer=false");
        assertThat(mvc.perform(get("/transfer").param("title", "invoice").param("transfer", "true").header("sign", signature))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(endpoint.calls.get()).isEqualTo(1);
    }

    @Test
    void appendingBatchTargetsToASignedRequestFailsBeforeTheController() throws Exception {
        var properties = settings();
        var verifier = verifier(properties, null);
        var request = request("GET", "/batch");
        request.addParameter("id", "1");
        String signature = sign(verifier.getSignParam(request).getUnsignedStr());
        var endpoint = new Endpoint();
        var mvc = MockMvcBuilders.standaloneSetup(endpoint).addInterceptors(new SignInterceptor(properties, verifier)).build();
        assertThat(mvc.perform(get("/batch").param("id", "1").header("sign", signature))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/batch").param("id", "1", "999").header("sign", signature))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(endpoint.calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "file", "sign"})
    void duplicatesAreRejectedEvenWhenEqualIgnoredOrUsedForTheSignature(String name) {
        var request = request("POST", "/batch");
        request.addParameter(name, "same", "same");
        assertThatThrownBy(() -> verifier(settings(), null).getSignParam(request))
                .isInstanceOf(SignParamIllegalArgumentException.class).hasMessageContaining("one value");
    }

    @Test
    void batchesCanStillBeSentAsASignedJsonArray() throws Exception {
        var properties = settings();
        var verifier = verifier(properties, null);
        var endpoint = new Endpoint();
        String signature = sign("post /json-batch\nauthorization=&body=[1,999]");
        var mvc = MockMvcBuilders.standaloneSetup(endpoint).addFilters(new CacheFilter())
                .addInterceptors(new SignInterceptor(properties, verifier)).build();
        var response = mvc.perform(post("/json-batch").contentType("application/json")
                .content("[1,999]").header("sign", signature)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("[1, 999]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"***", "AA==", ""})
    void malformedRsaSignaturesReturnBadRequestInsteadOfServerErrors(String malformed) throws Exception {
        var properties = settings();
        var endpoint = new Endpoint();
        var mvc = MockMvcBuilders.standaloneSetup(endpoint)
                .addInterceptors(new SignInterceptor(properties, verifier(properties, null))).build();
        assertThat(mvc.perform(get("/batch").param("id", "1").header("sign", malformed))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(endpoint.calls.get()).isZero();
    }

    @Test
    void aWellFormedButIncorrectSignatureReturnsUnauthorized() throws Exception {
        var properties = settings();
        var endpoint = new Endpoint();
        var mvc = MockMvcBuilders.standaloneSetup(endpoint)
                .addInterceptors(new SignInterceptor(properties, verifier(properties, null))).build();
        assertThat(mvc.perform(get("/batch").param("id", "1").header("sign", Base64.getEncoder().encodeToString(new byte[256])))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(endpoint.calls.get()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"SHA256WithRSA,***", "SHA256WithRSA,AA==", "SHA256WithRSA,''", "MD5WithRSA,***", "MD5WithRSA,AA==", "MD5WithRSA,''"})
    void malformedSignaturesNeverConsumeNonceInEitherRsaAlgorithm(String algorithm, String malformed) {
        var properties = settings();
        properties.getSign().setAlgorithm(algorithm);
        properties.getSign().setNonce(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, "nonce",
                InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.HEAD));
        var request = request("GET", "/batch");
        request.removeHeader("sign");
        request.addHeader("sign", malformed);
        request.addHeader("nonce", "unique");
        var redis = mock(RedisUtil.class);
        assertThatThrownBy(() -> verifier(properties, redis).verify(request)).isInstanceOf(SignParamIllegalArgumentException.class);
        verifyNoInteractions(redis);
    }

    @ParameterizedTest
    @ValueSource(strings = {"base64", "key-spec", "algorithm"})
    void serverConfigurationErrorsRemainConfigurationErrors(String invalid) {
        var properties = settings();
        switch (invalid) {
            case "base64" -> properties.getSign().setPublicKey("***");
            case "key-spec" -> properties.getSign().setPublicKey("AA==");
            case "algorithm" -> properties.getSign().setAlgorithm("no-such-algorithm");
        }
        assertThatThrownBy(() -> verifier(properties, null).verify(request("GET", "/batch")))
                .isInstanceOf(SignConfigException.class).hasCauseInstanceOf(Exception.class);
    }

    private InterceptorsAutoConfigurationProperties settings() {
        var settings = new InterceptorsAutoConfigurationProperties();
        var properties = new InterceptorsAutoConfigurationProperties.SignProperties();
        properties.setSign(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, "sign",
                InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.HEAD));
        properties.setPublicKey(Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
        settings.setSign(properties);
        return settings;
    }

    private SHA256WithRSASignUtil verifier(InterceptorsAutoConfigurationProperties settings, RedisUtil redis) {
        return new SHA256WithRSASignUtil(new ObjectMapper(), settings.getSign(), redis);
    }

    private MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.addHeader("sign", "placeholder");
        return request;
    }

    private String sign(String text) throws Exception {
        var signature = Signature.getInstance("SHA256WithRSA");
        signature.initSign(keys.getPrivate());
        signature.update(text.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    @RestController
    static class Endpoint {
        final AtomicInteger calls = new AtomicInteger();
        @GetMapping("/transfer")
        String transfer(@RequestParam(name = "title") String title, @RequestParam(name = "transfer", defaultValue = "false") boolean transfer) {
            calls.incrementAndGet();
            return title + "|transfer=" + transfer;
        }
        @GetMapping("/batch")
        String batch(@RequestParam(name = "id") List<Long> ids) { calls.incrementAndGet(); return ids.toString(); }
        @PostMapping("/json-batch")
        String jsonBatch(@RequestBody List<Long> ids) { calls.incrementAndGet(); return ids.toString(); }
    }
}
