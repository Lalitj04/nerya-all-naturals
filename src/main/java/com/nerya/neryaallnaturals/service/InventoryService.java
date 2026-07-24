package com.nerya.neryaallnaturals.service;

import com.nerya.neryaallnaturals.dto.InventoryRequest;
import com.nerya.neryaallnaturals.dto.InventoryResponse;
import com.nerya.neryaallnaturals.entity.Inventory;
import com.nerya.neryaallnaturals.entity.Product;
import com.nerya.neryaallnaturals.exception.ConflictException;
import com.nerya.neryaallnaturals.exception.InsufficientStockException;
import com.nerya.neryaallnaturals.exception.ResourceNotFoundException;
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
     * Create the single inventory row for a freshly created product (T37), seeded with the
     * given on-hand quantity (defaults to 0). Also syncs the product's derived inStock flag.
     *
     * @param product        the newly persisted product
     * @param quantityOnHand initial stock, or null for 0
     * @return the persisted inventory row
     */
    @Transactional
    public Inventory createForProduct(Product product, Integer quantityOnHand) {
        Inventory inventory = Inventory.builder()
                .product(product)
                .quantityOnHand(quantityOnHand != null ? quantityOnHand : 0)
                .quantityReserved(0)
                .quantitySold(0)
                .minStockLevel(5)
                .maxStockLevel(1000)
                .reorderQuantity(50)
                .build();
        Inventory saved = inventoryRepository.save(inventory);
        syncProductAvailability(saved);
        return saved;
    }

    // ---- Order-driven stock lifecycle (T49). All three lock the row FOR UPDATE and run inside
    // the caller's transaction, so reservations/sales never race or drift. ----

    /**
     * Reserve {@code quantity} units for a checkout: verifies available stock under a write lock
     * and moves it into {@code quantityReserved}. Throws {@link InsufficientStockException} if
     * the available quantity is short.
     */
    @Transactional
    public void reserve(Long productId, int quantity) {
        Inventory inventory = lockForProduct(productId);
        if (inventory.getAvailableQuantity() < quantity) {
            throw new InsufficientStockException(
                    "Only " + inventory.getAvailableQuantity() + " unit(s) of '"
                            + inventory.getProduct().getName() + "' are available");
        }
        inventory.setQuantityReserved(inventory.getQuantityReserved() + quantity);
        persistAndSync(inventory);
        log.info("Reserved {} unit(s) of product {} (reserved now {})",
                quantity, productId, inventory.getQuantityReserved());
    }

    /**
     * Release a previously-held reservation back to available stock — used when an order is
     * cancelled. Clamped at zero so a double-release can never drive the count negative.
     */
    @Transactional
    public void release(Long productId, int quantity) {
        Inventory inventory = lockForProduct(productId);
        int released = Math.max(0, inventory.getQuantityReserved() - quantity);
        inventory.setQuantityReserved(released);
        persistAndSync(inventory);
        log.info("Released {} unit(s) of product {} (reserved now {})", quantity, productId, released);
    }

    /**
     * Convert a reservation into a completed sale when an order ships: the units leave both
     * {@code quantityReserved} and {@code quantityOnHand} and land in {@code quantitySold}.
     */
    @Transactional
    public void ship(Long productId, int quantity) {
        Inventory inventory = lockForProduct(productId);
        inventory.setQuantityReserved(Math.max(0, inventory.getQuantityReserved() - quantity));
        inventory.setQuantityOnHand(Math.max(0, inventory.getQuantityOnHand() - quantity));
        inventory.setQuantitySold(inventory.getQuantitySold() + quantity);
        persistAndSync(inventory);
        log.info("Shipped {} unit(s) of product {} (on hand {}, sold {})",
                quantity, productId, inventory.getQuantityOnHand(), inventory.getQuantitySold());
    }

    private Inventory lockForProduct(Long productId) {
        return inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No inventory for product ID: " + productId));
    }

    private void persistAndSync(Inventory inventory) {
        Inventory saved = inventoryRepository.save(inventory);
        syncProductAvailability(saved);
    }

    /**
     * Recompute and persist the linked product's {@code inStock} flag from the inventory's
     * available quantity. Inventory is the single source of truth for stock (T36, fix V13),
     * so this runs on every inventory change.
     */
    @Transactional
    public void syncProductAvailability(Inventory inventory) {
        Product product = inventory.getProduct();
        boolean available = inventory.getAvailableQuantity() > 0;
        if (!Boolean.valueOf(available).equals(product.getInStock())) {
            product.setInStock(available);
            productRepository.save(product);
        }
    }

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
            throw new ResourceNotFoundException("Product not found with ID: " + inventoryRequest.getProductId());
        }

        // Check if inventory already exists for this product
        Optional<Inventory> existingInventory = inventoryRepository.findByProductId(inventoryRequest.getProductId());
        if (existingInventory.isPresent()) {
            throw new ConflictException("Inventory already exists for product ID: " + inventoryRequest.getProductId());
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
        syncProductAvailability(savedInventory);
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
                throw new ResourceNotFoundException("Product not found with ID: " + inventoryRequest.getProductId());
            }

            // Check if inventory already exists for the new product
            Optional<Inventory> existingInventory = inventoryRepository.findByProductId(inventoryRequest.getProductId());
            if (existingInventory.isPresent() && !existingInventory.get().getId().equals(id)) {
                throw new ConflictException("Inventory already exists for product ID: " + inventoryRequest.getProductId());
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
        syncProductAvailability(updatedInventory);
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
