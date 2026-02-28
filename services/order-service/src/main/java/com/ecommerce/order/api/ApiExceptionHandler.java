package com.ecommerce.order.api;

import com.ecommerce.order.service.OrderPlacementException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OrderPlacementException.class)
    public ResponseEntity<Map<String, Object>> handleOrderPlacementException(OrderPlacementException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "timestamp", Instant.now().toString(),
                        "error", "ORDER_PLACEMENT_FAILED",
                        "message", ex.getMessage()));
    }
}
