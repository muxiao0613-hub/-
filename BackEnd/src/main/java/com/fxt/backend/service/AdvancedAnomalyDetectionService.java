package com.fxt.backend.service;

import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.dto.AnomalyAnalysisReport;
import com.fxt.backend.dto.AnomalyAnalysisResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 高级异常检测服务 - 优化版
 * 采用更合理的阈值和权重配置
 * 优化异常判定逻辑，减少误判
 */
@Service
public class AdvancedAnomalyDetectionService {
    
    // ===== 优化后的阈值配置 =====
    // 降低严重异常阈值，使检测更敏感
    private static final double Z_SCORE_SEVERE = 2.5;    // 原3.0 -> 2.5
    private static final double Z_SCORE_MODERATE = 1.8;  // 原2.0 -> 1.8
    private static final double Z_SCORE_MILD = 1.2;      // 原1.5 -> 1.2
    private static final double IQR_MULTIPLIER = 1.5;
    
    // ===== 新增：百分位阈值 =====
    private static final double PERCENTILE_EXCELLENT = 85.0;  // 优秀线
    private static final double PERCENTILE_GOOD = 70.0;       // 良好线
    private static final double PERCENTILE_POOR = 30.0;       // 较差线
    private static final double PERCENTILE_BAD = 15.0;        // 差线
    
    public AnomalyAnalysisReport detectAnomalies(ArticleData article, List<ArticleData> allArticles) {
        AnomalyAnalysisReport report = new AnomalyAnalysisReport();
        List<AnomalyAnalysisResult> results = new ArrayList<>();
        
        // 过滤有效数据（放宽条件）
        List<ArticleData> validArticles = allArticles.stream()
            .filter(a -> a.getReadCount7d() != null && a.getReadCount7d() >= 0)
            .collect(Collectors.toList());
        
        // 数据量不足时使用简化分析
        if (validArticles.size() < 3) {
            return createSimpleReport(article, validArticles);
        }
        
        // ==================== 核心指标分析（优化权重）====================
        
        // 1. 7天阅读量分析（权重：35%）- 提高权重，这是最重要的指标
        AnomalyAnalysisResult readResult = analyzeMetricOptimized(
            article, validArticles, "7天阅读量", 
            ArticleData::getReadCount7d, 0.35
        );
        results.add(readResult);
        
        // 2. 7天互动量分析（权重：25%）
        AnomalyAnalysisResult interactionResult = analyzeMetricOptimized(
            article, validArticles, "7天互动量", 
            ArticleData::getInteractionCount7d, 0.25
        );
        results.add(interactionResult);
        
        // 3. 互动率分析（权重：20%）- 重要的效率指标
        AnomalyAnalysisResult interactionRateResult = analyzeInteractionRateOptimized(article, validArticles);
        interactionRateResult.setWeight(0.20);
        results.add(interactionRateResult);
        
        // 4. 7天好物访问分析（权重：12%）
        AnomalyAnalysisResult visitResult = analyzeMetricOptimized(
            article, validArticles, "7天好物访问", 
            ArticleData::getProductVisit7d, 0.12
        );
        results.add(visitResult);
        
        // 5. 转化率分析（权重：8%）
        AnomalyAnalysisResult conversionResult = analyzeConversionRateOptimized(article, validArticles);
        conversionResult.setWeight(0.08);
        results.add(conversionResult);
        
        report.setResults(results);
        
        // 使用优化后的综合评分逻辑
        calculateOptimizedOverallStatus(report, article);
        
        return report;
    }
    
