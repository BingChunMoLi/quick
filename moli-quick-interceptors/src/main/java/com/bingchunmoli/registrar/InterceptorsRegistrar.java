package com.bingchunmoli.registrar;

import com.bingchunmoli.interceptor.SignInterceptor;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor
@ConditionalOnBean({InterceptorsAutoConfigurationProperties.class, SignInterceptor.class})
public class InterceptorsRegistrar implements WebMvcConfigurer {

    private final InterceptorsAutoConfigurationProperties interceptorsAutoConfigurationProperties;
    private final SignInterceptor signInterceptor;

    @Override
    public void addInterceptors(@Nonnull InterceptorRegistry registry) {
        if (interceptorsAutoConfigurationProperties.getSign().getSign().isEnable()) {
            registry.addInterceptor(signInterceptor)
                    .addPathPatterns("/**")
                    .excludePathPatterns(interceptorsAutoConfigurationProperties.getSign().getIgnorePathList());
        }
    }

}
