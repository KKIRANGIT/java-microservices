package com.ecommerce.notification.service;

import com.ecommerce.notification.event.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderNotificationConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderNotificationConsumer.class);

    @KafkaListener(topics = "order-events", groupId = "notification-service")
    public void handleOrderEvent(OrderPlacedEvent event) {
        LOGGER.info(
                "Order received: orderNumber={}, skuCode={}, quantity={}, email={}, totalPrice={}",
                event.orderNumber(),
                event.skuCode(),
                event.quantity(),
                event.customerEmail(),
                event.totalPrice());
    }
}
