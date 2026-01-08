package com.fxt.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 得物图片爬取和下载服务
 * 结合Selenium和传统HTTP请求的混合方案
 */
@Service
public class DewuImageCrawlerService {
    
    @Autowired
    private SeleniumDewuCrawlerService seleniumCrawlerService;
    
    private final WebClient webClient;
    private final ExecutorService downloadExecutor;
    private final String baseDownloadPath;
    
    public DewuImageCrawlerService() {
        this.webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
            .build();
        this.downloadExecutor = Executors.newFixedThreadPool(3); // 3个并发下载线程
        this.baseDownloadPath = "downloads/images/";
        
        // 创建下载目录
        try {
            Files.createDirectories(Paths.get(baseDownloadPath));
        } catch (IOException e) {
            System.err.println("创建下载目录失败: " + e.getMessage());
        }
    }
    
    /**
     * 爬取并下载得物文章的所有图片
     */
    public CompletableFuture<ImageCrawlResult> crawlAndDownloadImages(String url, String articleId) {
        return CompletableFuture.supplyAsync(() -> {
            ImageCrawlResult result = new ImageCrawlResult();
            result.setArticleId(articleId);
            result.setSourceUrl(url);
            result.setStartTime(LocalDateTime.now());
            
            try {
                System.out.println("开始爬取得物文章图片: " + url);
                
                // 1. 使用Selenium获取图片URL列表
                List<String> imageUrls = seleniumCrawlerService.crawlDewuImages(url);
                result.setTotalImages(imageUrls.size());
                
                if (imageUrls.isEmpty()) {
                    result.setStatus("NO_IMAGES");
                    result.setMessage("未发现图片");
                    return result;
                }
                
                // 2. 创建文章专用目录
                String articleDir = baseDownloadPath + sanitizeFileName(articleId) + "/";
                Files.createDirectories(Paths.get(articleDir));
                result.setLocalPath(articleDir);
                
                // 3. 并发下载图片
                List<CompletableFuture<ImageDownloadInfo>> downloadTasks = new ArrayList<>();
                
                for (int i = 0; i < imageUrls.size() && i < 20; i++) { // 限制最多20张图片
                    String imageUrl = imageUrls.get(i);
                    String fileName = String.format("%s_img_%03d", articleId, i + 1);
                    
                    CompletableFuture<ImageDownloadInfo> downloadTask = downloadImageAsync(
                        imageUrl, articleDir, fileName
                    );
                    downloadTasks.add(downloadTask);
                }
                
                // 4. 等待所有下载完成
                CompletableFuture<Void> allDownloads = CompletableFuture.allOf(
                    downloadTasks.toArray(new CompletableFuture[0])
                );
                
                allDownloads.join(); // 等待完成
                
                // 5. 收集下载结果
                List<ImageDownloadInfo> downloadResults = new ArrayList<>();
                for (CompletableFuture<ImageDownloadInfo> task : downloadTasks) {
                    try {
                        ImageDownloadInfo downloadInfo = task.get();
                        if (downloadInfo != null) {
                            downloadResults.add(downloadInfo);
                        }
                    } catch (Exception e) {
                        System.err.println("获取下载结果失败: " + e.getMessage());
                    }
                }
                
                result.setDownloadedImages(downloadResults);
                result.setSuccessCount(downloadResults.stream()
                    .mapToInt(info -> info.isSuccess() ? 1 : 0).sum());
                
                // 6. 生成分析报告
                result.setAnalysisReport(generateImageAnalysisReport(downloadResults));
                
                result.setStatus("SUCCESS");
                result.setMessage(String.format("成功下载 %d/%d 张图片", 
                    result.getSuccessCount(), result.getTotalImages()));
                
                System.out.println("图片爬取完成: " + result.getMessage());
                
            } catch (Exception e) {
                result.setStatus("ERROR");
                result.setMessage("爬取失败: " + e.getMessage());
                System.err.println("图片爬取失败: " + e.getMessage());
                e.printStackTrace();
            } finally {
                result.setEndTime(LocalDateTime.now());
            }
            
            return result;
        }, downloadExecutor);
    }
    
