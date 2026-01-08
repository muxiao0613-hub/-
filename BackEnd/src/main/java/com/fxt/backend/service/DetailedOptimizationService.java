package com.fxt.backend.service;

import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.repository.ArticleDataRepository;
import com.fxt.backend.dto.AnomalyAnalysisReport;
import com.fxt.backend.dto.AnomalyAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 详细优化建议生成服务
 * 按照开题报告要求，生成针对性的、可操作的优化建议
 */
@Service
public class DetailedOptimizationService {
    
    @Autowired
    private ArticleDataRepository articleDataRepository;
    
    @Autowired
    private AIRecommendationService aiRecommendationService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public String generateDetailedOptimizations(ArticleData article) {
        List<ArticleData> allArticles = articleDataRepository.findAll();
        
        StringBuilder report = new StringBuilder();
        
        // ==================== 1. 异常原因分析 ====================
        report.append("═══════════════════════════════════════════\n");
        report.append("【一、异常原因分析】\n");
        report.append("═══════════════════════════════════════════\n\n");
        
        AnomalyAnalysisReport anomalyReport = parseAnomalyDetails(article.getAnomalyDetails());
        
        if (anomalyReport != null && !anomalyReport.getResults().isEmpty()) {
            for (AnomalyAnalysisResult result : anomalyReport.getResults()) {
                if (!"NORMAL".equals(result.getLevel())) {
                    report.append(String.format("📊 %s: %,.0f\n", result.getMetric(), result.getValue()));
                    report.append(String.format("   ├─ 平均值: %,.0f\n", result.getMean()));
                    report.append(String.format("   ├─ %s\n", result.getDeviation()));
                    report.append(String.format("   ├─ 处于所有文章的第 %.0f 百分位\n", result.getPercentile()));
                    report.append(String.format("   └─ 异常程度: %s\n\n", getLevelText(result.getLevel())));
                }
            }
        } else {
            report.append("暂无详细的异常分析数据，基于基础指标进行分析\n\n");
        }
        
        // ==================== 2. 标题分析 ====================
        report.append("═══════════════════════════════════════════\n");
        report.append("【二、标题分析】\n");
        report.append("═══════════════════════════════════════════\n\n");
        
        String title = article.getTitle();
        report.append(String.format("当前标题：「%s」（%d字）\n\n", title, title.length()));
        
        analyzeTitleIssues(title, report);
        
        // 找同款式高表现文章作为参考
        List<ArticleData> sameProductTopArticles = findTopArticlesByProduct(
            article.getStyleInfo(), allArticles, 3
        );
        
        if (!sameProductTopArticles.isEmpty()) {
            report.append("\n📖 同款式高流量文章标题参考：\n");
            int i = 1;
            for (ArticleData ref : sameProductTopArticles) {
                double refInteractionRate = calculateInteractionRate(ref);
                report.append(String.format("   %d. 「%s」\n", i++, ref.getTitle()));
                report.append(String.format("      阅读量: %,d | 互动率: %.1f%%\n",
                    ref.getReadCount7d() != null ? ref.getReadCount7d() : 0, refInteractionRate));
            }
        }
        
        // ==================== 3. 发文类型分析 ====================
        report.append("\n═══════════════════════════════════════════\n");
        report.append("【三、发文类型分析】\n");
        report.append("═══════════════════════════════════════════\n\n");
        
        String postType = article.getPostType();
        report.append(String.format("当前类型：%s\n\n", postType));
        
        // 各类型平均表现对比
        Map<String, PostTypeStats> typeStats = calculatePostTypeStats(allArticles);
        
        report.append("📊 各发文类型平均表现对比：\n");
        report.append("┌─────────────┬──────────┬──────────┬──────────┐\n");
        report.append("│   类型      │ 平均阅读  │ 平均互动  │ 互动率    │\n");
        report.append("├─────────────┼──────────┼──────────┼──────────┤\n");
        
        String bestType = null;
        double bestAvgRead = 0;
        
        for (Map.Entry<String, PostTypeStats> entry : typeStats.entrySet()) {
            PostTypeStats stats = entry.getValue();
            String marker = entry.getKey().equals(postType) ? "→ " : "  ";
            report.append(String.format("│%s%-10s │ %,8.0f │ %,8.0f │ %6.1f%%  │\n",
                marker, entry.getKey(), stats.avgRead, stats.avgInteraction, stats.interactionRate));
            
            if (stats.avgRead > bestAvgRead) {
                bestAvgRead = stats.avgRead;
                bestType = entry.getKey();
            }
        }
        report.append("└─────────────┴──────────┴──────────┴──────────┘\n");
        
        if (!postType.equals(bestType)) {
            double improvement = (bestAvgRead - typeStats.get(postType).avgRead) / typeStats.get(postType).avgRead * 100;
            report.append(String.format("\n💡 建议：「%s」类型平均表现最佳，可提升%.0f%%表现\n", bestType, improvement));
        }
        
        // ==================== 4. 发布时间分析 ====================
        report.append("\n═══════════════════════════════════════════\n");
        report.append("【四、发布时间分析】\n");
        report.append("═══════════════════════════════════════════\n\n");
        
        if (article.getPublishTime() != null) {
            int hour = article.getPublishTime().getHour();
            DayOfWeek dayOfWeek = article.getPublishTime().getDayOfWeek();
            
            report.append(String.format("发布时间：%s %d点\n\n",
                getDayOfWeekChinese(dayOfWeek), hour));
            
            // 分析最佳发布时间
            Map<Integer, Double> hourlyPerformance = calculateHourlyPerformance(allArticles);
            int bestHour = findBestHour(hourlyPerformance);
            
            if (Math.abs(hour - bestHour) > 2) {
                double improvement = (hourlyPerformance.get(bestHour) - hourlyPerformance.getOrDefault(hour, 0.0))
                    / hourlyPerformance.getOrDefault(hour, 1.0) * 100;
                report.append(String.format("💡 建议：数据显示 %d点 左右发布效果最佳\n", bestHour));
                report.append(String.format("   该时段平均阅读量比当前时段高 %.0f%%\n", improvement));
            } else {
                report.append("✅ 发布时间处于最佳时段\n");
            }
        }
        
        // ==================== 5. 转化漏斗分析 ====================
        report.append("\n═══════════════════════════════════════════\n");
        report.append("【五、转化漏斗分析】\n");
        report.append("═══════════════════════════════════════════\n\n");
        
        long read = article.getReadCount7d() != null ? article.getReadCount7d() : 0;
        long interaction = article.getInteractionCount7d() != null ? article.getInteractionCount7d() : 0;
        long visit = article.getProductVisit7d() != null ? article.getProductVisit7d() : 0;
        long want = article.getProductWant7d() != null ? article.getProductWant7d() : 0;
        
        report.append("阅读 → 互动 → 好物访问 → 好物想要\n");
        report.append(String.format(" %,d → %,d → %,d → %,d\n", read, interaction, visit, want));
        report.append(String.format("      %.1f%%    %.1f%%    %.1f%%\n",
            read > 0 ? (double)interaction/read*100 : 0,
            read > 0 ? (double)visit/read*100 : 0,
            visit > 0 ? (double)want/visit*100 : 0));
        
        // 与平均转化率对比
        FunnelStats avgFunnel = calculateAverageFunnel(allArticles);
        double currentInteractionRate = read > 0 ? (double)interaction/read*100 : 0;
        double currentVisitRate = read > 0 ? (double)visit/read*100 : 0;
        
        report.append("\n与平均水平对比：\n");
        if (currentInteractionRate < avgFunnel.interactionRate) {
            report.append(String.format("⚠️ 互动率（%.1f%%）低于平均（%.1f%%）\n",
                currentInteractionRate, avgFunnel.interactionRate));
            report.append("   建议：增加互动引导语，如「你们觉得呢？」「评论区告诉我」\n");
        }
        
        if (currentVisitRate < avgFunnel.visitRate) {
            report.append(String.format("⚠️ 好物访问率（%.1f%%）低于平均（%.1f%%）\n",
                currentVisitRate, avgFunnel.visitRate));
            report.append("   建议：强化产品展示，突出购买链接入口\n");
        }
        
        // ==================== 6. 具体行动建议 ====================
        report.append("\n═══════════════════════════════════════════\n");
        report.append("【六、具体行动建议】\n");
        report.append("═══════════════════════════════════════════\n\n");
        
        generateActionableSuggestions(article, report, typeStats, bestType);
        
        return report.toString();
    }
    
