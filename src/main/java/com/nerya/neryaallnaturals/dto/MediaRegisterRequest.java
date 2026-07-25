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

    @NotBlank(message = "Drive file ID is required")
    private String driveFileId;

    @NotNull(message = "Category is required")
    private MediaAsset.MediaCategory category;

    private String altText;

    private Integer sortOrder;

    private Long productId;

    /** Optional Category to link this asset to, for category tile/thumbnail images. */
    private Long categoryId;

    /** Optional Blog to link this asset to, for post cover/inline images. */
    private Long blogId;
}
