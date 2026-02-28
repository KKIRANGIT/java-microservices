package com.ecommerce.notification.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.notification.service.NotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@Import(NotificationApiExceptionHandler.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    void getRecentNotifications_returnsRows() throws Exception {
        when(notificationService.getRecentNotifications(20)).thenReturn(List.of(response("ORD-1")));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderNumber").value("ORD-1"))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));

        verify(notificationService).getRecentNotifications(20);
    }

    @Test
    void getRecentNotifications_forwardsCustomLimit() throws Exception {
        when(notificationService.getRecentNotifications(5)).thenReturn(List.of(response("ORD-2")));

        mockMvc.perform(get("/api/notifications?limit=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderNumber").value("ORD-2"));

        verify(notificationService).getRecentNotifications(5);
    }

    @Test
    void getByOrderNumber_returnsRow() throws Exception {
        when(notificationService.getByOrderNumber("ORD-3")).thenReturn(Optional.of(response("ORD-3")));

        mockMvc.perform(get("/api/notifications/ORD-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("ORD-3"));
    }

    @Test
    void getByOrderNumber_returnsNotFound_whenMissing() throws Exception {
        when(notificationService.getByOrderNumber("MISSING")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/notifications/MISSING"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRecentNotifications_returnsBadRequest_whenLimitInvalid() throws Exception {
        mockMvc.perform(get("/api/notifications?limit=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());

        verifyNoInteractions(notificationService);
    }

    private NotificationResponse response(String orderNumber) {
        return new NotificationResponse(
                orderNumber,
                "SKU-1",
                "Keyboard",
                1,
                new BigDecimal("99.00"),
                "user@example.com",
                Instant.parse("2026-02-28T09:00:00Z"),
                "CONFIRMED",
                "Inventory reserved",
                Instant.parse("2026-02-28T09:00:01Z"));
    }
}
