package com.nerya.neryaallnaturals.dto;

import com.nerya.neryaallnaturals.entity.Blog;
import com.nerya.neryaallnaturals.entity.BlogStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlogResponse {

    private Long id;
    private String title;
    private String slug;
    private String excerpt;
    private String content;
    private BlogStatus status;
    private String authorName;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    /** Lowest-sortOrder active image, by convention the cover image; null if none uploaded yet. */
    private String coverImageUrl;
    private List<MediaAssetResponse> images;

    public static BlogResponse fromEntity(Blog blog, List<MediaAssetResponse> images) {
        String coverImageUrl = images.isEmpty() ? null : images.get(0).getPublicUrl();
        return BlogResponse.builder()
                .id(blog.getId())
                .title(blog.getTitle())
                .slug(blog.getSlug())
                .excerpt(blog.getExcerpt())
                .content(blog.getContent())
                .status(blog.getStatus())
                .authorName(blog.getAuthor().getFullName())
                .publishedAt(blog.getPublishedAt())
                .createdAt(blog.getCreatedAt())
                .coverImageUrl(coverImageUrl)
                .images(images)
                .build();
    }
}
