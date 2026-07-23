package com.nerya.neryaallnaturals.service;

import com.nerya.neryaallnaturals.dto.CategoryRequest;
import com.nerya.neryaallnaturals.dto.CategoryResponse;
import com.nerya.neryaallnaturals.entity.Category;
import com.nerya.neryaallnaturals.entity.MediaAsset;
import com.nerya.neryaallnaturals.exception.ConflictException;
import com.nerya.neryaallnaturals.exception.ResourceNotFoundException;
import com.nerya.neryaallnaturals.repository.CategoryRepository;
import com.nerya.neryaallnaturals.repository.MediaAssetRepository;
import com.nerya.neryaallnaturals.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final MediaAssetRepository mediaAssetRepository;

    /**
     * Get all active categories
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllActiveCategories() {
        log.debug("Fetching all active categories");
        return withThumbnails(categoryRepository.findByIsActiveTrue());
    }

    /**
     * Get category by ID
     */
    @Transactional(readOnly = true)
    public Optional<CategoryResponse> getCategoryById(Long id) {
        log.debug("Fetching category with ID: {}", id);
        return categoryRepository.findById(id)
                .map(category -> CategoryResponse.fromEntity(category, thumbnailsFor(List.of(id)).get(id)));
    }

    /**
     * Get all parent categories
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getParentCategories() {
        log.debug("Fetching all parent categories");
        return withThumbnails(categoryRepository.findByParentCategoryIsNull());
    }

    /**
     * Map categories to responses, batch-fetching each one's category-image thumbnail in a
     * single query rather than one per category.
     */
    private List<CategoryResponse> withThumbnails(List<Category> categories) {
        if (categories.isEmpty()) {
            return List.of();
        }
        List<Long> ids = categories.stream().map(Category::getId).collect(Collectors.toList());
        Map<Long, String> thumbnails = thumbnailsFor(ids);
        return categories.stream()
                .map(category -> CategoryResponse.fromEntity(category, thumbnails.get(category.getId())))
                .collect(Collectors.toList());
    }

    /**
     * @return the lowest-sort-order active CATEGORY-linked media asset's public URL per
     * category id (absent when a category has no image).
     */
    private Map<Long, String> thumbnailsFor(List<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return mediaAssetRepository.findByLinkedCategoryIdInAndIsActiveTrue(categoryIds).stream()
                .collect(Collectors.groupingBy(
                        asset -> asset.getLinkedCategory().getId(),
                        Collectors.collectingAndThen(
                                Collectors.minBy(Comparator.comparingInt(
                                        a -> a.getSortOrder() != null ? a.getSortOrder() : 0)),
                                opt -> opt.map(MediaAsset::getPublicUrl).orElse(null))));
    }

    /**
     * Create a new category
     */
    @Transactional
    public CategoryResponse createCategory(CategoryRequest categoryRequest) {
        log.info("Creating new category: {}", categoryRequest.getName());

        // Check if category name already exists
        if (categoryRepository.findByName(categoryRequest.getName()).isPresent()) {
            throw new ConflictException("Category name already exists: " + categoryRequest.getName());
        }

        Category category = Category.builder()
                .name(categoryRequest.getName())
                .description(categoryRequest.getDescription())
                .imageUrl(categoryRequest.getImageUrl())
                .isActive(categoryRequest.getIsActive())
                .build();

        // Set parent category if provided
        if (categoryRequest.getParentId() != null) {
            Optional<Category> parentCategory = categoryRepository.findById(categoryRequest.getParentId());
            if (parentCategory.isEmpty()) {
                throw new ResourceNotFoundException("Parent category not found with ID: " + categoryRequest.getParentId());
            }
            category.setParentCategory(parentCategory.get());
        }

        Category savedCategory = categoryRepository.save(category);
        log.info("Category created successfully: {}", savedCategory.getName());

        return CategoryResponse.fromEntity(savedCategory);
    }

    /**
     * Update an existing category (admin only).
     */
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest categoryRequest) {
        log.info("Updating category with ID: {}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + id));

        // Reject a name change that collides with another category.
        if (!category.getName().equals(categoryRequest.getName())
                && categoryRepository.findByName(categoryRequest.getName()).isPresent()) {
            throw new ConflictException("Category name already exists: " + categoryRequest.getName());
        }

        category.setName(categoryRequest.getName());
        category.setDescription(categoryRequest.getDescription());
        category.setImageUrl(categoryRequest.getImageUrl());
        if (categoryRequest.getIsActive() != null) {
            category.setIsActive(categoryRequest.getIsActive());
        }

        if (categoryRequest.getParentId() != null) {
            if (categoryRequest.getParentId().equals(id)) {
                throw new ConflictException("A category cannot be its own parent");
            }
            Category parent = categoryRepository.findById(categoryRequest.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent category not found with ID: " + categoryRequest.getParentId()));
            category.setParentCategory(parent);
        } else {
            category.setParentCategory(null);
        }

        Category updated = categoryRepository.save(category);
        log.info("Category updated successfully: {}", updated.getName());
        return CategoryResponse.fromEntity(updated);
    }

    /**
     * Soft-delete a category (admin only). Refuses (409) when products or subcategories
     * still reference it, so nothing is silently orphaned — the caller must reassign or
     * remove those first.
     */
    @Transactional
    public void deleteCategory(Long id) {
        log.info("Deleting category with ID: {}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + id));

        if (productRepository.existsByCategoryId(id)) {
            throw new ConflictException("Cannot delete a category that still has products; "
                    + "reassign or remove them first");
        }
        if (!category.getSubCategories().isEmpty()) {
            throw new ConflictException("Cannot delete a category that still has subcategories");
        }

        category.setIsActive(false);
        categoryRepository.save(category);
        log.info("Category soft-deleted successfully with ID: {}", id);
    }
}

