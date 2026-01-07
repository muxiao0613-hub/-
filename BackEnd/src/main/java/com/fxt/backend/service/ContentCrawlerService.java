package com.fxt.backend.service;

import com.fxt.backend.entity.ArticleData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Service
public class ContentCrawlerService {
    
    private final WebClient webClient;
    
    public ContentCrawlerService() {
        this.webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
            .build();
    }
    
    public CompletableFuture<String> crawlContent(String url) {
        if (url == null || url.isEmpty()) {
            return CompletableFuture.completedFuture("");
        }
        
        return webClient.get()
            .uri(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(10))
            .map(this::extractContent)
            .onErrorReturn("")
            .toFuture();
    }
    
    public void crawlAllContent(ArticleData article) {
        try {
            String content = crawlContent(article.getArticleLink()).get();
            article.setContent(content);
        } catch (Exception e) {
            // 如果抓取失败，使用标题作为内容
            article.setContent(article.getTitle() != null ? article.getTitle() : "");
        }
    }
    
    private String extractContent(String html) {
        try {
            Document doc = Jsoup.parse(html);
            
            // 移除脚本和样式
            doc.select("script, style, nav, footer, header, aside").remove();
            
            // 尝试提取主要内容
            String content = extractMainContent(doc);
            
            if (content.isEmpty()) {
                // 如果没找到主要内容，提取所有文本
                content = doc.body().text();
            }
            
            // 清理和限制长度
            content = content.replaceAll("\\s+", " ").trim();
            if (content.length() > 5000) {
                content = content.substring(0, 5000) + "...";
            }
            
            return content;
        } catch (Exception e) {
            return "";
        }
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
}