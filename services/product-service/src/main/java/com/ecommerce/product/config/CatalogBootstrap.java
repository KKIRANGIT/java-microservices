package com.ecommerce.product.config;

import com.ecommerce.product.model.Product;
import com.ecommerce.product.repository.ProductRepository;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogBootstrap {

    @Bean
    CommandLineRunner seedCatalog(ProductRepository productRepository) {
        return args -> {
            if (productRepository.count() > 0) {
                return;
            }

            Product laptop = new Product();
            laptop.setSkuCode("LAPTOP-ACER-001");
            laptop.setName("Acer Nitro 16");
            laptop.setDescription("16 inch gaming laptop with Ryzen processor.");
            laptop.setPrice(new BigDecimal("1099.00"));

            Product headphones = new Product();
            headphones.setSkuCode("HEADPHONES-SONY-001");
            headphones.setName("Sony WH-1000XM5");
            headphones.setDescription("Noise cancelling wireless headphones.");
            headphones.setPrice(new BigDecimal("399.00"));

            Product keyboard = new Product();
            keyboard.setSkuCode("KEYBOARD-LOGI-001");
            keyboard.setName("Logitech MX Mechanical");
            keyboard.setDescription("Low profile wireless mechanical keyboard.");
            keyboard.setPrice(new BigDecimal("179.00"));

            productRepository.save(laptop);
            productRepository.save(headphones);
            productRepository.save(keyboard);
        };
    }
}