    /**
     * 生成AI智能建议（独立方法）
     */
    public String generateAISuggestions(ArticleData article) {
        List<ArticleData> allArticles = articleDataRepository.findAll();
        return aiRecommendationService.generateAIRecommendations(article, allArticles);
    }
    
    private void analyzeTitleIssues(String title, StringBuilder report) {
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        
        // 长度分析
        if (title.length() < 10) {
            issues.add("标题过短（仅" + title.length() + "字）");
            suggestions.add("扩展至15-25字，补充具体场景或痛点");
        } else if (title.length() > 30) {
            issues.add("标题过长（" + title.length() + "字）");
            suggestions.add("精简至15-25字，突出核心卖点");
        }
        
        // 情感词检测
        String[] emotionalWords = {"绝了", "必买", "好用", "值得", "优质", "热门", "爆款", "限时", "折扣", "特价"};
        boolean hasEmotional = Arrays.stream(emotionalWords).anyMatch(title::contains);
        if (!hasEmotional) {
            issues.add("缺少情感词汇");
            suggestions.add("添加「绝了」「必买」「值得」等情感词");
        }
        
        // 数字检测
        boolean hasNumber = title.matches(".*\\d+.*");
        if (!hasNumber) {
            issues.add("缺少具体数字");
            suggestions.add("添加「3个技巧」「7天见效」等具体数字");
        }
        
        // 疑问句检测
        boolean hasQuestion = title.contains("？") || title.contains("?");
        if (!hasQuestion && title.length() > 15) {
            suggestions.add("考虑使用疑问句式增加互动性");
        }
        
        if (issues.isEmpty()) {
            report.append("✅ 标题分析良好\n");
        } else {
            report.append("⚠️ 发现以下问题：\n");
            for (int i = 0; i < issues.size(); i++) {
                report.append(String.format("   %d. %s\n", i + 1, issues.get(i)));
                report.append(String.format("      建议：%s\n", suggestions.get(i)));
            }
        }
    }
    
