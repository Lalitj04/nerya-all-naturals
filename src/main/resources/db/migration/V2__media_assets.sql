-- Google Drive-backed media assets: metadata/URLs live here, files live in Drive.
-- Column types verified the same way as V1 — booted against a disposable MySQL 8
-- with ddl-auto: create and captured the resulting DDL, so this migration and the
-- MediaAsset entity agree exactly under ddl-auto: validate.

CREATE TABLE media_assets (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    drive_file_id   VARCHAR(128)  NOT NULL,
    file_name       VARCHAR(255)  NOT NULL,
    mime_type       VARCHAR(100),
    category        ENUM('BANNER','CATEGORY','GALLERY','HERO','PRODUCT') NOT NULL,
    folder_path     VARCHAR(512),
    public_url      VARCHAR(1024) NOT NULL,
    web_view_link   VARCHAR(1024),
    thumbnail_link  VARCHAR(1024),
    alt_text        VARCHAR(255),
    sort_order      INT,
    product_id      BIGINT,
    is_active       BIT(1) DEFAULT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    CONSTRAINT uk_media_assets_drive_file_id UNIQUE (drive_file_id),
    KEY idx_media_assets_category (category, is_active, sort_order),
    KEY idx_media_assets_product (product_id),
    CONSTRAINT fk_media_assets_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
