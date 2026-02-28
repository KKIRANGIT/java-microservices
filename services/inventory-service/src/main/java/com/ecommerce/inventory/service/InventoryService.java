package com.ecommerce.inventory.service;

import com.ecommerce.inventory.api.InventoryResponse;
import com.ecommerce.inventory.api.ReserveInventoryRequest;
import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public InventoryResponse getInventory(String skuCode) {
        Inventory inventory = inventoryRepository.findBySkuCode(skuCode).orElse(null);
        if (inventory == null) {
            return new InventoryResponse(skuCode, false, 0);
        }
        return new InventoryResponse(skuCode, inventory.getQuantity() > 0, inventory.getQuantity());
    }

    @Transactional
    public InventoryResponse reserveInventory(ReserveInventoryRequest request) {
        Inventory inventory = inventoryRepository.findBySkuCode(request.skuCode()).orElse(null);
        if (inventory == null || inventory.getQuantity() < request.quantity()) {
            int available = inventory == null ? 0 : inventory.getQuantity();
            return new InventoryResponse(request.skuCode(), false, available);
        }

        inventory.setQuantity(inventory.getQuantity() - request.quantity());
        return new InventoryResponse(request.skuCode(), true, inventory.getQuantity());
    }
}
