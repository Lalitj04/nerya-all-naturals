package com.nerya.neryaallnaturals.service;

import com.nerya.neryaallnaturals.dto.InventoryRequest;
import com.nerya.neryaallnaturals.dto.InventoryResponse;
import com.nerya.neryaallnaturals.entity.Inventory;
import com.nerya.neryaallnaturals.entity.Product;
import com.nerya.neryaallnaturals.repository.InventoryRepository;
import com.nerya.neryaallnaturals.repository.ProductRepository;
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
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    /**
     * Get all inventory records
     */
    @Transactional(readOnly = true)
    public List<InventoryResponse> getAllInventories() {
        log.debug("Fetching all inventory records");
        return inventoryRepository.findAll().stream()
                .map(InventoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get inventory by ID
     */
    @Transactional(readOnly = true)
    public Optional<InventoryResponse> getInventoryById(Long id) {
        log.debug("Fetching inventory with ID: {}", id);
        return inventoryRepository.findById(id)
                .map(InventoryResponse::fromEntity);
    }

    /**
     * Get inventory by product ID
     */
    @Transactional(readOnly = true)
    public Optional<InventoryResponse> getInventoryByProductId(Long productId) {
        log.debug("Fetching inventory for product ID: {}", productId);
        return inventoryRepository.findByProductId(productId)
                .map(InventoryResponse::fromEntity);
    }

    /**
     * Create a new inventory record
     */
    @Transactional
    public InventoryResponse createInventory(InventoryRequest inventoryRequest) {
        log.info("Creating new inventory for product ID: {}", inventoryRequest.getProductId());

        // Check if product exists
        Optional<Product> productOptional = productRepository.findById(inventoryRequest.getProductId());
        if (productOptional.isEmpty()) {
            throw new RuntimeException("Product not found with ID: " + inventoryRequest.getProductId());
        }

        // Check if inventory already exists for this product
        Optional<Inventory> existingInventory = inventoryRepository.findByProductId(inventoryRequest.getProductId());
        if (existingInventory.isPresent()) {
            throw new RuntimeException("Inventory already exists for product ID: " + inventoryRequest.getProductId());
        }

        // Create inventory
        Inventory inventory = Inventory.builder()
                .product(productOptional.get())
                .quantityOnHand(inventoryRequest.getQuantityOnHand())
                .quantityReserved(inventoryRequest.getQuantityReserved() != null ? inventoryRequest.getQuantityReserved() : 0)
                .quantitySold(inventoryRequest.getQuantitySold() != null ? inventoryRequest.getQuantitySold() : 0)
                .minStockLevel(inventoryRequest.getMinStockLevel() != null ? inventoryRequest.getMinStockLevel() : 5)
                .maxStockLevel(inventoryRequest.getMaxStockLevel() != null ? inventoryRequest.getMaxStockLevel() : 1000)
                .reorderQuantity(inventoryRequest.getReorderQuantity() != null ? inventoryRequest.getReorderQuantity() : 50)
                .lastUpdatedBy(inventoryRequest.getLastUpdatedBy())
                .build();

        Inventory savedInventory = inventoryRepository.save(inventory);
        log.info("Inventory created successfully for product ID: {}", inventoryRequest.getProductId());

        return InventoryResponse.fromEntity(savedInventory);
    }

    /**
     * Update inventory by ID
     */
    @Transactional
    public Optional<InventoryResponse> updateInventory(Long id, InventoryRequest inventoryRequest) {
        log.info("Updating inventory with ID: {}", id);

        Optional<Inventory> inventoryOptional = inventoryRepository.findById(id);
        if (inventoryOptional.isEmpty()) {
            return Optional.empty();
        }

        Inventory inventory = inventoryOptional.get();

        // Check if product ID is being changed
        if (!inventory.getProduct().getId().equals(inventoryRequest.getProductId())) {
            // Check if new product exists
            Optional<Product> productOptional = productRepository.findById(inventoryRequest.getProductId());
            if (productOptional.isEmpty()) {
                throw new RuntimeException("Product not found with ID: " + inventoryRequest.getProductId());
            }

            // Check if inventory already exists for the new product
            Optional<Inventory> existingInventory = inventoryRepository.findByProductId(inventoryRequest.getProductId());
            if (existingInventory.isPresent() && !existingInventory.get().getId().equals(id)) {
                throw new RuntimeException("Inventory already exists for product ID: " + inventoryRequest.getProductId());
            }

            inventory.setProduct(productOptional.get());
        }

        // Update inventory fields
        inventory.setQuantityOnHand(inventoryRequest.getQuantityOnHand());
        if (inventoryRequest.getQuantityReserved() != null) {
            inventory.setQuantityReserved(inventoryRequest.getQuantityReserved());
        }
        if (inventoryRequest.getQuantitySold() != null) {
            inventory.setQuantitySold(inventoryRequest.getQuantitySold());
        }
        if (inventoryRequest.getMinStockLevel() != null) {
            inventory.setMinStockLevel(inventoryRequest.getMinStockLevel());
        }
        if (inventoryRequest.getMaxStockLevel() != null) {
            inventory.setMaxStockLevel(inventoryRequest.getMaxStockLevel());
        }
        if (inventoryRequest.getReorderQuantity() != null) {
            inventory.setReorderQuantity(inventoryRequest.getReorderQuantity());
        }
        if (inventoryRequest.getLastUpdatedBy() != null) {
            inventory.setLastUpdatedBy(inventoryRequest.getLastUpdatedBy());
        }

        Inventory updatedInventory = inventoryRepository.save(inventory);
        log.info("Inventory updated successfully with ID: {}", id);

        return Optional.of(InventoryResponse.fromEntity(updatedInventory));
    }

    /**
     * Delete inventory by ID
     */
    @Transactional
    public boolean deleteInventory(Long id) {
        log.info("Deleting inventory with ID: {}", id);

        Optional<Inventory> inventoryOptional = inventoryRepository.findById(id);
        if (inventoryOptional.isEmpty()) {
            return false;
        }

        inventoryRepository.delete(inventoryOptional.get());
        log.info("Inventory deleted successfully with ID: {}", id);
        return true;
    }
}
