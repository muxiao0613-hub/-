package com.fxt.backend.service;

import com.fxt.backend.entity.ArticleData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelParserService {

    public List<ArticleData> parseExcelFile(MultipartFile file) throws IOException {
        List<ArticleData> articles = new ArrayList<>();
        
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            // 跳过标题行
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                ArticleData article = new ArticleData();
                
                // 解析各列数据 - 根据实际Excel结构
                article.setDataId(getCellValueAsString(row.getCell(0)));           // data_id
                article.setTitle(getCellValueAsString(row.getCell(1)));            // 标题
                article.setBrand(getCellValueAsString(row.getCell(2)));            // 品牌
                
                // 解析发文时间
                String publishTimeStr = getCellValueAsString(row.getCell(3));      // 发文时间
                if (publishTimeStr != null && !publishTimeStr.isEmpty()) {
                    try {
                        article.setPublishTime(parseDateTime(publishTimeStr));
                    } catch (Exception e) {
                        // 如果解析失败，设置为当前时间
                        article.setPublishTime(LocalDateTime.now());
                    }
                }
                
                article.setArticleLink(getCellValueAsString(row.getCell(4)));      // 发文链接
                article.setContentType(getCellValueAsString(row.getCell(5)));     // 内容形式
                article.setPostType(getCellValueAsString(row.getCell(6)));        // 发文类型
                
                // 新增字段：素材来源和款式信息
                String materialSource = getCellValueAsString(row.getCell(7));     // 素材来源
                String styleInfo = getCellValueAsString(row.getCell(8));          // 款式信息
                article.setMaterialSource(materialSource);
                article.setStyleInfo(styleInfo);
                
                // 解析数值型数据 - 根据实际列位置
                article.setReadCount7d(getCellValueAsLong(row.getCell(9)));       // 7天阅读/播放
                article.setInteractionCount7d(getCellValueAsLong(row.getCell(10))); // 7天互动
                Long productVisit7d = getCellValueAsLong(row.getCell(11));        // 7天好物访问
                Long productWant7d = getCellValueAsLong(row.getCell(12));         // 7天好物想要
                
                article.setReadCount14d(getCellValueAsLong(row.getCell(13)));     // 14天阅读/播放
                article.setInteractionCount14d(getCellValueAsLong(row.getCell(14))); // 14天互动
                Long productVisit14d = getCellValueAsLong(row.getCell(15));      // 14天好物访问
                Long productWant14d = getCellValueAsLong(row.getCell(16));       // 14天好物想要
                
                // 设置产品访问量
                article.setProductVisit7d(productVisit7d);
                article.setProductVisitCount(productVisit14d);
                
                // 计算分享量（使用好物想要作为分享指标）
                article.setShareCount7d(productWant7d);
                article.setShareCount14d(productWant14d);
                
                // 初始状态设为正常
                article.setAnomalyStatus("NORMAL");
                
                articles.add(article);
            }
        }
        
        return articles;
    }
    
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }
    
    private Long getCellValueAsLong(Cell cell) {
        if (cell == null) return 0L;
        
        switch (cell.getCellType()) {
            case NUMERIC:
                return (long) cell.getNumericCellValue();
            case STRING:
                try {
                    return Long.parseLong(cell.getStringCellValue());
                } catch (NumberFormatException e) {
                    return 0L;
                }
            default:
                return 0L;
        }
    }
    
    private LocalDateTime parseDateTime(String dateTimeStr) {
        // 尝试多种日期格式
        String[] patterns = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "MM/dd/yyyy",
            "dd/MM/yyyy"
        };
        
        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                if (pattern.contains("HH:mm:ss")) {
                    return LocalDateTime.parse(dateTimeStr, formatter);
                } else {
                    return LocalDateTime.parse(dateTimeStr + " 00:00:00", 
                        DateTimeFormatter.ofPattern(pattern + " HH:mm:ss"));
                }
            } catch (Exception e) {
                // 继续尝试下一个格式
            }
        }
        
        // 如果所有格式都失败，返回当前时间
        return LocalDateTime.now();
    }
}