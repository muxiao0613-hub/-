package com.fxt.backend.service;

import com.fxt.backend.dto.ImageInfo;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

@Service
public class PlaywrightCrawlerService {
    
    private static final Logger logger = LoggerFactory.getLogger(PlaywrightCrawlerService.class);
    
    private Playwright playwright;
    private Browser browser;
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(5);
    private final String downloadBasePath = "downloads/images/";
    
    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
    };
    
    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(downloadBasePath));
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(Arrays.asList(
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-gpu"
                )));
            logger.info("Playwright 浏览器初始化成功");
        } catch (Exception e) {
            logger.error("Playwright 初始化失败: {}", e.getMessage());
        }
    }
    
    @PreDestroy
    public void cleanup() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
        downloadExecutor.shutdown();
    }
    
    /**
     * 爬取得物文章内容和图片
     */
    public CrawlResult crawlDewuArticle(String url, String articleId) {
        CrawlResult result = new CrawlResult();
        result.setSourceUrl(url);
        result.setArticleId(articleId);
        
        if (browser == null) {
            result.setSuccess(false);
            result.setError("浏览器未初始化");
            return result;
        }
        
        BrowserContext context = null;
        Page page = null;
        
        try {
            // 创建浏览器上下文（模拟移动端获取更好的内容）
            context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(USER_AGENTS[0])
                .setViewportSize(390, 844)
                .setDeviceScaleFactor(3)
                .setIsMobile(true)
                .setHasTouch(true));
            
            page = context.newPage();
            
            // 监听网络请求，捕获图片URL
            Set<String> imageUrls = Collections.synchronizedSet(new LinkedHashSet<>());
            page.onResponse(response -> {
                String responseUrl = response.url();
                String contentType = response.headers().get("content-type");
                if (contentType != null && contentType.startsWith("image/")) {
                    if (isValidContentImage(responseUrl)) {
                        imageUrls.add(responseUrl);
                    }
                }
            });
            
            // 访问页面
            logger.info("开始爬取: {}", url);
            page.navigate(url, new Page.NavigateOptions()
                .setTimeout(30000)
                .setWaitUntil(WaitUntilState.NETWORKIDLE));
            
            // 等待内容加载
            page.waitForTimeout(2000);
            
            // 滚动页面加载懒加载图片
            scrollPage(page);
            
            // 从DOM中提取图片
            List<String> domImages = extractImagesFromDom(page);
            imageUrls.addAll(domImages);
            
            // 提取文本内容
            String textContent = extractTextContent(page);
            result.setTextContent(textContent);
            
            // 提取标题
            String title = page.title();
            result.setTitle(title);
            
            // 下载图片
            if (!imageUrls.isEmpty()) {
                String articleDir = downloadBasePath + sanitizeFileName(articleId) + "/";
                Files.createDirectories(Paths.get(articleDir));
                
                List<ImageInfo> downloadedImages = downloadImages(new ArrayList<>(imageUrls), articleDir, articleId);
                result.setImages(downloadedImages);
                result.setLocalImagesPath(articleDir);
            }
            
            result.setSuccess(true);
            result.setMessage(String.format("成功爬取内容，发现 %d 张图片", imageUrls.size()));
            logger.info("爬取完成: {}", result.getMessage());
            
        } catch (Exception e) {
            logger.error("爬取失败: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setError(e.getMessage());
        } finally {
            if (page != null) page.close();
            if (context != null) context.close();
        }
        
        return result;
    }
    
    /**
     * 滚动页面触发懒加载
     */
    private void scrollPage(Page page) {
        try {
            int scrollTimes = 5;
            for (int i = 0; i < scrollTimes; i++) {
                page.evaluate("window.scrollBy(0, window.innerHeight)");
                page.waitForTimeout(800);
            }
            // 滚回顶部
            page.evaluate("window.scrollTo(0, 0)");
            page.waitForTimeout(500);
        } catch (Exception e) {
            logger.warn("滚动页面失败: {}", e.getMessage());
        }
    }
    
    /**
     * 从DOM中提取图片URL
     */
    private List<String> extractImagesFromDom(Page page) {
        List<String> imageUrls = new ArrayList<>();
        
        try {
            // 提取img标签
            List<ElementHandle> images = page.querySelectorAll("img");
            for (ElementHandle img : images) {
                String src = img.getAttribute("src");
                String dataSrc = img.getAttribute("data-src");
                String dataOriginal = img.getAttribute("data-original");
                
                String imageUrl = null;
                if (isValidImageUrl(dataOriginal)) imageUrl = dataOriginal;
                else if (isValidImageUrl(dataSrc)) imageUrl = dataSrc;
                else if (isValidImageUrl(src)) imageUrl = src;
                
                if (imageUrl != null && isValidContentImage(imageUrl)) {
                    imageUrls.add(normalizeUrl(imageUrl));
                }
            }
            
            // 提取背景图片
            List<ElementHandle> bgElements = page.querySelectorAll("[style*='background-image']");
            for (ElementHandle elem : bgElements) {
                String style = elem.getAttribute("style");
                String bgUrl = extractBackgroundUrl(style);
                if (bgUrl != null && isValidContentImage(bgUrl)) {
                    imageUrls.add(normalizeUrl(bgUrl));
                }
            }
            
        } catch (Exception e) {
            logger.warn("提取DOM图片失败: {}", e.getMessage());
        }
        
        return imageUrls;
    }
    
    /**
     * 提取文本内容
     */
    private String extractTextContent(Page page) {
        StringBuilder content = new StringBuilder();
        
        try {
            // 尝试多种选择器提取内容
            String[] selectors = {
                ".content", ".post-content", ".article-content",
                ".detail-content", ".note-content", "article",
                ".desc", ".text-content", "main"
            };
            
            for (String selector : selectors) {
                ElementHandle element = page.querySelector(selector);
                if (element != null) {
                    String text = element.innerText();
                    if (text != null && text.length() > 50) {
                        content.append(text).append("\n\n");
                    }
                }
            }
            
            // 如果没找到，提取所有段落
            if (content.length() < 100) {
                List<ElementHandle> paragraphs = page.querySelectorAll("p");
                for (ElementHandle p : paragraphs) {
                    String text = p.innerText();
                    if (text != null && text.length() > 20) {
                        content.append(text).append("\n");
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warn("提取文本失败: {}", e.getMessage());
        }
        
        return content.toString().trim();
    }
    
    /**
     * 下载图片列表
     */
    private List<ImageInfo> downloadImages(List<String> urls, String downloadDir, String articleId) {
        List<ImageInfo> results = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        int index = 0;
        for (String url : urls) {
            if (index >= 20) break; // 限制最多20张
            
            final int idx = index++;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                ImageInfo info = downloadSingleImage(url, downloadDir, articleId, idx);
                if (info != null) {
                    results.add(info);
                }
            }, downloadExecutor);
            futures.add(future);
        }
        
        // 等待所有下载完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("等待下载超时: {}", e.getMessage());
        }
        
        return results;
    }
    
    /**
     * 下载单张图片
     */
    private ImageInfo downloadSingleImage(String imageUrl, String downloadDir, String articleId, int index) {
        ImageInfo info = new ImageInfo();
        info.setUrl(imageUrl);
        
        HttpURLConnection conn = null;
        try {
            URL url = new URL(imageUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENTS[0]);
            conn.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*");
            conn.setRequestProperty("Referer", "https://www.dewu.com/");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                info.setDownloaded(false);
                return info;
            }
            
            String contentType = conn.getContentType();
            String extension = guessExtension(imageUrl, contentType);
            String filename = String.format("%s_%03d%s", articleId, index, extension);
            Path filePath = Paths.get(downloadDir, filename);
            
            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(filePath.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                    if (totalBytes > 15 * 1024 * 1024) break; // 15MB限制
                }
                
                info.setLocalPath(filePath.toString());
                info.setFileSize(totalBytes);
                info.setDownloaded(true);
                info.setType(analyzeImageType(imageUrl));
                info.setDescription(String.format("图片%d - %s", index + 1, info.getType()));
                
                logger.info("下载成功: {} ({} KB)", filename, totalBytes / 1024);
            }
            
        } catch (Exception e) {
            logger.warn("下载图片失败 {}: {}", imageUrl, e.getMessage());
            info.setDownloaded(false);
        } finally {
            if (conn != null) conn.disconnect();
        }
        
        return info;
    }
    
    // 辅助方法
    private String normalizeUrl(String url) {
        if (url == null) return null;
        if (url.startsWith("//")) url = "https:" + url;
        return url;
    }
    
    private boolean isValidImageUrl(String url) {
        return url != null && !url.isEmpty() && !url.startsWith("data:") &&
                (url.startsWith("http") || url.startsWith("//"));
    }
    
    private boolean isValidContentImage(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return !lower.contains("icon") && !lower.contains("logo") &&
                !lower.contains("avatar") && !lower.contains("emoji") &&
               !lower.contains("_xs") && !lower.contains("thumb") &&
               !lower.contains("1x1") && !lower.contains("pixel");
    }
    
    private String extractBackgroundUrl(String style) {
        if (style == null) return null;
        Pattern pattern = Pattern.compile("url\\(['\"]?([^'\"\\)]+)['\"]?\\)");
        Matcher matcher = pattern.matcher(style);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    private String guessExtension(String url, String contentType) {
        if (contentType != null) {
            if (contentType.contains("webp")) return ".webp";
            if (contentType.contains("png")) return ".png";
            if (contentType.contains("gif")) return ".gif";
        }
        String lower = url.toLowerCase();
        if (lower.contains(".webp")) return ".webp";
        if (lower.contains(".png")) return ".png";
        if (lower.contains(".gif")) return ".gif";
        return ".jpg";
    }
    
    private String analyzeImageType(String url) {
        String lower = url.toLowerCase();
        if (lower.contains("product") || lower.contains("goods")) return "商品图";
        if (lower.contains("detail")) return "细节图";
        if (lower.contains("scene") || lower.contains("lifestyle")) return "场景图";
        return "内容图";
    }
    
    private String sanitizeFileName(String name) {
        if (name == null) return "unnamed";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    
    // 结果类
    public static class CrawlResult {
        private String articleId;
        private String sourceUrl;
        private boolean success;
        private String error;
        private String message;
        private String title;
        private String textContent;
        private List<ImageInfo> images = new ArrayList<>();
        private String localImagesPath;
        
        // Getters and Setters
        public String getArticleId() { return articleId; }
        public void setArticleId(String articleId) { this.articleId = articleId; }
        public String getSourceUrl() { return sourceUrl; }
        public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getTextContent() { return textContent; }
        public void setTextContent(String textContent) { this.textContent = textContent; }
        public List<ImageInfo> getImages() { return images; }
        public void setImages(List<ImageInfo> images) { this.images = images; }
        public String getLocalImagesPath() { return localImagesPath; }
        public void setLocalImagesPath(String localImagesPath) { this.localImagesPath = localImagesPath; }
    }
}