    /**
     * 优化后的指标分析方法
     */
    private AnomalyAnalysisResult analyzeMetricOptimized(
            ArticleData article, 
            List<ArticleData> allArticles, 
            String metricName, 
            java.util.function.Function<ArticleData, Long> getter,
            double weight) {
        
        List<Double> values = allArticles.stream()
            .map(getter)
            .filter(Objects::nonNull)
            .filter(v -> v >= 0)  // 允许0值
            .map(Long::doubleValue)
            .sorted()
            .collect(Collectors.toList());
        
        if (values.isEmpty()) {
            return createEmptyResult(metricName, weight);
        }
        
        double currentValue = getter.apply(article) != null ? getter.apply(article).doubleValue() : 0;
        
        // 基础统计量
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        
        // 中位数和MAD（更稳健）
        double median = calculateMedian(values);
        double mad = calculateMAD(values, median);
        
        // 计算Z-score（使用更稳健的方法）
        double zScore;
        if (stdDev > 0) {
            zScore = (currentValue - mean) / stdDev;
        } else {
            // 标准差为0时，基于中位数判断
            zScore = currentValue > median ? 0.5 : (currentValue < median ? -0.5 : 0);
        }
        
        // 计算稳健Z-score
        double robustZScore = 0;
        if (mad > 0) {
            robustZScore = 0.6745 * (currentValue - median) / mad;
        }
        
        // 综合Z分数
        double combinedZScore = stdDev > 0 ? (0.6 * zScore + 0.4 * robustZScore) : robustZScore;
        
        // 百分位计算
        double percentile = calculatePercentile(currentValue, values);
        
        // IQR异常检测
        double q1 = calculateQuantile(values, 0.25);
        double q3 = calculateQuantile(values, 0.75);
        double iqr = q3 - q1;
        double lowerBound = q1 - IQR_MULTIPLIER * iqr;
        double upperBound = q3 + IQR_MULTIPLIER * iqr;
        boolean isIQROutlier = currentValue < lowerBound || currentValue > upperBound;
        
        AnomalyAnalysisResult result = new AnomalyAnalysisResult();
        result.setMetric(metricName);
        result.setValue(currentValue);
        result.setMean(mean);
        result.setStdDev(stdDev);
        result.setZScore(combinedZScore);
        result.setPercentile(percentile);
        result.setWeight(weight);
        
        // 偏离描述（更直观）
        if (mean > 0) {
            double deviationPct = (currentValue - mean) / mean * 100;
            String direction = deviationPct > 0 ? "高于" : "低于";
            String level = "";
            if (Math.abs(deviationPct) > 100) level = "大幅";
            else if (Math.abs(deviationPct) > 50) level = "显著";
            else if (Math.abs(deviationPct) > 20) level = "明显";
            result.setDeviation(String.format("%s%s平均值 %.1f%%", level, direction, Math.abs(deviationPct)));
        } else {
            result.setDeviation("数据基准为0");
        }
        
        // 使用优化后的异常等级判定
        result.setLevel(determineOptimizedAnomalyLevel(combinedZScore, isIQROutlier, percentile, currentValue > mean));
        
        return result;
    }
    
    /**
     * 优化后的互动率分析
     */
    private AnomalyAnalysisResult analyzeInteractionRateOptimized(ArticleData article, List<ArticleData> allArticles) {
        List<Double> rates = allArticles.stream()
            .filter(a -> a.getReadCount7d() != null && a.getReadCount7d() > 50 && a.getInteractionCount7d() != null)
            .map(a -> (double) a.getInteractionCount7d() / a.getReadCount7d() * 100)
            .sorted()
            .collect(Collectors.toList());
        
        double currentRate = 0;
        if (article.getReadCount7d() != null && article.getReadCount7d() > 0 && article.getInteractionCount7d() != null) {
            currentRate = (double) article.getInteractionCount7d() / article.getReadCount7d() * 100;
        }
        
        if (rates.isEmpty()) {
            AnomalyAnalysisResult result = createEmptyResult("互动率", 0.20);
            result.setValue(currentRate);
            return result;
        }
        
        double mean = rates.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double stdDev = Math.sqrt(rates.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0));
        double zScore = stdDev > 0 ? (currentRate - mean) / stdDev : 0;
        double percentile = calculatePercentile(currentRate, rates);
        
        AnomalyAnalysisResult result = new AnomalyAnalysisResult();
        result.setMetric("互动率");
        result.setValue(currentRate);
        result.setMean(mean);
        result.setStdDev(stdDev);
        result.setZScore(zScore);
        result.setPercentile(percentile);
        
        // 互动率的判断标准
        String levelDesc;
        if (currentRate >= 8) levelDesc = "优秀";
        else if (currentRate >= 5) levelDesc = "良好";
        else if (currentRate >= 3) levelDesc = "一般";
        else levelDesc = "偏低";
        
        result.setDeviation(String.format("%.2f%% (%s，平均: %.2f%%)", currentRate, levelDesc, mean));
        result.setLevel(determineOptimizedAnomalyLevel(zScore, false, percentile, currentRate > mean));
        
