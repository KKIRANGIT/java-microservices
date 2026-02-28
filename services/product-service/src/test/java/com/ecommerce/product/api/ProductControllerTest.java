package com.ecommerce.product.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.product.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import(ProductApiExceptionHandler.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Test
    void getProducts_returnsList() throws Exception {
        when(productService.getAllProducts())
                .thenReturn(List.of(
                        new ProductResponse(1L, "SKU-1", "Keyboard", "Mechanical", new BigDecimal("99.00"))));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skuCode").value("SKU-1"))
                .andExpect(jsonPath("$[0].name").value("Keyboard"));
    }

    @Test
    void getBySku_returnsSingleRow() throws Exception {
        when(productService.getBySkuCode("SKU-99"))
                .thenReturn(new ProductResponse(99L, "SKU-99", "Headphones", "ANC", new BigDecimal("399.00")));

        mockMvc.perform(get("/api/products/sku/SKU-99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.name").value("Headphones"));
    }

    @Test
    void createProduct_returnsCreated() throws Exception {
        when(productService.createProduct(any(ProductRequest.class)))
                .thenReturn(new ProductResponse(7L, "SKU-7", "Monitor", "4K", new BigDecimal("499.00")));

        String body = """
                {
                  "skuCode": "SKU-7",
                  "name": "Monitor",
                  "description": "4K",
                  "price": 499.00
                }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.skuCode").value("SKU-7"));
    }

    @Test
    void getBySku_returnsNotFound_whenServiceThrows() throws Exception {
        when(productService.getBySkuCode("MISSING"))
                .thenThrow(new IllegalArgumentException("Product not found for sku MISSING"));

        mockMvc.perform(get("/api/products/sku/MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Product not found for sku MISSING"));
    }

    @Test
    void createProduct_returnsBadRequest_whenPayloadInvalid() throws Exception {
        String body = """
                {
                  "skuCode": "",
                  "name": "",
                  "description": "valid",
                  "price": 0
                }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());

        verifyNoInteractions(productService);
    }
}
