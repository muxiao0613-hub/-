package com.fxt.backend.crawler;

import com.fxt.backend.entity.ArticleData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 得物平台爬虫
 * 支持得物帖子数据采集
 */
@Component
public class DewuCrawler extends BaseCrawler {
    
    private static final String PLATFORM_NAME = "得物";
    private static final String USER_AGENT = 
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 DuApp/5.24.5";
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    protected void setupHeaders() {
        // 得物特定的请求头在makeRequest中设置
    }
    
    @Override
    public String getPlatformName() {
        return PLATFORM_NAME;
    }
    
    @Override
    public ArticleData crawl(ArticleData article) {
        try {
            String trendId = extractTrendId(article.getArticleLink());
            if (trendId == null) {
                updateCrawlStatus(article, "FAILED", "无法从链接提取trendId");
                return article;
            }
            
            // 构建API请求URL
            String apiUrl = buildApiUrl(trendId);
            
            // 发送请求
            String response = makeRequest(apiUrl, USER_AGENT);
            
            // 解析响应数据
            parseResponse(article, response);
            
            updateCrawlStatus(article, "SUCCESS", "爬取成功");
            
        } catch (Exception e) {
            updateCrawlStatus(article, "ERROR", "爬取异常: " + e.getMessage());
        }
        
        // 添加随机延迟
        randomDelay();
        
        return article;
    }
    
    /**
     * 从链接提取trendId
     */
    private String extractTrendId(String postLink) {
        if (postLink == null || postLink.isEmpty()) {
            return null;
        }
        
        Pattern pattern = Pattern.compile("trendId=(\\d+)");
        Matcher matcher = pattern.matcher(postLink);
        
        return matcher.find() ? matcher.group(1) : null;
    }
    
    /**
     * 构建API请求URL
     */
    private String buildApiUrl(String trendId) {
        long timestamp = System.currentTimeMillis();
        String sign = generateSign(trendId, timestamp);
        
        return String.format(
            "https://app.poizon.com/api/v1/h5/community/trend/detail?trendId=%s&timestamp=%d&sign=%s",
            trendId, timestamp, sign
        );
    }
    
    /**
     * 生成签名（简化版本）
     */
    private String generateSign(String trendId, long timestamp) {
        try {
            String signStr = String.format("trendId=%s&timestamp=%d", trendId, timestamp);
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(signStr.getBytes());
            
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "default_sign";
        }
    }
    
    /**
     * 解析API响应数据
     */
    private void parseResponse(ArticleData article, String response) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            
            if (jsonNode.path("code").asInt() == 200) {
                JsonNode data = jsonNode.path("data");
                
                // 更新数据字段
                if (data.has("viewCount")) {
                    article.setReadCount7d((long) data.path("viewCount").asInt());
                }
                
                if (data.has("interactionCount")) {
                    article.setInteractionCount7d((long) data.path("interactionCount").asInt());
                }
                
                if (data.has("shareCount")) {
                    article.setShareCount7d((long) data.path("shareCount").asInt());
                }
                
                if (data.has("productVisitCount")) {
                    article.setProductVisit7d((long) data.path("productVisitCount").asInt());
                }
                
                if (data.has("productWantCount")) {
                    article.setProductWant7d((long) data.path("productWantCount").asInt());
                }
                
                // 如果有内容信息，也可以更新
                if (data.has("content") && article.getContent() == null) {
                    article.setContent(data.path("content").asText());
                }
                
            } else {
                throw new RuntimeException("API返回错误: " + jsonNode.path("msg").asText("未知错误"));
            }
            
        } catch (Exception e) {
            throw new RuntimeException("解析响应数据失败: " + e.getMessage(), e);
        }
    }
}