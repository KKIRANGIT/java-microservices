package com.ecommerce.notification.service;

import com.ecommerce.notification.api.NotificationResponse;
import com.ecommerce.notification.event.OrderLifecycleEvent;
import com.ecommerce.notification.model.NotificationEvent;
import com.ecommerce.notification.repository.NotificationEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationEventRepository notificationEventRepository;

    public NotificationService(NotificationEventRepository notificationEventRepository) {
        this.notificationEventRepository = notificationEventRepository;
    }

    @Transactional
    public void recordNotification(OrderLifecycleEvent event) {
        NotificationEvent notificationEvent = notificationEventRepository
                .findByOrderNumber(event.orderNumber())
                .orElseGet(NotificationEvent::new);

        notificationEvent.setOrderNumber(event.orderNumber());
        notificationEvent.setSkuCode(event.skuCode());
        notificationEvent.setProductName(event.productName());
        notificationEvent.setQuantity(event.quantity());
        notificationEvent.setTotalPrice(event.totalPrice());
        notificationEvent.setCustomerEmail(event.customerEmail());
        notificationEvent.setOrderCreatedAt(event.orderCreatedAt());
        notificationEvent.setStatus(event.orderStatus());
        notificationEvent.setMessage(event.message());
        notificationEvent.setProcessedAt(event.occurredAt() == null ? Instant.now() : event.occurredAt());

        notificationEventRepository.save(notificationEvent);
    }

    public List<NotificationResponse> getRecentNotifications(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return notificationEventRepository
                .findAll(PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "processedAt")))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<NotificationResponse> getByOrderNumber(String orderNumber) {
        return notificationEventRepository.findByOrderNumber(orderNumber).map(this::toResponse);
    }

    private NotificationResponse toResponse(NotificationEvent notificationEvent) {
        return new NotificationResponse(
                notificationEvent.getOrderNumber(),
                notificationEvent.getSkuCode(),
                notificationEvent.getProductName(),
                notificationEvent.getQuantity(),
                notificationEvent.getTotalPrice(),
                notificationEvent.getCustomerEmail(),
                notificationEvent.getOrderCreatedAt(),
                notificationEvent.getStatus(),
                notificationEvent.getMessage(),
                notificationEvent.getProcessedAt());
    }
}
