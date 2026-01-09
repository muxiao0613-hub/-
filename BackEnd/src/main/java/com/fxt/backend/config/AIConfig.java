package com.fxt.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.api")
public class AIConfig {
    
    private boolean enabled = true;  // 默认启用
    private String provider = "qwen";
    private String key = "";
    private String url = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private String model = "qwen-turbo";
    private int maxTokens = 3000;
    private double temperature = 0.7;
    private int timeoutSeconds = 60;
    
    // AI聊天配置
    private boolean chatEnabled = true;
    private int maxHistory = 30;
    private String systemPrompt = "你是一位专业的社交媒体运营分析师，精通得物和小红书平台的内容运营策略。你会根据数据分析结果给出具体、可执行的优化建议。请用中文回答所有问题，回答要专业、详细、有条理。";
    
    // 代理配置
    private ProxyConfig proxy = new ProxyConfig();
    
    public static class ProxyConfig {
        private boolean enabled = false;
        private String host = "";
        private Integer port = 0;  // 使用Integer而不是int，允许null值
        private String username = "";
        private String password = "";
        
        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        
        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
    
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
    
    public ProxyConfig getProxy() { return proxy; }
    public void setProxy(ProxyConfig proxy) { this.proxy = proxy; }
    
    /**
     * 检查是否有有效的API密钥
     * 优化验证逻辑，支持多种密钥格式
     */
    public boolean hasValidKey() {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }
        
        String trimmedKey = key.trim();
        
        // 排除明显的占位符
        if (trimmedKey.equals("sk-your-openai-api-key-here") ||
            trimmedKey.equals("your-api-key") ||
            trimmedKey.length() < 20) {
            return false;
        }
        
        // 支持多种密钥格式
        // OpenAI: sk-xxx, sk-proj-xxx, sk-org-xxx
        // 通义千问: sk-xxx (32位字符)
        return trimmedKey.startsWith("sk-") && trimmedKey.length() >= 20;
    }
    
    /**
     * 获取脱敏后的密钥（用于日志）
     */
    public String getMaskedKey() {
        if (key == null || key.length() < 10) {
            return "***";
        }
        return key.substring(0, 8) + "..." + key.substring(key.length() - 4);
    }
}