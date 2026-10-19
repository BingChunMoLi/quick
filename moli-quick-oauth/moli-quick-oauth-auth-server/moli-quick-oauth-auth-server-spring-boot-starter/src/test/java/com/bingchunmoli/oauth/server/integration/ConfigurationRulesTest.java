package com.bingchunmoli.oauth.server.integration;
import com.bingchunmoli.oauth.server.authorization.web.*;
import com.bingchunmoli.oauth.server.resource.web.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.introspection.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.web.SecurityFilterChain;
import static org.assertj.core.api.Assertions.*;
import static com.bingchunmoli.oauth.server.integration.ServerFixture.*;
class ConfigurationRulesTest {
    Map<String,String> base(){return new LinkedHashMap<>(Map.of("server.port","0","moli.security.enabled","false"));}
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
    static class PlainHost { }
    @Test void disabledStartersDoNotRegisterQuickServerSupport() {
        try(var context=boot(PlainHost.class,base())) {
            assertThat(context.getBeansOfType(QuickAuthorizationServerWebSupport.class)).isEmpty();
            assertThat(context.getBeansOfType(QuickResourceServerWebSupport.class)).isEmpty();
        }
    }
    @Test void jwtRequiresExplicitIssuerAndAudience() {
        var p=base();p.put("moli.oauth.resource-server.enabled","true");p.put("spring.security.oauth2.resourceserver.jwt.issuer-uri","https://issuer.test");
        assertThatThrownBy(()->boot(PlainHost.class,p)).hasRootCauseMessage("JWT resources require issuer-uri and audiences");
    }
    @Test void hostJwtDecoderOverridesDefaultDecoderAndNativeConfigurationRequirements() throws Exception {
        var p=base();p.put("moli.oauth.resource-server.enabled","true");
        try(var context=boot(CustomJwtHost.class,p)) {
            assertThat(context.getBean("quickResourceJwtDecoder")).isSameAs(context.getBean("hostJwtDecoder"));
            String url="http://127.0.0.1:"+((org.springframework.boot.web.server.context.WebServerApplicationContext)context).getWebServer().getPort();
            var response=browser().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url+"/api/read")).header("Authorization","Bearer provided").GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).contains("host-user");
        }
    }
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration @Import(ResourceHost.class)
    static class CustomJwtHost {
        @Bean JwtDecoder hostJwtDecoder(){return token->Jwt.withTokenValue(token).header("alg","test").subject("host-user")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).claim("scope","api.read").build();}
    }
    @Test void hostOpaqueIntrospectorOverridesRemoteConfigurationRequirements() throws Exception {
        var p=base();p.put("moli.oauth.resource-server.enabled","true");p.put("moli.oauth.resource-server.mode","opaque");
        try(var context=boot(CustomOpaqueHost.class,p)) {
            assertThat(context.getBeansOfType(OpaqueTokenIntrospector.class)).hasSize(1);
            assertThat(context.getBean(QuickResourceServerWebSupport.class).isApplied()).isTrue();
        }
    }
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
    static class CustomOpaqueHost {
        @Bean OpaqueTokenIntrospector hostIntrospector(){return token->new DefaultOAuth2AuthenticatedPrincipal("host",Map.of("sub","host","scope",List.of("api.read")),List.of());}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"jwt","opaque"})
    void configuredPrincipalRequiresNonBlankScalarIdentity(String mode) throws Exception {
        var properties=base();properties.put("moli.oauth.resource-server.enabled","true");
        properties.put("moli.oauth.resource-server.mode",mode);properties.put("moli.oauth.resource-server.principal-claim","account");
        try(var context=boot(PrincipalClaimsHost.class,properties)) {
            String url="http://127.0.0.1:"+((org.springframework.boot.web.server.context.WebServerApplicationContext)context).getWebServer().getPort();
            for(String token:List.of("map","list","boolean","missing","blank","string","number")) {
                var response=browser().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url+"/api/read"))
                        .header("Authorization","Bearer "+token).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                boolean valid=Set.of("string","number").contains(token);
                assertThat(response.statusCode()).as(mode+" principal: "+token).isEqualTo(valid?200:401);
                if(valid) assertThat(response.body()).contains(token.equals("number")?"42":"account-user");
                else {
                    assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value->assertThat(value).contains("invalid_token"));
                    assertThat(response.body()+response.headers().map()).doesNotContain("private-composite-value");
                }
            }
        }
    }
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration @Import(ResourceHost.class)
    static class PrincipalClaimsHost {
        static Map<String,Object> claims(String token) {
            var claims=new LinkedHashMap<String,Object>(Map.of("sub","issuer-user","scope","api.read"));
            Object identity=switch(token) {
                case "map" -> Map.of("name","private-composite-value");
                case "list" -> List.of("private-composite-value");
                case "boolean" -> true;
                case "blank" -> "  ";
                case "string" -> "account-user";
                case "number" -> 42;
                default -> null;
            };
            if(identity!=null)claims.put("account",identity);return claims;
        }
        @Bean JwtDecoder hostJwtDecoder(){return token->Jwt.withTokenValue(token).header("alg","test")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).claims(map->map.putAll(claims(token))).build();}
        @Bean OpaqueTokenIntrospector hostIntrospector(){return token->new DefaultOAuth2AuthenticatedPrincipal("issuer-user",claims(token),List.of());}
    }
    @Test void customSecurityChainMustApplyResourceConfigurer() {
        var p=base();p.put("moli.oauth.resource-server.enabled","true");p.put("moli.oauth.resource-server.default-filter-chain-enabled","false");
        assertThatThrownBy(()->boot(UnconfiguredResourceHost.class,p)).hasMessage("Apply QuickResourceServerWebSupport.configurer() to the host API chain");
    }
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration @Import(CustomJwtHost.class)
    static class UnconfiguredResourceHost {
        @Bean SecurityFilterChain hostChain(HttpSecurity http) {http.authorizeHttpRequests(r->r.anyRequest().permitAll());return http.build();}
    }
    @Test void explicitNonServletEnableFailsClearly() {
        assertThatThrownBy(()->new SpringApplicationBuilder(PlainHost.class).web(WebApplicationType.NONE)
                .run("--moli.security.enabled=false","--moli.oauth.resource-server.enabled=true","--spring.main.banner-mode=off","--logging.level.root=OFF"))
                .hasMessageContaining("requires a Servlet web application");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void remoteIntrospectionFailureAndTimeoutAreSanitized(boolean timeout) throws Exception {
        var remote=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        remote.createContext("/introspect",exchange->{
            try {
                if(timeout)Thread.sleep(250);
                byte[] body="hidden-provider-token-and-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500,body.length);exchange.getResponseBody().write(body);
            } catch(Exception ignored) { } finally {exchange.close();}
        });remote.start();
        try {
            var p=base();p.put("moli.oauth.resource-server.enabled","true");p.put("moli.oauth.resource-server.mode","opaque");
            p.put("moli.oauth.resource-server.read-timeout","50ms");
            p.put("spring.security.oauth2.resourceserver.opaquetoken.introspection-uri","http://127.0.0.1:"+remote.getAddress().getPort()+"/introspect");
            p.put("spring.security.oauth2.resourceserver.opaquetoken.client-id","introspector");
            p.put("spring.security.oauth2.resourceserver.opaquetoken.client-secret","local-only-secret");
            try(var context=boot(ResourceHost.class,p)) {
                String url="http://127.0.0.1:"+((org.springframework.boot.web.server.context.WebServerApplicationContext)context).getWebServer().getPort();
                long before=System.nanoTime();
                var response=browser().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url+"/api/read"))
                        .header("Authorization","Bearer hidden-provider-token-and-secret").GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(response.body()+response.headers().map()).doesNotContain("hidden-provider-token-and-secret","local-only-secret");
                assertThat(java.time.Duration.ofNanos(System.nanoTime()-before)).isLessThan(java.time.Duration.ofSeconds(3));
            }
        } finally {remote.stop(0);}
    }
    @Test void authorizationAndResourceShareApplicationWithoutDecoderOrChainConflicts() throws Exception {
        try(var source=new ServerFixture("memory",null,null,null,0)) {
            var p=new LinkedHashMap<>(source.authProperties);
            int port=port();String url="http://127.0.0.1:"+port;
            p.put("server.port",Integer.toString(port));p.put("spring.security.oauth2.authorizationserver.issuer",url);
            p.put("moli.oauth.resource-server.enabled","true");p.put("spring.security.oauth2.resourceserver.jwt.issuer-uri",url);
            p.put("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",url+"/oauth2/jwks");p.put("spring.security.oauth2.resourceserver.jwt.audiences","quick-api");
            try(var combined=boot(CombinedHost.class,p)) {
                assertThat(combined.getBeansOfType(SecurityFilterChain.class)).hasSize(3);
                assertThat(combined.getBeansOfType(com.bingchunmoli.security.context.SecurityUserContext.class)).hasSize(1);
                var token=post(browser(),url+"/oauth2/token",Map.of("grant_type","client_credentials","scope","api.read"),basic("machine"));
                assertThat(token.statusCode()).as(token.body()).isEqualTo(200);
                String access=source.json.readTree(token.body()).path("access_token").asText();
                assertThat(source.api(url,"/api/read",access).statusCode()).isEqualTo(200);
                var post=browser().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url+"/api/read")).header("Authorization","Bearer "+access)
                        .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                assertThat(post.statusCode()).as("Host CSRF stays enabled with configurer").isEqualTo(403);
            }
        }
    }
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration @Import({AuthHost.class,ResourceHost.class})
    static class CombinedHost {
        @Bean @org.springframework.core.annotation.Order(100)
        SecurityFilterChain api(HttpSecurity http,QuickResourceServerWebSupport support) {
            http.securityMatcher("/api/**").authorizeHttpRequests(r->r.anyRequest().authenticated());
            http.with(support.configurer(),Customizer.withDefaults());return http.build();
        }
        @Bean @org.springframework.core.annotation.Order(200)
        SecurityFilterChain business(HttpSecurity http) {
            http.authorizeHttpRequests(r->r.requestMatchers("/login","/error").permitAll().anyRequest().authenticated()).formLogin(Customizer.withDefaults());
            return http.build();
        }
    }
}
