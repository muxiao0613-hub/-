package com.fxt.backend.controller;

import com.fxt.backend.dto.ChatRequest;
import com.fxt.backend.dto.ChatResponse;
import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.service.AIChatService;
import com.fxt.backend.service.MultiPlatformDataService;
import com.fxt.backend.repository.ArticleDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 多平台数据采集与AI分析控制器
 * 整合平台识别、数据采集、AI聊天等功能
 */
@RestController
@RequestMapping("/api/multiplatform")
@CrossOrigin(origins = "*")
public class MultiPlatformController {
    
    @Autowired
    private MultiPlatformDataService multiPlatformDataService;
    
    @Autowired
    private AIChatService aiChatService;
    
    @Autowired
    private ArticleDataRepository articleDataRepository;
    
    // ==================== 平台识别和数据采集 ====================
    
    /**
     * 获取平台统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getPlatformStatistics() {
        try {
            Map<String, Object> statistics = multiPlatformDataService.getPlatformStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * 批量爬取数据
     */
    @PostMapping("/crawl-batch")
    public ResponseEntity<Map<String, Object>> crawlBatch(@RequestBody Map<String, List<Long>> request) {
        try {
            List<Long> articleIds = request.get("articleIds");
            if (articleIds == null || articleIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "请选择要爬取的文章"
                ));
            }
            
            // 同步爬取（可以改为异步）
            Map<String, Object> results = multiPlatformDataService.crawlDataByIds(articleIds, progress -> {
                // 这里可以通过WebSocket推送进度
                System.out.printf("[%d/%d] 正在处理: %s - %s%n", 
                    progress.getCurrent(), 
                    progress.getTotal(),
                    progress.getPlatform().getDisplayName(),
                    progress.getArticle().getDataId()
                );
            });
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "批量爬取完成");
            response.putAll(results);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * 异步批量爬取数据
     */
    @PostMapping("/crawl-batch-async")
    public ResponseEntity<Map<String, Object>> crawlBatchAsync(@RequestBody Map<String, List<Long>> request) {
        try {
            List<Long> articleIds = request.get("articleIds");
            if (articleIds == null || articleIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "请选择要爬取的文章"
                ));
            }
            
            // 异步爬取
            CompletableFuture<Map<String, Object>> future = multiPlatformDataService.crawlAllDataAsync(articleIds, progress -> {
                // WebSocket推送进度
                System.out.printf("[%d/%d] 正在处理: %s - %s%n", 
                    progress.getCurrent(), 
                    progress.getTotal(),
                    progress.getPlatform().getDisplayName(),
                    progress.getArticle().getDataId()
                );
            });
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "爬取任务已启动，请稍后查看结果",
                "taskId", future.hashCode() // 简单的任务ID
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * 重新爬取单个文章
     */
    @PostMapping("/articles/{id}/recrawl")
    public ResponseEntity<Map<String, Object>> recrawlArticle(@PathVariable Long id) {
        try {
            ArticleData article = multiPlatformDataService.recrawlArticle(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", "SUCCESS".equals(article.getCrawlStatus()));
            response.put("message", article.getCrawlError() != null ? article.getCrawlError() : "重新爬取完成");
            response.put("crawlStatus", article.getCrawlStatus());
            response.put("article", article);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * 智能识别所有文章的平台
     */
    @PostMapping("/identify-platforms")
    public ResponseEntity<Map<String, Object>> identifyPlatforms() {
        try {
            List<ArticleData> articles = articleDataRepository.findAll();
            multiPlatformDataService.identifyPlatforms(articles);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "平台识别完成",
                "processedCount", articles.size()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    // ==================== AI聊天功能 ====================
    
    /**
     * AI聊天
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        try {
            // 生成会话ID（如果没有提供）
            if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
                request.setSessionId(UUID.randomUUID().toString());
            }
            
            ChatResponse response = aiChatService.chat(request);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("聊天服务异常: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 初始化AI聊天会话（加载数据分析）
     */
    @PostMapping("/chat/initialize")
    public ResponseEntity<ChatResponse> initializeChat(@RequestBody Map<String, String> request) {
        try {
            String sessionId = request.getOrDefault("sessionId", UUID.randomUUID().toString());
            ChatResponse response = aiChatService.initializeSession(sessionId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("初始化聊天失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 清空聊天历史
     */
    @PostMapping("/chat/clear")
    public ResponseEntity<ChatResponse> clearChatHistory(@RequestBody Map<String, String> request) {
        try {
            String sessionId = request.get("sessionId");
            if (sessionId == null || sessionId.isEmpty()) {
                return ResponseEntity.badRequest().body(new ChatResponse(false, "会话ID不能为空"));
            }
            
            ChatRequest clearRequest = new ChatRequest();
            clearRequest.setSessionId(sessionId);
            clearRequest.setClearHistory(true);
            
            ChatResponse response = aiChatService.chat(clearRequest);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("清空历史失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 获取快捷命令列表
     */
    @GetMapping("/chat/quick-commands")
    public ResponseEntity<Map<String, Object>> getQuickCommands() {
        try {
            Map<String, String> commands = aiChatService.getQuickCommands();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "commands", commands
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    // ==================== 系统状态 ====================
    
    /**
     * 获取系统状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            
            // 平台统计
            Map<String, Object> platformStats = multiPlatformDataService.getPlatformStatistics();
            status.put("platformStatistics", platformStats);
            
            // AI服务状态
            status.put("aiChatEnabled", true); // AIChatService总是可用
            
            // 快捷命令
            status.put("quickCommands", aiChatService.getQuickCommands().keySet());
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}