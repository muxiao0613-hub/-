package com.fxt.backend.controller;

import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.service.DetailedOptimizationService;
import com.fxt.backend.service.AIRecommendationService;
import com.fxt.backend.service.EnhancedImageDownloadService;
import com.fxt.backend.service.AIApiService;
import com.fxt.backend.service.PlaywrightCrawlerService;
import com.fxt.backend.repository.ArticleDataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 增强分析控制器
 * 提供AI建议、图片分析等新功能的API接口
 */
@RestController
@RequestMapping("/api/enhanced")
@CrossOrigin(origins = "*")
public class EnhancedAnalysisController {
    
    @Autowired
    private ArticleDataRepository articleDataRepository;
    
    @Autowired
    private DetailedOptimizationService detailedOptimizationService;
    
    @Autowired
    private AIRecommendationService aiRecommendationService;
    
    @Autowired
    private EnhancedImageDownloadService imageDownloadService;
    
    @Autowired
    private AIApiService aiApiService;
    
    @Autowired
    private PlaywrightCrawlerService playwrightCrawlerService;
    
    /**
     * 获取文章的AI建议
     */
    @GetMapping("/articles/{id}/ai-suggestions")
    public ResponseEntity<Map<String, Object>> getAISuggestions(@PathVariable Long id) {
        try {
            ArticleData article = articleDataRepository.findById(id).orElse(null);
            if (article == null) {
                return ResponseEntity.notFound().build();
            }
            
            // 如果还没有AI建议，生成一个
            if (article.getAiSuggestions() == null || article.getAiSuggestions().isEmpty()) {
                String aiSuggestions = detailedOptimizationService.generateAISuggestions(article);
                article.setAiSuggestions(aiSuggestions);
                articleDataRepository.save(article);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("aiSuggestions", article.getAiSuggestions());
            response.put("hasImages", article.getImagesDownloaded() != null && article.getImagesDownloaded());
            response.put("imagesPath", article.getLocalImagesPath());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 重新生成AI建议
     */
    @PostMapping("/articles/{id}/regenerate-ai")
    public ResponseEntity<Map<String, Object>> regenerateAISuggestions(@PathVariable Long id) {
        try {
            ArticleData article = articleDataRepository.findById(id).orElse(null);
            if (article == null) {
                return ResponseEntity.notFound().build();
            }
            
            // 重新生成AI建议
            String aiSuggestions = detailedOptimizationService.generateAISuggestions(article);
            article.setAiSuggestions(aiSuggestions);
            articleDataRepository.save(article);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("aiSuggestions", aiSuggestions);
            response.put("message", "AI建议已重新生成");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 获取图片分析信息
     */
    @GetMapping("/articles/{id}/images-analysis")
    public ResponseEntity<Map<String, Object>> getImagesAnalysis(@PathVariable Long id) {
        try {
            ArticleData article = articleDataRepository.findById(id).orElse(null);
            if (article == null) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("imagesInfo", article.getImagesInfo());
            response.put("imagesDownloaded", article.getImagesDownloaded());
            response.put("localImagesPath", article.getLocalImagesPath());
            response.put("crawlStatus", article.getCrawlStatus());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 获取系统统计信息（包含新功能）
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getEnhancedStatistics() {
        try {
            List<ArticleData> allArticles = articleDataRepository.findAll();
            
            long totalArticles = allArticles.size();
            long articlesWithAI = allArticles.stream()
                .mapToLong(a -> (a.getAiSuggestions() != null && !a.getAiSuggestions().isEmpty()) ? 1 : 0)
                .sum();
            long articlesWithImages = allArticles.stream()
                .mapToLong(a -> (a.getImagesDownloaded() != null && a.getImagesDownloaded()) ? 1 : 0)
                .sum();
            long successfulCrawls = allArticles.stream()
                .mapToLong(a -> "SUCCESS".equals(a.getCrawlStatus()) ? 1 : 0)
                .sum();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalArticles", totalArticles);
            stats.put("articlesWithAI", articlesWithAI);
            stats.put("articlesWithImages", articlesWithImages);
            stats.put("successfulCrawls", successfulCrawls);
            stats.put("aiCoverage", totalArticles > 0 ? (double) articlesWithAI / totalArticles * 100 : 0);
            stats.put("imageCoverage", totalArticles > 0 ? (double) articlesWithImages / totalArticles * 100 : 0);
            stats.put("crawlSuccessRate", totalArticles > 0 ? (double) successfulCrawls / totalArticles * 100 : 0);
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 批量生成AI建议
     */
    @PostMapping("/batch-generate-ai")
    public ResponseEntity<Map<String, Object>> batchGenerateAI(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<ArticleData> articles = articleDataRepository.findAll().stream()
                .filter(a -> a.getAiSuggestions() == null || a.getAiSuggestions().isEmpty())
                .limit(limit)
                .toList();
            
            int processed = 0;
            for (ArticleData article : articles) {
                try {
                    String aiSuggestions = detailedOptimizationService.generateAISuggestions(article);
                    article.setAiSuggestions(aiSuggestions);
                    articleDataRepository.save(article);
                    processed++;
                } catch (Exception e) {
                    System.err.println("生成AI建议失败: " + article.getTitle() + " - " + e.getMessage());
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("processed", processed);
            response.put("total", articles.size());
            response.put("message", String.format("成功为 %d 篇文章生成AI建议", processed));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 清理过期下载文件
     */
    @PostMapping("/cleanup-downloads")
    public ResponseEntity<Map<String, Object>> cleanupDownloads(
            @RequestParam(defaultValue = "7") int daysOld) {
        try {
            imageDownloadService.cleanupOldDownloads(daysOld);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("已清理 %d 天前的下载文件", daysOld));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 获取 AI 服务状态
     */
    @GetMapping("/ai-status")
    public ResponseEntity<Map<String, Object>> getAIStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("available", aiApiService.isAvailable());
        response.put("message", aiApiService.isAvailable() ? 
            "AI 服务已启用" : "AI 服务未配置，使用本地分析模式");
        return ResponseEntity.ok(response);
    }
    
    /**
     * 生成真正的 AI 建议
     */
    @PostMapping("/articles/{id}/generate-ai")
    public ResponseEntity<Map<String, Object>> generateRealAISuggestions(@PathVariable Long id) {
        try {
            ArticleData article = articleDataRepository.findById(id).orElse(null);
            if (article == null) {
                return ResponseEntity.notFound().build();
            }
            
            List<ArticleData> allArticles = articleDataRepository.findAll();
            String aiSuggestions = aiApiService.generateAnalysis(article, allArticles);
            
            article.setAiSuggestions(aiSuggestions);
            articleDataRepository.save(article);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("aiSuggestions", aiSuggestions);
            response.put("aiAvailable", aiApiService.isAvailable());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 使用 Playwright 重新爬取内容
     */
    @PostMapping("/articles/{id}/recrawl")
    public ResponseEntity<Map<String, Object>> recrawlArticle(@PathVariable Long id) {
        try {
            ArticleData article = articleDataRepository.findById(id).orElse(null);
            if (article == null) {
                return ResponseEntity.notFound().build();
            }
            
            String url = article.getArticleLink();
            if (url == null || url.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "文章链接为空");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            String articleId = article.getDataId() != null ? article.getDataId() : "article_" + article.getId();
            PlaywrightCrawlerService.CrawlResult result = playwrightCrawlerService.crawlDewuArticle(url, articleId);
            
            if (result.isSuccess()) {
                article.setContent(result.getTextContent());
                article.setCrawlStatus("SUCCESS");
                article.setCrawlError(null);
                
                if (result.getImages() != null && !result.getImages().isEmpty()) {
                    ObjectMapper mapper = new ObjectMapper();
                    article.setImagesInfo(mapper.writeValueAsString(result.getImages()));
                    article.setImagesDownloaded(true);
                    article.setLocalImagesPath(result.getLocalImagesPath());
                }
                
                articleDataRepository.save(article);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("imagesCount", result.getImages() != null ? result.getImages().size() : 0);
            response.put("content", result.getTextContent());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}