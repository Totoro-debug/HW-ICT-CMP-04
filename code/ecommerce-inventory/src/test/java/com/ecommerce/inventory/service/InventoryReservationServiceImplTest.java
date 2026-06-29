package com.ecommerce.inventory.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.entity.InventoryStock;
import com.ecommerce.inventory.entity.OutboundOrder;
import com.ecommerce.inventory.entity.ReservationStatus;
import com.ecommerce.inventory.entity.StockReservation;
import com.ecommerce.inventory.entity.Warehouse;
import com.ecommerce.inventory.query.ReserveItem;
import com.ecommerce.inventory.repository.InventoryStockRepository;
import com.ecommerce.inventory.repository.OutboundOrderRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
import com.ecommerce.inventory.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InventoryReservationServiceImpl")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryReservationServiceImplTest {

    @Mock
    private InventoryStockRepository stockRepo;

    @Mock
    private StockReservationRepository stockReservationRepo;

    @Mock
    private WarehouseRepository warehouseRepo;

    @Mock
    private OutboundOrderRepository outboundOrderRepo;

    @InjectMocks
    private InventoryReservationServiceImpl reservationService;

    private final Map<Long, Warehouse> warehouses = new HashMap<>();

    @BeforeEach
    void setUp() {
        warehouses.clear();
        when(stockRepo.save(any(InventoryStock.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockReservationRepo.save(any(StockReservation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboundOrderRepo.save(any(OutboundOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboundOrderRepo.findByOrderId(anyLong())).thenReturn(List.of());
        when(warehouseRepo.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            List<Warehouse> result = new ArrayList<>();
            for (Long id : ids) {
                Warehouse warehouse = warehouses.get(id);
                if (warehouse != null) {
                    result.add(warehouse);
                }
            }
            return result;
        });
    }

    @Test
    @DisplayName("reserve only increases reservedStock")
    void testReserve_onlyIncreasesReservedStock() {
        InventoryStock stock = stock(1L, 100L, 200, 0);
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock));

        reservationService.reserve(1L, List.of(new ReserveItem(100L, 40)));

        assertThat(stock.getOnHandStock()).isEqualTo(200);
        assertThat(stock.getReservedStock()).isEqualTo(40);
        assertThat(stock.getAvailableStock()).isEqualTo(160);
    }

    @Test
    @DisplayName("reserve prefers a single sufficient warehouse before splitting")
    void testReserve_prefersSingleWarehouseBeforeSplit() {
        InventoryStock partial = stock(1L, 100L, 3, 0);
        InventoryStock full = stock(2L, 100L, 10, 0);
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(partial, full));

        reservationService.reserve(1L, List.of(new ReserveItem(100L, 10)));

        assertThat(partial.getReservedStock()).isZero();
        assertThat(full.getReservedStock()).isEqualTo(10);
        verify(stockReservationRepo, times(1)).save(any(StockReservation.class));
    }

    @Test
    @DisplayName("reserve splits across warehouses only when no single warehouse is sufficient")
    void testReserve_splitsAcrossWarehousesWhenNecessary() {
        InventoryStock stock1 = stock(1L, 100L, 3, 0);
        InventoryStock stock2 = stock(2L, 100L, 7, 0);
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock1, stock2));

        reservationService.reserve(1L, List.of(new ReserveItem(100L, 10)));

        assertThat(stock1.getReservedStock()).isEqualTo(3);
        assertThat(stock2.getReservedStock()).isEqualTo(7);
        verify(stockReservationRepo, times(2)).save(any(StockReservation.class));
    }

    @Test
    @DisplayName("reserve prioritizes region matched warehouse before priority fallback")
    void testReserve_prioritizesRegionMatch() {
        InventoryStock nonMatching = stock(1L, 100L, 20, 0);
        InventoryStock matching = stock(2L, 100L, 20, 0);
        warehouses.put(1L, warehouse(1L, "北京", null, 1));
        warehouses.put(2L, warehouse(2L, "上海", "浙江,江苏", 9));
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(nonMatching, matching));

        reservationService.reserve(1L, List.of(new ReserveItem(100L, 10, "浙江")));

        assertThat(nonMatching.getReservedStock()).isZero();
        assertThat(matching.getReservedStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("reserve uses warehouse priority as stable fallback when matches tie")
    void testReserve_usesPriorityFallbackWhenMatchesTie() {
        InventoryStock lowerPriority = stock(1L, 100L, 20, 0);
        InventoryStock higherPriority = stock(2L, 100L, 20, 0);
        warehouses.put(1L, warehouse(1L, "浙江", null, 5));
        warehouses.put(2L, warehouse(2L, "浙江", null, 1));
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(lowerPriority, higherPriority));

        reservationService.reserve(1L, List.of(new ReserveItem(100L, 10, "浙江")));

        assertThat(lowerPriority.getReservedStock()).isZero();
        assertThat(higherPriority.getReservedStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("reserve throws BusinessException when stock is insufficient")
    void testReserve_throwsWhenInsufficientStock() {
        InventoryStock stock = stock(1L, 100L, 200, 0);
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock));

        assertThatThrownBy(() -> reservationService.reserve(1L, List.of(new ReserveItem(100L, 300))))
                .isInstanceOf(BusinessException.class)
                .matches(ex -> ((BusinessException) ex).getCode().equals("INVENTORY_NOT_ENOUGH"),
                        "should have code INVENTORY_NOT_ENOUGH");
    }

    @Test
    @DisplayName("reserve is idempotent for same orderId and same items")
    void testReserve_sameOrderIdSameItems_isIdempotent() {
        StockReservation existing = reservation(1L, 100L, 1L, 40, ReservationStatus.RESERVED);
        when(stockReservationRepo.findByOrderId(1L)).thenReturn(List.of(existing));

        reservationService.reserve(1L, List.of(new ReserveItem(100L, 40)));

        verify(stockRepo, never()).save(any());
        verify(stockReservationRepo, never()).save(any());
    }

    @Test
    @DisplayName("reserve rejects same orderId with different items")
    void testReserve_sameOrderIdDifferentItems_throwsConflict() {
        StockReservation existing = reservation(1L, 100L, 1L, 40, ReservationStatus.RESERVED);
        when(stockReservationRepo.findByOrderId(1L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> reservationService.reserve(1L, List.of(new ReserveItem(100L, 41))))
                .isInstanceOf(ConflictException.class);
        verify(stockRepo, never()).save(any());
        verify(stockReservationRepo, never()).save(any());
    }

    @Test
    @DisplayName("release only decreases reservedStock and is idempotent when repeated")
    void testRelease_increasesAvailableStock() {
        InventoryStock stock = stock(1L, 100L, 200, 40);
        StockReservation reservation = reservation(1L, 100L, 1L, 40, ReservationStatus.RESERVED);
        when(stockReservationRepo.findByOrderIdAndStatus(1L, ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation))
                .thenReturn(List.of());
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.of(stock));

        reservationService.release(1L);
        reservationService.release(1L);

        assertThat(stock.getReservedStock()).isZero();
        assertThat(stock.getOnHandStock()).isEqualTo(200);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    @DisplayName("release throws ResourceNotFoundException when stock is missing")
    void testRelease_throwsWhenStockNotFound() {
        StockReservation reservation = reservation(1L, 100L, 1L, 40, ReservationStatus.RESERVED);
        when(stockReservationRepo.findByOrderIdAndStatus(1L, ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation));
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.release(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deductAfterPayment decreases stock, marks reservation deducted, and creates outbound order")
    void testDeductAfterPayment_createsOutboundOrder() {
        InventoryStock stock = stock(1L, 100L, 200, 40);
        StockReservation reservation = reservation(1L, 100L, 1L, 40, ReservationStatus.RESERVED);
        when(stockReservationRepo.findByOrderIdAndStatus(1L, ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation));
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.of(stock));

        reservationService.deductAfterPayment(1L);

        assertThat(stock.getOnHandStock()).isEqualTo(160);
        assertThat(stock.getReservedStock()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.DEDUCTED);

        ArgumentCaptor<OutboundOrder> captor = ArgumentCaptor.forClass(OutboundOrder.class);
        verify(outboundOrderRepo).save(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(1L);
        assertThat(captor.getValue().getWarehouseId()).isEqualTo(1L);
        assertThat(captor.getValue().getSkuId()).isEqualTo(100L);
        assertThat(captor.getValue().getQuantity()).isEqualTo(40);
        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("deductAfterPayment is idempotent for repeated payment events")
    void testDeductAfterPayment_isIdempotentForRepeatedEvents() {
        InventoryStock stock = stock(1L, 100L, 200, 40);
        StockReservation reservation = reservation(1L, 100L, 1L, 40, ReservationStatus.RESERVED);
        when(stockReservationRepo.findByOrderIdAndStatus(1L, ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation))
                .thenReturn(List.of());
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.of(stock));

        reservationService.deductAfterPayment(1L);
        reservationService.deductAfterPayment(1L);

        verify(outboundOrderRepo, times(1)).save(any(OutboundOrder.class));
        assertThat(stock.getOnHandStock()).isEqualTo(160);
        assertThat(stock.getReservedStock()).isZero();
    }

    @Test
    @DisplayName("deductAfterPayment throws ResourceNotFoundException when stock is missing")
    void testDeductAfterPayment_throwsWhenStockNotFound() {
        StockReservation reservation = reservation(1L, 100L, 1L, 40, ReservationStatus.RESERVED);
        when(stockReservationRepo.findByOrderIdAndStatus(1L, ReservationStatus.RESERVED))
                .thenReturn(List.of(reservation));
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.deductAfterPayment(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private InventoryStock stock(Long warehouseId, Long skuId, int onHandStock, int reservedStock) {
        InventoryStock stock = new InventoryStock();
        stock.setWarehouseId(warehouseId);
        stock.setSkuId(skuId);
        stock.setOnHandStock(onHandStock);
        stock.setReservedStock(reservedStock);
        stock.setSafetyStock(0);
        return stock;
    }

    private Warehouse warehouse(Long id, String province, String serviceRegions, Integer priority) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setProvince(province);
        warehouse.setServiceRegions(serviceRegions);
        warehouse.setPriority(priority);
        return warehouse;
    }

    private StockReservation reservation(Long orderId,
                                         Long skuId,
                                         Long warehouseId,
                                         int quantity,
                                         ReservationStatus status) {
        StockReservation reservation = new StockReservation();
        reservation.setOrderId(orderId);
        reservation.setSkuId(skuId);
        reservation.setWarehouseId(warehouseId);
        reservation.setQuantity(quantity);
        reservation.setStatus(status);
        return reservation;
    }
}
