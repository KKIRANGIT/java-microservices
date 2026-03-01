package com.ecommerce.product.service;

import com.ecommerce.product.api.ProductRequest;
import com.ecommerce.product.api.ProductResponse;
import com.ecommerce.product.mapper.ProductMapper;
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
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }

    public ProductResponse createProduct(@Valid ProductRequest request) {
        Product product = productMapper.toEntity(request);
        return productMapper.toResponse(productRepository.save(product));
    }

    public ProductResponse getBySkuCode(@NotBlank(message = "skuCode is required") String skuCode) {
        Product product = productRepository
                .findBySkuCode(skuCode)
                .orElseThrow(() -> new IllegalArgumentException("Product not found for sku " + skuCode));
        return productMapper.toResponse(product);
    }
}
