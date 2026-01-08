package com.fxt.backend.service;

import com.fxt.backend.config.AIConfig;
import com.fxt.backend.entity.ArticleData;
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
import java.util.List;

@Service
public class AIApiService {
    
    private static final Logger logger = LoggerFactory.getLogger(AIApiService.class);
    
    @Autowired
    private AIConfig aiConfig;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    
    public AIApiService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    /**
     * 检查 AI 服务是否可用
     */
    public boolean isAvailable() {
        return aiConfig.isEnabled() && 
                aiConfig.getKey() != null && 
                !aiConfig.getKey().isEmpty();
    }
    
    /**
     * 生成 AI 分析建议
     */
    public String generateAnalysis(ArticleData article, List<ArticleData> allArticles) {
        if (!isAvailable()) {
            return generateLocalAnalysis(article, allArticles);
        }
        
        try {
            String prompt = buildAnalysisPrompt(article, allArticles);
            
            if ("claude".equalsIgnoreCase(aiConfig.getProvider())) {
                return callClaudeApi(prompt);
            } else {
                return callOpenAIApi(prompt);
            }
        } catch (Exception e) {
            logger.error("AI API 调用失败: {}", e.getMessage());
            return generateLocalAnalysis(article, allArticles);
        }
    }
    
    /**
     * 调用 OpenAI API
     */
    private String callOpenAIApi(String prompt) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", aiConfig.getModel());
        requestBody.put("max_tokens", aiConfig.getMaxTokens());
        requestBody.put("temperature", aiConfig.getTemperature());
        
        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode systemMessage = objectMapper.createObjectNode();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是一位专业的电商内容分析专家，擅长分析小红书、得物等平台的内容表现，并给出具体可操作的优化建议。请用中文回答。");
        messages.add(systemMessage);
        
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
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
            logger.error("OpenAI API 错误: {} - {}", response.statusCode(), response.body());
            throw new RuntimeException("API 调用失败: " + response.statusCode());
        }
        
        JsonNode responseJson = objectMapper.readTree(response.body());
        return responseJson.path("choices").path(0).path("message").path("content").asText();
    }
    
    /**
     * 调用 Claude API
     */
    private String callClaudeApi(String prompt) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", aiConfig.getClaudeModel());
        requestBody.put("max_tokens", aiConfig.getMaxTokens());
        
        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);
        
        requestBody.set("messages", messages);
        requestBody.put("system", "你是一位专业的电商内容分析专家，擅长分析小红书、得物等平台的内容表现，并给出具体可操作的优化建议。请用中文回答。");
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(aiConfig.getClaudeUrl()))
            .header("Content-Type", "application/json")
            .header("x-api-key", aiConfig.getKey())
            .header("anthropic-version", "2023-06-01")
            .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            logger.error("Claude API 错误: {} - {}", response.statusCode(), response.body());
            throw new RuntimeException("API 调用失败: " + response.statusCode());
        }
        
        JsonNode responseJson = objectMapper.readTree(response.body());
        return responseJson.path("content").path(0).path("text").asText();
    }
    
    /**
     * 构建分析提示词 - 根据图文内容和数据给出针对性建议
     */
    private String buildAnalysisPrompt(ArticleData article, List<ArticleData> allArticles) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("作为电商内容专家，请分析以下文章并给出3条优化建议（每条不超过50字）：\n\n");

        prompt.append("【基本信息】\n");
        prompt.append("标题: ").append(article.getTitle()).append("\n");
        prompt.append("品牌: ").append(article.getBrand()).append("\n");
        prompt.append("类型: ").append(article.getContentType()).append("\n\n");

        prompt.append("【核心数据】\n");
        prompt.append("7天阅读: ").append(article.getReadCount7d()).append("\n");
        prompt.append("7天互动: ").append(article.getInteractionCount7d()).append("\n");
        prompt.append("好物访问: ").append(article.getProductVisit7d()).append("\n");

        if (article.getReadCount7d() != null && article.getReadCount7d() > 0) {
            double interactionRate = article.getInteractionCount7d() != null ?
                (double) article.getInteractionCount7d() / article.getReadCount7d() * 100 : 0;
            prompt.append(String.format("互动率: %.1f%%\n", interactionRate));
        }

        // 简化平台对比
        double avgRead = allArticles.stream()
            .filter(a -> a.getReadCount7d() != null)
            .mapToLong(ArticleData::getReadCount7d)
            .average().orElse(0);
        
        prompt.append(String.format("平台均值: %.0f\n", avgRead));
        prompt.append("状态: ").append(article.getAnomalyStatus()).append("\n\n");

        // 大幅简化内容预览
        if (article.getContent() != null && !article.getContent().isEmpty()) {
            String contentPreview = article.getContent().length() > 200
                ? article.getContent().substring(0, 200) + "..."
                : article.getContent();
            prompt.append("【内容摘要】\n").append(contentPreview).append("\n\n");
        }

        prompt.append("请给出3条具体优化建议，格式：\n");
        prompt.append("1. [建议类型] 具体建议内容\n");
        prompt.append("2. [建议类型] 具体建议内容\n");
        prompt.append("3. [建议类型] 具体建议内容");

        return prompt.toString();
    }

        prompt.append("请给出以下方面的具体建议：\n");
        prompt.append("1. 标题优化（给出3个具体改进方案）\n");
        prompt.append("2. 图片优化（首图、数量、排版）\n");
        prompt.append("3. 内容结构优化\n");
        prompt.append("4. 发布时间建议\n");
        prompt.append("5. 互动率提升策略\n");
        prompt.append("6. 转化率优化\n");
        prompt.append("7. 针对").append(article.getBrand()).append("品牌和").append(article.getPostType()).append("类型的专门建议\n\n");
        prompt.append("请用清晰的结构化格式回答，每个建议都要具体可操作。");

        return prompt.toString();
    }
    
    /**
     * 本地分析（当 AI API 不可用时）
     */
    private String generateLocalAnalysis(ArticleData article, List<ArticleData> allArticles) {
        StringBuilder analysis = new StringBuilder();
        
        analysis.append("🤖 AI 智能分析报告\n");
        analysis.append("═══════════════════════════════════════════\n\n");
        
        analysis.append("⚠️ 注意：当前使用本地分析模式\n");
        analysis.append("如需更精准的AI分析，请在配置文件中设置AI API密钥\n\n");
        
        // 标题分析
        analysis.append("【1. 标题分析】\n");
        String title = article.getTitle();
        if (title != null) {
            analysis.append("当前标题：").append(title).append("\n");
            analysis.append("标题长度：").append(title.length()).append("字\n");
            
            if (title.length() < 10) {
                analysis.append("⚠️ 标题过短，建议扩展至15-25字\n");
            } else if (title.length() > 30) {
                analysis.append("⚠️ 标题过长，建议精简至15-25字\n");
            } else {
                analysis.append("✅ 标题长度适中\n");
            }
            
            boolean hasEmoji = title.matches(".*[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+.*");
            boolean hasNumber = title.matches(".*\\d+.*");
            
            if (!hasNumber) {
                analysis.append("💡 建议：添加具体数字，如「3个技巧」「7天见效」\n");
            }
            if (!hasEmoji) {
                analysis.append("💡 建议：适当添加表情符号增加吸引力\n");
            }
        }
        analysis.append("\n");
        
        // 数据分析
        analysis.append("【2. 数据表现分析】\n");
        long readCount = article.getReadCount7d() != null ? article.getReadCount7d() : 0;
        long interactionCount = article.getInteractionCount7d() != null ? article.getInteractionCount7d() : 0;
        
        double interactionRate = readCount > 0 ? (double) interactionCount / readCount * 100 : 0;
        analysis.append(String.format("互动率：%.2f%%\n", interactionRate));
        
        if (interactionRate < 3) {
            analysis.append("⚠️ 互动率偏低，需要优化\n");
            analysis.append("💡 建议：增加互动引导语，如「你们觉得呢？」「评论区告诉我」\n");
        } else if (interactionRate > 8) {
            analysis.append("✅ 互动率优秀！可以复制此内容策略\n");
        } else {
            analysis.append("📊 互动率处于正常水平\n");
        }
        analysis.append("\n");
        
        // 转化分析
        analysis.append("【3. 转化漏斗分析】\n");
        long productVisit = article.getProductVisit7d() != null ? article.getProductVisit7d() : 0;
        long productWant = article.getProductWant7d() != null ? article.getProductWant7d() : 0;
        
        double visitRate = readCount > 0 ? (double) productVisit / readCount * 100 : 0;
        double wantRate = productVisit > 0 ? (double) productWant / productVisit * 100 : 0;
        
        analysis.append(String.format("好物访问率：%.2f%%\n", visitRate));
        analysis.append(String.format("想要转化率：%.2f%%\n", wantRate));
        
        if (visitRate < 1) {
            analysis.append("💡 建议：强化产品展示，突出购买链接入口\n");
        }
        if (wantRate < 10) {
            analysis.append("💡 建议：突出产品卖点和性价比\n");
        }
        analysis.append("\n");
        
        // 发布时间建议
        analysis.append("【4. 发布时间建议】\n");
        analysis.append("📅 最佳发布时段：\n");
        analysis.append("   • 工作日：12:00-14:00，19:00-22:00\n");
        analysis.append("   • 周末：10:00-12:00，15:00-17:00，20:00-22:00\n");
        analysis.append("💡 建议在用户活跃高峰期发布，可提升15-25%曝光\n\n");
        
        // 行动建议
        analysis.append("【5. 具体行动建议】\n");
        if ("BAD_ANOMALY".equals(article.getAnomalyStatus())) {
            analysis.append("🔴 该内容表现较差，建议：\n");
            analysis.append("   □ 重新编辑标题，增加情感词汇\n");
            analysis.append("   □ 优化首图质量和吸引力\n");
            analysis.append("   □ 增加产品使用场景描述\n");
            analysis.append("   □ 添加互动引导语\n");
        } else if ("GOOD_ANOMALY".equals(article.getAnomalyStatus())) {
            analysis.append("🟢 该内容表现优秀，建议：\n");
            analysis.append("   □ 记录成功要素，制作同类内容\n");
            analysis.append("   □ 分析用户评论，找出受欢迎的点\n");
            analysis.append("   □ 保持相同的发布时间和风格\n");
        } else {
            analysis.append("🟡 该内容表现正常，建议：\n");
            analysis.append("   □ 参考高表现内容进行优化\n");
            analysis.append("   □ 尝试不同的标题和封面\n");
            analysis.append("   □ 增加内容的互动性\n");
        }
        
        return analysis.toString();
    }
}