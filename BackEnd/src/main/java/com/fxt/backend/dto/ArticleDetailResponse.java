package com.fxt.backend.dto;

import com.fxt.backend.entity.ArticleData;
import java.util.List;
import java.util.Map;

public class ArticleDetailResponse {
    private ArticleData article;
    private AnomalyAnalysisReport anomalyReport;
    private TitleAnalysis titleAnalysis;
    private List<ArticleData> benchmarkArticles; // 对比文章
    private Map<String, Double> brandAverages;    // 品牌平均值

    public ArticleDetailResponse() {}

    public ArticleDetailResponse(ArticleData article) {
        this.article = article;
    }

    // Getters and Setters
    public ArticleData getArticle() { return article; }
    public void setArticle(ArticleData article) { this.article = article; }

    public AnomalyAnalysisReport getAnomalyReport() { return anomalyReport; }
    public void setAnomalyReport(AnomalyAnalysisReport anomalyReport) { this.anomalyReport = anomalyReport; }

    public TitleAnalysis getTitleAnalysis() { return titleAnalysis; }
    public void setTitleAnalysis(TitleAnalysis titleAnalysis) { this.titleAnalysis = titleAnalysis; }

    public List<ArticleData> getBenchmarkArticles() { return benchmarkArticles; }
    public void setBenchmarkArticles(List<ArticleData> benchmarkArticles) { this.benchmarkArticles = benchmarkArticles; }

    public Map<String, Double> getBrandAverages() { return brandAverages; }
    public void setBrandAverages(Map<String, Double> brandAverages) { this.brandAverages = brandAverages; }
}