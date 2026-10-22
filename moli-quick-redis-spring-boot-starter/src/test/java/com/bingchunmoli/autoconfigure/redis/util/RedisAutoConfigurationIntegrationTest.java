package com.bingchunmoli.autoconfigure.redis.util;

import com.bingchunmoli.autoconfigure.redis.config.RedisSerializerAutoConfiguration;
import com.bingchunmoli.autoconfigure.redis.config.RedisUtilAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

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

    @Test
    void startsWithRealBootAutoConfigurationAndItsObjectTypedTemplate() {
        new ApplicationContextRunner().withUserConfiguration(BootHost.class)
                .withBean(RedisConnectionFactory.class, TestRedisConnectionFactory::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RedisUtil.class);
                    assertThat(context.getBean(RedisUtil.class).redisTemplate)
                            .isSameAs(context.getBean("redisTemplate"));
                    assertThat(context).hasBean("stringRedisTemplate");
                });
    }

    @Test
    void additionalTemplatesDoNotMakeRedisUtilInjectionAmbiguous() {
        RedisTemplate<Object, Object> extra = new RedisTemplate<>();
        extra.setConnectionFactory(new TestRedisConnectionFactory());
        new ApplicationContextRunner().withUserConfiguration(BootHost.class)
                .withBean(RedisConnectionFactory.class, TestRedisConnectionFactory::new)
                .withBean("otherTemplate", RedisTemplate.class, () -> extra)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RedisUtil.class).redisTemplate)
                            .isSameAs(context.getBean("redisTemplate")).isNotSameAs(extra);
                });
    }

    @Test
    void defaultTemplateUsesTheHostSerializerBean() {
        RedisSerializer<Object> serializer = RedisSerializer.java();
        contextRunner.withBean("redisSerializer", RedisSerializer.class, () -> serializer).run(context -> {
            assertThat(context).hasNotFailed();
            RedisTemplate<?, ?> template = context.getBean("redisTemplate", RedisTemplate.class);
            assertThat(template.getValueSerializer()).isSameAs(serializer);
            assertThat(template.getHashValueSerializer()).isSameAs(serializer);
        });
    }

    @Test
    void missingConnectionFactoryDoesNotCreateAnUnusableTemplateOrUtility() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                RedisSerializerAutoConfiguration.class, RedisUtilAutoConfiguration.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(RedisTemplate.class).doesNotHaveBean(RedisUtil.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class BootHost { }

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
