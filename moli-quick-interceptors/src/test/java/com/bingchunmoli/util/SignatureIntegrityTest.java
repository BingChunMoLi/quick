package com.bingchunmoli.util;

import com.bingchunmoli.exception.SignConfigException;
import com.bingchunmoli.filter.CacheFilter;
import com.bingchunmoli.interceptor.SignInterceptor;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class SignatureIntegrityTest {
    private static final String ORIGINAL = "{\"amount\":1}";
    private static final String CHANGED = "{\"amount\":999999}";
    private static KeyPair keys;

    @BeforeAll
    static void keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
    }

    @ParameterizedTest
    @CsvSource({"application/json,body", "application/json,Body", "application/problem+json,body", "application/problem+json,Body"})
    void cannotUseTheOriginalSignatureWithAReplacedJsonBody(String mediaType, String queryName) throws Exception {
        var properties = settings();
        var util = util(properties);
        String signature = signature(canonical(util, mediaType));
        var endpoint = new Endpoint();
        var mvc = MockMvcBuilders.standaloneSetup(endpoint).addFilters(new CacheFilter())
                .addInterceptors(new SignInterceptor(properties, util)).build();

        var original = mvc.perform(post("/transfer").contentType(mediaType).characterEncoding("UTF-8")
                .content(ORIGINAL).header("sign", signature)).andReturn().getResponse();
        assertThat(original.getStatus()).isEqualTo(200);
        assertThat(original.getContentAsString()).isEqualTo(ORIGINAL);
        var tampered = mvc.perform(post("/transfer").contentType(mediaType).characterEncoding("UTF-8")
                .content(CHANGED).param(queryName, ORIGINAL).header("sign", signature)).andReturn().getResponse();
        assertThat(tampered.getStatus()).isEqualTo(400);
        assertThat(endpoint.calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"authorization", "Authorization"})
    void requestParametersCannotReplaceTheSignedAuthenticationHeader(String queryName) throws Exception {
        var properties = settings();
        var util = util(properties);
        var request = new MockHttpServletRequest("POST", "/transfer");
        request.addHeader("Authorization", "Bearer original");
        request.addHeader("sign", "placeholder");
        String signature = signature(util.getSignParam(request).getUnsignedStr());
        var endpoint = new Endpoint();
        var mvc = MockMvcBuilders.standaloneSetup(endpoint).addInterceptors(new SignInterceptor(properties, util)).build();
        var response = mvc.perform(post("/transfer").contentType("text/plain").content(CHANGED)
                .header("Authorization", "Bearer changed").param(queryName, "Bearer original")
                .header("sign", signature)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(endpoint.calls.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"body", "Authorization", "sign"})
    void metadataCannotReplaceReservedFieldsOrTheSignatureField(String field) {
        var settings = settings();
        settings.getSign().setNonce(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, field,
                InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.HEAD));
        assertThatThrownBy(() -> util(settings).getSignParam(new MockHttpServletRequest("GET", "/transfer")))
                .isInstanceOf(SignConfigException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"body", "authorization", "t", "nonce"})
    void integrityAndReplayFieldsCannotBeRemovedByTheIgnoreList(String ignored) {
        var settings = settings();
        settings.getSign().setTimestamp(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, "t",
                InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.HEAD));
        settings.getSign().setNonce(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, "nonce",
                InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.HEAD));
        settings.getSign().setIgnoreParametersList(List.of(ignored));
        assertThatThrownBy(() -> util(settings).getSignParam(new MockHttpServletRequest("GET", "/transfer")))
                .isInstanceOf(SignConfigException.class);
    }

    @Test
    void ordinaryNonJsonBodyParameterKeepsItsExistingSignatureFormat() throws Exception {
        var util = util(settings());
        var request = new MockHttpServletRequest("GET", "/transfer");
        request.addHeader("sign", "placeholder");
        request.addParameter("body", "business-data");
        String canonical = util.getSignParam(request).getUnsignedStr();
        assertThat(canonical).isEqualTo("get /transfer\nauthorization=&body=business-data");
        request.removeHeader("sign");
        request.addHeader("sign", signature(canonical));
        assertThat(util.verify(request)).isTrue();
    }

    private static InterceptorsAutoConfigurationProperties settings() {
        var properties = new InterceptorsAutoConfigurationProperties();
        var settings = new InterceptorsAutoConfigurationProperties.SignProperties();
        settings.setPublicKey(Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
        settings.setSign(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, "sign",
                InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.HEAD));
        properties.setSign(settings);
        return properties;
    }

    private SHA256WithRSASignUtil util(InterceptorsAutoConfigurationProperties settings) {
        return new SHA256WithRSASignUtil(new ObjectMapper(), settings.getSign(), null);
    }

    private String canonical(SHA256WithRSASignUtil util, String mediaType) throws Exception {
        var request = new MockHttpServletRequest("POST", "/transfer");
        request.setContentType(mediaType);
        request.setCharacterEncoding("UTF-8");
        request.setContent(ORIGINAL.getBytes(StandardCharsets.UTF_8));
        request.addHeader("sign", "placeholder");
        var canonical = new AtomicReference<String>();
        new CacheFilter().doFilter(request, new MockHttpServletResponse(), (wrapped, response) ->
                canonical.set(util.getSignParam((HttpServletRequest) wrapped).getUnsignedStr()));
        return canonical.get();
    }

    private String signature(String text) throws Exception {
        var signature = Signature.getInstance("SHA256WithRSA");
        signature.initSign(keys.getPrivate());
        signature.update(text.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    @RestController
    static class Endpoint {
        final AtomicInteger calls = new AtomicInteger();
        @PostMapping("/transfer")
        String transfer(@RequestBody String body) {
            calls.incrementAndGet();
            return body;
        }
    }
}
