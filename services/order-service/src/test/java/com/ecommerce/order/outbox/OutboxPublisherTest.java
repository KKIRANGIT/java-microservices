package com.ecommerce.order.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.order.event.OrderCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @Test
    void publishPending_marksEventAsPublished_whenKafkaSendSucceeds() throws Exception {
        OutboxEvent outboxEvent = pendingEvent();
        OrderCreatedEvent payload = samplePayload();
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(outboxEvent));
        when(objectMapper.readValue(eq(outboxEvent.getPayload()), eq(OrderCreatedEvent.class))).thenReturn(payload);
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq(outboxEvent.getTopic()), eq(outboxEvent.getEventKey()), eq(payload)))
                .thenReturn(future);

        outboxPublisher.publishPending();

        verify(kafkaTemplate).send(eq("order-created-events"), eq("ORD-1"), eq(payload));
        assertEquals(OutboxStatus.PUBLISHED, outboxEvent.getStatus());
        assertNotNull(outboxEvent.getPublishedAt());
        assertNull(outboxEvent.getLastError());
        assertEquals(0, outboxEvent.getAttempts());
    }

    @Test
    void publishPending_keepsEventPendingAndIncrementsAttempts_whenKafkaSendFails() throws Exception {
        OutboxEvent outboxEvent = pendingEvent();
        OrderCreatedEvent payload = samplePayload();
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(outboxEvent));
        when(objectMapper.readValue(eq(outboxEvent.getPayload()), eq(OrderCreatedEvent.class))).thenReturn(payload);
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
        outboxEvent.setAggregateType("ORDER");
        outboxEvent.setAggregateId("ORD-1");
        outboxEvent.setTopic("order-created-events");
        outboxEvent.setEventKey("ORD-1");
        outboxEvent.setEventType(OrderCreatedEvent.class.getName());
        outboxEvent.setPayload("{\"orderNumber\":\"ORD-1\"}");
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setAttempts(0);
        outboxEvent.setCreatedAt(Instant.parse("2026-02-28T12:00:00Z"));
        return outboxEvent;
    }

    private OrderCreatedEvent samplePayload() {
        return new OrderCreatedEvent(
                "ORD-1",
                "SKU-1",
                1,
                "Keyboard",
                new BigDecimal("99.00"),
                "user@example.com",
                Instant.parse("2026-02-28T12:00:00Z"));
    }
}
