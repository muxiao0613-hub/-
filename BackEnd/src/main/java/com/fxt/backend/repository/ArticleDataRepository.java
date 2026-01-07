package com.fxt.backend.repository;

import com.fxt.backend.entity.ArticleData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArticleDataRepository extends JpaRepository<ArticleData, Long> {
    
    List<ArticleData> findByAnomalyStatus(String anomalyStatus);
    
    @Query("SELECT a FROM ArticleData a WHERE a.anomalyStatus IN ('GOOD_ANOMALY', 'BAD_ANOMALY')")
    List<ArticleData> findAnomalousArticles();
    
    @Query("SELECT AVG(a.readCount7d) FROM ArticleData a WHERE a.readCount7d IS NOT NULL")
    Double getAverageReadCount7d();
    
    @Query("SELECT AVG(a.interactionCount7d) FROM ArticleData a WHERE a.interactionCount7d IS NOT NULL")
    Double getAverageInteractionCount7d();
    
    @Query("SELECT AVG(a.shareCount7d) FROM ArticleData a WHERE a.shareCount7d IS NOT NULL")
    Double getAverageShareCount7d();
}