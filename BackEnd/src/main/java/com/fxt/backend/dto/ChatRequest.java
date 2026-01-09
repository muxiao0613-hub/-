package com.fxt.backend.dto;

/**
 * AI聊天请求DTO
 */
public class ChatRequest {
    private String message;
    private String sessionId; // 会话ID，用于维护对话历史
    private boolean clearHistory; // 是否清空历史
    
    public ChatRequest() {}
    
    public ChatRequest(String message, String sessionId) {
        this.message = message;
        this.sessionId = sessionId;
    }
    
    // Getters and Setters
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public boolean isClearHistory() { return clearHistory; }
    public void setClearHistory(boolean clearHistory) { this.clearHistory = clearHistory; }
}