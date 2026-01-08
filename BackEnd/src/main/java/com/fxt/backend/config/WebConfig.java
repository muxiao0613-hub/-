package com.fxt.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置静态资源映射，让前端能访问下载的图片
        registry.addResourceHandler("/api/images/**")
                .addResourceLocations("file:../downloads/images/")
                .setCachePeriod(3600); // 缓存1小时
    }
}