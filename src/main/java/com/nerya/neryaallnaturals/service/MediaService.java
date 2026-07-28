package com.nerya.neryaallnaturals.service;

import com.nerya.neryaallnaturals.config.CloudinaryProperties;
import com.nerya.neryaallnaturals.dto.MediaAssetResponse;
import com.nerya.neryaallnaturals.dto.MediaRegisterRequest;
import com.nerya.neryaallnaturals.dto.MediaUpdateRequest;
import com.nerya.neryaallnaturals.entity.Blog;
import com.nerya.neryaallnaturals.entity.Category;
import com.nerya.neryaallnaturals.entity.MediaAsset;
import com.nerya.neryaallnaturals.entity.MediaAsset.MediaCategory;
import com.nerya.neryaallnaturals.entity.Product;
import com.nerya.neryaallnaturals.exception.ConflictException;
import com.nerya.neryaallnaturals.exception.ResourceNotFoundException;
import com.nerya.neryaallnaturals.repository.BlogRepository;
import com.nerya.neryaallnaturals.repository.CategoryRepository;
import com.nerya.neryaallnaturals.repository.MediaAssetRepository;
import com.nerya.neryaallnaturals.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

    private final MediaAssetRepository mediaAssetRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BlogRepository blogRepository;
    private final CloudinaryService cloudinaryService;
    private final CloudinaryProperties cloudinaryProperties;

    @Transactional
    public MediaAssetResponse upload(MultipartFile file, MediaCategory category, String altText,
                                      String title, String subtitle, String linkUrl, String linkText,
                                      Integer sortOrder, Long productId, Long categoryId,
                                      Long blogId) throws IOException {
        Product product = resolveProduct(productId);
        Category linkedCategory = resolveCategory(categoryId);
        Blog linkedBlog = resolveBlog(blogId);

        String folder = folderFor(category);
        CloudinaryService.UploadResult uploaded = cloudinaryService.upload(file, folder);

        MediaAsset asset = MediaAsset.builder()
                .storageKey(uploaded.publicId())
                .fileName(file.getOriginalFilename())
                .mimeType(file.getContentType())
                .category(category)
                .publicUrl(uploaded.url())
                .folderPath(folder)
                .altText(altText)
                .title(title)
                .subtitle(subtitle)
                .linkUrl(linkUrl)
                .linkText(linkText)
                .sortOrder(sortOrder != null ? sortOrder : 0)
                .product(product)
                .linkedCategory(linkedCategory)
                .linkedBlog(linkedBlog)
                .isActive(true)
                .build();

        MediaAsset saved = mediaAssetRepository.save(asset);
        log.info("Uploaded media asset {} (category {})", saved.getId(), category);
        return MediaAssetResponse.fromEntity(saved);
    }

    /**
     * Record an image that is already hosted elsewhere (any CDN/Cloudinary URL) as a media asset,
     * without re-uploading it. Useful for images uploaded outside the app.
     */
    @Transactional
    public MediaAssetResponse registerExisting(MediaRegisterRequest request) {
        String storageKey = StringUtils.hasText(request.getStorageKey())
                ? request.getStorageKey()
                : "external-" + UUID.randomUUID();

        if (mediaAssetRepository.findByStorageKey(storageKey).isPresent()) {
            throw new ConflictException("A media asset already exists for this storage key");
        }

        Product product = resolveProduct(request.getProductId());
        Category linkedCategory = resolveCategory(request.getCategoryId());
        Blog linkedBlog = resolveBlog(request.getBlogId());

        MediaAsset asset = MediaAsset.builder()
                .storageKey(storageKey)
                .fileName(request.getPublicUrl())
                .category(request.getCategory())
                .publicUrl(request.getPublicUrl())
                .altText(request.getAltText())
                .title(request.getTitle())
                .subtitle(request.getSubtitle())
                .linkUrl(request.getLinkUrl())
                .linkText(request.getLinkText())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .product(product)
                .linkedCategory(linkedCategory)
                .linkedBlog(linkedBlog)
                .isActive(true)
                .build();

        MediaAsset saved = mediaAssetRepository.save(asset);
        log.info("Registered existing image {} as media asset {}", request.getPublicUrl(), saved.getId());
        return MediaAssetResponse.fromEntity(saved);
    }

    @Transactional
    public MediaAssetResponse update(Long id, MediaUpdateRequest request) {
        MediaAsset asset = mediaAssetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset not found"));

        if (request.getAltText() != null) {
            asset.setAltText(request.getAltText());
        }
        if (request.getTitle() != null) {
            asset.setTitle(request.getTitle());
        }
        if (request.getSubtitle() != null) {
            asset.setSubtitle(request.getSubtitle());
        }
        if (request.getLinkUrl() != null) {
            asset.setLinkUrl(request.getLinkUrl());
        }
        if (request.getLinkText() != null) {
            asset.setLinkText(request.getLinkText());
        }
        if (request.getSortOrder() != null) {
            asset.setSortOrder(request.getSortOrder());
        }
        if (request.getCategory() != null) {
            asset.setCategory(request.getCategory());
        }
        if (request.getIsActive() != null) {
            asset.setIsActive(request.getIsActive());
        }

        MediaAsset updated = mediaAssetRepository.save(asset);
        return MediaAssetResponse.fromEntity(updated);
    }

    @Transactional
    public void delete(Long id, boolean hard) throws IOException {
        MediaAsset asset = mediaAssetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset not found"));

        if (hard) {
            cloudinaryService.delete(asset.getStorageKey());
            mediaAssetRepository.delete(asset);
            log.info("Hard-deleted media asset {} (storage key {})", id, asset.getStorageKey());
        } else {
            asset.setIsActive(false);
            mediaAssetRepository.save(asset);
            log.info("Soft-deleted media asset {}", id);
        }
    }

    @Transactional(readOnly = true)
    public List<MediaAssetResponse> getActive(MediaCategory category, Long productId) {
        List<MediaAsset> assets;
        if (category != null && productId != null) {
            assets = mediaAssetRepository.findByCategoryAndProductIdAndIsActiveTrueOrderBySortOrderAsc(category, productId);
        } else if (category != null) {
            assets = mediaAssetRepository.findByCategoryAndIsActiveTrueOrderBySortOrderAsc(category);
        } else if (productId != null) {
            assets = mediaAssetRepository.findByProductIdAndIsActiveTrue(productId);
        } else {
            assets = mediaAssetRepository.findByIsActiveTrueOrderBySortOrderAsc();
        }
        return toResponses(assets);
    }

    @Transactional(readOnly = true)
    public List<MediaAssetResponse> getByCategory(MediaCategory category) {
        return toResponses(mediaAssetRepository.findByCategoryAndIsActiveTrueOrderBySortOrderAsc(category));
    }

    @Transactional(readOnly = true)
    public List<MediaAssetResponse> getByProduct(Long productId) {
        return toResponses(mediaAssetRepository.findByProductIdAndIsActiveTrue(productId));
    }

    @Transactional(readOnly = true)
    public List<MediaAssetResponse> getByCategoryEntity(Long categoryId) {
        return toResponses(mediaAssetRepository.findByLinkedCategoryIdAndIsActiveTrueOrderBySortOrderAsc(categoryId));
    }

    @Transactional(readOnly = true)
    public List<MediaAssetResponse> getByBlog(Long blogId) {
        return toResponses(mediaAssetRepository.findByLinkedBlogIdAndIsActiveTrueOrderBySortOrderAsc(blogId));
    }

    @Transactional(readOnly = true)
    public MediaAssetResponse getById(Long id) {
        return mediaAssetRepository.findById(id)
                .map(MediaAssetResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset not found"));
    }

    private List<MediaAssetResponse> toResponses(List<MediaAsset> assets) {
        return assets.stream().map(MediaAssetResponse::fromEntity).collect(Collectors.toList());
    }

    /** Cloudinary folder an asset of this category is stored under, e.g. {@code nerya/hero}. */
    private String folderFor(MediaCategory category) {
        return cloudinaryProperties.getFolder() + "/" + category.name().toLowerCase();
    }

    private Product resolveProduct(Long productId) {
        if (productId == null) {
            return null;
        }
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + categoryId));
    }

    private Blog resolveBlog(Long blogId) {
        if (blogId == null) {
            return null;
        }
        return blogRepository.findById(blogId)
                .orElseThrow(() -> new ResourceNotFoundException("Blog not found with ID: " + blogId));
    }
}
