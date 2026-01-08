package com.fxt.backend.service;

import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.dto.AnomalyAnalysisReport;
import com.fxt.backend.dto.AnomalyAnalysisResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 高级异常检测服务 - 优化版
 * 实现多种算法：Z-score、IQR、Isolation Forest、LOF、DBSCAN
 * 使用多维度综合评分机制
 */
@Service
public class AdvancedAnomalyDetectionService {
    
    // 异常检测阈值配置
    private static final double Z_SCORE_SEVERE = 3.0;
    private static final double Z_SCORE_MODERATE = 2.0;
    private static final double Z_SCORE_MILD = 1.5;
    private static final double IQR_MULTIPLIER = 1.5;
    private static final double ISOLATION_SCORE_THRESHOLD = -0.5;
    
    public AnomalyAnalysisReport detectAnomalies(ArticleData article, List<ArticleData> allArticles) {
        AnomalyAnalysisReport report = new AnomalyAnalysisReport();
        List<AnomalyAnalysisResult> results = new ArrayList<>();
        
        // 过滤有效数据
        List<ArticleData> validArticles = allArticles.stream()
            .filter(a -> a.getReadCount7d() != null && a.getReadCount7d() > 0)
            .collect(Collectors.toList());
        
        if (validArticles.size() < 5) {
            return createBasicReport(article);
        }
        
        // ==================== 核心指标分析 ====================
        
        // 1. 7天阅读量分析（权重：30%）
        AnomalyAnalysisResult readResult = analyzeMetricAdvanced(
            article, validArticles, "7天阅读量", 
            ArticleData::getReadCount7d, 0.30
        );
        results.add(readResult);
        
        // 2. 7天互动量分析（权重：25%）
        AnomalyAnalysisResult interactionResult = analyzeMetricAdvanced(
            article, validArticles, "7天互动量", 
            ArticleData::getInteractionCount7d, 0.25
        );
        results.add(interactionResult);
        
        // 3. 7天好物访问分析（权重：20%）
        AnomalyAnalysisResult visitResult = analyzeMetricAdvanced(
            article, validArticles, "7天好物访问", 
            ArticleData::getProductVisit7d, 0.20
        );
        results.add(visitResult);
        
        // 4. 互动率分析（权重：15%）
        AnomalyAnalysisResult interactionRateResult = analyzeInteractionRate(article, validArticles);
        interactionRateResult.setWeight(0.15);
        results.add(interactionRateResult);
        
        // 5. 转化率分析（权重：10%）
        AnomalyAnalysisResult conversionResult = analyzeConversionRate(article, validArticles);
        conversionResult.setWeight(0.10);
        results.add(conversionResult);
        
        // 6. Isolation Forest 综合分析
        AnomalyAnalysisResult isolationResult = performIsolationForestAnalysis(article, validArticles);
        results.add(isolationResult);
        
        // 7. LOF 局部离群因子分析
        AnomalyAnalysisResult lofResult = performLOFAnalysis(article, validArticles);
        results.add(lofResult);
        
        // 8. 增长趋势分析
        AnomalyAnalysisResult growthResult = analyzeGrowthTrend(article, validArticles);
        results.add(growthResult);
        
        report.setResults(results);
        
        // 计算加权综合评分和状态
        calculateWeightedOverallStatus(report);
        
        return report;
    }
    
    /**
     * 高级指标分析 - 结合Z-score、IQR和百分位
     */
    private AnomalyAnalysisResult analyzeMetricAdvanced(
            ArticleData article, 
            List<ArticleData> allArticles, 
            String metricName, 
            java.util.function.Function<ArticleData, Long> getter,
            double weight) {
        
        List<Double> values = allArticles.stream()
            .map(getter)
            .filter(Objects::nonNull)
            .filter(v -> v > 0)
            .map(Long::doubleValue)
            .sorted()
            .collect(Collectors.toList());
        
        if (values.isEmpty()) {
            return createEmptyResult(metricName);
        }
        
        double currentValue = getter.apply(article) != null ? getter.apply(article).doubleValue() : 0;
        
        // 统计指标
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        
        // 使用稳健统计量（中位数和MAD）
        double median = calculateMedian(values);
        double mad = calculateMAD(values, median);
        
        // Z-score计算（传统和稳健）
        double zScore = stdDev > 0 ? (currentValue - mean) / stdDev : 0;
        double robustZScore = mad > 0 ? 0.6745 * (currentValue - median) / mad : 0;
        
        // 综合Z分数（取两者的加权平均）
        double combinedZScore = 0.6 * zScore + 0.4 * robustZScore;
        
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
        
        // 偏离描述
        if (mean > 0) {
            double deviationPct = (currentValue - mean) / mean * 100;
            String direction = deviationPct > 0 ? "高于" : "低于";
            result.setDeviation(String.format("%s平均值 %.1f%%", direction, Math.abs(deviationPct)));
        } else {
            result.setDeviation("无参考数据");
        }
        
        // 异常等级判定（综合多种方法）
        result.setLevel(determineAnomalyLevel(combinedZScore, isIQROutlier, percentile, currentValue > mean));
        
        return result;
    }
    
