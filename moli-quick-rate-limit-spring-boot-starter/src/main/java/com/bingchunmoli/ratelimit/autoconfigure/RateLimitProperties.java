package com.bingchunmoli.ratelimit.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.InitializingBean;

import java.time.Duration;

/**
 * Configuration properties for method rate limiting.
 */
@ConfigurationProperties(prefix = "moli.rate-limit")
public class RateLimitProperties implements InitializingBean {

    private boolean enabled = true;
    private Store store = Store.MEMORY;
    private long defaultPermits = 100;
    private Duration defaultWindow = Duration.ofMinutes(1);
    private String keyPrefix = "moli:rate-limit";
    private int order = Ordered.HIGHEST_PRECEDENCE + 50;

    @Override
    public void afterPropertiesSet() {
        if (!enabled) return;
        if (defaultPermits <= 0) {
            throw new IllegalArgumentException("moli.rate-limit.default-permits must be positive");
        }
        if (defaultWindow == null || defaultWindow.isNegative() || defaultWindow.isZero()) {
            throw new IllegalArgumentException("moli.rate-limit.default-window must be positive");
        }
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("moli.rate-limit.key-prefix must not be blank");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store == null ? Store.MEMORY : store;
    }

    public long getDefaultPermits() {
        return defaultPermits;
    }

    public void setDefaultPermits(long defaultPermits) {
        this.defaultPermits = defaultPermits;
    }

    public Duration getDefaultWindow() {
        return defaultWindow;
    }

    public void setDefaultWindow(Duration defaultWindow) {
        this.defaultWindow = defaultWindow;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public enum Store {
        MEMORY,
        REDIS
    }
}
