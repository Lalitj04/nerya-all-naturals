package com.nerya.neryaallnaturals.controller;

import com.nerya.neryaallnaturals.annotation.AdminOnly;
import com.nerya.neryaallnaturals.dto.CategoryRequest;
import com.nerya.neryaallnaturals.dto.CategoryResponse;
import com.nerya.neryaallnaturals.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Fetch all active categories
     * Open API - No authentication required
     * 
     * @return list of all active categories
     */
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        log.info("Fetching all active categories");
        List<CategoryResponse> categories = categoryService.getAllActiveCategories();
        return ResponseEntity.ok(categories);
    }

    /**
     * Fetch category by ID
     * Open API - No authentication required
     * 
     * @param id category ID
     * @return category details
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getCategoryById(@PathVariable Long id) {
        log.info("Fetching category with ID: {}", id);
        Optional<CategoryResponse> category = categoryService.getCategoryById(id);
        
        if (category.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Category not found with ID: " + id);
        }
        
        return ResponseEntity.ok(category.get());
    }

    /**
     * Fetch all parent categories
     * Open API - No authentication required
     * 
     * @return list of parent categories
     */
    @GetMapping("/parents")
    public ResponseEntity<List<CategoryResponse>> getParentCategories() {
        log.info("Fetching all parent categories");
        List<CategoryResponse> categories = categoryService.getParentCategories();
        return ResponseEntity.ok(categories);
    }

    /**
     * Admin only - Create a new category
     * Admin API - Requires authentication
     * 
     * @param categoryRequest category details
     * @return created category
     */
    @PostMapping("/admin")
    @AdminOnly
    public ResponseEntity<?> createCategory(@Valid @RequestBody CategoryRequest categoryRequest) {
        log.info("Admin: Creating new category: {}", categoryRequest.getName());
        
        try {
            CategoryResponse createdCategory = categoryService.createCategory(categoryRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdCategory);
            
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }
}

