package com.ecommerce.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.order.api.OrderRequest;
import com.ecommerce.order.api.OrderResponse;
import com.ecommerce.order.client.ProductResponse;
import com.ecommerce.order.event.InventoryProcessedEvent;
import com.ecommerce.order.event.OrderCreatedEvent;
import com.ecommerce.order.event.OrderLifecycleEvent;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.model.CustomerOrder;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.outbox.OutboxService;
import com.ecommerce.order.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductCatalogClient productCatalogClient;

    @Mock
    private OutboxService outboxService;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Spy
    private OrderMapper orderMapper = Mappers.getMapper(OrderMapper.class);

    @InjectMocks
    private OrderService orderService;

    @Test
    void getOrders_mapsRepositoryRows() {
        CustomerOrder order = baseOrder();
        when(orderRepository.findAll()).thenReturn(List.of(order));

        List<OrderResponse> responses = orderService.getOrders();

        assertEquals(1, responses.size());
        assertEquals(order.getOrderNumber(), responses.get(0).orderNumber());
        assertEquals(order.getSkuCode(), responses.get(0).skuCode());
    }

    @Test
    void getOrderByNumber_returnsRow_whenPresent() {
        CustomerOrder order = baseOrder();
        when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderByNumber("ORD-1");

        assertEquals("ORD-1", response.orderNumber());
    }

    @Test
    void getOrderByNumber_throws_whenMissing() {
        when(orderRepository.findByOrderNumber("MISSING")).thenReturn(Optional.empty());

        OrderPlacementException ex = assertThrows(
                OrderPlacementException.class,
                () -> orderService.getOrderByNumber("MISSING"));

        assertEquals("Order not found for orderNumber MISSING", ex.getMessage());
    }

    @Test
    void placeOrder_throws_whenQuantityInvalid() {
        OrderRequest request = new OrderRequest("SKU-1", 0, "user@example.com");

        OrderPlacementException ex = assertThrows(
                OrderPlacementException.class,
                () -> orderService.placeOrder(request));

        assertEquals("Quantity must be greater than zero", ex.getMessage());
        verify(orderRepository, never()).save(any(CustomerOrder.class));
    }

    @Test
    void placeOrder_throws_whenEmailMissing() {
        OrderRequest request = new OrderRequest("SKU-1", 1, " ");

        OrderPlacementException ex = assertThrows(
                OrderPlacementException.class,
                () -> orderService.placeOrder(request));

        assertEquals("Customer email is required", ex.getMessage());
        verify(orderRepository, never()).save(any(CustomerOrder.class));
    }

    @Test
    void placeOrder_throws_whenProductServiceFails() {
        OrderRequest request = new OrderRequest("SKU-1", 1, "user@example.com");
        when(productCatalogClient.getProductBySku("SKU-1"))
                .thenThrow(new ProductServiceUnavailableException("down", new RuntimeException("down")));

        OrderPlacementException ex = assertThrows(
                OrderPlacementException.class,
                () -> orderService.placeOrder(request));

        assertEquals("Product service is temporarily unavailable. Please retry.", ex.getMessage());
    }

    @Test
    void placeOrder_throws_whenProductMissing() {
        OrderRequest request = new OrderRequest("SKU-1", 1, "user@example.com");
        when(productCatalogClient.getProductBySku("SKU-1"))
                .thenThrow(new ProductNotFoundException("Product not found for sku SKU-1"));

        OrderPlacementException ex = assertThrows(
                OrderPlacementException.class,
                () -> orderService.placeOrder(request));

        assertEquals("Product not found for sku SKU-1", ex.getMessage());
    }

    @Test
    void placeOrder_savesPendingOrder_andEnqueuesCreatedEvent() {
        OrderRequest request = new OrderRequest("SKU-1", 2, "user@example.com");
        ProductResponse productResponse = new ProductResponse(
                1L,
                "SKU-1",
                "Keyboard",
                "Mechanical",
                new BigDecimal("120.00"));

        when(productCatalogClient.getProductBySku("SKU-1"))
                .thenReturn(productResponse);
        when(orderRepository.save(any(CustomerOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.placeOrder(request);

        assertEquals(OrderStatus.PENDING.name(), response.status());
        assertEquals(new BigDecimal("240.00"), response.totalPrice());
        assertNotNull(response.orderNumber());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService)
                .enqueue(
                        eq("ORDER"),
                        eq(response.orderNumber()),
                        eq("order-created-events"),
                        eq(response.orderNumber()),
                        eventCaptor.capture());
        OrderCreatedEvent outboxEvent = (OrderCreatedEvent) eventCaptor.getValue();
        assertEquals(response.orderNumber(), outboxEvent.orderNumber());
        assertEquals("SKU-1", outboxEvent.skuCode());
        assertEquals(2, outboxEvent.quantity());
    }

    @Test
    void handleInventoryEvent_ignoresUnknownOrder() {
        when(orderRepository.findByOrderNumber("ORD-404")).thenReturn(Optional.empty());
        InventoryProcessedEvent event = new InventoryProcessedEvent(
                "ORD-404",
                "SKU-1",
                1,
                0,
                false,
                "Missing",
                Instant.now());

        orderService.handleInventoryEvent(event);

        verify(orderRepository, never()).save(any(CustomerOrder.class));
        verify(outboxService, never()).enqueue(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void handleInventoryEvent_ignoresAlreadyFinalizedOrder() {
        CustomerOrder order = baseOrder();
        order.setStatus(OrderStatus.CONFIRMED.name());
        when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        InventoryProcessedEvent event = new InventoryProcessedEvent(
                "ORD-1",
                "SKU-1",
                1,
                5,
                true,
                "Inventory reserved",
                Instant.now());

        orderService.handleInventoryEvent(event);

        verify(orderRepository, never()).save(any(CustomerOrder.class));
        verify(outboxService, never()).enqueue(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void handleInventoryEvent_confirmsOrder_whenReserved() {
        CustomerOrder order = baseOrder();
        order.setStatus(OrderStatus.PENDING.name());
        when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryProcessedEvent event = new InventoryProcessedEvent(
                "ORD-1",
                "SKU-1",
                1,
                12,
                true,
                "Inventory reserved",
                Instant.now());

        orderService.handleInventoryEvent(event);

        ArgumentCaptor<CustomerOrder> saveCaptor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orderRepository).save(saveCaptor.capture());
        assertEquals(OrderStatus.CONFIRMED.name(), saveCaptor.getValue().getStatus());
        assertEquals(null, saveCaptor.getValue().getFailureReason());
        assertNotNull(saveCaptor.getValue().getUpdatedAt());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService)
                .enqueue(eq("ORDER"), eq("ORD-1"), eq("order-events"), eq("ORD-1"), eventCaptor.capture());
        OrderLifecycleEvent outboxEvent = (OrderLifecycleEvent) eventCaptor.getValue();
        assertEquals(OrderStatus.CONFIRMED.name(), outboxEvent.orderStatus());
        assertTrue(outboxEvent.message().contains("Remaining quantity: 12"));
    }

    @Test
    void handleInventoryEvent_cancelsOrder_whenReservationFails() {
        CustomerOrder order = baseOrder();
        order.setStatus(OrderStatus.PENDING.name());
        when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryProcessedEvent event = new InventoryProcessedEvent(
                "ORD-1",
                "SKU-1",
                2,
                0,
                false,
                "Insufficient inventory",
                Instant.now());

        orderService.handleInventoryEvent(event);

        ArgumentCaptor<CustomerOrder> saveCaptor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orderRepository).save(saveCaptor.capture());
        assertEquals(OrderStatus.CANCELLED.name(), saveCaptor.getValue().getStatus());
        assertEquals("Insufficient inventory", saveCaptor.getValue().getFailureReason());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService)
                .enqueue(eq("ORDER"), eq("ORD-1"), eq("order-events"), eq("ORD-1"), eventCaptor.capture());
        OrderLifecycleEvent outboxEvent = (OrderLifecycleEvent) eventCaptor.getValue();
        assertEquals(OrderStatus.CANCELLED.name(), outboxEvent.orderStatus());
        assertEquals("Insufficient inventory", outboxEvent.message());
    }

    private CustomerOrder baseOrder() {
        CustomerOrder order = new CustomerOrder();
        order.setOrderNumber("ORD-1");
        order.setSkuCode("SKU-1");
        order.setProductName("Keyboard");
        order.setQuantity(1);
        order.setUnitPrice(new BigDecimal("99.00"));
        order.setTotalPrice(new BigDecimal("99.00"));
        order.setCustomerEmail("user@example.com");
        order.setCreatedAt(Instant.parse("2026-02-28T12:00:00Z"));
        order.setUpdatedAt(Instant.parse("2026-02-28T12:00:00Z"));
        order.setStatus(OrderStatus.PENDING.name());
        order.setFailureReason(null);
        return order;
    }
}
