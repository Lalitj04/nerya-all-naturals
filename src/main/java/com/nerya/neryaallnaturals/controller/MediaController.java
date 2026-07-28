package com.nerya.neryaallnaturals.controller;

import com.nerya.neryaallnaturals.annotation.AdminOnly;
import com.nerya.neryaallnaturals.dto.MediaAssetResponse;
import com.nerya.neryaallnaturals.dto.MediaRegisterRequest;
import com.nerya.neryaallnaturals.dto.MediaUpdateRequest;
import com.nerya.neryaallnaturals.entity.MediaAsset.MediaCategory;
import com.nerya.neryaallnaturals.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Media", description = "Cloudinary-backed image metadata: admin uploads/registers/manages assets; " +
        "the storefront fetches them by category or product with no auth required")
public class MediaController {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private final MediaService mediaService;

    /**
     * Admin only - Upload an image to Cloudinary and record its metadata.
     * Admin API - Requires authentication
     */
    @Operation(summary = "Upload an image", description = "Uploads to Cloudinary (into a folder derived from the " +
            "category), and records its public URL + metadata. Accepts image/jpeg, image/png, " +
            "image/webp, image/gif up to 5 MB.")
    @PostMapping(value = "/admin/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminOnly
    public ResponseEntity<MediaAssetResponse> upload(
            @Parameter(description = "Image file", required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Media category", required = true)
            @RequestParam("category") MediaCategory category,
            @Parameter(description = "Alt text for accessibility/SEO")
            @RequestParam(value = "altText", required = false) String altText,
            @Parameter(description = "On-screen headline (mainly for HERO/BANNER slides)")
            @RequestParam(value = "title", required = false) String title,
            @Parameter(description = "Supporting sub-heading text")
            @RequestParam(value = "subtitle", required = false) String subtitle,
            @Parameter(description = "Click-through/redirection target when the image is clicked")
            @RequestParam(value = "linkUrl", required = false) String linkUrl,
            @Parameter(description = "CTA button label paired with linkUrl")
            @RequestParam(value = "linkText", required = false) String linkText,
            @Parameter(description = "Display order within the category (default 0)")
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            @Parameter(description = "Product to attach this image to (optional)")
            @RequestParam(value = "productId", required = false) Long productId,
            @Parameter(description = "Category to link this image to, for category tiles (optional)")
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @Parameter(description = "Blog post to attach this image to, for cover/inline post images (optional)")
            @RequestParam(value = "blogId", required = false) Long blogId) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }

        log.info("Admin: uploading media file '{}' to category {}", file.getOriginalFilename(), category);
        MediaAssetResponse response = mediaService.upload(file, category, altText, title, subtitle,
                linkUrl, linkText, sortOrder, productId, categoryId, blogId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Admin only - Register an image already hosted elsewhere (any CDN/Cloudinary URL).
     * Admin API - Requires authentication
     */
    @Operation(summary = "Register an existing hosted image", description = "Records an image already hosted " +
            "elsewhere by its public URL, without re-uploading it.")
    @PostMapping("/admin/register")
    @AdminOnly
    public ResponseEntity<MediaAssetResponse> register(@Valid @RequestBody MediaRegisterRequest request) throws IOException {
        log.info("Admin: registering existing image {}", request.getPublicUrl());
        MediaAssetResponse response = mediaService.registerExisting(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Admin only - Update media metadata.
     * Admin API - Requires authentication
     */
    @Operation(summary = "Update media metadata", description = "Updates alt text, sort order, category, and/or " +
            "active flag. Fields left null are unchanged.")
    @PutMapping("/admin/{id}")
    @AdminOnly
    public ResponseEntity<MediaAssetResponse> update(@PathVariable Long id, @Valid @RequestBody MediaUpdateRequest request) {
        log.info("Admin: updating media asset {}", id);
        return ResponseEntity.ok(mediaService.update(id, request));
    }

    /**
     * Admin only - Soft delete (default) or hard delete (?hard=true, also removes the Cloudinary asset).
     * Admin API - Requires authentication
     */
    @Operation(summary = "Delete a media asset", description = "By default sets isActive=false (soft delete). " +
            "With ?hard=true, also deletes the underlying Cloudinary asset and removes the row.")
    @DeleteMapping("/admin/{id}")
    @AdminOnly
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                        @Parameter(description = "Also delete the Cloudinary asset")
                                        @RequestParam(value = "hard", defaultValue = "false") boolean hard) throws IOException {
        log.info("Admin: deleting media asset {} (hard={})", id, hard);
        mediaService.delete(id, hard);
        return ResponseEntity.noContent().build();
    }

    /**
     * Fetch active media assets, optionally filtered by category and/or product.
     * Open API - No authentication required
     */
    @Operation(summary = "List media assets", description = "Active assets, optionally filtered by category " +
            "and/or product. No authentication required.")
    @GetMapping
    public ResponseEntity<List<MediaAssetResponse>> getAll(
            @Parameter(description = "Filter by category") @RequestParam(value = "category", required = false) MediaCategory category,
            @Parameter(description = "Filter by product ID") @RequestParam(value = "productId", required = false) Long productId) {
        return ResponseEntity.ok(mediaService.getActive(category, productId));
    }

    /**
     * Fetch active media assets for a category, ordered by sort order.
     * Open API - No authentication required
     */
    @Operation(summary = "List media by category", description = "Active assets in a category (e.g. HERO, " +
            "BANNER), ordered by sort order. No authentication required.")
    @GetMapping("/category/{category}")
    public ResponseEntity<List<MediaAssetResponse>> getByCategory(@PathVariable MediaCategory category) {
        return ResponseEntity.ok(mediaService.getByCategory(category));
    }

    /**
     * Fetch a product's active media gallery.
     * Open API - No authentication required
     */
    @Operation(summary = "List a product's media gallery", description = "No authentication required.")
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<MediaAssetResponse>> getByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(mediaService.getByProduct(productId));
    }

    /**
     * Fetch the images linked to a Category entity (category tiles), ordered by sort order.
     * Open API - No authentication required
     */
    @Operation(summary = "List a category's tile images",
            description = "Active images linked to a Category entity by id. No authentication required.")
    @GetMapping("/category-entity/{categoryId}")
    public ResponseEntity<List<MediaAssetResponse>> getByCategoryEntity(@PathVariable Long categoryId) {
        return ResponseEntity.ok(mediaService.getByCategoryEntity(categoryId));
    }

    /**
     * Fetch the images linked to a Blog post (cover + inline gallery), ordered by sort order.
     * Open API - No authentication required
     */
    @Operation(summary = "List a blog post's images",
            description = "Active images linked to a Blog entity by id, ordered by sort order (sortOrder 0 is " +
                    "the cover image by convention). No authentication required.")
    @GetMapping("/blog/{blogId}")
    public ResponseEntity<List<MediaAssetResponse>> getByBlog(@PathVariable Long blogId) {
        return ResponseEntity.ok(mediaService.getByBlog(blogId));
    }

    /**
     * Fetch a single media asset by ID.
     * Open API - No authentication required
     */
    @Operation(summary = "Get a media asset by ID", description = "No authentication required.")
    @GetMapping("/{id}")
    public ResponseEntity<MediaAssetResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(mediaService.getById(id));
    }
}
