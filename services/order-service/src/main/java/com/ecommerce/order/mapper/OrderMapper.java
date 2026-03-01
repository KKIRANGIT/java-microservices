package com.ecommerce.order.mapper;

import com.ecommerce.order.api.OrderRequest;
import com.ecommerce.order.api.OrderResponse;
import com.ecommerce.order.client.ProductResponse;
import com.ecommerce.order.model.CustomerOrder;
import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface OrderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderNumber", source = "orderNumber")
    @Mapping(target = "skuCode", source = "product.skuCode")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "quantity", source = "request.quantity")
    @Mapping(target = "unitPrice", source = "product.price")
    @Mapping(target = "totalPrice", expression = "java(product.price().multiply(java.math.BigDecimal.valueOf(request.quantity())))")
    @Mapping(target = "customerEmail", source = "request.customerEmail")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "createdAt", source = "now")
    @Mapping(target = "updatedAt", source = "now")
    CustomerOrder toPendingOrder(
            OrderRequest request,
            ProductResponse product,
            String orderNumber,
            Instant now,
            String status);

    OrderResponse toResponse(CustomerOrder order);
}
