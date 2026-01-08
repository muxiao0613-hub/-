package com.fxt.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.selenium")
public class SeleniumConfig {

    private String chromeDriverPath = "chromedriver.exe";
    private boolean headless = true;
    private int waitTimeout = 15;
    private int pageLoadTimeout = 30;

    // Getters and Setters
    public String getChromeDriverPath() { return chromeDriverPath; }
    public void setChromeDriverPath(String chromeDriverPath) { this.chromeDriverPath = chromeDriverPath; }

    public boolean isHeadless() { return headless; }
    public void setHeadless(boolean headless) { this.headless = headless; }

    public int getWaitTimeout() { return waitTimeout; }
    public void setWaitTimeout(int waitTimeout) { this.waitTimeout = waitTimeout; }

    public int getPageLoadTimeout() { return pageLoadTimeout; }
    public void setPageLoadTimeout(int pageLoadTimeout) { this.pageLoadTimeout = pageLoadTimeout; }
}