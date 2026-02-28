package com.ecommerce.notification.api;

import com.ecommerce.notification.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/notifications")
@Validated
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> getRecentNotifications(
            @RequestParam(name = "limit", defaultValue = "20")
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 100, message = "limit must be at most 100")
            int limit) {
        return notificationService.getRecentNotifications(limit);
    }

    @GetMapping("/{orderNumber}")
    public NotificationResponse getByOrderNumber(
            @PathVariable("orderNumber") @NotBlank(message = "orderNumber is required") String orderNumber) {
        return notificationService.getByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Notification event not found for order " + orderNumber));
    }
}
