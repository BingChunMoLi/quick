package com.bingchunmoli.oauth.server.resource.autoconfigure;
import org.springframework.boot.autoconfigure.*;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
public final class QuickResourceServerImportFilter implements AutoConfigurationImportFilter, EnvironmentAware {
    private Environment environment;
    public void setEnvironment(Environment value) { environment=value; }
    public boolean[] match(String[] names, AutoConfigurationMetadata metadata) {
        boolean enabled=environment!=null && environment.getProperty("moli.oauth.resource-server.enabled",Boolean.class,false);
        boolean[] result=new boolean[names.length];
        var replaced=java.util.Set.of(
            "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration",
            "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration",
            "org.springframework.boot.security.oauth2.server.resource.autoconfigure.reactive.ReactiveOAuth2ResourceServerAutoConfiguration",
            "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.reactive.ReactiveOAuth2ResourceServerWebSecurityAutoConfiguration");
        for(int i=0;i<names.length;i++) result[i]=!enabled || names[i]==null || !replaced.contains(names[i]);
        return result;
    }
}
