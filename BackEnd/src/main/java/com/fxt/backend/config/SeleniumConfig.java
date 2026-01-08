package com.fxt.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Selenium配置类
 */
@Configuration
@ConfigurationProperties(prefix = "app.selenium")
public class SeleniumConfig {
    
    private String chromeDriverPath = "chromedriver.exe";
    private boolean headless = false;
    private int waitTimeout = 10;
    private int pageLoadTimeout = 30;
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    
    // Getters and Setters
    public String getChromeDriverPath() {
        return chromeDriverPath;
    }
    
    public void setChromeDriverPath(String chromeDriverPath) {
        this.chromeDriverPath = chromeDriverPath;
    }
    
    public boolean isHeadless() {
        return headless;
    }
    
    public void setHeadless(boolean headless) {
        this.headless = headless;
    }
    
    public int getWaitTimeout() {
        return waitTimeout;
    }
    
    public void setWaitTimeout(int waitTimeout) {
        this.waitTimeout = waitTimeout;
    }
    
    public int getPageLoadTimeout() {
        return pageLoadTimeout;
    }
    
    public void setPageLoadTimeout(int pageLoadTimeout) {
        this.pageLoadTimeout = pageLoadTimeout;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}