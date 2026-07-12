package com.bingchunmoli.security.autoconfigure;

import com.bingchunmoli.security.jwt.InMemoryJwtSessionStore;
import com.bingchunmoli.security.jwt.JwtSessionStore;
import com.bingchunmoli.security.jwt.JwtTokenProperties;
import com.bingchunmoli.security.jwt.RedisJwtSessionStore;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Creates JWT session store implementations by configured class name.
 *
 * @author MoLi
 */
public class JwtSessionStoreFactory {

    private static final String REDIS_UTIL_CLASS_NAME = "com.bingchunmoli.autoconfigure.redis.util.RedisUtil";

    private static final String REDIS_TEMPLATE_CLASS_NAME = "org.springframework.data.redis.core.RedisTemplate";

    private final ListableBeanFactory beanFactory;

    public JwtSessionStoreFactory(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    public JwtSessionStore create(JwtTokenProperties properties) {
        String className = properties.getSessionStoreClassName();
        if (className == null || className.isBlank()) {
            return new InMemoryJwtSessionStore();
        }
        try {
            Class<?> storeClass = ClassUtils.forName(className, getClass().getClassLoader());
            if (!JwtSessionStore.class.isAssignableFrom(storeClass)) {
                throw new IllegalArgumentException(className + " must implement " + JwtSessionStore.class.getName());
            }
            if (RedisJwtSessionStore.class.getName().equals(className)) {
                return new RedisJwtSessionStore(resolveRequiredRedisTemplate(), properties);
            }
            return instantiate(storeClass.asSubclass(JwtSessionStore.class), properties);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to create JwtSessionStore: " + className, ex);
        }
    }

    private JwtSessionStore instantiate(Class<? extends JwtSessionStore> storeClass, JwtTokenProperties properties)
            throws ReflectiveOperationException {
        Constructor<? extends JwtSessionStore> propertiesConstructor = findConstructor(storeClass, JwtTokenProperties.class);
        if (propertiesConstructor != null) {
            return propertiesConstructor.newInstance(properties);
        }

        Constructor<? extends JwtSessionStore> defaultConstructor = findConstructor(storeClass);
        if (defaultConstructor != null) {
            return defaultConstructor.newInstance();
        }

        throw new IllegalStateException("No supported constructor found for " + storeClass.getName());
    }

    private RedisTemplate<String, Object> resolveRequiredRedisTemplate() throws ReflectiveOperationException {
        RedisTemplate<String, Object> redisTemplate = resolveRedisTemplateFromRedisUtil();
        if (redisTemplate != null) {
            return redisTemplate;
        }
        redisTemplate = resolveRedisTemplateBean();
        if (redisTemplate != null) {
            return redisTemplate;
        }
        throw new IllegalStateException("RedisJwtSessionStore requires RedisUtil or RedisTemplate bean");
    }

    @SuppressWarnings("unchecked")
    private RedisTemplate<String, Object> resolveRedisTemplateFromRedisUtil() throws ReflectiveOperationException {
        if (!ClassUtils.isPresent(REDIS_UTIL_CLASS_NAME, getClass().getClassLoader())) {
            return null;
        }
        Class<?> redisUtilClass = ClassUtils.forName(REDIS_UTIL_CLASS_NAME, getClass().getClassLoader());
        String[] beanNames = beanFactory.getBeanNamesForType(redisUtilClass);
        if (beanNames.length == 0) {
            return null;
        }
        Object redisUtil = beanFactory.getBean(beanNames[0]);
        Field redisTemplateField = redisUtilClass.getField("redisTemplate");
        Object value = redisTemplateField.get(redisUtil);
        if (value instanceof RedisTemplate<?, ?> template) {
            return (RedisTemplate<String, Object>) template;
        }
        throw new IllegalStateException("RedisUtil.redisTemplate must be a RedisTemplate");
    }

    @SuppressWarnings("unchecked")
    private RedisTemplate<String, Object> resolveRedisTemplateBean() throws ClassNotFoundException {
        if (!ClassUtils.isPresent(REDIS_TEMPLATE_CLASS_NAME, getClass().getClassLoader())) {
            return null;
        }
        Class<?> redisTemplateClass = ClassUtils.forName(REDIS_TEMPLATE_CLASS_NAME, getClass().getClassLoader());
        String[] beanNames = beanFactory.getBeanNamesForType(redisTemplateClass);
        return beanNames.length == 0 ? null : (RedisTemplate<String, Object>) beanFactory.getBean(beanNames[0]);
    }

    private Constructor<? extends JwtSessionStore> findConstructor(Class<? extends JwtSessionStore> storeClass,
                                                                  Class<?>... parameterTypes) {
        try {
            return storeClass.getConstructor(parameterTypes);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }
}
