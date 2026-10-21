package com.bingchunmoli.util;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.exception.SignConfigException;
import com.bingchunmoli.exception.SignParamIllegalArgumentException;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SignatureTimestampTest {
    private static final long NOW = 1_792_454_400_000L;
    private final RedisUtil redis = mock(RedisUtil.class);

    @ParameterizedTest
    @ValueSource(strings = {"1792454400001", "1792540800000", "-1", "9223372036854775807", "invalid", "", "1792454399000", "1792454398999"})
    void invalidFutureOrExpiredTimestampFailsWithoutTouchingNonce(String timestamp) {
        var util = util(() -> NOW, settings());
        assertThatThrownBy(() -> util.verify(request(timestamp)))
                .isInstanceOf(SignParamIllegalArgumentException.class);
        verifyNoInteractions(redis);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 999})
    void validTimestampRetainsTheExistingNonceTtl(long age) {
        when(redis.setObjectIfAbsent("moli:sign:nonce:unique", "unique", 1000L, TimeUnit.MILLISECONDS)).thenReturn(true);
        assertThat(util(() -> NOW, settings()).verify(request(Long.toString(NOW - age)))).isTrue();
        verify(redis).setObjectIfAbsent("moli:sign:nonce:unique", "unique", 1000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void replayCannotPassWhenTheNonceExpiresBecauseTheTimestampExpiresAsWell() {
        var clock = new AtomicLong(NOW);
        when(redis.setObjectIfAbsent("moli:sign:nonce:unique", "unique", 1000L, TimeUnit.MILLISECONDS))
                .thenReturn(true, false, true);
        var util = util(clock::get, settings());
        assertThat(util.verify(request(Long.toString(NOW)))).isTrue();
        assertThatThrownBy(() -> util.verify(request(Long.toString(NOW))))
                .isInstanceOf(SignParamIllegalArgumentException.class).hasMessageContaining("repeated nonce");
        clock.addAndGet(1000);
        assertThatThrownBy(() -> util.verify(request(Long.toString(NOW))))
                .isInstanceOf(SignParamIllegalArgumentException.class).hasMessageContaining("timestamp");
        verify(redis, times(2)).setObjectIfAbsent("moli:sign:nonce:unique", "unique", 1000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void signatureWhichExpiresDuringVerificationCannotClaimANonce() {
        var reads = new AtomicInteger();
        var util = util(() -> reads.getAndIncrement() == 0 ? NOW : NOW + 1000, settings());
        assertThatThrownBy(() -> util.verify(request(Long.toString(NOW))))
                .isInstanceOf(SignParamIllegalArgumentException.class).hasMessageContaining("timestamp");
        verifyNoInteractions(redis);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void nonPositiveValidityWindowsAreRejectedAsConfigurationErrors(long timeout) {
        var settings = settings();
        settings.setSignValidTime(timeout);
        assertThatThrownBy(() -> util(() -> NOW, settings).verify(request(Long.toString(NOW))))
                .isInstanceOf(SignConfigException.class);
        verifyNoInteractions(redis);
    }

    private MD5SignUtil util(LongSupplier clock, InterceptorsAutoConfigurationProperties.SignProperties settings) {
        return new MD5SignUtil(new ObjectMapper(), settings, redis) {
            @Override protected long currentTimeMillis() { return clock.getAsLong(); }
        };
    }

    private InterceptorsAutoConfigurationProperties.SignProperties settings() {
        var settings = new InterceptorsAutoConfigurationProperties.SignProperties();
        settings.setSignValidTime(1000);
        settings.setSign(field("sign"));
        settings.setTimestamp(field("t"));
        settings.setNonce(field("nonce"));
        return settings;
    }

    private InterceptorsAutoConfigurationProperties.SignProperties.CustomParam field(String name) {
        return new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, name,
                InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.HEAD);
    }

    private MockHttpServletRequest request(String timestamp) {
        String canonical = "get /api/demo\nauthorization=&nonce=unique&t=" + timestamp;
        String signature = DigestUtils.md5DigestAsHex(canonical.getBytes(StandardCharsets.UTF_8));
        var request = new MockHttpServletRequest("GET", "/api/demo");
        request.addHeader("sign", signature);
        request.addHeader("nonce", "unique");
        request.addHeader("t", timestamp);
        return request;
    }
}
