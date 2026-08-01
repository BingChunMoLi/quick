package com.bingchunmoli.web.exception;

import com.bingchunmoli.idempotency.exception.DuplicateRequestException;
import com.bingchunmoli.ratelimit.exception.RateLimitExceededException;
import com.bingchunmoli.web.autoconfigure.QuickWebProperties;
import com.bingchunmoli.web.request.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class WebExceptionHandlerIntegrationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        QuickWebProperties properties = new QuickWebProperties();
        WebExceptionResponseFactory factory = new DefaultWebExceptionResponseFactory();
        RequestIdFilter requestIdFilter = new RequestIdFilter(properties.getRequestId(), () -> "generated-request-id");
        mockMvc = standaloneSetup(new TestController())
                .setControllerAdvice(
                        new GlobalWebExceptionHandler(factory, properties),
                        new DuplicateRequestExceptionHandler(factory, properties),
                        new RateLimitExceptionHandler(factory, properties))
                .addFilters(requestIdFilter)
                .build();
    }

    @Test
    void shouldRenderBusinessExceptionWithRequestId() throws Exception {
        mockMvc.perform(get("/business").header("X-Request-Id", "request-123"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("X-Request-Id", "request-123"))
                .andExpect(jsonPath("$.code").value("A1234"))
                .andExpect(jsonPath("$.msg").value("业务状态不允许"))
                .andExpect(jsonPath("$.data.path").value("/business"))
                .andExpect(jsonPath("$.data.requestId").value("request-123"))
                .andExpect(jsonPath("$.data.details.reason").value("state"));
    }

    @Test
    void shouldRenderValidationAndMalformedJsonErrors() throws Exception {
        mockMvc.perform(post("/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"))
                .andExpect(jsonPath("$.data.details.name").value("名称不能为空"));

        mockMvc.perform(post("/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0427"))
                .andExpect(jsonPath("$.msg").value("请求 JSON 解析失败"));
    }

    @Test
    void shouldHideUnexpectedExceptionMessageByDefault() throws Exception {
        mockMvc.perform(get("/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("B0001"))
                .andExpect(jsonPath("$.msg").value("系统执行出错"));
    }

    @Test
    void shouldMapIdempotencyConflict() throws Exception {
        mockMvc.perform(get("/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("A0506"))
                .andExpect(jsonPath("$.msg").value("重复请求，请稍后重试"));
    }

    @Test
    void shouldMapRateLimitAndRetryAfterHeader() throws Exception {
        mockMvc.perform(get("/limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(jsonPath("$.code").value("A0501"))
                .andExpect(jsonPath("$.data.details.permits").value(10))
                .andExpect(jsonPath("$.data.details.retryAfterSeconds").value(2));
    }

    @RestController
    static class TestController {

        @GetMapping("/business")
        String business() {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "A1234",
                    "业务状态不允许",
                    Map.of("reason", "state"));
        }

        @PostMapping("/validate")
        String validate(@Valid @RequestBody RequestBodyDto request) {
            return request.name();
        }

        @GetMapping("/failure")
        String failure() {
            throw new IllegalStateException("sensitive internal message");
        }

        @GetMapping("/duplicate")
        String duplicate() {
            throw new DuplicateRequestException("internal:key");
        }

        @GetMapping("/limited")
        String limited() {
            throw new RateLimitExceededException(
                    "请求过于频繁", "internal:key", 10, Duration.ofMillis(1500));
        }
    }

    record RequestBodyDto(@NotBlank(message = "名称不能为空") String name) {
    }
}
