package example.client;
import com.bingchunmoli.oauth.client.*;
import com.bingchunmoli.oauth.client.web.*;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
@SpringBootApplication
public class ClientApplication {
    public static void main(String[] args){SpringApplication.run(ClientApplication.class,args);}
    @Bean OAuth2ProviderAdapter localProvider(){return new OAuth2ProviderAdapter(){
        public String id(){return "quick-server";}
        public void customizeAuthorization(OAuth2AuthorizationRequest.Builder request){OAuth2AuthorizationRequestCustomizers.withPkce().accept(request);}
    };}
    @Bean SecurityFilterChain hostChain(HttpSecurity http,QuickOAuth2WebSupport quick){
        http.authorizeHttpRequests(r->r.requestMatchers("/oauth2/**","/login/oauth2/**","/error").permitAll().anyRequest().authenticated());
        http.exceptionHandling(e->e.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/local")));
        http.with(quick.configurer(),Customizer.withDefaults());return http.build();
    }
    @Bean OAuth2LoginHandler localLogin(){return (identity,user,reference,request,response)->{
        // Demo association by stable external identity; replace with a host account lookup.
        var account=UsernamePasswordAuthenticationToken.authenticated("local:"+identity.subject(),null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
        request.getSession();request.changeSessionId();
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(account);SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context,request,response);
        request.getSession().setAttribute("authorization",reference);response.sendRedirect("/me");
    };}
    @RestController
    static class Api {
        final QuickOAuth2Client quick;final String base;
        Api(QuickOAuth2Client quick,Environment environment){this.quick=quick;this.base=environment.getRequiredProperty("example.resource-base");}
        @GetMapping("/me") public Map<String,String> me(Authentication authentication){return Map.of("account",authentication.getName());}
        @GetMapping("/api") public String api(HttpServletRequest request){
            var ref=(AuthorizedClientRef)request.getSession(false).getAttribute("authorization");
            return quick.restClient(ref,RestClient.builder().baseUrl(base)).get().uri("/api/me").retrieve().body(String.class);
        }
    }
}
