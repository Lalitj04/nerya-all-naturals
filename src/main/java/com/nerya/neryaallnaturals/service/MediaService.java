package com.nerya.neryaallnaturals.service;

import com.google.api.services.drive.model.File;
import com.nerya.neryaallnaturals.config.GoogleDriveProperties;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

    private final MediaAssetRepository mediaAssetRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BlogRepository blogRepository;
    private final GoogleDriveService googleDriveService;
    private final GoogleDriveProperties googleDriveProperties;

    @Transactional
    public MediaAssetResponse upload(MultipartFile file, MediaCategory category, String altText,
                                      Integer sortOrder, Long productId, Long categoryId,
                                      Long blogId) throws IOException {
        String folderId = resolveFolderId(category);
        Product product = resolveProduct(productId);
        Category linkedCategory = resolveCategory(categoryId);
        Blog linkedBlog = resolveBlog(blogId);

        File driveFile = googleDriveService.upload(file, folderId);
        googleDriveService.makePublicReader(driveFile.getId());

        MediaAsset asset = MediaAsset.builder()
                .driveFileId(driveFile.getId())
                .fileName(driveFile.getName())
                .mimeType(driveFile.getMimeType())
                .category(category)
                .publicUrl(GoogleDriveService.publicUrlFor(driveFile.getId()))
                .webViewLink(driveFile.getWebViewLink())
                .thumbnailLink(driveFile.getThumbnailLink())
                .altText(altText)
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

    @Transactional
    public MediaAssetResponse registerExisting(MediaRegisterRequest request) throws IOException {
        if (mediaAssetRepository.findByDriveFileId(request.getDriveFileId()).isPresent()) {
            throw new ConflictException("A media asset already exists for this Drive file");
        }

        Product product = resolveProduct(request.getProductId());
        Category linkedCategory = resolveCategory(request.getCategoryId());
        Blog linkedBlog = resolveBlog(request.getBlogId());

        File driveFile = googleDriveService.getMetadata(request.getDriveFileId());
        googleDriveService.makePublicReader(request.getDriveFileId());

        MediaAsset asset = MediaAsset.builder()
                .driveFileId(request.getDriveFileId())
                .fileName(driveFile.getName())
                .mimeType(driveFile.getMimeType())
                .category(request.getCategory())
                .publicUrl(GoogleDriveService.publicUrlFor(request.getDriveFileId()))
                .webViewLink(driveFile.getWebViewLink())
                .thumbnailLink(driveFile.getThumbnailLink())
                .altText(request.getAltText())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .product(product)
                .linkedCategory(linkedCategory)
                .linkedBlog(linkedBlog)
                .isActive(true)
                .build();

        MediaAsset saved = mediaAssetRepository.save(asset);
        log.info("Registered existing Drive file {} as media asset {}", request.getDriveFileId(), saved.getId());
        return MediaAssetResponse.fromEntity(saved);
    }

    @Transactional
    public MediaAssetResponse update(Long id, MediaUpdateRequest request) {
        MediaAsset asset = mediaAssetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset not found"));

        if (request.getAltText() != null) {
            asset.setAltText(request.getAltText());
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
            googleDriveService.delete(asset.getDriveFileId());
            mediaAssetRepository.delete(asset);
            log.info("Hard-deleted media asset {} (Drive file {})", id, asset.getDriveFileId());
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

    private String resolveFolderId(MediaCategory category) {
        String folderId = googleDriveProperties.getFolders().get(category);
        if (folderId == null || folderId.isBlank()) {
            throw new IllegalStateException("No Drive folder configured for category " + category);
        }
        return folderId;
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
