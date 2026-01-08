package com.fxt.backend.controller;

import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.service.*;
import com.fxt.backend.repository.ArticleDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/enhanced")
@CrossOrigin(origins = "*")
public class EnhancedAnalysisController {

    @Autowired
    private ArticleDataRepository articleDataRepository;

    @Autowired
    private AIApiService aiApiService;

    @Autowired
    private ContentCrawlerService contentCrawlerService;

    @Autowired
    private ExportService exportService;

    // ==================== AI建议相关接口 ====================

    @GetMapping("/articles/{id}/ai-suggestions")
    public ResponseEntity<Map<String, Object>> getAISuggestions(@PathVariable Long id) {
        try {
            ArticleData article = articleDataRepository.findById(id).orElse(null);
            if (article == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("aiSuggestions", article.getAiSuggestions());
            response.put("hasAiSuggestions", article.getAiSuggestions() != null && !article.getAiSuggestions().isEmpty());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/articles/{id}/generate-ai")
    public ResponseEntity<Map<String, Object>> generateAISuggestions(@PathVariable Long id) {
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
            response.put("message", aiApiService.isAvailable() ? "AI建议生成成功" : "使用本地分析模式生成建议");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== 导出功能接口 ====================

    /**
     * 导出单篇文章的AI建议为Word文档
     */
    @GetMapping("/articles/{id}/export-ai")
    public ResponseEntity<?> exportAISuggestions(@PathVariable Long id) {
        try {
            ArticleData article = articleDataRepository.findById(id).orElse(null);
            if (article == null) {
                return ResponseEntity.notFound().build();
            }

            ExportService.ExportResult result = exportService.exportAISuggestionsToWord(article);

            if (!result.isSuccess()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", result.getMessage()
                ));
            }

            // 返回文件下载
            File file = new File(result.getFilePath());
            Resource resource = new FileSystemResource(file);

            String encodedFileName = URLEncoder.encode(result.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename*=UTF-8''" + encodedFileName)
                .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * 批量导出AI建议
     */
    @PostMapping("/export-ai-batch")
    public ResponseEntity<?> exportAISuggestionsBatch(@RequestBody Map<String, List<Long>> request) {
        try {
            List<Long> ids = request.get("ids");
            if (ids == null || ids.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "请选择要导出的文章"
                ));
            }

            List<ArticleData> articles = articleDataRepository.findAllById(ids).stream()
                .filter(a -> a.getAiSuggestions() != null && !a.getAiSuggestions().isEmpty())
                .collect(Collectors.toList());

            if (articles.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "所选文章均无AI建议，请先生成AI建议"
                ));
            }

            ExportService.ExportResult result = exportService.exportMultipleAISuggestions(articles);

            if (!result.isSuccess()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", result.getMessage()
                ));
            }

            File file = new File(result.getFilePath());
            Resource resource = new FileSystemResource(file);

            String encodedFileName = URLEncoder.encode(result.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename*=UTF-8''" + encodedFileName)
                .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== 爬取相关接口 ====================

    @PostMapping("/articles/{id}/recrawl")
    public ResponseEntity<Map<String, Object>> recrawlArticle(@PathVariable Long id) {
        try {
            ArticleData article = articleDataRepository.findById(id).orElse(null);
            if (article == null) {
                return ResponseEntity.notFound().build();
            }

            String url = article.getArticleLink();
            if (url == null || url.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "文章链接为空"
                ));
            }

            // 使用ContentCrawlerService进行爬取
            contentCrawlerService.crawlAllContent(article);
            articleDataRepository.save(article);

            // 解析图片数量
            int imagesCount = 0;
            if (article.getImagesInfo() != null && !article.getImagesInfo().isEmpty()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    String[] imageUrls = mapper.readValue(article.getImagesInfo(), String[].class);
                    imagesCount = imageUrls.length;
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", "SUCCESS".equals(article.getCrawlStatus()));
            response.put("message", article.getCrawlError() != null ? article.getCrawlError() : "爬取完成");
            response.put("imagesCount", imagesCount);
            response.put("content", article.getContent());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== 其他接口 ====================

    @GetMapping("/ai-status")
    public ResponseEntity<Map<String, Object>> getAIStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("available", aiApiService.isAvailable());
        response.put("message", aiApiService.isAvailable() ? 
            "AI服务已启用（OpenAI）" : "AI服务未配置，使用本地分析模式");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getEnhancedStatistics() {
        try {
            List<ArticleData> allArticles = articleDataRepository.findAll();

            long totalArticles = allArticles.size();
            long articlesWithAI = allArticles.stream()
                .filter(a -> a.getAiSuggestions() != null && !a.getAiSuggestions().isEmpty())
                .count();
            long successfulCrawls = allArticles.stream()
                .filter(a -> "SUCCESS".equals(a.getCrawlStatus()))
                .count();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalArticles", totalArticles);
            stats.put("articlesWithAI", articlesWithAI);
            stats.put("successfulCrawls", successfulCrawls);

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}