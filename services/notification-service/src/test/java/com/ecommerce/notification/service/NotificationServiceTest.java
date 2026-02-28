package com.ecommerce.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.notification.api.NotificationResponse;
import com.ecommerce.notification.event.OrderLifecycleEvent;
import com.ecommerce.notification.model.NotificationEvent;
import com.ecommerce.notification.repository.NotificationEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationEventRepository notificationEventRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void recordNotification_createsNewRow_whenOrderNotSeenBefore() {
        when(notificationEventRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.empty());
        when(notificationEventRepository.save(any(NotificationEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Instant before = Instant.now();
        notificationService.recordNotification(lifecycleEvent("ORD-1", "CONFIRMED", null, null));
        Instant after = Instant.now();

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventRepository).save(captor.capture());
        NotificationEvent saved = captor.getValue();
        assertEquals("ORD-1", saved.getOrderNumber());
        assertEquals("CONFIRMED", saved.getStatus());
        assertNotNull(saved.getProcessedAt());
        assertTrue(!saved.getProcessedAt().isBefore(before) && !saved.getProcessedAt().isAfter(after));
    }

    @Test
    void recordNotification_updatesExistingRow_andUsesOccurredAtWhenProvided() {
        NotificationEvent existing = new NotificationEvent();
        existing.setOrderNumber("ORD-2");
        when(notificationEventRepository.findByOrderNumber("ORD-2")).thenReturn(Optional.of(existing));

        Instant occurredAt = Instant.parse("2026-02-28T10:00:00Z");
        notificationService.recordNotification(lifecycleEvent("ORD-2", "CANCELLED", "Insufficient inventory", occurredAt));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventRepository).save(captor.capture());
        NotificationEvent saved = captor.getValue();
        assertEquals("CANCELLED", saved.getStatus());
        assertEquals("Insufficient inventory", saved.getMessage());
        assertEquals(occurredAt, saved.getProcessedAt());
    }

    @Test
    void getRecentNotifications_clampsLimitToMax100() {
        NotificationEvent event = notificationEvent("ORD-10");
        when(notificationEventRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(event)));

        List<NotificationResponse> responses = notificationService.getRecentNotifications(500);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationEventRepository).findAll(pageableCaptor.capture());
        assertEquals(100, pageableCaptor.getValue().getPageSize());
        assertEquals(1, responses.size());
        assertEquals("ORD-10", responses.get(0).orderNumber());
    }

    @Test
    void getRecentNotifications_clampsLimitToMin1() {
        when(notificationEventRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        notificationService.getRecentNotifications(0);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationEventRepository).findAll(pageableCaptor.capture());
        assertEquals(1, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void getByOrderNumber_returnsOptionalResponse() {
        NotificationEvent event = notificationEvent("ORD-11");
        when(notificationEventRepository.findByOrderNumber("ORD-11")).thenReturn(Optional.of(event));

        Optional<NotificationResponse> response = notificationService.getByOrderNumber("ORD-11");

        assertTrue(response.isPresent());
        assertEquals("ORD-11", response.get().orderNumber());
    }

    private OrderLifecycleEvent lifecycleEvent(String orderNumber, String status, String message, Instant occurredAt) {
        return new OrderLifecycleEvent(
                orderNumber,
                "SKU-1",
                "Keyboard",
                1,
                new BigDecimal("99.00"),
                "user@example.com",
                status,
                message,
                Instant.parse("2026-02-28T09:00:00Z"),
                occurredAt);
    }

    private NotificationEvent notificationEvent(String orderNumber) {
        NotificationEvent event = new NotificationEvent();
        event.setOrderNumber(orderNumber);
        event.setSkuCode("SKU-1");
        event.setProductName("Keyboard");
        event.setQuantity(1);
        event.setTotalPrice(new BigDecimal("99.00"));
        event.setCustomerEmail("user@example.com");
        event.setOrderCreatedAt(Instant.parse("2026-02-28T09:00:00Z"));
        event.setStatus("CONFIRMED");
        event.setMessage("ok");
        event.setProcessedAt(Instant.parse("2026-02-28T10:00:00Z"));
        return event;
    }
}
