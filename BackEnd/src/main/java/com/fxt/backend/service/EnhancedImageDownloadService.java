package com.fxt.backend.service;

import com.fxt.backend.dto.ImageInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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
 * 增强的图片下载服务
 * 支持批量下载、智能分类、格式转换等功能
 */
@Service
public class EnhancedImageDownloadService {
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService downloadExecutor;
    private final String baseDownloadPath;
    
    public EnhancedImageDownloadService() {
        this.webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
            .build();
        this.objectMapper = new ObjectMapper();
        this.downloadExecutor = Executors.newFixedThreadPool(5); // 5个并发下载线程
        this.baseDownloadPath = "downloads/images/";
        
        // 创建下载目录
        try {
            Files.createDirectories(Paths.get(baseDownloadPath));
        } catch (IOException e) {
            System.err.println("创建下载目录失败: " + e.getMessage());
        }
    }
    
    /**
     * 从HTML文档中提取并下载所有图片
     */
    public CompletableFuture<List<ImageInfo>> extractAndDownloadImages(Document doc, String articleId) {
        return CompletableFuture.supplyAsync(() -> {
            List<ImageInfo> imageInfos = new ArrayList<>();
            
            try {
                // 创建文章专用目录
                String articleDir = baseDownloadPath + sanitizeFileName(articleId) + "/";
                Files.createDirectories(Paths.get(articleDir));
                
                // 提取所有图片元素
                Elements images = doc.select("img, picture source, [style*=background-image]");
                
                System.out.println("发现 " + images.size() + " 个图片元素");
                
                List<CompletableFuture<ImageInfo>> downloadTasks = new ArrayList<>();
                
                int imageIndex = 0;
                for (Element img : images) {
                    if (imageIndex >= 20) break; // 限制最多下载20张图片
                    
                    ImageInfo imageInfo = extractImageInfo(img, imageIndex);
                    if (imageInfo != null && isValidImageUrl(imageInfo.getUrl())) {
                        // 异步下载图片
                        CompletableFuture<ImageInfo> downloadTask = downloadImageAsync(
                            imageInfo, articleDir, articleId + "_img_" + imageIndex
                        );
                        downloadTasks.add(downloadTask);
                        imageIndex++;
                    }
                }
                
                // 等待所有下载完成
                CompletableFuture<Void> allDownloads = CompletableFuture.allOf(
                    downloadTasks.toArray(new CompletableFuture[0])
                );
                
                allDownloads.join(); // 等待完成
                
                // 收集结果
                for (CompletableFuture<ImageInfo> task : downloadTasks) {
                    try {
                        ImageInfo result = task.get();
                        if (result != null) {
                            imageInfos.add(result);
                        }
                    } catch (Exception e) {
                        System.err.println("获取下载结果失败: " + e.getMessage());
                    }
                }
                
                System.out.println("成功下载 " + imageInfos.stream().mapToInt(img -> img.getDownloaded() ? 1 : 0).sum() + " 张图片");
                
            } catch (Exception e) {
                System.err.println("图片提取和下载过程出错: " + e.getMessage());
                e.printStackTrace();
            }
            
            return imageInfos;
        }, downloadExecutor);
    }
    
    /**
     * 从图片元素提取信息
     */
    private ImageInfo extractImageInfo(Element imgElement, int index) {
        try {
            String src = imgElement.attr("src");
            String dataSrc = imgElement.attr("data-src");
            String alt = imgElement.attr("alt");
            String title = imgElement.attr("title");
            String width = imgElement.attr("width");
            String height = imgElement.attr("height");
            
            // 优先使用data-src（懒加载）
            if ((src == null || src.isEmpty()) && !dataSrc.isEmpty()) {
                src = dataSrc;
            }
            
            // 处理相对路径
            if (src.startsWith("//")) {
                src = "https:" + src;
            } else if (src.startsWith("/")) {
                // 需要基础URL，这里暂时跳过
                return null;
            }
            
            if (src == null || src.isEmpty()) {
                return null;
            }
            
            ImageInfo imageInfo = new ImageInfo(src, alt, title, "");
            
            // 设置尺寸
            try {
                if (!width.isEmpty()) imageInfo.setWidth(Integer.parseInt(width));
                if (!height.isEmpty()) imageInfo.setHeight(Integer.parseInt(height));
            } catch (NumberFormatException e) {
                // 忽略尺寸解析错误
            }
            
            // 分析图片类型
            imageInfo.setType(analyzeImageType(src, alt, title, imgElement));
            
            // 生成描述
            imageInfo.setDescription(generateImageDescription(imageInfo, imgElement, index));
            
            return imageInfo;
            
        } catch (Exception e) {
            System.err.println("提取图片信息失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 异步下载单张图片
     */
    private CompletableFuture<ImageInfo> downloadImageAsync(ImageInfo imageInfo, String downloadDir, String fileName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = imageInfo.getUrl();
                System.out.println("开始下载图片: " + url);
                
                // 获取文件扩展名
                String extension = getFileExtension(url);
                if (extension.isEmpty()) {
                    extension = ".jpg"; // 默认扩展名
                }
                
                String localFileName = sanitizeFileName(fileName) + extension;
                Path localPath = Paths.get(downloadDir, localFileName);
                
                // 下载图片
                byte[] imageBytes = webClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", url)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
                
                if (imageBytes != null && imageBytes.length > 0) {
                    // 检查是否为有效图片
                    if (isValidImageData(imageBytes)) {
                        Files.write(localPath, imageBytes);
                        
                        imageInfo.setLocalPath(localPath.toString());
                        imageInfo.setFileSize((long) imageBytes.length);
                        imageInfo.setDownloaded(true);
                        
                        System.out.println("图片下载成功: " + localPath);
                    } else {
                        System.err.println("下载的文件不是有效图片: " + url);
                        imageInfo.setDownloaded(false);
                    }
                } else {
                    System.err.println("下载的图片数据为空: " + url);
                    imageInfo.setDownloaded(false);
                }
                
            } catch (Exception e) {
                System.err.println("下载图片失败 " + imageInfo.getUrl() + ": " + e.getMessage());
                imageInfo.setDownloaded(false);
            }
            
            return imageInfo;
        }, downloadExecutor);
    }
    
