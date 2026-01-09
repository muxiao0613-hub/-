package com.fxt.backend.service;

import com.fxt.backend.config.AIConfig;
import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.util.NetworkDiagnostic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
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
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 服务启动时检查AI配置
     */
    @PostConstruct
    public void checkAIConfiguration() {
        logger.info("========== AI服务配置检查 ==========");
        logger.info("AI服务启用状态: {}", aiConfig.isEnabled());
        logger.info("AI提供商: {} (通义千问)", aiConfig.getProvider());
        logger.info("配置的API密钥: {}", aiConfig.getKey() != null ? aiConfig.getKey() : "null");
        logger.info("API密钥长度: {}", aiConfig.getKey() != null ? aiConfig.getKey().length() : 0);
        logger.info("API密钥状态: {}", aiConfig.hasValidKey() ? "已配置 (" + aiConfig.getMaskedKey() + ")" : "未配置");
        logger.info("模型: {}", aiConfig.getModel());
        logger.info("API地址: {}", aiConfig.getUrl());
        logger.info("最大Token数: {}", aiConfig.getMaxTokens());
        logger.info("超时时间: {}秒", aiConfig.getTimeoutSeconds());
        
        // 测试网络连接
        if (aiConfig.hasValidKey()) {
            testQwenConnection();
        } else {
            logger.warn("⚠️ API密钥未配置或无效，跳过网络连接测试");
        }
        
        logger.info("AI服务可用性: {}", isAvailable() ? "✓ 可用" : "✗ 不可用");
        logger.info("=====================================");
    }
    
    /**
     * 测试通义千问连接
     */
    private void testQwenConnection() {
        try {
            logger.info("测试通义千问连接...");
            
            // 简单的连接测试
            HttpRequest testRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"))
                .header("Authorization", "Bearer " + aiConfig.getKey())
                .header("User-Agent", "Java-HttpClient")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(testRequest, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200 || response.statusCode() == 400) {
                logger.info("✓ 网络连接正常，通义千问API可访问");
            } else if (response.statusCode() == 401) {
                logger.warn("⚠️ API密钥无效或已过期");
            } else {
                logger.warn("⚠️ 通义千问API返回状态码: {}", response.statusCode());
            }
            
        } catch (java.net.ConnectException e) {
            logger.error("✗ 网络连接失败: 无法连接到通义千问服务器");
            logger.error("可能的解决方案:");
            logger.error("1. 检查网络连接");
            logger.error("2. 检查防火墙设置");
            
        } catch (java.net.SocketTimeoutException e) {
            logger.error("✗ 连接超时: 通义千问服务器响应缓慢");
        } catch (Exception e) {
            logger.error("✗ 网络测试失败: {}", e.getMessage());
        }
    }

    /**
     * 检查 AI 服务是否可用
     */
    public boolean isAvailable() {
        boolean available = aiConfig != null &&
                aiConfig.isEnabled() &&
                aiConfig.hasValidKey();
        
        if (!available) {
            logger.debug("AI服务不可用 - enabled: {}, hasValidKey: {}",
                aiConfig != null ? aiConfig.isEnabled() : "null",
                aiConfig != null ? aiConfig.hasValidKey() : "null");
        }
        
        return available;
    }

    /**
     * 生成 AI 分析建议
     */
    public String generateAnalysis(ArticleData article, List<ArticleData> allArticles) {
        if (!isAvailable()) {
            logger.info("AI服务不可用，使用本地分析模式");
            return generateLocalAnalysis(article, allArticles);
        }

        try {
            logger.info("开始调用通义千问API生成分析建议...");
            String prompt = buildAnalysisPrompt(article, allArticles);
            String result = callQwenApi(prompt);
            logger.info("通义千问API调用成功，返回内容长度: {} 字符", result.length());
            return result;
        } catch (Exception e) {
            logger.error("AI API 调用失败: {}", e.getMessage(), e);
            logger.info("降级使用本地分析模式");
            return generateLocalAnalysis(article, allArticles);
        }
    }

    /**
     * 调用通义千问API（增强版 - 支持重试）
     */
    private String callQwenApi(String prompt) throws Exception {
        return callQwenApiWithRetry(prompt, 3);
    }
    
    /**
     * 带重试机制的通义千问API调用
     */
    private String callQwenApiWithRetry(String prompt, int maxRetries) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.info("通义千问API调用尝试 {}/{}", attempt, maxRetries);
                return performQwenApiCall(prompt);
            } catch (Exception e) {
                lastException = e;
                logger.warn("通义千问API调用失败 (尝试 {}/{}): {}", attempt, maxRetries, e.getMessage());
                
                if (attempt < maxRetries) {
                    // 指数退避重试
                    long delay = (long) Math.pow(2, attempt) * 1000;
                    logger.info("等待 {}ms 后重试...", delay);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                }
            }
        }
        
        throw new RuntimeException("通义千问API调用失败，已重试" + maxRetries + "次", lastException);
    }
    
    /**
     * 执行实际的通义千问API调用
     */
    private String performQwenApiCall(String prompt) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", aiConfig.getModel());
        requestBody.put("max_tokens", aiConfig.getMaxTokens());
        requestBody.put("temperature", aiConfig.getTemperature());

        ArrayNode messages = objectMapper.createArrayNode();
        
        // 系统提示
        ObjectNode systemMessage = objectMapper.createObjectNode();
        systemMessage.put("role", "system");
        systemMessage.put("content", aiConfig.getSystemPrompt());
        messages.add(systemMessage);

        // 用户提示
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);

        requestBody.set("messages", messages);

        String requestBodyStr = objectMapper.writeValueAsString(requestBody);
        logger.debug("通义千问API请求体: {}", requestBodyStr.substring(0, Math.min(500, requestBodyStr.length())) + "...");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiConfig.getUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + aiConfig.getKey())
                .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyStr))
                .build();

        logger.info("发送请求到: {}", aiConfig.getUrl());
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        logger.info("通义千问API响应状态码: {}", response.statusCode());

        if (response.statusCode() != 200) {
            String errorBody = response.body();
            logger.error("通义千问API 错误响应: {}", errorBody);
            
            // 解析错误信息
            try {
                JsonNode errorJson = objectMapper.readTree(errorBody);
                String errorMessage = errorJson.path("error").path("message").asText("未知错误");
                throw new RuntimeException("通义千问API错误: " + errorMessage);
            } catch (Exception parseError) {
                throw new RuntimeException("API 调用失败，状态码: " + response.statusCode() + ", 响应: " + errorBody);
            }
        }

        JsonNode responseJson = objectMapper.readTree(response.body());
        String content = responseJson.path("choices").path(0).path("message").path("content").asText();
        
        // 记录使用的token数
        JsonNode usage = responseJson.path("usage");
        if (!usage.isMissingNode()) {
            logger.info("Token使用情况 - 提示: {}, 完成: {}, 总计: {}",
                usage.path("prompt_tokens").asInt(),
                usage.path("completion_tokens").asInt(),
                usage.path("total_tokens").asInt());
        }
        
        return content;
    }

    /**
     * 构建分析提示词（优化版）
     */
    private String buildAnalysisPrompt(ArticleData article, List<ArticleData> allArticles) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# 电商内容深度分析任务\n\n");
        prompt.append("请作为资深电商内容专家，对以下文章进行全面分析并给出专业建议。\n\n");
        
        prompt.append("## 文章基本信息\n");
        prompt.append("- **标题**: ").append(article.getTitle() != null ? article.getTitle() : "无标题").append("\n");
        prompt.append("- **品牌**: ").append(article.getBrand()).append("\n");
        prompt.append("- **平台**: ").append(article.getPlatform() != null ? article.getPlatform() : article.getMaterialSource()).append("\n");
        prompt.append("- **内容类型**: ").append(article.getContentType()).append("\n");
        prompt.append("- **发文类型**: ").append(article.getPostType()).append("\n");
        prompt.append("- **款式信息**: ").append(article.getStyleInfo() != null ? article.getStyleInfo() : "无").append("\n\n");

        prompt.append("## 核心数据指标\n");
        prompt.append("| 指标 | 7天数据 | 14天数据 |\n");
        prompt.append("|------|---------|----------|\n");
        prompt.append("| 阅读量 | ").append(formatNum(article.getReadCount7d())).append(" | ").append(formatNum(article.getReadCount14d())).append(" |\n");
        prompt.append("| 互动量 | ").append(formatNum(article.getInteractionCount7d())).append(" | ").append(formatNum(article.getInteractionCount14d())).append(" |\n");
        prompt.append("| 好物访问 | ").append(formatNum(article.getProductVisit7d())).append(" | ").append(formatNum(article.getProductVisitCount())).append(" |\n");
        prompt.append("| 好物想要 | ").append(formatNum(article.getProductWant7d())).append(" | ").append(formatNum(article.getProductWant14d())).append(" |\n\n");

        // 计算关键比率
        if (article.getReadCount7d() != null && article.getReadCount7d() > 0) {
            double interactionRate = (double) (article.getInteractionCount7d() != null ? article.getInteractionCount7d() : 0) / article.getReadCount7d() * 100;
            double conversionRate = (double) (article.getProductVisit7d() != null ? article.getProductVisit7d() : 0) / article.getReadCount7d() * 100;
            prompt.append("## 关键效率指标\n");
            prompt.append(String.format("- **互动率**: %.2f%%\n", interactionRate));
            prompt.append(String.format("- **好物转化率**: %.2f%%\n", conversionRate));
        }

        // 平均数据对比
        double avgRead = allArticles.stream()
                .filter(a -> a.getReadCount7d() != null)
                .mapToLong(ArticleData::getReadCount7d)
                .average().orElse(0);
        double avgInteraction = allArticles.stream()
                .filter(a -> a.getInteractionCount7d() != null)
                .mapToLong(ArticleData::getInteractionCount7d)
                .average().orElse(0);
        
        prompt.append("\n## 平台数据对比\n");
        prompt.append(String.format("- 平台平均阅读量: %.0f\n", avgRead));
        prompt.append(String.format("- 平台平均互动量: %.0f\n", avgInteraction));
        prompt.append("- 当前状态评级: **").append(getStatusText(article.getAnomalyStatus())).append("**\n\n");

        // 内容摘要
        if (article.getContent() != null && !article.getContent().isEmpty()) {
            String preview = article.getContent().length() > 500 ?
                    article.getContent().substring(0, 500) + "..." : article.getContent();
            prompt.append("## 内容摘要\n");
            prompt.append("```\n").append(preview).append("\n```\n\n");
        }

        prompt.append("## 请提供以下分析和建议\n\n");
        prompt.append("### 1. 数据诊断\n");
        prompt.append("分析当前数据表现的优势和不足，找出关键问题点。\n\n");
        
        prompt.append("### 2. 标题优化\n");
        prompt.append("请给出3个具体的爆款标题改写方案，说明改写理由。\n\n");
        
        prompt.append("### 3. 内容优化\n");
        prompt.append("从结构、吸引力、互动性三个维度给出具体优化建议。\n\n");
        
        prompt.append("### 4. 视觉优化\n");
        prompt.append("针对图片/封面的优化建议。\n\n");
        
        prompt.append("### 5. 发布策略\n");
        prompt.append("最佳发布时间、频率建议。\n\n");
        
        prompt.append("### 6. 互动提升\n");
        prompt.append("如何设计互动钩子(Hook)提升评论和分享。\n\n");
        
        prompt.append("### 7. 转化优化\n");
        prompt.append("如何引导用户点击好物链接，提升转化率。\n\n");
        
        prompt.append("### 8. 平台特化建议\n");
        prompt.append("针对").append(article.getPlatform() != null ? article.getPlatform() : "当前平台").append("的特定优化策略。\n\n");
        
        prompt.append("请使用清晰的Markdown格式输出，每个建议都要具体、可执行。");

        return prompt.toString();
    }

    private String formatNum(Long num) {
        return num != null ? String.format("%,d", num) : "0";
    }

    private String getStatusText(String status) {
        if ("GOOD_ANOMALY".equals(status)) return "表现优秀 ⭐";
        if ("BAD_ANOMALY".equals(status)) return "需要优化 ⚠️";
        return "正常";
    }

    /**
     * 本地分析逻辑（兜底方案）- 优化版
     */
    private String generateLocalAnalysis(ArticleData article, List<ArticleData> allArticles) {
        StringBuilder analysis = new StringBuilder();
        analysis.append("# 🤖 本地智能分析报告\n");
        analysis.append("> AI服务未启用，以下为基于规则的分析结果\n\n");
        analysis.append("---\n\n");

        // 1. 数据诊断
        analysis.append("## 📊 数据诊断\n\n");
        
        long readCount = article.getReadCount7d() != null ? article.getReadCount7d() : 0;
        long interactionCount = article.getInteractionCount7d() != null ? article.getInteractionCount7d() : 0;
        double interactionRate = readCount > 0 ? (double) interactionCount / readCount * 100 : 0;
        
        double avgRead = allArticles.stream()
            .filter(a -> a.getReadCount7d() != null)
            .mapToLong(ArticleData::getReadCount7d)
            .average().orElse(0);
            
        analysis.append(String.format("- **7天阅读量**: %,d（平均: %.0f）\n", readCount, avgRead));
        analysis.append(String.format("- **互动率**: %.2f%%\n", interactionRate));
        
        if (readCount > avgRead * 1.5) {
            analysis.append("- ✅ 阅读量表现优秀，高于平均水平50%以上\n");
        } else if (readCount < avgRead * 0.5) {
            analysis.append("- ⚠️ 阅读量偏低，需要重点优化\n");
        }
        
        if (interactionRate > 5) {
            analysis.append("- ✅ 互动率良好\n");
        } else if (interactionRate < 2) {
            analysis.append("- ⚠️ 互动率偏低，需要增加互动引导\n");
        }
        analysis.append("\n");

        // 2. 标题分析
        analysis.append("## 📝 标题分析\n\n");
        String title = article.getTitle();
        if (title != null) {
            analysis.append(String.format("当前标题: 「%s」（%d字）\n\n", title, title.length()));
            
            if (title.length() < 12) {
                analysis.append("- ⚠️ 标题过短，信息量不足\n");
                analysis.append("- 💡 建议扩展至15-25字，补充具体卖点\n");
            } else if (title.length() > 30) {
                analysis.append("- ⚠️ 标题过长，可能影响阅读\n");
                analysis.append("- 💡 建议精简至15-25字\n");
            } else {
                analysis.append("- ✅ 标题长度合适\n");
            }
            
            boolean hasEmotional = title.matches(".*(绝了|必买|好用|值得|推荐|神器|爆款).*");
            if (!hasEmotional) {
                analysis.append("- 💡 建议添加情感词，如「绝了」「必买」「值得」\n");
            }
            
            boolean hasNumber = title.matches(".*\\d+.*");
            if (!hasNumber) {
                analysis.append("- 💡 建议添加数字增强可信度，如「3个技巧」「7天见效」\n");
            }
        }
        analysis.append("\n");

        // 3. 平台建议
        analysis.append("## 📱 平台优化建议\n\n");
        String platform = article.getPlatform() != null ? article.getPlatform() : article.getMaterialSource();
        if (platform != null) {
            if (platform.contains("得物") || platform.contains("新媒体图文")) {
                analysis.append("### 得物平台特点\n");
                analysis.append("- 重视产品展示和上脚效果\n");
                analysis.append("- 图片质量要求高，建议使用专业拍摄\n");
                analysis.append("- 发布时间：工作日19-21点，周末14-16点效果最佳\n");
                analysis.append("- 标签策略：使用热门话题+品牌标签\n");
            } else if (platform.contains("小红书")) {
                analysis.append("### 小红书平台特点\n");
                analysis.append("- 强调真实体验和种草感\n");
                analysis.append("- 封面图要有冲击力\n");
                analysis.append("- 发布时间：午间12-13点，晚间18-20点效果最佳\n");
                analysis.append("- 标签策略：热门话题+细分标签+地域标签\n");
            }
        }
        analysis.append("\n");

        // 4. 行动建议
        analysis.append("## 🎯 立即行动清单\n\n");
        if ("BAD_ANOMALY".equals(article.getAnomalyStatus())) {
            analysis.append("内容表现较差，建议重点优化：\n\n");
            analysis.append("- [ ] 重写标题，突出核心卖点\n");
            analysis.append("- [ ] 更换封面图，提升吸引力\n");
            analysis.append("- [ ] 添加互动引导语\n");
            analysis.append("- [ ] 选择最佳时间重新发布\n");
            analysis.append("- [ ] 添加热门话题标签\n");
        } else if ("GOOD_ANOMALY".equals(article.getAnomalyStatus())) {
            analysis.append("内容表现优秀，建议复制成功经验：\n\n");
            analysis.append("- [ ] 分析成功要素，记录到内容库\n");
            analysis.append("- [ ] 制作同系列内容\n");
            analysis.append("- [ ] 保持相似的发布时间\n");
            analysis.append("- [ ] 总结用户评论中的亮点\n");
        } else {
            analysis.append("内容表现正常，可进行以下优化：\n\n");
            analysis.append("- [ ] 优化标题吸引力\n");
            analysis.append("- [ ] 增强内容互动性\n");
            analysis.append("- [ ] 尝试不同发布时间\n");
        }
        
        analysis.append("\n---\n");
        analysis.append("💡 **提示**: 通义千问AI服务可获得更详细的个性化分析和建议\n");

        return analysis.toString();
    }
}