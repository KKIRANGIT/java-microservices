package com.ecommerce.order.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

class KafkaTopicConfigTest {

    private final KafkaTopicConfig config = new KafkaTopicConfig();

    @Test
    void orderCreatedTopic_hasExpectedDefinition() {
        NewTopic topic = config.orderCreatedTopic();
        assertEquals("order-created-events", topic.name());
        assertEquals(1, topic.numPartitions());
        assertEquals(1, topic.replicationFactor());
    }

    @Test
    void inventoryEventsTopic_hasExpectedDefinition() {
        NewTopic topic = config.inventoryEventsTopic();
        assertEquals("inventory-events", topic.name());
        assertEquals(1, topic.numPartitions());
        assertEquals(1, topic.replicationFactor());
    }

    @Test
    void orderEventsTopic_hasExpectedDefinition() {
        NewTopic topic = config.orderEventsTopic();
        assertEquals("order-events", topic.name());
        assertEquals(1, topic.numPartitions());
        assertEquals(1, topic.replicationFactor());
    }
}
