package com.bingchunmoli.oauth.server.resource.autoconfigure;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.util.ClassUtils;
@AutoConfiguration
@ConditionalOnProperty(prefix="moli.oauth.resource-server",name="enabled",havingValue="true")
public class QuickResourceServerServletRequirement {
    @Bean SmartInitializingSingleton resourceServletRequirement(ApplicationContext context) {
        return () -> {
            if(!ClassUtils.isPresent("jakarta.servlet.ServletContext",getClass().getClassLoader()) ||
                    !(context instanceof org.springframework.web.context.WebApplicationContext))
                throw new IllegalStateException("Quick OAuth resource-server requires a Servlet web application");
        };
    }
}
