package com.bingchunmoli.oauth.client.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/** Suppress only Boot's OAuth web chain while Quick explicitly owns OAuth integration. */
public final class QuickOAuth2ImportFilter implements AutoConfigurationImportFilter, EnvironmentAware {
    private Environment environment;
    @Override public void setEnvironment(Environment environment) { this.environment = environment; }
    @Override public boolean[] match(String[] names, AutoConfigurationMetadata metadata) {
        boolean enabled = environment != null && environment.getProperty("moli.oauth.client.enabled", Boolean.class, false);
        boolean[] matches = new boolean[names.length];
        for (int i = 0; i < names.length; i++) {
            matches[i] = !enabled || !"org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration".equals(names[i]);
        }
        return matches;
    }
}
