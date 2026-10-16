package com.bingchunmoli.codegen.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("moli.codegen")
public class CodegenProperties {
    private boolean enabled;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