    private void generateActionableSuggestions(ArticleData article, StringBuilder report, 
                                             Map<String, PostTypeStats> typeStats, String bestType) {
        
        if ("BAD_ANOMALY".equals(article.getAnomalyStatus())) {
            report.append("🔴 该内容表现较差，建议采取以下行动：\n\n");
            
            report.append("【立即可做】\n");
            report.append("□ 1. 重新编辑标题，参考上述高流量标题特点\n");
            report.append("□ 2. 检查首图质量，确保清晰吸引人\n");
            report.append("□ 3. 在最佳时段重新发布\n");
            report.append("□ 4. 添加相关话题标签提高曝光\n\n");
            
            report.append("【内容优化】\n");
            report.append("□ 5. 增加产品使用场景描述\n");
            report.append("□ 6. 添加与用户互动的问句\n");
            report.append("□ 7. 优化图片排版和质量\n");
            if (!article.getPostType().equals(bestType)) {
                report.append(String.format("□ 8. 考虑尝试「%s」类型内容\n", bestType));
            }
            
        } else if ("GOOD_ANOMALY".equals(article.getAnomalyStatus())) {
            report.append("🟢 该内容表现优秀，建议：\n\n");
            
            report.append("【复制成功经验】\n");
            report.append("□ 1. 记录该内容的成功要素\n");
            report.append("□ 2. 使用相似的标题结构\n");
            report.append("□ 3. 保持相同的发布时间\n");
            report.append("□ 4. 制作同款式的系列内容\n");
            report.append("□ 5. 分析用户评论找出受欢迎的点\n");
            
        } else {
            report.append("🟡 该内容表现正常，可进一步优化：\n\n");
            
            report.append("【提升建议】\n");
            report.append("□ 1. 参考同类型高表现内容的特点\n");
            report.append("□ 2. 优化标题增加吸引力\n");
            report.append("□ 3. 增强内容的互动性\n");
            report.append("□ 4. 考虑在更佳时段发布\n");
        }
        
        // 针对发文类型的专门建议
        report.append("\n【针对「").append(article.getPostType()).append("」类型的专门建议】\n");
        
        switch (article.getPostType()) {
            case "户外穿搭":
                report.append("• 突出搭配的实用性和场景适用性\n");
                report.append("• 展示不同角度的穿搭效果\n");
                report.append("• 添加搭配小贴士或心得分享\n");
                report.append("• 结合天气、场合等实际因素\n");
                break;
            case "室内上脚":
                report.append("• 重点展示产品细节和质感\n");
                report.append("• 对比不同光线下的效果\n");
                report.append("• 分享上脚感受和舒适度\n");
                report.append("• 突出产品的设计亮点\n");
                break;
            case "室内摆拍":
                report.append("• 注重构图和美感\n");
                report.append("• 突出产品设计亮点\n");
                report.append("• 可以加入生活化场景元素\n");
                report.append("• 利用道具增强视觉效果\n");
                break;
            case "户外摆拍":
                report.append("• 利用自然光线展示产品\n");
                report.append("• 结合环境突出产品特色\n");
                report.append("• 展示产品在真实场景中的表现\n");
                report.append("• 注意背景与产品的搭配\n");
                break;
            default:
                report.append("• 保持内容的专业性和吸引力\n");
                report.append("• 注重用户体验和互动\n");
                break;
        }
    }
    
