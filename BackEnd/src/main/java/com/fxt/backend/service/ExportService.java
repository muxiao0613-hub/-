package com.fxt.backend.service;

import com.fxt.backend.entity.ArticleData;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 导出服务 - 支持导出AI建议为Word文档
 */
@Service
public class ExportService {

    private static final Logger logger = LoggerFactory.getLogger(ExportService.class);

    private final String exportBasePath = "exports/";

    public ExportService() {
        try {
            Files.createDirectories(Paths.get(exportBasePath));
        } catch (IOException e) {
            logger.error("创建导出目录失败: {}", e.getMessage());
        }
    }

    /**
     * 导出AI建议为Word文档
     */
    public ExportResult exportAISuggestionsToWord(ArticleData article) {
        ExportResult result = new ExportResult();

        if (article == null) {
            result.setSuccess(false);
            result.setMessage("文章不存在");
            return result;
        }

        if (article.getAiSuggestions() == null || article.getAiSuggestions().isEmpty()) {
            result.setSuccess(false);
            result.setMessage("该文章暂无AI建议，请先生成AI建议");
            return result;
        }

        try {
            // 创建Word文档
            XWPFDocument document = new XWPFDocument();

            // 添加标题
            XWPFParagraph titlePara = document.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText("AI智能优化建议报告");
            titleRun.setBold(true);
            titleRun.setFontSize(22);
            titleRun.setFontFamily("微软雅黑");
            titleRun.addBreak();

            // 添加文章信息
            addSectionTitle(document, "文章基本信息");

            addInfoRow(document, "文章标题", article.getTitle());
            addInfoRow(document, "品牌", article.getBrand());
            addInfoRow(document, "发文类型", article.getPostType());
            addInfoRow(document, "内容类型", article.getContentType());
            addInfoRow(document, "发布时间", article.getPublishTime() != null ?
                article.getPublishTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "未知");

            // 添加数据表现
            addSectionTitle(document, "数据表现");

            addInfoRow(document, "7天阅读量", String.valueOf(article.getReadCount7d()));
            addInfoRow(document, "7天互动量", String.valueOf(article.getInteractionCount7d()));
            addInfoRow(document, "7天好物访问", String.valueOf(article.getProductVisit7d()));
            addInfoRow(document, "7天好物想要", String.valueOf(article.getProductWant7d()));

            // 计算关键指标
            if (article.getReadCount7d() != null && article.getReadCount7d() > 0) {
                double interactionRate = article.getInteractionCount7d() != null ?
                    (double) article.getInteractionCount7d() / article.getReadCount7d() * 100 : 0;
                double conversionRate = article.getProductVisit7d() != null ?
                    (double) article.getProductVisit7d() / article.getReadCount7d() * 100 : 0;

                addInfoRow(document, "互动率", String.format("%.2f%%", interactionRate));
                addInfoRow(document, "转化率", String.format("%.2f%%", conversionRate));
            }

            addInfoRow(document, "异常状态", getAnomalyStatusText(article.getAnomalyStatus()));

            // 添加AI建议内容
            addSectionTitle(document, "AI智能优化建议");

            XWPFParagraph aiPara = document.createParagraph();
            XWPFRun aiRun = aiPara.createRun();

            // 处理AI建议的换行
            String[] lines = article.getAiSuggestions().split("\n");
            for (int i = 0; i < lines.length; i++) {
                aiRun.setText(lines[i]);
                if (i < lines.length - 1) {
                    aiRun.addBreak();
                }
            }
            aiRun.setFontSize(11);
            aiRun.setFontFamily("微软雅黑");

            // 添加生成时间
            XWPFParagraph footerPara = document.createParagraph();
            footerPara.setAlignment(ParagraphAlignment.RIGHT);
            XWPFRun footerRun = footerPara.createRun();
            footerRun.setText("报告生成时间: " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            footerRun.setFontSize(9);
            footerRun.setColor("888888");

            // 保存文件
            String fileName = String.format("AI建议_%s_%s.docx",
                sanitizeFileName(article.getTitle()),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
            Path filePath = Paths.get(exportBasePath, fileName);

            try (FileOutputStream out = new FileOutputStream(filePath.toFile())) {
                document.write(out);
            }
            document.close();

            result.setSuccess(true);
            result.setFilePath(filePath.toString());
            result.setFileName(fileName);
            result.setMessage("导出成功");

            logger.info("AI建议导出成功: {}", filePath);

        } catch (Exception e) {
            logger.error("导出失败: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setMessage("导出失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 批量导出多篇文章的AI建议
     */
    public ExportResult exportMultipleAISuggestions(java.util.List<ArticleData> articles) {
        ExportResult result = new ExportResult();

        if (articles == null || articles.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("没有可导出的文章");
            return result;
        }

        try {
            XWPFDocument document = new XWPFDocument();

            // 添加封面标题
            XWPFParagraph coverTitle = document.createParagraph();
            coverTitle.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun coverRun = coverTitle.createRun();
            coverRun.setText("AI智能优化建议汇总报告");
            coverRun.setBold(true);
            coverRun.setFontSize(26);
            coverRun.setFontFamily("微软雅黑");
            coverRun.addBreak();
            coverRun.addBreak();

            XWPFParagraph infoPara = document.createParagraph();
            infoPara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun infoRun = infoPara.createRun();
            infoRun.setText(String.format("共包含 %d 篇文章的优化建议", articles.size()));
            infoRun.setFontSize(14);
            infoRun.addBreak();
            infoRun.setText("生成时间: " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            // 分页
            document.createParagraph().createRun().addBreak(BreakType.PAGE);

            // 遍历每篇文章
            int index = 1;
            for (ArticleData article : articles) {
                if (article.getAiSuggestions() == null || article.getAiSuggestions().isEmpty()) {
                    continue;
                }

                // 文章标题
                XWPFParagraph articleTitle = document.createParagraph();
                XWPFRun articleTitleRun = articleTitle.createRun();
                articleTitleRun.setText(String.format("第%d篇: %s", index++, article.getTitle()));
                articleTitleRun.setBold(true);
                articleTitleRun.setFontSize(14);
                articleTitleRun.setFontFamily("微软雅黑");

                // 基本信息
                addInfoRow(document, "品牌", article.getBrand());
                addInfoRow(document, "类型", article.getPostType());
                addInfoRow(document, "状态", getAnomalyStatusText(article.getAnomalyStatus()));

                // AI建议
                XWPFParagraph aiPara = document.createParagraph();
                XWPFRun aiRun = aiPara.createRun();
                String[] lines = article.getAiSuggestions().split("\n");
                for (int i = 0; i < lines.length; i++) {
                    aiRun.setText(lines[i]);
                    if (i < lines.length - 1) {
                        aiRun.addBreak();
                    }
                }
                aiRun.setFontSize(10);

                // 分隔线
                XWPFParagraph separator = document.createParagraph();
                separator.setBorderBottom(Borders.SINGLE);
                separator.createRun().addBreak();
            }

            // 保存文件
            String fileName = String.format("AI建议汇总_%s.docx",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
            Path filePath = Paths.get(exportBasePath, fileName);

            try (FileOutputStream out = new FileOutputStream(filePath.toFile())) {
                document.write(out);
            }
            document.close();

            result.setSuccess(true);
            result.setFilePath(filePath.toString());
            result.setFileName(fileName);
            result.setMessage(String.format("成功导出 %d 篇文章的AI建议", index - 1));

        } catch (Exception e) {
            logger.error("批量导出失败: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setMessage("导出失败: " + e.getMessage());
        }

        return result;
    }

    // 辅助方法
    private void addSectionTitle(XWPFDocument document, String title) {
        XWPFParagraph para = document.createParagraph();
        para.setSpacingBefore(200);
        XWPFRun run = para.createRun();
        run.setText("【" + title + "】");
        run.setBold(true);
        run.setFontSize(14);
        run.setFontFamily("微软雅黑");
        run.setColor("409EFF");
    }

    private void addInfoRow(XWPFDocument document, String label, String value) {
        XWPFParagraph para = document.createParagraph();
        XWPFRun labelRun = para.createRun();
        labelRun.setText(label + ": ");
        labelRun.setBold(true);
        labelRun.setFontSize(11);
        labelRun.setFontFamily("微软雅黑");

        XWPFRun valueRun = para.createRun();
        valueRun.setText(value != null ? value : "未知");
        valueRun.setFontSize(11);
        valueRun.setFontFamily("微软雅黑");
    }

    private String getAnomalyStatusText(String status) {
        if ("GOOD_ANOMALY".equals(status)) return "表现优秀 ✓";
        if ("BAD_ANOMALY".equals(status)) return "需要优化 ✗";
        return "正常";
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "unnamed";
        // 移除非法文件名字符，限制长度
        return name.replaceAll("[\\\\/:*?\"<>|]", "_")
                   .substring(0, Math.min(name.length(), 30));
    }

    // 结果类
    public static class ExportResult {
        private boolean success;
        private String message;
        private String filePath;
        private String fileName;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
    }
}