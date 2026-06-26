package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.InboundRequest;
import com.ecommerce.inventory.dto.OutboundRequest;
import com.ecommerce.inventory.dto.StockAdjustmentRequest;
import com.ecommerce.inventory.dto.StockWarningResponse;
import com.ecommerce.inventory.dto.WarehouseCreateRequest;
import com.ecommerce.inventory.entity.InventoryStock;
import com.ecommerce.inventory.entity.StockAdjustment;
import com.ecommerce.inventory.entity.Warehouse;
import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.inventory.service.StockAdjustmentService;
import com.ecommerce.inventory.service.StockWarningService;
import com.ecommerce.inventory.service.WarehouseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminInventoryController")
@WebMvcTest(AdminInventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminInventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WarehouseService warehouseService;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private StockWarningService stockWarningService;

    @MockBean
    private StockAdjustmentService stockAdjustmentService;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- warehouse tests ----

    @Test
    @DisplayName("POST /api/v1/admin/warehouses creates warehouse and returns 201")
    void testCreateWarehouse_returnsCreated() throws Exception {
        WarehouseCreateRequest request = new WarehouseCreateRequest();
        request.setName("Main Warehouse");
        request.setProvince("Guangdong");
        request.setCity("Shenzhen");
        request.setPriority(1);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);
        warehouse.setName("Main Warehouse");
        warehouse.setProvince("Guangdong");
        warehouse.setCity("Shenzhen");
        warehouse.setStatus("ACTIVE");
        warehouse.setPriority(1);

        when(warehouseService.create(any(WarehouseCreateRequest.class))).thenReturn(warehouse);

        mockMvc.perform(post("/api/v1/admin/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Main Warehouse"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ---- inbound tests ----

    @Test
    @DisplayName("POST /api/v1/admin/inventory/inbound creates inbound and returns 201")
    void testInbound_returnsCreated() throws Exception {
        InboundRequest request = new InboundRequest();
        request.setWarehouseId(1L);
        request.setSkuId(100L);
        request.setQuantity(50);

        InventoryStock stock = new InventoryStock();
        stock.setId(1L);
        stock.setWarehouseId(1L);
        stock.setSkuId(100L);
        stock.setOnHandStock(50);
        stock.setReservedStock(0);

        when(inventoryService.inbound(any(InboundRequest.class))).thenReturn(stock);

        mockMvc.perform(post("/api/v1/admin/inventory/inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warehouseId").value(1))
                .andExpect(jsonPath("$.skuId").value(100))
                .andExpect(jsonPath("$.onHandStock").value(50));
    }

    // ---- outbound tests ----

    @Test
    @DisplayName("POST /api/v1/admin/inventory/outbound creates outbound and returns 201")
    void testOutbound_returnsCreated() throws Exception {
        InventoryStock stock = new InventoryStock();
        stock.setId(1L);
        stock.setWarehouseId(1L);
        stock.setSkuId(100L);
        stock.setOnHandStock(70);
        stock.setReservedStock(0);

        when(inventoryService.outbound(eq(1L), eq(100L), eq(30), isNull())).thenReturn(stock);

        OutboundRequest request = new OutboundRequest();
        request.setWarehouseId(1L);
        request.setSkuId(100L);
        request.setQuantity(30);

        mockMvc.perform(post("/api/v1/admin/inventory/outbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warehouseId").value(1))
                .andExpect(jsonPath("$.skuId").value(100))
                .andExpect(jsonPath("$.onHandStock").value(70));

        verify(inventoryService).outbound(1L, 100L, 30, null);
    }

    @Test
    @DisplayName("POST /api/v1/admin/inventory/outbound includes optional orderId")
    void testOutbound_withOrderId() throws Exception {
        InventoryStock stock = new InventoryStock();
        stock.setId(1L);
        stock.setWarehouseId(1L);
        stock.setSkuId(100L);
        stock.setOnHandStock(80);

        when(inventoryService.outbound(eq(1L), eq(100L), eq(20), eq(42L))).thenReturn(stock);

        OutboundRequest request = new OutboundRequest();
        request.setWarehouseId(1L);
        request.setSkuId(100L);
        request.setQuantity(20);
        request.setOrderId(42L);

        mockMvc.perform(post("/api/v1/admin/inventory/outbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(inventoryService).outbound(1L, 100L, 20, 42L);
    }

    // ---- adjustment tests ----

    @Test
    @DisplayName("POST /api/v1/admin/inventory/adjustments creates adjustment and returns 201")
    void testCreateAdjustment_returnsCreated() throws Exception {
        StockAdjustment adjustment = new StockAdjustment();
        adjustment.setId(1L);
        adjustment.setWarehouseId(1L);
        adjustment.setSkuId(100L);
        adjustment.setBeforeQty(100);
        adjustment.setAfterQty(80);
        adjustment.setReason("Physical count");

        when(stockAdjustmentService.create(eq(1L), eq(100L), eq(80), eq("Physical count")))
                .thenReturn(adjustment);

        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setWarehouseId(1L);
        request.setSkuId(100L);
        request.setAfterQty(80);
        request.setReason("Physical count");

        mockMvc.perform(post("/api/v1/admin/inventory/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.beforeQty").value(100))
                .andExpect(jsonPath("$.afterQty").value(80))
                .andExpect(jsonPath("$.reason").value("Physical count"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/inventory/adjustments is not exposed")
    void testListAdjustments_notExposed() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inventory/adjustments")
                        .param("warehouseId", "1"))
                .andExpect(status().isMethodNotAllowed());
    }

    // ---- warnings tests ----

    @Test
    @DisplayName("GET /api/v1/admin/inventory/warnings returns stock warnings")
    void testGetWarnings_returnsWarnings() throws Exception {
        StockWarningResponse warning = new StockWarningResponse();
        warning.setSkuId(100L);
        warning.setWarehouseId(1L);
        warning.setOnHandStock(5);
        warning.setSafetyStock(10);
        warning.setWarningThreshold(20);
        warning.setMessage("SKU 100 in warehouse 1 is below warning threshold: 5 <= 20");

        when(stockWarningService.getWarnings()).thenReturn(List.of(warning));

        mockMvc.perform(get("/api/v1/admin/inventory/warnings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skuId").value(100))
                .andExpect(jsonPath("$[0].warehouseId").value(1))
                .andExpect(jsonPath("$[0].onHandStock").value(5))
                .andExpect(jsonPath("$[0].warningThreshold").value(20));
    }

    @Test
    @DisplayName("POST /api/v1/admin/inventory/warnings/rule is not exposed")
    void testSetWarningRule_notExposed() throws Exception {
        mockMvc.perform(post("/api/v1/admin/inventory/warnings/rule")
                        .param("skuId", "100")
                        .param("warehouseId", "1")
                        .param("warningThreshold", "15"))
                .andExpect(status().isNotFound());
    }
}
