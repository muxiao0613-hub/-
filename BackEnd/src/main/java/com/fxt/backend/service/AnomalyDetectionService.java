package com.fxt.backend.service;

import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnomalyDetectionService {
    
    public void detectAnomalies(List<ArticleData> articles) {
        // 1. 计算所有指标的统计数据
        StatisticsContext context = calculateStatistics(articles);
        
        // 2. 为每篇文章生成详细的异常分析
        for (ArticleData article : articles) {
            AnomalyAnalysisReport report = analyzeArticle(article, context);
            
            // 3. 设置异常状态和详细分析
            article.setAnomalyStatus(report.getOverallStatus());
            article.setAnomalyDetails(report.toJson());
            article.setAnomalyScore(report.getOverallScore());
        }
    }
    
    private StatisticsContext calculateStatistics(List<ArticleData> articles) {
        // 提取各指标的有效数据
        List<Double> readCounts = articles.stream()
            .filter(a -> a.getReadCount7d() != null && a.getReadCount7d() > 0)
            .map(a -> a.getReadCount7d().doubleValue())
            .collect(Collectors.toList());
            
        List<Double> interactionCounts = articles.stream()
            .filter(a -> a.getInteractionCount7d() != null && a.getInteractionCount7d() > 0)
            .map(a -> a.getInteractionCount7d().doubleValue())
            .collect(Collectors.toList());
            
        List<Double> shareCounts = articles.stream()
            .filter(a -> a.getShareCount7d() != null && a.getShareCount7d() > 0)
            .map(a -> a.getShareCount7d().doubleValue())
            .collect(Collectors.toList());
            
        List<Double> productVisits = articles.stream()
            .filter(a -> a.getProductVisitCount() != null && a.getProductVisitCount() > 0)
            .map(a -> a.getProductVisitCount().doubleValue())
            .collect(Collectors.toList());
        
        return new StatisticsContext(
            new MetricStatistics(readCounts),
            new MetricStatistics(interactionCounts),
            new MetricStatistics(shareCounts),
            new MetricStatistics(productVisits)
        );
    }
    
    private AnomalyAnalysisReport analyzeArticle(ArticleData article, StatisticsContext context) {
        AnomalyAnalysisReport report = new AnomalyAnalysisReport();
        
        // 分析阅读量
        if (article.getReadCount7d() != null && article.getReadCount7d() > 0) {
            AnomalyAnalysisResult readResult = analyzeMetric(
                "7天阅读量",
                article.getReadCount7d().doubleValue(),
                context.getReadCountStats()
            );
            report.addResult(readResult);
        }
        
        // 分析互动量
        if (article.getInteractionCount7d() != null && article.getInteractionCount7d() > 0) {
            AnomalyAnalysisResult interactionResult = analyzeMetric(
                "7天互动量",
                article.getInteractionCount7d().doubleValue(),
                context.getInteractionCountStats()
            );
            report.addResult(interactionResult);
        }
        
        // 分析分享量
        if (article.getShareCount7d() != null && article.getShareCount7d() > 0) {
            AnomalyAnalysisResult shareResult = analyzeMetric(
                "7天分享量",
                article.getShareCount7d().doubleValue(),
                context.getShareCountStats()
            );
            report.addResult(shareResult);
        }
        
        // 分析好物访问量
        if (article.getProductVisitCount() != null && article.getProductVisitCount() > 0) {
            AnomalyAnalysisResult productResult = analyzeMetric(
                "好物访问量",
                article.getProductVisitCount().doubleValue(),
                context.getProductVisitStats()
            );
            report.addResult(productResult);
        }
        
        // 综合评估
        report.calculateOverallStatus();
        
        return report;
    }
    
    private AnomalyAnalysisResult analyzeMetric(String metricName, double value, MetricStatistics stats) {
        AnomalyAnalysisResult result = new AnomalyAnalysisResult();
        result.setMetric(metricName);
        result.setValue(value);
        result.setMean(stats.getMean());
        result.setStdDev(stats.getStdDev());
        
        // 计算百分位
        result.setPercentile(stats.calculatePercentile(value));
        
        return result;
    }
}