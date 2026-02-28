package com.ecommerce.product.config;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.product.model.Product;
import com.ecommerce.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CatalogBootstrapTest {

    private final CatalogBootstrap catalogBootstrap = new CatalogBootstrap();

    @Test
    void seedCatalog_doesNothing_whenDataAlreadyPresent() throws Exception {
        ProductRepository repository = org.mockito.Mockito.mock(ProductRepository.class);
        when(repository.count()).thenReturn(2L);

        catalogBootstrap.seedCatalog(repository).run();

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any(Product.class));
    }

    @Test
    void seedCatalog_insertsDefaultProducts_whenEmpty() throws Exception {
        ProductRepository repository = org.mockito.Mockito.mock(ProductRepository.class);
        when(repository.count()).thenReturn(0L);

        catalogBootstrap.seedCatalog(repository).run();

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repository, times(3)).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Set.of("LAPTOP-ACER-001", "HEADPHONES-SONY-001", "KEYBOARD-LOGI-001"),
                captor.getAllValues().stream().map(Product::getSkuCode).collect(java.util.stream.Collectors.toSet()));
    }
}
