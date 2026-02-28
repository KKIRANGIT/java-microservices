package com.ecommerce.inventory.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.inventory.event.InventoryProcessedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @Test
    void publishPending_marksEventAsPublished_whenKafkaSendSucceeds() throws Exception {
        OutboxEvent outboxEvent = pendingEvent();
        InventoryProcessedEvent payload = samplePayload();
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(outboxEvent));
        when(objectMapper.readValue(eq(outboxEvent.getPayload()), eq(InventoryProcessedEvent.class))).thenReturn(payload);
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq(outboxEvent.getTopic()), eq(outboxEvent.getEventKey()), eq(payload)))
                .thenReturn(future);

        outboxPublisher.publishPending();

        verify(kafkaTemplate).send(eq("inventory-events"), eq("ORD-1"), eq(payload));
        assertEquals(OutboxStatus.PUBLISHED, outboxEvent.getStatus());
        assertNotNull(outboxEvent.getPublishedAt());
        assertNull(outboxEvent.getLastError());
        assertEquals(0, outboxEvent.getAttempts());
    }

    @Test
    void publishPending_keepsEventPendingAndIncrementsAttempts_whenKafkaSendFails() throws Exception {
        OutboxEvent outboxEvent = pendingEvent();
        InventoryProcessedEvent payload = samplePayload();
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(outboxEvent));
        when(objectMapper.readValue(eq(outboxEvent.getPayload()), eq(InventoryProcessedEvent.class))).thenReturn(payload);
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(eq(outboxEvent.getTopic()), eq(outboxEvent.getEventKey()), eq(payload)))
                .thenReturn(failedFuture);

        outboxPublisher.publishPending();

        assertEquals(OutboxStatus.PENDING, outboxEvent.getStatus());
        assertEquals(1, outboxEvent.getAttempts());
        assertTrue(outboxEvent.getLastError().contains("broker down"));
    }

    private OutboxEvent pendingEvent() {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(1L);
        outboxEvent.setAggregateType("INVENTORY");
        outboxEvent.setAggregateId("ORD-1");
        outboxEvent.setTopic("inventory-events");
        outboxEvent.setEventKey("ORD-1");
        outboxEvent.setEventType(InventoryProcessedEvent.class.getName());
        outboxEvent.setPayload("{\"orderNumber\":\"ORD-1\"}");
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setAttempts(0);
        outboxEvent.setCreatedAt(Instant.parse("2026-02-28T12:00:00Z"));
        return outboxEvent;
    }

    private InventoryProcessedEvent samplePayload() {
        return new InventoryProcessedEvent(
                "ORD-1",
                "SKU-1",
                2,
                8,
                true,
                "Inventory reserved",
                Instant.parse("2026-02-28T12:00:01Z"));
    }
}
