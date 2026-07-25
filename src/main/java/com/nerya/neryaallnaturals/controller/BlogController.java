package com.nerya.neryaallnaturals.controller;

import com.nerya.neryaallnaturals.annotation.AdminOnly;
import com.nerya.neryaallnaturals.dto.BlogRequest;
import com.nerya.neryaallnaturals.dto.BlogResponse;
import com.nerya.neryaallnaturals.dto.PagedResponse;
import com.nerya.neryaallnaturals.service.BlogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Blog APIs (T64/T66). Admin writes/publishes posts; the public only ever sees published ones,
 * looked up by slug so drafts can't be guessed at by id.
 */
@RestController
@RequestMapping("/api/blogs")
@RequiredArgsConstructor
@Slf4j
public class BlogController {

    private final BlogService blogService;

    // ---- public ----

    @GetMapping
    public ResponseEntity<PagedResponse<BlogResponse>> getPublished(Pageable pageable) {
        return ResponseEntity.ok(blogService.getPublished(pageable));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<BlogResponse> getPublishedBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(blogService.getPublishedBySlug(slug));
    }

    // ---- admin ----

    @GetMapping("/admin")
    @AdminOnly
    public ResponseEntity<PagedResponse<BlogResponse>> getAllForAdmin(Pageable pageable) {
        return ResponseEntity.ok(blogService.getAllForAdmin(pageable));
    }

    @GetMapping("/admin/{id}")
    @AdminOnly
    public ResponseEntity<BlogResponse> getForAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(blogService.getForAdmin(id));
    }

    @PostMapping("/admin")
    @AdminOnly
    public ResponseEntity<BlogResponse> create(@Valid @RequestBody BlogRequest request, Authentication authentication) {
        log.info("Admin '{}' creating blog draft '{}'", authentication.getName(), request.getTitle());
        BlogResponse response = blogService.createDraft(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/admin/{id}")
    @AdminOnly
    public ResponseEntity<BlogResponse> update(@PathVariable Long id, @Valid @RequestBody BlogRequest request) {
        log.info("Admin updating blog {}", id);
        return ResponseEntity.ok(blogService.updateBlog(id, request));
    }

    @PostMapping("/admin/{id}/publish")
    @AdminOnly
    public ResponseEntity<BlogResponse> publish(@PathVariable Long id) {
        log.info("Admin publishing blog {}", id);
        return ResponseEntity.ok(blogService.publish(id));
    }

    @PostMapping("/admin/{id}/unpublish")
    @AdminOnly
    public ResponseEntity<BlogResponse> unpublish(@PathVariable Long id) {
        log.info("Admin unpublishing blog {}", id);
        return ResponseEntity.ok(blogService.unpublish(id));
    }

    @DeleteMapping("/admin/{id}")
    @AdminOnly
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Admin deleting blog {}", id);
        blogService.deleteBlog(id);
        return ResponseEntity.noContent().build();
    }
}
