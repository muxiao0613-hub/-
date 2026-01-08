package com.fxt.backend.dto;

import java.util.List;

/**
 * 图片信息DTO
 */
public class ImageInfo {
    private String url;
    private String alt;
    private String title;
    private String description;
    private String localPath;
    private String type; // product, scene, detail, etc.
    private Integer width;
    private Integer height;
    private Long fileSize;
    private Boolean downloaded;
    
    public ImageInfo() {}
    
    public ImageInfo(String url, String alt, String title, String description) {
        this.url = url;
        this.alt = alt;
        this.title = title;
        this.description = description;
        this.downloaded = false;
    }
    
    // Getters and Setters
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public String getAlt() { return alt; }
    public void setAlt(String alt) { this.alt = alt; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    
    public Boolean getDownloaded() { return downloaded; }
    public void setDownloaded(Boolean downloaded) { this.downloaded = downloaded; }
}

/**
 * 图片集合信息
 */
class ImagesCollection {
    private List<ImageInfo> images;
    private List<VideoInfo> videos;
    private Integer totalImages;
    private Integer totalVideos;
    private String contentType; // 图文结合、纯图片、纯文字等
    
    public ImagesCollection() {}
    
    // Getters and Setters
    public List<ImageInfo> getImages() { return images; }
    public void setImages(List<ImageInfo> images) { this.images = images; }
    
    public List<VideoInfo> getVideos() { return videos; }
    public void setVideos(List<VideoInfo> videos) { this.videos = videos; }
    
    public Integer getTotalImages() { return totalImages; }
    public void setTotalImages(Integer totalImages) { this.totalImages = totalImages; }
    
    public Integer getTotalVideos() { return totalVideos; }
    public void setTotalVideos(Integer totalVideos) { this.totalVideos = totalVideos; }
    
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
}

/**
 * 视频信息DTO
 */
class VideoInfo {
    private String url;
    private String title;
    private String platform;
    private String thumbnail;
    private String localPath;
    private Boolean downloaded;
    
    public VideoInfo() {}
    
    public VideoInfo(String url, String title, String platform) {
        this.url = url;
        this.title = title;
        this.platform = platform;
        this.downloaded = false;
    }
    
    // Getters and Setters
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    
    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }
    
    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }
    
    public Boolean getDownloaded() { return downloaded; }
    public void setDownloaded(Boolean downloaded) { this.downloaded = downloaded; }
}