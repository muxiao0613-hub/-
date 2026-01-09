package com.fxt.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "article_data")
public class ArticleData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "data_id")
    private String dataId;
    
    @Column(name = "title")
    private String title;
    
    @Column(name = "brand")
    private String brand;
    
    @Column(name = "publish_time")
    private LocalDateTime publishTime;
    
    @Column(name = "article_link")
    private String articleLink;
    
    @Column(name = "content_type")
    private String contentType;
    
    @Column(name = "post_type")
    private String postType;
    
    @Column(name = "material_source")
    private String materialSource;
    
    @Column(name = "platform")
    private String platform; // 智能识别的平台：得物、小红书、未知平台
    
    @Column(name = "style_info")
    private String styleInfo;
    
    @Column(name = "read_count_7d")
    private Long readCount7d;
    
    @Column(name = "read_count_14d")
    private Long readCount14d;
    
    @Column(name = "interaction_count_7d")
    private Long interactionCount7d;
    
    @Column(name = "interaction_count_14d")
    private Long interactionCount14d;
    
    @Column(name = "share_count_7d")
    private Long shareCount7d;
    
    @Column(name = "share_count_14d")
    private Long shareCount14d;
    
    @Column(name = "product_visit_7d")
    private Long productVisit7d;
    
    @Column(name = "product_visit_count")
    private Long productVisitCount;
    
    @Column(name = "product_want_7d")
    private Long productWant7d;
    
    @Column(name = "product_want_14d")
    private Long productWant14d;
    
    @Column(name = "anomaly_status")
    private String anomalyStatus; // NORMAL, GOOD_ANOMALY, BAD_ANOMALY
    
    @Column(name = "anomaly_details", columnDefinition = "TEXT")
    private String anomalyDetails; // JSON格式的详细异常分析
    
    @Column(name = "anomaly_score")
    private Double anomalyScore; // 综合异常评分 (0-100)
    
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "title_analysis", columnDefinition = "TEXT")
    private String titleAnalysis; // 标题分析JSON
    
    @Column(name = "content_analysis", columnDefinition = "TEXT")
    private String contentAnalysis; // 内容分析JSON
    
    @Column(name = "crawl_status")
    private String crawlStatus; // 抓取状态: SUCCESS/FAILED/PENDING
    
    @Column(name = "crawl_error")
    private String crawlError; // 抓取失败原因
    
    @Column(name = "optimization_suggestions", columnDefinition = "TEXT")
    private String optimizationSuggestions;
    
    @Column(name = "ai_suggestions", columnDefinition = "TEXT")
    private String aiSuggestions; // AI生成的智能建议
    
    @Column(name = "images_info", columnDefinition = "TEXT")
    private String imagesInfo; // 图片信息JSON格式 - 包含原始URL列表
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 构造函数
    public ArticleData() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDataId() { return dataId; }
    public void setDataId(String dataId) { this.dataId = dataId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public LocalDateTime getPublishTime() { return publishTime; }
    public void setPublishTime(LocalDateTime publishTime) { this.publishTime = publishTime; }

    public String getArticleLink() { return articleLink; }
    public void setArticleLink(String articleLink) { this.articleLink = articleLink; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getPostType() { return postType; }
    public void setPostType(String postType) { this.postType = postType; }

    public String getMaterialSource() { return materialSource; }
    public void setMaterialSource(String materialSource) { this.materialSource = materialSource; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getStyleInfo() { return styleInfo; }
    public void setStyleInfo(String styleInfo) { this.styleInfo = styleInfo; }

    public Long getReadCount7d() { return readCount7d; }
    public void setReadCount7d(Long readCount7d) { this.readCount7d = readCount7d; }

    public Long getReadCount14d() { return readCount14d; }
    public void setReadCount14d(Long readCount14d) { this.readCount14d = readCount14d; }

    public Long getInteractionCount7d() { return interactionCount7d; }
    public void setInteractionCount7d(Long interactionCount7d) { this.interactionCount7d = interactionCount7d; }

    public Long getInteractionCount14d() { return interactionCount14d; }
    public void setInteractionCount14d(Long interactionCount14d) { this.interactionCount14d = interactionCount14d; }

    public Long getShareCount7d() { return shareCount7d; }
    public void setShareCount7d(Long shareCount7d) { this.shareCount7d = shareCount7d; }

    public Long getShareCount14d() { return shareCount14d; }
    public void setShareCount14d(Long shareCount14d) { this.shareCount14d = shareCount14d; }

    public Long getProductVisit7d() { return productVisit7d; }
    public void setProductVisit7d(Long productVisit7d) { this.productVisit7d = productVisit7d; }

    public Long getProductVisitCount() { return productVisitCount; }
    public void setProductVisitCount(Long productVisitCount) { this.productVisitCount = productVisitCount; }

    public Long getProductWant7d() { return productWant7d; }
    public void setProductWant7d(Long productWant7d) { this.productWant7d = productWant7d; }

    public Long getProductWant14d() { return productWant14d; }
    public void setProductWant14d(Long productWant14d) { this.productWant14d = productWant14d; }

    public String getAnomalyStatus() { return anomalyStatus; }
    public void setAnomalyStatus(String anomalyStatus) { this.anomalyStatus = anomalyStatus; }

    public String getAnomalyDetails() { return anomalyDetails; }
    public void setAnomalyDetails(String anomalyDetails) { this.anomalyDetails = anomalyDetails; }

    public Double getAnomalyScore() { return anomalyScore; }
    public void setAnomalyScore(Double anomalyScore) { this.anomalyScore = anomalyScore; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTitleAnalysis() { return titleAnalysis; }
    public void setTitleAnalysis(String titleAnalysis) { this.titleAnalysis = titleAnalysis; }

    public String getContentAnalysis() { return contentAnalysis; }
    public void setContentAnalysis(String contentAnalysis) { this.contentAnalysis = contentAnalysis; }

    public String getCrawlStatus() { return crawlStatus; }
    public void setCrawlStatus(String crawlStatus) { this.crawlStatus = crawlStatus; }

    public String getCrawlError() { return crawlError; }
    public void setCrawlError(String crawlError) { this.crawlError = crawlError; }

    public String getOptimizationSuggestions() { return optimizationSuggestions; }
    public void setOptimizationSuggestions(String optimizationSuggestions) { this.optimizationSuggestions = optimizationSuggestions; }

    public String getAiSuggestions() { return aiSuggestions; }
    public void setAiSuggestions(String aiSuggestions) { this.aiSuggestions = aiSuggestions; }

    public String getImagesInfo() { return imagesInfo; }
    public void setImagesInfo(String imagesInfo) { this.imagesInfo = imagesInfo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}