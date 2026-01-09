package com.fxt.backend.crawler;

import com.fxt.backend.entity.ArticleData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 小红书平台爬虫
 * 支持小红书笔记数据采集
 */
@Component
public class XiaohongshuCrawler extends BaseCrawler {
    
    private static final String PLATFORM_NAME = "小红书";
    private static final String USER_AGENT = 
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1";
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    protected void setupHeaders() {
        // 小红书特定的请求头在makeRequest中设置
    }
    
    @Override
    public String getPlatformName() {
        return PLATFORM_NAME;
    }
    
    @Override
    public ArticleData crawl(ArticleData article) {
        try {
            String noteId = extractNoteId(article.getArticleLink());
            if (noteId == null) {
                updateCrawlStatus(article, "FAILED", "无法从链接提取笔记ID");
                return article;
            }
            
            // 构建API请求URL
            String apiUrl = buildApiUrl(noteId);
            
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
     * 从链接提取笔记ID
     */
    private String extractNoteId(String postLink) {
        if (postLink == null || postLink.isEmpty()) {
            return null;
        }
        
        // 小红书链接格式匹配
        Pattern[] patterns = {
            Pattern.compile("xiaohongshu\\.com/(?:explore|discovery/item)/([a-zA-Z0-9]+)"),
            Pattern.compile("xhslink\\.com/([a-zA-Z0-9]+)"),
            Pattern.compile("note/([a-zA-Z0-9]+)"),
            // 兼容得物链接（用于测试数据）
            Pattern.compile("trendId=(\\d+)")
        };
        
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(postLink);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        return null;
    }
    
    /**
     * 构建API请求URL
     */
    private String buildApiUrl(String noteId) {
        // 生成签名参数
        String xs = generateXs();
        long xt = System.currentTimeMillis();
        
        return String.format(
            "https://edith.xiaohongshu.com/api/sns/web/v1/feed?source_note_id=%s&image_scenes=FD_PRV_WEBP,FD_WM_WEBP&x-s=%s&x-t=%d",
            noteId, xs, xt
        );
    }
    
    /**
     * 生成x-s签名（简化版本）
     */
    private String generateXs() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        
        for (int i = 0; i < 44; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return sb.toString();
    }
    
    /**
     * 解析API响应数据
     */
    private void parseResponse(ArticleData article, String response) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            
            if (jsonNode.path("success").asBoolean()) {
                JsonNode items = jsonNode.path("data").path("items");
                
                if (items.isArray() && items.size() > 0) {
                    JsonNode noteCard = items.get(0).path("note_card");
                    JsonNode interactInfo = noteCard.path("interact_info");
                    
                    // 更新数据字段
                    if (interactInfo.has("view_count")) {
                        article.setReadCount7d((long) interactInfo.path("view_count").asInt());
                    }
                    
                    // 计算总互动数（点赞+评论+收藏）
                    int likedCount = interactInfo.path("liked_count").asInt();
                    int commentCount = interactInfo.path("comment_count").asInt();
                    int collectedCount = interactInfo.path("collected_count").asInt();
                    article.setInteractionCount7d((long) (likedCount + commentCount + collectedCount));
                    
                    if (interactInfo.has("share_count")) {
                        article.setShareCount7d((long) interactInfo.path("share_count").asInt());
                    }
                    
                    // 如果有内容信息，也可以更新
                    JsonNode desc = noteCard.path("desc");
                    if (desc.isTextual() && article.getContent() == null) {
                        article.setContent(desc.asText());
                    }
                }
                
            } else {
                throw new RuntimeException("API返回错误: " + jsonNode.path("msg").asText("未知错误"));
            }
            
        } catch (Exception e) {
            throw new RuntimeException("解析响应数据失败: " + e.getMessage(), e);
        }
    }
}