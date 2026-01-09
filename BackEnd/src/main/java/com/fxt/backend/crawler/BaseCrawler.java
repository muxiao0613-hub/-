package com.fxt.backend.crawler;

import com.fxt.backend.entity.ArticleData;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Random;

/**
 * 爬虫基类
 * 提供通用的HTTP请求和重试机制
 */
public abstract class BaseCrawler {
    
    protected final WebClient webClient;
    protected final Random random = new Random();
    
    // 配置参数
    protected static final int REQUEST_TIMEOUT = 30;
    protected static final int RETRY_TIMES = 3;
    protected static final double RETRY_DELAY = 2.0;
    
    public BaseCrawler() {
        this.webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
            .build();
        setupHeaders();
    }
    
    /**
     * 设置请求头 - 子类实现
     */
    protected abstract void setupHeaders();
    
    /**
     * 获取平台名称
     */
    public abstract String getPlatformName();
    
    /**
     * 爬取数据 - 子类实现具体逻辑
     */
    public abstract ArticleData crawl(ArticleData article);
    
    /**
     * 发送HTTP请求，带重试机制
     */
    protected String makeRequest(String url, String userAgent) {
        Exception lastException = null;
        
        for (int attempt = 0; attempt < RETRY_TIMES; attempt++) {
            try {
                String response = webClient.get()
                    .uri(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json, text/html, */*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Connection", "keep-alive")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT))
                    .block();
                
                return response;
                
            } catch (Exception e) {
                lastException = e;
                if (attempt < RETRY_TIMES - 1) {
                    try {
                        Thread.sleep((long) (RETRY_DELAY * (attempt + 1) * 1000));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        throw new RuntimeException("请求失败: " + url, lastException);
    }
    
    /**
     * 随机延迟，避免请求过快
     */
    protected void randomDelay() {
        try {
            Thread.sleep(500 + random.nextInt(1000)); // 0.5-1.5秒随机延迟
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 更新爬取状态
     */
    protected void updateCrawlStatus(ArticleData article, String status, String message) {
        article.setCrawlStatus(status);
        article.setCrawlError(message);
        System.out.println(String.format("[%s] %s - %s: %s", 
            getPlatformName(), article.getDataId(), status, message));
    }
}