package com.fxt.backend.service;

import com.fxt.backend.entity.ArticleData;
import com.fxt.backend.enums.DataSource;
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
            
            // 检测平台类型：通过检查第二列是否为"标题"
            Row headerRow = sheet.getRow(0);
            boolean isDewu = headerRow != null &&
                            getCellValueAsString(headerRow.getCell(1)) != null &&
                            getCellValueAsString(headerRow.getCell(1)).equals("标题");
            
            // 跳过标题行
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                ArticleData article = new ArticleData();
                
                if (isDewu) {
                    // 得物格式解析（有标题列）
                    parseDewuRow(row, article);
                } else {
                    // 小红书格式解析（无标题列）
                    parseXiaohongshuRow(row, article);
                }
                
                // 初始状态设为正常
                article.setAnomalyStatus("NORMAL");
                articles.add(article);
            }
        }
        
        return articles;
    }
                
    
    private void parseDewuRow(Row row, ArticleData article) {
        article.setDataId(getCellValueAsString(row.getCell(0)));
        article.setTitle(getCellValueAsString(row.getCell(1)));
        article.setBrand(getCellValueAsString(row.getCell(2)));
        
        String publishTimeStr = getCellValueAsString(row.getCell(3));
        if (publishTimeStr != null && !publishTimeStr.isEmpty()) {
            try {
                article.setPublishTime(parseDateTime(publishTimeStr));
            } catch (Exception e) {
                article.setPublishTime(LocalDateTime.now());
            }
        }
        
        article.setArticleLink(getCellValueAsString(row.getCell(4)));
        article.setContentType(getCellValueAsString(row.getCell(5)));
        article.setPostType(getCellValueAsString(row.getCell(6)));
        article.setMaterialSource(getCellValueAsString(row.getCell(7)));
        article.setStyleInfo(getCellValueAsString(row.getCell(8)));
        article.setPlatform("得物");
        
        // 7天数据
        article.setReadCount7d(getCellValueAsLong(row.getCell(9)));
        article.setInteractionCount7d(getCellValueAsLong(row.getCell(10)));
        article.setProductVisit7d(getCellValueAsLong(row.getCell(11)));
        article.setProductWant7d(getCellValueAsLong(row.getCell(12)));
        
        // 14天数据
        article.setReadCount14d(getCellValueAsLong(row.getCell(13)));
        article.setInteractionCount14d(getCellValueAsLong(row.getCell(14)));
        article.setProductVisitCount(getCellValueAsLong(row.getCell(15)));
        article.setProductWant14d(getCellValueAsLong(row.getCell(16)));
        
        article.setShareCount7d(article.getProductWant7d());
        article.setShareCount14d(article.getProductWant14d());
    }
    
    private void parseXiaohongshuRow(Row row, ArticleData article) {
        article.setDataId(getCellValueAsString(row.getCell(0)));
        article.setTitle(null); // 小红书无标题列
        article.setBrand(getCellValueAsString(row.getCell(1)));
        
        String publishTimeStr = getCellValueAsString(row.getCell(2));
        if (publishTimeStr != null && !publishTimeStr.isEmpty()) {
            try {
                article.setPublishTime(parseDateTime(publishTimeStr));
            } catch (Exception e) {
                article.setPublishTime(LocalDateTime.now());
            }
        }
        
        article.setArticleLink(getCellValueAsString(row.getCell(3)));
        article.setContentType(getCellValueAsString(row.getCell(4)));
        article.setPostType(getCellValueAsString(row.getCell(5)));
        article.setMaterialSource(getCellValueAsString(row.getCell(6)));
        article.setStyleInfo(getCellValueAsString(row.getCell(7)));
        article.setPlatform("小红书");
        
        // 7天数据
        article.setReadCount7d(getCellValueAsLong(row.getCell(8)));
        article.setInteractionCount7d(getCellValueAsLong(row.getCell(9)));
        article.setProductVisit7d(getCellValueAsLong(row.getCell(10)));
        article.setProductWant7d(getCellValueAsLong(row.getCell(11)));
        
        // 14天数据
        article.setReadCount14d(getCellValueAsLong(row.getCell(12)));
        article.setInteractionCount14d(getCellValueAsLong(row.getCell(13)));
        article.setProductVisitCount(getCellValueAsLong(row.getCell(14)));
        article.setProductWant14d(getCellValueAsLong(row.getCell(15)));
        
        article.setShareCount7d(article.getProductWant7d());
        article.setShareCount14d(article.getProductWant14d());
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
        String[] patterns = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd",
            "yyyy/MM/dd"
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
        
        return LocalDateTime.now();
    }
}