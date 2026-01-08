package com.fxt.backend.service;

import com.fxt.backend.dto.ImageInfo;
import com.fxt.backend.entity.ArticleData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
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

/**
 * 统一爬取服务 - 同时爬取文字和图片
 * 用于上传文件时的自动爬取和详情页的重新爬取
 */
@Service
public class UnifiedCrawlerService {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedCrawlerService.class);

    private Playwright playwright;
    private Browser browser;
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(5);
    private final String downloadBasePath = "downloads/images/";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String MOBILE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1";

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
                    "--disable-gpu",
                    "--disable-web-security"
                )));
            logger.info("统一爬取服务初始化成功");
        } catch (Exception e) {
            logger.error("统一爬取服务初始化失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
        downloadExecutor.shutdown();
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

        // 检查是否为得物链接
        if (url.contains("poizon.com") || url.contains("dewu.com")) {
            return crawlDewuArticle(url, articleId);
        } else {
            return crawlGenericArticle(url, articleId);
        }
    }

    /**
     * 爬取得物文章 - 专门针对得物平台优化
     */
    private CrawlResult crawlDewuArticle(String url, String articleId) {
        CrawlResult result = new CrawlResult();
        result.setArticleId(articleId);
        result.setSourceUrl(url);

        if (browser == null) {
            result.setSuccess(false);
            result.setMessage("浏览器未初始化，请确保已安装Playwright");
            return result;
        }

        BrowserContext context = null;
        Page page = null;

        try {
            // 创建移动端浏览器上下文 - 得物移动端页面内容更完整
            context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(MOBILE_USER_AGENT)
                .setViewportSize(390, 844)
                .setDeviceScaleFactor(3)
                .setIsMobile(true)
                .setHasTouch(true)
                .setLocale("zh-CN"));

            page = context.newPage();

            // 收集网络请求中的图片URL
            Set<String> networkImageUrls = Collections.synchronizedSet(new LinkedHashSet<>());
            page.onResponse(response -> {
                String responseUrl = response.url();
                String contentType = response.headers().get("content-type");
                if (contentType != null && contentType.startsWith("image/") && isValidContentImage(responseUrl)) {
                    networkImageUrls.add(responseUrl);
                }
            });

            // 访问页面
            logger.info("开始爬取得物文章: {}", url);
            page.navigate(url, new Page.NavigateOptions()
                .setTimeout(30000)
                .setWaitUntil(WaitUntilState.NETWORKIDLE));

            // 等待页面渲染
            page.waitForTimeout(3000);

            // 滚动页面加载懒加载内容
            scrollPage(page);

            // 提取文字内容
            String textContent = extractDewuTextContent(page);
            result.setTextContent(textContent);

            // 提取页面标题
            String title = extractDewuTitle(page);
            result.setTitle(title);

            // 从DOM中提取图片URL
            List<String> domImageUrls = extractDewuImages(page);

            // 合并网络请求和DOM中的图片
            Set<String> allImageUrls = new LinkedHashSet<>();
            allImageUrls.addAll(networkImageUrls);
            allImageUrls.addAll(domImageUrls);

            logger.info("发现 {} 张图片 (网络: {}, DOM: {})",
                allImageUrls.size(), networkImageUrls.size(), domImageUrls.size());

            // 下载图片
            if (!allImageUrls.isEmpty()) {
                String articleDir = downloadBasePath + sanitizeFileName(articleId) + "/";
                Files.createDirectories(Paths.get(articleDir));

                List<ImageInfo> downloadedImages = downloadImages(new ArrayList<>(allImageUrls), articleDir, articleId);
                result.setImages(downloadedImages);
                result.setLocalImagesPath(articleDir);

                long successCount = downloadedImages.stream().filter(img -> Boolean.TRUE.equals(img.getDownloaded())).count();
                result.setMessage(String.format("成功爬取内容，下载 %d/%d 张图片", successCount, allImageUrls.size()));
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
            if (page != null) page.close();
            if (context != null) context.close();
        }

        return result;
    }

    /**
     * 提取得物文章的文字内容
     */
    private String extractDewuTextContent(Page page) {
        StringBuilder content = new StringBuilder();

        try {
            // 得物文章内容的常见选择器
            String[] contentSelectors = {
                ".content-detail",
                ".note-content",
                ".article-content", 
                ".post-content",
                ".detail-content",
                ".trend-content",
                "[class*='content']",
                "[class*='detail']",
                "[class*='desc']",
                "article",
                "main"
            };

            for (String selector : contentSelectors) {
                try {
                    ElementHandle element = page.querySelector(selector);
                    if (element != null) {
                        String text = element.innerText();
                        if (text != null && text.trim().length() > 30) {
                            content.append(text.trim()).append("\n\n");
                        }
                    }
                } catch (Exception e) {
                    // 继续尝试下一个选择器
                }
            }

            // 如果主选择器没找到足够内容，尝试提取所有段落
            if (content.length() < 100) {
                List<ElementHandle> paragraphs = page.querySelectorAll("p, div[class*='text'], span[class*='desc']");
                for (ElementHandle p : paragraphs) {
                    try {
                        String text = p.innerText();
                        if (text != null && text.trim().length() > 20 && !text.contains("关注") && !text.contains("点赞")) {
                            content.append(text.trim()).append("\n");
                        }
                    } catch (Exception e) {
                        // 忽略单个元素错误
                    }
                }
            }

            // 提取评论区信息（如果有）
            try {
                List<ElementHandle> comments = page.querySelectorAll("[class*='comment'] [class*='content'], [class*='reply'] [class*='text']");
                if (!comments.isEmpty() && comments.size() <= 10) {
                    content.append("\n\n--- 热门评论 ---\n");
                    int count = 0;
                    for (ElementHandle comment : comments) {
                        if (count >= 5) break;
                        String commentText = comment.innerText();
                        if (commentText != null && commentText.trim().length() > 5) {
                            content.append("• ").append(commentText.trim()).append("\n");
                            count++;
                        }
                    }
                }
            } catch (Exception e) {
                // 评论提取失败不影响主要内容
            }

        } catch (Exception e) {
            logger.warn("提取文字内容失败: {}", e.getMessage());
        }

        String result = content.toString().trim();
        return result.isEmpty() ? "未能提取到文字内容" : result;
    }

    /**
     * 提取得物文章标题
     */
    private String extractDewuTitle(Page page) {
        try {
            String[] titleSelectors = {
                ".title",
                ".post-title",
                ".article-title",
                "[class*='title']",
                "h1",
                "h2"
            };

            for (String selector : titleSelectors) {
                try {
                    ElementHandle element = page.querySelector(selector);
                    if (element != null) {
                        String title = element.innerText();
                        if (title != null && title.trim().length() > 3 && title.trim().length() < 200) {
                            return title.trim();
                        }
                    }
                } catch (Exception e) {
                    // 继续尝试
                }
            }

            String pageTitle = page.title();
            if (pageTitle != null && !pageTitle.isEmpty()) {
                pageTitle = pageTitle.replaceAll("-得物.*$", "").replaceAll("-poizon.*$", "").trim();
                return pageTitle;
            }
        } catch (Exception e) {
            logger.warn("提取标题失败: {}", e.getMessage());
        }

        return "未知标题";
    }

    /**
     * 从DOM中提取得物图片URL
     */
    private List<String> extractDewuImages(Page page) {
        List<String> imageUrls = new ArrayList<>();

        try {
            String[] imageSelectors = {
                "[class*='content'] img",
                "[class*='detail'] img",
                "[class*='post'] img",
                "[class*='trend'] img",
                "[class*='image'] img",
                "[class*='photo'] img",
                "[class*='pic'] img",
                "[class*='swiper'] img",
                "[class*='carousel'] img",
                "[class*='slide'] img",
                "img[src*='cdn']",
                "img[src*='image']"
            };

            Set<String> seenUrls = new HashSet<>();

            for (String selector : imageSelectors) {
                try {
                    List<ElementHandle> images = page.querySelectorAll(selector);
                    for (ElementHandle img : images) {
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
                            imageUrl = normalizeImageUrl(imageUrl);
                            if (!seenUrls.contains(imageUrl)) {
                                seenUrls.add(imageUrl);
                                imageUrls.add(imageUrl);
                            }
                        }
                    }
                } catch (Exception e) {
                    // 继续尝试下一个选择器
                }
            }

            // 提取背景图片
            try {
                List<ElementHandle> bgElements = page.querySelectorAll("[style*='background-image']");
                for (ElementHandle elem : bgElements) {
                    String style = elem.getAttribute("style");
                    String bgUrl = extractBackgroundUrl(style);
                    if (bgUrl != null && isValidContentImage(bgUrl)) {
                        bgUrl = normalizeImageUrl(bgUrl);
                        if (!seenUrls.contains(bgUrl)) {
                            seenUrls.add(bgUrl);
                            imageUrls.add(bgUrl);
                        }
                    }
                }
            } catch (Exception e) {
                // 背景图提取失败不影响主要功能
            }

        } catch (Exception e) {
            logger.warn("提取DOM图片失败: {}", e.getMessage());
        }

        return imageUrls;
    }

    /**
     * 通用网页爬取
     */
    private CrawlResult crawlGenericArticle(String url, String articleId) {
        CrawlResult result = new CrawlResult();
        result.setArticleId(articleId);
        result.setSourceUrl(url);

        if (browser == null) {
            result.setSuccess(false);
            result.setMessage("浏览器未初始化");
            return result;
        }

        BrowserContext context = null;
        Page page = null;

        try {
            context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(MOBILE_USER_AGENT)
                .setViewportSize(390, 844));

            page = context.newPage();

            Set<String> networkImageUrls = Collections.synchronizedSet(new LinkedHashSet<>());
            page.onResponse(response -> {
                String responseUrl = response.url();
                String contentType = response.headers().get("content-type");
                if (contentType != null && contentType.startsWith("image/") && isValidContentImage(responseUrl)) {
                    networkImageUrls.add(responseUrl);
                }
            });

            page.navigate(url, new Page.NavigateOptions()
                .setTimeout(30000)
                .setWaitUntil(WaitUntilState.NETWORKIDLE));

            page.waitForTimeout(2000);
            scrollPage(page);

            String textContent = extractGenericTextContent(page);
            result.setTextContent(textContent);
            result.setTitle(page.title());

            List<String> domImageUrls = extractGenericImages(page);
            Set<String> allImageUrls = new LinkedHashSet<>();
            allImageUrls.addAll(networkImageUrls);
            allImageUrls.addAll(domImageUrls);

            if (!allImageUrls.isEmpty()) {
                String articleDir = downloadBasePath + sanitizeFileName(articleId) + "/";
                Files.createDirectories(Paths.get(articleDir));

                List<ImageInfo> downloadedImages = downloadImages(new ArrayList<>(allImageUrls), articleDir, articleId);
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
            if (page != null) page.close();
            if (context != null) context.close();
        }

        return result;
    }

    private String extractGenericTextContent(Page page) {
        StringBuilder content = new StringBuilder();

        String[] selectors = {"article", ".content", ".post-content", "main", "p"};

        for (String selector : selectors) {
            try {
                List<ElementHandle> elements = page.querySelectorAll(selector);
                for (ElementHandle elem : elements) {
                    String text = elem.innerText();
                    if (text != null && text.trim().length() > 30) {
                        content.append(text.trim()).append("\n\n");
                    }
                }
                if (content.length() > 200) break;
            } catch (Exception e) {
                // 继续
            }
        }

        return content.toString().trim();
    }

    private List<String> extractGenericImages(Page page) {
        List<String> imageUrls = new ArrayList<>();

        try {
            List<ElementHandle> images = page.querySelectorAll("img");
            for (ElementHandle img : images) {
                String src = img.getAttribute("src");
                String dataSrc = img.getAttribute("data-src");

                String imageUrl = isValidImageUrl(dataSrc) ? dataSrc : src;
                if (imageUrl != null && isValidContentImage(imageUrl)) {
                    imageUrls.add(normalizeImageUrl(imageUrl));
                }
            }
        } catch (Exception e) {
            logger.warn("提取图片失败: {}", e.getMessage());
        }

        return imageUrls;
    }

    private void scrollPage(Page page) {
        try {
            for (int i = 0; i < 5; i++) {
                page.evaluate("window.scrollBy(0, window.innerHeight)");
                page.waitForTimeout(800);
            }
            page.evaluate("window.scrollTo(0, 0)");
            page.waitForTimeout(500);
        } catch (Exception e) {
            logger.warn("滚动页面失败: {}", e.getMessage());
        }
    }

    private List<ImageInfo> downloadImages(List<String> urls, String downloadDir, String articleId) {
        List<ImageInfo> results = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        int index = 0;
        for (String url : urls) {
            if (index >= 20) break;

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
                    if (totalBytes > 15 * 1024 * 1024) break;
                }

                info.setLocalPath(filePath.toString());
                info.setFileSize(totalBytes);
                info.setDownloaded(true);
                info.setType(analyzeImageType(imageUrl));
                info.setDescription(String.format("图片%d - %s", index + 1, info.getType() != null ? info.getType() : "内容图"));

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

    private String normalizeImageUrl(String url) {
        if (url == null) return null;
        url = url.trim();
        if (url.startsWith("//")) url = "https:" + url;
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

    public static class CrawlResult {
        private String articleId;
        private String sourceUrl;
        private boolean success;
        private String message;
        private String title;
        private String textContent;
        private List<ImageInfo> images = new ArrayList<>();
        private String localImagesPath;

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