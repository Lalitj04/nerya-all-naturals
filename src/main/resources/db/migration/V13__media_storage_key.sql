-- Switch the media backend from Google Drive to Cloudinary. The provider-specific id column is
-- renamed drive_file_id -> storage_key (now holds a Cloudinary public id) and widened to 255.
-- The existing UNIQUE index (uk_media_assets_drive_file_id) carries over to the renamed column.
-- Drive-only columns (web_view_link, thumbnail_link) are kept but simply left null by Cloudinary.

ALTER TABLE media_assets
    CHANGE COLUMN drive_file_id storage_key VARCHAR(255) NOT NULL;