    // 辅助方法
    private AnomalyAnalysisReport parseAnomalyDetails(String anomalyDetails) {
        if (anomalyDetails == null || anomalyDetails.isEmpty()) {
            return null;
        }
        
        try {
            return objectMapper.readValue(anomalyDetails, AnomalyAnalysisReport.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    private String getLevelText(String level) {
        switch (level) {
            case "SEVERE": return "严重异常";
            case "MODERATE": return "中度异常";
            case "MILD": return "轻度异常";
            default: return "正常";
        }
    }
    
    private List<ArticleData> findTopArticlesByProduct(String styleInfo, List<ArticleData> allArticles, int limit) {
        if (styleInfo == null) return new ArrayList<>();
        
        return allArticles.stream()
            .filter(a -> styleInfo.equals(a.getStyleInfo()))
            .filter(a -> a.getReadCount7d() != null && a.getReadCount7d() > 0)
            .sorted((a, b) -> Long.compare(b.getReadCount7d(), a.getReadCount7d()))
            .limit(limit)
            .toList();
    }
    
    private double calculateInteractionRate(ArticleData article) {
        if (article.getReadCount7d() == null || article.getReadCount7d() == 0) return 0;
        if (article.getInteractionCount7d() == null) return 0;
        return (double) article.getInteractionCount7d() / article.getReadCount7d() * 100;
    }
    
    private Map<String, PostTypeStats> calculatePostTypeStats(List<ArticleData> articles) {
        return articles.stream()
            .filter(a -> a.getPostType() != null && a.getReadCount7d() != null && a.getReadCount7d() > 0)
            .collect(Collectors.groupingBy(
                ArticleData::getPostType,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    list -> {
                        double avgRead = list.stream().mapToLong(a -> a.getReadCount7d()).average().orElse(0);
                        double avgInteraction = list.stream()
                            .filter(a -> a.getInteractionCount7d() != null)
                            .mapToLong(a -> a.getInteractionCount7d()).average().orElse(0);
                        double interactionRate = avgRead > 0 ? avgInteraction / avgRead * 100 : 0;
                        return new PostTypeStats(avgRead, avgInteraction, interactionRate);
                    }
                )
            ));
    }
    
    private String getDayOfWeekChinese(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY: return "周一";
            case TUESDAY: return "周二";
            case WEDNESDAY: return "周三";
            case THURSDAY: return "周四";
            case FRIDAY: return "周五";
            case SATURDAY: return "周六";
            case SUNDAY: return "周日";
            default: return "未知";
        }
    }
    
    private Map<Integer, Double> calculateHourlyPerformance(List<ArticleData> articles) {
        return articles.stream()
            .filter(a -> a.getPublishTime() != null && a.getReadCount7d() != null)
            .collect(Collectors.groupingBy(
                a -> a.getPublishTime().getHour(),
                Collectors.averagingLong(a -> a.getReadCount7d())
            ));
    }
    
    private int findBestHour(Map<Integer, Double> hourlyPerformance) {
        return hourlyPerformance.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(20);
    }
    
    private FunnelStats calculateAverageFunnel(List<ArticleData> articles) {
        List<ArticleData> validArticles = articles.stream()
            .filter(a -> a.getReadCount7d() != null && a.getReadCount7d() > 0)
            .toList();
        
        double avgInteractionRate = validArticles.stream()
            .filter(a -> a.getInteractionCount7d() != null)
            .mapToDouble(a -> (double) a.getInteractionCount7d() / a.getReadCount7d() * 100)
            .average().orElse(0);
        
        double avgVisitRate = validArticles.stream()
            .filter(a -> a.getProductVisit7d() != null)
            .mapToDouble(a -> (double) a.getProductVisit7d() / a.getReadCount7d() * 100)
            .average().orElse(0);
        
        return new FunnelStats(avgInteractionRate, avgVisitRate);
    }
    
    // 内部类
    private static class PostTypeStats {
        final double avgRead;
        final double avgInteraction;
        final double interactionRate;
        
        PostTypeStats(double avgRead, double avgInteraction, double interactionRate) {
            this.avgRead = avgRead;
            this.avgInteraction = avgInteraction;
            this.interactionRate = interactionRate;
        }
    }
    
    private static class FunnelStats {
        final double interactionRate;
        final double visitRate;
        
        FunnelStats(double interactionRate, double visitRate) {
            this.interactionRate = interactionRate;
            this.visitRate = visitRate;
        }
    }
}