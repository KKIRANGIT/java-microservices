package com.ecommerce.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.product.api.ProductRequest;
import com.ecommerce.product.api.ProductResponse;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void getAllProducts_mapsRepositoryRows() {
        Product first = product(1L, "SKU-1", "Name 1", "Desc 1", "10.00");
        Product second = product(2L, "SKU-2", "Name 2", "Desc 2", "20.00");
        when(productRepository.findAll()).thenReturn(List.of(first, second));

        List<ProductResponse> responses = productService.getAllProducts();

        assertEquals(2, responses.size());
        assertEquals("SKU-1", responses.get(0).skuCode());
        assertEquals("Name 2", responses.get(1).name());
    }

    @Test
    void createProduct_savesAndReturnsMappedResponse() {
        ProductRequest request = new ProductRequest("SKU-10", "Keyboard", "Wireless", new BigDecimal("179.00"));
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> {
                    Product p = invocation.getArgument(0);
                    p.setId(10L);
                    return p;
                });

        ProductResponse response = productService.createProduct(request);

        assertEquals(10L, response.id());
        assertEquals("SKU-10", response.skuCode());
        assertEquals(new BigDecimal("179.00"), response.price());

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertEquals("Keyboard", captor.getValue().getName());
        assertEquals("Wireless", captor.getValue().getDescription());
    }

    @Test
    void getBySkuCode_returnsResponse_whenFound() {
        Product product = product(9L, "SKU-9", "Mouse", "Gaming", "59.00");
        when(productRepository.findBySkuCode("SKU-9")).thenReturn(Optional.of(product));

        ProductResponse response = productService.getBySkuCode("SKU-9");

        assertEquals(9L, response.id());
        assertEquals("Mouse", response.name());
    }

    @Test
    void getBySkuCode_throws_whenMissing() {
        when(productRepository.findBySkuCode("MISSING")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> productService.getBySkuCode("MISSING"));

        assertEquals("Product not found for sku MISSING", ex.getMessage());
    }

    private Product product(Long id, String sku, String name, String desc, String price) {
        Product product = new Product();
        product.setId(id);
        product.setSkuCode(sku);
        product.setName(name);
        product.setDescription(desc);
        product.setPrice(new BigDecimal(price));
        return product;
    }
}
