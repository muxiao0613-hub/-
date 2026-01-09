package com.fxt.backend.controller;

import com.fxt.backend.config.AIConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 配置检查控制器
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {
    
    @Autowired
    private AIConfig aiConfig;
    
    /**
     * 检查AI配置状态
     */
    @GetMapping("/ai-status")
    public Map<String, Object> getAIStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("enabled", aiConfig.isEnabled());
        status.put("provider", aiConfig.getProvider());
        status.put("hasValidKey", aiConfig.hasValidKey());
        status.put("keyLength", aiConfig.getKey() != null ? aiConfig.getKey().length() : 0);
        status.put("maskedKey", aiConfig.hasValidKey() ? aiConfig.getMaskedKey() : "未配置");
        status.put("model", aiConfig.getModel());
        status.put("url", aiConfig.getUrl());
        status.put("maxTokens", aiConfig.getMaxTokens());
        status.put("temperature", aiConfig.getTemperature());
        status.put("timeoutSeconds", aiConfig.getTimeoutSeconds());
        status.put("chatEnabled", aiConfig.isChatEnabled());
        
        // 调试信息
        status.put("rawKey", aiConfig.getKey()); // 临时调试用，生产环境应该移除
        
        return status;
    }
}