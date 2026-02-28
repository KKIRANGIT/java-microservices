package com.ecommerce.notification.service;

import com.ecommerce.notification.event.OrderLifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderNotificationConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderNotificationConsumer.class);

    private final NotificationService notificationService;

    public OrderNotificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "order-events", groupId = "notification-service")
    public void handleOrderEvent(OrderLifecycleEvent event) {
        notificationService.recordNotification(event);
        LOGGER.info(
                "Notification processed: orderNumber={}, status={}, skuCode={}, message={}",
                event.orderNumber(),
                event.orderStatus(),
                event.skuCode(),
                event.message());
    }
}
