package com.fxt.backend.service;

import com.fxt.backend.entity.ArticleData;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ContentAnalysisService {
    
    private static final List<String> POSITIVE_KEYWORDS = Arrays.asList(
        "推荐", "必买", "好用", "值得", "优质", "热门", "爆款", "限时", "折扣", "特价",
        "新品", "首发", "独家", "精选", "口碑", "评测", "种草", "分享", "体验", "测评"
    );
    
    private static final List<String> ENGAGEMENT_KEYWORDS = Arrays.asList(
        "评论", "点赞", "分享", "收藏", "关注", "互动", "讨论", "交流", "反馈", "建议"
    );
    
    public void analyzeAndGenerateOptimizations(ArticleData article) {
        String content = article.getContent() != null ? article.getContent() : "";
        String title = article.getTitle() != null ? article.getTitle() : "";
        
        StringBuilder suggestions = new StringBuilder();
        
        // 基于异常状态生成不同的建议
        if ("GOOD_ANOMALY".equals(article.getAnomalyStatus())) {
            suggestions.append(generateGoodAnomalySuggestions(article, title, content));
        } else if ("BAD_ANOMALY".equals(article.getAnomalyStatus())) {
            suggestions.append(generateBadAnomalySuggestions(article, title, content));
        } else {
            suggestions.append(generateNormalSuggestions(article, title, content));
        }
        
        article.setOptimizationSuggestions(suggestions.toString());
    }
    
    private String generateGoodAnomalySuggestions(ArticleData article, String title, String content) {
        StringBuilder suggestions = new StringBuilder();
        suggestions.append("【优秀表现分析】\n");
        suggestions.append("该文章表现异常优秀，以下是成功要素分析和复制建议：\n\n");
        
        // 分析成功要素
        suggestions.append("✅ 成功要素分析：\n");
        if (containsKeywords(title, POSITIVE_KEYWORDS)) {
            suggestions.append("- 标题使用了吸引人的关键词，建议在后续文章中继续使用类似表达\n");
        }
        
        if (title.length() > 10 && title.length() < 30) {
            suggestions.append("- 标题长度适中（").append(title.length()).append("字），易于阅读和传播\n");
        }
        
        if (containsKeywords(content, ENGAGEMENT_KEYWORDS)) {
            suggestions.append("- 内容具有较强的互动性，成功引导用户参与\n");
        }
        
        suggestions.append("\n📈 复制成功经验：\n");
        suggestions.append("- 保持当前的内容风格和发布时间\n");
        suggestions.append("- 可以制作系列内容，延续热度\n");
        suggestions.append("- 考虑在其他平台同步发布，扩大影响力\n");
        suggestions.append("- 分析用户评论，了解受欢迎的具体原因\n");
        
        return suggestions.toString();
    }
    
    private String generateBadAnomalySuggestions(ArticleData article, String title, String content) {
        StringBuilder suggestions = new StringBuilder();
        suggestions.append("【改进建议】\n");
        suggestions.append("该文章表现不佳，以下是具体的优化建议：\n\n");
        
        // 标题优化
        suggestions.append("🎯 标题优化：\n");
        if (title.length() < 10) {
            suggestions.append("- 标题过短（").append(title.length()).append("字），建议扩展到15-25字\n");
        } else if (title.length() > 30) {
            suggestions.append("- 标题过长（").append(title.length()).append("字），建议精简到15-25字\n");
        }
        
        if (!containsKeywords(title, POSITIVE_KEYWORDS)) {
            suggestions.append("- 建议在标题中加入吸引性词汇：").append(String.join("、", POSITIVE_KEYWORDS.subList(0, 5))).append("\n");
        }
        
        // 内容优化
        suggestions.append("\n📝 内容优化：\n");
        if (content.length() < 200) {
            suggestions.append("- 内容过于简短，建议丰富内容，增加详细描述和使用体验\n");
        }
        
        if (!containsKeywords(content, ENGAGEMENT_KEYWORDS)) {
            suggestions.append("- 增加互动元素，如提问、征集意见等，提高用户参与度\n");
        }
        
        suggestions.append("- 添加更多视觉元素（图片、视频）提升吸引力\n");
        suggestions.append("- 结合热点话题或节日营销\n");
        
        // 发布策略
        suggestions.append("\n⏰ 发布策略：\n");
        suggestions.append("- 尝试在用户活跃时间发布（晚上7-10点，周末）\n");
        suggestions.append("- 考虑重新编辑后再次发布\n");
        suggestions.append("- 增加标签和关键词，提高搜索可见性\n");
        
        return suggestions.toString();
    }
    
    private String generateNormalSuggestions(ArticleData article, String title, String content) {
        StringBuilder suggestions = new StringBuilder();
        suggestions.append("【常规优化建议】\n");
        suggestions.append("该文章表现正常，以下是进一步提升的建议：\n\n");
        
        suggestions.append("📊 数据表现：\n");
        suggestions.append("- 7天阅读量：").append(article.getReadCount7d() != null ? article.getReadCount7d() : 0).append("\n");
        suggestions.append("- 7天互动量：").append(article.getInteractionCount7d() != null ? article.getInteractionCount7d() : 0).append("\n");
        suggestions.append("- 7天分享量：").append(article.getShareCount7d() != null ? article.getShareCount7d() : 0).append("\n");
        
        suggestions.append("\n🚀 提升建议：\n");
        suggestions.append("- 优化标题，增加数字或疑问句式\n");
        suggestions.append("- 在内容中增加用户痛点解决方案\n");
        suggestions.append("- 添加行动号召（CTA），引导用户互动\n");
        suggestions.append("- 考虑与其他博主合作，扩大传播范围\n");
        
        return suggestions.toString();
    }
    
    private boolean containsKeywords(String text, List<String> keywords) {
        if (text == null || text.isEmpty()) return false;
        
        return keywords.stream().anyMatch(text::contains);
    }
    
    public double calculateContentQualityScore(String title, String content) {
        double score = 0.0;
        
        // 标题评分 (30%)
        if (title != null) {
            if (title.length() >= 10 && title.length() <= 30) score += 10;
            if (containsKeywords(title, POSITIVE_KEYWORDS)) score += 10;
            if (title.matches(".*[0-9].*")) score += 5; // 包含数字
            if (title.contains("？") || title.contains("?")) score += 5; // 疑问句
        }
        
        // 内容评分 (70%)
        if (content != null) {
            if (content.length() > 200) score += 20;
            if (content.length() > 500) score += 10;
            if (containsKeywords(content, POSITIVE_KEYWORDS)) score += 15;
            if (containsKeywords(content, ENGAGEMENT_KEYWORDS)) score += 15;
            
            // 段落结构
            long paragraphs = content.chars().filter(ch -> ch == '\n').count();
            if (paragraphs > 2) score += 10;
        }
        
        return Math.min(score, 100.0); // 最高100分
    }
}