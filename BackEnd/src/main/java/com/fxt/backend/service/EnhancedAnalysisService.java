package com.fxt.backend.service;

import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.repository.ArticleDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EnhancedAnalysisService {
    
    @Autowired
    private ArticleDataRepository articleDataRepository;
    
    public String generateEnhancedAnalysis(ArticleData article) {
        List<ArticleData> allArticles = articleDataRepository.findAll();
        
        StringBuilder analysis = new StringBuilder();
        
        // === 核心指标分析 ===
        analysis.append("【数据分析报告】\n\n");
        analysis.append("📊 核心指标分析：\n");
        
        // 计算互动率
        double interactionRate = calculateInteractionRate(article);
        double avgInteractionRate = calculateAverageInteractionRate(allArticles);
        
        analysis.append(String.format("• 互动率：%.2f%%（平均：%.2f%%，%s）\n",
            interactionRate,
            avgInteractionRate,
            interactionRate > avgInteractionRate ? "高于平均" : "低于平均"
        ));
        
        // 计算转化率（好物访问/阅读）
        double conversionRate = calculateConversionRate(article);
        double avgConversionRate = calculateAverageConversionRate(allArticles);
        
        analysis.append(String.format("• 好物转化率：%.2f%%（平均：%.2f%%，%s）\n",
            conversionRate,
            avgConversionRate,
            conversionRate > avgConversionRate ? "高于平均" : "低于平均"
        ));
        
        // 计算购买意向率（好物想要/好物访问）
        double purchaseIntentRate = calculatePurchaseIntentRate(article);
        analysis.append(String.format("• 购买意向率：%.2f%%（%s）\n",
            purchaseIntentRate,
            purchaseIntentRate > 15 ? "优秀" : purchaseIntentRate > 8 ? "良好" : "需提升"
        ));
        
        // 计算7-14天增长率
        double readGrowthRate = calculateGrowthRate(
            article.getReadCount7d(), article.getReadCount14d()
        );
        analysis.append(String.format("• 7-14天增长率：%.1f%%（%s）\n\n",
            readGrowthRate,
            readGrowthRate > 50 ? "持续发酵" : readGrowthRate > 20 ? "正常增长" : "热度下降快"
        ));
        
        // === 发文类型分析 ===
        analysis.append("📸 发文类型分析：\n");
        analysis.append(String.format("• 当前类型：%s\n", article.getPostType()));
        
        // 分析发文类型表现
        Map<String, Double> typePerformance = analyzePostTypePerformance(allArticles);
        String bestType = typePerformance.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("");
        
        analysis.append(String.format("• 表现最好的类型：%s（平均阅读：%.0f）\n",
            bestType, typePerformance.getOrDefault(bestType, 0.0)));
        
        if (!article.getPostType().equals(bestType)) {
            double improvement = (typePerformance.getOrDefault(bestType, 0.0) - 
                                typePerformance.getOrDefault(article.getPostType(), 0.0)) /
                                typePerformance.getOrDefault(article.getPostType(), 1.0) * 100;
            analysis.append(String.format("• 建议：尝试「%s」类型，可能提升%.0f%%表现\n", bestType, improvement));
        }
        analysis.append("\n");
        
        // === 品牌对比分析 ===
        analysis.append("🏷️ 品牌表现对比：\n");
        List<ArticleData> sameBrandArticles = allArticles.stream()
            .filter(a -> article.getBrand().equals(a.getBrand()))
            .toList();
        
        if (sameBrandArticles.size() > 1) {
            double brandAvgRead = sameBrandArticles.stream()
                .mapToLong(a -> a.getReadCount7d() != null ? a.getReadCount7d() : 0)
                .average().orElse(0);
            
            analysis.append(String.format("• 品牌平均阅读量：%.0f\n", brandAvgRead));
            analysis.append(String.format("• 本文表现：%s品牌平均%.0f%%\n",
                article.getReadCount7d() > brandAvgRead ? "高于" : "低于",
                Math.abs((article.getReadCount7d() - brandAvgRead) / brandAvgRead * 100)
            ));
            
            // 找出同品牌最佳文章
            ArticleData bestInBrand = sameBrandArticles.stream()
                .max(Comparator.comparing(a -> a.getReadCount7d() != null ? a.getReadCount7d() : 0))
                .orElse(null);
            
            if (bestInBrand != null && !bestInBrand.getId().equals(article.getId())) {
                analysis.append(String.format("• 品牌最佳：「%s」- %s（阅读：%d）\n",
                    bestInBrand.getTitle().length() > 20 ? 
                        bestInBrand.getTitle().substring(0, 20) + "..." : bestInBrand.getTitle(),
                    bestInBrand.getPostType(),
                    bestInBrand.getReadCount7d()
                ));
            }
        }
        analysis.append("\n");
        
        // === 款式信息分析 ===
        if (article.getStyleInfo() != null && !article.getStyleInfo().isEmpty()) {
            analysis.append("👕 款式表现分析：\n");
            List<ArticleData> sameStyleArticles = allArticles.stream()
                .filter(a -> article.getStyleInfo().equals(a.getStyleInfo()))
                .toList();
            
            if (sameStyleArticles.size() > 1) {
                double styleAvgRead = sameStyleArticles.stream()
                    .mapToLong(a -> a.getReadCount7d() != null ? a.getReadCount7d() : 0)
                    .average().orElse(0);
                
                analysis.append(String.format("• 款式：%s\n", article.getStyleInfo()));
                analysis.append(String.format("• 该款式平均表现：%.0f阅读量\n", styleAvgRead));
                analysis.append(String.format("• 本文在该款式中排名：第%d/%d\n",
                    getRankInStyle(article, sameStyleArticles),
                    sameStyleArticles.size()
                ));
            }
            analysis.append("\n");
        }
        
        return analysis.toString();
    }
    
    public String generateActionableSuggestions(ArticleData article) {
        List<ArticleData> allArticles = articleDataRepository.findAll();
        StringBuilder suggestions = new StringBuilder();
        
        suggestions.append("【具体优化建议】\n\n");
        
        // 基于互动率分析
        double interactionRate = calculateInteractionRate(article);
        if (interactionRate < 5) {
            suggestions.append("🎯 提升互动的具体行动：\n");
            suggestions.append(String.format("   问题：当前互动率仅%.1f%%，低于平均水平\n", interactionRate));
            
            // 找到高互动率的同类文章
            List<ArticleData> highInteractionArticles = findHighInteractionArticles(allArticles, article.getPostType());
            if (!highInteractionArticles.isEmpty()) {
                suggestions.append("   参考：同类型高互动文章的特点：\n");
                for (int i = 0; i < Math.min(3, highInteractionArticles.size()); i++) {
                    ArticleData ref = highInteractionArticles.get(i);
                    double refRate = calculateInteractionRate(ref);
                    suggestions.append(String.format("   • %s（互动率：%.1f%%）\n",
                        ref.getPostType(), refRate));
                }
            }
            suggestions.append("\n");
        }
        
        // 基于转化率分析
        double conversionRate = calculateConversionRate(article);
        if (conversionRate < 1) {
            suggestions.append("🛒 提升转化的具体行动：\n");
            suggestions.append("   问题：好物访问转化率较低\n");
            suggestions.append("   建议：\n");
            suggestions.append("   1. 在内容中突出产品卖点和使用场景\n");
            suggestions.append("   2. 添加购买引导语（如「链接在主页」）\n");
            suggestions.append("   3. 考虑添加价格对比或优惠信息\n");
            suggestions.append("   4. 优化首图，确保产品清晰可见\n\n");
        }
        
        // 基于发布时间分析
        if (article.getPublishTime() != null) {
            Map<Integer, Double> hourlyPerformance = analyzeHourlyPerformance(allArticles);
            int currentHour = article.getPublishTime().getHour();
            int bestHour = hourlyPerformance.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(20);
            
            if (Math.abs(currentHour - bestHour) > 2) {
                double improvement = (hourlyPerformance.getOrDefault(bestHour, 0.0) - 
                                    hourlyPerformance.getOrDefault(currentHour, 0.0)) /
                                    hourlyPerformance.getOrDefault(currentHour, 1.0) * 100;
                suggestions.append("⏰ 发布时间优化：\n");
                suggestions.append(String.format("   当前发布：%d点\n", currentHour));
                suggestions.append(String.format("   建议时间：%d点左右（可提升%.0f%%表现）\n\n",
                    bestHour, improvement));
            }
        }
        
        // 基于内容类型分析
        suggestions.append("📝 内容优化建议：\n");
        if ("图文".equals(article.getContentType())) {
            suggestions.append("   ✅ 当前为图文内容，符合平台特性\n");
            suggestions.append("   建议：\n");
            suggestions.append("   1. 确保首图有足够吸引力\n");
            suggestions.append("   2. 图片数量控制在3-9张\n");
            suggestions.append("   3. 每张图片配简洁文字说明\n");
            suggestions.append("   4. 保持图片风格统一\n");
        }
        
        // 基于发文类型给出具体建议
        String postType = article.getPostType();
        suggestions.append(String.format("   针对「%s」类型的专门建议：\n", postType));
        
        switch (postType) {
            case "户外穿搭":
                suggestions.append("   • 突出搭配的实用性和场景适用性\n");
                suggestions.append("   • 展示不同角度的穿搭效果\n");
                suggestions.append("   • 添加搭配小贴士或心得分享\n");
                break;
            case "室内上脚":
                suggestions.append("   • 重点展示产品细节和质感\n");
                suggestions.append("   • 对比不同光线下的效果\n");
                suggestions.append("   • 分享上脚感受和舒适度\n");
                break;
            case "室内摆拍":
                suggestions.append("   • 注重构图和美感\n");
                suggestions.append("   • 突出产品设计亮点\n");
                suggestions.append("   • 可以加入生活化场景元素\n");
                break;
            case "户外摆拍":
                suggestions.append("   • 利用自然光线展示产品\n");
                suggestions.append("   • 结合环境突出产品特色\n");
                suggestions.append("   • 展示产品在真实场景中的表现\n");
                break;
        }
        
        return suggestions.toString();
    }
    
    // 辅助方法
    private double calculateInteractionRate(ArticleData article) {
        if (article.getReadCount7d() == null || article.getReadCount7d() == 0) return 0;
        if (article.getInteractionCount7d() == null) return 0;
        return (double) article.getInteractionCount7d() / article.getReadCount7d() * 100;
    }
    
    private double calculateAverageInteractionRate(List<ArticleData> articles) {
        return articles.stream()
            .filter(a -> a.getReadCount7d() != null && a.getReadCount7d() > 0 && a.getInteractionCount7d() != null)
            .mapToDouble(this::calculateInteractionRate)
            .average()
            .orElse(0);
    }
    
    private double calculateConversionRate(ArticleData article) {
        if (article.getReadCount7d() == null || article.getReadCount7d() == 0) return 0;
        if (article.getProductVisit7d() == null) return 0;
        return (double) article.getProductVisit7d() / article.getReadCount7d() * 100;
    }
    
    private double calculateAverageConversionRate(List<ArticleData> articles) {
        return articles.stream()
            .filter(a -> a.getReadCount7d() != null && a.getReadCount7d() > 0 && a.getProductVisit7d() != null)
            .mapToDouble(this::calculateConversionRate)
            .average()
            .orElse(0);
    }
    
    private double calculatePurchaseIntentRate(ArticleData article) {
        if (article.getProductVisit7d() == null || article.getProductVisit7d() == 0) return 0;
        if (article.getProductWant7d() == null) return 0;
        return (double) article.getProductWant7d() / article.getProductVisit7d() * 100;
    }
    
    private double calculateGrowthRate(Long value7d, Long value14d) {
        if (value7d == null || value7d == 0) return 0;
        if (value14d == null) return 0;
        return (double) (value14d - value7d) / value7d * 100;
    }
    
    private Map<String, Double> analyzePostTypePerformance(List<ArticleData> articles) {
        return articles.stream()
            .filter(a -> a.getPostType() != null && a.getReadCount7d() != null)
            .collect(Collectors.groupingBy(
                ArticleData::getPostType,
                Collectors.averagingLong(a -> a.getReadCount7d())
            ));
    }
    
    private List<ArticleData> findHighInteractionArticles(List<ArticleData> articles, String postType) {
        return articles.stream()
            .filter(a -> postType.equals(a.getPostType()))
            .filter(a -> calculateInteractionRate(a) > 8) // 互动率大于8%
            .sorted((a, b) -> Double.compare(calculateInteractionRate(b), calculateInteractionRate(a)))
            .limit(5)
            .toList();
    }
    
    private Map<Integer, Double> analyzeHourlyPerformance(List<ArticleData> articles) {
        return articles.stream()
            .filter(a -> a.getPublishTime() != null && a.getReadCount7d() != null)
            .collect(Collectors.groupingBy(
                a -> a.getPublishTime().getHour(),
                Collectors.averagingLong(a -> a.getReadCount7d())
            ));
    }
    
    private int getRankInStyle(ArticleData article, List<ArticleData> sameStyleArticles) {
        List<ArticleData> sorted = sameStyleArticles.stream()
            .sorted((a, b) -> Long.compare(
                b.getReadCount7d() != null ? b.getReadCount7d() : 0,
                a.getReadCount7d() != null ? a.getReadCount7d() : 0
            ))
            .toList();
        
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getId().equals(article.getId())) {
                return i + 1;
            }
        }
        return sorted.size();
    }
}