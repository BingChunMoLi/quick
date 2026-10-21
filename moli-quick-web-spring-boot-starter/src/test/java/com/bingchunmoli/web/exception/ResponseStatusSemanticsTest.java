package com.bingchunmoli.web.exception;

import com.bingchunmoli.web.autoconfigure.QuickWebProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.annotation.AliasFor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ResponseStatusSemanticsTest {
    private MockMvc mvc;
    private MockMvc springControl;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new Endpoint()).setControllerAdvice(handler(new QuickWebProperties())).build();
        springControl = standaloneSetup(new Endpoint()).build();
    }

    @ParameterizedTest
    @CsvSource({
            "direct,404,Order missing",
            "alias,409,Conflict",
            "inherited,404,Order missing",
            "composed,422,Invalid state",
            "composed-override,410,Order expired",
            "wrapped,404,Order missing",
            "deeply-wrapped,404,Order missing",
            "outer-status,409,Conflict"
    })
    void declaredStatusesMatchSpringWhileKeepingTheStandardBody(String scenario, int status, String message)
            throws Exception {
        springControl.perform(get("/" + scenario)).andExpect(status().is(status));
        mvc.perform(get("/" + scenario))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.msg").value(message))
                .andExpect(jsonPath("$.data.path").value("/" + scenario));
    }

    @Test
    void aWrappedSpringStatusExceptionAlsoKeepsItsHeaders() throws Exception {
        springControl.perform(get("/wrapped-spring"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "7"));
        mvc.perform(get("/wrapped-spring"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "7"))
                .andExpect(jsonPath("$.msg").value("retry later"));
    }

    @Test
    void annotatedServerFailuresUseTheConfiguredSystemCodeAndHideTheInternalMessage() throws Exception {
        var properties = new QuickWebProperties();
        properties.getExceptionHandling().setSystemErrorCode("CUSTOM_SERVER");
        var custom = standaloneSetup(new Endpoint()).setControllerAdvice(handler(properties)).build();
        springControl.perform(get("/server")).andExpect(status().isServiceUnavailable());
        custom.perform(get("/server"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CUSTOM_SERVER"))
                .andExpect(jsonPath("$.msg").value("Service Unavailable"));
    }

    @Test
    void customFactoryReceivesTheDeclaredStatusAndConfiguredClientCode() throws Exception {
        var properties = new QuickWebProperties();
        properties.getExceptionHandling().setClientErrorCode("CUSTOM_CLIENT");
        WebExceptionResponseFactory factory = context -> ResponseEntity.status(context.status())
                .header("X-Factory", "kept").body(Map.of("code", context.code(), "reason", context.message()));
        var custom = standaloneSetup(new Endpoint()).setControllerAdvice(
                new GlobalWebExceptionHandler(factory, properties)).build();
        custom.perform(get("/wrapped"))
                .andExpect(status().isNotFound()).andExpect(header().string("X-Factory", "kept"))
                .andExpect(jsonPath("$.code").value("CUSTOM_CLIENT"))
                .andExpect(jsonPath("$.reason").value("Order missing"));
    }

    @Test
    void declaredReasonsUseTheApplicationsMessageSourceAndRequestLocale() throws Exception {
        var messages = new StaticMessageSource();
        messages.addMessage("order.missing", Locale.FRENCH, "Commande introuvable");
        var handler = handler(new QuickWebProperties());
        handler.setMessageSource(messages);
        var localized = standaloneSetup(new Endpoint()).setControllerAdvice(handler).build();
        localized.perform(get("/localized").locale(Locale.FRENCH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.msg").value("Commande introuvable"));
        localized.perform(get("/localized").locale(Locale.ENGLISH))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.msg").value("order.missing"));
    }

    @Test
    void controllerSpecificHandlersRetainPriority() throws Exception {
        var custom = standaloneSetup(new OverrideEndpoint()).setControllerAdvice(handler(new QuickWebProperties())).build();
        custom.perform(get("/override"))
                .andExpect(status().isUnprocessableEntity()).andExpect(content().string("controller handled"));
    }

    @Test
    void higherPriorityHostAdviceRetainsPriority() throws Exception {
        var custom = standaloneSetup(new Endpoint()).setControllerAdvice(
                handler(new QuickWebProperties()), new HostAdvice()).build();
        custom.perform(get("/direct"))
                .andExpect(status().isGone()).andExpect(content().string("host handled"));
    }

    @Test
    void unannotatedFailuresRemainHiddenUnlessExplicitlyEnabled() throws Exception {
        mvc.perform(get("/ordinary"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.msg").value("系统执行出错"));
        var properties = new QuickWebProperties();
        properties.getExceptionHandling().setIncludeExceptionMessage(true);
        standaloneSetup(new Endpoint()).setControllerAdvice(handler(properties)).build()
                .perform(get("/ordinary"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.msg").value("sensitive internal message"));
    }

    @Test
    void aCyclicCauseChainTerminatesWithTheNormalFallback() {
        var first = new IllegalStateException("first");
        var second = new IllegalArgumentException("second", first);
        first.initCause(second);
        var request = new MockHttpServletRequest("GET", "/cycle");
        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertThat(handler(new QuickWebProperties()).handleUnexpected(first, request).getStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private static GlobalWebExceptionHandler handler(QuickWebProperties properties) {
        return new GlobalWebExceptionHandler(new DefaultWebExceptionResponseFactory(), properties);
    }

    @ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Order missing")
    static class Missing extends RuntimeException { }
    static class ChildMissing extends Missing { }
    @ResponseStatus(HttpStatus.CONFLICT)
    static class Conflict extends RuntimeException {
        Conflict() { }
        Conflict(Throwable cause) { super(cause); }
    }
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    static class Unavailable extends RuntimeException {
        Unavailable() { super("sensitive server details"); }
    }
    @Target(TYPE) @Retention(RUNTIME)
    @ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY)
    @interface BusinessStatus {
        @AliasFor(annotation = ResponseStatus.class, attribute = "code")
        HttpStatus value() default HttpStatus.UNPROCESSABLE_ENTITY;
        @AliasFor(annotation = ResponseStatus.class, attribute = "reason")
        String reason() default "Invalid state";
    }
    @BusinessStatus
    static class InvalidState extends RuntimeException { }
    @BusinessStatus(value = HttpStatus.GONE, reason = "Order expired")
    static class Expired extends RuntimeException { }
    @ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "order.missing")
    static class LocalizedMissing extends RuntimeException { }

    static class RetryLater extends ResponseStatusException {
        RetryLater() { super(HttpStatus.TOO_MANY_REQUESTS, "retry later"); }
        @Override
        public HttpHeaders getHeaders() {
            var headers = new HttpHeaders();
            headers.set("Retry-After", "7");
            return headers;
        }
    }

    @RestController
    static class Endpoint {
        @GetMapping("/{scenario}")
        String fail(@PathVariable("scenario") String scenario) {
            throw switch (scenario) {
                case "direct" -> new Missing();
                case "alias" -> new Conflict();
                case "inherited" -> new ChildMissing();
                case "composed" -> new InvalidState();
                case "composed-override" -> new Expired();
                case "wrapped" -> new IllegalStateException("wrapper", new Missing());
                case "deeply-wrapped" -> new IllegalStateException("outer", new IllegalArgumentException("inner", new Missing()));
                case "outer-status" -> new Conflict(new Missing());
                case "server" -> new Unavailable();
                case "localized" -> new LocalizedMissing();
                case "wrapped-spring" -> new IllegalStateException("wrapper", new RetryLater());
                default -> new IllegalStateException("sensitive internal message");
            };
        }
    }

    @RestController
    static class OverrideEndpoint {
        @GetMapping("/override") String fail() { throw new Missing(); }
        @ExceptionHandler(Missing.class)
        ResponseEntity<String> handle() { return ResponseEntity.unprocessableEntity().body("controller handled"); }
    }

    @RestControllerAdvice @Order(0)
    static class HostAdvice {
        @ExceptionHandler(Missing.class)
        ResponseEntity<String> handle() { return ResponseEntity.status(HttpStatus.GONE).body("host handled"); }
    }
}
