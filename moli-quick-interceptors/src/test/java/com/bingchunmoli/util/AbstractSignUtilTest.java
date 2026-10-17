package com.bingchunmoli.util;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.bean.SignParamDTO;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AbstractSignUtilTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReadSignFromQueryWhenPositionIsAll() {
        InterceptorsAutoConfigurationProperties.SignProperties properties = signProperties();
        MD5SignUtil signUtil = new MD5SignUtil(objectMapper, properties, mock(RedisUtil.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/demo");
        request.addParameter("name", "moli");
        request.addParameter("sign", "abc");

        SignParamDTO signParam = signUtil.getSignParam(request);

        assertThat(signParam.getSignatureStr()).isEqualTo("abc");
        assertThat(signParam.getUnsignedStr()).isEqualTo("get /api/demo\nauthorization=&name=moli");
    }

    @Test
    void shouldReadBodyPositionParamFromRequestParametersWhenBodyIsEmpty() {
        InterceptorsAutoConfigurationProperties.SignProperties properties = signProperties();
        properties.setSignValidTime(60_000);
        properties.setTimestamp(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(
                true,
                "timestamp",
                InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.BODY
        ));
        MD5SignUtil signUtil = new MD5SignUtil(objectMapper, properties, mock(RedisUtil.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/demo");
        request.addParameter("timestamp", String.valueOf(System.currentTimeMillis()));
        request.addParameter("sign", "abc");

        SignParamDTO signParam = signUtil.getSignParam(request);

        assertThat(signParam.getUnsignedStr()).contains("timestamp=");
    }

    @Test
    void shouldExtractNonceWithoutClaimingItDuringCanonicalization() {
        InterceptorsAutoConfigurationProperties.SignProperties properties = signProperties();
        properties.setNonce(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(
                true,
                "nonce",
                InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.QUERY
        ));
        RedisUtil redisUtil = mock(RedisUtil.class);
        MD5SignUtil signUtil = new MD5SignUtil(objectMapper, properties, redisUtil);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/demo");
        request.addParameter("nonce", "abc123");
        request.addParameter("sign", "abc");

        var parameters = signUtil.getSignParam(request);
        assertThat(parameters.getNonce().getValue()).isEqualTo("abc123");
        verifyNoInteractions(redisUtil);
    }

    private InterceptorsAutoConfigurationProperties.SignProperties signProperties() {
        InterceptorsAutoConfigurationProperties.SignProperties properties = new InterceptorsAutoConfigurationProperties.SignProperties();
        properties.setSign(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(
                true,
                "sign",
                InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.ALL
        ));
        return properties;
    }
}
