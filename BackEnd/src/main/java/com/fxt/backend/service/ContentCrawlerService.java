package com.fxt.backend.service;

import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.dto.ImageInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

@Service
public class ContentCrawlerService {
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Autowired
    private EnhancedImageDownloadService imageDownloadService;
    
    @Autowired
    private DewuImageCrawlerService dewuImageCrawlerService;
    
    public ContentCrawlerService() {
        this.webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
            .build();
        this.objectMapper = new ObjectMapper();
    }
    
    public CompletableFuture<String> crawlContent(String url) {
        if (url == null || url.isEmpty()) {
            return CompletableFuture.completedFuture("");
        }
        
        // 尝试多个用户代理
        String[] userAgents = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"
        };
        
        return CompletableFuture.supplyAsync(() -> {
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    String userAgent = userAgents[attempt % userAgents.length];
                    System.out.println("尝试抓取 (第" + (attempt + 1) + "次): " + url);
                    
                    String content = webClient.get()
                        .uri(url)
                        .header("User-Agent", userAgent)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .header("Accept-Encoding", "gzip, deflate, br")
                        .header("Connection", "keep-alive")
                        .header("Upgrade-Insecure-Requests", "1")
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(15))
                        .block();
                    
                    if (content != null && !content.trim().isEmpty()) {
                        String extractedContent = extractContent(content);
                        System.out.println("抓取成功，内容长度: " + extractedContent.length());
                        return extractedContent;
                    }
                } catch (Exception e) {
                    System.err.println("第" + (attempt + 1) + "次抓取失败: " + e.getMessage());
                    if (attempt == 2) { // 最后一次尝试
                        throw new RuntimeException("所有抓取尝试都失败了: " + e.getMessage(), e);
                    }
                    
                    // 等待一下再重试
                    try {
                        Thread.sleep(1000 * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            return "";
        });
    }
    
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
            String content = crawlContent(articleLink).get();
            
