package com.fxt.backend.repository;

import com.fxt.backend.entity.ArticleData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    
    // 按品牌和状态查询
    List<ArticleData> findByBrandAndAnomalyStatus(String brand, String anomalyStatus);
    
    // 按品牌查询并按阅读量排序
    List<ArticleData> findByBrandOrderByReadCount7dDesc(String brand);
    
    // 获取同品牌优秀文章（限制数量）
    List<ArticleData> findTop5ByBrandAndAnomalyStatusOrderByReadCount7dDesc(String brand, String anomalyStatus);
    
    // 按内容类型查询优秀文章
    @Query("SELECT a FROM ArticleData a WHERE a.contentType = :contentType AND a.anomalyStatus = 'GOOD_ANOMALY' ORDER BY a.readCount7d DESC")
    List<ArticleData> findTopByContentType(@Param("contentType") String contentType);
    
    // 获取品牌平均数据
    @Query("SELECT AVG(a.readCount7d), AVG(a.interactionCount7d), AVG(a.shareCount7d) FROM ArticleData a WHERE a.brand = :brand")
    Object[] getBrandAverages(@Param("brand") String brand);
}