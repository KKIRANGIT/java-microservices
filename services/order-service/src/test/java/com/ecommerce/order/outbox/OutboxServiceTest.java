package com.ecommerce.order.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.order.event.OrderCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
        OrderCreatedEvent event = new OrderCreatedEvent(
                "ORD-1",
                "SKU-1",
                1,
                "Keyboard",
                new BigDecimal("99.00"),
                "user@example.com",
                Instant.parse("2026-02-28T12:00:00Z"));
        when(objectMapper.writeValueAsString(event)).thenReturn("{\"orderNumber\":\"ORD-1\"}");

        outboxService.enqueue("ORDER", "ORD-1", "order-created-events", "ORD-1", event);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent outboxEvent = outboxCaptor.getValue();
        assertEquals("ORDER", outboxEvent.getAggregateType());
        assertEquals("ORD-1", outboxEvent.getAggregateId());
        assertEquals("order-created-events", outboxEvent.getTopic());
        assertEquals("ORD-1", outboxEvent.getEventKey());
        assertEquals(OrderCreatedEvent.class.getName(), outboxEvent.getEventType());
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
                () -> outboxService.enqueue("ORDER", "ORD-1", "order-created-events", "ORD-1", new Object()));

        assertEquals("Unable to serialize outbox payload", ex.getMessage());
    }
}
