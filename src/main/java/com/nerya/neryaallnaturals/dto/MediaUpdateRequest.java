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
public class MediaUpdateRequest {

    private String altText;

    /** On-screen headline (send "" to clear). */
    private String title;

    /** Supporting sub-heading text (send "" to clear). */
    private String subtitle;

    /** Click-through/redirection target (send "" to clear). */
    private String linkUrl;

    /** CTA button label (send "" to clear). */
    private String linkText;

    private Integer sortOrder;

    private MediaAsset.MediaCategory category;

    private Boolean isActive;
}
