package com.ecommerce.inventory.service;

import com.ecommerce.inventory.api.InventoryResponse;
import com.ecommerce.inventory.api.ReserveInventoryRequest;
import com.ecommerce.inventory.event.InventoryProcessedEvent;
import com.ecommerce.inventory.event.OrderCreatedEvent;
import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.outbox.OutboxService;
import com.ecommerce.inventory.repository.InventoryRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryService.class);
    private static final String INVENTORY_EVENTS_TOPIC = "inventory-events";

    private final InventoryRepository inventoryRepository;
    private final OutboxService outboxService;

    public InventoryService(
            InventoryRepository inventoryRepository,
            OutboxService outboxService) {
        this.inventoryRepository = inventoryRepository;
        this.outboxService = outboxService;
    }

    public List<InventoryResponse> getAllInventory() {
        return inventoryRepository.findAll().stream()
                .sorted(Comparator.comparing(Inventory::getSkuCode))
                .map(inventory -> new InventoryResponse(
                        inventory.getSkuCode(),
                        inventory.getQuantity() > 0,
                        inventory.getQuantity()))
                .toList();
    }

    public InventoryResponse getInventory(String skuCode) {
        Inventory inventory = inventoryRepository.findBySkuCode(skuCode).orElse(null);
        if (inventory == null) {
            return new InventoryResponse(skuCode, false, 0);
        }
        return new InventoryResponse(skuCode, inventory.getQuantity() > 0, inventory.getQuantity());
    }

    @Transactional
    public InventoryResponse updateInventory(String skuCode, int quantity) {
        Inventory inventory = inventoryRepository.findBySkuCode(skuCode).orElseGet(() -> {
            Inventory created = new Inventory();
            created.setSkuCode(skuCode);
            return created;
        });
        inventory.setQuantity(quantity);
        Inventory saved = inventoryRepository.save(inventory);
        return new InventoryResponse(saved.getSkuCode(), saved.getQuantity() > 0, saved.getQuantity());
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

    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        LOGGER.info(
                "Processing order-created event: orderNumber={}, skuCode={}, quantity={}",
                event.orderNumber(),
                event.skuCode(),
                event.quantity());

        Inventory inventory = inventoryRepository.findBySkuCode(event.skuCode()).orElse(null);

        boolean reserved = false;
        int available;
        String message;

        if (inventory == null) {
            available = 0;
            message = "Inventory SKU not found";
        } else if (inventory.getQuantity() < event.quantity()) {
            available = inventory.getQuantity();
            message = "Insufficient inventory";
        } else {
            inventory.setQuantity(inventory.getQuantity() - event.quantity());
            reserved = true;
            available = inventory.getQuantity();
            message = "Inventory reserved";
        }

        outboxService.enqueue(
                "INVENTORY",
                event.orderNumber(),
                INVENTORY_EVENTS_TOPIC,
                event.orderNumber(),
                new InventoryProcessedEvent(
                        event.orderNumber(),
                        event.skuCode(),
                        event.quantity(),
                        available,
                        reserved,
                        message,
                        Instant.now()));
        LOGGER.info(
                "Enqueued inventory event in outbox: orderNumber={}, skuCode={}, reserved={}, availableQuantity={}",
                event.orderNumber(),
                event.skuCode(),
                reserved,
                available);
    }
}
