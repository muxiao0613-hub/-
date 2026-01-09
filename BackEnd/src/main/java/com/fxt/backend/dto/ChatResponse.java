package com.fxt.backend.dto;

import java.util.List;

/**
 * AI聊天响应DTO
 */
public class ChatResponse {
    private boolean success;
    private String message;
    private String response;
    private String sessionId;
    private List<ChatMessage> history;
    private boolean aiAvailable;
    
    public ChatResponse() {}
    
    public ChatResponse(boolean success, String response) {
        this.success = success;
        this.response = response;
    }
    
    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public List<ChatMessage> getHistory() { return history; }
    public void setHistory(List<ChatMessage> history) { this.history = history; }
    
    public boolean isAiAvailable() { return aiAvailable; }
    public void setAiAvailable(boolean aiAvailable) { this.aiAvailable = aiAvailable; }
}