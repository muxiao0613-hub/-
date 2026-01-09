package com.fxt.backend.enums;

/**
 * 数据来源平台枚举
 * 支持智能识别Excel中的"素材来源"字段
 */
public enum DataSource {
    DEWU("dewu", "得物"),
    XIAOHONGSHU("xhs", "小红书"),
    UNKNOWN("unknown", "未知平台");

    private final String code;
    private final String displayName;

    DataSource(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 根据素材来源字段值智能识别平台
     * @param sourceValue Excel中的"素材来源"字段值
     * @return 识别的数据来源平台
     */
    public static DataSource fromSourceField(String sourceValue) {
        if (sourceValue == null || sourceValue.trim().isEmpty()) {
            return UNKNOWN;
        }

        String source = sourceValue.toLowerCase().trim();
        
        // 得物平台识别规则
        if (source.contains("新媒体图文") || 
            source.contains("得物") || 
            source.equals("dewu")) {
            return DEWU;
        }
        
        // 小红书平台识别规则
        if (source.contains("小红书") || 
            source.equals("xiaohongshu") || 
            source.equals("xhs")) {
            return XIAOHONGSHU;
        }
        
        return UNKNOWN;
    }

    /**
     * 根据链接URL智能识别平台
     * @param url 文章链接
     * @return 识别的数据来源平台
     */
    public static DataSource fromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return UNKNOWN;
        }

        String lowerUrl = url.toLowerCase();
        
        if (lowerUrl.contains("poizon.com") || lowerUrl.contains("dewu.com")) {
            return DEWU;
        }
        
        if (lowerUrl.contains("xiaohongshu.com") || lowerUrl.contains("xhslink.com")) {
            return XIAOHONGSHU;
        }
        
        return UNKNOWN;
    }
}