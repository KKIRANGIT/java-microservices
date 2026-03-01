package com.ecommerce.notification.mapper;

import com.ecommerce.notification.api.NotificationResponse;
import com.ecommerce.notification.event.OrderLifecycleEvent;
import com.ecommerce.notification.model.NotificationEvent;
import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface NotificationMapper {

    @Mapping(target = "status", source = "orderStatus")
    @Mapping(target = "processedAt", expression = "java(resolveProcessedAt(event))")
    void updateFromEvent(OrderLifecycleEvent event, @MappingTarget NotificationEvent target);

    NotificationResponse toResponse(NotificationEvent notificationEvent);

    default Instant resolveProcessedAt(OrderLifecycleEvent event) {
        return event.occurredAt() == null ? Instant.now() : event.occurredAt();
    }
}
