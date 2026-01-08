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
    private UnifiedCrawlerService unifiedCrawlerService;

    @Autowired
    private ContentAnalysisService contentAnalysisService;

    @Autowired
    private ArticleDataRepository articleDataRepository;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public List<ArticleData> processExcelFile(MultipartFile file) throws Exception {
        List<ArticleData> articles = excelParserService.parseExcelFile(file);
        articles = articleDataRepository.saveAll(articles);
        anomalyDetectionService.detectAnomalies(articles);
        processContentAsync(articles);
        return articleDataRepository.saveAll(articles);
    }

    private void processContentAsync(List<ArticleData> articles) {
        List<CompletableFuture<Void>> futures = articles.stream()
            .map(article -> CompletableFuture.runAsync(() -> {
                try {
                    UnifiedCrawlerService.CrawlResult crawlResult = unifiedCrawlerService.crawlArticle(article);
                    unifiedCrawlerService.applyCrawlResultToArticle(article, crawlResult);
                    contentAnalysisService.analyzeAndGenerateOptimizations(article);
                    // 注意：不再自动生成AI建议
                    articleDataRepository.save(article);
                } catch (Exception e) {
                    System.err.println("处理文章失败: " + article.getTitle() + ", 错误: " + e.getMessage());
                }
            }, executorService))
            .collect(Collectors.toList());

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
        return articleDataRepository.findById(id).orElse(null);
    }
}