package com.fxt.backend.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

public class AnomalyAnalysisReport {
    private List<AnomalyAnalysisResult> results = new ArrayList<>();
    private String overallStatus;
    private double overallScore;

    public void addResult(AnomalyAnalysisResult result) {
        results.add(result);
    }

    public void calculateOverallStatus() {
        if (results.isEmpty()) {
            overallStatus = "NORMAL";
            overallScore = 50;
            return;
        }

        // 计算综合评分
        double totalScore = 0;
        int severeCount = 0;
        int moderateCount = 0;
        boolean hasGoodAnomaly = false;
        boolean hasBadAnomaly = false;

        for (AnomalyAnalysisResult result : results) {
            double zScore = result.getZScore();

            if ("SEVERE".equals(result.getLevel())) {
                severeCount++;
                if (zScore > 0) hasGoodAnomaly = true;
                else hasBadAnomaly = true;
            } else if ("MODERATE".equals(result.getLevel())) {
                moderateCount++;
                if (zScore > 0) hasGoodAnomaly = true;
                else hasBadAnomaly = true;
            }

            // 评分：Z分数越高分数越高，归一化到0-100
            totalScore += Math.max(0, Math.min(100, (zScore + 3) / 6 * 100));
        }

        overallScore = totalScore / results.size();

        // 判断整体状态
        if (hasBadAnomaly && !hasGoodAnomaly) {
            overallStatus = "BAD_ANOMALY";
        } else if (hasGoodAnomaly && !hasBadAnomaly) {
            overallStatus = "GOOD_ANOMALY";
        } else if (hasGoodAnomaly && hasBadAnomaly) {
            // 混合情况，根据评分判断
            overallStatus = overallScore >= 50 ? "GOOD_ANOMALY" : "BAD_ANOMALY";
        } else {
            overallStatus = "NORMAL";
        }
    }

    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    // Getters and Setters
    public List<AnomalyAnalysisResult> getResults() { return results; }
    public void setResults(List<AnomalyAnalysisResult> results) { this.results = results; }
    
    public String getOverallStatus() { return overallStatus; }
    public void setOverallStatus(String overallStatus) { this.overallStatus = overallStatus; }
    
    public double getOverallScore() { return overallScore; }
    public void setOverallScore(double overallScore) { this.overallScore = overallScore; }
}