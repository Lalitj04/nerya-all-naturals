-- Display copy + click-through for media assets, so HERO/BANNER (and any) images can carry an
-- on-screen headline, sub-heading, and a redirection URL/CTA — not just an SEO alt_text.
-- All nullable; images that don't need copy simply leave them empty.

ALTER TABLE media_assets
    ADD COLUMN title     VARCHAR(255),
    ADD COLUMN subtitle  VARCHAR(500),
    ADD COLUMN link_url  VARCHAR(1024),
    ADD COLUMN link_text VARCHAR(100);
