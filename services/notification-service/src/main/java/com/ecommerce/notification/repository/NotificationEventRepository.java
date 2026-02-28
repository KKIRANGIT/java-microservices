package com.ecommerce.notification.repository;

import com.ecommerce.notification.model.NotificationEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {

    Optional<NotificationEvent> findByOrderNumber(String orderNumber);
}
