package com.fxt.backend.service;

import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.repository.ArticleDataRepository;
import com.fxt.backend.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
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
    private DetailedOptimizationService detailedOptimizationService;
    
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
                    
                    // 生成AI建议
                    String aiSuggestions = detailedOptimizationService.generateAISuggestions(article);
                    article.setAiSuggestions(aiSuggestions);
                    
                    // 保存更新
                    articleDataRepository.save(article);
                } catch (Exception e) {
                    // 记录错误但不中断处理
                    System.err.println("处理文章失败: " + article.getTitle() + ", 错误: " + e.getMessage());
                }
            }, executorService))
            .collect(Collectors.toList());
        
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
    
    public ArticleDetailResponse getArticleDetailResponse(ArticleData article) {
        ArticleDetailResponse response = new ArticleDetailResponse(article);
        
        // 解析异常分析报告
        if (article.getAnomalyDetails() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                AnomalyAnalysisReport anomalyReport = mapper.readValue(
                    article.getAnomalyDetails(), 
                    AnomalyAnalysisReport.class
                );
                response.setAnomalyReport(anomalyReport);
            } catch (Exception e) {
                // 解析失败时创建空报告
                response.setAnomalyReport(new AnomalyAnalysisReport());
            }
        }
        
        // 解析标题分析
        if (article.getTitleAnalysis() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                TitleAnalysis titleAnalysis = mapper.readValue(
                    article.getTitleAnalysis(), 
                    TitleAnalysis.class
                );
                response.setTitleAnalysis(titleAnalysis);
            } catch (Exception e) {
                // 解析失败时重新分析
                response.setTitleAnalysis(TitleAnalysis.analyze(article.getTitle()));
            }
        } else {
            response.setTitleAnalysis(TitleAnalysis.analyze(article.getTitle()));
        }
        
        // 获取对比文章
        List<ArticleData> benchmarkArticles = articleDataRepository
            .findTop5ByBrandAndAnomalyStatusOrderByReadCount7dDesc(
                article.getBrand(), 
                "GOOD_ANOMALY"
            );
        response.setBenchmarkArticles(benchmarkArticles);
        
        // 获取品牌平均数据
        Object[] averages = articleDataRepository.getBrandAverages(article.getBrand());
        if (averages != null && averages.length >= 3) {
            Map<String, Double> brandAverages = new HashMap<>();
            brandAverages.put("avgReadCount", averages[0] != null ? (Double) averages[0] : 0.0);
            brandAverages.put("avgInteractionCount", averages[1] != null ? (Double) averages[1] : 0.0);
            brandAverages.put("avgShareCount", averages[2] != null ? (Double) averages[2] : 0.0);
            response.setBrandAverages(brandAverages);
        }
        
        return response;
    }
    
    public ArticleData getArticleById(Long id) {
        return articleDataRepository.findById(id).orElse(null);
    }
}