package com.fxt.backend;

import com.fxt.backend.enums.DataSource;
import com.fxt.backend.service.CrawlerFactory;
import com.fxt.backend.service.MultiPlatformDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class MultiPlatformSystemTest {

    @Autowired
    private CrawlerFactory crawlerFactory;

    @Autowired
    private MultiPlatformDataService multiPlatformDataService;

    @Test
    public void testDataSourceIdentification() {
        // 测试平台识别
        assertEquals(DataSource.DEWU, DataSource.fromSourceField("新媒体图文"));
        assertEquals(DataSource.DEWU, DataSource.fromSourceField("得物"));
        assertEquals(DataSource.XIAOHONGSHU, DataSource.fromSourceField("小红书"));
        assertEquals(DataSource.XIAOHONGSHU, DataSource.fromSourceField("xhs"));
        assertEquals(DataSource.UNKNOWN, DataSource.fromSourceField("未知平台"));
    }

    @Test
    public void testUrlIdentification() {
        // 测试URL识别
        assertEquals(DataSource.DEWU, DataSource.fromUrl("https://m.poizon.com/trend/123"));
        assertEquals(DataSource.XIAOHONGSHU, DataSource.fromUrl("https://www.xiaohongshu.com/explore/abc123"));
        assertEquals(DataSource.UNKNOWN, DataSource.fromUrl("https://unknown.com/test"));
    }

    @Test
    public void testCrawlerFactory() {
        // 测试爬虫工厂
        assertNotNull(crawlerFactory.createCrawler(DataSource.DEWU));
        assertNotNull(crawlerFactory.createCrawler(DataSource.XIAOHONGSHU));
        assertNull(crawlerFactory.createCrawler(DataSource.UNKNOWN));
        
        assertTrue(crawlerFactory.isSupported(DataSource.DEWU));
        assertTrue(crawlerFactory.isSupported(DataSource.XIAOHONGSHU));
        assertFalse(crawlerFactory.isSupported(DataSource.UNKNOWN));
    }

    @Test
    public void testPlatformStatistics() {
        // 测试平台统计
        var statistics = multiPlatformDataService.getPlatformStatistics();
        assertNotNull(statistics);
        assertTrue(statistics.containsKey("totalArticles"));
        assertTrue(statistics.containsKey("platformDistribution"));
        assertTrue(statistics.containsKey("supportedPlatforms"));
    }
}