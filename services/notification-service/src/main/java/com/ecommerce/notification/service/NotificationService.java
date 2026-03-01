package com.ecommerce.notification.service;

import com.ecommerce.notification.api.NotificationResponse;
import com.ecommerce.notification.event.OrderLifecycleEvent;
import com.ecommerce.notification.mapper.NotificationMapper;
import com.ecommerce.notification.model.NotificationEvent;
import com.ecommerce.notification.repository.NotificationEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class NotificationService {

    private final NotificationEventRepository notificationEventRepository;
    private final MeterRegistry meterRegistry;
    private final NotificationMapper notificationMapper;

    public NotificationService(
            NotificationEventRepository notificationEventRepository,
            MeterRegistry meterRegistry,
            NotificationMapper notificationMapper) {
        this.notificationEventRepository = notificationEventRepository;
        this.meterRegistry = meterRegistry;
        this.notificationMapper = notificationMapper;
    }

    @Transactional
    public void recordNotification(OrderLifecycleEvent event) {
        NotificationEvent notificationEvent = notificationEventRepository
                .findByOrderNumber(event.orderNumber())
                .orElseGet(NotificationEvent::new);
        notificationMapper.updateFromEvent(event, notificationEvent);

        notificationEventRepository.save(notificationEvent);
        meterRegistry.counter("ecommerce.notifications.processed", "status", event.orderStatus()).increment();
    }

    public List<NotificationResponse> getRecentNotifications(
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 100, message = "limit must be at most 100")
            int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return notificationEventRepository
                .findAll(PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "processedAt")))
                .stream()
                .map(notificationMapper::toResponse)
                .toList();
    }

    public Optional<NotificationResponse> getByOrderNumber(
            @NotBlank(message = "orderNumber is required") String orderNumber) {
        return notificationEventRepository.findByOrderNumber(orderNumber).map(notificationMapper::toResponse);
    }
}
