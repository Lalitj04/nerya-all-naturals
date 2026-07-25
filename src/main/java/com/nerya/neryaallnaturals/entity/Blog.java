package com.nerya.neryaallnaturals.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/** An admin-authored blog post (T61/T62). Images attach via {@link MediaAsset#getLinkedBlog()}. */
@Entity
@Table(name = "blogs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Blog extends BaseEntity {

    @NotBlank(message = "Title is required")
    @Size(max = 200)
    @Column(name = "title", nullable = false)
    private String title;

    @NotBlank(message = "Slug is required")
    @Size(max = 220)
    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Size(max = 500)
    @Column(name = "excerpt")
    private String excerpt;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BlogStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /** Set once, on the first publish; never overwritten by later re-publishes. */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
