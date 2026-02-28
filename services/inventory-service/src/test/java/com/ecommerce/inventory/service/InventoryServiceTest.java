package com.ecommerce.inventory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.inventory.api.InventoryResponse;
import com.ecommerce.inventory.api.ReserveInventoryRequest;
import com.ecommerce.inventory.event.InventoryProcessedEvent;
import com.ecommerce.inventory.event.OrderCreatedEvent;
import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void getAllInventory_returnsSortedRows() {
        when(inventoryRepository.findAll())
                .thenReturn(List.of(
                        inventory("SKU-B", 3),
                        inventory("SKU-A", 0)));

        List<InventoryResponse> responses = inventoryService.getAllInventory();

        assertEquals(2, responses.size());
        assertEquals("SKU-A", responses.get(0).skuCode());
        assertFalse(responses.get(0).available());
        assertEquals(0, responses.get(0).quantity());
    }

    @Test
    void getInventory_returnsMissingAsUnavailable() {
        when(inventoryRepository.findBySkuCode("NONE")).thenReturn(Optional.empty());

        InventoryResponse response = inventoryService.getInventory("NONE");

        assertEquals("NONE", response.skuCode());
        assertFalse(response.available());
        assertEquals(0, response.quantity());
    }

    @Test
    void reserveInventory_returnsUnavailable_whenMissing() {
        when(inventoryRepository.findBySkuCode("SKU-X")).thenReturn(Optional.empty());

        InventoryResponse response = inventoryService.reserveInventory(new ReserveInventoryRequest("SKU-X", 2));

        assertFalse(response.available());
        assertEquals(0, response.quantity());
    }

    @Test
    void reserveInventory_returnsUnavailable_whenInsufficient() {
        Inventory existing = inventory("SKU-X", 1);
        when(inventoryRepository.findBySkuCode("SKU-X")).thenReturn(Optional.of(existing));

        InventoryResponse response = inventoryService.reserveInventory(new ReserveInventoryRequest("SKU-X", 2));

        assertFalse(response.available());
        assertEquals(1, response.quantity());
        assertEquals(1, existing.getQuantity());
    }

    @Test
    void reserveInventory_decrementsAndReturnsAvailable_whenSufficient() {
        Inventory existing = inventory("SKU-X", 5);
        when(inventoryRepository.findBySkuCode("SKU-X")).thenReturn(Optional.of(existing));

        InventoryResponse response = inventoryService.reserveInventory(new ReserveInventoryRequest("SKU-X", 2));

        assertTrue(response.available());
        assertEquals(3, response.quantity());
        assertEquals(3, existing.getQuantity());
    }

    @Test
    void handleOrderCreated_publishesFailedEvent_whenSkuMissing() {
        when(inventoryRepository.findBySkuCode("SKU-MISS")).thenReturn(Optional.empty());
        OrderCreatedEvent event = new OrderCreatedEvent(
                "ORD-1",
                "SKU-MISS",
                1,
                "Product",
                new BigDecimal("50.00"),
                "a@x.com",
                Instant.now());

        inventoryService.handleOrderCreated(event);

        ArgumentCaptor<InventoryProcessedEvent> captor = ArgumentCaptor.forClass(InventoryProcessedEvent.class);
        verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq("inventory-events"), org.mockito.ArgumentMatchers.eq("ORD-1"), captor.capture());
        assertFalse(captor.getValue().reserved());
        assertEquals("Inventory SKU not found", captor.getValue().message());
        assertEquals(0, captor.getValue().availableQuantity());
    }

    @Test
    void handleOrderCreated_publishesFailedEvent_whenInsufficient() {
        Inventory existing = inventory("SKU-1", 1);
        when(inventoryRepository.findBySkuCode("SKU-1")).thenReturn(Optional.of(existing));
        OrderCreatedEvent event = new OrderCreatedEvent(
                "ORD-2",
                "SKU-1",
                3,
                "Product",
                new BigDecimal("70.00"),
                "a@x.com",
                Instant.now());

        inventoryService.handleOrderCreated(event);

        ArgumentCaptor<InventoryProcessedEvent> captor = ArgumentCaptor.forClass(InventoryProcessedEvent.class);
        verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq("inventory-events"), org.mockito.ArgumentMatchers.eq("ORD-2"), captor.capture());
        assertFalse(captor.getValue().reserved());
        assertEquals("Insufficient inventory", captor.getValue().message());
        assertEquals(1, captor.getValue().availableQuantity());
    }

    @Test
    void handleOrderCreated_reservesAndPublishesSuccessEvent_whenSufficient() {
        Inventory existing = inventory("SKU-1", 8);
        when(inventoryRepository.findBySkuCode("SKU-1")).thenReturn(Optional.of(existing));
        OrderCreatedEvent event = new OrderCreatedEvent(
                "ORD-3",
                "SKU-1",
                2,
                "Product",
                new BigDecimal("70.00"),
                "a@x.com",
                Instant.now());

        inventoryService.handleOrderCreated(event);

        ArgumentCaptor<InventoryProcessedEvent> captor = ArgumentCaptor.forClass(InventoryProcessedEvent.class);
        verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq("inventory-events"), org.mockito.ArgumentMatchers.eq("ORD-3"), captor.capture());
        assertTrue(captor.getValue().reserved());
        assertEquals("Inventory reserved", captor.getValue().message());
        assertEquals(6, captor.getValue().availableQuantity());
        assertEquals(6, existing.getQuantity());
    }

    private Inventory inventory(String skuCode, int quantity) {
        Inventory inventory = new Inventory();
        inventory.setSkuCode(skuCode);
        inventory.setQuantity(quantity);
        return inventory;
    }
}
