-- 添加AI建议和图片相关字段
ALTER TABLE article_data ADD COLUMN ai_suggestions TEXT;
ALTER TABLE article_data ADD COLUMN images_info TEXT;
ALTER TABLE article_data ADD COLUMN images_downloaded BOOLEAN DEFAULT FALSE;
ALTER TABLE article_data ADD COLUMN local_images_path TEXT;

-- 创建索引以提高查询性能
CREATE INDEX idx_article_data_ai_suggestions ON article_data(ai_suggestions(100));
CREATE INDEX idx_article_data_images_downloaded ON article_data(images_downloaded);
CREATE INDEX idx_article_data_crawl_status ON article_data(crawl_status);

-- 更新现有记录的默认值
UPDATE article_data SET images_downloaded = FALSE WHERE images_downloaded IS NULL;