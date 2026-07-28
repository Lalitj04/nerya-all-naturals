package com.nerya.neryaallnaturals.dto;

import com.nerya.neryaallnaturals.entity.MediaAsset;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaRegisterRequest {

    /** URL of an image already hosted elsewhere (e.g. a Cloudinary/CDN URL) to record as-is. */
    @NotBlank(message = "Public URL is required")
    private String publicUrl;

    /**
     * Optional provider id/storage key for the image. If omitted, a synthetic unique key is
     * generated (such externally-registered assets aren't deletable through the provider).
     */
    private String storageKey;

    @NotNull(message = "Category is required")
    private MediaAsset.MediaCategory category;

    private String altText;

    /** Optional on-screen headline (mainly for HERO/BANNER slides). */
    private String title;

    /** Optional supporting sub-heading text. */
    private String subtitle;

    /** Optional click-through/redirection target when the image is clicked. */
    private String linkUrl;

    /** Optional CTA button label paired with {@code linkUrl}. */
    private String linkText;

    private Integer sortOrder;

    private Long productId;

    /** Optional Category to link this asset to, for category tile/thumbnail images. */
    private Long categoryId;

    /** Optional Blog to link this asset to, for post cover/inline images. */
    private Long blogId;
}
