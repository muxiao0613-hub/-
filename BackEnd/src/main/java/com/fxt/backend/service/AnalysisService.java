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
    private ContentAnalysisService contentAnalysisService;

    @Autowired
    private ContentCrawlerService contentCrawlerService;

    @Autowired
    private ArticleDataRepository articleDataRepository;

    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    public List<ArticleData> processExcelFile(MultipartFile file) throws Exception {
        List<ArticleData> articles = excelParserService.parseExcelFile(file);
        articles = articleDataRepository.saveAll(articles);
        
        // 异步执行异常检测，不阻塞用户响应
        List<ArticleData> finalArticles = articles;
        CompletableFuture.runAsync(() -> {
            try {
                System.out.println("开始后台异常检测分析，共 " + finalArticles.size() + " 篇文章");
                anomalyDetectionService.detectAnomalies(finalArticles);
                articleDataRepository.saveAll(finalArticles);
                System.out.println("后台异常检测分析完成");
            } catch (Exception e) {
                System.err.println("后台异常检测失败: " + e.getMessage());
            }
        }, executorService);
        
        return articles; // 立即返回，让用户看到文章列表
    }

    // processContentAsync方法已移除，改为按需爬取

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

        if (article.getAnomalyDetails() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                AnomalyAnalysisReport anomalyReport = mapper.readValue(
                    article.getAnomalyDetails(), 
                    AnomalyAnalysisReport.class
                );
                response.setAnomalyReport(anomalyReport);
            } catch (Exception e) {
                response.setAnomalyReport(new AnomalyAnalysisReport());
            }
        }

        if (article.getTitleAnalysis() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                TitleAnalysis titleAnalysis = mapper.readValue(
                    article.getTitleAnalysis(), 
                    TitleAnalysis.class
                );
                response.setTitleAnalysis(titleAnalysis);
            } catch (Exception e) {
                response.setTitleAnalysis(TitleAnalysis.analyze(article.getTitle()));
            }
        } else {
            response.setTitleAnalysis(TitleAnalysis.analyze(article.getTitle()));
        }

        List<ArticleData> benchmarkArticles = articleDataRepository
            .findTop5ByBrandAndAnomalyStatusOrderByReadCount7dDesc(
                article.getBrand(), 
                "GOOD_ANOMALY"
            );
        response.setBenchmarkArticles(benchmarkArticles);

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
        ArticleData article = articleDataRepository.findById(id).orElse(null);
        if (article != null) {
            // 检查是否需要爬取内容
            boolean needsCrawling = article.getContent() == null || 
                                  article.getContent().isEmpty() || 
                                  !"SUCCESS".equals(article.getCrawlStatus());
            
            // 检查是否需要生成基础优化建议
            boolean needsOptimization = article.getOptimizationSuggestions() == null || 
                                      article.getOptimizationSuggestions().isEmpty();
            
            if (needsCrawling || needsOptimization) {
                try {
                    // 1. 如果需要，先进行内容爬取
                    if (needsCrawling) {
                        System.out.println("自动爬取文章内容: " + article.getTitle());
                        contentCrawlerService.crawlAllContent(article);
                    }
                    
                    // 2. 如果需要，生成基础优化建议
                    if (needsOptimization) {
                        System.out.println("自动生成基础优化建议: " + article.getTitle());
                        contentAnalysisService.analyzeAndGenerateOptimizations(article);
                    }
                    
                    // 3. 保存更新后的文章
                    article = articleDataRepository.save(article);
                    System.out.println("文章详情自动处理完成: " + article.getTitle());
                    
                } catch (Exception e) {
                    System.err.println("自动处理文章详情失败: " + e.getMessage());
                    // 即使处理失败，也返回原始文章数据
                }
            }
        }
        return article;
    }
}