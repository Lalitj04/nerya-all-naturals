-- Optional link from a media asset to a Category entity, for category tile/thumbnail
-- images (T38). Nullable so existing product/hero/banner assets are unaffected.

ALTER TABLE media_assets
    ADD COLUMN category_id BIGINT NULL,
    ADD CONSTRAINT fk_media_assets_category FOREIGN KEY (category_id) REFERENCES categories (id);
