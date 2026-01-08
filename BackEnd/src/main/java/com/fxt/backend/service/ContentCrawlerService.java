package com.fxt.backend.service;

import com.fxt.backend.entity.ArticleData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ContentCrawlerService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // 移动端User-Agent (iPhone Safari)
    private static final String MOBILE_USER_AGENT = 
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

    public ContentCrawlerService() {
        this.webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 爬取文章内容 - 只提取文字和图片URL，不下载图片
     */
    public void crawlAllContent(ArticleData article) {
        article.setCrawlStatus("PENDING");

        String articleLink = article.getArticleLink();

        // 检查链接是否有效
        if (articleLink == null || articleLink.trim().isEmpty()) {
            article.setContent("无链接信息");
            article.setCrawlStatus("FAILED");
            article.setCrawlError("文章链接为空");
            return;
        }

        // 清理链接格式
        articleLink = articleLink.trim();
        if (!articleLink.startsWith("http://") && !articleLink.startsWith("https://")) {
            if (articleLink.startsWith("www.")) {
                articleLink = "https://" + articleLink;
            } else {
                article.setContent("链接格式错误: " + articleLink);
                article.setCrawlStatus("FAILED");
                article.setCrawlError("链接格式不正确，需要以http://或https://开头");
                return;
            }
        }

        try {
            System.out.println("开始抓取内容: " + articleLink);
            
            // 获取HTML内容
            String html = fetchHtmlContent(articleLink);
            
            if (html != null && !html.trim().isEmpty()) {
                // 解析HTML
                Document doc = Jsoup.parse(html);
                
                // 提取文字内容
                String textContent = extractTextContent(doc, html);
                
                // 提取图片URL列表
                List<String> imageUrls = extractImageUrls(doc, html);
                
                // 构建返回结构
                Map<String, Object> result = new HashMap<>();
                result.put("title", article.getTitle());
                result.put("content", textContent);
                result.put("imageUrls", imageUrls);
                
                // 保存结果
                article.setContent(textContent);
                String imagesJson = objectMapper.writeValueAsString(imageUrls);
                article.setImagesInfo(imagesJson);
                
                article.setCrawlStatus("SUCCESS");
                article.setCrawlError(null);
                System.out.println("内容抓取成功，文字长度: " + textContent.length() + ", 图片数量: " + imageUrls.size());
            } else {
                // 内容为空，使用标题作为备选
                String fallbackContent = "标题: " + (article.getTitle() != null ? article.getTitle() : "无标题");
                fallbackContent += "\n链接: " + articleLink;
                fallbackContent += "\n注意: 无法抓取到文章内容，可能是网站有反爬虫保护";

                article.setContent(fallbackContent);
                article.setCrawlStatus("PARTIAL");
                article.setCrawlError("抓取到的内容为空，使用标题作为替代");
                article.setImagesInfo("[]");
                System.out.println("内容抓取为空，使用备选内容");
            }
        } catch (Exception e) {
            // 抓取失败，提供详细的错误信息和备选内容
            String fallbackContent = "标题: " + (article.getTitle() != null ? article.getTitle() : "无标题");
            fallbackContent += "\n链接: " + articleLink;
            fallbackContent += "\n品牌: " + (article.getBrand() != null ? article.getBrand() : "未知");
            fallbackContent += "\n内容类型: " + (article.getContentType() != null ? article.getContentType() : "未知");
            fallbackContent += "\n\n抓取失败原因: " + e.getMessage();
            fallbackContent += "\n\n建议: 请手动查看原文链接获取完整内容";

            article.setContent(fallbackContent);
            article.setCrawlStatus("FAILED");
            article.setCrawlError("抓取失败: " + e.getMessage());
            article.setImagesInfo("[]");

            System.err.println("内容抓取失败 - 文章: " + article.getTitle() +
                              ", 链接: " + articleLink +
                              ", 错误: " + e.getMessage());
        }
    }

    /**
     * 使用HTTP GET请求获取HTML内容
     */
    private String fetchHtmlContent(String url) {
        try {
            return webClient.get()
                .uri(url)
                .header("User-Agent", MOBILE_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(15))
                .block();
        } catch (Exception e) {
            System.err.println("获取HTML失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 提取文字内容 - 按照指定优先级顺序
     */
    private String extractTextContent(Document doc, String html) {
        StringBuilder content = new StringBuilder();

        // 1. 正文容器（优先）
        String[] contentSelectors = {
            ".content-detail",
            ".note-content", 
            ".article-content",
            ".post-content",
            "article",
            "main"
        };

        for (String selector : contentSelectors) {
            Elements elements = doc.select(selector);
            for (Element element : elements) {
                String text = element.text().trim();
                if (text.length() > 50) { // 过滤短文本
                    content.append(text).append("\n\n");
                }
            }
            if (content.length() > 100) {
                break; // 找到足够内容就停止
            }
        }

        // 2. 段落兜底
        if (content.length() < 100) {
            Elements paragraphs = doc.select("p");
            for (Element p : paragraphs) {
                String text = p.text().trim();
                if (text.length() > 20) { // 过滤短段落
                    content.append(text).append("\n");
                }
            }
        }

        // 3. script JSON 兜底（得物重点）
        if (content.length() < 100) {
            String scriptContent = extractFromScriptJson(html);
            if (!scriptContent.isEmpty()) {
                content.append(scriptContent);
            }
        }

        String result = content.toString().trim();
        
        // 清理和限制长度
        result = result.replaceAll("\\s+", " ").trim();
        
        if (result.length() > 8000) {
            result = result.substring(0, 8000) + "...\n\n[内容已截断，完整内容请查看原文链接]";
        }

        return result.isEmpty() ? "未能提取到文字内容" : result;
    }

    /**
     * 从script中解析连续中文文本（得物重点）
     */
    private String extractFromScriptJson(String html) {
        StringBuilder content = new StringBuilder();
        
        // 匹配script标签中的JSON数据
        Pattern scriptPattern = Pattern.compile("<script[^>]*>(.*?)</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher scriptMatcher = scriptPattern.matcher(html);
        
        while (scriptMatcher.find()) {
            String scriptContent = scriptMatcher.group(1);
            
            // 尝试解析JSON
            try {
                if (scriptContent.trim().startsWith("{") || scriptContent.trim().startsWith("[")) {
                    JsonNode jsonNode = objectMapper.readTree(scriptContent);
                    extractChineseTextFromJson(jsonNode, content);
                }
            } catch (Exception e) {
                // 如果不是JSON，直接提取中文文本
                extractChineseTextDirect(scriptContent, content);
            }
        }
        
        return content.toString().trim();
    }

    /**
     * 从JSON节点中递归提取中文文本
     */
    private void extractChineseTextFromJson(JsonNode node, StringBuilder content) {
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (isChineseText(text) && text.length() > 10) {
                content.append(text).append("\n");
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                extractChineseTextFromJson(item, content);
            }
        } else if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                extractChineseTextFromJson(entry.getValue(), content);
            });
        }
    }

    /**
     * 直接从文本中提取中文内容
     */
    private void extractChineseTextDirect(String text, StringBuilder content) {
        // 匹配连续的中文文本
        Pattern chinesePattern = Pattern.compile("[\\u4e00-\\u9fa5][\\u4e00-\\u9fa5\\s，。！？；：\"\"''（）【】]{10,}");
        Matcher matcher = chinesePattern.matcher(text);
        
        while (matcher.find()) {
            String chineseText = matcher.group().trim();
            if (chineseText.length() > 10) {
                content.append(chineseText).append("\n");
            }
        }
    }

    /**
     * 判断是否为中文文本
     */
    private boolean isChineseText(String text) {
        if (text == null || text.length() < 5) return false;
        
        int chineseCount = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x4e00 && c <= 0x9fa5) {
                chineseCount++;
            }
        }
        
        return chineseCount > text.length() * 0.3; // 中文字符占比超过30%
    }

    /**
     * 提取图片URL列表 - 覆盖所有情况
     */
    private List<String> extractImageUrls(Document doc, String html) {
        Set<String> imageUrls = new LinkedHashSet<>();

        // 1. img标签
        extractFromImgTags(doc, imageUrls);
        
        // 2. background-image
        extractFromBackgroundImages(doc, imageUrls);
        
        // 3. script中的图片URL（得物关键）
        extractFromScriptImages(html, imageUrls);

        // 4. 图片过滤和处理
        List<String> filteredUrls = new ArrayList<>();
        for (String url : imageUrls) {
            String processedUrl = processImageUrl(url);
            if (processedUrl != null && isValidContentImage(processedUrl)) {
                filteredUrls.add(processedUrl);
            }
        }

        return filteredUrls;
    }

    /**
     * 从img标签提取图片URL
     */
    private void extractFromImgTags(Document doc, Set<String> imageUrls) {
        Elements images = doc.select("img");
        
        for (Element img : images) {
            // 按优先级获取图片URL
            String url = null;
            
            // data-original (懒加载)
            if (url == null || url.isEmpty()) {
                url = img.attr("data-original");
            }
            
            // data-src (懒加载)
            if (url == null || url.isEmpty()) {
                url = img.attr("data-src");
            }
            
            // lazy-src
            if (url == null || url.isEmpty()) {
                url = img.attr("lazy-src");
            }
            
            // srcset (取最大尺寸)
            if (url == null || url.isEmpty()) {
                String srcset = img.attr("srcset");
                if (!srcset.isEmpty()) {
                    url = extractLargestFromSrcset(srcset);
                }
            }
            
            // src
            if (url == null || url.isEmpty()) {
                url = img.attr("src");
            }
            
            if (url != null && !url.isEmpty()) {
                imageUrls.add(url);
            }
        }
    }

    /**
     * 从srcset中提取最大尺寸的图片
     */
    private String extractLargestFromSrcset(String srcset) {
        String[] sources = srcset.split(",");
        String largestUrl = null;
        int maxWidth = 0;
        
        for (String source : sources) {
            source = source.trim();
            String[] parts = source.split("\\s+");
            if (parts.length >= 2) {
                String url = parts[0];
                String descriptor = parts[1];
                
                if (descriptor.endsWith("w")) {
                    try {
                        int width = Integer.parseInt(descriptor.substring(0, descriptor.length() - 1));
                        if (width > maxWidth) {
                            maxWidth = width;
                            largestUrl = url;
                        }
                    } catch (NumberFormatException e) {
                        // 忽略
                    }
                }
            }
        }
        
        return largestUrl != null ? largestUrl : (sources.length > 0 ? sources[0].split("\\s+")[0] : null);
    }

    /**
     * 从background-image提取图片URL
     */
    private void extractFromBackgroundImages(Document doc, Set<String> imageUrls) {
        Elements elementsWithBg = doc.select("[style*=background-image]");
        
        for (Element element : elementsWithBg) {
            String style = element.attr("style");
            String bgUrl = extractUrlFromStyle(style);
            if (bgUrl != null) {
                imageUrls.add(bgUrl);
            }
        }
    }

    /**
     * 从style属性中提取URL
     */
    private String extractUrlFromStyle(String style) {
        Pattern pattern = Pattern.compile("background-image\\s*:\\s*url\\s*\\(\\s*['\"]?([^'\"\\)]+)['\"]?\\s*\\)");
        Matcher matcher = pattern.matcher(style);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }

    /**
     * 从script中提取图片URL（得物关键）
     */
    private void extractFromScriptImages(String html, Set<String> imageUrls) {
        // 匹配得物CDN图片URL
        Pattern dewuPattern = Pattern.compile("(https?://[^\\s\"']*(?:cdn\\.poizon|cdn\\.dewu)[^\\s\"']*\\.(?:webp|jpg|jpeg|png))", Pattern.CASE_INSENSITIVE);
        Matcher matcher = dewuPattern.matcher(html);
        
        while (matcher.find()) {
            String url = matcher.group(1);
            imageUrls.add(url);
        }
        
        // 通用图片URL匹配
        Pattern generalPattern = Pattern.compile("(https?://[^\\s\"']*\\.(?:webp|jpg|jpeg|png))", Pattern.CASE_INSENSITIVE);
        Matcher generalMatcher = generalPattern.matcher(html);
        
        while (generalMatcher.find()) {
            String url = generalMatcher.group(1);
            imageUrls.add(url);
        }
    }

    /**
     * 处理图片URL - 去掉压缩参数，拿原图URL
     */
    private String processImageUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        url = url.trim();
        
        // 处理相对URL
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        
        // 去掉压缩参数，拿原图
        int paramIndex = url.indexOf("?x-oss-process=");
        if (paramIndex > 0) {
            url = url.substring(0, paramIndex);
        }
        
        // 去掉其他压缩参数
        paramIndex = url.indexOf("?imageView");
        if (paramIndex > 0) {
            url = url.substring(0, paramIndex);
        }
        
        return url;
    }

    /**
     * 图片过滤规则 - 过滤icon/logo/avatar/loading/占位图
     */
    private boolean isValidContentImage(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        String lowerUrl = url.toLowerCase();
        
        // 过滤规则
        String[] filterKeywords = {
            "icon", "logo", "avatar", "loading", "placeholder",
            "thumb", "1x1", "pixel", "empty", "default",
            "16x16", "32x32", "64x64", "favicon"
        };
        
        for (String keyword : filterKeywords) {
            if (lowerUrl.contains(keyword)) {
                return false;
            }
        }
        
        // 必须是有效的图片URL
        return lowerUrl.matches(".*\\.(webp|jpg|jpeg|png).*");
    }
}