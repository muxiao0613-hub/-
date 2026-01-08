package com.fxt.backend.service;

import com.fxt.backend.dto.ImageInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 增强的图片下载服务
 * 支持真正的批量下载、智能分类、格式转换等功能
 */
@Service
public class EnhancedImageDownloadService {
    
    private final ObjectMapper objectMapper;
    private final ExecutorService downloadExecutor;
    private final String baseDownloadPath;
    
    // 常见User-Agent列表
    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    };
    
    public EnhancedImageDownloadService() {
        this.objectMapper = new ObjectMapper();
        this.downloadExecutor = Executors.newFixedThreadPool(8); // 8个并发下载线程
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
                String safeArticleId = sanitizeFileName(articleId);
                String articleDir = baseDownloadPath + safeArticleId + "/";
                Files.createDirectories(Paths.get(articleDir));
                
                // 提取所有图片元素
                Elements images = doc.select("img[src], img[data-src], img[data-original], picture source[srcset]");
                
                System.out.println("发现 " + images.size() + " 个图片元素");
                
                List<CompletableFuture<ImageInfo>> downloadTasks = new ArrayList<>();
                Set<String> processedUrls = new HashSet<>(); // 防止重复下载
                
                int imageIndex = 0;
                for (Element img : images) {
                    if (imageIndex >= 30) break; // 限制最多下载30张图片
                    
                    ImageInfo imageInfo = extractImageInfo(img, imageIndex);
                    if (imageInfo != null && isValidImageUrl(imageInfo.getUrl())) {
                        String normalizedUrl = normalizeUrl(imageInfo.getUrl());
                        if (!processedUrls.contains(normalizedUrl)) {
                            processedUrls.add(normalizedUrl);
                            
                            // 异步下载图片
                            final int idx = imageIndex;
                            CompletableFuture<ImageInfo> downloadTask = downloadImageAsync(
                                imageInfo, articleDir, safeArticleId + "_img_" + idx
                            );
                            downloadTasks.add(downloadTask);
                            imageIndex++;
                        }
                    }
                }
                
                // 额外处理背景图片
                Elements bgElements = doc.select("*[style*=background-image]");
                for (Element elem : bgElements) {
                    if (imageIndex >= 30) break;
                    
                    String style = elem.attr("style");
                    String bgUrl = extractBackgroundUrl(style);
                    if (bgUrl != null && isValidImageUrl(bgUrl)) {
                        String normalizedUrl = normalizeUrl(bgUrl);
                        if (!processedUrls.contains(normalizedUrl)) {
                            processedUrls.add(normalizedUrl);
                            
                            ImageInfo bgInfo = new ImageInfo(bgUrl, "背景图片", "", "背景图片");
                            bgInfo.setType("background");
                            
                            final int idx = imageIndex;
                            CompletableFuture<ImageInfo> downloadTask = downloadImageAsync(
                                bgInfo, articleDir, safeArticleId + "_bg_" + idx
                            );
                            downloadTasks.add(downloadTask);
                            imageIndex++;
                        }
                    }
                }
                
                // 等待所有下载完成（设置超时）
                try {
                    CompletableFuture<Void> allDownloads = CompletableFuture.allOf(
                        downloadTasks.toArray(new CompletableFuture[0])
                    );
                    allDownloads.get(120, TimeUnit.SECONDS); // 2分钟超时
                } catch (Exception e) {
                    System.err.println("等待下载完成时出错: " + e.getMessage());
                }
                
                // 收集结果
                for (CompletableFuture<ImageInfo> task : downloadTasks) {
                    try {
                        ImageInfo result = task.getNow(null);
                        if (result != null) {
                            imageInfos.add(result);
                        }
                    } catch (Exception e) {
                        System.err.println("获取下载结果失败: " + e.getMessage());
                    }
                }
                
                long downloadedCount = imageInfos.stream().filter(img -> Boolean.TRUE.equals(img.getDownloaded())).count();
                System.out.println("成功下载 " + downloadedCount + "/" + imageInfos.size() + " 张图片");
                
            } catch (Exception e) {
                System.err.println("图片提取和下载过程出错: " + e.getMessage());
                e.printStackTrace();
            }
            
            return imageInfos;
        }, downloadExecutor);
    }
    
    /**
     * 从背景样式中提取URL
     */
    private String extractBackgroundUrl(String style) {
        if (style == null) return null;
        
        // 匹配 background-image: url(...)
        int start = style.indexOf("url(");
        if (start == -1) return null;
        
        start += 4;
        int end = style.indexOf(")", start);
        if (end == -1) return null;
        
        String url = style.substring(start, end).trim();
        // 移除引号
        url = url.replace("\"", "").replace("'", "");
        return url;
    }
    
    /**
     * 从图片元素提取信息
     */
    private ImageInfo extractImageInfo(Element imgElement, int index) {
        try {
            String src = imgElement.attr("src");
            String dataSrc = imgElement.attr("data-src");
            String dataOriginal = imgElement.attr("data-original");
            String srcset = imgElement.attr("srcset");
            String alt = imgElement.attr("alt");
            String title = imgElement.attr("title");
            String width = imgElement.attr("width");
            String height = imgElement.attr("height");
            
            // 优先级: data-original > data-src > srcset最大图 > src
            if ((src == null || src.isEmpty() || src.startsWith("data:")) && !dataOriginal.isEmpty()) {
                src = dataOriginal;
            }
            if ((src == null || src.isEmpty() || src.startsWith("data:")) && !dataSrc.isEmpty()) {
                src = dataSrc;
            }
            if ((src == null || src.isEmpty() || src.startsWith("data:")) && !srcset.isEmpty()) {
                src = extractLargestFromSrcset(srcset);
            }
            
            // 处理相对路径
            src = normalizeUrl(src);
            
            if (src == null || src.isEmpty() || src.startsWith("data:")) {
                return null;
            }
            
            ImageInfo imageInfo = new ImageInfo(src, alt, title, "");
            
            // 设置尺寸
            try {
                if (!width.isEmpty()) imageInfo.setWidth(Integer.parseInt(width.replaceAll("[^0-9]", "")));
                if (!height.isEmpty()) imageInfo.setHeight(Integer.parseInt(height.replaceAll("[^0-9]", "")));
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
     * 从srcset中提取最大图片URL
     */
    private String extractLargestFromSrcset(String srcset) {
        if (srcset == null || srcset.isEmpty()) return null;
        
        String[] parts = srcset.split(",");
        String largestUrl = null;
        int maxWidth = 0;
        
        for (String part : parts) {
            String[] tokens = part.trim().split("\\s+");
            if (tokens.length > 0) {
                String url = tokens[0];
                int width = 0;
                if (tokens.length > 1) {
                    String widthStr = tokens[1].replaceAll("[^0-9]", "");
                    try {
                        width = Integer.parseInt(widthStr);
                    } catch (NumberFormatException e) {
                        width = 0;
                    }
                }
                if (width > maxWidth || largestUrl == null) {
                    maxWidth = width;
                    largestUrl = url;
                }
            }
        }
        
        return largestUrl;
    }
    
    /**
     * 标准化URL
     */
    private String normalizeUrl(String url) {
        if (url == null) return null;
        url = url.trim();
        
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        
        // 移除URL中的转义字符
        url = url.replace("\\u002F", "/");
        
        return url;
    }
    
    /**
     * 异步下载单张图片 - 使用HttpURLConnection实现真正的下载
     */
    private CompletableFuture<ImageInfo> downloadImageAsync(ImageInfo imageInfo, String downloadDir, String fileName) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            FileOutputStream outputStream = null;
            
            try {
                String urlStr = imageInfo.getUrl();
                System.out.println("开始下载图片: " + urlStr);
                
                URL url = new URL(urlStr);
                connection = (HttpURLConnection) url.openConnection();
                
                // 设置请求头
                String userAgent = USER_AGENTS[new Random().nextInt(USER_AGENTS.length)];
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8");
                connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
                connection.setRequestProperty("Referer", urlStr);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                
                int responseCode = connection.getResponseCode();
                
                // 处理重定向
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == 307 || responseCode == 308) {
                    String newUrl = connection.getHeaderField("Location");
                    if (newUrl != null) {
                        connection.disconnect();
                        url = new URL(newUrl);
                        connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestProperty("User-Agent", userAgent);
                        connection.setConnectTimeout(15000);
                        connection.setReadTimeout(30000);
                        responseCode = connection.getResponseCode();
                    }
                }
                
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    System.err.println("下载失败，HTTP状态码: " + responseCode + " - " + urlStr);
                    imageInfo.setDownloaded(false);
                    return imageInfo;
                }
                
                // 获取内容类型和长度
                String contentType = connection.getContentType();
                long contentLength = connection.getContentLengthLong();
                
                // 确定文件扩展名
                String extension = determineExtension(urlStr, contentType);
                String localFileName = sanitizeFileName(fileName) + extension;
                Path localPath = Paths.get(downloadDir, localFileName);
                
                // 下载图片数据
                inputStream = connection.getInputStream();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[8192];
                int bytesRead;
                long totalRead = 0;
                
                while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, bytesRead);
                    totalRead += bytesRead;
                    
                    // 限制最大下载大小为15MB
                    if (totalRead > 15 * 1024 * 1024) {
                        System.err.println("图片太大，跳过: " + urlStr);
                        imageInfo.setDownloaded(false);
                        return imageInfo;
                    }
                }
                
                byte[] imageBytes = buffer.toByteArray();
                
                if (imageBytes.length > 0 && isValidImageData(imageBytes)) {
                    // 写入文件
                    Files.write(localPath, imageBytes);
                    
                    // 尝试获取图片尺寸
                    try {
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
                        if (img != null) {
                            imageInfo.setWidth(img.getWidth());
                            imageInfo.setHeight(img.getHeight());
                        }
                    } catch (Exception e) {
                        // 忽略尺寸获取错误
                    }
                    
                    imageInfo.setLocalPath(localPath.toString());
                    imageInfo.setFileSize((long) imageBytes.length);
                    imageInfo.setDownloaded(true);
                    
                    System.out.println("图片下载成功: " + localPath + " (" + formatFileSize(imageBytes.length) + ")");
                } else {
                    System.err.println("下载的数据不是有效图片: " + urlStr);
                    imageInfo.setDownloaded(false);
                }
                
            } catch (Exception e) {
                System.err.println("下载图片失败 " + imageInfo.getUrl() + ": " + e.getMessage());
                imageInfo.setDownloaded(false);
            } finally {
                try {
                    if (inputStream != null) inputStream.close();
                    if (outputStream != null) outputStream.close();
                    if (connection != null) connection.disconnect();
                } catch (Exception e) {
                    // 忽略关闭错误
                }
            }
            
            return imageInfo;
        }, downloadExecutor);
    }
    
    /**
     * 确定文件扩展名
     */
    private String determineExtension(String url, String contentType) {
        // 优先从Content-Type判断
        if (contentType != null) {
            if (contentType.contains("jpeg") || contentType.contains("jpg")) return ".jpg";
            if (contentType.contains("png")) return ".png";
            if (contentType.contains("gif")) return ".gif";
            if (contentType.contains("webp")) return ".webp";
            if (contentType.contains("bmp")) return ".bmp";
            if (contentType.contains("svg")) return ".svg";
        }
        
        // 从URL判断
        String lowerUrl = url.toLowerCase();
        int queryIndex = lowerUrl.indexOf('?');
        if (queryIndex > 0) {
            lowerUrl = lowerUrl.substring(0, queryIndex);
        }
        
        if (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg")) return ".jpg";
        if (lowerUrl.endsWith(".png")) return ".png";
        if (lowerUrl.endsWith(".gif")) return ".gif";
        if (lowerUrl.endsWith(".webp")) return ".webp";
        if (lowerUrl.endsWith(".bmp")) return ".bmp";
        if (lowerUrl.endsWith(".svg")) return ".svg";
        
        return ".jpg"; // 默认扩展名
    }
    
    /**
     * 分析图片类型
     */
    private String analyzeImageType(String src, String alt, String title, Element imgElement) {
        String combined = ((src != null ? src : "") + " " + 
                          (alt != null ? alt : "") + " " + 
                          (title != null ? title : "")).toLowerCase();
        
        // 检查父元素的class和id
        Element parent = imgElement.parent();
        String parentInfo = "";
        if (parent != null) {
            parentInfo = (parent.attr("class") + " " + parent.attr("id")).toLowerCase();
        }
        
        String allInfo = combined + " " + parentInfo;
        
        if (allInfo.contains("product") || allInfo.contains("item") || allInfo.contains("goods") || allInfo.contains("商品")) {
            return "product";
        } else if (allInfo.contains("detail") || allInfo.contains("close") || allInfo.contains("zoom") || allInfo.contains("细节")) {
            return "detail";
        } else if (allInfo.contains("scene") || allInfo.contains("lifestyle") || allInfo.contains("use") || allInfo.contains("场景")) {
            return "scene";
        } else if (allInfo.contains("avatar") || allInfo.contains("user") || allInfo.contains("profile") || allInfo.contains("头像")) {
            return "avatar";
        } else if (allInfo.contains("logo") || allInfo.contains("brand")) {
            return "logo";
        } else if (allInfo.contains("banner") || allInfo.contains("hero") || allInfo.contains("cover")) {
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
        
        if (imageInfo.getAlt() != null && !imageInfo.getAlt().isEmpty() && imageInfo.getAlt().length() > 2) {
            desc.append(imageInfo.getAlt());
        } else if (imageInfo.getTitle() != null && !imageInfo.getTitle().isEmpty() && imageInfo.getTitle().length() > 2) {
            desc.append(imageInfo.getTitle());
        } else {
            // 根据类型生成描述
            switch (imageInfo.getType()) {
                case "product": desc.append("商品展示图"); break;
                case "detail": desc.append("商品细节图"); break;
                case "scene": desc.append("使用场景图"); break;
                case "avatar": desc.append("用户头像"); break;
                case "logo": desc.append("品牌标识"); break;
                case "banner": desc.append("横幅图片"); break;
                case "background": desc.append("背景图片"); break;
                default: desc.append("内容配图");
            }
        }
        
        return desc.toString();
    }
    
    /**
     * 检查是否为有效的图片URL
     */
    private boolean isValidImageUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        if (url.startsWith("data:")) return false; // 跳过base64图片
        
        String lowerUrl = url.toLowerCase();
        
        // 过滤掉明显的非图片或装饰性图片
        if (lowerUrl.contains("1x1") || lowerUrl.contains("pixel") || lowerUrl.contains("spacer") ||
            lowerUrl.contains("tracking") || lowerUrl.contains("beacon") || lowerUrl.contains("analytics")) {
            return false;
        }
        
        // 需要以http开头或//开头
        return url.startsWith("http") || url.startsWith("//");
    }
    
    /**
     * 检查下载的数据是否为有效图片
     */
    private boolean isValidImageData(byte[] data) {
        if (data == null || data.length < 10) return false;
        
        // 检查文件头
        // JPEG: FF D8 FF
        if (data[0] == (byte)0xFF && data[1] == (byte)0xD8 && data[2] == (byte)0xFF) return true;
        
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (data[0] == (byte)0x89 && data[1] == (byte)0x50 && data[2] == (byte)0x4E && data[3] == (byte)0x47) return true;
        
        // GIF: 47 49 46 38
        if (data[0] == (byte)0x47 && data[1] == (byte)0x49 && data[2] == (byte)0x46 && data[3] == (byte)0x38) return true;
        
        // WebP: 52 49 46 46 ... 57 45 42 50
        if (data[0] == (byte)0x52 && data[1] == (byte)0x49 && data[2] == (byte)0x46 && data[3] == (byte)0x46 && data.length > 12) {
            if (data[8] == (byte)0x57 && data[9] == (byte)0x45 && data[10] == (byte)0x42 && data[11] == (byte)0x50) return true;
        }
        
        // BMP: 42 4D
        if (data[0] == (byte)0x42 && data[1] == (byte)0x4D) return true;
        
        // SVG: 检查是否包含<svg
        String header = new String(data, 0, Math.min(data.length, 200));
        if (header.contains("<svg") || header.contains("<?xml")) return true;
        
        return false;
    }
    
    /**
     * 清理文件名，移除非法字符
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "unnamed";
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_").substring(0, Math.min(fileName.length(), 50));
    }
    
    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        return String.format("%.2f MB", size / (1024.0 * 1024.0));
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
        long downloadedCount = images.stream().filter(img -> Boolean.TRUE.equals(img.getDownloaded())).count();
        long totalSize = images.stream()
            .filter(img -> img.getFileSize() != null)
            .mapToLong(ImageInfo::getFileSize)
            .sum();
        
        report.append("📷 图片内容分析报告\n");
        report.append("═══════════════════════════════════════\n");
        report.append(String.format("总图片数: %d 张\n", images.size()));
        report.append(String.format("成功下载: %d 张\n", downloadedCount));
        report.append(String.format("总大小: %s\n", formatFileSize(totalSize)));
        
        // 类型分布
        Map<String, Long> typeCount = new HashMap<>();
        for (ImageInfo img : images) {
            String type = img.getType() != null ? img.getType() : "content";
            typeCount.merge(type, 1L, Long::sum);
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
        for (int i = 0; i < Math.min(images.size(), 10); i++) {
            ImageInfo img = images.get(i);
            report.append(String.format("%d. %s %s", 
                i + 1, 
                img.getDescription(),
                Boolean.TRUE.equals(img.getDownloaded()) ? "✓" : "✗"
            ));
            
            if (Boolean.TRUE.equals(img.getDownloaded())) {
                if (img.getWidth() != null && img.getHeight() != null) {
                    report.append(String.format(" [%dx%d]", img.getWidth(), img.getHeight()));
                }
                if (img.getFileSize() != null) {
                    report.append(String.format(" (%s)", formatFileSize(img.getFileSize())));
                }
            }
            report.append("\n");
        }
        
        if (images.size() > 10) {
            report.append(String.format("... 还有 %d 张图片\n", images.size() - 10));
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
            case "background": return "背景图";
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
