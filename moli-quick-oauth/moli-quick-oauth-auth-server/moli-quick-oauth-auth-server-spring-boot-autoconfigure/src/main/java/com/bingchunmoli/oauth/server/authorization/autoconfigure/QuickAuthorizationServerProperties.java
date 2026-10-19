package com.bingchunmoli.oauth.server.authorization.autoconfigure;
import java.time.Duration;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("moli.oauth.auth-server")
public class QuickAuthorizationServerProperties {
    private boolean enabled;
    private boolean defaultFilterChainEnabled = true;
    private int order = 0;
    private final Login login = new Login();
    private final Storage storage = new Storage();
    private final Signing signing = new Signing();
    private final Map<String, List<String>> audiences = new HashMap<>();
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public boolean isDefaultFilterChainEnabled() { return defaultFilterChainEnabled; }
    public void setDefaultFilterChainEnabled(boolean value) { defaultFilterChainEnabled = value; }
    public int getOrder() { return order; }
    public void setOrder(int value) { order = value; }
    public Login getLogin() { return login; }
    public Storage getStorage() { return storage; }
    public Signing getSigning() { return signing; }
    public Map<String, List<String>> getAudiences() { return audiences; }
    public static class Login {
        private boolean enabled;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
    }
    public static class Storage {
        private String type = "memory", keyPrefix = "moli:oauth:authorization";
        private Duration waitTimeout = Duration.ofSeconds(15), lease = Duration.ofSeconds(30);
        public String getType() { return type; }
        public void setType(String value) { type = value.toLowerCase(Locale.ROOT); }
        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String value) {
            if (value == null || value.isBlank() || value.contains("{") || value.contains("}")) throw new IllegalArgumentException("Invalid authorization key prefix");
            keyPrefix = value;
        }
        public Duration getWaitTimeout() { return waitTimeout; }
        public void setWaitTimeout(Duration value) { waitTimeout = value; }
        public Duration getLease() { return lease; }
        public void setLease(Duration value) { lease = value; }
    }
    public static class Signing {
        private String jwkSetLocation, keyStoreLocation, keyStorePassword, keyAlias, activeKeyId;
        private boolean developmentEphemeral;
        public String getJwkSetLocation() { return jwkSetLocation; }
        public void setJwkSetLocation(String value) { jwkSetLocation = value==null || value.isBlank()?null:value; }
        public String getKeyStoreLocation() { return keyStoreLocation; }
        public void setKeyStoreLocation(String value) { keyStoreLocation = value==null || value.isBlank()?null:value; }
        public String getKeyStorePassword() { return keyStorePassword; }
        public void setKeyStorePassword(String value) { keyStorePassword = value; }
        public String getKeyAlias() { return keyAlias; }
        public void setKeyAlias(String value) { keyAlias = value; }
        public String getActiveKeyId() { return activeKeyId; }
        public void setActiveKeyId(String value) { activeKeyId = value==null || value.isBlank()?null:value; }
        public boolean isDevelopmentEphemeral() { return developmentEphemeral; }
        public void setDevelopmentEphemeral(boolean value) { developmentEphemeral = value; }
    }
}
