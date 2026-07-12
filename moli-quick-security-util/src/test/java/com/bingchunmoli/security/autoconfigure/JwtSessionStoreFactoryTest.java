package com.bingchunmoli.security.autoconfigure;

import com.bingchunmoli.autoconfigure.redis.util.RedisUtil;
import com.bingchunmoli.security.jwt.InMemoryJwtSessionStore;
import com.bingchunmoli.security.jwt.JwtSessionStore;
import com.bingchunmoli.security.jwt.JwtTokenProperties;
import com.bingchunmoli.security.jwt.RedisJwtSessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldPreferRedisUtilWhenBothRedisUtilAndRedisTemplateExist() {
        JwtTokenProperties properties = new JwtTokenProperties();
        properties.setSessionStoreClassName(RedisJwtSessionStore.class.getName());
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RedisTemplate redisUtilTemplate = mock(RedisTemplate.class);
        RedisTemplate fallbackTemplate = mock(RedisTemplate.class);
        ValueOperations valueOperations = mock(ValueOperations.class);
        when(redisUtilTemplate.opsForValue()).thenReturn(valueOperations);
        RedisUtil redisUtil = new RedisUtil(redisUtilTemplate);
        beanFactory.registerSingleton("redisTemplate", fallbackTemplate);
        beanFactory.registerSingleton("redisUtil", redisUtil);

        JwtSessionStore store = new JwtSessionStoreFactory(beanFactory).create(properties);

        store.isTokenBlacklisted("token-1");

        verify(redisUtilTemplate).opsForValue();
        verify(fallbackTemplate, never()).opsForValue();
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
}
