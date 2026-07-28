package com.nerya.neryaallnaturals.dto;

import com.nerya.neryaallnaturals.entity.MediaAsset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAssetResponse {

    private Long id;
    private MediaAsset.MediaCategory category;
    private String publicUrl;
    private String altText;
    /** On-screen headline (mainly for HERO/BANNER slides), or null. */
    private String title;
    /** Supporting sub-heading text, or null. */
    private String subtitle;
    /** Click-through/redirection target when the image is clicked, or null. */
    private String linkUrl;
    /** Optional CTA button label paired with {@link #linkUrl}, or null. */
    private String linkText;
    private Integer sortOrder;
    private Long productId;
    /** Linked Category entity id (for category tile images), or null. */
    private Long categoryId;
    /** Linked Blog entity id (for post cover/inline images), or null. */
    private Long blogId;

    public static MediaAssetResponse fromEntity(MediaAsset asset) {
        return MediaAssetResponse.builder()
                .id(asset.getId())
                .category(asset.getCategory())
                .publicUrl(asset.getPublicUrl())
                .altText(asset.getAltText())
                .title(asset.getTitle())
                .subtitle(asset.getSubtitle())
                .linkUrl(asset.getLinkUrl())
                .linkText(asset.getLinkText())
                .sortOrder(asset.getSortOrder())
                .productId(asset.getProduct() != null ? asset.getProduct().getId() : null)
                .categoryId(asset.getLinkedCategory() != null ? asset.getLinkedCategory().getId() : null)
                .blogId(asset.getLinkedBlog() != null ? asset.getLinkedBlog().getId() : null)
                .build();
    }
}
