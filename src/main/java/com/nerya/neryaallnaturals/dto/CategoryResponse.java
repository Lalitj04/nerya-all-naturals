package com.nerya.neryaallnaturals.dto;

import com.nerya.neryaallnaturals.entity.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    /** Drive-backed category tile image (first active CATEGORY-linked media asset), if any. */
    private String thumbnailUrl;
    private Boolean isActive;
    private Long parentId;
    private String parentName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CategoryResponse fromEntity(Category category) {
        return fromEntity(category, null);
    }

    public static CategoryResponse fromEntity(Category category, String thumbnailUrl) {
        CategoryResponse.CategoryResponseBuilder builder = CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .thumbnailUrl(thumbnailUrl)
                .isActive(category.getIsActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt());

        if (category.getParentCategory() != null) {
            builder.parentId(category.getParentCategory().getId())
                   .parentName(category.getParentCategory().getName());
        }

        return builder.build();
    }
}

