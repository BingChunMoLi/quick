package com.bingchunmoli.codegen.web;

import com.bingchunmoli.codegen.CodeGenerator;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.*;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnProperty(prefix = "moli.codegen", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CodegenProperties.class)
public class CodegenAutoConfiguration {
    @Bean @ConditionalOnMissingBean public CodeGenerator codeGenerator() { return new CodeGenerator(); }
    @Bean public CodegenController codegenController(CodeGenerator generator, ListableBeanFactory factory) { return new CodegenController(generator, factory); }
    @Bean public CodegenExceptionHandler codegenExceptionHandler() { return new CodegenExceptionHandler(); }
    @Bean public WebMvcConfigurer codegenResources() {
        return new WebMvcConfigurer() {
            @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/moli-codegen/**").addResourceLocations("classpath:/codegen-ui/");
            }
            @Override public void addViewControllers(ViewControllerRegistry registry) {
                registry.addRedirectViewController("/moli-codegen", "/moli-codegen/index.html");
                registry.addRedirectViewController("/moli-codegen/", "/moli-codegen/index.html");
            }
        };
    }
}