    /**
     * 分析图片类型
     */
    private String analyzeImageType(String src, String alt, String title, Element imgElement) {
        String combined = (src + " " + alt + " " + title).toLowerCase();
        
        // 检查父元素的class和id
        Element parent = imgElement.parent();
        String parentInfo = "";
        if (parent != null) {
            parentInfo = (parent.attr("class") + " " + parent.attr("id")).toLowerCase();
        }
        
        String allInfo = combined + " " + parentInfo;
        
        if (allInfo.contains("product") || allInfo.contains("item") || allInfo.contains("goods")) {
            return "product";
        } else if (allInfo.contains("detail") || allInfo.contains("close") || allInfo.contains("zoom")) {
            return "detail";
        } else if (allInfo.contains("scene") || allInfo.contains("lifestyle") || allInfo.contains("use")) {
            return "scene";
        } else if (allInfo.contains("avatar") || allInfo.contains("user") || allInfo.contains("profile")) {
            return "avatar";
        } else if (allInfo.contains("logo") || allInfo.contains("brand")) {
            return "logo";
        } else if (allInfo.contains("banner") || allInfo.contains("hero")) {
            return "banner";
        } else {
            return "content";
        }
    }
    
    /**
     * 生成图片描述
     */
    private String generateImageDescription(ImageInfo imageInfo, Element imgElement, int index) {
        StringBuilder desc = new StringBuilder();
        
        desc.append("图片").append(index + 1).append(": ");
        
        if (imageInfo.getAlt() != null && !imageInfo.getAlt().isEmpty()) {
            desc.append(imageInfo.getAlt());
        } else if (imageInfo.getTitle() != null && !imageInfo.getTitle().isEmpty()) {
            desc.append(imageInfo.getTitle());
        } else {
            // 根据类型生成描述
            switch (imageInfo.getType()) {
                case "product":
                    desc.append("商品展示图");
                    break;
                case "detail":
                    desc.append("商品细节图");
                    break;
                case "scene":
                    desc.append("使用场景图");
                    break;
                case "avatar":
                    desc.append("用户头像");
                    break;
                case "logo":
                    desc.append("品牌标识");
                    break;
                case "banner":
                    desc.append("横幅图片");
                    break;
                default:
                    desc.append("内容配图");
            }
        }
        
        // 添加尺寸信息
        if (imageInfo.getWidth() != null && imageInfo.getHeight() != null) {
            desc.append(" [").append(imageInfo.getWidth()).append("×").append(imageInfo.getHeight()).append("]");
        }
        
        return desc.toString();
    }
    
    /**
     * 检查是否为有效的图片URL
     */
    private boolean isValidImageUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        // 过滤掉明显的非图片URL
        String lowerUrl = url.toLowerCase();
        
