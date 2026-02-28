package com.ecommerce.inventory.api;

import com.ecommerce.inventory.service.InventoryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public List<InventoryResponse> getAllInventory() {
        return inventoryService.getAllInventory();
    }

    @GetMapping("/{skuCode}")
    public InventoryResponse getInventory(@PathVariable("skuCode") String skuCode) {
        return inventoryService.getInventory(skuCode);
    }

    @PutMapping("/{skuCode}")
    public InventoryResponse updateInventory(
            @PathVariable("skuCode") String skuCode,
            @RequestBody UpdateInventoryRequest request) {
        if (request.quantity() < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Quantity must be zero or greater");
        }
        return inventoryService.updateInventory(skuCode, request.quantity());
    }

    @PostMapping("/reserve")
    public InventoryResponse reserve(@RequestBody ReserveInventoryRequest request) {
        return inventoryService.reserveInventory(request);
    }
}
