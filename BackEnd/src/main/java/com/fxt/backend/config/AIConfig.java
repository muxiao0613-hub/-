package com.fxt.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ai.api")
public class AIConfig {
    
    private boolean enabled = false;
    private String key = "";
    private String url = "https://api.openai.com/v1/chat/completions";
    private String model = "gpt-3.5-turbo";
    private int maxTokens = 2000;
    private double temperature = 0.7;
    private int timeoutSeconds = 60;
    
    // 支持 Claude API
    private String provider = "openai"; // openai 或 claude
    private String claudeUrl = "https://api.anthropic.com/v1/messages";
    private String claudeModel = "claude-3-sonnet-20240229";
    
    // Getters and Setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public String getClaudeUrl() { return claudeUrl; }
    public void setClaudeUrl(String claudeUrl) { this.claudeUrl = claudeUrl; }
    
    public String getClaudeModel() { return claudeModel; }
    public void setClaudeModel(String claudeModel) { this.claudeModel = claudeModel; }
}