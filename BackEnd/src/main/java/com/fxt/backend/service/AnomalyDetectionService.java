package com.fxt.backend.service;

import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.repository.ArticleDataRepository;
import com.fxt.backend.dto.AnomalyAnalysisReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnomalyDetectionService {
    
    @Autowired
    private ArticleDataRepository articleDataRepository;
    
    @Autowired
    private AdvancedAnomalyDetectionService advancedAnomalyDetectionService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public void detectAnomalies(List<ArticleData> articles) {
        // 为每篇文章进行高级异常检测
        for (ArticleData article : articles) {
            detectAndAnalyzeAnomalies(article, articles);
        }
    }
    
    public void detectAndAnalyzeAnomalies(ArticleData article, List<ArticleData> allArticles) {
        // 使用高级异常检测服务
        AnomalyAnalysisReport report = advancedAnomalyDetectionService.detectAnomalies(article, allArticles);
        
        // 设置异常状态和评分
        article.setAnomalyStatus(report.getOverallStatus());
        article.setAnomalyScore(report.getOverallScore());
        
        // 将详细报告序列化为JSON存储
        try {
            String reportJson = objectMapper.writeValueAsString(report);
            article.setAnomalyDetails(reportJson);
        } catch (Exception e) {
            article.setAnomalyDetails("{}");
        }
        
        // 计算并存储关键指标
        calculateAndStoreMetrics(article, allArticles);
    }
    
    private void calculateAndStoreMetrics(ArticleData article, List<ArticleData> allArticles) {
        // 计算互动率
        if (article.getReadCount7d() != null && article.getReadCount7d() > 0 && article.getInteractionCount7d() != null) {
            double interactionRate = (double) article.getInteractionCount7d() / article.getReadCount7d() * 100;
            // 可以添加字段存储，或在需要时计算
        }
        
        // 计算转化率
        if (article.getReadCount7d() != null && article.getReadCount7d() > 0 && article.getProductVisit7d() != null) {
            double conversionRate = (double) article.getProductVisit7d() / article.getReadCount7d() * 100;
            // 可以添加字段存储，或在需要时计算
        }
        
        // 计算增长率
        if (article.getReadCount7d() != null && article.getReadCount7d() > 0 && article.getReadCount14d() != null) {
            double growthRate = (double) (article.getReadCount14d() - article.getReadCount7d()) / article.getReadCount7d() * 100;
            // 可以添加字段存储，或在需要时计算
        }
    }
}