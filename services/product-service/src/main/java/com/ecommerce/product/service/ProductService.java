package com.ecommerce.product.service;

import com.ecommerce.product.api.ProductRequest;
import com.ecommerce.product.api.ProductResponse;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.repository.ProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(ProductService::toResponse)
                .toList();
    }

    public ProductResponse createProduct(@Valid ProductRequest request) {
        Product product = new Product();
        product.setSkuCode(request.skuCode());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        return toResponse(productRepository.save(product));
    }

    public ProductResponse getBySkuCode(@NotBlank(message = "skuCode is required") String skuCode) {
        Product product = productRepository
                .findBySkuCode(skuCode)
                .orElseThrow(() -> new IllegalArgumentException("Product not found for sku " + skuCode));
        return toResponse(product);
    }

    public static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSkuCode(),
                product.getName(),
                product.getDescription(),
                product.getPrice());
    }
}
