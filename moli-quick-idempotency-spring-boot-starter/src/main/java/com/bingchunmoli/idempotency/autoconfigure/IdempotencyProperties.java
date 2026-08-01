package com.bingchunmoli.idempotency.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

import java.time.Duration;

/**
 * Configuration properties for method idempotency.
 */
@ConfigurationProperties(prefix = "moli.idempotency")
public class IdempotencyProperties {

    private boolean enabled = true;
    private Store store = Store.MEMORY;
    private Duration defaultTimeout = Duration.ofMinutes(10);
    private String keyPrefix = "moli:idempotency";
    private int order = Ordered.HIGHEST_PRECEDENCE + 100;

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

    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
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
