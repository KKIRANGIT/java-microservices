package com.ecommerce.notification.api;

import com.ecommerce.notification.service.NotificationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> getRecentNotifications(
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return notificationService.getRecentNotifications(limit);
    }

    @GetMapping("/{orderNumber}")
    public NotificationResponse getByOrderNumber(@PathVariable("orderNumber") String orderNumber) {
        return notificationService.getByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Notification event not found for order " + orderNumber));
    }
}
