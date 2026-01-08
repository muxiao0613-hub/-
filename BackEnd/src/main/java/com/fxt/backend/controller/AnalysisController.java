package com.fxt.backend.controller;

import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.service.AnalysisService;
import com.fxt.backend.dto.ArticleDetailResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
@CrossOrigin(origins = "*")
public class AnalysisController {
    
    @Autowired
    private AnalysisService analysisService;
    
    @PostMapping("/upload")
    public ResponseEntity<?> uploadExcel(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("文件不能为空");
            }
            
            List<ArticleData> articles = analysisService.processExcelFile(file);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "文件上传和分析成功");
            response.put("totalCount", articles.size());
            response.put("articles", articles);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "文件处理失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/articles")
    public ResponseEntity<List<ArticleData>> getAllArticles() {
        List<ArticleData> articles = analysisService.getAllArticles();
        return ResponseEntity.ok(articles);
    }
    
    @GetMapping("/articles/anomalous")
    public ResponseEntity<List<ArticleData>> getAnomalousArticles() {
        List<ArticleData> articles = analysisService.getAnomalousArticles();
        return ResponseEntity.ok(articles);
    }
    
    @GetMapping("/articles/status/{status}")
    public ResponseEntity<List<ArticleData>> getArticlesByStatus(@PathVariable String status) {
        List<ArticleData> articles = analysisService.getArticlesByStatus(status);
        return ResponseEntity.ok(articles);
    }
    
    @GetMapping("/articles/{id}/detail")
    public ResponseEntity<ArticleDetailResponse> getArticleDetail(@PathVariable Long id) {
        ArticleData article = analysisService.getArticleById(id);
        if (article != null) {
            ArticleDetailResponse response = analysisService.getArticleDetailResponse(article);
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/articles/{id}")
    public ResponseEntity<ArticleData> getArticleById(@PathVariable Long id) {
        ArticleData article = analysisService.getArticleById(id);
        if (article != null) {
            return ResponseEntity.ok(article);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        List<ArticleData> allArticles = analysisService.getAllArticles();
        
        long normalCount = allArticles.stream()
            .filter(a -> "NORMAL".equals(a.getAnomalyStatus()))
            .count();
        
        long goodAnomalyCount = allArticles.stream()
            .filter(a -> "GOOD_ANOMALY".equals(a.getAnomalyStatus()))
            .count();
        
        long badAnomalyCount = allArticles.stream()
            .filter(a -> "BAD_ANOMALY".equals(a.getAnomalyStatus()))
            .count();
        
        double avgReadCount = allArticles.stream()
            .filter(a -> a.getReadCount7d() != null)
            .mapToLong(ArticleData::getReadCount7d)
            .average()
            .orElse(0.0);
        
        double avgInteractionCount = allArticles.stream()
            .filter(a -> a.getInteractionCount7d() != null)
            .mapToLong(ArticleData::getInteractionCount7d)
            .average()
            .orElse(0.0);
        
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalCount", allArticles.size());
        statistics.put("normalCount", normalCount);
        statistics.put("goodAnomalyCount", goodAnomalyCount);
        statistics.put("badAnomalyCount", badAnomalyCount);
        statistics.put("avgReadCount", avgReadCount);
        statistics.put("avgInteractionCount", avgInteractionCount);
        
        return ResponseEntity.ok(statistics);
    }
    
    @DeleteMapping("/articles")
    public ResponseEntity<Map<String, Object>> deleteAllArticles() {
        analysisService.deleteAllArticles();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "所有数据已清除");
        
        return ResponseEntity.ok(response);
    }
}