package com.nerya.neryaallnaturals.service;

import com.nerya.neryaallnaturals.dto.CategoryRequest;
import com.nerya.neryaallnaturals.dto.CategoryResponse;
import com.nerya.neryaallnaturals.entity.Category;
import com.nerya.neryaallnaturals.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * Get all active categories
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllActiveCategories() {
        log.debug("Fetching all active categories");
        return categoryRepository.findByIsActiveTrue().stream()
                .map(CategoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get category by ID
     */
    @Transactional(readOnly = true)
    public Optional<CategoryResponse> getCategoryById(Long id) {
        log.debug("Fetching category with ID: {}", id);
        return categoryRepository.findById(id)
                .map(CategoryResponse::fromEntity);
    }

    /**
     * Get all parent categories
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getParentCategories() {
        log.debug("Fetching all parent categories");
        return categoryRepository.findByParentCategoryIsNull().stream()
                .map(CategoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Create a new category
     */
    @Transactional
    public CategoryResponse createCategory(CategoryRequest categoryRequest) {
        log.info("Creating new category: {}", categoryRequest.getName());

        // Check if category name already exists
        if (categoryRepository.findByName(categoryRequest.getName()).isPresent()) {
            throw new RuntimeException("Category name already exists: " + categoryRequest.getName());
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
                throw new RuntimeException("Parent category not found with ID: " + categoryRequest.getParentId());
            }
            category.setParentCategory(parentCategory.get());
        }

        Category savedCategory = categoryRepository.save(category);
        log.info("Category created successfully: {}", savedCategory.getName());

        return CategoryResponse.fromEntity(savedCategory);
    }
}

