package com.fxt.backend.service;

import com.fxt.backend.config.SeleniumConfig;
import com.fxt.backend.dto.ImageInfo;
import com.fxt.backend.entity.ArticleData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * 统一爬取服务 - 使用Selenium + ChromeDriver
 * 整合了所有爬取功能，替代之前分散的多个服务
 */
@Service
public class SeleniumCrawlerService {

    private static final Logger logger = LoggerFactory.getLogger(SeleniumCrawlerService.class);

    @Autowired
    private SeleniumConfig config;

    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(5);
    private final String downloadBasePath = "downloads/images/";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String MOBILE_USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1";

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(downloadBasePath));
            // 自动下载和配置ChromeDriver
            WebDriverManager.chromedriver().setup();
            logger.info("Selenium爬取服务初始化成功");
        } catch (Exception e) {
            logger.error("初始化失败: {}", e.getMessage());
        }
    }

    /**
     * 创建ChromeDriver实例
     */
    private WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();

        if (config.isHeadless()) {
            options.addArguments("--headless=new");
        }

        options.addArguments(
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--disable-gpu",
            "--disable-blink-features=AutomationControlled",
            "--window-size=390,844",
            "--user-agent=" + MOBILE_USER_AGENT
        );

        // 禁用自动化检测
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        // 添加移动设备模拟
        Map<String, Object> mobileEmulation = new HashMap<>();
        mobileEmulation.put("deviceName", "iPhone 12 Pro");
        options.setExperimentalOption("mobileEmulation", mobileEmulation);

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(config.getPageLoadTimeout()));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(config.getWaitTimeout()));

        return driver;
    }

    /**
     * 统一爬取方法 - 同时获取文字和图片
     */
    public CrawlResult crawlArticle(ArticleData article) {
        String url = article.getArticleLink();
        String articleId = article.getDataId() != null ? article.getDataId() : "article_" + article.getId();

        CrawlResult result = new CrawlResult();
        result.setArticleId(articleId);
        result.setSourceUrl(url);

        if (url == null || url.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("文章链接为空");
            return result;
        }

        // 根据URL类型选择爬取策略
        if (url.contains("poizon.com") || url.contains("dewu.com")) {
            return crawlDewuArticle(url, articleId);
        } else {
            return crawlGenericArticle(url, articleId);
        }
    }

    /**
     * 爬取得物文章
     */
    private CrawlResult crawlDewuArticle(String url, String articleId) {
        CrawlResult result = new CrawlResult();
        result.setArticleId(articleId);
        result.setSourceUrl(url);

        WebDriver driver = null;

        try {
            driver = createDriver();
            logger.info("开始爬取得物文章: {}", url);

            // 访问页面
            driver.get(url);

            // 等待页面加载
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(config.getWaitTimeout()));
            wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("[class*='content']")),
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("img")),
                ExpectedConditions.presenceOfElementLocated(By.tagName("body"))
            ));

            // 等待额外时间让动态内容加载
            Thread.sleep(3000);

            // 滚动页面加载懒加载内容
            scrollPage(driver);

            // 提取文字内容
            String textContent = extractTextContent(driver);
            result.setTextContent(textContent);

            // 提取标题
            String title = extractTitle(driver);
            result.setTitle(title);

            // 提取图片URL
            List<String> imageUrls = extractImageUrls(driver);
            logger.info("发现 {} 张图片", imageUrls.size());

            // 下载图片
            if (!imageUrls.isEmpty()) {
                String articleDir = downloadBasePath + sanitizeFileName(articleId) + "/";
                Files.createDirectories(Paths.get(articleDir));

                List<ImageInfo> downloadedImages = downloadImages(imageUrls, articleDir, articleId);
                result.setImages(downloadedImages);
                result.setLocalImagesPath(articleDir);

                long successCount = downloadedImages.stream()
                    .filter(img -> Boolean.TRUE.equals(img.getDownloaded()))
                    .count();
                result.setMessage(String.format("成功爬取内容，下载 %d/%d 张图片", successCount, imageUrls.size()));
            } else {
                result.setMessage("成功爬取文字内容，未发现图片");
            }

            result.setSuccess(true);
            logger.info("爬取完成: {}", result.getMessage());

        } catch (Exception e) {
            logger.error("爬取失败: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setMessage("爬取失败: " + e.getMessage());
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    logger.warn("关闭浏览器失败: {}", e.getMessage());
                }
            }
        }

        return result;
    }

    /**
     * 通用网页爬取
     */
    private CrawlResult crawlGenericArticle(String url, String articleId) {
        CrawlResult result = new CrawlResult();
        result.setArticleId(articleId);
        result.setSourceUrl(url);

        WebDriver driver = null;

        try {
            driver = createDriver();
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(config.getWaitTimeout()));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            Thread.sleep(2000);
            scrollPage(driver);

            String textContent = extractTextContent(driver);
            result.setTextContent(textContent);
            result.setTitle(driver.getTitle());

            List<String> imageUrls = extractImageUrls(driver);

            if (!imageUrls.isEmpty()) {
                String articleDir = downloadBasePath + sanitizeFileName(articleId) + "/";
                Files.createDirectories(Paths.get(articleDir));

                List<ImageInfo> downloadedImages = downloadImages(imageUrls, articleDir, articleId);
                result.setImages(downloadedImages);
                result.setLocalImagesPath(articleDir);
            }

            result.setSuccess(true);
            result.setMessage("爬取完成");

        } catch (Exception e) {
            logger.error("通用爬取失败: {}", e.getMessage());
            result.setSuccess(false);
            result.setMessage("爬取失败: " + e.getMessage());
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    // 忽略
                }
            }
        }

        return result;
    }

    /**
     * 滚动页面触发懒加载
     */
    private void scrollPage(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            for (int i = 0; i < 5; i++) {
                js.executeScript("window.scrollBy(0, window.innerHeight)");
                Thread.sleep(800);
            }

            // 滚回顶部
            js.executeScript("window.scrollTo(0, 0)");
            Thread.sleep(500);
        } catch (Exception e) {
            logger.warn("滚动页面失败: {}", e.getMessage());
        }
    }

    /**
     * 提取文字内容
     */
    private String extractTextContent(WebDriver driver) {
        StringBuilder content = new StringBuilder();

        // 内容选择器优先级列表
        String[] contentSelectors = {
            ".content-detail", ".note-content", ".article-content",
            ".post-content", ".detail-content", ".trend-content",
            "[class*='content']", "[class*='detail']", "[class*='desc']",
            "article", "main", ".main-content"
        };

        for (String selector : contentSelectors) {
            try {
                List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                for (WebElement element : elements) {
                    String text = element.getText();
                    if (text != null && text.trim().length() > 30) {
                        content.append(text.trim()).append("\n\n");
                    }
                }
                if (content.length() > 200) break;
            } catch (Exception e) {
                // 继续尝试下一个选择器
            }
        }

        // 如果没找到足够内容，提取所有段落
        if (content.length() < 100) {
            try {
                List<WebElement> paragraphs = driver.findElements(By.cssSelector("p, div[class*='text']"));
                for (WebElement p : paragraphs) {
                    String text = p.getText();
                    if (text != null && text.trim().length() > 20) {
                        content.append(text.trim()).append("\n");
                    }
                }
            } catch (Exception e) {
                // 忽略
            }
        }

        String result = content.toString().trim();
        return result.isEmpty() ? "未能提取到文字内容" : result;
    }

    /**
     * 提取标题
     */
    private String extractTitle(WebDriver driver) {
        String[] titleSelectors = {
            ".title", ".post-title", ".article-title",
            "[class*='title']", "h1", "h2"
        };

        for (String selector : titleSelectors) {
            try {
                WebElement element = driver.findElement(By.cssSelector(selector));
                String title = element.getText();
                if (title != null && title.trim().length() > 3 && title.trim().length() < 200) {
                    return title.trim();
                }
            } catch (Exception e) {
                // 继续尝试
            }
        }

        // 使用页面标题作为备选
        String pageTitle = driver.getTitle();
        if (pageTitle != null && !pageTitle.isEmpty()) {
            return pageTitle.replaceAll("-得物.*$", "").replaceAll("-poizon.*$", "").trim();
        }

        return "未知标题";
    }

    /**
     * 提取图片URL
     */
    private List<String> extractImageUrls(WebDriver driver) {
        Set<String> imageUrls = new LinkedHashSet<>();

        // 图片选择器列表
        String[] imageSelectors = {
            "[class*='content'] img", "[class*='detail'] img",
            "[class*='post'] img", "[class*='image'] img",
            "[class*='photo'] img", "[class*='pic'] img",
            "[class*='swiper'] img", "[class*='carousel'] img",
            "img[src*='cdn']", "img[src*='image']", "img"
        };

        for (String selector : imageSelectors) {
            try {
                List<WebElement> images = driver.findElements(By.cssSelector(selector));
                for (WebElement img : images) {
                    String src = img.getAttribute("src");
                    String dataSrc = img.getAttribute("data-src");
                    String dataOriginal = img.getAttribute("data-original");
                    String lazySrc = img.getAttribute("lazy-src");

                    String imageUrl = null;
                    if (isValidImageUrl(dataOriginal)) imageUrl = dataOriginal;
                    else if (isValidImageUrl(dataSrc)) imageUrl = dataSrc;
                    else if (isValidImageUrl(lazySrc)) imageUrl = lazySrc;
                    else if (isValidImageUrl(src)) imageUrl = src;

                    if (imageUrl != null && isValidContentImage(imageUrl)) {
                        imageUrls.add(normalizeImageUrl(imageUrl));
                    }
                }
            } catch (Exception e) {
                // 继续尝试下一个选择器
            }
        }

        // 提取背景图片
        try {
            List<WebElement> bgElements = driver.findElements(By.cssSelector("[style*='background-image']"));
            for (WebElement elem : bgElements) {
                String style = elem.getAttribute("style");
                String bgUrl = extractBackgroundUrl(style);
                if (bgUrl != null && isValidContentImage(bgUrl)) {
                    imageUrls.add(normalizeImageUrl(bgUrl));
                }
            }
        } catch (Exception e) {
            // 忽略
        }

        return new ArrayList<>(imageUrls);
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
            conn.setRequestProperty("User-Agent", MOBILE_USER_AGENT);
            conn.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*");
            conn.setRequestProperty("Referer", "https://www.dewu.com/");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);

            int responseCode = conn.getResponseCode();

            // 处理重定向
            if (responseCode == 301 || responseCode == 302 || responseCode == 307) {
                String newUrl = conn.getHeaderField("Location");
                if (newUrl != null) {
                    conn.disconnect();
                    return downloadSingleImage(newUrl, downloadDir, articleId, index);
                }
            }

            if (responseCode != 200) {
                info.setDownloaded(false);
                return info;
            }

            String contentType = conn.getContentType();
            String extension = guessExtension(imageUrl, contentType);
            String filename = String.format("%s_%03d%s", sanitizeFileName(articleId), index, extension);
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
                info.setDescription(String.format("图片%d - %s", index + 1,
                    info.getType() != null ? info.getType() : "内容图"));

                logger.debug("下载成功: {} ({} KB)", filename, totalBytes / 1024);
            }

        } catch (Exception e) {
            logger.warn("下载图片失败 {}: {}", imageUrl, e.getMessage());
            info.setDownloaded(false);
        } finally {
            if (conn != null) conn.disconnect();
        }

        return info;
    }

    // ==================== 辅助方法 ====================

    private String normalizeImageUrl(String url) {
        if (url == null) return null;
        url = url.trim();
        if (url.startsWith("//")) url = "https:" + url;
        // 移除压缩参数获取原图
        int paramIndex = url.indexOf("?x-oss-process=");
        if (paramIndex > 0) url = url.substring(0, paramIndex);
        return url;
    }

    private boolean isValidImageUrl(String url) {
        return url != null && !url.isEmpty() && !url.startsWith("data:") &&
                (url.startsWith("http") || url.startsWith("//"));
    }

    private boolean isValidContentImage(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        // 过滤掉图标、logo等非内容图片
        return !lower.contains("icon") && !lower.contains("logo") &&
               !lower.contains("avatar") && !lower.contains("emoji") &&
               !lower.contains("_xs") && !lower.contains("thumb") &&
               !lower.contains("1x1") && !lower.contains("pixel") &&
               !lower.contains("loading") && !lower.contains("placeholder") &&
               !lower.contains("16x16") && !lower.contains("32x32") &&
               !lower.contains("favicon");
    }

    private String extractBackgroundUrl(String style) {
        if (style == null) return null;
        int start = style.indexOf("url(");
        if (start == -1) return null;
        start += 4;
        int end = style.indexOf(")", start);
        if (end == -1) return null;
        String url = style.substring(start, end).trim();
        return url.replace("\"", "").replace("'", "");
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
        return name.replaceAll("[^a-zA-Z0-9._-]", "_").substring(0, Math.min(name.length(), 50));
    }

    /**
     * 将爬取结果应用到文章对象
     */
    public void applyCrawlResultToArticle(ArticleData article, CrawlResult result) {
        if (result.isSuccess()) {
            article.setContent(result.getTextContent());
            article.setCrawlStatus("SUCCESS");
            article.setCrawlError(null);

            if (result.getImages() != null && !result.getImages().isEmpty()) {
                try {
                    article.setImagesInfo(objectMapper.writeValueAsString(result.getImages()));
                    long downloadedCount = result.getImages().stream()
                        .filter(img -> Boolean.TRUE.equals(img.getDownloaded()))
                        .count();
                    article.setImagesDownloaded(downloadedCount > 0);
                    article.setLocalImagesPath(result.getLocalImagesPath());
                } catch (Exception e) {
                    logger.error("序列化图片信息失败: {}", e.getMessage());
                }
            }
        } else {
            article.setCrawlStatus("FAILED");
            article.setCrawlError(result.getMessage());
        }
    }

    // ==================== 结果类 ====================

    public static class CrawlResult {
        private String articleId;
        private String sourceUrl;
        private boolean success;
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