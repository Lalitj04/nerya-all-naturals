package com.nerya.neryaallnaturals.controller;

import com.nerya.neryaallnaturals.annotation.AdminOnly;
import com.nerya.neryaallnaturals.dto.InventoryRequest;
import com.nerya.neryaallnaturals.dto.InventoryResponse;
import com.nerya.neryaallnaturals.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * Admin only - Get all inventory records
     * Admin API - Requires authentication
     * 
     * @return list of all inventory records
     */
    @GetMapping("/admin/all")
    @AdminOnly
    public ResponseEntity<List<InventoryResponse>> getAllInventories() {
        log.info("Admin: Fetching all inventory records");
        List<InventoryResponse> inventories = inventoryService.getAllInventories();
        return ResponseEntity.ok(inventories);
    }

    /**
     * Admin only - Get inventory by ID
     * Admin API - Requires authentication
     * 
     * @param id inventory ID
     * @return inventory details
     */
    @GetMapping("/admin/{id}")
    @AdminOnly
    public ResponseEntity<?> getInventoryById(@PathVariable Long id) {
        log.info("Admin: Fetching inventory with ID: {}", id);
        Optional<InventoryResponse> inventory = inventoryService.getInventoryById(id);
        
        if (inventory.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Inventory not found with ID: " + id);
        }
        
        return ResponseEntity.ok(inventory.get());
    }

    /**
     * Admin only - Get inventory by product ID
     * Admin API - Requires authentication
     * 
     * @param productId product ID
     * @return inventory details for the product
     */
    @GetMapping("/admin/product/{productId}")
    @AdminOnly
    public ResponseEntity<?> getInventoryByProductId(@PathVariable Long productId) {
        log.info("Admin: Fetching inventory for product ID: {}", productId);
        Optional<InventoryResponse> inventory = inventoryService.getInventoryByProductId(productId);
        
        if (inventory.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Inventory not found for product ID: " + productId);
        }
        
        return ResponseEntity.ok(inventory.get());
    }

    /**
     * Admin only - Create a new inventory record
     * Admin API - Requires authentication
     * 
     * @param inventoryRequest inventory details
     * @return created inventory
     */
    @PostMapping("/admin")
    @AdminOnly
    public ResponseEntity<?> createInventory(@Valid @RequestBody InventoryRequest inventoryRequest) {
        log.info("Admin: Creating new inventory for product ID: {}", inventoryRequest.getProductId());
        
        try {
            InventoryResponse createdInventory = inventoryService.createInventory(inventoryRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdInventory);
            
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    /**
     * Admin only - Update inventory details
     * Admin API - Requires authentication
     * 
     * @param id inventory ID
     * @param inventoryRequest updated inventory details
     * @return updated inventory
     */
    @PutMapping("/admin/{id}")
    @AdminOnly
    public ResponseEntity<?> updateInventory(
            @PathVariable Long id,
            @Valid @RequestBody InventoryRequest inventoryRequest) {
        log.info("Admin: Updating inventory with ID: {}", id);
        
        try {
            Optional<InventoryResponse> updatedInventory = inventoryService.updateInventory(id, inventoryRequest);
            
            if (updatedInventory.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Inventory not found with ID: " + id);
            }
            
            return ResponseEntity.ok(updatedInventory.get());
            
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    /**
     * Admin only - Delete inventory
     * Admin API - Requires authentication
     * 
     * @param id inventory ID
     * @return success message
     */
    @DeleteMapping("/admin/{id}")
    @AdminOnly
    public ResponseEntity<?> deleteInventory(@PathVariable Long id) {
        log.info("Admin: Deleting inventory with ID: {}", id);
        
        boolean deleted = inventoryService.deleteInventory(id);
        
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Inventory not found with ID: " + id);
        }
        
        return ResponseEntity.ok("Inventory deleted successfully");
    }
}