    /**
     * 互动率分析
     */
    private AnomalyAnalysisResult analyzeInteractionRate(ArticleData article, List<ArticleData> allArticles) {
        List<Double> rates = allArticles.stream()
            .filter(a -> a.getReadCount7d() != null && a.getReadCount7d() > 100 && a.getInteractionCount7d() != null)
            .map(a -> (double) a.getInteractionCount7d() / a.getReadCount7d() * 100)
            .sorted()
            .collect(Collectors.toList());
        
        double currentRate = 0;
        if (article.getReadCount7d() != null && article.getReadCount7d() > 0 && article.getInteractionCount7d() != null) {
            currentRate = (double) article.getInteractionCount7d() / article.getReadCount7d() * 100;
        }
        
        if (rates.isEmpty()) {
            return createEmptyResult("互动率");
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
        result.setDeviation(String.format("%.2f%% (平均: %.2f%%)", currentRate, mean));
        result.setLevel(determineAnomalyLevel(zScore, false, percentile, currentRate > mean));
        
        return result;
    }
    
    /**
     * 转化率分析
     */
    private AnomalyAnalysisResult analyzeConversionRate(ArticleData article, List<ArticleData> allArticles) {
        List<Double> rates = allArticles.stream()
            .filter(a -> a.getReadCount7d() != null && a.getReadCount7d() > 100 && a.getProductVisit7d() != null)
            .map(a -> (double) a.getProductVisit7d() / a.getReadCount7d() * 100)
            .sorted()
            .collect(Collectors.toList());
        
        double currentRate = 0;
        if (article.getReadCount7d() != null && article.getReadCount7d() > 0 && article.getProductVisit7d() != null) {
            currentRate = (double) article.getProductVisit7d() / article.getReadCount7d() * 100;
        }
        
        if (rates.isEmpty()) {
            return createEmptyResult("好物转化率");
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
        result.setLevel(determineAnomalyLevel(zScore, false, percentile, currentRate > mean));
        
        return result;
    }
    
    /**
     * 增长趋势分析
     */
    private AnomalyAnalysisResult analyzeGrowthTrend(ArticleData article, List<ArticleData> allArticles) {
        List<Double> growthRates = allArticles.stream()
            .filter(a -> a.getReadCount7d() != null && a.getReadCount7d() > 0 && a.getReadCount14d() != null)
            .map(a -> (double) (a.getReadCount14d() - a.getReadCount7d()) / a.getReadCount7d() * 100)
            .sorted()
            .collect(Collectors.toList());
        
        double currentGrowth = 0;
        if (article.getReadCount7d() != null && article.getReadCount7d() > 0 && article.getReadCount14d() != null) {
            currentGrowth = (double) (article.getReadCount14d() - article.getReadCount7d()) / article.getReadCount7d() * 100;
        }
        
        if (growthRates.isEmpty()) {
            return createEmptyResult("增长趋势");
        }
        
        double mean = growthRates.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double stdDev = Math.sqrt(growthRates.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0));
        double zScore = stdDev > 0 ? (currentGrowth - mean) / stdDev : 0;
        double percentile = calculatePercentile(currentGrowth, growthRates);
        
        AnomalyAnalysisResult result = new AnomalyAnalysisResult();
        result.setMetric("7-14天增长率");
        result.setValue(currentGrowth);
        result.setMean(mean);
        result.setStdDev(stdDev);
        result.setZScore(zScore);
        result.setPercentile(percentile);
        
        String trendDesc;
        if (currentGrowth > 50) {
            trendDesc = "持续发酵中";
        } else if (currentGrowth > 20) {
            trendDesc = "正常增长";
        } else if (currentGrowth > 0) {
            trendDesc = "增长放缓";
        } else {
            trendDesc = "热度下降";
        }
        result.setDeviation(String.format("%.1f%% (%s)", currentGrowth, trendDesc));
        result.setLevel(determineAnomalyLevel(zScore, false, percentile, currentGrowth > mean));
        
        return result;
    }
    
    /**
     * Isolation Forest 算法实现
     */
    private AnomalyAnalysisResult performIsolationForestAnalysis(ArticleData article, List<ArticleData> allArticles) {
        // 构建特征矩阵
        List<double[]> features = allArticles.stream()
            .map(this::extractFeatures)
            .collect(Collectors.toList());
        
        double[] currentFeatures = extractFeatures(article);
        
        // Isolation Forest评分
        double anomalyScore = calculateIsolationScore(currentFeatures, features, 100, 10);
        
        // 转换为百分位
        List<Double> allScores = features.stream()
            .map(f -> calculateIsolationScore(f, features, 100, 10))
            .sorted()
            .collect(Collectors.toList());
        
        double percentile = calculatePercentile(anomalyScore, allScores);
        
        AnomalyAnalysisResult result = new AnomalyAnalysisResult();
        result.setMetric("Isolation Forest异常评分");
        result.setValue(anomalyScore);
        result.setMean(allScores.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        result.setStdDev(0);
        result.setZScore(0);
        result.setPercentile(percentile);
        result.setDeviation(String.format("异常评分: %.3f (越低越异常)", anomalyScore));
        
        if (anomalyScore < -0.6) {
            result.setLevel("SEVERE");
        } else if (anomalyScore < -0.4) {
            result.setLevel("MODERATE");
        } else if (anomalyScore < -0.2) {
            result.setLevel("MILD");
        } else {
            result.setLevel("NORMAL");
        }
        
        return result;
    }
    
    /**
     * LOF (Local Outlier Factor) 算法实现
     */
    private AnomalyAnalysisResult performLOFAnalysis(ArticleData article, List<ArticleData> allArticles) {
        List<double[]> features = allArticles.stream()
            .map(this::extractFeatures)
            .collect(Collectors.toList());
        
        double[] currentFeatures = extractFeatures(article);
        
        // 计算LOF分数
        int k = Math.min(10, features.size() - 1);
        double lofScore = calculateLOF(currentFeatures, features, k);
        
        // 计算所有点的LOF用于比较
        List<Double> allLOF = new ArrayList<>();
        for (double[] f : features) {
            allLOF.add(calculateLOF(f, features, k));
        }
        allLOF.sort(Double::compare);
        
        double percentile = calculatePercentile(lofScore, allLOF);
        
        AnomalyAnalysisResult result = new AnomalyAnalysisResult();
        result.setMetric("LOF局部离群因子");
        result.setValue(lofScore);
        result.setMean(allLOF.stream().mapToDouble(Double::doubleValue).average().orElse(1));
        result.setStdDev(0);
        result.setZScore(0);
        result.setPercentile(percentile);
        result.setDeviation(String.format("LOF: %.2f (>1.5为异常)", lofScore));
        
        if (lofScore > 2.0) {
            result.setLevel("SEVERE");
        } else if (lofScore > 1.5) {
            result.setLevel("MODERATE");
        } else if (lofScore > 1.2) {
            result.setLevel("MILD");
        } else {
            result.setLevel("NORMAL");
        }
        
        return result;
    }
    
    /**
     * 提取特征向量
     */
    private double[] extractFeatures(ArticleData article) {
        double readCount = article.getReadCount7d() != null ? article.getReadCount7d().doubleValue() : 0;
        double interactionCount = article.getInteractionCount7d() != null ? article.getInteractionCount7d().doubleValue() : 0;
        double productVisit = article.getProductVisit7d() != null ? article.getProductVisit7d().doubleValue() : 0;
        double productWant = article.getProductWant7d() != null ? article.getProductWant7d().doubleValue() : 0;
        
        // 计算比率特征
        double interactionRate = readCount > 0 ? interactionCount / readCount : 0;
        double conversionRate = readCount > 0 ? productVisit / readCount : 0;
        double wantRate = productVisit > 0 ? productWant / productVisit : 0;
        
        // 对数变换减少偏斜
        return new double[]{
            Math.log1p(readCount),
            Math.log1p(interactionCount),
            Math.log1p(productVisit),
            Math.log1p(productWant),
            interactionRate * 100,
            conversionRate * 100,
            wantRate * 100
        };
    }
    
    /**
     * Isolation Forest评分计算
     */
    private double calculateIsolationScore(double[] target, List<double[]> allFeatures, int numTrees, int maxDepth) {
        if (allFeatures.size() < 2) return 0;
        
        double totalPathLength = 0;
        Random random = new Random(42);
        
        for (int i = 0; i < numTrees; i++) {
            totalPathLength += calculatePathLength(target, allFeatures, 0, maxDepth, random);
        }
        
        double avgPathLength = totalPathLength / numTrees;
        double expectedPathLength = calculateExpectedPathLength(allFeatures.size());
        
        return -Math.pow(2, -avgPathLength / expectedPathLength);
    }
    
    private double calculatePathLength(double[] target, List<double[]> data, int depth, int maxDepth, Random random) {
        if (data.size() <= 1 || depth >= maxDepth) {
            return depth + calculateAveragePathLength(data.size());
        }
        
        int featureIndex = random.nextInt(target.length);
        
        double minVal = data.stream().mapToDouble(f -> f[featureIndex]).min().orElse(0);
        double maxVal = data.stream().mapToDouble(f -> f[featureIndex]).max().orElse(0);
        
        if (minVal >= maxVal) {
            return depth + calculateAveragePathLength(data.size());
        }
        
        double splitPoint = minVal + random.nextDouble() * (maxVal - minVal);
        
        List<double[]> leftData = data.stream()
            .filter(f -> f[featureIndex] < splitPoint)
            .collect(Collectors.toList());
        
        if (target[featureIndex] < splitPoint) {
            return calculatePathLength(target, leftData, depth + 1, maxDepth, random);
        } else {
            List<double[]> rightData = data.stream()
                .filter(f -> f[featureIndex] >= splitPoint)
                .collect(Collectors.toList());
            return calculatePathLength(target, rightData, depth + 1, maxDepth, random);
        }
    }
    
    private double calculateAveragePathLength(int n) {
        if (n <= 1) return 0;
        return 2.0 * (Math.log(n - 1) + 0.5772156649) - (2.0 * (n - 1) / n);
    }
    
    private double calculateExpectedPathLength(int n) {
        if (n <= 1) return 0;
        return 2.0 * (Math.log(n - 1) + 0.5772156649) - (2.0 * (n - 1) / n);
    }
    
    /**
     * LOF计算
     */
    private double calculateLOF(double[] target, List<double[]> data, int k) {
        if (data.size() <= k) return 1.0;
        
        // 计算到所有点的距离
        List<double[]> distances = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            double dist = euclideanDistance(target, data.get(i));
            distances.add(new double[]{i, dist});
        }
        distances.sort((a, b) -> Double.compare(a[1], b[1]));
        
        // k近邻
        List<Integer> kNeighbors = new ArrayList<>();
        double kDistance = 0;
        for (int i = 0; i < Math.min(k, distances.size()); i++) {
            if (distances.get(i)[1] > 0) { // 排除自身
                kNeighbors.add((int) distances.get(i)[0]);
                kDistance = distances.get(i)[1];
            }
        }
        
        if (kNeighbors.isEmpty() || kDistance == 0) return 1.0;
        
        // 计算可达密度
        double lrd = calculateLRD(target, data, kNeighbors, k);
        if (lrd == 0) return 1.0;
        
        // 计算LOF
        double sumLrd = 0;
        for (int idx : kNeighbors) {
            List<Integer> neighborKNeighbors = getKNeighbors(data.get(idx), data, k);
            sumLrd += calculateLRD(data.get(idx), data, neighborKNeighbors, k);
        }
        
        return (sumLrd / kNeighbors.size()) / lrd;
    }
    
    private double calculateLRD(double[] point, List<double[]> data, List<Integer> neighbors, int k) {
        if (neighbors.isEmpty()) return 0;
        
        double sumReachDist = 0;
        for (int idx : neighbors) {
            double dist = euclideanDistance(point, data.get(idx));
            double neighborKDist = getKDistance(data.get(idx), data, k);
            sumReachDist += Math.max(dist, neighborKDist);
        }
        
        return sumReachDist > 0 ? neighbors.size() / sumReachDist : 0;
    }
    
    private List<Integer> getKNeighbors(double[] point, List<double[]> data, int k) {
        List<double[]> distances = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            double dist = euclideanDistance(point, data.get(i));
            if (dist > 0) {
                distances.add(new double[]{i, dist});
            }
        }
        distances.sort((a, b) -> Double.compare(a[1], b[1]));
        
        List<Integer> neighbors = new ArrayList<>();
        for (int i = 0; i < Math.min(k, distances.size()); i++) {
            neighbors.add((int) distances.get(i)[0]);
        }
        return neighbors;
    }
    
