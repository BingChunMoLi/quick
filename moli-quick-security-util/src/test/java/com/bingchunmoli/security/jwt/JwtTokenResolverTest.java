package com.bingchunmoli.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenResolverTest {
    @Test
    void customHeaderPrefixesRetainTheirCaseSensitiveContract() {
        var properties = new JwtTokenProperties();
        properties.setHeaderName("X-Token");
        properties.setTokenPrefix("ApiKey ");
        var resolver = new JwtTokenResolver(properties);
        var request = new MockHttpServletRequest();
        request.addHeader("X-Token", "apikey AbCdEf");
        assertThat(resolver.resolve(request)).isEmpty();
        request.removeHeader("X-Token");
        request.addHeader("X-Token", "ApiKey AbCdEf");
        assertThat(resolver.resolve(request)).contains("AbCdEf");
    }
}
