package com.fxt.backend.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TitleAnalysis {
    private int length;
    private boolean hasEmotionalWords;
    private boolean hasSpecificNumber;
    private boolean hasQuestion;
    private boolean hasCallToAction;
    private int keywordCount;
    private double qualityScore;

    public static TitleAnalysis analyze(String title) {
        TitleAnalysis analysis = new TitleAnalysis();
        
        if (title == null || title.isEmpty()) {
            return analysis;
        }
        
        analysis.setLength(title.length());
        
        // 检查情感词
        String[] emotionalWords = {"绝了", "救命", "太棒", "超绝", "爱了", "惊艳", "yyds", "封神", "天花板", 
                                  "必买", "推荐", "种草", "好用", "值得", "优质", "热门", "爆款"};
        for (String word : emotionalWords) {
            if (title.contains(word)) {
                analysis.setHasEmotionalWords(true);
                analysis.keywordCount++;
            }
        }
        
        // 检查数字
        analysis.setHasSpecificNumber(title.matches(".*\\d+.*"));
        
        // 检查疑问句
        analysis.setHasQuestion(title.contains("？") || title.contains("?") ||
                               title.contains("怎么") || title.contains("如何") ||
                               title.contains("为什么") || title.contains("哪个"));
        
        // 检查行动号召
        String[] ctaWords = {"必看", "收藏", "推荐", "必买", "速看", "快来", "赶紧", "立即"};
        for (String word : ctaWords) {
            if (title.contains(word)) {
                analysis.setHasCallToAction(true);
                break;
            }
        }
        
        // 计算质量分数
        double score = 0;
        if (analysis.getLength() >= 10 && analysis.getLength() <= 25) score += 25;
        else if (analysis.getLength() > 25 && analysis.getLength() <= 35) score += 15;
        
        if (analysis.isHasEmotionalWords()) score += 25;
        if (analysis.isHasSpecificNumber()) score += 20;
        if (analysis.isHasQuestion()) score += 15;
        if (analysis.isHasCallToAction()) score += 15;
        
        analysis.setQualityScore(Math.min(100, score));
        
        return analysis;
    }

    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    // Getters and Setters
    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }
    
    public boolean isHasEmotionalWords() { return hasEmotionalWords; }
    public void setHasEmotionalWords(boolean hasEmotionalWords) { this.hasEmotionalWords = hasEmotionalWords; }
    
    public boolean isHasSpecificNumber() { return hasSpecificNumber; }
    public void setHasSpecificNumber(boolean hasSpecificNumber) { this.hasSpecificNumber = hasSpecificNumber; }
    
    public boolean isHasQuestion() { return hasQuestion; }
    public void setHasQuestion(boolean hasQuestion) { this.hasQuestion = hasQuestion; }
    
    public boolean isHasCallToAction() { return hasCallToAction; }
    public void setHasCallToAction(boolean hasCallToAction) { this.hasCallToAction = hasCallToAction; }
    
    public int getKeywordCount() { return keywordCount; }
    public void setKeywordCount(int keywordCount) { this.keywordCount = keywordCount; }
    
    public double getQualityScore() { return qualityScore; }
    public void setQualityScore(double qualityScore) { this.qualityScore = qualityScore; }
}