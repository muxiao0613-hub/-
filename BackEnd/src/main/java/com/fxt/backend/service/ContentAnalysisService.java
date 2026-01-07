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
    
    private static final List<String> POSITIVE_KEYWORDS = Arrays.asList(
        "推荐", "必买", "好用", "值得", "优质", "热门", "爆款", "限时", "折扣", "特价",
        "新品", "首发", "独家", "精选", "口碑", "评测", "种草", "分享", "体验", "测评"
    );
    
    private static final List<String> ENGAGEMENT_KEYWORDS = Arrays.asList(
        "评论", "点赞", "分享", "收藏", "关注", "互动", "讨论", "交流", "反馈", "建议"
    );
    
    public void analyzeAndGenerateOptimizations(ArticleData article) {
        // 1. 分析标题
        TitleAnalysis titleAnalysis = TitleAnalysis.analyze(article.getTitle());
        article.setTitleAnalysis(titleAnalysis.toJson());
        
        // 2. 获取异常分析详情
        AnomalyAnalysisReport anomalyReport = parseAnomalyDetails(article.getAnomalyDetails());
        
        // 3. 获取同品牌优秀文章作为对比
        List<ArticleData> benchmarkArticles = getBenchmarkArticles(article);
        
        // 4. 生成针对性建议
        String suggestions = generateDetailedSuggestions(
            article, 
            anomalyReport, 
            titleAnalysis, 
            benchmarkArticles
        );
        
        article.setOptimizationSuggestions(suggestions);
    }
    
    private AnomalyAnalysisReport parseAnomalyDetails(String anomalyDetails) {
        if (anomalyDetails == null || anomalyDetails.isEmpty()) {
            return new AnomalyAnalysisReport();
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(anomalyDetails, AnomalyAnalysisReport.class);
        } catch (Exception e) {
            return new AnomalyAnalysisReport();
        }
    }
    
    private String generateDetailedSuggestions(
        ArticleData article,
        AnomalyAnalysisReport anomalyReport,
        TitleAnalysis titleAnalysis,
        List<ArticleData> benchmarkArticles
    ) {
        StringBuilder sb = new StringBuilder();
        
        // === 异常原因分析 ===
        sb.append("【异常原因分析】\n\n");
        
        if (!anomalyReport.getResults().isEmpty()) {
            for (var result : anomalyReport.getResults()) {
                if (!"NORMAL".equals(result.getLevel())) {
                    sb.append(String.format("📊 %s: %.0f\n", result.getMetric(), result.getValue()));
                    sb.append(String.format("   • 平均值: %.0f\n", result.getMean()));
                    sb.append(String.format("   • %s\n", result.getDeviation()));
                    sb.append(String.format("   • 处于所有文章的第 %.0f 百分位\n", result.getPercentile()));
                    sb.append(String.format("   • 异常程度: %s\n\n", getLevelDescription(result.getLevel())));
                }
            }
        } else {
            sb.append("暂无详细的异常分析数据\n\n");
        }
        
        // === 标题分析 ===
        sb.append("【标题分析】\n\n");
        sb.append(String.format("当前标题: 「%s」\n", article.getTitle()));
        sb.append(String.format("标题质量评分: %.0f/100\n\n", titleAnalysis.getQualityScore()));
        
        // 具体分析标题的问题
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
        
        // 对比优秀文章的标题
        if (!benchmarkArticles.isEmpty()) {
            sb.append("📖 同品牌高流量文章标题参考:\n");
            for (int i = 0; i < Math.min(3, benchmarkArticles.size()); i++) {
                ArticleData benchmark = benchmarkArticles.get(i);
                sb.append(String.format("   %d. 「%s」 - 阅读量: %d\n", 
                    i + 1, benchmark.getTitle(), benchmark.getReadCount7d()));
            }
            sb.append("\n");
        }
        
        // === 内容分析 ===
        sb.append("【内容分析】\n\n");
        
        if (article.getContent() != null && !article.getContent().isEmpty()) {
            String content = article.getContent();
            
            // 分析内容结构
            int paragraphCount = content.split("\n").length;
            if (paragraphCount < 3) {
                sb.append("⚠️ 内容结构单薄\n");
                sb.append(String.format("   当前段落数: %d\n", paragraphCount));
                sb.append("   建议：增加内容层次，使用「问题-方案-效果」结构\n\n");
            }
            
            // 分析图片内容 - 更准确的检测
            boolean hasImages = detectImages(content);
            boolean hasVideos = detectVideos(content);
            
            if (hasImages) {
                sb.append("✅ 包含图片内容\n");
                sb.append("   分析：图文结合有助于提升用户体验和互动率\n");
                
                // 分析图片数量和质量
                analyzeImageQuality(content, sb);
                sb.append("\n");
            } else {
                // 检查是否是图文内容但爬虫未检测到
                if ("图文".equals(article.getContentType())) {
                    sb.append("📷 内容类型标记为图文\n");
                    sb.append("   分析：虽然标记为图文内容，但爬虫未检测到图片\n");
                    sb.append("   可能原因：网站反爬虫保护、动态加载图片、或图片格式特殊\n");
                    sb.append("   建议：手动检查原文确认图片质量，优化图片SEO标签\n\n");
                } else {
                    sb.append("⚠️ 缺少图片内容\n");
                    sb.append("   分析：纯文字内容可能影响用户参与度\n");
                    sb.append("   建议：添加相关图片、图表或视觉元素提升吸引力\n\n");
                }
            }
            
            if (hasVideos) {
                sb.append("✅ 包含视频内容\n");
                sb.append("   分析：视频内容通常有更高的互动率和分享率\n");
                sb.append("   优势：多媒体内容能显著提升用户停留时间\n\n");
            }
            
            // 分析关键词密度
            long keywordCount = POSITIVE_KEYWORDS.stream()
                .mapToLong(keyword -> content.split(keyword, -1).length - 1)
                .sum();
            double keywordDensity = (double) keywordCount / content.length();
            
            if (keywordDensity < 0.02) {
                sb.append("⚠️ 关键词密度不足\n");
                sb.append("   建议：适当增加产品相关关键词，提高搜索可见性\n\n");
            }
            
            // 分析互动元素
            boolean hasCallToAction = ENGAGEMENT_KEYWORDS.stream()
                .anyMatch(keyword -> content.contains(keyword));
            if (!hasCallToAction) {
                sb.append("⚠️ 缺少互动引导\n");
                sb.append("   建议：在结尾添加「你们觉得呢？」「评论区告诉我」等互动语\n\n");
            }
            
            // 分析内容长度
            if (content.length() < 200) {
                sb.append("⚠️ 内容过于简短\n");
                sb.append("   建议：丰富内容描述，增加使用体验和详细信息\n\n");
            } else if (content.length() > 2000) {
                sb.append("✅ 内容详实\n");
                sb.append("   优势：详细的内容有助于用户理解和决策\n\n");
            }
            
        } else {
            sb.append("⚠️ 未能获取文章内容，无法进行深度分析\n");
            sb.append("   建议：检查文章链接是否有效，或手动补充内容摘要\n\n");
        }
        
        // === 数据对比 ===
        sb.append("【数据对比】\n\n");
        
        if (!benchmarkArticles.isEmpty()) {
            double avgReadCount = benchmarkArticles.stream()
                .mapToLong(a -> a.getReadCount7d() != null ? a.getReadCount7d() : 0)
                .average()
                .orElse(0);
            
            long currentRead = article.getReadCount7d() != null ? article.getReadCount7d() : 0;
            double gap = avgReadCount - currentRead;
            
            sb.append(String.format("📈 同品牌优秀文章平均阅读量: %.0f\n", avgReadCount));
            sb.append(String.format("📉 本文阅读量: %d\n", currentRead));
            if (avgReadCount > 0) {
                sb.append(String.format("📊 差距: %.0f (%.1f%%)\n\n", gap, (gap / avgReadCount) * 100));
            }
        }
        
        // === 具体优化行动 ===
        sb.append("【具体优化行动】\n\n");
        sb.append("1️⃣ 立即可做:\n");
        sb.append("   • 修改标题，参考上述高流量标题的特点\n");
        sb.append("   • 检查首图是否足够吸引人\n");
        sb.append("   • 添加相关话题标签\n");
        
        // 根据内容类型给出具体建议
        if (article.getContent() != null) {
            String content = article.getContent();
            if (content.contains("图片")) {
                sb.append("   • 优化图片质量和排版，确保图片清晰美观\n");
                sb.append("   • 在图片中添加文字说明或标注\n");
            } else {
                sb.append("   • 添加高质量配图，图文结合提升吸引力\n");
            }
            
            if (content.contains("视频")) {
                sb.append("   • 优化视频封面图，提高点击率\n");
            }
        }
        sb.append("\n");
        
        sb.append("2️⃣ 短期优化:\n");
        sb.append("   • 在最佳发布时间重新发布\n");
        sb.append("   • 增加互动引导语句\n");
        sb.append("   • 优化内容结构和段落\n");
        sb.append("   • 制作图片轮播或拼图效果\n");
        sb.append("   • 添加产品使用场景图\n\n");
        
        sb.append("3️⃣ 长期改进:\n");
        sb.append("   • 研究同品牌爆款文章的内容模式\n");
        sb.append("   • 建立标题公式库\n");
        sb.append("   • 定期分析用户反馈和评论\n");
        sb.append("   • 建立图片素材库，保持视觉风格一致\n");
        sb.append("   • 学习热门博主的图文搭配技巧\n");
        
        // 针对图文内容的专门建议
        sb.append("\n📸 图文内容优化建议:\n");
        sb.append("   • 图片数量：建议3-9张，保持奇数更有视觉冲击力\n");
        sb.append("   • 图片质量：确保高清、光线充足、构图美观\n");
        sb.append("   • 图片顺序：首图最重要，要有吸引力和代表性\n");
        sb.append("   • 文字搭配：每张图片配1-2句精炼描述\n");
        sb.append("   • 视觉风格：保持滤镜、色调、风格的统一性\n");
        sb.append("   • 产品展示：多角度展示产品细节和使用效果\n");
        
        return sb.toString();
    }
    
    private List<ArticleData> getBenchmarkArticles(ArticleData article) {
        // 获取同品牌的优秀文章，限制数量避免性能问题
        return articleDataRepository.findTop5ByBrandAndAnomalyStatusOrderByReadCount7dDesc(
            article.getBrand(), 
            "GOOD_ANOMALY"
        );
    }
    
    private boolean detectImages(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        
        // 多种图片检测方式
        return content.contains("📷 图片内容分析") ||
               content.contains("图片") ||
               content.contains("共发现") ||
               content.contains("商品图") ||
               content.contains("场景图") ||
               content.contains("细节图") ||
               content.contains("配图") ||
               content.contains("图文结合") ||
               content.contains("视觉效果");
    }
    
    private boolean detectVideos(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        
        return content.contains("🎥 视频内容") ||
               content.contains("视频") ||
               content.contains("YouTube") ||
               content.contains("B站") ||
               content.contains("bilibili") ||
               content.contains("多媒体");
    }
    
    private void analyzeImageQuality(String content, StringBuilder sb) {
        // 分析图片数量
        if (content.contains("共发现")) {
            try {
                String imageCountStr = content.replaceAll(".*共发现 (\\d+) 张.*", "$1");
                int imageCount = Integer.parseInt(imageCountStr);
                
                if (imageCount >= 5) {
                    sb.append("   优势：图片数量丰富（").append(imageCount).append("张），视觉冲击力强\n");
                } else if (imageCount >= 3) {
                    sb.append("   优势：图片数量适中（").append(imageCount).append("张），内容充实\n");
                } else if (imageCount >= 1) {
                    sb.append("   建议：可以增加更多图片（当前").append(imageCount).append("张），提升视觉吸引力\n");
                }
                
                // 分析图片类型
                if (content.contains("商品图")) {
                    sb.append("   包含商品展示图，有助于产品理解\n");
                }
                if (content.contains("场景图")) {
                    sb.append("   包含使用场景图，增强代入感\n");
                }
                if (content.contains("细节图")) {
                    sb.append("   包含产品细节图，提升信任度\n");
                }
                
            } catch (NumberFormatException e) {
                sb.append("   检测到图片内容，建议优化图片质量和数量\n");
            }
        } else {
            sb.append("   检测到图片相关内容，建议确保图片清晰美观\n");
        }
    }
    
    private String getLevelDescription(String level) {
        switch (level) {
            case "SEVERE": return "严重异常";
            case "MODERATE": return "中度异常";
            case "MILD": return "轻度异常";
            default: return "正常";
        }
    }
}