    /**
     * 异步下载单张图片
     */
    private CompletableFuture<ImageDownloadInfo> downloadImageAsync(String imageUrl, String downloadDir, String fileName) {
        return CompletableFuture.supplyAsync(() -> {
            ImageDownloadInfo info = new ImageDownloadInfo();
            info.setUrl(imageUrl);
            info.setFileName(fileName);
            info.setStartTime(LocalDateTime.now());
            
            try {
                System.out.println("开始下载图片: " + imageUrl);
                
                // 获取文件扩展名
                String extension = getFileExtension(imageUrl);
                if (extension.isEmpty()) {
                    extension = ".jpg"; // 默认扩展名
                }
                
                String localFileName = sanitizeFileName(fileName) + extension;
                Path localPath = Paths.get(downloadDir, localFileName);
                info.setLocalPath(localPath.toString());
                
                // 下载图片
                byte[] imageBytes = webClient.get()
                    .uri(imageUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://www.dewu.com/")
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
                
                if (imageBytes != null && imageBytes.length > 0) {
                    // 验证图片数据
                    if (isValidImageData(imageBytes)) {
                        Files.write(localPath, imageBytes);
                        
                        info.setFileSize((long) imageBytes.length);
                        info.setSuccess(true);
                        info.setMessage("下载成功");
                        
                        // 分析图片信息
                        analyzeImageInfo(info, imageBytes);
                        
                        System.out.println("图片下载成功: " + localPath);
                    } else {
                        info.setSuccess(false);
                        info.setMessage("下载的文件不是有效图片");
                        System.err.println("无效图片数据: " + imageUrl);
                    }
                } else {
                    info.setSuccess(false);
                    info.setMessage("下载的图片数据为空");
                    System.err.println("图片数据为空: " + imageUrl);
                }
                
            } catch (Exception e) {
                info.setSuccess(false);
                info.setMessage("下载失败: " + e.getMessage());
                System.err.println("下载图片失败 " + imageUrl + ": " + e.getMessage());
            } finally {
                info.setEndTime(LocalDateTime.now());
            }
            
            return info;
        }, downloadExecutor);
    }
    
    /**
     * 分析图片信息
     */
    private void analyzeImageInfo(ImageDownloadInfo info, byte[] imageBytes) {
        try {
            // 检测图片格式
            String format = detectImageFormat(imageBytes);
            info.setFormat(format);
            
            // 估算图片尺寸（简单实现）
            if ("JPEG".equals(format) || "JPG".equals(format)) {
                // 可以添加更详细的JPEG解析
                info.setEstimatedWidth(800); // 默认值
                info.setEstimatedHeight(600);
            } else if ("PNG".equals(format)) {
                // 可以添加PNG解析
                info.setEstimatedWidth(800);
                info.setEstimatedHeight(600);
            }
            
            // 分析图片类型
            String imageType = analyzeImageType(info.getUrl(), info.getFileName());
            info.setImageType(imageType);
            
        } catch (Exception e) {
            System.err.println("分析图片信息失败: " + e.getMessage());
        }
    }
    
    /**
     * 检测图片格式
     */
    private String detectImageFormat(byte[] imageBytes) {
        if (imageBytes.length < 10) return "UNKNOWN";
        
        // JPEG: FF D8 FF
        if (imageBytes[0] == (byte) 0xFF && imageBytes[1] == (byte) 0xD8 && imageBytes[2] == (byte) 0xFF) {
            return "JPEG";
        }
        
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (imageBytes[0] == (byte) 0x89 && imageBytes[1] == 0x50 && 
            imageBytes[2] == 0x4E && imageBytes[3] == 0x47) {
            return "PNG";
        }
        
        // GIF: 47 49 46 38
        if (imageBytes[0] == 0x47 && imageBytes[1] == 0x49 && 
            imageBytes[2] == 0x46 && imageBytes[3] == 0x38) {
            return "GIF";
        }
        
        // WebP: 52 49 46 46 ... 57 45 42 50
        if (imageBytes.length > 12 && 
            imageBytes[0] == 0x52 && imageBytes[1] == 0x49 && 
            imageBytes[2] == 0x46 && imageBytes[3] == 0x46 &&
            imageBytes[8] == 0x57 && imageBytes[9] == 0x45 && 
            imageBytes[10] == 0x42 && imageBytes[11] == 0x50) {
            return "WEBP";
        }
        
        return "UNKNOWN";
    }
    
    /**
     * 分析图片类型
     */
    private String analyzeImageType(String url, String fileName) {
        String combined = (url + " " + fileName).toLowerCase();
        
        if (combined.contains("product") || combined.contains("item") || combined.contains("goods")) {
            return "product";
        } else if (combined.contains("detail") || combined.contains("close") || combined.contains("zoom")) {
            return "detail";
        } else if (combined.contains("scene") || combined.contains("lifestyle") || combined.contains("wear")) {
            return "scene";
        } else if (combined.contains("model") || combined.contains("person")) {
            return "model";
        } else if (combined.contains("brand") || combined.contains("logo")) {
            return "brand";
        } else {
            return "content";
        }
    }
    
    /**
     * 生成图片分析报告
     */
    private String generateImageAnalysisReport(List<ImageDownloadInfo> downloadResults) {
        StringBuilder report = new StringBuilder();
        
        int totalImages = downloadResults.size();
        int successCount = downloadResults.stream().mapToInt(info -> info.isSuccess() ? 1 : 0).sum();
        long totalSize = downloadResults.stream()
            .filter(ImageDownloadInfo::isSuccess)
            .mapToLong(info -> info.getFileSize() != null ? info.getFileSize() : 0)
            .sum();
        
        report.append("📷 得物图片爬取分析报告\n");
        report.append("═══════════════════════════════════════\n");
        report.append(String.format("总图片数: %d 张\n", totalImages));
        report.append(String.format("成功下载: %d 张 (%.1f%%)\n", successCount, 
            totalImages > 0 ? (double) successCount / totalImages * 100 : 0));
        report.append(String.format("总大小: %.2f MB\n", totalSize / 1024.0 / 1024.0));
        
        // 格式分布
        Map<String, Long> formatCount = downloadResults.stream()
            .filter(ImageDownloadInfo::isSuccess)
            .collect(java.util.stream.Collectors.groupingBy(
                info -> info.getFormat() != null ? info.getFormat() : "UNKNOWN",
                java.util.stream.Collectors.counting()
            ));
        
        if (!formatCount.isEmpty()) {
            report.append("\n图片格式分布:\n");
            formatCount.forEach((format, count) -> 
                report.append(String.format("  %s: %d 张\n", format, count)));
        }
        
        // 类型分布
        Map<String, Long> typeCount = downloadResults.stream()
            .filter(ImageDownloadInfo::isSuccess)
            .collect(java.util.stream.Collectors.groupingBy(
                info -> info.getImageType() != null ? info.getImageType() : "unknown",
                java.util.stream.Collectors.counting()
            ));
        
        if (!typeCount.isEmpty()) {
            report.append("\n图片类型分布:\n");
            typeCount.forEach((type, count) -> {
                String typeName = getTypeDisplayName(type);
                report.append(String.format("  %s: %d 张\n", typeName, count));
            });
        }
        
        // 质量评估
        report.append("\n质量评估:\n");
        if (successCount >= 5) {
            report.append("✅ 图片内容丰富，视觉效果佳\n");
        } else if (successCount >= 3) {
            report.append("✅ 图片内容适中\n");
        } else if (successCount >= 1) {
            report.append("⚠️ 图片内容较少，建议增加\n");
        } else {
            report.append("❌ 未获取到图片内容\n");
        }
        
        return report.toString();
    }
    
    private String getTypeDisplayName(String type) {
        switch (type) {
            case "product": return "商品图";
            case "detail": return "细节图";
            case "scene": return "场景图";
            case "model": return "模特图";
            case "brand": return "品牌图";
            default: return "内容图";
        }
    }
    
    // 辅助方法
    private boolean isValidImageData(byte[] data) {
        if (data == null || data.length < 10) return false;
        
        // 检查文件头
        return (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) || // JPEG
               (data[0] == (byte) 0x89 && data[1] == 0x50) ||         // PNG
               (data[0] == 0x47 && data[1] == 0x49) ||                // GIF
               (data[0] == 0x52 && data[1] == 0x49);                  // WebP
    }
    
    private String getFileExtension(String url) {
        try {
            int queryIndex = url.indexOf('?');
            if (queryIndex > 0) {
                url = url.substring(0, queryIndex);
            }
            
            int lastDot = url.lastIndexOf('.');
            if (lastDot > 0 && lastDot < url.length() - 1) {
                String ext = url.substring(lastDot).toLowerCase();
                if (ext.matches("\\.(jpg|jpeg|png|gif|webp|bmp)")) {
                    return ext;
                }
            }
        } catch (Exception e) {
            // 忽略错误
        }
        return "";
    }
    
    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "unnamed";
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_").substring(0, Math.min(fileName.length(), 50));
    }
    
