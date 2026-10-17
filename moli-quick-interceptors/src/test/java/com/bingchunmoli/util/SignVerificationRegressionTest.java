package com.bingchunmoli.util;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.exception.SignParamIllegalArgumentException;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SignVerificationRegressionTest {
    @Test
    void extractingParametersAndInvalidSignaturesDoNotConsumeNonce() {
        RedisTemplate template = mock(RedisTemplate.class);
        var util = new MD5SignUtil(new ObjectMapper(), settings("MD5"), new RedisUtil(template));
        var request = request("invalid");
        util.getSignParam(request);
        assertThat(util.verify(request)).isFalse();
        verifyNoInteractions(template);
    }

    @Test
    void onlyOneConcurrentRequestCanClaimTheSameNonce() throws Exception {
        RedisTemplate template = mock(RedisTemplate.class);
        ValueOperations operations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(operations);
        var barrier = new CyclicBarrier(2);
        var claimed = new AtomicBoolean();
        when(operations.setIfAbsent("moli:sign:nonce:shared", "shared", 2000L, TimeUnit.MILLISECONDS))
                .thenAnswer(invocation -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return claimed.compareAndSet(false, true);
                });
        var util = new MD5SignUtil(new ObjectMapper(), settings("MD5"), new RedisUtil(template));
        String signature = DigestUtils.md5DigestAsHex(unsigned().getBytes(StandardCharsets.UTF_8));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Boolean> verify = () -> {
            try { return util.verify(request(signature)); }
            catch (SignParamIllegalArgumentException duplicate) { return false; }
        };
        try {
            var first = executor.submit(verify);
            var second = executor.submit(verify);
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            verify(operations, times(2)).setIfAbsent("moli:sign:nonce:shared", "shared", 2000L, TimeUnit.MILLISECONDS);
            verifyNoMoreInteractions(operations);
        } finally { executor.shutdownNow(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SHA256withRSA", "MD5withRSA"})
    void rsaAlgorithmsAlsoClaimNonceOnlyAfterSuccessfulVerification(String algorithm) throws Exception {
        RedisTemplate template = mock(RedisTemplate.class);
        ValueOperations operations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(operations);
        when(operations.setIfAbsent("moli:sign:nonce:shared", "shared", 2000L, TimeUnit.MILLISECONDS))
                .thenReturn(true, false);
        var keys = KeyPairGenerator.getInstance("RSA"); keys.initialize(2048);
        var pair = keys.generateKeyPair();
        var settings = settings(algorithm);
        settings.setPublicKey(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        RSAAbstractSignUtil util = algorithm.equals("MD5withRSA")
                ? new MD5WithRSASignUtil(new ObjectMapper(), settings, new RedisUtil(template))
                : new SHA256WithRSASignUtil(new ObjectMapper(), settings, new RedisUtil(template));
        Signature signer = Signature.getInstance(algorithm);
        signer.initSign(pair.getPrivate()); signer.update("wrong payload".getBytes(StandardCharsets.UTF_8));
        assertThat(util.verify(request(Base64.getEncoder().encodeToString(signer.sign())))).isFalse();
        verifyNoInteractions(operations);
        signer.initSign(pair.getPrivate()); signer.update(unsigned().getBytes(StandardCharsets.UTF_8));
        String validSignature = Base64.getEncoder().encodeToString(signer.sign());
        assertThat(util.verify(request(validSignature))).isTrue();
        assertThatThrownBy(() -> util.verify(request(validSignature)))
                .isInstanceOf(SignParamIllegalArgumentException.class).hasMessageContaining("repeated nonce");
        verify(operations, times(2)).setIfAbsent("moli:sign:nonce:shared", "shared", 2000L, TimeUnit.MILLISECONDS);
        verifyNoMoreInteractions(operations);
    }

    @Test
    void missingRedisAcknowledgementDoesNotAllowTheRequest() {
        RedisTemplate template = mock(RedisTemplate.class);
        ValueOperations operations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(operations);
        when(operations.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(null);
        var util = new MD5SignUtil(new ObjectMapper(), settings("MD5"), new RedisUtil(template));
        String signature = DigestUtils.md5DigestAsHex(unsigned().getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> util.verify(request(signature))).isInstanceOf(SignParamIllegalArgumentException.class);
    }

    private static InterceptorsAutoConfigurationProperties.SignProperties settings(String algorithm) {
        var settings = new InterceptorsAutoConfigurationProperties.SignProperties();
        settings.setAlgorithm(algorithm);
        settings.setSign(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, "sign",
                InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.HEAD));
        settings.setNonce(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, "nonce",
                InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.QUERY));
        return settings;
    }

    private static MockHttpServletRequest request(String signature) {
        var request = new MockHttpServletRequest("GET", "/api/demo");
        request.addParameter("nonce", "shared"); request.addHeader("sign", signature);
        return request;
    }

    private static String unsigned() { return "get /api/demo\nauthorization=&nonce=shared"; }
}
