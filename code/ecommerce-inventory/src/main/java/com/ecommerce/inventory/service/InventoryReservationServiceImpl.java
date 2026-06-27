package com.ecommerce.inventory.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.cache.InventorySummaryCache;
import com.ecommerce.inventory.entity.InventoryStock;
import com.ecommerce.inventory.entity.ReservationStatus;
import com.ecommerce.inventory.entity.StockReservation;
import com.ecommerce.inventory.query.InventoryReservationService;
import com.ecommerce.inventory.query.ReserveItem;
import com.ecommerce.inventory.repository.InventoryStockRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Handles stock reservation, release, and deduction during the order lifecycle.
 */
@Service
public class InventoryReservationServiceImpl implements InventoryReservationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationServiceImpl.class);

    private final InventoryStockRepository inventoryStockRepository;
    private final StockReservationRepository stockReservationRepository;

    public InventoryReservationServiceImpl(InventoryStockRepository inventoryStockRepository,
                                           StockReservationRepository stockReservationRepository) {
        this.inventoryStockRepository = inventoryStockRepository;
        this.stockReservationRepository = stockReservationRepository;
    }

    @Override
    @Transactional
    public void reserve(Long orderId, List<ReserveItem> items) {
        List<StockReservation> existingReservations = stockReservationRepository.findByOrderId(orderId);
        if (existingReservations != null && !existingReservations.isEmpty()) {
            if (matchesExistingReservation(existingReservations, items)) {
                log.info("Stock reserve idempotent hit for orderId={}, reservationsCount={}",
                        orderId, existingReservations.size());
                return;
            }
            throw new ConflictException("Stock reservation idempotency conflict for orderId=" + orderId);
        }

        for (ReserveItem item : items) {
            List<InventoryStock> stocks = inventoryStockRepository.findBySkuId(item.getSkuId());
            int remaining = item.getQuantity();

            for (InventoryStock stock : stocks) {
                if (remaining <= 0) {
                    break;
                }
                int available = stock.getAvailableStock();
                if (available <= 0) {
                    continue;
                }
                int toReserve = Math.min(remaining, available);

                stock.setReservedStock(stock.getReservedStock() + toReserve);
                inventoryStockRepository.save(stock);
                InventorySummaryCache.evict(stock.getSkuId());

                StockReservation reservation = new StockReservation();
                reservation.setOrderId(orderId);
                reservation.setSkuId(item.getSkuId());
                reservation.setWarehouseId(stock.getWarehouseId());
                reservation.setQuantity(toReserve);
                reservation.setStatus(ReservationStatus.RESERVED);
                reservation.setExpiresAt(LocalDateTime.now().plusMinutes(30));
                stockReservationRepository.save(reservation);

                remaining -= toReserve;
            }

            if (remaining > 0) {
                throw new BusinessException("INVENTORY_NOT_ENOUGH",
                        "Not enough available stock for skuId=" + item.getSkuId()
                                + ", shortage=" + remaining);
            }
        }
        log.info("Stock reserved for orderId={}, itemsCount={}", orderId, items.size());
    }

    private boolean matchesExistingReservation(List<StockReservation> existingReservations, List<ReserveItem> items) {
        return items.stream().allMatch(item -> {
            int reservedQuantity = existingReservations.stream()
                    .filter(reservation -> reservation.getSkuId().equals(item.getSkuId()))
                    .mapToInt(StockReservation::getQuantity)
                    .sum();
            return reservedQuantity == item.getQuantity();
        }) && existingReservations.stream().allMatch(reservation -> items.stream()
                .anyMatch(item -> item.getSkuId().equals(reservation.getSkuId())));
    }

    @Override
    @Transactional
    public void release(Long orderId) {
        List<StockReservation> reservations = stockReservationRepository
                .findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED);

        for (StockReservation reservation : reservations) {
            InventoryStock stock = inventoryStockRepository
                    .findByWarehouseIdAndSkuId(reservation.getWarehouseId(), reservation.getSkuId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "InventoryStock not found for release"));

            stock.setReservedStock(Math.max(0, stock.getReservedStock() - reservation.getQuantity()));
            inventoryStockRepository.save(stock);

            reservation.setStatus(ReservationStatus.RELEASED);
            stockReservationRepository.save(reservation);
        }
        log.info("Stock released for orderId={}, reservationsCount={}", orderId, reservations.size());
    }

    @Override
    @Transactional
    public void deductAfterPayment(Long orderId) {
        List<StockReservation> reservations = stockReservationRepository
                .findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED);

        for (StockReservation reservation : reservations) {
            InventoryStock stock = inventoryStockRepository
                    .findByWarehouseIdAndSkuId(reservation.getWarehouseId(), reservation.getSkuId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "InventoryStock not found for deduction"));

            stock.setOnHandStock(stock.getOnHandStock() - reservation.getQuantity());
            stock.setReservedStock(stock.getReservedStock() - reservation.getQuantity());
            inventoryStockRepository.save(stock);

            reservation.setStatus(ReservationStatus.DEDUCTED);
            stockReservationRepository.save(reservation);
        }
        log.info("Stock deducted after payment for orderId={}, reservationsCount={}",
                orderId, reservations.size());
    }
}
