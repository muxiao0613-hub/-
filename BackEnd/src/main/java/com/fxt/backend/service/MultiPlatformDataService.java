package com.fxt.backend.service;

import com.fxt.backend.crawler.BaseCrawler;
import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.enums.DataSource;
import com.fxt.backend.repository.ArticleDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 多平台数据管理服务
 * 负责智能平台识别、数据采集和处理
 */
@Service
public class MultiPlatformDataService {
    
    private static final Logger logger = LoggerFactory.getLogger(MultiPlatformDataService.class);
    
    @Autowired
    private ArticleDataRepository articleDataRepository;
    
    @Autowired
    private CrawlerFactory crawlerFactory;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    
    /**
     * 智能识别并更新文章的平台信息
     */
    public void identifyPlatforms(List<ArticleData> articles) {
        for (ArticleData article : articles) {
            DataSource platform = identifyPlatform(article);
            
            // 更新平台信息到数据库（如果需要新增字段）
            logger.info("文章 {} 识别为平台: {}", 
                article.getDataId(), crawlerFactory.getPlatformName(platform));
        }
    }
    
    /**
     * 智能识别单个文章的平台
     */
    public DataSource identifyPlatform(ArticleData article) {
        // 1. 优先根据素材来源字段识别
        DataSource sourceFromField = DataSource.fromSourceField(article.getMaterialSource());
        if (sourceFromField != DataSource.UNKNOWN) {
            return sourceFromField;
        }
        
        // 2. 根据链接URL识别
        DataSource sourceFromUrl = DataSource.fromUrl(article.getArticleLink());
        if (sourceFromUrl != DataSource.UNKNOWN) {
            return sourceFromUrl;
        }
        
        // 3. 根据其他特征识别（标题、内容等）
        return identifyByContent(article);
    }
    
    /**
     * 根据内容特征识别平台
     */
    private DataSource identifyByContent(ArticleData article) {
        String title = article.getTitle();
        String content = article.getContent();
        
        if (title != null || content != null) {
            String text = (title + " " + (content != null ? content : "")).toLowerCase();
            
            // 得物特征词
            if (text.contains("得物") || text.contains("poizon") || text.contains("dewu") ||
                text.contains("好物") || text.contains("上脚")) {
                return DataSource.DEWU;
            }
            
            // 小红书特征词
            if (text.contains("小红书") || text.contains("xhs") || text.contains("种草") ||
                text.contains("笔记") || text.contains("分享")) {
                return DataSource.XIAOHONGSHU;
            }
        }
        
        return DataSource.UNKNOWN;
    }
    
    /**
     * 根据文章ID批量爬取数据
     */
    public Map<String, Object> crawlDataByIds(List<Long> articleIds, Consumer<CrawlProgress> progressCallback) {
        List<ArticleData> articles = articleDataRepository.findAllById(articleIds);
        return crawlAllData(articles, progressCallback);
    }
    
    /**
     * 批量爬取数据
     */
    public Map<String, Object> crawlAllData(List<ArticleData> articles, Consumer<CrawlProgress> progressCallback) {
        Map<String, Integer> results = new HashMap<>();
        results.put("success", 0);
        results.put("failed", 0);
        results.put("error", 0);
        results.put("skipped", 0);
        
        Map<DataSource, Integer> platformCounts = new HashMap<>();
        
        for (int i = 0; i < articles.size(); i++) {
            ArticleData article = articles.get(i);
            
            try {
                // 识别平台
                DataSource platform = identifyPlatform(article);
                platformCounts.merge(platform, 1, Integer::sum);
                
                // 获取对应爬虫
                BaseCrawler crawler = crawlerFactory.createCrawler(platform);
                
                if (crawler != null) {
                    // 执行爬取
                    article = crawler.crawl(article);
                    article.setUpdatedAt(LocalDateTime.now());
                    articleDataRepository.save(article);
                    
                    results.merge(article.getCrawlStatus().toLowerCase(), 1, Integer::sum);
                } else {
                    article.setCrawlStatus("SKIPPED");
                    article.setCrawlError("不支持的平台: " + platform.getDisplayName());
                    articleDataRepository.save(article);
                    results.put("skipped", results.get("skipped") + 1);
                }
                
                // 回调进度
                if (progressCallback != null) {
                    CrawlProgress progress = new CrawlProgress(
                        i + 1, articles.size(), article, platform
                    );
                    progressCallback.accept(progress);
                }
                
                // 随机延迟
                Thread.sleep(500 + new Random().nextInt(1000));
                
            } catch (Exception e) {
                logger.error("爬取文章失败: {}", article.getDataId(), e);
                article.setCrawlStatus("ERROR");
                article.setCrawlError("爬取异常: " + e.getMessage());
                articleDataRepository.save(article);
                results.put("error", results.get("error") + 1);
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("results", results);
        result.put("platformCounts", platformCounts);
        result.put("totalProcessed", articles.size());
        
        return result;
    }
    
    /**
     * 异步爬取数据
     */
    public CompletableFuture<Map<String, Object>> crawlAllDataAsync(List<Long> articleIds, Consumer<CrawlProgress> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            return crawlDataByIds(articleIds, progressCallback);
        }, executorService);
    }
    
    /**
     * 重新爬取单个文章
     */
    public ArticleData recrawlArticle(Long articleId) {
        ArticleData article = articleDataRepository.findById(articleId)
            .orElseThrow(() -> new RuntimeException("文章不存在: " + articleId));
        
        DataSource platform = identifyPlatform(article);
        BaseCrawler crawler = crawlerFactory.createCrawler(platform);
        
        if (crawler == null) {
            throw new RuntimeException("不支持的平台: " + platform.getDisplayName());
        }
        
        article = crawler.crawl(article);
        article.setUpdatedAt(LocalDateTime.now());
        
        return articleDataRepository.save(article);
    }
    
    /**
     * 获取平台统计信息
     */
    public Map<String, Object> getPlatformStatistics() {
        List<ArticleData> allArticles = articleDataRepository.findAll();
        
        Map<String, Integer> platformCounts = new HashMap<>();
        Map<String, Integer> crawlStatusCounts = new HashMap<>();
        
        for (ArticleData article : allArticles) {
            DataSource platform = identifyPlatform(article);
            platformCounts.merge(platform.getDisplayName(), 1, Integer::sum);
            
            String status = article.getCrawlStatus() != null ? article.getCrawlStatus() : "PENDING";
            crawlStatusCounts.merge(status, 1, Integer::sum);
        }
        
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalArticles", allArticles.size());
        statistics.put("platformDistribution", platformCounts);
        statistics.put("crawlStatusDistribution", crawlStatusCounts);
        statistics.put("supportedPlatforms", crawlerFactory.getSupportedPlatforms());
        
        return statistics;
    }
    
    /**
     * 爬取进度信息
     */
    public static class CrawlProgress {
        private final int current;
        private final int total;
        private final ArticleData article;
        private final DataSource platform;
        
        public CrawlProgress(int current, int total, ArticleData article, DataSource platform) {
            this.current = current;
            this.total = total;
            this.article = article;
            this.platform = platform;
        }
        
        public int getCurrent() { return current; }
        public int getTotal() { return total; }
        public ArticleData getArticle() { return article; }
        public DataSource getPlatform() { return platform; }
        
        public double getProgress() {
            return (double) current / total * 100;
        }
    }
}