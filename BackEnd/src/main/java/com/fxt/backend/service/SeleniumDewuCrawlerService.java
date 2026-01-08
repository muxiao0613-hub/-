package com.fxt.backend.service;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 基于Selenium的得物图片爬取服务
 * 专门用于爬取得物平台的图片内容
 */
@Service
public class SeleniumDewuCrawlerService {
    
    private static final String CHROME_DRIVER_PATH = "chromedriver.exe"; // 可配置路径
    private static final int WAIT_TIMEOUT = 10; // 等待超时时间（秒）
    
    /**
     * 爬取得物文章的图片
     */
    public List<String> crawlDewuImages(String url) {
        List<String> imageUrls = new ArrayList<>();
        WebDriver driver = null;
        
        try {
            driver = createWebDriver();
            
            // 访问页面
            driver.get(url);
            
            // 等待页面加载
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_TIMEOUT));
            
            // 等待关键元素加载
            wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(By.className("image-container")),
                ExpectedConditions.presenceOfElementLocated(By.className("post-image")),
                ExpectedConditions.presenceOfElementLocated(By.className("content-image")),
                ExpectedConditions.presenceOfElementLocated(By.tagName("img"))
            ));
            
            // 滚动页面以加载懒加载图片
            scrollToLoadImages(driver);
            
            // 提取图片URL
            imageUrls = extractImageUrls(driver);
            
            System.out.println("成功爬取到 " + imageUrls.size() + " 张图片");
            
        } catch (Exception e) {
            System.err.println("爬取图片失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    System.err.println("关闭浏览器失败: " + e.getMessage());
                }
            }
        }
        
        return imageUrls;
    }
    
    /**
     * 创建WebDriver实例
     */
    private WebDriver createWebDriver() {
        // 设置Chrome选项
        ChromeOptions options = new ChromeOptions();
        
        // 无头模式（可选）
        // options.addArguments("--headless");
        
        // 其他优化选项
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        
        // 禁用图片加载以提高速度（如果只需要获取URL）
        // Map<String, Object> prefs = new HashMap<>();
        // prefs.put("profile.managed_default_content_settings.images", 2);
        // options.setExperimentalOption("prefs", prefs);
        
        WebDriver driver = new ChromeDriver(options);
        
        // 设置隐式等待
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        
        return driver;
    }
    
    /**
     * 滚动页面以触发懒加载
     */
    private void scrollToLoadImages(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_TIMEOUT));
            
            // 获取页面高度
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Long lastHeight = (Long) js.executeScript("return document.body.scrollHeight");
            
            int scrollAttempts = 0;
            int maxScrollAttempts = 5;
            
            while (scrollAttempts < maxScrollAttempts) {
                // 滚动到页面底部
                js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                
                // 等待新内容加载
                Thread.sleep(2000);
                
                // 获取新的页面高度
                Long newHeight = (Long) js.executeScript("return document.body.scrollHeight");
                
                // 如果页面高度没有变化，说明已经加载完毕
                if (newHeight.equals(lastHeight)) {
                    break;
                }
                
                lastHeight = newHeight;
                scrollAttempts++;
            }
            
            // 滚动回顶部
            js.executeScript("window.scrollTo(0, 0);");
            Thread.sleep(1000);
            
        } catch (Exception e) {
            System.err.println("滚动页面失败: " + e.getMessage());
        }
    }
    
    /**
     * 提取页面中的图片URL
     */
    private List<String> extractImageUrls(WebDriver driver) {
        Set<String> imageUrls = new HashSet<>();
        
        try {
            // 查找所有图片元素
            List<WebElement> images = driver.findElements(By.tagName("img"));
            List<WebElement> divImages = driver.findElements(By.cssSelector("div[style*='background-image']"));
            
            // 处理img标签
            for (WebElement img : images) {
                String src = img.getAttribute("src");
                String dataSrc = img.getAttribute("data-src");
                String dataOriginal = img.getAttribute("data-original");
                
                // 优先使用懒加载属性
                String imageUrl = null;
                if (isValidImageUrl(dataSrc)) {
                    imageUrl = dataSrc;
                } else if (isValidImageUrl(dataOriginal)) {
                    imageUrl = dataOriginal;
                } else if (isValidImageUrl(src)) {
                    imageUrl = src;
                }
                
                if (imageUrl != null && !isFilteredImage(imageUrl)) {
                    imageUrls.add(imageUrl);
                }
            }
            
            // 处理背景图片
            for (WebElement div : divImages) {
                String style = div.getAttribute("style");
                if (style != null && style.contains("background-image")) {
                    String imageUrl = extractUrlFromStyle(style);
                    if (imageUrl != null && !isFilteredImage(imageUrl)) {
                        imageUrls.add(imageUrl);
                    }
                }
            }
            
            // 查找特定的得物图片容器
            List<WebElement> dewuContainers = driver.findElements(By.cssSelector(
                ".image-container img, .post-content img, .article-content img, .content-wrapper img"
            ));
            
            for (WebElement container : dewuContainers) {
                String src = container.getAttribute("src");
                if (isValidImageUrl(src) && !isFilteredImage(src)) {
                    imageUrls.add(src);
                }
            }
            
        } catch (Exception e) {
            System.err.println("提取图片URL失败: " + e.getMessage());
        }
        
        return new ArrayList<>(imageUrls);
    }
    
    /**
     * 从CSS样式中提取图片URL
     */
    private String extractUrlFromStyle(String style) {
        try {
            int start = style.indexOf("url(") + 4;
            int end = style.indexOf(")", start);
            if (start > 3 && end > start) {
                String url = style.substring(start, end);
                // 移除引号
                url = url.replaceAll("[\"']", "");
                return url;
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return null;
    }
    
    /**
     * 验证是否为有效的图片URL
     */
    private boolean isValidImageUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        url = url.toLowerCase();
        
        // 检查是否为图片格式
        return url.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp)(\\?.*)?$") ||
               url.contains("image") ||
               url.contains("img") ||
               url.contains("photo");
    }
    
    /**
     * 过滤不需要的图片（如图标、广告等）
     */
    private boolean isFilteredImage(String url) {
        if (url == null) return true;
        
        String lowerUrl = url.toLowerCase();
        
        // 过滤条件
        return lowerUrl.contains("icon") ||
               lowerUrl.contains("logo") ||
               lowerUrl.contains("avatar") ||
               lowerUrl.contains("ad") ||
               lowerUrl.contains("banner") ||
               lowerUrl.contains("1x1") ||
               lowerUrl.contains("pixel") ||
               lowerUrl.contains("spacer") ||
               lowerUrl.matches(".*\\d+x\\d+.*") && 
               (lowerUrl.contains("16x16") || lowerUrl.contains("32x32") || lowerUrl.contains("64x64"));
    }
    
    /**
     * 获取页面标题
     */
    public String getPageTitle(String url) {
        WebDriver driver = null;
        try {
            driver = createWebDriver();
            driver.get(url);
            return driver.getTitle();
        } catch (Exception e) {
            System.err.println("获取页面标题失败: " + e.getMessage());
            return "";
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    System.err.println("关闭浏览器失败: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 获取页面文本内容
     */
    public String getPageContent(String url) {
        WebDriver driver = null;
        try {
            driver = createWebDriver();
            driver.get(url);
            
            // 等待页面加载
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_TIMEOUT));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
            
            // 提取主要内容
            List<WebElement> contentElements = driver.findElements(By.cssSelector(
                ".content, .post-content, .article-content, .main-content, p"
            ));
            
            StringBuilder content = new StringBuilder();
            for (WebElement element : contentElements) {
                String text = element.getText();
                if (text != null && text.trim().length() > 10) {
                    content.append(text).append("\n");
                }
            }
            
            return content.toString();
            
        } catch (Exception e) {
            System.err.println("获取页面内容失败: " + e.getMessage());
            return "";
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    System.err.println("关闭浏览器失败: " + e.getMessage());
                }
            }
        }
    }
}