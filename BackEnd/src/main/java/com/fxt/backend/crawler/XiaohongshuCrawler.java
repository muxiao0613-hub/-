package com.fxt.backend.crawler;

import com.fxt.backend.entity.ArticleData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 小红书平台爬虫
 * 支持小红书笔记数据采集
 */
@Component
public class XiaohongshuCrawler extends BaseCrawler {
    
    private static final String PLATFORM_NAME = "小红书";
    
    // 小红书移动端User-Agent
    private static final String USER_AGENT = 
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1";
    
    // 小红书Web端User-Agent（备用）
    private static final String WEB_USER_AGENT = 
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    
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
            
            // 尝试多种方式获取内容
            boolean success = false;
            
            // 方式1：直接访问H5页面
            success = crawlFromH5Page(article, noteId);
            
            // 方式2：如果H5失败，尝试Web API
            if (!success) {
                success = crawlFromWebApi(article, noteId);
            }
            
            // 方式3：如果都失败，尝试直接解析页面
            if (!success) {
                success = crawlFromDirectPage(article);
            }
            
            if (success) {
                updateCrawlStatus(article, "SUCCESS", "爬取成功");
            } else {
                updateCrawlStatus(article, "PARTIAL", "部分内容获取成功");
            }
            
        } catch (Exception e) {
            updateCrawlStatus(article, "ERROR", "爬取异常: " + e.getMessage());
        }
        
        // 添加随机延迟
        randomDelay();
        
        return article;
    }
    
    /**
     * 从链接提取笔记ID
     * 支持多种链接格式：
     * - https://www.xiaohongshu.com/explore/笔记ID
     * - https://www.xiaohongshu.com/discovery/item/笔记ID
     * - https://xhslink.com/短链接
     * - http://xhslink.com/a/短链接
     */
    private String extractNoteId(String postLink) {
        if (postLink == null || postLink.isEmpty()) {
            return null;
        }
        
        // 标准链接格式
        Pattern standardPattern = Pattern.compile("xiaohongshu\\.com/(?:explore|discovery/item)/([a-zA-Z0-9]+)");
        Matcher standardMatcher = standardPattern.matcher(postLink);
        if (standardMatcher.find()) {
            return standardMatcher.group(1);
        }
        
        // 短链接格式 - 需要先解析重定向
        if (postLink.contains("xhslink.com")) {
            return resolveShortLink(postLink);
        }
        
        // 尝试从URL末尾提取ID
        Pattern idPattern = Pattern.compile("/([a-zA-Z0-9]{24})(?:\\?|$)");
        Matcher idMatcher = idPattern.matcher(postLink);
        if (idMatcher.find()) {
            return idMatcher.group(1);
        }
        
        return null;
    }
    
    /**
     * 解析短链接获取真实笔记ID
     */
    private String resolveShortLink(String shortUrl) {
        try {
            // 发送请求获取重定向后的URL
            String response = makeRequestWithRedirect(shortUrl);
            if (response != null) {
                Pattern pattern = Pattern.compile("/(?:explore|discovery/item)/([a-zA-Z0-9]+)");
                Matcher matcher = pattern.matcher(response);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            System.err.println("解析短链接失败: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * 发送请求并跟踪重定向
     */
    private String makeRequestWithRedirect(String url) {
        try {
            return webClient.get()
                .uri(url)
                .header("User-Agent", USER_AGENT)
                .exchangeToMono(response -> {
                    if (response.statusCode().is3xxRedirection()) {
                        String location = response.headers().asHttpHeaders().getFirst("Location");
                        return reactor.core.publisher.Mono.just(location != null ? location : "");
                    }
                    return response.bodyToMono(String.class);
                })
                .block();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 方式1：从H5页面获取内容
     */
    private boolean crawlFromH5Page(ArticleData article, String noteId) {
        try {
            String h5Url = "https://www.xiaohongshu.com/explore/" + noteId;
            String html = makeRequest(h5Url, USER_AGENT);
            
            if (html == null || html.isEmpty()) {
                return false;
            }
            
            // 从HTML中提取JSON数据
            return parseH5Html(article, html);
            
        } catch (Exception e) {
            System.err.println("H5页面爬取失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 解析H5页面HTML
     */
    private boolean parseH5Html(ArticleData article, String html) {
        try {
            // 小红书会在页面中嵌入JSON数据
            // 查找 window.__INITIAL_STATE__ 或类似的数据
            Pattern jsonPattern = Pattern.compile("window\\.__INITIAL_STATE__\\s*=\\s*(\\{.*?\\})\\s*;?\\s*</script>", 
                Pattern.DOTALL);
            Matcher matcher = jsonPattern.matcher(html);
            
            if (matcher.find()) {
                String jsonStr = matcher.group(1);
                // 处理Unicode转义
                jsonStr = decodeUnicode(jsonStr);
                
                JsonNode rootNode = objectMapper.readTree(jsonStr);
                return extractDataFromJson(article, rootNode);
            }
            
            // 备用方案：直接解析HTML
            return parseHtmlContent(article, html);
            
        } catch (Exception e) {
            System.err.println("解析H5 HTML失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 从JSON中提取数据
     */
    private boolean extractDataFromJson(ArticleData article, JsonNode rootNode) {
        try {
            JsonNode noteData = findNoteData(rootNode);
            
            if (noteData != null) {
                // 提取标题
                if (noteData.has("title") && article.getTitle() == null) {
                    article.setTitle(noteData.get("title").asText());
                }
                
                // 提取内容描述
                if (noteData.has("desc")) {
                    article.setContent(noteData.get("desc").asText());
                } else if (noteData.has("noteCard") && noteData.get("noteCard").has("desc")) {
                    article.setContent(noteData.get("noteCard").get("desc").asText());
                }
                
                // 提取图片列表
                List<String> imageUrls = extractImages(noteData);
                if (!imageUrls.isEmpty()) {
                    article.setImagesInfo(objectMapper.writeValueAsString(imageUrls));
                }
                
                // 提取互动数据（如果有）
                extractInteractionData(article, noteData);
                
                return true;
            }
            
            return false;
        } catch (Exception e) {
            System.err.println("从JSON提取数据失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 递归查找笔记数据节点
     */
    private JsonNode findNoteData(JsonNode node) {
        if (node.has("note")) {
            return node.get("note");
        }
        if (node.has("noteDetailMap")) {
            JsonNode detailMap = node.get("noteDetailMap");
            if (detailMap.isObject() && detailMap.size() > 0) {
                return detailMap.iterator().next();
            }
        }
        if (node.has("noteData")) {
            return node.get("noteData");
        }
        
        // 递归搜索
        for (JsonNode child : node) {
            if (child.isObject()) {
                JsonNode found = findNoteData(child);
                if (found != null) {
                    return found;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 提取图片URL列表
     */
    private List<String> extractImages(JsonNode noteData) {
        List<String> imageUrls = new ArrayList<>();
        
        try {
            // 尝试多种路径获取图片
            JsonNode imageList = null;
            
            if (noteData.has("imageList")) {
                imageList = noteData.get("imageList");
            } else if (noteData.has("images")) {
                imageList = noteData.get("images");
            } else if (noteData.has("noteCard") && noteData.get("noteCard").has("imageList")) {
                imageList = noteData.get("noteCard").get("imageList");
            }
            
            if (imageList != null && imageList.isArray()) {
                for (JsonNode img : imageList) {
                    String url = null;
                    
                    // 优先获取高清图
                    if (img.has("urlDefault")) {
                        url = img.get("urlDefault").asText();
                    } else if (img.has("url")) {
                        url = img.get("url").asText();
                    } else if (img.has("infoList") && img.get("infoList").isArray()) {
                        // 获取最大尺寸的图片
                        JsonNode infoList = img.get("infoList");
                        for (JsonNode info : infoList) {
                            if (info.has("url")) {
                                url = info.get("url").asText();
                                break;
                            }
                        }
                    }
                    
                    if (url != null && !url.isEmpty()) {
                        // 确保URL是完整的
                        if (!url.startsWith("http")) {
                            url = "https:" + url;
                        }
                        imageUrls.add(url);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("提取图片失败: " + e.getMessage());
        }
        
        return imageUrls;
    }
    
    /**
     * 提取互动数据
     */
    private void extractInteractionData(ArticleData article, JsonNode noteData) {
        try {
            JsonNode interactInfo = noteData.has("interactInfo") ? 
                noteData.get("interactInfo") : noteData;
            
            // 点赞数
            if (interactInfo.has("likedCount")) {
                String likedCount = interactInfo.get("likedCount").asText();
                article.setInteractionCount7d(parseCount(likedCount));
            }
            
            // 收藏数
            if (interactInfo.has("collectedCount")) {
                String collectedCount = interactInfo.get("collectedCount").asText();
                article.setProductWant7d(parseCount(collectedCount));
            }
            
            // 评论数
            if (interactInfo.has("commentCount")) {
                String commentCount = interactInfo.get("commentCount").asText();
                article.setShareCount7d(parseCount(commentCount));
            }
            
        } catch (Exception e) {
            System.err.println("提取互动数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 解析数量字符串（支持 "1.2万" 这样的格式）
     */
    private Long parseCount(String countStr) {
        if (countStr == null || countStr.isEmpty()) {
            return 0L;
        }
        
        try {
            countStr = countStr.trim();
            
            if (countStr.contains("万")) {
                double num = Double.parseDouble(countStr.replace("万", ""));
                return (long) (num * 10000);
            } else if (countStr.contains("亿")) {
                double num = Double.parseDouble(countStr.replace("亿", ""));
                return (long) (num * 100000000);
            } else {
                return Long.parseLong(countStr.replaceAll("[^0-9]", ""));
            }
        } catch (Exception e) {
            return 0L;
        }
    }
    
    /**
     * 方式2：从Web API获取内容
     */
    private boolean crawlFromWebApi(ArticleData article, String noteId) {
        try {
            // 小红书Web API（需要登录态，这里尝试公开接口）
            String apiUrl = "https://www.xiaohongshu.com/api/sns/web/v1/note/" + noteId;
            
            String response = webClient.get()
                .uri(apiUrl)
                .header("User-Agent", WEB_USER_AGENT)
                .header("Accept", "application/json")
                .header("Referer", "https://www.xiaohongshu.com/")
                .header("Origin", "https://www.xiaohongshu.com")
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            if (response != null && !response.isEmpty()) {
                JsonNode jsonNode = objectMapper.readTree(response);
                
                if (jsonNode.has("data")) {
                    return extractDataFromJson(article, jsonNode.get("data"));
                }
            }
            
            return false;
        } catch (Exception e) {
            System.err.println("Web API爬取失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 方式3：直接解析页面HTML
     */
    private boolean crawlFromDirectPage(ArticleData article) {
        try {
            String url = article.getArticleLink();
            if (url == null || url.isEmpty()) {
                return false;
            }
            
            String html = makeRequest(url, WEB_USER_AGENT);
            if (html == null || html.isEmpty()) {
                return false;
            }
            
            return parseHtmlContent(article, html);
            
        } catch (Exception e) {
            System.err.println("直接页面解析失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 直接解析HTML内容（兜底方案）
     */
    private boolean parseHtmlContent(ArticleData article, String html) {
        try {
            // 使用Jsoup解析
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
            
            // 提取标题
            org.jsoup.nodes.Element titleElement = doc.selectFirst("meta[property=og:title]");
            if (titleElement != null && article.getTitle() == null) {
                article.setTitle(titleElement.attr("content"));
            }
            
            // 提取描述内容
            org.jsoup.nodes.Element descElement = doc.selectFirst("meta[property=og:description]");
            if (descElement != null) {
                article.setContent(descElement.attr("content"));
            }
            
            // 提取图片
            List<String> imageUrls = new ArrayList<>();
            org.jsoup.select.Elements imgElements = doc.select("meta[property=og:image]");
            for (org.jsoup.nodes.Element img : imgElements) {
                String url = img.attr("content");
                if (url != null && !url.isEmpty()) {
                    imageUrls.add(url);
                }
            }
            
            if (!imageUrls.isEmpty()) {
                article.setImagesInfo(objectMapper.writeValueAsString(imageUrls));
            }
            
            // 如果有内容说明解析成功
            return article.getContent() != null && !article.getContent().isEmpty();
            
        } catch (Exception e) {
            System.err.println("HTML内容解析失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 解码Unicode转义字符
     */
    private String decodeUnicode(String str) {
        Pattern pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        Matcher matcher = pattern.matcher(str);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            int charCode = Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(sb, String.valueOf((char) charCode));
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
}