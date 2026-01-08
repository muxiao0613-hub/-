package com.fxt.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * 得物图片下载服务 - 直接从CDN下载
 * 
 * 核心发现：
 * 1. 得物的图片存储在CDN: image-h5-cdn.dewu.com 或 cdn.poizon.com
 * 2. 图片URL格式可预测：/app/{year}/community/{id}_{info}.webp
 * 3. 图片可以直接HTTP下载，无需特殊认证
 * 4. 关键是如何获取图片URL列表
 * 
 * 解决方案：
 * 方案A: 使用Selenium/Playwright渲染JavaScript获取完整页面
 * 方案B: 抓包分析得物APP的API请求
 * 方案C: 用户手动提供图片URL（从浏览器开发者工具复制）
 * 方案D: 调用第三方网页截图API
 */
@Service
public class DewuDirectDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(DewuDirectDownloadService.class);
    
    private final ExecutorService executor = Executors.newFixedThreadPool(8);
    private final String downloadBasePath = "downloads/images/";

    // 得物CDN域名列表
    private static final String[] DEWU_CDN_HOSTS = {
        "image-h5-cdn.dewu.com",
        "cdn.poizon.com", 
        "img.poizon.com",
        "image.dewu.com"
    };

    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
    };
    
    private final Random random = new Random();

    public DewuDirectDownloadService() {
        try {
            Files.createDirectories(Paths.get(downloadBasePath));
        } catch (IOException e) {
            logger.error("创建下载目录失败", e);
        }
    }

    /**
     * 直接下载图片URL列表
     * 适用于用户已经获取到图片URL的情况
     */
    public DownloadResult downloadImageUrls(List<String> imageUrls, Long articleId) {
        DownloadResult result = new DownloadResult();
        result.setArticleId(articleId);
        result.setTotalUrls(imageUrls.size());
        
        if (imageUrls.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("图片URL列表为空");
            return result;
        }

        String articleDir = downloadBasePath + "article_" + articleId + "/";
        try {
            Files.createDirectories(Paths.get(articleDir));
        } catch (IOException e) {
            result.setSuccess(false);
            result.setMessage("创建目录失败: " + e.getMessage());
            return result;
        }

        List<DownloadedImage> downloadedImages = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();
        
        int index = 0;
        for (String url : imageUrls) {
            final int idx = index++;
            final String imgUrl = normalizeImageUrl(url);
            
            Future<?> future = executor.submit(() -> {
                DownloadedImage img = downloadSingleImage(imgUrl, articleDir, articleId, idx);
                if (img != null) {
                    downloadedImages.add(img);
                }
            });
            futures.add(future);
        }

        // 等待所有下载完成，最长2分钟
        for (Future<?> future : futures) {
            try {
                future.get(120, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.warn("下载任务异常: {}", e.getMessage());
            }
        }

        result.setSuccess(true);
        result.setDownloadedCount(downloadedImages.size());
        result.setImages(downloadedImages);
        result.setMessage(String.format("成功下载 %d/%d 张图片", downloadedImages.size(), imageUrls.size()));
        
        logger.info("文章 {} 下载完成: {}", articleId, result.getMessage());
        return result;
    }

    /**
     * 标准化图片URL
     */
    private String normalizeImageUrl(String url) {
        if (url == null) return null;
        
        // 添加协议
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        
        // 移除压缩参数，获取原图
        int paramIndex = url.indexOf("?x-oss-process=");
        if (paramIndex > 0) {
            url = url.substring(0, paramIndex);
        }
        
        return url;
    }

    /**
     * 下载单张图片
     */
    private DownloadedImage downloadSingleImage(String imageUrl, String saveDir, Long articleId, int index) {
        DownloadedImage result = new DownloadedImage();
        result.setOriginalUrl(imageUrl);
        result.setIndex(index);
        
        HttpURLConnection conn = null;
        try {
            URL url = new URL(imageUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENTS[random.nextInt(USER_AGENTS.length)]);
            conn.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            conn.setRequestProperty("Referer", "https://m.poizon.com/");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);
            
            int responseCode = conn.getResponseCode();
            
            // 处理重定向
            if (responseCode == 301 || responseCode == 302 || responseCode == 307 || responseCode == 308) {
                String newUrl = conn.getHeaderField("Location");
                if (newUrl != null) {
                    conn.disconnect();
                    logger.debug("重定向到: {}", newUrl);
                    return downloadSingleImage(newUrl, saveDir, articleId, index);
                }
            }
            
            if (responseCode != 200) {
                result.setSuccess(false);
                result.setError("HTTP响应码: " + responseCode);
                logger.warn("下载失败 {}: HTTP {}", imageUrl, responseCode);
                return result;
            }
            
            // 检查Content-Type
            String contentType = conn.getContentType();
            if (contentType != null && !contentType.contains("image")) {
                result.setSuccess(false);
                result.setError("非图片类型: " + contentType);
                return result;
            }
            
            // 文件大小检查
            long contentLength = conn.getContentLengthLong();
            if (contentLength > 20 * 1024 * 1024) { // 20MB
                result.setSuccess(false);
                result.setError("文件过大: " + (contentLength / 1024 / 1024) + "MB");
                return result;
            }
            
            // 确定扩展名
            String extension = guessExtension(imageUrl, contentType);
            String filename = String.format("dewu_%d_%03d%s", articleId, index, extension);
            Path filePath = Paths.get(saveDir, filename);
            
            // 下载文件
            try (InputStream in = conn.getInputStream();
                 BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(filePath.toFile()))) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                
                result.setSuccess(true);
                result.setLocalPath(filePath.toString());
                result.setFilename(filename);
                result.setFileSize(totalBytes);
                result.setContentType(contentType);
                
                logger.info("✓ 下载成功: {} ({} KB)", filename, totalBytes / 1024);
            }
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setError(e.getMessage());
            logger.error("下载异常 {}: {}", imageUrl, e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        
        return result;
    }

    /**
     * 从HTML内容中提取得物CDN图片URL
     */
    public List<String> extractDewuImageUrls(String htmlContent) {
        List<String> urls = new ArrayList<>();
        
        if (htmlContent == null || htmlContent.isEmpty()) {
            return urls;
        }
        
        // 匹配得物CDN图片URL的正则
        String regex = "https?://(?:image-h5-cdn\\.dewu\\.com|cdn\\.poizon\\.com|img\\.poizon\\.com)/[^\"'\\s<>]+\\.(?:webp|jpg|jpeg|png|gif)(?:\\?[^\"'\\s<>]*)?";
        
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(htmlContent);
        
        Set<String> uniqueUrls = new LinkedHashSet<>();
        while (matcher.find()) {
            String url = normalizeImageUrl(matcher.group());
            if (isValidContentImage(url)) {
                uniqueUrls.add(url);
            }
        }
        
        urls.addAll(uniqueUrls);
        return urls;
    }

    /**
     * 判断是否为有效的内容图片（排除小图标等）
     */
    private boolean isValidContentImage(String url) {
        if (url == null) return false;
        
        // 排除规则
        String lower = url.toLowerCase();
        if (lower.contains("logo") || lower.contains("icon") || 
            lower.contains("avatar") || lower.contains("emoji") ||
            lower.contains("_xs") || lower.contains("_s.") ||
            lower.contains("thumb") || lower.contains("mini")) {
            return false;
        }
        
        // 得物内容图片通常在 /app/{year}/community/ 路径下
        if (url.contains("/app/") && url.contains("/community/")) {
            return true;
        }
        
        return true;
    }

    /**
     * 猜测文件扩展名
     */
    private String guessExtension(String url, String contentType) {
        // 优先从URL判断
        String lower = url.toLowerCase();
        if (lower.contains(".webp")) return ".webp";
        if (lower.contains(".png")) return ".png";
        if (lower.contains(".gif")) return ".gif";
        if (lower.contains(".jpg") || lower.contains(".jpeg")) return ".jpg";
        
        // 从Content-Type判断
        if (contentType != null) {
            if (contentType.contains("webp")) return ".webp";
            if (contentType.contains("png")) return ".png";
            if (contentType.contains("gif")) return ".gif";
        }
        
        return ".jpg";
    }

    /**
     * 验证URL是否为得物CDN图片
     */
    public boolean isDewuCdnUrl(String url) {
        if (url == null) return false;
        for (String host : DEWU_CDN_HOSTS) {
            if (url.contains(host)) return true;
        }
        return false;
    }

    // ========== 结果类 ==========
    
    public static class DownloadResult {
        private Long articleId;
        private boolean success;
        private String message;
        private int totalUrls;
        private int downloadedCount;
        private List<DownloadedImage> images = new ArrayList<>();

        public Long getArticleId() { return articleId; }
        public void setArticleId(Long articleId) { this.articleId = articleId; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public int getTotalUrls() { return totalUrls; }
        public void setTotalUrls(int totalUrls) { this.totalUrls = totalUrls; }
        public int getDownloadedCount() { return downloadedCount; }
        public void setDownloadedCount(int downloadedCount) { this.downloadedCount = downloadedCount; }
        public List<DownloadedImage> getImages() { return images; }
        public void setImages(List<DownloadedImage> images) { this.images = images; }
    }

    public static class DownloadedImage {
        private int index;
        private String originalUrl;
        private String localPath;
        private String filename;
        private long fileSize;
        private String contentType;
        private boolean success;
        private String error;

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }
        public String getOriginalUrl() { return originalUrl; }
        public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }
        public String getLocalPath() { return localPath; }
        public void setLocalPath(String localPath) { this.localPath = localPath; }
        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}
