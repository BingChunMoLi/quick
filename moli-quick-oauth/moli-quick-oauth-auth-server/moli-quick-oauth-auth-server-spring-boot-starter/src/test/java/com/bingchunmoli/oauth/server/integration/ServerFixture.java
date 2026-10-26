package com.bingchunmoli.oauth.server.integration;
import com.bingchunmoli.oauth.client.*;
import com.bingchunmoli.oauth.client.web.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.*;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.http.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

final class ServerFixture implements AutoCloseable {
    static final RSAKey KEY=key();
    static RSAKey key() {try{return new RSAKeyGenerator(2048).keyID("server-test-key").generate();}catch(Exception e){throw new IllegalStateException(e);}}
    static final String CLIENT_SUFFIX=java.util.UUID.randomUUID().toString();
    static String clientId(String logical) {return logical+"-"+CLIENT_SUFFIX;}
    static final String SECRET="local-protocol-test",USER_PASSWORD="local-user-test";
    final JsonMapper json=JsonMapper.builder().build();
    final String authBase,resourceBase,opaqueBase,clientBase,prefix;
    final Map<String,String> authProperties=new LinkedHashMap<>();
    ConfigurableApplicationContext auth,resource,opaque,client;
    static int port() {try(var socket=new java.net.ServerSocket(0,0,InetAddress.getByName("127.0.0.1"))){return socket.getLocalPort();}catch(Exception e){throw new IllegalStateException(e);}}
    ServerFixture(String store,String jdbcUrl,String jdbcUser,String jdbcPassword,int redisPort) {
        this(store,jdbcUrl,jdbcUser,jdbcPassword,redisPort,Map.of());
    }
    ServerFixture(String store,String jdbcUrl,String jdbcUser,String jdbcPassword,int redisPort,Map<String,String> overrides) {
        authBase="http://127.0.0.1:"+port()+overrides.getOrDefault("server.servlet.context-path","");resourceBase="http://127.0.0.1:"+port();
        opaqueBase="http://127.0.0.1:"+port();clientBase="http://127.0.0.1:"+port();prefix="test:quick:authorization:"+UUID.randomUUID();
        authProperties.put("server.port",URI.create(authBase).getPort()+"");
        authProperties.put("server.servlet.session.cookie.name","AUTH_SESSION");
        authProperties.put("moli.oauth.auth-server.enabled","true");
        authProperties.put("moli.oauth.auth-server.login.enabled","true");
        authProperties.put("moli.oauth.auth-server.storage.type",store);
        authProperties.put("moli.oauth.auth-server.storage.key-prefix",prefix);
        authProperties.put("spring.security.oauth2.authorizationserver.issuer",authBase);
        authProperties.put("test.jdbc.url",jdbcUrl==null?"":jdbcUrl);authProperties.put("test.jdbc.user",jdbcUser==null?"":jdbcUser);
        authProperties.put("test.jdbc.password",jdbcPassword==null?"":jdbcPassword);authProperties.put("test.redis.port",redisPort+"");
        for(String id:List.of("browser","machine","reference")) {
            String p="spring.security.oauth2.authorizationserver.client."+id+".";
            authProperties.put(p+"registration.client-id",clientId(id));
            authProperties.put(p+"registration.client-secret",PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(SECRET));
            authProperties.put(p+"registration.client-authentication-methods","client_secret_basic");
            authProperties.put(p+"registration.authorization-grant-types",id.equals("browser")?"authorization_code,refresh_token":"client_credentials");
            authProperties.put(p+"registration.scopes",id.equals("browser")?"openid,profile,api.read":"api.read");
            authProperties.put(p+"registration.redirect-uris",clientBase+"/login/oauth2/code/local");
            authProperties.put(p+"require-authorization-consent","true");
            authProperties.put("moli.oauth.auth-server.audiences."+clientId(id),"quick-api");
        }
        authProperties.put("spring.security.oauth2.authorizationserver.client.reference.token.access-token-format","reference");
        authProperties.putAll(overrides);
        try {
            restartAuthorization();
            resource=boot(ResourceHost.class,Map.of("server.port",URI.create(resourceBase).getPort()+"","moli.oauth.resource-server.enabled","true",
                    "moli.oauth.resource-server.roles-claim","roles","spring.security.oauth2.resourceserver.jwt.issuer-uri",authBase,
                    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",authBase+"/oauth2/jwks","spring.security.oauth2.resourceserver.jwt.audiences","quick-api"));
            opaque=boot(ResourceHost.class,Map.of("server.port",URI.create(opaqueBase).getPort()+"","moli.oauth.resource-server.enabled","true",
                    "moli.oauth.resource-server.mode","opaque","spring.security.oauth2.resourceserver.opaquetoken.introspection-uri",authBase+"/oauth2/introspect",
                    "spring.security.oauth2.resourceserver.opaquetoken.client-id",clientId("machine"),"spring.security.oauth2.resourceserver.opaquetoken.client-secret",SECRET));
            client=boot(ClientHost.class,Map.ofEntries(Map.entry("server.port",URI.create(clientBase).getPort()+""),
                    Map.entry("moli.oauth.client.enabled","true"),Map.entry("moli.oauth.client.login.enabled","true"),Map.entry("server.servlet.session.cookie.name","CLIENT_SESSION"),
                    Map.entry("spring.security.oauth2.client.registration.local.provider","local-server"),
                    Map.entry("spring.security.oauth2.client.registration.local.client-id",clientId("browser")),
                    Map.entry("spring.security.oauth2.client.registration.local.client-secret",SECRET),
                    Map.entry("spring.security.oauth2.client.registration.local.authorization-grant-type","authorization_code"),
                    Map.entry("spring.security.oauth2.client.registration.local.scope","openid,profile,api.read"),
                    Map.entry("spring.security.oauth2.client.provider.local-server.issuer-uri",authBase),Map.entry("test.resource",
                            "reference".equals(authProperties.get("spring.security.oauth2.authorizationserver.client.browser.token.access-token-format"))?opaqueBase:resourceBase)));
        } catch(Throwable failure) {close();throw failure;}
    }
    void restartAuthorization() {if(auth!=null)auth.close();auth=boot(AuthHost.class,authProperties);}
    static ConfigurableApplicationContext boot(Class<?> host,Map<String,String> properties) {
        List<String> args=new ArrayList<>(List.of("--server.address=127.0.0.1","--spring.main.banner-mode=off","--logging.level.root=ERROR","--moli.security.jwt.enabled=false"));
        properties.forEach((key,value)->args.add("--"+key+"="+value));
        return new SpringApplicationBuilder(host).run(args.toArray(String[]::new));
    }
    static HttpClient browser() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
    }
    static String form(Map<String,String> values) {return values.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));}
    static String encode(String value) {return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    static HttpResponse<String> get(HttpClient browser,String url) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(url)).header("Accept","text/html").timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    static HttpResponse<String> post(HttpClient browser,String url,Map<String,String> values,String authorization) throws Exception {
        return postBody(browser,url,form(values),authorization);
    }
    static HttpResponse<String> postBody(HttpClient browser,String url,String body,String authorization) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(url)).header("Content-Type","application/x-www-form-urlencoded").timeout(Duration.ofSeconds(15));
        if(authorization!=null)builder.header("Authorization",authorization);
        return browser.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> consent(HttpClient browser,String state,String scopes) throws Exception {
        String body=form(Map.of("client_id",clientId("browser"),"state",state));
        for(String scope:scopes.split(" ")) if(!scope.equals("openid") && !scope.isBlank()) body+="&scope="+encode(scope);
        return postBody(browser,authBase+"/oauth2/authorize",body,null);
    }
    static String basic(String id) {return "Basic "+Base64.getEncoder().encodeToString((clientId(id)+":"+SECRET).getBytes(StandardCharsets.UTF_8));}
    String location(HttpResponse<String> response) {return URI.create(authBase).resolve(response.headers().firstValue("Location").orElseThrow(()->new AssertionError(response.statusCode()+": "+response.body()))).toString();}
    HttpClient login() throws Exception {
        var browser=browser();
        var page=get(browser,authBase+"/login");assertThat(page.statusCode()).as(page.body()).isEqualTo(200);
        Matcher token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());
        assertThat(token.find()).as("Login CSRF input").isTrue();
        var response=post(browser,authBase+"/login",Map.of("username","alice","password",USER_PASSWORD,"_csrf",token.group(1)),null);
        assertThat(response.statusCode()).isEqualTo(302);return browser;
    }
    String authorize(HttpClient browser,String requestedScopes,String verifier) throws Exception {
        String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String url=authBase+"/oauth2/authorize?"+form(Map.of("response_type","code","client_id",clientId("browser"),"redirect_uri",clientBase+"/login/oauth2/code/local",
                "scope",requestedScopes,"state",UUID.randomUUID().toString(),"code_challenge",challenge,"code_challenge_method","S256","nonce","oidc-nonce"));
        var response=get(browser,url);
        if(response.statusCode()==200) {
            Matcher state=Pattern.compile("name=\"state\"[^>]*value=\"([^\"]+)\"").matcher(response.body());
            assertThat(state.find()).as(response.body()).isTrue();
            response=consent(browser,state.group(1),requestedScopes);
        }
        assertThat(response.statusCode()).as(response.body()).isEqualTo(302);
        Map<String,String> query=MockQuery.parse(URI.create(location(response)).getRawQuery());
        assertThat(query).as(location(response)).containsKey("code");return query.get("code");
    }
    JsonNode exchange(String code,String verifier) throws Exception {
        var response=post(browser(),authBase+"/oauth2/token",Map.of("grant_type","authorization_code","code",code,
                "redirect_uri",clientBase+"/login/oauth2/code/local","code_verifier",verifier),basic("browser"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);return json.readTree(response.body());
    }
    JsonNode machine(String id) throws Exception {
        var response=post(browser(),authBase+"/oauth2/token",Map.of("grant_type","client_credentials","scope","api.read"),basic(id));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);return json.readTree(response.body());
    }
    HttpResponse<String> api(String base,String path,String token) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15)).GET();
        if(token!=null)builder.header("Authorization","Bearer "+token);
        return browser().send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    @Override public void close() {
        if(client!=null)client.close();if(opaque!=null)opaque.close();if(resource!=null)resource.close();if(auth!=null)auth.close();
    }
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
    static class AuthHost {
        @Bean @ConditionalOnProperty(name="test.consent-timeout",havingValue="true")
        com.bingchunmoli.oauth.server.authorization.GrantCoordinator unavailableConsentCoordinator() {
            return new com.bingchunmoli.oauth.server.authorization.GrantCoordinator() {
                public <T> T execute(String id,java.util.function.Supplier<T> work) {
                    throw com.bingchunmoli.oauth.server.authorization.LocalGrantCoordinator.unavailable();
                }
            };
        }
        @Bean @ConditionalOnProperty(name="test.consent-race",havingValue="true")
        ConsentBarrierService consentRaceAuthorizations() {return new ConsentBarrierService();}
        @Bean @ConditionalOnProperty(name="test.custom-codec",havingValue="true")
        com.bingchunmoli.oauth.server.authorization.AuthorizationAttributeCodec customAttributes() {
            return new TenantAttributeCodec();
        }
        @Bean JWKSource<SecurityContext> testSigningKeys() {return new ImmutableJWKSet<>(new JWKSet(KEY));}
        @Bean UserDetailsService users() {return new InMemoryUserDetailsManager(User.withUsername("alice")
                .password(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(USER_PASSWORD)).roles("READER").build());}
        @Bean OAuth2TokenCustomizer<JwtEncodingContext> testRoleClaim() {return context->{if(context.getTokenType().getValue().equals("access_token"))context.getClaims().claim("roles",List.of("READER"));};}
        @Bean @ConditionalOnProperty(prefix="moli.oauth.auth-server.storage",name="type",havingValue="jdbc")
        javax.sql.DataSource source(Environment environment) {return new DriverManagerDataSource(environment.getRequiredProperty("test.jdbc.url"),
                environment.getRequiredProperty("test.jdbc.user"),environment.getRequiredProperty("test.jdbc.password"));}
        @Bean(destroyMethod="destroy") @ConditionalOnProperty(prefix="moli.oauth.auth-server.storage",name="type",havingValue="redis")
        LettuceConnectionFactory redisFactory(Environment environment) {return new LettuceConnectionFactory("127.0.0.1",environment.getRequiredProperty("test.redis.port",Integer.class));}
        @Bean @ConditionalOnProperty(prefix="moli.oauth.auth-server.storage",name="type",havingValue="redis")
        StringRedisTemplate redis(LettuceConnectionFactory factory) {return new StringRedisTemplate(factory);}
    }
    static final class ConsentBarrierService implements org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService,AutoCloseable {
        private final com.bingchunmoli.oauth.server.authorization.ExpiringMemoryAuthorizationService delegate=
                new com.bingchunmoli.oauth.server.authorization.ExpiringMemoryAuthorizationService();
        private final java.util.concurrent.CountDownLatch arrived=new java.util.concurrent.CountDownLatch(2);
        private final java.util.concurrent.atomic.AtomicInteger reads=new java.util.concurrent.atomic.AtomicInteger();
        private volatile String state;
        void arm(String value){state=value;}
        public void save(org.springframework.security.oauth2.server.authorization.OAuth2Authorization authorization){delegate.save(authorization);}
        public void remove(org.springframework.security.oauth2.server.authorization.OAuth2Authorization authorization){delegate.remove(authorization);}
        public org.springframework.security.oauth2.server.authorization.OAuth2Authorization findById(String id){return delegate.findById(id);}
        public org.springframework.security.oauth2.server.authorization.OAuth2Authorization findByToken(String token,org.springframework.security.oauth2.server.authorization.OAuth2TokenType type){
            var authorization=delegate.findByToken(token,type);
            if(token.equals(state) && type!=null && "state".equals(type.getValue()) && reads.incrementAndGet()<=2) {
                arrived.countDown();
                try {if(!arrived.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Consent race setup timed out");}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
            }
            return authorization;
        }
        public void close(){delegate.close();}
    }
    record TenantIdentity(String tenant,String subject) { }
    static final class TenantAttributeCodec implements com.bingchunmoli.oauth.server.authorization.AuthorizationAttributeCodec {
        private final com.bingchunmoli.oauth.server.authorization.AuthorizationAttributeCodec delegate=
                new com.bingchunmoli.oauth.server.authorization.SecurityAuthorizationAttributeCodec();
        public String encode(Map<String,Object> attributes) {
            var portable=new LinkedHashMap<>(attributes);
            if(portable.get("tenantIdentity") instanceof TenantIdentity identity)
                portable.put("tenantIdentity",new LinkedHashMap<>(Map.of("tenant",identity.tenant(),"subject",identity.subject())));
            if(portable.get(java.security.Principal.class.getName()) instanceof Authentication authentication &&
                    authentication.getPrincipal() instanceof TenantIdentity identity)
                portable.put(java.security.Principal.class.getName(),org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                        .authenticated(identity.subject(),null,authentication.getAuthorities()));
            return "tenant-v1:"+Base64.getEncoder().encodeToString(delegate.encode(portable).getBytes(StandardCharsets.UTF_8));
        }
        public Map<String,Object> decode(String value) {
            String payload=value.startsWith("tenant-v1:")?new String(Base64.getDecoder().decode(value.substring(10)),StandardCharsets.UTF_8):value;
            var attributes=new LinkedHashMap<>(delegate.decode(payload));
            if(attributes.get("tenantIdentity") instanceof Map<?,?> identity)
                attributes.put("tenantIdentity",new TenantIdentity((String)identity.get("tenant"),(String)identity.get("subject")));
            if(attributes.get("tenantIdentity") instanceof TenantIdentity identity &&
                    attributes.get(java.security.Principal.class.getName()) instanceof Authentication authentication)
                attributes.put(java.security.Principal.class.getName(),org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                        .authenticated(identity,null,authentication.getAuthorities()));
            return attributes;
        }
    }
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
    static class ResourceHost {
        @Bean ResourceApi resourceApi() {return new ResourceApi();}
    }
    @RestController
    static class ResourceApi {
        @GetMapping("/api/read") @PreAuthorize("hasAuthority('SCOPE_api.read')")
        Map<String,Object> read(Authentication authentication) {return Map.of("principal",authentication.getName(),"authorities",authentication.getAuthorities().stream().map(a->a.getAuthority()).toList());}
        @GetMapping("/api/admin") @PreAuthorize("hasAuthority('SCOPE_api.admin')") String admin() {return "admin";}
    }
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
    static class ClientHost {
        @Bean OAuth2ProviderAdapter localProvider() {
            return new OAuth2ProviderAdapter() {
                public String id() { return "local-server"; }
                public void customizeAuthorization(org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.Builder request) {
                    org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers.withPkce().accept(request);
                }
            };
        }
        @Bean SecurityFilterChain clientChain(HttpSecurity http,QuickOAuth2WebSupport support) {
            http.authorizeHttpRequests(r->r.anyRequest().permitAll());http.with(support.configurer(),Customizer.withDefaults());return http.build();
        }
        @Bean OAuth2LoginHandler handler() {return (identity,user,reference,request,response)->{
            request.getSession().setAttribute("authorization",reference);response.setContentType("application/json");response.getWriter().write("{\"login\":true}");
        };}
        @Bean ClientApi clientApi(QuickOAuth2Client quick,Environment environment) {return new ClientApi(quick,environment.getRequiredProperty("test.resource"));}
    }
    @RestController
    static class ClientApi {
        final QuickOAuth2Client quick;final String resource;
        ClientApi(QuickOAuth2Client quick,String resource){this.quick=quick;this.resource=resource;}
        @GetMapping("/api") String api(HttpServletRequest request) {
            return quick.restClient((AuthorizedClientRef)request.getSession().getAttribute("authorization"),RestClient.builder().baseUrl(resource))
                    .get().uri("/api/read").retrieve().body(String.class);
        }
        @GetMapping("/who") String who(Authentication authentication) {return authentication==null?"anonymous":authentication.getName();}
    }
    static class MockQuery {
        static Map<String,String> parse(String query) {
            Map<String,String> values=new LinkedHashMap<>();if(query==null)return values;
            for(String item:query.split("&")) {String[] pair=item.split("=",2);values.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),pair.length==1?"":URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}
            return values;
        }
    }
}
