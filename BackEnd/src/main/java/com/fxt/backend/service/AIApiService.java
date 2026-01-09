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
        return aiConfig != null &&
                aiConfig.isEnabled() &&
                aiConfig.hasValidKey();
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
            return callOpenAIApi(prompt);
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
        systemMessage.put("content", aiConfig.getSystemPrompt());
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
     * 构建分析提示词
     */
    private String buildAnalysisPrompt(ArticleData article, List<ArticleData> allArticles) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("作为电商内容专家，请针对以下文章进行深度分析：\n\n");
        prompt.append("【基本信息】\n");
        prompt.append("标题: ").append(article.getTitle()).append("\n");
        prompt.append("品牌: ").append(article.getBrand()).append("\n");
        prompt.append("内容类型: ").append(article.getContentType()).append("\n");
        prompt.append("素材来源: ").append(article.getMaterialSource()).append("\n\n");

        prompt.append("【核心数据】\n");
        prompt.append("7天阅读量: ").append(article.getReadCount7d()).append("\n");
        prompt.append("7天互动数: ").append(article.getInteractionCount7d()).append("\n");
        prompt.append("好物访问量: ").append(article.getProductVisit7d()).append("\n");

        if (article.getReadCount7d() != null && article.getReadCount7d() > 0) {
            double rate = (double) article.getInteractionCount7d() / article.getReadCount7d() * 100;
            prompt.append(String.format("当前互动率: %.1f%%\n", rate));
        }

        double avgRead = allArticles.stream()
                .filter(a -> a.getReadCount7d() != null)
                .mapToLong(ArticleData::getReadCount7d)
                .average().orElse(0);
        prompt.append(String.format("同类目平均阅读量: %.0f\n", avgRead));
        prompt.append("目前状态评级: ").append(article.getAnomalyStatus()).append("\n\n");

        if (article.getContent() != null && !article.getContent().isEmpty()) {
            String preview = article.getContent().length() > 300 ?
                    article.getContent().substring(0, 300) + "..." : article.getContent();
            prompt.append("【内容摘要】\n").append(preview).append("\n\n");
        }

        prompt.append("请给出以下 7 个维度的具体优化建议，要求具体、可落地：\n");
        prompt.append("1. 标题优化（给出3个具体的爆款标题改写方案）\n");
        prompt.append("2. 图片优化建议（首图吸引力、排版风格）\n");
        prompt.append("3. 内容结构调整\n");
        prompt.append("4. 最佳发布时间建议\n");
        prompt.append("5. 互动率提升具体钩子（Hook）设置\n");
        prompt.append("6. 转化率优化（如何引导用户点击好物链接）\n");
        prompt.append("7. 针对 ").append(article.getBrand()).append(" 品牌和 ").append(article.getMaterialSource()).append(" 平台的定制化建议。\n\n");
        prompt.append("请使用清晰的 Markdown 结构输出回复。");

        return prompt.toString();
    }

    /**
     * 本地分析逻辑（兜底方案）
     */
    private String generateLocalAnalysis(ArticleData article, List<ArticleData> allArticles) {
        StringBuilder analysis = new StringBuilder();
        analysis.append("🤖 本地规则分析报告 (AI 接口未开启/故障)\n");
        analysis.append("═══════════════════════════════════════════\n\n");

        // 1. 标题逻辑
        analysis.append("【1. 标题诊断】\n");
        String title = article.getTitle();
        if (title != null) {
            if (title.length() < 12) analysis.append("❌ 标题过短，信息量不足，建议增加至 18-24 字。\n");
            else if (title.length() > 32) analysis.append("❌ 标题过长，核心卖点不突出，建议精简。\n");
            else analysis.append("✅ 标题长度合适。\n");
        }

        // 2. 平台识别
        analysis.append("\n【2. 平台分析】\n");
        String source = article.getMaterialSource();
        if (source != null) {
            if (source.contains("得物") || source.contains("新媒体图文")) {
                analysis.append("📱 得物平台：建议重点关注产品展示和上脚效果\n");
            } else if (source.contains("小红书")) {
                analysis.append("📝 小红书平台：建议重点关注种草内容和生活场景\n");
            }
        }

        // 3. 数据逻辑
        analysis.append("\n【3. 数据表现】\n");
        long readCount = article.getReadCount7d() != null ? article.getReadCount7d() : 0;
        long interactionCount = article.getInteractionCount7d() != null ? article.getInteractionCount7d() : 0;
        double interactionRate = readCount > 0 ? (double) interactionCount / readCount * 100 : 0;

        analysis.append(String.format("当前互动率: %.2f%%\n", interactionRate));
        if (interactionRate < 2.0) analysis.append("💡 建议：在正文末尾增加提问，引导用户评论互动。\n");

        // 4. 状态建议
        analysis.append("\n【4. 行动建议】\n");
        if ("BAD_ANOMALY".equals(article.getAnomalyStatus())) {
            analysis.append("🚩 内容表现异常偏低：建议检查是否有敏感词，或首图是否不够吸引人。\n");
        } else {
            analysis.append("✨ 表现稳定：建议保持当前发布频率，持续观察。\n");
        }

        analysis.append("\n💡 提示：配置OpenAI API密钥可获得更详细的AI分析建议。");

        return analysis.toString();
    }
}