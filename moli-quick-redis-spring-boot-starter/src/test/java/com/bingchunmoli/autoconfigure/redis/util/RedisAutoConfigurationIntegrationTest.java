package com.bingchunmoli.autoconfigure.redis.util;

import com.bingchunmoli.autoconfigure.redis.config.RedisSerializerAutoConfiguration;
import com.bingchunmoli.autoconfigure.redis.config.RedisUtilAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;

class RedisAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RedisSerializerAutoConfiguration.class,
                    RedisUtilAutoConfiguration.class
            ))
            .withBean(RedisConnectionFactory.class, TestRedisConnectionFactory::new);

    @Test
    void shouldAutoConfigureRedisTemplateAndRedisUtilForBoot406() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RedisTemplate.class);
            assertThat(context).hasSingleBean(RedisUtil.class);

            RedisTemplate<String, Object> redisTemplate = context.getBean(RedisTemplate.class);
            assertThat(redisTemplate.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
            assertThat(redisTemplate.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
            assertThat(redisTemplate.getValueSerializer()).isInstanceOf(Jackson2JsonRedisSerializer.class);
            assertThat(context.getBean(RedisUtil.class).redisTemplate).isSameAs(redisTemplate);
        });
    }

    @Test
    void shouldBackOffWhenConsumerProvidesRedisTemplateAndRedisUtil() {
        RedisTemplate<String, Object> customTemplate = new RedisTemplate<>();
        customTemplate.setConnectionFactory(new TestRedisConnectionFactory());
        RedisUtil customRedisUtil = new RedisUtil(customTemplate);

        contextRunner
                .withBean("redisTemplate", RedisTemplate.class, () -> customTemplate)
                .withBean(RedisUtil.class, () -> customRedisUtil)
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisTemplate.class);
                    assertThat(context).hasSingleBean(RedisUtil.class);
                    assertThat(context.getBean(RedisTemplate.class)).isSameAs(customTemplate);
                    assertThat(context.getBean(RedisUtil.class)).isSameAs(customRedisUtil);
                });
    }

    static class TestRedisConnectionFactory implements RedisConnectionFactory {

        @Override
        public boolean getConvertPipelineAndTxResults() {
            return true;
        }

        @Override
        public RedisConnection getConnection() {
            throw new UnsupportedOperationException("No real Redis connection is needed for auto-configuration tests");
        }

        @Override
        public RedisClusterConnection getClusterConnection() {
            throw new UnsupportedOperationException("No real Redis connection is needed for auto-configuration tests");
        }

        @Override
        public RedisSentinelConnection getSentinelConnection() {
            throw new UnsupportedOperationException("No real Redis connection is needed for auto-configuration tests");
        }

        @Override
        public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
            return null;
        }
    }
}
