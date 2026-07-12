package com.bingchunmoli.security.autoconfigure;

import com.bingchunmoli.security.jwt.InMemoryJwtSessionStore;
import com.bingchunmoli.security.jwt.JwtSessionStore;
import com.bingchunmoli.security.jwt.JwtTokenProperties;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Constructor;

/**
 * Creates JWT session store implementations by configured class name.
 *
 * @author MoLi
 */
public class JwtSessionStoreFactory {

    private static final String REDIS_UTIL_CLASS_NAME = "com.bingchunmoli.autoconfigure.redis.util.RedisUtil";

    private static final String REDIS_TEMPLATE_CLASS_NAME = "org.springframework.data.redis.core.RedisTemplate";

    private static final String REDIS_STORE_CLASS_NAME = "com.bingchunmoli.security.jwt.RedisJwtSessionStore";

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
            return instantiate(storeClass.asSubclass(JwtSessionStore.class), properties);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to create JwtSessionStore: " + className, ex);
        }
    }

    private JwtSessionStore instantiate(Class<? extends JwtSessionStore> storeClass, JwtTokenProperties properties)
            throws ReflectiveOperationException {
        Object redisUtil = resolveRedisUtil();
        if (redisUtil != null) {
            Constructor<? extends JwtSessionStore> constructor = findRedisAccessConstructor(storeClass);
            if (constructor != null) {
                return constructor.newInstance(redisUtil, properties);
            }
        }

        Object redisTemplate = resolveRedisTemplate();
        if (redisTemplate != null) {
            Constructor<? extends JwtSessionStore> constructor = findRedisAccessConstructor(storeClass);
            if (constructor != null) {
                return constructor.newInstance(redisTemplate, properties);
            }
        }

        if (isRedisStore(storeClass)) {
            throw new IllegalStateException("RedisJwtSessionStore requires RedisUtil or RedisTemplate bean");
        }

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

    private Object resolveRedisUtil() throws ClassNotFoundException {
        if (!ClassUtils.isPresent(REDIS_UTIL_CLASS_NAME, getClass().getClassLoader())) {
            return null;
        }
        Class<?> redisUtilClass = redisUtilClass();
        String[] beanNames = beanFactory.getBeanNamesForType(redisUtilClass);
        return beanNames.length == 0 ? null : beanFactory.getBean(beanNames[0]);
    }

    private Object resolveRedisTemplate() throws ClassNotFoundException {
        if (!ClassUtils.isPresent(REDIS_TEMPLATE_CLASS_NAME, getClass().getClassLoader())) {
            return null;
        }
        Class<?> redisTemplateClass = ClassUtils.forName(REDIS_TEMPLATE_CLASS_NAME, getClass().getClassLoader());
        String[] beanNames = beanFactory.getBeanNamesForType(redisTemplateClass);
        return beanNames.length == 0 ? null : beanFactory.getBean(beanNames[0]);
    }

    private Class<?> redisUtilClass() throws ClassNotFoundException {
        return ClassUtils.forName(REDIS_UTIL_CLASS_NAME, getClass().getClassLoader());
    }

    private Constructor<? extends JwtSessionStore> findRedisAccessConstructor(Class<? extends JwtSessionStore> storeClass) {
        return findConstructor(storeClass, Object.class, JwtTokenProperties.class);
    }

    private boolean isRedisStore(Class<? extends JwtSessionStore> storeClass) {
        return REDIS_STORE_CLASS_NAME.equals(storeClass.getName());
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
