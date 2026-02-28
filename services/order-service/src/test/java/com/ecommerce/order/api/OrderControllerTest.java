package com.ecommerce.order.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.order.service.OrderPlacementException;
import com.ecommerce.order.service.OrderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@Import(ApiExceptionHandler.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    void getOrders_returnsRows() throws Exception {
        when(orderService.getOrders()).thenReturn(List.of(response("ORD-1", "PENDING", null)));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderNumber").value("ORD-1"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getOrderByNumber_returnsSingleRow() throws Exception {
        when(orderService.getOrderByNumber("ORD-1")).thenReturn(response("ORD-1", "CONFIRMED", null));

        mockMvc.perform(get("/api/orders/ORD-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("ORD-1"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void placeOrder_returnsCreated() throws Exception {
        when(orderService.placeOrder(any(OrderRequest.class))).thenReturn(response("ORD-2", "PENDING", null));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skuCode":"SKU-1","quantity":1,"customerEmail":"user@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value("ORD-2"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void placeOrder_returnsBadRequest_whenServiceThrows() throws Exception {
        when(orderService.placeOrder(any(OrderRequest.class)))
                .thenThrow(new OrderPlacementException("Quantity must be greater than zero"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skuCode":"SKU-1","quantity":0,"customerEmail":"user@example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ORDER_PLACEMENT_FAILED"))
                .andExpect(jsonPath("$.message").value("Quantity must be greater than zero"));
    }

    private OrderResponse response(String orderNumber, String status, String failureReason) {
        return new OrderResponse(
                orderNumber,
                "SKU-1",
                "Keyboard",
                1,
                new BigDecimal("99.00"),
                new BigDecimal("99.00"),
                "user@example.com",
                status,
                failureReason,
                Instant.parse("2026-02-28T12:00:00Z"),
                Instant.parse("2026-02-28T12:00:01Z"));
    }
}