    // 内部类
    public static class ImageCrawlResult {
        private String articleId;
        private String sourceUrl;
        private String localPath;
        private int totalImages;
        private int successCount;
        private List<ImageDownloadInfo> downloadedImages;
        private String status;
        private String message;
        private String analysisReport;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        
        // Getters and Setters
        public String getArticleId() { return articleId; }
        public void setArticleId(String articleId) { this.articleId = articleId; }
        
        public String getSourceUrl() { return sourceUrl; }
        public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
        
        public String getLocalPath() { return localPath; }
        public void setLocalPath(String localPath) { this.localPath = localPath; }
        
        public int getTotalImages() { return totalImages; }
        public void setTotalImages(int totalImages) { this.totalImages = totalImages; }
        
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        
        public List<ImageDownloadInfo> getDownloadedImages() { return downloadedImages; }
        public void setDownloadedImages(List<ImageDownloadInfo> downloadedImages) { this.downloadedImages = downloadedImages; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getAnalysisReport() { return analysisReport; }
        public void setAnalysisReport(String analysisReport) { this.analysisReport = analysisReport; }
        
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    }
    
    public static class ImageDownloadInfo {
        private String url;
        private String fileName;
        private String localPath;
        private Long fileSize;
        private boolean success;
        private String message;
        private String format;
        private String imageType;
        private Integer estimatedWidth;
        private Integer estimatedHeight;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        
        // Getters and Setters
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public String getLocalPath() { return localPath; }
        public void setLocalPath(String localPath) { this.localPath = localPath; }
        
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        
        public String getImageType() { return imageType; }
        public void setImageType(String imageType) { this.imageType = imageType; }
        
        public Integer getEstimatedWidth() { return estimatedWidth; }
        public void setEstimatedWidth(Integer estimatedWidth) { this.estimatedWidth = estimatedWidth; }
        
        public Integer getEstimatedHeight() { return estimatedHeight; }
        public void setEstimatedHeight(Integer estimatedHeight) { this.estimatedHeight = estimatedHeight; }
        
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    }
}