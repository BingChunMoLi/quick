package com.bingchunmoli.security.jwt;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class JwtAuthenticationFilterTest {
    private final JwtTokenProperties properties = new JwtTokenProperties();
    private final JwtTokenService tokens = mock(JwtTokenService.class);
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidTokenReturnsUnauthorizedWithoutRunningTheChain() throws Exception {
        var request = request();
        var response = new MockHttpServletResponse();
        when(tokens.authenticate("bad-token")).thenThrow(new JwtTokenException("private-validation-detail"));
        new JwtAuthenticationFilter(new JwtTokenResolver(properties), tokens).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).doesNotContain("private-validation-detail", "bad-token");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(chain);
    }

    @Test
    void failureClearsPartiallyPopulatedContextAndCallsCustomEntryPoint() throws Exception {
        var request = request();
        var response = new MockHttpServletResponse();
        AuthenticationEntryPoint entryPoint = mock(AuthenticationEntryPoint.class);
        when(tokens.authenticate("bad-token")).thenAnswer(call -> {
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated("partial-user", null, List.of()));
            throw new JwtTokenException("invalid token");
        });
        new JwtAuthenticationFilter(new JwtTokenResolver(properties), tokens, entryPoint).doFilter(request, response, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(entryPoint).commence(eq(request), eq(response), any());
        verifyNoInteractions(chain);
    }

    @Test
    void existingAuthenticationDoesNotTriggerJwtValidation() throws Exception {
        var authentication = UsernamePasswordAuthenticationToken.authenticated("host-user", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var request = request();
        var response = new MockHttpServletResponse();
        new JwtAuthenticationFilter(new JwtTokenResolver(properties), tokens).doFilter(request, response, chain);
        verifyNoInteractions(tokens);
        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication);
    }

    @Test
    void validTokenPopulatesContextAndContinuesChain() throws Exception {
        var authentication = new JwtAuthenticationToken(new JwtUser("id", "user", List.of(), Map.of()), "bad-token");
        when(tokens.authenticate("bad-token")).thenReturn(authentication);
        var request = request();
        var response = new MockHttpServletResponse();
        new JwtAuthenticationFilter(new JwtTokenResolver(properties), tokens).doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication);
    }

    @Test
    void doesNotTurnStorageFailuresIntoAuthenticationFailures() throws Exception {
        when(tokens.authenticate("bad-token")).thenThrow(new IllegalStateException("storage unavailable"));
        assertThatThrownBy(() -> new JwtAuthenticationFilter(new JwtTokenResolver(properties), tokens)
                .doFilter(request(), new MockHttpServletResponse(), chain))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(chain);
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("GET", "/private");
        request.addHeader("Authorization", "Bearer bad-token");
        return request;
    }
}
