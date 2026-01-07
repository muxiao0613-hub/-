package com.fxt.backend.dto;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MetricStatistics {
    private double mean;
    private double stdDev;
    private double min;
    private double max;
    private List<Double> sortedValues;

    public MetricStatistics(List<Double> values) {
        if (values == null || values.isEmpty()) {
            this.mean = 0;
            this.stdDev = 0;
            this.min = 0;
            this.max = 0;
            this.sortedValues = Collections.emptyList();
            return;
        }

        // 计算基本统计量
        this.sortedValues = values.stream().sorted().collect(Collectors.toList());
        this.min = sortedValues.get(0);
        this.max = sortedValues.get(sortedValues.size() - 1);
        
        // 计算平均值
        this.mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        
        // 计算标准差
        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0);
        this.stdDev = Math.sqrt(variance);
    }

    public double calculatePercentile(double value) {
        if (sortedValues.isEmpty()) return 50;
        
        int count = 0;
        for (double v : sortedValues) {
            if (v <= value) count++;
        }
        return (double) count / sortedValues.size() * 100;
    }

    public double getQ1() {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil(sortedValues.size() * 0.25) - 1;
        return sortedValues.get(Math.max(0, index));
    }

    public double getQ3() {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil(sortedValues.size() * 0.75) - 1;
        return sortedValues.get(Math.min(sortedValues.size() - 1, index));
    }

    public double getIQR() {
        return getQ3() - getQ1();
    }

    // Getters
    public double getMean() { return mean; }
    public double getStdDev() { return stdDev; }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public List<Double> getSortedValues() { return sortedValues; }
}