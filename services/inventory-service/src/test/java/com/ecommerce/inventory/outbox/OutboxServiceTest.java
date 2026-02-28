package com.ecommerce.inventory.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.inventory.event.InventoryProcessedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxService outboxService;

    @Test
    void enqueue_savesPendingOutboxRow() throws Exception {
        InventoryProcessedEvent event = new InventoryProcessedEvent(
                "ORD-1",
                "SKU-1",
                2,
                8,
                true,
                "Inventory reserved",
                Instant.parse("2026-02-28T12:00:01Z"));
        when(objectMapper.writeValueAsString(event)).thenReturn("{\"orderNumber\":\"ORD-1\"}");

        outboxService.enqueue("INVENTORY", "ORD-1", "inventory-events", "ORD-1", event);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent outboxEvent = outboxCaptor.getValue();
        assertEquals("INVENTORY", outboxEvent.getAggregateType());
        assertEquals("ORD-1", outboxEvent.getAggregateId());
        assertEquals("inventory-events", outboxEvent.getTopic());
        assertEquals("ORD-1", outboxEvent.getEventKey());
        assertEquals(InventoryProcessedEvent.class.getName(), outboxEvent.getEventType());
        assertEquals("{\"orderNumber\":\"ORD-1\"}", outboxEvent.getPayload());
        assertEquals(OutboxStatus.PENDING, outboxEvent.getStatus());
        assertEquals(0, outboxEvent.getAttempts());
        assertNotNull(outboxEvent.getCreatedAt());
    }

    @Test
    void enqueue_throws_whenSerializationFails() throws Exception {
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("boom") {});

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> outboxService.enqueue("INVENTORY", "ORD-1", "inventory-events", "ORD-1", new Object()));

        assertEquals("Unable to serialize outbox payload", ex.getMessage());
    }
}
