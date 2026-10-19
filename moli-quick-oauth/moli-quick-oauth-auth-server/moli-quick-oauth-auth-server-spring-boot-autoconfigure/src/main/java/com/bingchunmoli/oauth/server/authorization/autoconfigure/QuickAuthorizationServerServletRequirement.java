package com.bingchunmoli.oauth.server.authorization.autoconfigure;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.util.ClassUtils;
@AutoConfiguration
@ConditionalOnProperty(prefix="moli.oauth.auth-server",name="enabled",havingValue="true")
public class QuickAuthorizationServerServletRequirement {
    @Bean SmartInitializingSingleton authorizationServletRequirement(ApplicationContext context) {
        return () -> {
            if(!ClassUtils.isPresent("jakarta.servlet.ServletContext",getClass().getClassLoader()) ||
                    !(context instanceof org.springframework.web.context.WebApplicationContext))
                throw new IllegalStateException("Quick OAuth auth-server requires a Servlet web application");
        };
    }
}
