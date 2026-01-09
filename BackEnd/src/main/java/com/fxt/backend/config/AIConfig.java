package com.fxt.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ai.api")
public class AIConfig {
    
    private boolean enabled = false;
    private String provider = "openai";
    private String key = "";
    private String url = "https://api.openai.com/v1/chat/completions";
    private String model = "gpt-4o-mini";
    private int maxTokens = 2000;
    private double temperature = 0.7;
    private int timeoutSeconds = 60;
    
    // AI聊天配置
    private boolean chatEnabled = true;
    private int maxHistory = 20;
    private String systemPrompt = "你是一位专业的社交媒体运营分析师，精通得物和小红书平台的内容运营策略。请用中文回答所有问题。";
    
    // Getters and Setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
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
    
    public boolean isChatEnabled() { return chatEnabled; }
    public void setChatEnabled(boolean chatEnabled) { this.chatEnabled = chatEnabled; }
    
    public int getMaxHistory() { return maxHistory; }
    public void setMaxHistory(int maxHistory) { this.maxHistory = maxHistory; }
    
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    
    /**
     * 检查是否有有效的API密钥
     */
    public boolean hasValidKey() {
        return key != null && !key.trim().isEmpty() && 
               !key.equals("sk-your-openai-api-key-here") &&
               key.startsWith("sk-");
    }
}