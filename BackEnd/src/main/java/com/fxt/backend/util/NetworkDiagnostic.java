package com.fxt.backend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 网络连接诊断工具
 * 支持OpenAI和通义千问连接诊断
 */
public class NetworkDiagnostic {
    
    private static final Logger logger = LoggerFactory.getLogger(NetworkDiagnostic.class);
    
    /**
     * 诊断通义千问连接问题
     */
    public static void diagnoseQwenConnection() {
        logger.info("========== 通义千问网络连接诊断 ==========");
        
        // 1. 测试DNS解析
        testDNSResolution("dashscope.aliyuncs.com");
        
        // 2. 测试基本连接
        testBasicConnection("dashscope.aliyuncs.com", 443);
        
        // 3. 测试HTTP连接
        testHttpConnection("https://dashscope.aliyuncs.com");
        
        // 4. 检查代理设置
        checkProxySettings();
        
        logger.info("==========================================");
    }
    
    private static void testDNSResolution(String hostname) {
        try {
            logger.info("测试DNS解析: {}", hostname);
            InetAddress address = InetAddress.getByName(hostname);
            logger.info("✓ DNS解析成功: {} -> {}", hostname, address.getHostAddress());
        } catch (UnknownHostException e) {
            logger.error("✗ DNS解析失败: {}", e.getMessage());
            logger.error("建议: 检查DNS设置或网络连接");
        }
    }
    
    private static void testBasicConnection(String hostname, int port) {
        try {
            logger.info("测试TCP连接: {}:{}", hostname, port);
            
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(hostname, port), 10000);
                    return true;
                } catch (IOException e) {
                    return false;
                }
            });
            
            Boolean connected = future.get(15, TimeUnit.SECONDS);
            if (connected) {
                logger.info("✓ TCP连接成功");
            } else {
                logger.error("✗ TCP连接失败");
            }
            
        } catch (Exception e) {
            logger.error("✗ TCP连接测试异常: {}", e.getMessage());
            logger.error("可能原因: 防火墙阻止、网络不稳定或需要代理");
        }
    }
    
    private static void testHttpConnection(String url) {
        try {
            logger.info("测试HTTP连接: {}", url);
            
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "NetworkDiagnostic/1.0");
            
            int responseCode = connection.getResponseCode();
            logger.info("HTTP响应码: {}", responseCode);
            
            if (responseCode == 200 || responseCode == 401 || responseCode == 403) {
                logger.info("✓ HTTP连接成功（服务器可达）");
            } else {
                logger.warn("⚠️ HTTP连接异常，响应码: {}", responseCode);
            }
            
        } catch (IOException e) {
            logger.error("✗ HTTP连接失败: {}", e.getMessage());
            
            if (e instanceof ConnectException) {
                logger.error("连接被拒绝，可能需要代理或VPN");
            } else if (e instanceof SocketTimeoutException) {
                logger.error("连接超时，网络可能不稳定");
            }
        }
    }
    
    private static void checkProxySettings() {
        logger.info("检查系统代理设置:");
        
        String httpProxy = System.getProperty("http.proxyHost");
        String httpProxyPort = System.getProperty("http.proxyPort");
        String httpsProxy = System.getProperty("https.proxyHost");
        String httpsProxyPort = System.getProperty("https.proxyPort");
        
        if (httpProxy != null) {
            logger.info("HTTP代理: {}:{}", httpProxy, httpProxyPort);
        }
        if (httpsProxy != null) {
            logger.info("HTTPS代理: {}:{}", httpsProxy, httpsProxyPort);
        }
        
        if (httpProxy == null && httpsProxy == null) {
            logger.info("未检测到系统代理设置");
            logger.info("通义千问在国内可直接访问，通常不需要代理");
        }
    }
    
    /**
     * 提供通义千问网络问题解决建议
     */
    public static void provideQwenSolutions() {
        logger.info("========== 通义千问网络问题解决建议 ==========");
        logger.info("如果遇到连接问题，请尝试以下解决方案：");
        logger.info("");
        logger.info("1. 检查网络连接");
        logger.info("   - 确保网络正常");
        logger.info("   - 尝试访问阿里云官网");
        logger.info("");
        logger.info("2. 防火墙设置");
        logger.info("   - 检查防火墙是否阻止Java应用");
        logger.info("   - 允许端口443的出站连接");
        logger.info("");
        logger.info("3. API密钥检查");
        logger.info("   - 确认密钥格式正确: sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        logger.info("   - 检查密钥是否已激活和有余额");
        logger.info("");
        logger.info("4. 替代方案");
        logger.info("   - 暂时禁用AI功能: ai.api.enabled=false");
        logger.info("   - 联系阿里云技术支持");
        logger.info("=====================================");
    }
}