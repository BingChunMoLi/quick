package com.bingchunmoli.web.exception;

import com.bingchunmoli.web.autoconfigure.QuickWebProperties;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class FrameworkHttpSemanticsTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new Endpoint()).setControllerAdvice(
                new GlobalWebExceptionHandler(new DefaultWebExceptionResponseFactory(), new QuickWebProperties())).build();
    }

    @ParameterizedTest @ValueSource(strings = {"text/plain", "application/xml"})
    void unsupportedMediaTypesRemain415WithTheStandardErrorBody(String type) throws Exception {
        mvc.perform(post("/json").contentType(type).content("payload"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.data.path").value("/json"));
    }

    @Test
    void missingContentTypeAlsoRemainsAClientMediaTypeError() throws Exception {
        mvc.perform(post("/json").content("payload"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("A0001"));
    }

    @Test
    void methodNotAllowedPreservesAllowAndItsExistingBusinessCode() throws Exception {
        mvc.perform(get("/json"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.msg").value("请求方法不支持"));
    }

    @Test
    void incompatibleAcceptRemains406() throws Exception {
        mvc.perform(post("/json").contentType(MediaType.APPLICATION_JSON).content("{}").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void explicitSpringErrorPreservesStatusAndRetryHeaders() throws Exception {
        mvc.perform(get("/retry"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "7"))
                .andExpect(header().string("X-Framework", "kept"))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.msg").value("retry later"));
    }

    @Test
    void customResponseFactoryKeepsItsBodyAndHeadersAlongsideProtocolHeaders() throws Exception {
        WebExceptionResponseFactory factory = context -> ResponseEntity.status(context.status())
                .header("X-Factory", "kept").body(Map.of("custom", context.code()));
        var custom = standaloneSetup(new Endpoint()).setControllerAdvice(
                new GlobalWebExceptionHandler(factory, new QuickWebProperties())).build();
        custom.perform(get("/json"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"))
                .andExpect(header().string("X-Factory", "kept"))
                .andExpect(jsonPath("$.custom").value("A0001"));
        custom.perform(get("/retry"))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "7"))
                .andExpect(header().string("X-Factory", "kept"));
    }

    @Test
    void missingPathVariableIsStillAServerMappingError() throws Exception {
        mvc.perform(get("/path/one"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("B0001"));
    }

    @Test
    void returnValueValidationIsAServerFailure() throws Exception {
        mvc.perform(get("/invalid-return"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("B0001"));
    }

    @Test
    void validJsonRequestsRemainSuccessful() throws Exception {
        mvc.perform(post("/json").contentType(MediaType.APPLICATION_JSON).content("{\"ok\":\"yes\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value("yes"));
    }

    @RestController
    static class Endpoint {
        @PostMapping(value = "/json", consumes = "application/json", produces = "application/json")
        Map<String, String> json(@RequestBody Map<String, String> body) { return body; }
        @GetMapping("/retry")
        void retry() {
            var error = new ErrorResponseException(HttpStatus.TOO_MANY_REQUESTS);
            error.getBody().setDetail("retry later");
            error.getHeaders().set(HttpHeaders.RETRY_AFTER, "7");
            error.getHeaders().set("X-Framework", "kept");
            throw error;
        }
        @GetMapping("/path/{id}")
        String missingPathVariable(@PathVariable("missing") String missing) { return missing; }
        @GetMapping("/invalid-return") @NotBlank
        String invalidReturn() { return ""; }
    }
}
