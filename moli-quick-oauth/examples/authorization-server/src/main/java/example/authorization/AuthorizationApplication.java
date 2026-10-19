package example.authorization;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
@SpringBootApplication
public class AuthorizationApplication {
    public static void main(String[] args) {SpringApplication.run(AuthorizationApplication.class,args);}
    @Bean UserDetailsService users(Environment environment) {
        String hash=environment.getRequiredProperty("example.user.password-hash");
        if(hash.isBlank())throw new IllegalStateException("Configure an encoded example user password");
        // Replace with the host's real user repository. Never use this demo account in production.
        return new InMemoryUserDetailsManager(User.withUsername("alice").password(hash).roles("USER").build());
    }
}