        // 检查是否包含图片扩展名
        boolean hasImageExtension = lowerUrl.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp|svg)(\\?.*)?$");
        
        // 检查是否包含图片相关关键词
        boolean hasImageKeywords = lowerUrl.contains("image") || lowerUrl.contains("img") || 
                                  lowerUrl.contains("photo") || lowerUrl.contains("pic");
        
        // 过滤掉小图标和装饰性图片
        boolean isDecorative = lowerUrl.contains("icon") || lowerUrl.contains("logo") || 
                              lowerUrl.contains("avatar") || lowerUrl.contains("1x1") ||
                              lowerUrl.contains("pixel") || lowerUrl.contains("spacer");
        
        return (hasImageExtension || hasImageKeywords) && !isDecorative && url.startsWith("http");
    }
    
    /**
     * 检查下载的数据是否为有效图片
     */
    private boolean isValidImageData(byte[] data) {
        if (data == null || data.length < 10) {
            return false;
        }
        
        // 检查文件头
        String header = bytesToHex(Arrays.copyOf(data, Math.min(10, data.length)));
        
        // JPEG: FF D8 FF
        if (header.startsWith("FFD8FF")) return true;
        
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (header.startsWith("89504E47")) return true;
        
        // GIF: 47 49 46 38
        if (header.startsWith("47494638")) return true;
        
        // WebP: 52 49 46 46 ... 57 45 42 50
        if (header.startsWith("52494646") && data.length > 12) {
            String webpHeader = bytesToHex(Arrays.copyOfRange(data, 8, 12));
            if (webpHeader.equals("57454250")) return true;
        }
        
        // BMP: 42 4D
        if (header.startsWith("424D")) return true;
        
        return false;
    }
    
    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02X", b));
        }
        return result.toString();
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String url) {
        try {
            // 移除查询参数
            int queryIndex = url.indexOf('?');
            if (queryIndex > 0) {
                url = url.substring(0, queryIndex);
            }
            
            int lastDot = url.lastIndexOf('.');
            if (lastDot > 0 && lastDot < url.length() - 1) {
                String ext = url.substring(lastDot).toLowerCase();
                // 验证是否为有效的图片扩展名
                if (ext.matches("\\.(jpg|jpeg|png|gif|webp|bmp|svg)")) {
                    return ext;
                }
            }
        } catch (Exception e) {
            // 忽略错误
        }
        return "";
    }
    
    /**
     * 清理文件名，移除非法字符
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "unnamed";
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_").substring(0, Math.min(fileName.length(), 50));
    }
    
    /**
     * 生成图片统计报告
     */
    public String generateImageReport(List<ImageInfo> images) {
        if (images == null || images.isEmpty()) {
            return "未发现图片内容";
        }
        
        StringBuilder report = new StringBuilder();
        
        // 基本统计
        long downloadedCount = images.stream().mapToLong(img -> img.getDownloaded() ? 1 : 0).sum();
        long totalSize = images.stream().mapToLong(img -> img.getFileSize() != null ? img.getFileSize() : 0).sum();
        
        report.append("📷 图片内容分析报告\n");
        report.append("═══════════════════════════════════════\n");
        report.append(String.format("总图片数: %d 张\n", images.size()));
        report.append(String.format("成功下载: %d 张\n", downloadedCount));
        report.append(String.format("总大小: %.2f MB\n", totalSize / 1024.0 / 1024.0));
        
        // 类型分布
        Map<String, Long> typeCount = new HashMap<>();
        for (ImageInfo img : images) {
            typeCount.merge(img.getType(), 1L, Long::sum);
        }
        
        if (!typeCount.isEmpty()) {
            report.append("\n图片类型分布:\n");
            typeCount.forEach((type, count) -> {
                String typeName = getTypeDisplayName(type);
                report.append(String.format("  %s: %d 张\n", typeName, count));
            });
        }
        
        // 详细列表
        report.append("\n图片详情:\n");
        for (int i = 0; i < images.size(); i++) {
            ImageInfo img = images.get(i);
            report.append(String.format("%d. %s %s\n", 
                i + 1, 
                img.getDescription(),
                img.getDownloaded() ? "✓" : "✗"
            ));
            
            if (img.getDownloaded() && img.getLocalPath() != null) {
                report.append(String.format("   本地路径: %s\n", img.getLocalPath()));
            }
        }
        
        // 内容评价
        report.append("\n内容评价:\n");
        if (downloadedCount >= 5) {
            report.append("✅ 图片内容丰富，视觉效果佳\n");
        } else if (downloadedCount >= 3) {
            report.append("✅ 图片内容适中\n");
        } else if (downloadedCount >= 1) {
            report.append("⚠️ 图片内容较少，建议增加\n");
        } else {
            report.append("❌ 缺少图片内容，影响用户体验\n");
        }
        
        return report.toString();
    }
    
    private String getTypeDisplayName(String type) {
        switch (type) {
            case "product": return "商品图";
            case "detail": return "细节图";
            case "scene": return "场景图";
            case "avatar": return "头像";
            case "logo": return "标识";
            case "banner": return "横幅";
            default: return "内容图";
        }
    }
    
    /**
     * 清理过期的下载文件
     */
    public void cleanupOldDownloads(int daysOld) {
        try {
            Path downloadPath = Paths.get(baseDownloadPath);
            if (Files.exists(downloadPath)) {
                Files.walk(downloadPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant()
                                .isBefore(java.time.Instant.now().minus(daysOld, java.time.temporal.ChronoUnit.DAYS));
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            System.out.println("删除过期文件: " + path);
                        } catch (IOException e) {
                            System.err.println("删除文件失败: " + path + " - " + e.getMessage());
                        }
                    });
            }
        } catch (IOException e) {
            System.err.println("清理过期文件失败: " + e.getMessage());
        }
    }
}