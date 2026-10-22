package com.bingchunmoli.oauth.client.redis;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RedisAuthorizationRequestRepositoryTest {
    private static OAuth2AuthorizationRequest authorization(boolean formPost) {
        return OAuth2AuthorizationRequest.authorizationCode().authorizationUri("https://provider.test/auth")
                .clientId("app").redirectUri("https://app.test/login/oauth2/code/apple").state("random-state")
                .additionalParameters(values -> { if (formPost) values.put("response_mode", "form_post"); }).build();
    }
    @SuppressWarnings("unchecked")
    private static StringRedisTemplate redis() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        return redis;
    }
    @Test void formPostCookieIsSecureHttpOnlyAndCrossSite() {
        var repository = new RedisAuthorizationRequestRepository(redis(), Duration.ofMinutes(10), true);
        var request = new MockHttpServletRequest(); request.setContextPath("/app");
        var response = new MockHttpServletResponse(); repository.saveAuthorizationRequest(authorization(true), request, response);
        assertThat(response.getHeader("Set-Cookie")).contains("SameSite=None", "Secure", "HttpOnly", "Path=/app");
    }
    @Test void queryCallbackKeepsLaxCookie() {
        var repository = new RedisAuthorizationRequestRepository(redis(), Duration.ofMinutes(10), false);
        var response = new MockHttpServletResponse(); repository.saveAuthorizationRequest(authorization(false), new MockHttpServletRequest(), response);
        assertThat(response.getHeader("Set-Cookie")).contains("SameSite=Lax").doesNotContain("Secure");
    }
    @Test void insecureFormPostIsRejectedBeforeSavingState() {
        var redis = redis(); var repository = new RedisAuthorizationRequestRepository(redis, Duration.ofMinutes(10), false);
        assertThatThrownBy(() -> repository.saveAuthorizationRequest(authorization(true), new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("HTTPS");
        verify(redis.opsForValue(), never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }
}
