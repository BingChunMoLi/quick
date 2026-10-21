package com.bingchunmoli.util;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.exception.SignParamIllegalArgumentException;
import com.bingchunmoli.filter.CacheFilter;
import com.bingchunmoli.interceptor.SignInterceptor;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class SignatureBodyRegressionTest {
    private static KeyPair keys;

    @BeforeAll
    static void keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"text/plain", "application/xml", "application/octet-stream", "multipart/form-data;boundary=example", "application/*"})
    void rawBodiesCannotBeAcceptedUnderASignatureThatDoesNotCoverThem(String type) throws Exception {
        var properties = settings(HEAD);
        var endpoint = new Endpoint();
        var mvc = mvc(properties, null, endpoint);
        String signature = sign("post /echo\nauthorization=");
        var request = post("/echo").content("amount=999").header("sign", signature);
        if (type != null && !type.isEmpty()) request.contentType(type);
        assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(endpoint.calls.get()).isZero();
    }

    @Test
    void unknownLengthBodiesCannotBypassTheMediaTypeGuard() {
        var request = new MockHttpServletRequest("POST", "/echo") {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        request.setContent("amount=999".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Transfer-Encoding", "chunked");
        request.addHeader("sign", "placeholder");
        var redis = mock(RedisUtil.class);
        assertThatThrownBy(() -> verifier(settings(HEAD), redis).verify(request))
                .isInstanceOf(SignParamIllegalArgumentException.class).hasMessageContaining("bodies require");
        verifyNoInteractions(redis);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "PATCH", "DELETE"})
    void urlEncodedBodiesOnMethodsWithoutServletFormParsingAreRejected(String method) {
        var request = new MockHttpServletRequest(method, "/echo");
        request.setContentType("application/x-www-form-urlencoded");
        request.setContent("amount=999".getBytes(StandardCharsets.UTF_8));
        request.addHeader("sign", "placeholder");
        assertThatThrownBy(() -> verifier(settings(HEAD), null).getSignParam(request))
                .isInstanceOf(SignParamIllegalArgumentException.class);
    }

    @Test
    void postFormFieldsStillVerifyAndReachTheController() throws Exception {
        var properties = settings(BODY);
        var endpoint = new Endpoint();
        String signature = sign("post /form\namount=1&authorization=");
        var response = mvc(properties, null, endpoint).perform(post("/form")
                .contentType("application/x-www-form-urlencoded").param("amount", "1").param("sign", signature))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("1");
        assertThat(endpoint.calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.class, names = {"BODY", "ALL"})
    void aJsonSignatureCanBeInsertedWithoutChangingItsInputAndStillProtectsBusinessData(
            InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition position) throws Exception {
        var properties = settings(position);
        var endpoint = new Endpoint();
        var mvc = mvc(properties, null, endpoint);
        // Independent client construction: first serialize with an empty signature string.
        String unsignedBody = "{\"amount\":1,\"sign\":\"\"}";
        String signature = sign("post /echo\nauthorization=&body=" + SignatureCanonicalizer.encodeComponent(unsignedBody));
        String wireBody = "{\"amount\":1,\"sign\":\"" + signature + "\"}";
        var response = mvc.perform(post("/echo").contentType("application/json").content(wireBody))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo(wireBody);
        assertThat(mvc.perform(post("/echo").contentType("application/json").content(wireBody.replace("amount\":1", "amount\":999")))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(endpoint.calls.get()).isEqualTo(1);
    }

    @Test
    void customNestedSignaturePreservesBusinessNumberSpellingWhitespaceAndUnicode() throws Exception {
        var properties = settings(BODY);
        properties.getSign().getSign().setName("proof");
        String emptyBody = "{ \"amount\":1.00, \"label\":\"中文&=\", \"auth\":{\"proof\":\"\"} }";
        String signature = sign("post /echo\nauthorization=&body=" + SignatureCanonicalizer.encodeComponent(emptyBody));
        String wireBody = emptyBody.replace("\"proof\":\"\"", "\"proof\":\"" + signature + "\"");
        var response = mvc(properties, null, new Endpoint()).perform(post("/echo")
                .contentType("application/problem+json").characterEncoding("UTF-8").content(wireBody))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(wireBody);
    }

    @ParameterizedTest
    @EnumSource(value = InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.class, names = {"HEAD", "QUERY", "ALL"})
    void signatureFromHeaderOrQueryStillSignsAnUnchangedBusinessFieldNamedSign(
            InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition position) throws Exception {
        var properties = settings(position);
        String body = "{\"sign\":\"business-data\",\"amount\":1}";
        String signature = sign("post /echo\nauthorization=&body=" + SignatureCanonicalizer.encodeComponent(body));
        var request = post("/echo").contentType("application/json").content(body);
        if (position == QUERY) request.param("sign", signature);
        else request.header("sign", signature);
        assertThat(mvc(properties, null, new Endpoint()).perform(request).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{invalid", "{\"sign\":\"one\",\"sign\":\"two\"}", "{\"sign\":\"one\"} {}",
            "{\"sign\":null}", "{\"sign\":123}", "{\"sign\":{}}", "{\"sign\":\"one\",\"auth\":{\"sign\":\"two\"}}"})
    void malformedOrAmbiguousJsonSignatureBodiesReturn400AndDoNotConsumeNonce(String body) throws Exception {
        for (var position : new InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition[]{BODY, ALL}) {
            var properties = settings(position);
            properties.getSign().setNonce(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, "nonce", HEAD));
            var redis = mock(RedisUtil.class);
            var endpoint = new Endpoint();
            assertThat(mvc(properties, redis, endpoint).perform(post("/echo").contentType("application/json")
                    .content(body).header("nonce", "unused")).andReturn().getResponse().getStatus()).isEqualTo(400);
            assertThat(endpoint.calls.get()).isZero();
            verifyNoInteractions(redis);
        }
    }

    @Test
    void successfulJsonBodySignaturesClaimNonceOnceAndRejectReplay() throws Exception {
        var properties = settings(BODY);
        properties.getSign().setNonce(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, "nonce", BODY));
        properties.getSign().setTimestamp(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, "t", BODY));
        properties.getSign().setSignValidTime(60_000);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String body = "{\"amount\":1,\"t\":" + timestamp + ",\"nonce\":\"unique\",\"sign\":\"\"}";
        String signature = sign("post /echo\nauthorization=&body=" + SignatureCanonicalizer.encodeComponent(body)
                + "&nonce=unique&t=" + timestamp);
        String wireBody = body.replace("\"sign\":\"\"", "\"sign\":\"" + signature + "\"");
        var redis = mock(RedisUtil.class);
        when(redis.setObjectIfAbsent(properties.getSign().getNonceRedisPrefix() + "unique", "unique", 60_000L, TimeUnit.MILLISECONDS))
                .thenReturn(true, false);
        var endpoint = new Endpoint();
        var mvc = mvc(properties, redis, endpoint);
        assertThat(mvc.perform(post("/echo").contentType("application/json").content(wireBody))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(post("/echo").contentType("application/json").content(wireBody))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(endpoint.calls.get()).isEqualTo(1);
    }

    @Test
    void blankingTheSignatureDoesNotReserializeAnyOtherJsonValues() {
        assertThat(SignatureCanonicalizer.jsonBodyForSigning("{\n \"a\":1.00,\"sign\":\"some\\\"value\",\"b\":\"%&=\"\n}", "sign"))
                .isEqualTo("{ \"a\":1.00,\"sign\":\"\",\"b\":\"%&=\"}");
    }

    private static InterceptorsAutoConfigurationProperties settings(
            InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition position) {
        var properties = new InterceptorsAutoConfigurationProperties();
        var sign = new InterceptorsAutoConfigurationProperties.SignProperties();
        sign.setSign(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, "sign", position));
        sign.setPublicKey(Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
        properties.setSign(sign);
        return properties;
    }

    private static SHA256WithRSASignUtil verifier(InterceptorsAutoConfigurationProperties properties, RedisUtil redis) {
        return new SHA256WithRSASignUtil(new ObjectMapper(), properties.getSign(), redis);
    }

    private static MockMvc mvc(InterceptorsAutoConfigurationProperties properties, RedisUtil redis, Endpoint endpoint) {
        return MockMvcBuilders.standaloneSetup(endpoint).addFilters(new CacheFilter())
                .addInterceptors(new SignInterceptor(properties, verifier(properties, redis))).build();
    }

    private static String sign(String text) throws Exception {
        var signature = Signature.getInstance("SHA256WithRSA");
        signature.initSign(keys.getPrivate());
        signature.update(text.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    @RestController
    static class Endpoint {
        final AtomicInteger calls = new AtomicInteger();
        @PostMapping(value = "/echo", produces = "text/plain;charset=UTF-8")
        String echo(@RequestBody String body) { calls.incrementAndGet(); return body; }
        @PostMapping("/form")
        String form(@RequestParam(name = "amount") String amount) { calls.incrementAndGet(); return amount; }
    }
}
