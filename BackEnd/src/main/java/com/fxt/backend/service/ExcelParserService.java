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
                
                // 解析各列数据
                article.setDataId(getCellValueAsString(row.getCell(0)));
                article.setTitle(getCellValueAsString(row.getCell(1)));
                article.setBrand(getCellValueAsString(row.getCell(2)));
                
                // 解析发文时间
                String publishTimeStr = getCellValueAsString(row.getCell(3));
                if (publishTimeStr != null && !publishTimeStr.isEmpty()) {
                    try {
                        article.setPublishTime(parseDateTime(publishTimeStr));
                    } catch (Exception e) {
                        // 如果解析失败，设置为当前时间
                        article.setPublishTime(LocalDateTime.now());
                    }
                }
                
                article.setArticleLink(getCellValueAsString(row.getCell(4)));
                article.setContentType(getCellValueAsString(row.getCell(5)));
                article.setPostType(getCellValueAsString(row.getCell(6)));
                
                // 解析数值型数据
                article.setReadCount7d(getCellValueAsLong(row.getCell(7)));
                article.setReadCount14d(getCellValueAsLong(row.getCell(8)));
                article.setInteractionCount7d(getCellValueAsLong(row.getCell(9)));
                article.setInteractionCount14d(getCellValueAsLong(row.getCell(10)));
                article.setShareCount7d(getCellValueAsLong(row.getCell(11)));
                article.setShareCount14d(getCellValueAsLong(row.getCell(12)));
                article.setProductVisitCount(getCellValueAsLong(row.getCell(13)));
                
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