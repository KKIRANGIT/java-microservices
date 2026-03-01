package com.ecommerce.inventory.mapper;

import com.ecommerce.inventory.api.InventoryResponse;
import com.ecommerce.inventory.model.Inventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface InventoryMapper {

    @Mapping(target = "available", expression = "java(inventory.getQuantity() != null && inventory.getQuantity() > 0)")
    InventoryResponse toInventoryResponse(Inventory inventory);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "skuCode", source = "skuCode")
    @Mapping(target = "quantity", source = "quantity")
    Inventory toInventory(String skuCode, int quantity);

    default InventoryResponse toReservationResponse(String skuCode, boolean available, int quantity) {
        return new InventoryResponse(skuCode, available, quantity);
    }
}
