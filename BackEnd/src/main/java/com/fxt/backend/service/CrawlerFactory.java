package com.fxt.backend.service;

import com.fxt.backend.crawler.BaseCrawler;
import com.fxt.backend.crawler.DewuCrawler;
import com.fxt.backend.enums.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 爬虫工厂类
 * 根据数据来源创建对应的爬虫实例
 */
@Service
public class CrawlerFactory {
    
    @Autowired
    private DewuCrawler dewuCrawler;
    
    private final Map<DataSource, BaseCrawler> crawlers = new HashMap<>();
    
    /**
     * 初始化爬虫映射
     */
    public void initializeCrawlers() {
        crawlers.put(DataSource.DEWU, dewuCrawler);
        // 小红书爬虫已移除
    }
    
    /**
     * 根据数据来源创建对应的爬虫
     * @param source 数据来源
     * @return 爬虫实例，如果不支持则返回null
     */
    public BaseCrawler createCrawler(DataSource source) {
        if (crawlers.isEmpty()) {
            initializeCrawlers();
        }
        
        return crawlers.get(source);
    }
    
    /**
     * 获取平台名称
     * @param source 数据来源
     * @return 平台显示名称
     */
    public String getPlatformName(DataSource source) {
        return source != null ? source.getDisplayName() : "未知平台";
    }
    
    /**
     * 检查是否支持该平台
     * @param source 数据来源
     * @return 是否支持
     */
    public boolean isSupported(DataSource source) {
        if (crawlers.isEmpty()) {
            initializeCrawlers();
        }
        
        return crawlers.containsKey(source);
    }
    
    /**
     * 获取所有支持的平台
     * @return 支持的平台列表
     */
    public Map<DataSource, String> getSupportedPlatforms() {
        Map<DataSource, String> platforms = new HashMap<>();
        platforms.put(DataSource.DEWU, DataSource.DEWU.getDisplayName());
        // 小红书暂不支持爬取
        return platforms;
    }
}