package com.bingchunmoli.util;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.exception.SignParamIllegalArgumentException;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SignatureMetadataSourceTest {
    @ParameterizedTest
    @CsvSource({"t,HEAD,GET", "t,ALL,GET", "nonce,HEAD,GET", "nonce,ALL,GET", "sign,HEAD,GET", "sign,ALL,GET",
            "t,HEAD,POST", "t,ALL,POST", "nonce,HEAD,POST", "nonce,ALL,POST", "sign,HEAD,POST", "sign,ALL,POST"})
    void headerMetadataCannotSilentlyReplaceQueryOrFormParameters(String name, String position, String method) {
        var properties = settings();
        var field = new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, name, valueOf(position));
        switch (name) {
            case "t" -> properties.setTimestamp(field);
            case "nonce" -> properties.setNonce(field);
            case "sign" -> properties.setSign(field);
        }
        var request = new MockHttpServletRequest(method, "/action");
        if (method.equals("POST")) request.setContentType("application/x-www-form-urlencoded");
        request.addHeader("sign", "placeholder");
        request.addHeader(name, name.equals("t") ? String.valueOf(System.currentTimeMillis()) : "header-value");
        request.addParameter(name, "999");
        var redis = mock(RedisUtil.class);
        assertThatThrownBy(() -> new MD5SignUtil(new ObjectMapper(), properties, redis).verify(request))
                .isInstanceOf(SignParamIllegalArgumentException.class).hasMessageContaining("sources");
        verifyNoInteractions(redis);
    }

    @ParameterizedTest
    @ValueSource(strings = {"t", "nonce", "sign"})
    void equalValuesStillCannotBeDuplicatedAcrossSources(String name) {
        var properties = settings();
        var field = new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, name, HEAD);
        switch (name) {
            case "t" -> properties.setTimestamp(field);
            case "nonce" -> properties.setNonce(field);
            case "sign" -> properties.setSign(field);
        }
        String value = name.equals("t") ? String.valueOf(System.currentTimeMillis()) : "same";
        var request = new MockHttpServletRequest("GET", "/action");
        request.addHeader("sign", "placeholder");
        request.addHeader(name, value);
        request.addParameter(name, value);
        assertThatThrownBy(() -> new MD5SignUtil(new ObjectMapper(), properties, null).getSignParam(request))
                .isInstanceOf(SignParamIllegalArgumentException.class).hasMessageContaining("sources");
    }

    @ParameterizedTest
    @ValueSource(strings = {"t", "nonce"})
    void jsonBodyMetadataCannotReplaceASameNamedQueryValue(String name) {
        var properties = settings();
        var field = new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, name, BODY);
        if (name.equals("t")) properties.setTimestamp(field);
        else properties.setNonce(field);
        String value = name.equals("t") ? String.valueOf(System.currentTimeMillis()) : "unique";
        var request = new MockHttpServletRequest("POST", "/action");
        request.setContentType("application/json");
        request.setContent(("{\"" + name + "\":\"" + value + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        request.addHeader("sign", "placeholder");
        request.addParameter(name, "999");
        assertThatThrownBy(() -> new MD5SignUtil(new ObjectMapper(), properties, null).getSignParam(request))
                .isInstanceOf(SignParamIllegalArgumentException.class).hasMessageContaining("sources");
    }

    @ParameterizedTest
    @ValueSource(strings = {"t", "nonce"})
    void jsonBodyMetadataCannotHaveAnUnsignedAlternateHeader(String name) {
        var properties = settings();
        var field = new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, name, BODY);
        if (name.equals("t")) properties.setTimestamp(field);
        else properties.setNonce(field);
        String value = name.equals("t") ? String.valueOf(System.currentTimeMillis()) : "unique";
        var request = new MockHttpServletRequest("POST", "/action");
        request.setContentType("application/json");
        request.setContent(("{\"" + name + "\":\"" + value + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        request.addHeader("sign", "placeholder");
        request.addHeader(name, "999");
        assertThatThrownBy(() -> new MD5SignUtil(new ObjectMapper(), properties, null).getSignParam(request))
                .isInstanceOf(SignParamIllegalArgumentException.class).hasMessageContaining("sources");
    }

    @ParameterizedTest
    @ValueSource(strings = {"HEAD", "QUERY", "ALL"})
    void metadataFromOneSourceStillProducesTheSameSignedValue(String position) {
        var properties = settings();
        properties.setNonce(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, "nonce", valueOf(position)));
        var request = new MockHttpServletRequest("GET", "/action");
        request.addHeader("sign", "placeholder");
        if (position.equals("QUERY")) request.addParameter("nonce", "unique");
        else request.addHeader("nonce", "unique");
        assertThat(new MD5SignUtil(new ObjectMapper(), properties, null).getSignParam(request).getUnsignedStr())
                .isEqualTo("get /action\nauthorization=&nonce=unique");
    }

    private static InterceptorsAutoConfigurationProperties.SignProperties settings() {
        var properties = new InterceptorsAutoConfigurationProperties.SignProperties();
        properties.setSign(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, "sign", HEAD));
        properties.setSignValidTime(60_000);
        return properties;
    }
}
