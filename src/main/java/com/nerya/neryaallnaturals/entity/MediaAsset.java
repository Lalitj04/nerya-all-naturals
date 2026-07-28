package com.nerya.neryaallnaturals.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "media_assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAsset extends BaseEntity {

    /** Provider-side identifier (Cloudinary public id) — used to delete/manage the asset. */
    @NotBlank(message = "Storage key is required")
    @Size(max = 255)
    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @NotBlank(message = "File name is required")
    @Size(max = 255)
    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Size(max = 100)
    @Column(name = "mime_type")
    private String mimeType;

    @NotNull(message = "Category is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private MediaCategory category;

    @Size(max = 512)
    @Column(name = "folder_path")
    private String folderPath;

    @NotBlank(message = "Public URL is required")
    @Size(max = 1024)
    @Column(name = "public_url", nullable = false)
    private String publicUrl;

    @Size(max = 1024)
    @Column(name = "web_view_link")
    private String webViewLink;

    @Size(max = 1024)
    @Column(name = "thumbnail_link")
    private String thumbnailLink;

    @Size(max = 255)
    @Column(name = "alt_text")
    private String altText;

    // ---- optional display copy + CTA, mainly for HERO/BANNER slides (nullable for other categories) ----

    /** On-screen headline shown over/next to the image (distinct from {@link #altText}). */
    @Size(max = 255)
    @Column(name = "title")
    private String title;

    /** Supporting/sub-heading text under the title. */
    @Size(max = 500)
    @Column(name = "subtitle")
    private String subtitle;

    /** Click-through/redirection target when the image is clicked (relative path or absolute URL). */
    @Size(max = 1024)
    @Column(name = "link_url")
    private String linkUrl;

    /** Optional call-to-action button label paired with {@link #linkUrl}. */
    @Size(max = 100)
    @Column(name = "link_text")
    private String linkText;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    /**
     * Optional link to a Category, used for category tile/thumbnail images. Named
     * {@code linkedCategory} because {@link #category} is already taken by the
     * {@link MediaCategory} enum.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category linkedCategory;

    /** Optional link to a Blog, for cover/inline post images (T61/T65). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blog_id")
    private Blog linkedBlog;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    public enum MediaCategory {
        HERO,
        BANNER,
        PRODUCT,
        CATEGORY,
        GALLERY,
        BLOG
    }
}
