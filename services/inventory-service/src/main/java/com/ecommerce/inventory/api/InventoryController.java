package com.ecommerce.inventory.api;

import com.ecommerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@Validated
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public List<InventoryResponse> getAllInventory() {
        return inventoryService.getAllInventory();
    }

    @GetMapping("/{skuCode}")
    public InventoryResponse getInventory(@PathVariable("skuCode") @NotBlank(message = "skuCode is required") String skuCode) {
        return inventoryService.getInventory(skuCode);
    }

    @PutMapping("/{skuCode}")
    public InventoryResponse updateInventory(
            @PathVariable("skuCode") @NotBlank(message = "skuCode is required") String skuCode,
            @RequestBody @Valid UpdateInventoryRequest request) {
        return inventoryService.updateInventory(skuCode, request.quantity());
    }

    @PostMapping("/reserve")
    public InventoryResponse reserve(@RequestBody @Valid ReserveInventoryRequest request) {
        return inventoryService.reserveInventory(request);
    }
}
