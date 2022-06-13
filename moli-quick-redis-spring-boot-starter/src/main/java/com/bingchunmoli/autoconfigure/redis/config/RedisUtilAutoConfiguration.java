package com.bingchunmoli.autoconfigure.redis.config;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * RedisUtil的自动配置类
 *
 * @author MoLi
 */
@Configuration
@ConditionalOnMissingBean(value = {RedisUtilAutoConfiguration.class, RedisUtil.class})
@ConditionalOnClass(RedisSerializerAutoConfiguration.class)
@ConditionalOnBean(RedisSerializerAutoConfiguration.class)
public class RedisUtilAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(RedisUtil.class)
    public RedisUtil redisUtil(RedisTemplate<String, Object> redisTemplate) {
        return new RedisUtil(redisTemplate);
    }
}
