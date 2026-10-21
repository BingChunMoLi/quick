package com.bingchunmoli.util;

import com.bingchunmoli.filter.CacheFilter;
import com.bingchunmoli.interceptor.SignInterceptor;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.*;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Real Servlet parameter parsing and chunked transport are intentionally covered outside MockMvc. */
class SignatureHttpRegressionTest {
    private static final InterceptorsAutoConfigurationProperties SETTINGS = new InterceptorsAutoConfigurationProperties();
    private static KeyPair keys;
    private static SHA256WithRSASignUtil verifier;
    private static ConfigurableApplicationContext context;
    private static HttpClient client;
    private static ExecutorService clientExecutor;
    private static String base;

    @BeforeAll
    static void start() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        var options = new InterceptorsAutoConfigurationProperties.SignProperties();
        options.setSign(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, "sign", HEAD));
        options.setPublicKey(Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
        SETTINGS.setSign(options);
        verifier = new SHA256WithRSASignUtil(new ObjectMapper(), options, null);
        context = new SpringApplicationBuilder(Host.class).run("--server.address=127.0.0.1", "--server.port=0",
                "--spring.main.banner-mode=off", "--logging.level.root=ERROR");
        base = "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
        clientExecutor = Executors.newCachedThreadPool();
        client = HttpClient.newBuilder().executor(clientExecutor).build();
    }

    @AfterAll
    static void stop() {
        try {
            if (context != null) context.close();
        } finally {
            if (clientExecutor != null) clientExecutor.shutdownNow();
        }
    }

    @BeforeEach
    void reset() {
        SETTINGS.getSign().getSign().setParameterPosition(HEAD);
        SETTINGS.getSign().setTimestamp(null);
        SETTINGS.getSign().setNonce(null);
    }

    @ParameterizedTest
    @EnumSource(value = InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.class, names = {"HEAD", "ALL"})
    void headerTimestampsCannotHideAdditionalMvcBoundQueryOrFormFields(
            InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition position) throws Exception {
        SETTINGS.getSign().setTimestamp(new InterceptorsAutoConfigurationProperties.SignProperties.CustomParam(true, "t", position));
        SETTINGS.getSign().setSignValidTime(60_000);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = sign("get /query-t\nauthorization=&t=" + timestamp);
        var accepted = client.send(HttpRequest.newBuilder(URI.create(base + "/query-t"))
                .header("t", timestamp).header("sign", signature).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(accepted.body()).isEqualTo("0");
        var changed = client.send(HttpRequest.newBuilder(URI.create(base + "/query-t?t=999"))
                .header("t", timestamp).header("sign", signature).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(changed.statusCode()).isEqualTo(400);
        String formSignature = sign("post /form\namount=1&authorization=&t=" + timestamp);
        var form = client.send(HttpRequest.newBuilder(URI.create(base + "/form"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("t", timestamp).header("sign", formSignature)
                .POST(HttpRequest.BodyPublishers.ofString("amount=1&t=999")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(form.statusCode()).isEqualTo(400);
    }

    @Test
    void unsignedRawBodiesAreRejectedWithKnownAndUnknownLengths() throws Exception {
        String signature = sign("post /echo\nauthorization=");
        for (String type : List.of("text/plain", "application/xml", "application/octet-stream", "")) {
            for (boolean chunked : List.of(false, true)) {
                var publisher = chunked ? HttpRequest.BodyPublishers.ofInputStream(() ->
                        new ByteArrayInputStream("amount=999".getBytes(StandardCharsets.UTF_8)))
                        : HttpRequest.BodyPublishers.ofString("amount=999");
                var request = HttpRequest.newBuilder(URI.create(base + "/echo"))
                        .timeout(Duration.ofSeconds(10)).header("sign", signature).POST(publisher);
                if (!type.isEmpty()) request.header("Content-Type", type);
                assertThat(client.send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400);
            }
        }
    }

    @Test
    void servletPostFormParsingStillProtectsTheActualBoundFields() throws Exception {
        SETTINGS.getSign().getSign().setParameterPosition(BODY);
        String signature = sign("post /form\namount=1&authorization=");
        var accepted = post("/form", "application/x-www-form-urlencoded", "amount=1&sign="
                + java.net.URLEncoder.encode(signature, StandardCharsets.UTF_8), null);
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(accepted.body()).isEqualTo("1");
        assertThat(post("/form", "application/x-www-form-urlencoded", "amount=999&sign="
                + java.net.URLEncoder.encode(signature, StandardCharsets.UTF_8), null).statusCode()).isEqualTo(401);
    }

    @Test
    void headerSignaturesStillProtectAndPreserveJsonBodies() throws Exception {
        String body = "{\"amount\":1}";
        String signature = sign("post /echo\nauthorization=&body=" + body);
        var accepted = post("/echo", "application/json", body, signature);
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(accepted.body()).isEqualTo(body);
        assertThat(post("/echo", "application/json", "{\"amount\":999}", signature).statusCode()).isEqualTo(401);
    }

    @ParameterizedTest
    @EnumSource(value = InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.class, names = {"BODY", "ALL"})
    void bodySignaturesWorkAndRejectModifiedBusinessData(
            InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition position) throws Exception {
        SETTINGS.getSign().getSign().setParameterPosition(position);
        String signature = sign("post /echo\nauthorization=&body={\"amount\":1,\"sign\":\"\"}");
        String body = "{\"amount\":1,\"sign\":\"" + signature + "\"}";
        var accepted = post("/echo", "application/json", body, null);
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(accepted.body()).isEqualTo(body);
        assertThat(post("/echo", "application/json", body.replace("amount\":1", "amount\":999"), null).statusCode())
                .isEqualTo(401);
    }

    @ParameterizedTest
    @EnumSource(value = InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition.class, names = {"BODY", "ALL"})
    void malformedJsonMetadataIsAClientError(
            InterceptorsAutoConfigurationProperties.SignProperties.ParameterPosition position) throws Exception {
        SETTINGS.getSign().getSign().setParameterPosition(position);
        assertThat(post("/echo", "application/json", "{invalid", null).statusCode()).isEqualTo(400);
        assertThat(post("/echo", "application/json", "{\"amount\":1}", null).statusCode()).isEqualTo(400);
    }

    private static String sign(String input) throws Exception {
        var signature = Signature.getInstance("SHA256WithRSA");
        signature.initSign(keys.getPrivate());
        signature.update(input.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private static HttpResponse<String> post(String path, String type, String body, String signature) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", type).POST(HttpRequest.BodyPublishers.ofString(body));
        if (signature != null) request.header("sign", signature);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
            "com.bingchunmoli.autoconfigure.redis.config.RedisUtilAutoConfiguration",
            "com.bingchunmoli.autoconfigure.redis.config.RedisSerializerAutoConfiguration"})
    @Import(Endpoint.class)
    static class Host {
        @Bean CacheFilter cacheFilter() { return new CacheFilter(); }
        @Bean WebMvcConfigurer signatureMvc() {
            return new WebMvcConfigurer() {
                @Override public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(new SignInterceptor(SETTINGS, verifier)).excludePathPatterns("/error");
                }
            };
        }
    }

    @RestController
    static class Endpoint {
        @GetMapping("/query-t") String queryT(@RequestParam(name = "t", defaultValue = "0") String t) { return t; }
        @PostMapping("/echo") String echo(@RequestBody String body) { return body; }
        @PostMapping("/form") String form(@RequestParam(name = "amount") String amount) { return amount; }
    }
}