        return result;
    }
    
    /**
     * 优化后的转化率分析
     */
    private AnomalyAnalysisResult analyzeConversionRateOptimized(ArticleData article, List<ArticleData> allArticles) {
        List<Double> rates = allArticles.stream()
            .filter(a -> a.getReadCount7d() != null && a.getReadCount7d() > 50 && a.getProductVisit7d() != null)
            .map(a -> (double) a.getProductVisit7d() / a.getReadCount7d() * 100)
            .sorted()
            .collect(Collectors.toList());
        
        double currentRate = 0;
        if (article.getReadCount7d() != null && article.getReadCount7d() > 0 && article.getProductVisit7d() != null) {
            currentRate = (double) article.getProductVisit7d() / article.getReadCount7d() * 100;
        }
        
        if (rates.isEmpty()) {
            AnomalyAnalysisResult result = createEmptyResult("好物转化率", 0.08);
            result.setValue(currentRate);
            return result;
        }
        
        double mean = rates.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double stdDev = Math.sqrt(rates.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0));
        double zScore = stdDev > 0 ? (currentRate - mean) / stdDev : 0;
        double percentile = calculatePercentile(currentRate, rates);
        
        AnomalyAnalysisResult result = new AnomalyAnalysisResult();
        result.setMetric("好物转化率");
        result.setValue(currentRate);
        result.setMean(mean);
        result.setStdDev(stdDev);
        result.setZScore(zScore);
        result.setPercentile(percentile);
        result.setDeviation(String.format("%.2f%% (平均: %.2f%%)", currentRate, mean));
        result.setLevel(determineOptimizedAnomalyLevel(zScore, false, percentile, currentRate > mean));
        
        return result;
    }
    
    /**
     * 优化后的异常等级判定
     * 更合理的判定逻辑，减少误判
     */
    private String determineOptimizedAnomalyLevel(double zScore, boolean isIQROutlier, double percentile, boolean isPositive) {
        double absZ = Math.abs(zScore);
        
        // 基于百分位的判定（更直观）
        if (isPositive) {
            // 正向：表现好
            if (percentile >= PERCENTILE_EXCELLENT || absZ > Z_SCORE_SEVERE) {
                return "SEVERE";  // 极其优秀
            } else if (percentile >= PERCENTILE_GOOD || absZ > Z_SCORE_MODERATE) {
                return "MODERATE";  // 表现良好
            } else if (percentile >= 60 || absZ > Z_SCORE_MILD) {
                return "MILD";  // 略高于平均
            }
        } else {
            // 负向：表现差
            if (percentile <= PERCENTILE_BAD || absZ > Z_SCORE_SEVERE) {
                return "SEVERE";  // 表现很差
            } else if (percentile <= PERCENTILE_POOR || absZ > Z_SCORE_MODERATE) {
                return "MODERATE";  // 表现较差
            } else if (percentile <= 40 || absZ > Z_SCORE_MILD) {
                return "MILD";  // 略低于平均
            }
        }
        
        // IQR异常检测作为补充
        if (isIQROutlier && absZ > 1.0) {
            return "MILD";
        }
        
        return "NORMAL";
    }
    
    /**
     * 优化后的综合状态计算
     */
    private void calculateOptimizedOverallStatus(AnomalyAnalysisReport report, ArticleData article) {
        List<AnomalyAnalysisResult> results = report.getResults();
        
        if (results.isEmpty()) {
            report.setOverallStatus("NORMAL");
            report.setOverallScore(50.0);
            return;
        }
        
        double weightedScore = 0;
        double totalWeight = 0;
        int positiveAnomalyCount = 0;
        int negativeAnomalyCount = 0;
        int severePositive = 0;
        int severeNegative = 0;
        
        for (AnomalyAnalysisResult result : results) {
            double weight = result.getWeight() != null ? result.getWeight() : 1.0 / results.size();
            
            // 基于百分位计算分数（更直观）
            double score = result.getPercentile();
            
            // Z-score微调
            if (result.getZScore() > 0) {
                score = Math.min(100, score + result.getZScore() * 2);
            } else {
                score = Math.max(0, score + result.getZScore() * 2);
            }
            
            weightedScore += score * weight;
            totalWeight += weight;
            
            // 统计异常类型
            if (!"NORMAL".equals(result.getLevel())) {
                boolean isPositive = result.getZScore() > 0 || result.getPercentile() > 50;
                if (isPositive) {
                    positiveAnomalyCount++;
                    if ("SEVERE".equals(result.getLevel())) severePositive++;
                } else {
                    negativeAnomalyCount++;
                    if ("SEVERE".equals(result.getLevel())) severeNegative++;
                }
            }
        }
        
        double finalScore = totalWeight > 0 ? weightedScore / totalWeight : 50;
        report.setOverallScore(finalScore);
        
        // ===== 优化后的状态判定逻辑 =====
        // 更清晰的判定标准
        
        // 1. 优先检查严重异常
        if (severeNegative >= 2) {
            report.setOverallStatus("BAD_ANOMALY");
            return;
        }
        if (severePositive >= 2) {
            report.setOverallStatus("GOOD_ANOMALY");
            return;
        }
        
        // 2. 综合评分判定
        if (finalScore >= 75) {
            report.setOverallStatus("GOOD_ANOMALY");
        } else if (finalScore <= 25) {
            report.setOverallStatus("BAD_ANOMALY");
        } else if (finalScore >= 65 && positiveAnomalyCount > negativeAnomalyCount) {
            report.setOverallStatus("GOOD_ANOMALY");
        } else if (finalScore <= 35 && negativeAnomalyCount > positiveAnomalyCount) {
            report.setOverallStatus("BAD_ANOMALY");
        } else {
            // 3. 异常数量判定
            if (negativeAnomalyCount >= 3 && positiveAnomalyCount <= 1) {
                report.setOverallStatus("BAD_ANOMALY");
            } else if (positiveAnomalyCount >= 3 && negativeAnomalyCount <= 1) {
                report.setOverallStatus("GOOD_ANOMALY");
            } else {
                report.setOverallStatus("NORMAL");
            }
        }
    }
    
    /**
     * 简化报告（数据量不足时使用）
     */
    private AnomalyAnalysisReport createSimpleReport(ArticleData article, List<ArticleData> validArticles) {
        AnomalyAnalysisReport report = new AnomalyAnalysisReport();
        
        // 基于绝对值判断
        long readCount = article.getReadCount7d() != null ? article.getReadCount7d() : 0;
        long interactionCount = article.getInteractionCount7d() != null ? article.getInteractionCount7d() : 0;
        
        double score = 50;
        String status = "NORMAL";
        
        // 简单阈值判断
        if (readCount > 10000) {
            score = 80;
            status = "GOOD_ANOMALY";
        } else if (readCount > 5000) {
            score = 65;
        } else if (readCount < 500) {
            score = 30;
            status = "BAD_ANOMALY";
        } else if (readCount < 1000) {
            score = 40;
        }
        
        // 互动率调整
        if (readCount > 0) {
            double interactionRate = (double) interactionCount / readCount * 100;
            if (interactionRate > 8) score += 10;
            else if (interactionRate < 2) score -= 10;
        }
        
        score = Math.max(0, Math.min(100, score));
        
        report.setOverallScore(score);
        report.setOverallStatus(status);
        report.setResults(new ArrayList<>());
        
        return report;
    }
    
    // ==================== 辅助方法 ====================
    
    private double calculateMedian(List<Double> values) {
        if (values.isEmpty()) return 0;
        int size = values.size();
        if (size % 2 == 0) {
            return (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
        } else {
            return values.get(size / 2);
        }
    }
    
    private double calculateMAD(List<Double> values, double median) {
        List<Double> deviations = values.stream()
            .map(v -> Math.abs(v - median))
            .sorted()
            .collect(Collectors.toList());
        return calculateMedian(deviations);
    }
    
    private double calculatePercentile(double value, List<Double> sortedValues) {
        if (sortedValues.isEmpty()) return 50;
        
        int count = 0;
        for (double v : sortedValues) {
            if (v <= value) count++;
        }
        return (double) count / sortedValues.size() * 100;
    }
    
    private double calculateQuantile(List<Double> sortedValues, double quantile) {
        if (sortedValues.isEmpty()) return 0;
        
        int index = (int) Math.ceil(quantile * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }
    
    private AnomalyAnalysisResult createEmptyResult(String metricName, double weight) {
        AnomalyAnalysisResult result = new AnomalyAnalysisResult();
        result.setMetric(metricName);
        result.setValue(0.0);
        result.setMean(0.0);
        result.setStdDev(0.0);
        result.setZScore(0.0);
        result.setPercentile(50.0);
        result.setDeviation("数据不足");
        result.setLevel("NORMAL");
        result.setWeight(weight);
        return result;
    }
}