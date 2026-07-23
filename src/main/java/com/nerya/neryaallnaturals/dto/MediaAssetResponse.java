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
    private Integer sortOrder;
    private Long productId;
    /** Linked Category entity id (for category tile images), or null. */
    private Long categoryId;

    public static MediaAssetResponse fromEntity(MediaAsset asset) {
        return MediaAssetResponse.builder()
                .id(asset.getId())
                .category(asset.getCategory())
                .publicUrl(asset.getPublicUrl())
                .altText(asset.getAltText())
                .sortOrder(asset.getSortOrder())
                .productId(asset.getProduct() != null ? asset.getProduct().getId() : null)
                .categoryId(asset.getLinkedCategory() != null ? asset.getLinkedCategory().getId() : null)
                .build();
    }
}
