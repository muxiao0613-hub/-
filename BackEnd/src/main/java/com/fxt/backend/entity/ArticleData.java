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
    
    @Column(name = "product_visit_count")
    private Long productVisitCount;
    
    @Column(name = "anomaly_status")
    private String anomalyStatus; // NORMAL, GOOD_ANOMALY, BAD_ANOMALY
    
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "optimization_suggestions", columnDefinition = "TEXT")
    private String optimizationSuggestions;
    
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

    public Long getProductVisitCount() { return productVisitCount; }
    public void setProductVisitCount(Long productVisitCount) { this.productVisitCount = productVisitCount; }

    public String getAnomalyStatus() { return anomalyStatus; }
    public void setAnomalyStatus(String anomalyStatus) { this.anomalyStatus = anomalyStatus; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getOptimizationSuggestions() { return optimizationSuggestions; }
    public void setOptimizationSuggestions(String optimizationSuggestions) { this.optimizationSuggestions = optimizationSuggestions; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}