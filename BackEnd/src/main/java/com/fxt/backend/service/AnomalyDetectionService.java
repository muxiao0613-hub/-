package com.fxt.backend.service;

import com.fxt.backend.entity.ArticleData;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnomalyDetectionService {
    
    private static final double Z_SCORE_THRESHOLD = 2.0;
    private static final double IQR_MULTIPLIER = 1.5;
    
    public void detectAnomalies(List<ArticleData> articles) {
        // 对每个指标进行异常检测
        detectReadCountAnomalies(articles);
        detectInteractionCountAnomalies(articles);
        detectShareCountAnomalies(articles);
        
        // 综合判断异常状态
        for (ArticleData article : articles) {
            determineOverallAnomalyStatus(article);
        }
    }
    
    private void detectReadCountAnomalies(List<ArticleData> articles) {
        List<Double> readCounts = articles.stream()
            .filter(a -> a.getReadCount7d() != null && a.getReadCount7d() > 0)
            .map(a -> a.getReadCount7d().doubleValue())
            .collect(Collectors.toList());
            
        if (readCounts.isEmpty()) return;
        
        DescriptiveStatistics stats = new DescriptiveStatistics();
        readCounts.forEach(stats::addValue);
        
        double mean = stats.getMean();
        double stdDev = stats.getStandardDeviation();
        double q1 = stats.getPercentile(25);
        double q3 = stats.getPercentile(75);
        double iqr = q3 - q1;
        
        for (ArticleData article : articles) {
            if (article.getReadCount7d() == null || article.getReadCount7d() == 0) continue;
            
            double value = article.getReadCount7d().doubleValue();
            double zScore = Math.abs((value - mean) / stdDev);
            
            // Z-Score检测
            if (zScore > Z_SCORE_THRESHOLD) {
                if (value > mean) {
                    article.setAnomalyStatus("GOOD_ANOMALY");
                } else {
                    article.setAnomalyStatus("BAD_ANOMALY");
                }
            }
            
            // IQR检测
            if (value > q3 + IQR_MULTIPLIER * iqr) {
                article.setAnomalyStatus("GOOD_ANOMALY");
            } else if (value < q1 - IQR_MULTIPLIER * iqr) {
                article.setAnomalyStatus("BAD_ANOMALY");
            }
        }
    }
    
    private void detectInteractionCountAnomalies(List<ArticleData> articles) {
        List<Double> interactionCounts = articles.stream()
            .filter(a -> a.getInteractionCount7d() != null && a.getInteractionCount7d() > 0)
            .map(a -> a.getInteractionCount7d().doubleValue())
            .collect(Collectors.toList());
            
        if (interactionCounts.isEmpty()) return;
        
        DescriptiveStatistics stats = new DescriptiveStatistics();
        interactionCounts.forEach(stats::addValue);
        
        double mean = stats.getMean();
        double stdDev = stats.getStandardDeviation();
        
        for (ArticleData article : articles) {
            if (article.getInteractionCount7d() == null || article.getInteractionCount7d() == 0) continue;
            
            double value = article.getInteractionCount7d().doubleValue();
            double zScore = Math.abs((value - mean) / stdDev);
            
            if (zScore > Z_SCORE_THRESHOLD) {
                if (value > mean && !"BAD_ANOMALY".equals(article.getAnomalyStatus())) {
                    article.setAnomalyStatus("GOOD_ANOMALY");
                } else if (value < mean) {
                    article.setAnomalyStatus("BAD_ANOMALY");
                }
            }
        }
    }
    
    private void detectShareCountAnomalies(List<ArticleData> articles) {
        List<Double> shareCounts = articles.stream()
            .filter(a -> a.getShareCount7d() != null && a.getShareCount7d() > 0)
            .map(a -> a.getShareCount7d().doubleValue())
            .collect(Collectors.toList());
            
        if (shareCounts.isEmpty()) return;
        
        DescriptiveStatistics stats = new DescriptiveStatistics();
        shareCounts.forEach(stats::addValue);
        
        double mean = stats.getMean();
        double stdDev = stats.getStandardDeviation();
        
        for (ArticleData article : articles) {
            if (article.getShareCount7d() == null || article.getShareCount7d() == 0) continue;
            
            double value = article.getShareCount7d().doubleValue();
            double zScore = Math.abs((value - mean) / stdDev);
            
            if (zScore > Z_SCORE_THRESHOLD) {
                if (value > mean && !"BAD_ANOMALY".equals(article.getAnomalyStatus())) {
                    article.setAnomalyStatus("GOOD_ANOMALY");
                } else if (value < mean) {
                    article.setAnomalyStatus("BAD_ANOMALY");
                }
            }
        }
    }
    
    private void determineOverallAnomalyStatus(ArticleData article) {
        // 如果还没有被标记为异常，保持正常状态
        if (article.getAnomalyStatus() == null) {
            article.setAnomalyStatus("NORMAL");
        }
        
        // 计算综合得分来进一步确认异常状态
        double totalScore = 0;
        int validMetrics = 0;
        
        if (article.getReadCount7d() != null && article.getReadCount7d() > 0) {
            totalScore += article.getReadCount7d();
            validMetrics++;
        }
        
        if (article.getInteractionCount7d() != null && article.getInteractionCount7d() > 0) {
            totalScore += article.getInteractionCount7d() * 10; // 互动权重更高
            validMetrics++;
        }
        
        if (article.getShareCount7d() != null && article.getShareCount7d() > 0) {
            totalScore += article.getShareCount7d() * 20; // 分享权重最高
            validMetrics++;
        }
        
        // 基于综合得分的阈值判断
        if (validMetrics > 0) {
            double avgScore = totalScore / validMetrics;
            if (avgScore > 10000) { // 高阈值
                if (!"BAD_ANOMALY".equals(article.getAnomalyStatus())) {
                    article.setAnomalyStatus("GOOD_ANOMALY");
                }
            } else if (avgScore < 100) { // 低阈值
                article.setAnomalyStatus("BAD_ANOMALY");
            }
        }
    }
}