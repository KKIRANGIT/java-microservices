package com.ecommerce.product.mapper;

import com.ecommerce.product.api.ProductRequest;
import com.ecommerce.product.api.ProductResponse;
import com.ecommerce.product.model.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    Product toEntity(ProductRequest request);

    ProductResponse toResponse(Product product);
}
