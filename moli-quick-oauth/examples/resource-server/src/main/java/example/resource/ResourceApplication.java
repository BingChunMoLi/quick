package example.resource;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@SpringBootApplication
@RestController
public class ResourceApplication {
    public static void main(String[] args){SpringApplication.run(ResourceApplication.class,args);}
    @GetMapping("/api/me") @PreAuthorize("hasAuthority('SCOPE_api.read')")
    public Map<String,Object> me(Authentication authentication){return Map.of("subject",authentication.getName(),"authorities",authentication.getAuthorities().stream().map(a->a.getAuthority()).toList());}
    @GetMapping("/api/admin") @PreAuthorize("hasAuthority('SCOPE_api.admin')")
    public String admin(){return "admin";}
}
