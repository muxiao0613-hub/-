package com.fxt.backend.service;

import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.repository.ArticleDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AnalysisService {
    
    @Autowired
    private ExcelParserService excelParserService;
    
    @Autowired
    private AnomalyDetectionService anomalyDetectionService;
    
    @Autowired
    private ContentCrawlerService contentCrawlerService;
    
    @Autowired
    private ContentAnalysisService contentAnalysisService;
    
    @Autowired
    private ArticleDataRepository articleDataRepository;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    public List<ArticleData> processExcelFile(MultipartFile file) throws Exception {
        // 1. 解析Excel文件
        List<ArticleData> articles = excelParserService.parseExcelFile(file);
        
        // 2. 保存到数据库
        articles = articleDataRepository.saveAll(articles);
        
        // 3. 异常检测
        anomalyDetectionService.detectAnomalies(articles);
        
        // 4. 异步抓取内容和生成建议
        processContentAsync(articles);
        
        // 5. 更新数据库
        return articleDataRepository.saveAll(articles);
    }
    
    private void processContentAsync(List<ArticleData> articles) {
        List<CompletableFuture<Void>> futures = articles.stream()
            .map(article -> CompletableFuture.runAsync(() -> {
                try {
                    // 抓取内容
                    contentCrawlerService.crawlAllContent(article);
                    
                    // 生成优化建议
                    contentAnalysisService.analyzeAndGenerateOptimizations(article);
                    
                    // 保存更新
                    articleDataRepository.save(article);
                } catch (Exception e) {
                    // 记录错误但不中断处理
                    System.err.println("处理文章失败: " + article.getTitle() + ", 错误: " + e.getMessage());
                }
            }, executorService))
            .toList();
        
        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
    
    public List<ArticleData> getAllArticles() {
        return articleDataRepository.findAll();
    }
    
    public List<ArticleData> getAnomalousArticles() {
        return articleDataRepository.findAnomalousArticles();
    }
    
    public List<ArticleData> getArticlesByStatus(String status) {
        return articleDataRepository.findByAnomalyStatus(status);
    }
    
    public void deleteAllArticles() {
        articleDataRepository.deleteAll();
    }
    
    public ArticleData getArticleById(Long id) {
        return articleDataRepository.findById(id).orElse(null);
    }
}