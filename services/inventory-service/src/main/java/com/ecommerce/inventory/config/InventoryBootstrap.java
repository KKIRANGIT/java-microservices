package com.ecommerce.inventory.config;

import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InventoryBootstrap {

    @Bean
    CommandLineRunner seedInventory(InventoryRepository inventoryRepository) {
        return args -> {
            if (inventoryRepository.count() > 0) {
                return;
            }

            inventoryRepository.save(build("LAPTOP-ACER-001", 30));
            inventoryRepository.save(build("HEADPHONES-SONY-001", 50));
            inventoryRepository.save(build("KEYBOARD-LOGI-001", 80));
        };
    }

    private Inventory build(String skuCode, int quantity) {
        Inventory inventory = new Inventory();
        inventory.setSkuCode(skuCode);
        inventory.setQuantity(quantity);
        return inventory;
    }
}
