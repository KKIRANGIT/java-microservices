package com.ecommerce.inventory.config;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class InventoryBootstrapTest {

    private final InventoryBootstrap inventoryBootstrap = new InventoryBootstrap();

    @Test
    void seedInventory_doesNothing_whenAlreadySeeded() throws Exception {
        InventoryRepository repository = Mockito.mock(InventoryRepository.class);
        when(repository.count()).thenReturn(1L);

        inventoryBootstrap.seedInventory(repository).run();

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any(Inventory.class));
    }

    @Test
    void seedInventory_savesDefaultSkus_whenEmpty() throws Exception {
        InventoryRepository repository = Mockito.mock(InventoryRepository.class);
        when(repository.count()).thenReturn(0L);

        inventoryBootstrap.seedInventory(repository).run();

        ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
        verify(repository, times(3)).save(captor.capture());
        Set<String> skus = captor.getAllValues().stream().map(Inventory::getSkuCode).collect(Collectors.toSet());
        org.junit.jupiter.api.Assertions.assertEquals(
                Set.of("LAPTOP-ACER-001", "HEADPHONES-SONY-001", "KEYBOARD-LOGI-001"),
                skus);
    }
}
