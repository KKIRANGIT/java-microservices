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
import com.ecommerce.inventory.outbox.OutboxService;
import com.ecommerce.inventory.repository.InventoryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private OutboxService outboxService;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

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
    void updateInventory_updatesExistingRow() {
        Inventory existing = inventory("SKU-1", 5);
        when(inventoryRepository.findBySkuCode("SKU-1")).thenReturn(Optional.of(existing));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryResponse response = inventoryService.updateInventory("SKU-1", 22);

        assertEquals("SKU-1", response.skuCode());
        assertTrue(response.available());
        assertEquals(22, response.quantity());
        assertEquals(22, existing.getQuantity());
    }

    @Test
    void updateInventory_createsInventoryRow_whenMissing() {
        when(inventoryRepository.findBySkuCode("SKU-NEW")).thenReturn(Optional.empty());
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryResponse response = inventoryService.updateInventory("SKU-NEW", 0);

        assertEquals("SKU-NEW", response.skuCode());
        assertFalse(response.available());
        assertEquals(0, response.quantity());
    }

    @Test
    void handleOrderCreated_enqueuesFailedEvent_whenSkuMissing() {
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

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService)
                .enqueue(
                        org.mockito.ArgumentMatchers.eq("INVENTORY"),
                        org.mockito.ArgumentMatchers.eq("ORD-1"),
                        org.mockito.ArgumentMatchers.eq("inventory-events"),
                        org.mockito.ArgumentMatchers.eq("ORD-1"),
                        captor.capture());
        InventoryProcessedEvent outboxEvent = (InventoryProcessedEvent) captor.getValue();
        assertFalse(outboxEvent.reserved());
        assertEquals("Inventory SKU not found", outboxEvent.message());
        assertEquals(0, outboxEvent.availableQuantity());
    }

    @Test
    void handleOrderCreated_enqueuesFailedEvent_whenInsufficient() {
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

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService)
                .enqueue(
                        org.mockito.ArgumentMatchers.eq("INVENTORY"),
                        org.mockito.ArgumentMatchers.eq("ORD-2"),
                        org.mockito.ArgumentMatchers.eq("inventory-events"),
                        org.mockito.ArgumentMatchers.eq("ORD-2"),
                        captor.capture());
        InventoryProcessedEvent outboxEvent = (InventoryProcessedEvent) captor.getValue();
        assertFalse(outboxEvent.reserved());
        assertEquals("Insufficient inventory", outboxEvent.message());
        assertEquals(1, outboxEvent.availableQuantity());
    }

    @Test
    void handleOrderCreated_reservesAndEnqueuesSuccessEvent_whenSufficient() {
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

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService)
                .enqueue(
                        org.mockito.ArgumentMatchers.eq("INVENTORY"),
                        org.mockito.ArgumentMatchers.eq("ORD-3"),
                        org.mockito.ArgumentMatchers.eq("inventory-events"),
                        org.mockito.ArgumentMatchers.eq("ORD-3"),
                        captor.capture());
        InventoryProcessedEvent outboxEvent = (InventoryProcessedEvent) captor.getValue();
        assertTrue(outboxEvent.reserved());
        assertEquals("Inventory reserved", outboxEvent.message());
        assertEquals(6, outboxEvent.availableQuantity());
        assertEquals(6, existing.getQuantity());
    }

    private Inventory inventory(String skuCode, int quantity) {
        Inventory inventory = new Inventory();
        inventory.setSkuCode(skuCode);
        inventory.setQuantity(quantity);
        return inventory;
    }
}
