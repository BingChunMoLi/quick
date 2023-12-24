package com.bingchunmoli.autoconfigure;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.filter.CacheFilter;
import com.bingchunmoli.interceptor.SignInterceptor;
import com.bingchunmoli.properties.InterceptorsAutoConfigurationProperties;
import com.bingchunmoli.registrar.InterceptorsRegistrar;
import com.bingchunmoli.util.SHA256WithRSASignUtil;
import com.bingchunmoli.util.SignUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moli.interceptor.sign", value = "enable", havingValue = "true")
@ConditionalOnClass({SignInterceptor.class, InterceptorsRegistrar.class, CacheFilter.class})
@Import({SignInterceptor.class, InterceptorsRegistrar.class, CacheFilter.class})
public class SignAutoConfiguration {

    private final InterceptorsAutoConfigurationProperties interceptorsAutoConfigurationProperties;
    private final ObjectMapper om;
    private final RedisUtil redisUtil;

    @Bean
    public SignUtil signUtil(){
        String algorithm = interceptorsAutoConfigurationProperties.getSign().getAlgorithm();
        return switch (algorithm) {
            case "SHA256WithRSA" -> new SHA256WithRSASignUtil(om, interceptorsAutoConfigurationProperties.getSign(), redisUtil);
            default -> throw new IllegalStateException("Unexpected value: " + algorithm);
        };
    }
}
