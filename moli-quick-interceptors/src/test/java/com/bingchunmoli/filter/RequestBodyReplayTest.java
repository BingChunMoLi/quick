package com.bingchunmoli.filter;

import com.bingchunmoli.exception.SignConfigException;
import com.bingchunmoli.interceptor.SignInterceptor;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.bingchunmoli.util.MD5SignUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RequestBodyReplayTest {
    private static final String BODY = "{\n\"name\":\"中文\",\n\"amount\":123\n}";

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "application/problem+json"})
    void signedJsonRemainsReadableByTheController(String contentType) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new EchoController())
                .addFilters(new CacheFilter()).addInterceptors(interceptor()).build();
        mvc.perform(post("/echo").contentType(contentType).characterEncoding("UTF-8").content(BODY)
                        .header("sign", signature()))
                .andExpect(status().isOk()).andExpect(content().string(BODY));
    }

    @Test
    void replacesAnExistingNonReplayableCachingWrapper() throws Exception {
        Filter original = (request, response, chain) -> chain.doFilter(
                new ContentCachingRequestWrapper((HttpServletRequest) request, 1024), response);
        var mvc = MockMvcBuilders.standaloneSetup(new EchoController())
                .addFilters(original, new CacheFilter()).addInterceptors(interceptor()).build();
        mvc.perform(post("/echo").contentType("application/json").characterEncoding("UTF-8").content(BODY)
                        .header("sign", signature()))
                .andExpect(status().isOk()).andExpect(content().string(BODY));
    }

    @Test
    void readerAndInputStreamCanReadTheSameBytesRepeatedly() throws Exception {
        var request = request();
        new CacheFilter().doFilter(request, new MockHttpServletResponse(), (cached, response) -> {
            assertThat(new String(cached.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(BODY);
            assertThat(cached.getReader().lines().toList()).containsExactly("{", "\"name\":\"中文\",", "\"amount\":123", "}");
            assertThat(new String(cached.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(BODY);
        });
    }

    @Test
    void preservesBytesAlreadyReadIntoAnExistingCachingWrapper() throws Exception {
        var cached = new ContentCachingRequestWrapper(request(), 1024);
        assertThat(cached.getInputStream().readNBytes(5)).hasSize(5);
        new CacheFilter().doFilter(cached, new MockHttpServletResponse(), (snapshot, response) ->
                assertThat(new String(snapshot.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(BODY));
    }

    @Test
    void otherHttpWrappersDoNotHideTheReplayableRequestFromTheInterceptor() throws Exception {
        var request = request(); request.addHeader("sign", signature());
        new CacheFilter().doFilter(request, new MockHttpServletResponse(), (cached, response) -> {
            var outer = new HttpServletRequestWrapper((HttpServletRequest) cached);
            try { assertThat(interceptor().preHandle(outer, (MockHttpServletResponse) response, new Object())).isTrue(); }
            catch (Exception error) { throw new RuntimeException(error); }
            assertThat(new String(outer.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(BODY);
        });
    }

    @Test
    void manuallyRegisteredInterceptorFailsBeforeConsumingAnUnwrappedBody() {
        var request = request(); request.addHeader("sign", signature());
        assertThatThrownBy(() -> interceptor().preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(SignConfigException.class).hasMessageContaining("CacheFilter");
        assertThatCode(() -> assertThat(request.getInputStream().readAllBytes()).isEqualTo(BODY.getBytes(StandardCharsets.UTF_8)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsOversizeBodiesWithAndWithoutContentLength(boolean unknownLength) throws Exception {
        var request = unknownLength ? new MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        } : new MockHttpServletRequest();
        request.setContentType("application/json"); request.setContent(new byte[1024 * 1024 + 1]);
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();
        new CacheFilter().doFilter(request, response, (req, res) -> called.set(true));
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(called).isFalse();
    }

    private static MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/echo");
        request.setContentType("application/json"); request.setCharacterEncoding("UTF-8");
        request.setContent(BODY.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static SignInterceptor interceptor() {
        var settings = new InterceptorsAutoConfigurationProperties.SignProperties();
        settings.setSign(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, "sign",
                InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.HEAD));
        var properties = new InterceptorsAutoConfigurationProperties(); properties.setSign(settings);
        return new SignInterceptor(properties, new MD5SignUtil(new ObjectMapper(), settings, null));
    }

    private static String signature() {
        return DigestUtils.md5DigestAsHex(("post /echo\nauthorization=&body=" + BODY.replace("\n", "")).getBytes(StandardCharsets.UTF_8));
    }

    @RestController
    static class EchoController {
        @PostMapping(value = "/echo", produces = "application/json;charset=UTF-8")
        String echo(@RequestBody String body) { return body; }
    }
}
