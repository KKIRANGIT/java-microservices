package com.ecommerce.inventory.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.inventory.service.InventoryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @Test
    void getAllInventory_returnsRows() throws Exception {
        when(inventoryService.getAllInventory())
                .thenReturn(List.of(new InventoryResponse("SKU-1", true, 5)));

        mockMvc.perform(get("/api/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skuCode").value("SKU-1"))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[0].quantity").value(5));
    }

    @Test
    void getInventory_returnsBySku() throws Exception {
        when(inventoryService.getInventory("SKU-2"))
                .thenReturn(new InventoryResponse("SKU-2", false, 0));

        mockMvc.perform(get("/api/inventory/SKU-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuCode").value("SKU-2"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void reserve_returnsServiceResponse() throws Exception {
        when(inventoryService.reserveInventory(any(ReserveInventoryRequest.class)))
                .thenReturn(new InventoryResponse("SKU-3", true, 11));

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skuCode":"SKU-3","quantity":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuCode").value("SKU-3"))
                .andExpect(jsonPath("$.quantity").value(11));
    }
}
