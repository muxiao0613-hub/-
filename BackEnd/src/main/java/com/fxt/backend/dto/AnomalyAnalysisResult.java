package com.fxt.backend.dto;

public class AnomalyAnalysisResult {
    private String metric;           // 指标名称
    private double value;            // 实际值
    private double mean;             // 平均值
    private double stdDev;           // 标准差
    private double zScore;           // Z分数
    private double percentile;       // 百分位
    private String deviation;        // 偏离描述
    private String level;            // 异常级别 (SEVERE/MODERATE/MILD/NORMAL)
    private Double weight;           // 权重（新增字段）

    public AnomalyAnalysisResult() {}

    public AnomalyAnalysisResult(String metric, double value, double mean, double stdDev) {
        this.metric = metric;
        this.value = value;
        this.mean = mean;
        this.stdDev = stdDev;
        calculateDerivedValues();
    }

    private void calculateDerivedValues() {
        if (stdDev > 0) {
            this.zScore = (value - mean) / stdDev;
        } else {
            this.zScore = 0;
        }

        if (mean > 0) {
            double deviationPercent = ((value - mean) / mean) * 100;
            if (deviationPercent > 0) {
                this.deviation = String.format("高于平均值 %.1f%%", deviationPercent);
            } else {
                this.deviation = String.format("低于平均值 %.1f%%", Math.abs(deviationPercent));
            }
        } else {
            this.deviation = "无法计算偏离度";
        }

        double absZScore = Math.abs(zScore);
        if (absZScore > 3) {
            this.level = "SEVERE";
        } else if (absZScore > 2) {
            this.level = "MODERATE";
        } else if (absZScore > 1.5) {
            this.level = "MILD";
        } else {
            this.level = "NORMAL";
        }
    }

    // Getters and Setters
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public double getValue() { return value; }
    public void setValue(double value) { 
        this.value = value; 
        calculateDerivedValues();
    }

    public double getMean() { return mean; }
    public void setMean(double mean) { 
        this.mean = mean; 
        calculateDerivedValues();
    }

    public double getStdDev() { return stdDev; }
    public void setStdDev(double stdDev) { 
        this.stdDev = stdDev; 
        calculateDerivedValues();
    }

    public double getZScore() { return zScore; }
    public void setZScore(double zScore) { this.zScore = zScore; }

    public double getPercentile() { return percentile; }
    public void setPercentile(double percentile) { this.percentile = percentile; }

    public String getDeviation() { return deviation; }
    public void setDeviation(String deviation) { this.deviation = deviation; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    
    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
}
