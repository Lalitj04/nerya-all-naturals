-- Admin-authored blog posts (T61). Images ride the existing Google Drive media pipeline (P1):
-- media_assets gains a BLOG category and an optional blog_id link, mirroring how category tile
-- images already attach via linked_category_id.

CREATE TABLE blogs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(200)  NOT NULL,
    slug            VARCHAR(220)  NOT NULL,
    excerpt         VARCHAR(500),
    content         LONGTEXT,
    status          VARCHAR(20)   NOT NULL,
    author_id       BIGINT        NOT NULL,
    published_at    DATETIME(6),
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    CONSTRAINT uk_blogs_slug UNIQUE (slug),
    CONSTRAINT fk_blogs_author FOREIGN KEY (author_id) REFERENCES users (id),
    KEY idx_blogs_status_published_at (status, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE media_assets
    MODIFY COLUMN category ENUM('BANNER','BLOG','CATEGORY','GALLERY','HERO','PRODUCT') NOT NULL,
    ADD COLUMN blog_id BIGINT,
    ADD CONSTRAINT fk_media_assets_blog FOREIGN KEY (blog_id) REFERENCES blogs (id),
    ADD KEY idx_media_assets_blog (blog_id);
