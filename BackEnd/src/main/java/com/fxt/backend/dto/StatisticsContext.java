package com.fxt.backend.dto;

public class StatisticsContext {
    private MetricStatistics readCountStats;
    private MetricStatistics interactionCountStats;
    private MetricStatistics shareCountStats;
    private MetricStatistics productVisitStats;

    public StatisticsContext() {}

    public StatisticsContext(MetricStatistics readCountStats, 
                           MetricStatistics interactionCountStats,
                           MetricStatistics shareCountStats,
                           MetricStatistics productVisitStats) {
        this.readCountStats = readCountStats;
        this.interactionCountStats = interactionCountStats;
        this.shareCountStats = shareCountStats;
        this.productVisitStats = productVisitStats;
    }

    // Getters and Setters
    public MetricStatistics getReadCountStats() { return readCountStats; }
    public void setReadCountStats(MetricStatistics readCountStats) { this.readCountStats = readCountStats; }

    public MetricStatistics getInteractionCountStats() { return interactionCountStats; }
    public void setInteractionCountStats(MetricStatistics interactionCountStats) { this.interactionCountStats = interactionCountStats; }

    public MetricStatistics getShareCountStats() { return shareCountStats; }
    public void setShareCountStats(MetricStatistics shareCountStats) { this.shareCountStats = shareCountStats; }

    public MetricStatistics getProductVisitStats() { return productVisitStats; }
    public void setProductVisitStats(MetricStatistics productVisitStats) { this.productVisitStats = productVisitStats; }
}