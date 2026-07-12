package com.bingchunmoli.security.autoconfigure;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.security.jwt.InMemoryJwtSessionStore;
import com.bingchunmoli.security.jwt.JwtSessionStore;
import com.bingchunmoli.security.jwt.JwtTokenProperties;
import com.bingchunmoli.security.jwt.RedisJwtSessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class JwtSessionStoreFactoryTest {

    @Test
    void shouldUseInMemoryStoreByDefault() {
        JwtSessionStore store = new JwtSessionStoreFactory(new DefaultListableBeanFactory())
                .create(new JwtTokenProperties());

        assertThat(store).isInstanceOf(InMemoryJwtSessionStore.class);
    }

    @Test
    void shouldCreateStoreByConfiguredClassName() {
        JwtTokenProperties properties = new JwtTokenProperties();
        properties.setSessionStoreClassName(CustomStore.class.getName());

        JwtSessionStore store = new JwtSessionStoreFactory(new DefaultListableBeanFactory()).create(properties);

        assertThat(store).isInstanceOf(CustomStore.class);
    }

    @Test
    void shouldFallbackToRedisTemplateWhenRedisUtilIsMissing() {
        JwtTokenProperties properties = new JwtTokenProperties();
        properties.setSessionStoreClassName(RedisJwtSessionStore.class.getName());
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("redisTemplate", mock(RedisTemplate.class));

        JwtSessionStore store = new JwtSessionStoreFactory(beanFactory).create(properties);

        assertThat(store).isInstanceOf(RedisJwtSessionStore.class);
    }

    @Test
    void shouldPreferRedisUtilWhenBothRedisUtilAndRedisTemplateExist() {
        JwtTokenProperties properties = new JwtTokenProperties();
        properties.setSessionStoreClassName(CapturingRedisAccessStore.class.getName());
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RedisTemplate redisTemplate = mock(RedisTemplate.class);
        RedisUtil redisUtil = new RedisUtil(redisTemplate);
        beanFactory.registerSingleton("redisTemplate", redisTemplate);
        beanFactory.registerSingleton("redisUtil", redisUtil);

        new JwtSessionStoreFactory(beanFactory).create(properties);

        assertThat(CapturingRedisAccessStore.redisAccess).isSameAs(redisUtil);
    }

    @Test
    void shouldThrowWhenRedisStoreConfiguredWithoutRedisBeans() {
        JwtTokenProperties properties = new JwtTokenProperties();
        properties.setSessionStoreClassName(RedisJwtSessionStore.class.getName());

        assertThatThrownBy(() -> new JwtSessionStoreFactory(new DefaultListableBeanFactory()).create(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RedisUtil or RedisTemplate");
    }

    public static class CustomStore extends InMemoryJwtSessionStore {
    }

    public static class CapturingRedisAccessStore extends InMemoryJwtSessionStore {

        static Object redisAccess;

        public CapturingRedisAccessStore(Object redisAccess, JwtTokenProperties properties) {
            CapturingRedisAccessStore.redisAccess = redisAccess;
        }
    }
}
