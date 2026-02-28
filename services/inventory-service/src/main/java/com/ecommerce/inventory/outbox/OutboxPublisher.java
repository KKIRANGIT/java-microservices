package com.ecommerce.inventory.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        Gauge.builder(
                        "ecommerce.outbox.pending.events",
                        outboxEventRepository,
                        repo -> repo.countByStatus(OutboxStatus.PENDING))
                .description("Pending outbox events waiting for publish")
                .tag("service", "inventory-service")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:1500}")
    @Transactional
    public void publishPending() {
        Pageable page = PageRequest.of(0, DEFAULT_BATCH_SIZE);
        List<OutboxEvent> events = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, page);
        for (OutboxEvent event : events) {
            try {
                Object payload = readPayload(event);
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), payload).get(10, TimeUnit.SECONDS);
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                event.setLastError(null);
                meterRegistry.counter(
                                "ecommerce.outbox.publish",
                                "service",
                                "inventory-service",
                                "result",
                                "success",
                                "topic",
                                event.getTopic())
                        .increment();
            } catch (Exception ex) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(safeError(ex.getMessage()));
                meterRegistry.counter(
                                "ecommerce.outbox.publish",
                                "service",
                                "inventory-service",
                                "result",
                                "failure",
                                "topic",
                                event.getTopic())
                        .increment();
                LOGGER.error(
                        "Outbox publish failed: id={}, topic={}, aggregateType={}, aggregateId={}, attempts={}",
                        event.getId(),
                        event.getTopic(),
                        event.getAggregateType(),
                        event.getAggregateId(),
                        event.getAttempts(),
                        ex);
            }
        }
    }

    private Object readPayload(OutboxEvent event) throws Exception {
        Class<?> eventClass = Class.forName(event.getEventType());
        return objectMapper.readValue(event.getPayload(), eventClass);
    }

    private String safeError(String error) {
        if (error == null || error.isBlank()) {
            return "Unknown error";
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
