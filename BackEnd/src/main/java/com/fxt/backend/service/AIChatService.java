package com.fxt.backend.service;

import com.fxt.backend.config.AIConfig;
import com.fxt.backend.dto.ChatMessage;
import com.fxt.backend.dto.ChatRequest;
import com.fxt.backend.dto.ChatResponse;
import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.repository.ArticleDataRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI聊天服务
 * 支持多轮对话和数据分析
 */
@Service
public class AIChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(AIChatService.class);
    
    @Autowired
    private AIConfig aiConfig;
    
    @Autowired
    private ArticleDataRepository articleDataRepository;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    
    // 会话历史存储 (sessionId -> 消息列表)
    private final Map<String, List<ChatMessage>> sessionHistories = new ConcurrentHashMap<>();
    
    // 快捷命令映射
    private final Map<String, String> quickCommands = new HashMap<>();
    
    public AIChatService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        
        initializeQuickCommands();
    }
    
    /**
     * 初始化快捷命令
     */
    private void initializeQuickCommands() {
        quickCommands.put("内容策略", "根据当前数据，你认为哪种类型的内容表现最好？我应该如何调整内容策略？");
        quickCommands.put("发布时间", "从数据来看，最佳发布时间是什么时候？有什么规律吗？");
        quickCommands.put("互动提升", "如何提升帖子的互动率？有什么具体的技巧？");
        quickCommands.put("转化优化", "如何提升好物访问和想要的转化率？");
        quickCommands.put("平台差异", "得物和小红书两个平台的运营策略应该有什么区别？");
        quickCommands.put("数据分析", "请分析当前的整体数据表现，给出专业建议。");
        quickCommands.put("标题优化", "如何写出更吸引人的标题？给我一些具体的技巧和案例。");
        quickCommands.put("图片建议", "什么样的图片更容易获得高点击率？");
    }
    
    /**
     * 处理聊天请求
     */
    public ChatResponse chat(ChatRequest request) {
        ChatResponse response = new ChatResponse();
        response.setSessionId(request.getSessionId());
        response.setAiAvailable(isAIAvailable());
        
        try {
            // 处理清空历史命令
            if (request.isClearHistory()) {
                clearHistory(request.getSessionId());
                response.setSuccess(true);
                response.setResponse("对话历史已清空，我们可以开始新的对话了。");
                response.setHistory(getHistory(request.getSessionId()));
                return response;
            }
            
            // 处理快捷命令
            String message = processQuickCommand(request.getMessage());
            
            // 获取AI回复
            String aiResponse;
            if (isAIAvailable()) {
                aiResponse = callQwenChat(message, request.getSessionId());
            } else {
                aiResponse = generateLocalResponse(message);
            }
            
            // 更新对话历史
            addToHistory(request.getSessionId(), "user", request.getMessage());
            addToHistory(request.getSessionId(), "assistant", aiResponse);
            
            response.setSuccess(true);
            response.setResponse(aiResponse);
            response.setHistory(getHistory(request.getSessionId()));
            
        } catch (Exception e) {
            logger.error("AI聊天失败: {}", e.getMessage());
            response.setSuccess(false);
            response.setMessage("聊天服务暂时不可用: " + e.getMessage());
            response.setResponse("抱歉，我现在无法回答您的问题。请稍后再试。");
        }
        
        return response;
    }
    
    /**
     * 初始化会话（加载数据分析）
     */
    public ChatResponse initializeSession(String sessionId) {
        ChatResponse response = new ChatResponse();
        response.setSessionId(sessionId);
        response.setAiAvailable(isAIAvailable());
        
        try {
            // 清空历史
            clearHistory(sessionId);
            
            // 生成数据分析提示
            String dataAnalysis = generateDataAnalysisPrompt();
            
            String initialMessage = "请分析以下运营数据，并给出你的初步观察和建议：\n\n" + dataAnalysis + 
                "\n\n请提供：\n1. 数据整体表现评估\n2. 发现的问题或值得关注的点\n3. 3-5条具体的优化建议";
            
            String aiResponse;
            if (isAIAvailable()) {
                aiResponse = callQwenChat(initialMessage, sessionId);
            } else {
                aiResponse = generateLocalDataAnalysis();
            }
            
            // 添加到历史
            addToHistory(sessionId, "system", "数据分析初始化");
            addToHistory(sessionId, "assistant", aiResponse);
            
            response.setSuccess(true);
            response.setResponse(aiResponse);
            response.setHistory(getHistory(sessionId));
            
        } catch (Exception e) {
            logger.error("初始化会话失败: {}", e.getMessage());
            response.setSuccess(false);
            response.setMessage("初始化失败: " + e.getMessage());
        }
        
        return response;
    }
    
    /**
     * 处理快捷命令
     */
    private String processQuickCommand(String message) {
        if (message.startsWith("/")) {
            String command = message.substring(1);
            return quickCommands.getOrDefault(command, message);
        }
        return message;
    }
    
    /**
     * 调用通义千问聊天API
     */
    private String callQwenChat(String message, String sessionId) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", aiConfig.getModel());
        requestBody.put("max_tokens", aiConfig.getMaxTokens());
        requestBody.put("temperature", aiConfig.getTemperature());
        
        ArrayNode messages = objectMapper.createArrayNode();
        
        // 添加系统提示
        ObjectNode systemMessage = objectMapper.createObjectNode();
        systemMessage.put("role", "system");
        systemMessage.put("content", aiConfig.getSystemPrompt());
        messages.add(systemMessage);
        
        // 添加历史对话（限制数量）
        List<ChatMessage> history = getHistory(sessionId);
        int startIndex = Math.max(0, history.size() - aiConfig.getMaxHistory());
        for (int i = startIndex; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            if (!"system".equals(msg.getRole())) {
                ObjectNode historyMessage = objectMapper.createObjectNode();
                historyMessage.put("role", msg.getRole());
                historyMessage.put("content", msg.getContent());
                messages.add(historyMessage);
            }
        }
        
        // 添加当前消息
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", message);
        messages.add(userMessage);
        
        requestBody.set("messages", messages);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiConfig.getUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + aiConfig.getKey())
                .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API 错误: " + response.statusCode() + " - " + response.body());
        }
        
        JsonNode responseJson = objectMapper.readTree(response.body());
        return responseJson.path("choices").path(0).path("message").path("content").asText();
    }
    
    /**
     * 生成本地响应（兜底方案）
     */
    private String generateLocalResponse(String message) {
        String lowerMessage = message.toLowerCase();
        
        if (lowerMessage.contains("内容策略") || lowerMessage.contains("策略")) {
            return "📊 基于数据分析建议：\n\n" +
                   "1. **图文内容**表现更稳定，建议保持70%的图文比例\n" +
                   "2. **产品展示类**内容转化率更高\n" +
                   "3. **教程类**内容互动率较好\n" +
                   "4. 建议在标题中加入数字和情感词汇\n\n" +
                   "💡 提示：这是本地分析结果，开启AI服务可获得更详细的个性化建议。";
        }
        
        if (lowerMessage.contains("发布时间") || lowerMessage.contains("时间")) {
            return "⏰ 最佳发布时间建议：\n\n" +
                   "**得物平台：**\n" +
                   "- 工作日：19:00-21:00\n" +
                   "- 周末：14:00-16:00, 20:00-22:00\n\n" +
                   "**小红书平台：**\n" +
                   "- 工作日：12:00-13:00, 18:00-20:00\n" +
                   "- 周末：10:00-12:00, 15:00-17:00\n\n" +
                   "💡 建议根据你的粉丝活跃时间进行调整。";
        }
        
        return "🤖 本地模式回复：\n\n" +
               "感谢您的提问！目前AI服务未开启，我只能提供基础的建议。\n\n" +
               "建议：\n" +
               "1. 配置通义千问API密钥以获得智能分析\n" +
               "2. 查看数据面板了解详细表现\n" +
               "3. 使用快捷命令：/内容策略、/发布时间、/互动提升等\n\n" +
               "如需详细分析，请开启AI服务。";
    }
    
    /**
     * 生成数据分析提示
     */
    private String generateDataAnalysisPrompt() {
        List<ArticleData> articles = articleDataRepository.findAll();
        
        if (articles.isEmpty()) {
            return "暂无数据，请先上传Excel文件。";
        }
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("【基本信息】\n");
        prompt.append("总帖子数：").append(articles.size()).append("\n");
        
        // 平台分布
        Map<String, Long> platformCount = new HashMap<>();
        articles.forEach(article -> {
            String source = article.getMaterialSource();
            platformCount.merge(source, 1L, Long::sum);
        });
        prompt.append("平台分布：").append(platformCount).append("\n");
        
        // 数据概览
        long totalRead7d = articles.stream().mapToLong(a -> a.getReadCount7d() != null ? a.getReadCount7d() : 0).sum();
        long totalInteraction7d = articles.stream().mapToLong(a -> a.getInteractionCount7d() != null ? a.getInteractionCount7d() : 0).sum();
        double avgRead = articles.stream().mapToLong(a -> a.getReadCount7d() != null ? a.getReadCount7d() : 0).average().orElse(0);
        
        prompt.append("\n【数据概览】\n");
        prompt.append("7天总阅读量：").append(totalRead7d).append("\n");
        prompt.append("7天总互动量：").append(totalInteraction7d).append("\n");
        prompt.append("平均阅读量：").append(String.format("%.0f", avgRead)).append("\n");
        
        if (totalRead7d > 0) {
            double interactionRate = (double) totalInteraction7d / totalRead7d * 100;
            prompt.append("整体互动率：").append(String.format("%.2f%%", interactionRate)).append("\n");
        }
        
        return prompt.toString();
    }
    
    /**
     * 生成本地数据分析
     */
    private String generateLocalDataAnalysis() {
        return "📊 **数据分析报告**\n\n" +
               generateDataAnalysisPrompt() + "\n\n" +
               "💡 **初步建议：**\n" +
               "1. 关注互动率较低的内容，分析原因\n" +
               "2. 复制表现好的内容类型和发布时间\n" +
               "3. 优化标题和首图吸引力\n" +
               "4. 定期分析竞品内容策略\n\n" +
               "🔧 **提示：** 开启AI服务可获得更深入的个性化分析和建议。";
    }
    
    /**
     * 检查AI服务是否可用
     */
    private boolean isAIAvailable() {
        boolean available = aiConfig != null && aiConfig.isEnabled() && aiConfig.hasValidKey();
        logger.debug("AI聊天服务可用性检查: enabled={}, hasValidKey={}, result={}",
            aiConfig != null ? aiConfig.isEnabled() : "null",
            aiConfig != null ? aiConfig.hasValidKey() : "null",
            available);
        return available;
    }
    
    /**
     * 添加消息到历史
     */
    private void addToHistory(String sessionId, String role, String content) {
        sessionHistories.computeIfAbsent(sessionId, k -> new ArrayList<>())
                .add(new ChatMessage(role, content));
    }
    
    /**
     * 获取对话历史
     */
    private List<ChatMessage> getHistory(String sessionId) {
        return sessionHistories.getOrDefault(sessionId, new ArrayList<>());
    }
    
    /**
     * 清空对话历史
     */
    private void clearHistory(String sessionId) {
        sessionHistories.remove(sessionId);
    }
    
    /**
     * 获取快捷命令列表
     */
    public Map<String, String> getQuickCommands() {
        return new HashMap<>(quickCommands);
    }
}