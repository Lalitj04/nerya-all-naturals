package com.nerya.neryaallnaturals.service;

import com.nerya.neryaallnaturals.dto.BlogRequest;
import com.nerya.neryaallnaturals.dto.BlogResponse;
import com.nerya.neryaallnaturals.dto.MediaAssetResponse;
import com.nerya.neryaallnaturals.dto.PagedResponse;
import com.nerya.neryaallnaturals.entity.Blog;
import com.nerya.neryaallnaturals.entity.BlogStatus;
import com.nerya.neryaallnaturals.entity.User;
import com.nerya.neryaallnaturals.exception.ConflictException;
import com.nerya.neryaallnaturals.exception.ResourceNotFoundException;
import com.nerya.neryaallnaturals.repository.BlogRepository;
import com.nerya.neryaallnaturals.repository.MediaAssetRepository;
import com.nerya.neryaallnaturals.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin-authored blog posts (P9). Images ride the existing Google Drive media pipeline
 * ({@link MediaService}) via {@code MediaAsset.linkedBlog} — this service only owns the post
 * content and its draft/publish lifecycle.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlogService {

    private final BlogRepository blogRepository;
    private final UserRepository userRepository;
    private final MediaAssetRepository mediaAssetRepository;

    @Transactional
    public BlogResponse createDraft(String username, BlogRequest request) {
        User author = getUser(username);

        Blog blog = Blog.builder()
                .title(request.getTitle())
                .slug(generateUniqueSlug(request.getTitle()))
                .excerpt(request.getExcerpt())
                .content(request.getContent())
                .status(BlogStatus.DRAFT)
                .author(author)
                .build();

        Blog saved = blogRepository.save(blog);
        log.info("Admin '{}' created blog draft {} ('{}')", username, saved.getId(), saved.getTitle());
        return toResponse(saved);
    }

    /** Edits content; the slug is regenerated from the title only while still a DRAFT (T63). */
    @Transactional
    public BlogResponse updateBlog(Long id, BlogRequest request) {
        Blog blog = findOrThrow(id);

        if (blog.getStatus() == BlogStatus.DRAFT && !blog.getTitle().equals(request.getTitle())) {
            blog.setSlug(generateUniqueSlug(request.getTitle(), blog.getId()));
        }
        blog.setTitle(request.getTitle());
        blog.setExcerpt(request.getExcerpt());
        blog.setContent(request.getContent());

        Blog saved = blogRepository.save(blog);
        log.info("Updated blog {}", id);
        return toResponse(saved);
    }

    @Transactional
    public BlogResponse publish(Long id) {
        Blog blog = findOrThrow(id);
        if (blog.getContent() == null || blog.getContent().isBlank()) {
            throw new ConflictException("Cannot publish a post with no content");
        }
        blog.setStatus(BlogStatus.PUBLISHED);
        if (blog.getPublishedAt() == null) {
            blog.setPublishedAt(LocalDateTime.now());
        }
        Blog saved = blogRepository.save(blog);
        log.info("Published blog {}", id);
        return toResponse(saved);
    }

    @Transactional
    public BlogResponse unpublish(Long id) {
        Blog blog = findOrThrow(id);
        blog.setStatus(BlogStatus.DRAFT);
        Blog saved = blogRepository.save(blog);
        log.info("Unpublished blog {}", id);
        return toResponse(saved);
    }

    @Transactional
    public void deleteBlog(Long id) {
        Blog blog = findOrThrow(id);
        blogRepository.delete(blog);
        log.info("Deleted blog {}", id);
    }

    // ---- admin reads (see drafts too) ----

    @Transactional(readOnly = true)
    public PagedResponse<BlogResponse> getAllForAdmin(Pageable pageable) {
        Page<Blog> page = blogRepository.findAll(normalize(pageable));
        return toPagedResponse(page);
    }

    @Transactional(readOnly = true)
    public BlogResponse getForAdmin(Long id) {
        return toResponse(findOrThrow(id));
    }

    // ---- public reads (published only) ----

    @Transactional(readOnly = true)
    public PagedResponse<BlogResponse> getPublished(Pageable pageable) {
        Page<Blog> page = blogRepository.findByStatus(BlogStatus.PUBLISHED, normalizePublic(pageable));
        return toPagedResponse(page);
    }

    @Transactional(readOnly = true)
    public BlogResponse getPublishedBySlug(String slug) {
        Blog blog = blogRepository.findBySlug(slug)
                .filter(b -> b.getStatus() == BlogStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Blog post not found"));
        return toResponse(blog);
    }

    // ---- helpers ----

    private BlogResponse toResponse(Blog blog) {
        List<MediaAssetResponse> images = mediaAssetRepository
                .findByLinkedBlogIdAndIsActiveTrueOrderBySortOrderAsc(blog.getId()).stream()
                .map(MediaAssetResponse::fromEntity)
                .collect(Collectors.toList());
        return BlogResponse.fromEntity(blog, images);
    }

    private PagedResponse<BlogResponse> toPagedResponse(Page<Blog> page) {
        List<BlogResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return PagedResponse.of(content, page);
    }

    private Blog findOrThrow(Long id) {
        return blogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blog post not found"));
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String generateUniqueSlug(String title) {
        return generateUniqueSlug(title, null);
    }

    /** Slugify the title, appending -2, -3, … on collision (excluding the post's own row on edit). */
    private String generateUniqueSlug(String title, Long excludingId) {
        String base = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("[\\s-]+", "-");
        if (base.isBlank()) {
            base = "post";
        }
        String candidate = base;
        int suffix = 2;
        while (slugTaken(candidate, excludingId)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private boolean slugTaken(String slug, Long excludingId) {
        return blogRepository.findBySlug(slug)
                .filter(existing -> excludingId == null || !existing.getId().equals(excludingId))
                .isPresent();
    }

    /** Clamp page size (max 100, default 20) and default to newest-first when unsorted. */
    private Pageable normalize(Pageable pageable) {
        int size = Math.min(pageable.isPaged() ? pageable.getPageSize() : 20, 100);
        int number = pageable.isPaged() ? pageable.getPageNumber() : 0;
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt");
        return PageRequest.of(number, size, sort);
    }

    private Pageable normalizePublic(Pageable pageable) {
        int size = Math.min(pageable.isPaged() ? pageable.getPageSize() : 20, 100);
        int number = pageable.isPaged() ? pageable.getPageNumber() : 0;
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "publishedAt");
        return PageRequest.of(number, size, sort);
    }
}
