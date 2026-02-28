package com.ecommerce.notification.service;

import static org.mockito.Mockito.verify;

import com.ecommerce.notification.event.OrderLifecycleEvent;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderNotificationConsumerTest {

    @Test
    void handleOrderEvent_delegatesToNotificationService() {
        NotificationService notificationService = Mockito.mock(NotificationService.class);
        OrderNotificationConsumer consumer = new OrderNotificationConsumer(notificationService);
        OrderLifecycleEvent event = new OrderLifecycleEvent(
                "ORD-1",
                "SKU-1",
                "Keyboard",
                1,
                new BigDecimal("99.00"),
                "user@example.com",
                "CONFIRMED",
                "Inventory reserved",
                Instant.parse("2026-02-28T09:00:00Z"),
                Instant.parse("2026-02-28T09:00:01Z"));

        consumer.handleOrderEvent(event);

        verify(notificationService).recordNotification(event);
    }
}
