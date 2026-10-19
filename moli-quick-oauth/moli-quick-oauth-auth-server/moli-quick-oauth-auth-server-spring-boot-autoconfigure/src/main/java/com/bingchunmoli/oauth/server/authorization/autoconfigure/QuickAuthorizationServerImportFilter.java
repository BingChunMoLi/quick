package com.bingchunmoli.oauth.server.authorization.autoconfigure;
import org.springframework.boot.autoconfigure.*;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
public final class QuickAuthorizationServerImportFilter implements AutoConfigurationImportFilter, EnvironmentAware {
    private Environment environment;
    public void setEnvironment(Environment value) { environment = value; }
    public boolean[] match(String[] names, AutoConfigurationMetadata metadata) {
        boolean enabled = environment != null && environment.getProperty("moli.oauth.auth-server.enabled", Boolean.class, false);
        boolean[] result = new boolean[names.length];
        for (int i = 0; i < names.length; i++) result[i] = !enabled || !(names[i] != null &&
            (names[i].equals("org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerAutoConfiguration") ||
             names[i].equals("org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerJwtAutoConfiguration") ||
             names[i].equals("org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")));
        return result;
    }
}