            if (content != null && !content.trim().isEmpty()) {
                // 解析HTML获取Document对象用于图片下载
                Document doc = Jsoup.parse(content);
                
                // 提取文本内容
                String textContent = extractContent(content);
                article.setContent(textContent);
                
                // 异步下载图片 - 优先使用您的Selenium爬取方案
                String articleId = article.getDataId() != null ? article.getDataId() : "article_" + article.getId();
                
                // 检查是否为得物链接，使用专门的爬取服务
                if (articleLink.contains("dewu.com") || articleLink.contains("得物")) {
                    CompletableFuture<DewuImageCrawlerService.ImageCrawlResult> dewuImageFuture = 
                        dewuImageCrawlerService.crawlAndDownloadImages(articleLink, articleId);
                    
                    dewuImageFuture.thenAccept(crawlResult -> {
                        try {
                            if ("SUCCESS".equals(crawlResult.getStatus())) {
                                // 转换为通用格式
                                List<ImageInfo> images = convertDewuResultToImageInfo(crawlResult);
                                String imagesJson = objectMapper.writeValueAsString(images);
                                article.setImagesInfo(imagesJson);
                                
                                // 添加得物专用分析报告
                                String enhancedContent = textContent + "\n\n" + crawlResult.getAnalysisReport();
                                article.setContent(enhancedContent);
                                
                                article.setImagesDownloaded(crawlResult.getSuccessCount() > 0);
                                article.setLocalImagesPath(crawlResult.getLocalPath());
                                
                                System.out.println("得物图片爬取完成，共下载 " + crawlResult.getSuccessCount() + " 张图片");
                            } else {
                                System.err.println("得物图片爬取失败: " + crawlResult.getMessage());
                                article.setImagesDownloaded(false);
                            }
                        } catch (Exception e) {
                            System.err.println("处理得物爬取结果失败: " + e.getMessage());
                            article.setImagesDownloaded(false);
                        }
                    }).exceptionally(throwable -> {
                        System.err.println("得物图片爬取过程出错: " + throwable.getMessage());
                        article.setImagesDownloaded(false);
                        return null;
                    });
                } else {
                    // 使用通用图片下载服务
                    CompletableFuture<List<ImageInfo>> imageDownloadFuture = 
                        imageDownloadService.extractAndDownloadImages(doc, articleId);
                    
                    imageDownloadFuture.thenAccept(images -> {
                        try {
                            if (!images.isEmpty()) {
                                String imagesJson = objectMapper.writeValueAsString(images);
                                article.setImagesInfo(imagesJson);
                                
                                String imageReport = imageDownloadService.generateImageReport(images);
                                article.setContent(textContent + "\n\n" + imageReport);
                                
                                long downloadedCount = images.stream().mapToLong(img -> img.getDownloaded() ? 1 : 0).sum();
                                article.setImagesDownloaded(downloadedCount > 0);
                                
                                if (downloadedCount > 0) {
                                    String basePath = "downloads/images/" + articleId + "/";
                                    article.setLocalImagesPath(basePath);
                                }
                                
                                System.out.println("通用图片下载完成，共下载 " + downloadedCount + " 张图片");
                            } else {
                                article.setImagesDownloaded(false);
                                System.out.println("未发现可下载的图片");
                            }
                        } catch (Exception e) {
                            System.err.println("保存图片信息失败: " + e.getMessage());
                            article.setImagesDownloaded(false);
                        }
                    }).exceptionally(throwable -> {
                        System.err.println("图片下载过程出错: " + throwable.getMessage());
                        article.setImagesDownloaded(false);
                        return null;
                    });
                }
                
                article.setCrawlStatus("SUCCESS");
                article.setCrawlError(null);
                System.out.println("内容抓取成功，长度: " + textContent.length());
            } else {
                // 内容为空，使用标题作为备选
                String fallbackContent = "标题: " + (article.getTitle() != null ? article.getTitle() : "无标题");
                fallbackContent += "\n链接: " + articleLink;
                fallbackContent += "\n注意: 无法抓取到文章内容，可能是网站有反爬虫保护";
                
                article.setContent(fallbackContent);
                article.setCrawlStatus("PARTIAL");
                article.setCrawlError("抓取到的内容为空，使用标题作为替代");
                article.setImagesDownloaded(false);
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
            article.setImagesDownloaded(false);
            
            System.err.println("内容抓取失败 - 文章: " + article.getTitle() + 
                             ", 链接: " + articleLink + 
                             ", 错误: " + e.getMessage());
        }
    }
    
    private String extractContent(String html) {
        try {
            Document doc = Jsoup.parse(html);
            
            // 移除脚本和样式
            doc.select("script, style, nav, footer, header, aside, .ad, .advertisement, .sidebar").remove();
            
            // 尝试提取主要内容
            String content = extractMainContent(doc);
            
            if (content.isEmpty()) {
                // 如果没找到主要内容，尝试提取标题和描述
                content = extractFallbackContent(doc);
            }
            
            if (content.isEmpty()) {
                // 最后尝试提取所有文本
                content = doc.body().text();
            }
            
            // 提取图片信息
            String imageInfo = extractImageInfo(doc);
            if (!imageInfo.isEmpty()) {
                content = content + "\n\n" + imageInfo;
            }
            
            // 清理和限制长度
            content = content.replaceAll("\\s+", " ").trim();
            
            // 如果内容太短，可能是抓取失败
            if (content.length() < 50) {
                return "抓取到的内容过短: " + content;
            }
            
            if (content.length() > 8000) {
                content = content.substring(0, 8000) + "...\n\n[内容已截断，完整内容请查看原文链接]";
            }
            
            return content;
        } catch (Exception e) {
            return "内容解析失败: " + e.getMessage();
        }
    }
    
    private String extractImageInfo(Document doc) {
        StringBuilder imageInfo = new StringBuilder();
        
        // 查找图片 - 使用更全面的选择器
        Elements images = doc.select("img, picture source, [style*=background-image], .image, .photo, .pic");
        
        // 也检查内联样式中的背景图片
        Elements elementsWithBgImage = doc.select("*[style*=background-image]");
        
        int totalImageCount = images.size() + elementsWithBgImage.size();
        
        if (totalImageCount > 0) {
            imageInfo.append("📷 图片内容分析:\n");
            
            int validImageCount = 0;
            
            // 处理img标签
            for (Element img : images) {
                if (validImageCount >= 10) break; // 最多分析10张图片
                
                String src = img.attr("src");
                String dataSrc = img.attr("data-src"); // 懒加载图片
                String alt = img.attr("alt");
                String title = img.attr("title");
                
                // 优先使用data-src（懒加载）
                if (src.isEmpty() && !dataSrc.isEmpty()) {
                    src = dataSrc;
                }
                
                // 过滤掉明显的装饰性图片和小图标
                if (isDecorativeImage(src, alt, title)) {
                    continue;
                }
                
                validImageCount++;
                imageInfo.append(String.format("  图片%d: ", validImageCount));
                
                // 分析图片内容
                String imageDescription = analyzeImageContent(src, alt, title, img);
                imageInfo.append(imageDescription);
                
                // 添加图片尺寸信息（如果有）
                String width = img.attr("width");
                String height = img.attr("height");
                if (!width.isEmpty() && !height.isEmpty()) {
                    imageInfo.append(String.format(" [尺寸: %s×%s]", width, height));
                }
                
                imageInfo.append("\n");
            }
            
            // 处理背景图片
            for (Element elem : elementsWithBgImage) {
                if (validImageCount >= 10) break;
                
                String style = elem.attr("style");
                if (style.contains("background-image")) {
                    validImageCount++;
                    imageInfo.append(String.format("  背景图%d: 样式背景图片\n", validImageCount - images.size()));
                }
            }
            
            if (validImageCount > 0) {
                imageInfo.append(String.format("共发现 %d 张相关图片", validImageCount));
                
                // 根据图片数量给出评价
                if (validImageCount >= 5) {
                    imageInfo.append(" - 图片丰富，视觉效果佳");
                } else if (validImageCount >= 3) {
                    imageInfo.append(" - 图片适中，内容充实");
                } else {
                    imageInfo.append(" - 图片较少，可考虑增加");
                }
                imageInfo.append("\n");
                
                // 分析图片类型分布
                analyzeImageTypes(images, imageInfo);
            } else {
                imageInfo.append("未检测到有效的内容图片\n");
            }
        }
        
        // 查找视频内容 - 扩展检测范围
        Elements videos = doc.select("video, iframe[src*=video], iframe[src*=youtube], iframe[src*=bilibili], " +
                                   "iframe[src*=youku], iframe[src*=iqiyi], .video, .player, [class*=video]");
        
        if (!videos.isEmpty()) {
            imageInfo.append("\n🎥 视频内容分析:\n");
            int videoCount = 0;
            
            for (Element video : videos) {
                if (videoCount >= 5) break; // 最多分析5个视频
                
                String src = video.attr("src");
                String title = video.attr("title");
                String tagName = video.tagName();
                
                videoCount++;
                imageInfo.append(String.format("  视频%d: ", videoCount));
                
                if (!title.isEmpty()) {
                    imageInfo.append(title);
                } else if (!src.isEmpty()) {
                    // 从URL推断视频平台
                    if (src.contains("youtube")) {
                        imageInfo.append("YouTube视频");
                    } else if (src.contains("bilibili")) {
                        imageInfo.append("B站视频");
                    } else if (src.contains("youku")) {
                        imageInfo.append("优酷视频");
                    } else {
                        imageInfo.append("视频内容");
                    }
                    imageInfo.append(" [链接: ").append(src).append("]");
                } else {
                    imageInfo.append("嵌入式视频内容");
                }
                
                imageInfo.append("\n");
            }
            
            if (videoCount > 0) {
                imageInfo.append(String.format("共发现 %d 个视频内容 - 多媒体内容丰富\n", videoCount));
            }
        }
        
        // 检查是否为图文混合内容
        if (totalImageCount > 0) {
            String textContent = doc.body().text();
            if (textContent.length() > 100) {
                imageInfo.append("\n📝 内容类型: 图文结合 - 内容形式丰富，用户体验佳\n");
            }
        }
        
        return imageInfo.toString();
    }
    
    private boolean isDecorativeImage(String src, String alt, String title) {
        if (src == null) src = "";
        if (alt == null) alt = "";
        if (title == null) title = "";
        
        String combined = (src + " " + alt + " " + title).toLowerCase();
        
        // 过滤条件
        return combined.contains("icon") || 
               combined.contains("logo") || 
               combined.contains("avatar") || 
               combined.contains("button") || 
               combined.contains("arrow") ||
               combined.contains("loading") ||
               combined.contains("spinner") ||
               src.endsWith(".gif") ||
               src.contains("1x1") ||
               src.contains("pixel") ||
               (src.contains("icon") && src.contains("16")) ||
               (src.contains("icon") && src.contains("24")) ||
               (src.contains("icon") && src.contains("32"));
    }
    
    private String analyzeImageContent(String src, String alt, String title, Element img) {
        StringBuilder description = new StringBuilder();
        
        // 优先使用alt文本
        if (!alt.isEmpty() && alt.length() > 2) {
            description.append(alt);
        } else if (!title.isEmpty() && title.length() > 2) {
            description.append(title);
        } else {
            // 从URL分析图片类型
            String filename = src.substring(src.lastIndexOf("/") + 1).toLowerCase();
            
            if (filename.contains("product") || filename.contains("item") || filename.contains("goods")) {
                description.append("商品展示图");
            } else if (filename.contains("user") || filename.contains("person") || filename.contains("avatar")) {
                description.append("用户相关图片");
            } else if (filename.contains("detail") || filename.contains("close") || filename.contains("zoom")) {
                description.append("商品细节图");
            } else if (filename.contains("scene") || filename.contains("lifestyle") || filename.contains("use")) {
                description.append("使用场景图");
            } else if (filename.contains("compare") || filename.contains("vs")) {
                description.append("对比图片");
            } else if (filename.contains("before") || filename.contains("after")) {
                description.append("前后对比图");
            } else {
                description.append("内容配图");
            }
        }
        
        // 检查图片的父元素，获取更多上下文
        Element parent = img.parent();
        if (parent != null) {
            String parentClass = parent.attr("class");
            String parentText = parent.text();
            
            if (parentClass.contains("gallery") || parentClass.contains("carousel")) {
                description.append("(图片轮播)");
            } else if (parentClass.contains("main") || parentClass.contains("primary")) {
                description.append("(主要图片)");
            } else if (!parentText.isEmpty() && parentText.length() < 50) {
                description.append("(").append(parentText.trim()).append(")");
            }
        }
        
        return description.toString();
    }
    
    private void analyzeImageTypes(Elements images, StringBuilder imageInfo) {
        int productImages = 0;
        int sceneImages = 0;
        int detailImages = 0;
        
        for (Element img : images) {
            String src = img.attr("src").toLowerCase();
            String alt = img.attr("alt").toLowerCase();
            String combined = src + " " + alt;
            
            if (combined.contains("product") || combined.contains("item") || combined.contains("goods")) {
                productImages++;
            } else if (combined.contains("scene") || combined.contains("lifestyle") || combined.contains("use")) {
                sceneImages++;
            } else if (combined.contains("detail") || combined.contains("close") || combined.contains("zoom")) {
                detailImages++;
            }
        }
        
        if (productImages > 0 || sceneImages > 0 || detailImages > 0) {
            imageInfo.append("图片类型分布: ");
            if (productImages > 0) imageInfo.append("商品图×").append(productImages).append(" ");
            if (sceneImages > 0) imageInfo.append("场景图×").append(sceneImages).append(" ");
            if (detailImages > 0) imageInfo.append("细节图×").append(detailImages).append(" ");
            imageInfo.append("\n");
        }
    }
    
    private String extractFallbackContent(Document doc) {
        StringBuilder content = new StringBuilder();
        
        // 尝试提取标题
        Elements titles = doc.select("h1, h2, .title, .post-title, .article-title");
        if (!titles.isEmpty()) {
            content.append("标题: ").append(titles.first().text()).append("\n\n");
        }
        
        // 尝试提取描述或摘要
        Elements descriptions = doc.select("meta[name=description], .description, .summary, .excerpt");
        for (Element desc : descriptions) {
            String text = desc.hasAttr("content") ? desc.attr("content") : desc.text();
            if (text.length() > 20) {
                content.append("摘要: ").append(text).append("\n\n");
                break;
            }
        }
        
        // 尝试提取段落
        Elements paragraphs = doc.select("p");
        int count = 0;
        for (Element p : paragraphs) {
            String text = p.text().trim();
            if (text.length() > 30) {
                content.append(text).append("\n\n");
                count++;
                if (count >= 3) break; // 最多提取3个段落
            }
        }
        
        return content.toString().trim();
    }
    
    private String extractMainContent(Document doc) {
        // 尝试多种选择器来提取主要内容
        String[] selectors = {
            "article",
            ".content",
            ".post-content",
            ".entry-content",
            ".article-content",
            "main",
            "#content",
            ".main-content",
            "p"
        };
        
        for (String selector : selectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                StringBuilder content = new StringBuilder();
                for (Element element : elements) {
                    String text = element.text().trim();
                    if (text.length() > 50) { // 只保留有意义的内容
                        content.append(text).append(" ");
                    }
                }
                if (content.length() > 100) {
                    return content.toString().trim();
                }
            }
        }
        
        return "";
    }
    
    /**
     * 将得物爬取结果转换为通用ImageInfo格式
     */
    private List<ImageInfo> convertDewuResultToImageInfo(DewuImageCrawlerService.ImageCrawlResult crawlResult) {
        List<ImageInfo> imageInfos = new ArrayList<>();
        
        if (crawlResult.getDownloadedImages() != null) {
            for (DewuImageCrawlerService.ImageDownloadInfo dewuInfo : crawlResult.getDownloadedImages()) {
                ImageInfo imageInfo = new ImageInfo();
                imageInfo.setUrl(dewuInfo.getUrl());
                imageInfo.setLocalPath(dewuInfo.getLocalPath());
                imageInfo.setFileSize(dewuInfo.getFileSize());
                imageInfo.setDownloaded(dewuInfo.isSuccess());
                imageInfo.setType(dewuInfo.getImageType());
                imageInfo.setWidth(dewuInfo.getEstimatedWidth());
                imageInfo.setHeight(dewuInfo.getEstimatedHeight());
                
                // 生成描述
                String description = String.format("得物图片 - %s", 
                    dewuInfo.getImageType() != null ? getTypeDisplayName(dewuInfo.getImageType()) : "内容图");
                if (dewuInfo.getFormat() != null) {
                    description += " [" + dewuInfo.getFormat() + "]";
                }
                imageInfo.setDescription(description);
                
                imageInfos.add(imageInfo);
            }
        }
        
        return imageInfos;
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
}