    private double getKDistance(double[] point, List<double[]> data, int k) {
        List<Double> distances = new ArrayList<>();
        for (double[] other : data) {
            double dist = euclideanDistance(point, other);
            if (dist > 0) {
                distances.add(dist);
            }
        }
        distances.sort(Double::compare);
        return k <= distances.size() ? distances.get(k - 1) : (distances.isEmpty() ? 0 : distances.get(distances.size() - 1));
    }
    
    private double euclideanDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.pow(a[i] - b[i], 2);
        }
        return Math.sqrt(sum);
    }
    
    /**
     * 计算中位数
     */
    private double calculateMedian(List<Double> values) {
        if (values.isEmpty()) return 0;
        int size = values.size();
        if (size % 2 == 0) {
            return (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
        } else {
            return values.get(size / 2);
        }
    }
    
    /**
     * 计算MAD (Median Absolute Deviation)
     */
    private double calculateMAD(List<Double> values, double median) {
        List<Double> deviations = values.stream()
            .map(v -> Math.abs(v - median))
            .sorted()
            .collect(Collectors.toList());
        return calculateMedian(deviations);
    }
    
    /**
     * 综合判定异常等级
     */
    private String determineAnomalyLevel(double zScore, boolean isIQROutlier, double percentile, boolean isPositive) {
        double absZ = Math.abs(zScore);
        
        // 综合判定
        if (absZ > Z_SCORE_SEVERE || (absZ > Z_SCORE_MODERATE && isIQROutlier)) {
            return "SEVERE";
        } else if (absZ > Z_SCORE_MODERATE || (absZ > Z_SCORE_MILD && isIQROutlier)) {
            return "MODERATE";
        } else if (absZ > Z_SCORE_MILD || isIQROutlier) {
            return "MILD";
        } else {
            return "NORMAL";
        }
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
    
    /**
     * 计算加权综合状态
     */
    private void calculateWeightedOverallStatus(AnomalyAnalysisReport report) {
        List<AnomalyAnalysisResult> results = report.getResults();
        
        double weightedScore = 0;
        double totalWeight = 0;
        int positiveAnomalies = 0;
        int negativeAnomalies = 0;
        
        for (AnomalyAnalysisResult result : results) {
            double weight = result.getWeight() != null ? result.getWeight() : 1.0 / results.size();
            
            // 基于Z-score和百分位计算分数
            double score;
            if (result.getZScore() > 0) {
                // 正向异常（表现好）
                score = 50 + result.getZScore() * 10;
            } else {
                // 负向异常（表现差）
                score = 50 + result.getZScore() * 10;
            }
            score = Math.max(0, Math.min(100, score));
            
            weightedScore += score * weight;
            totalWeight += weight;
            
            // 统计异常类型
            if (!"NORMAL".equals(result.getLevel())) {
                if (result.getZScore() > 0 || result.getPercentile() > 70) {
                    positiveAnomalies++;
                } else {
                    negativeAnomalies++;
                }
            }
        }
        
        double finalScore = totalWeight > 0 ? weightedScore / totalWeight : 50;
        report.setOverallScore(finalScore);
        
        // 判断整体状态
        if (negativeAnomalies >= 3 && positiveAnomalies < 2) {
            report.setOverallStatus("BAD_ANOMALY");
        } else if (positiveAnomalies >= 3 && negativeAnomalies < 2) {
            report.setOverallStatus("GOOD_ANOMALY");
        } else if (finalScore < 35) {
            report.setOverallStatus("BAD_ANOMALY");
        } else if (finalScore > 65) {
            report.setOverallStatus("GOOD_ANOMALY");
        } else {
            report.setOverallStatus("NORMAL");
        }
    }
    
    private AnomalyAnalysisReport createBasicReport(ArticleData article) {
        AnomalyAnalysisReport report = new AnomalyAnalysisReport();
        report.setOverallStatus("NORMAL");
        report.setOverallScore(50.0);
        report.setResults(new ArrayList<>());
        return report;
    }
    
    private AnomalyAnalysisResult createEmptyResult(String metricName) {
        AnomalyAnalysisResult result = new AnomalyAnalysisResult();
        result.setMetric(metricName);
        result.setValue(0.0);
        result.setMean(0.0);
        result.setStdDev(0.0);
        result.setZScore(0.0);
        result.setPercentile(50.0);
        result.setDeviation("数据不足");
        result.setLevel("NORMAL");
        return result;
    }
}
