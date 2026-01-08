package com.fxt.backend.service;

import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.repository.ArticleDataRepository;
import com.fxt.backend.dto.TitleAnalysis;
import com.fxt.backend.dto.AnomalyAnalysisReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class ContentAnalysisService {
    
    @Autowired
    private ArticleDataRepository articleDataRepository;
    
    @Autowired
    private EnhancedAnalysisService enhancedAnalysisService;
    
    @Autowired
    private DetailedOptimizationService detailedOptimizationService;
    
    public void analyzeAndGenerateOptimizations(ArticleData article) {
        // 1. 分析标题
        TitleAnalysis titleAnalysis = TitleAnalysis.analyze(article.getTitle());
        article.setTitleAnalysis(titleAnalysis.toJson());
        
        // 2. 使用详细优化服务生成完整的分析报告
        String detailedOptimizations = detailedOptimizationService.generateDetailedOptimizations(article);
        
        article.setOptimizationSuggestions(detailedOptimizations);
    }
    
    private void generateTitleSuggestions(TitleAnalysis titleAnalysis, StringBuilder sb) {
        if (titleAnalysis.getLength() < 10) {
            sb.append("⚠️ 标题过短（仅").append(titleAnalysis.getLength()).append("字）\n");
            sb.append("   分析：短标题信息量不足，难以吸引用户点击\n");
            sb.append("   建议：扩展至15-25字，补充具体场景或痛点\n\n");
        } else if (titleAnalysis.getLength() > 30) {
            sb.append("⚠️ 标题过长（").append(titleAnalysis.getLength()).append("字）\n");
            sb.append("   分析：标题过长可能影响阅读体验\n");
            sb.append("   建议：精简至15-25字，突出核心卖点\n\n");
        }
        
        if (!titleAnalysis.isHasEmotionalWords()) {
            sb.append("⚠️ 缺少情感词汇\n");
            sb.append("   分析：标题过于平淡，缺乏感染力\n");
            sb.append("   建议：添加「绝了」「必买」「值得」等情感词\n\n");
        }
        
        if (!titleAnalysis.isHasSpecificNumber()) {
            sb.append("⚠️ 缺少具体数字\n");
            sb.append("   分析：数字能增加标题的可信度和吸引力\n");
            sb.append("   建议：添加「3个技巧」「7天见效」等具体数字\n\n");
        }
    }
    
    private void generateContentTypeSuggestions(ArticleData article, StringBuilder sb) {
        sb.append("📸 图文内容优化建议:\n");
        
        // 基于内容形式给出建议
        if ("图文".equals(article.getContentType())) {
            sb.append("   ✅ 当前为图文内容，符合平台特性\n");
            sb.append("   • 图片数量：建议3-9张，保持奇数更有视觉冲击力\n");
            sb.append("   • 图片质量：确保高清、光线充足、构图美观\n");
            sb.append("   • 图片顺序：首图最重要，要有吸引力和代表性\n");
            sb.append("   • 文字搭配：每张图片配1-2句精炼描述\n");
            sb.append("   • 视觉风格：保持滤镜、色调、风格的统一性\n");
            sb.append("   • 产品展示：多角度展示产品细节和使用效果\n\n");
        }
        
        // 基于素材来源给出建议
        if (article.getMaterialSource() != null) {
            sb.append(String.format("📷 素材来源分析：%s\n", article.getMaterialSource()));
            if ("新媒体图文".equals(article.getMaterialSource())) {
                sb.append("   • 优势：专业的新媒体素材，视觉效果有保障\n");
                sb.append("   • 建议：保持素材的专业性，注意与品牌调性匹配\n");
            }
            sb.append("\n");
        }
        
        // 转化漏斗分析
        sb.append("🔄 转化漏斗优化：\n");
        sb.append("   阅读 → 好物访问 → 好物想要 的完整转化路径\n");
        
        if (article.getReadCount7d() != null && article.getProductVisit7d() != null && article.getProductWant7d() != null) {
            double visitRate = (double) article.getProductVisit7d() / article.getReadCount7d() * 100;
            double wantRate = article.getProductVisit7d() > 0 ? 
                (double) article.getProductWant7d() / article.getProductVisit7d() * 100 : 0;
            
            sb.append(String.format("   • 访问转化率：%.1f%% (%d/%d)\n", 
                visitRate, article.getProductVisit7d(), article.getReadCount7d()));
            sb.append(String.format("   • 想要转化率：%.1f%% (%d/%d)\n", 
                wantRate, article.getProductWant7d(), article.getProductVisit7d()));
            
            if (visitRate < 5) {
                sb.append("   ⚠️ 访问转化率偏低，建议优化产品展示和引导\n");
            }
            if (wantRate < 10) {
                sb.append("   ⚠️ 想要转化率偏低，建议突出产品卖点和性价比\n");
            }
        }
    }
}