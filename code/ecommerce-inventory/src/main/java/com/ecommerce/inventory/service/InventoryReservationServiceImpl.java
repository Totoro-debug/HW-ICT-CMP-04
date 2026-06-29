package com.ecommerce.inventory.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.cache.InventorySummaryCache;
import com.ecommerce.inventory.entity.InventoryStock;
import com.ecommerce.inventory.entity.OutboundOrder;
import com.ecommerce.inventory.entity.ReservationStatus;
import com.ecommerce.inventory.entity.StockReservation;
import com.ecommerce.inventory.entity.Warehouse;
import com.ecommerce.inventory.query.InventoryReservationService;
import com.ecommerce.inventory.query.ReserveItem;
import com.ecommerce.inventory.repository.InventoryStockRepository;
import com.ecommerce.inventory.repository.OutboundOrderRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
import com.ecommerce.inventory.repository.WarehouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Handles stock reservation, release, and deduction during the order lifecycle.
 */
@Service
public class InventoryReservationServiceImpl implements InventoryReservationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationServiceImpl.class);
    private static final DateTimeFormatter OUTBOUND_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final InventoryStockRepository inventoryStockRepository;
    private final StockReservationRepository stockReservationRepository;
    private final WarehouseRepository warehouseRepository;
    private final OutboundOrderRepository outboundOrderRepository;

    public InventoryReservationServiceImpl(InventoryStockRepository inventoryStockRepository,
                                           StockReservationRepository stockReservationRepository,
                                           WarehouseRepository warehouseRepository,
                                           OutboundOrderRepository outboundOrderRepository) {
        this.inventoryStockRepository = inventoryStockRepository;
        this.stockReservationRepository = stockReservationRepository;
        this.warehouseRepository = warehouseRepository;
        this.outboundOrderRepository = outboundOrderRepository;
    }

    @Override
    @Transactional
    public void reserve(Long orderId, List<ReserveItem> items) {
        reserveInternal(orderId, null, items);
    }

    @Override
    @Transactional
    public void reserve(String reservationRef, List<ReserveItem> items) {
        reserveInternal(null, reservationRef, items);
    }

    @Override
    @Transactional
    public void bindReservation(String reservationRef, Long orderId) {
        if (reservationRef == null || reservationRef.isBlank() || orderId == null) {
            return;
        }
        List<StockReservation> reservations = stockReservationRepository.findAll().stream()
                .filter(reservation -> reservationRef.equals(reservation.getReservationRef()))
                .collect(Collectors.toList());
        for (StockReservation reservation : reservations) {
            reservation.setOrderId(orderId);
            stockReservationRepository.save(reservation);
        }
    }

    private void reserveInternal(Long orderId, String reservationRef, List<ReserveItem> items) {
        List<StockReservation> existingReservations = findExistingReservations(orderId, reservationRef);
        if (!existingReservations.isEmpty()) {
            if (matchesExistingReservation(existingReservations, items)) {
                log.info("Stock reserve idempotent hit for orderId={}, reservationRef={}, reservationsCount={}",
                        orderId, reservationRef, existingReservations.size());
                return;
            }
            throw new ConflictException("Stock reservation idempotency conflict for orderId=" + orderId
                    + ", reservationRef=" + reservationRef);
        }

        for (ReserveItem item : items) {
            List<WarehouseCandidate> candidates = buildCandidates(item);
            int totalAvailable = candidates.stream()
                    .mapToInt(WarehouseCandidate::getAvailableStock)
                    .sum();
            if (totalAvailable < item.getQuantity()) {
                throw new BusinessException("INVENTORY_NOT_ENOUGH",
                        "Not enough available stock for skuId=" + item.getSkuId()
                                + ", shortage=" + (item.getQuantity() - totalAvailable));
            }

            WarehouseCandidate singleWarehouse = candidates.stream()
                    .filter(candidate -> candidate.getAvailableStock() >= item.getQuantity())
                    .findFirst()
                    .orElse(null);
            if (singleWarehouse != null) {
                reserveStock(orderId, reservationRef, item, item.getQuantity(), singleWarehouse.getStock());
                continue;
            }

            int remaining = item.getQuantity();
            for (WarehouseCandidate candidate : candidates) {
                if (remaining <= 0) {
                    break;
                }
                int available = candidate.getAvailableStock();
                if (available <= 0) {
                    continue;
                }
                int toReserve = Math.min(remaining, available);
                reserveStock(orderId, reservationRef, item, toReserve, candidate.getStock());
                remaining -= toReserve;
            }
        }
        log.info("Stock reserved for orderId={}, reservationRef={}, itemsCount={}",
                orderId, reservationRef, items.size());
    }

    private void reserveStock(Long orderId,
                              String reservationRef,
                              ReserveItem item,
                              int quantity,
                              InventoryStock stock) {
        stock.setReservedStock(stock.getReservedStock() + quantity);
        inventoryStockRepository.save(stock);
        InventorySummaryCache.evict(stock.getSkuId());

        StockReservation reservation = new StockReservation();
        reservation.setOrderId(orderId);
        reservation.setReservationRef(reservationRef);
        reservation.setSkuId(item.getSkuId());
        reservation.setWarehouseId(stock.getWarehouseId());
        reservation.setQuantity(quantity);
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        stockReservationRepository.save(reservation);
    }

    private List<StockReservation> findExistingReservations(Long orderId, String reservationRef) {
        if (orderId != null) {
            return stockReservationRepository.findByOrderId(orderId);
        }
        if (reservationRef == null || reservationRef.isBlank()) {
            return List.of();
        }
        return stockReservationRepository.findAll().stream()
                .filter(reservation -> reservationRef.equals(reservation.getReservationRef()))
                .collect(Collectors.toList());
    }

    private List<WarehouseCandidate> buildCandidates(ReserveItem item) {
        List<InventoryStock> stocks = inventoryStockRepository.findBySkuId(item.getSkuId());
        if (stocks.isEmpty()) {
            return List.of();
        }
        Map<Long, Warehouse> warehouseById = warehouseRepository.findAllById(stocks.stream()
                        .map(InventoryStock::getWarehouseId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        Comparator<WarehouseCandidate> comparator = Comparator
                .comparing(WarehouseCandidate::isRegionMatched)
                .reversed()
                .thenComparing(WarehouseCandidate::hasSufficientStock)
                .reversed()
                .thenComparingInt(WarehouseCandidate::getDistanceApproxScore)
                .reversed()
                .thenComparingInt(WarehouseCandidate::getPriorityRank)
                .thenComparingLong(WarehouseCandidate::getWarehouseId);
        return stocks.stream()
                .map(stock -> toCandidate(stock, warehouseById.get(stock.getWarehouseId()), item))
                .sorted(comparator)
                .toList();
    }

    private WarehouseCandidate toCandidate(InventoryStock stock, Warehouse warehouse, ReserveItem item) {
        String requestedProvince = normalize(item.getProvince());
        String warehouseProvince = warehouse == null ? null : normalize(warehouse.getProvince());
        Set<String> serviceRegions = parseServiceRegions(warehouse == null ? null : warehouse.getServiceRegions());
        boolean regionMatched = requestedProvince != null
                && (requestedProvince.equals(warehouseProvince) || serviceRegions.contains(requestedProvince));
        int distanceApproxScore = requestedProvince == null ? 0
                : requestedProvince.equals(warehouseProvince) ? 2
                : serviceRegions.contains(requestedProvince) ? 1 : 0;
        int priorityRank = warehouse != null && warehouse.getPriority() != null
                ? warehouse.getPriority()
                : Integer.MAX_VALUE;
        return new WarehouseCandidate(stock, stock.getAvailableStock(), regionMatched,
                stock.getAvailableStock() >= item.getQuantity(), distanceApproxScore, priorityRank);
    }

    private Set<String> parseServiceRegions(String serviceRegions) {
        if (serviceRegions == null || serviceRegions.isBlank()) {
            return Collections.emptySet();
        }
        return List.of(serviceRegions.replace("[", "")
                        .replace("]", "")
                        .replace("\"", "")
                        .split("[,;|\\s]+"))
                .stream()
                .map(this::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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
        releaseReservations(reservations, "orderId=" + orderId);
    }

    @Override
    @Transactional
    public void release(String reservationRef) {
        List<StockReservation> reservations = stockReservationRepository.findAll().stream()
                .filter(reservation -> reservationRef.equals(reservation.getReservationRef()))
                .filter(reservation -> reservation.getStatus() == ReservationStatus.RESERVED)
                .collect(Collectors.toList());
        releaseReservations(reservations, "reservationRef=" + reservationRef);
    }

    private void releaseReservations(List<StockReservation> reservations, String reason) {
        for (StockReservation reservation : reservations) {
            InventoryStock stock = inventoryStockRepository
                    .findByWarehouseIdAndSkuId(reservation.getWarehouseId(), reservation.getSkuId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "InventoryStock not found for release"));

            stock.setReservedStock(Math.max(0, stock.getReservedStock() - reservation.getQuantity()));
            inventoryStockRepository.save(stock);
            InventorySummaryCache.evict(stock.getSkuId());

            reservation.setStatus(ReservationStatus.RELEASED);
            stockReservationRepository.save(reservation);
        }
        log.info("Stock released for {}, reservationsCount={}", reason, reservations.size());
    }

    @Override
    @Transactional
    public void deductAfterPayment(Long orderId) {
        List<StockReservation> reservations = stockReservationRepository
                .findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED);
        List<OutboundOrder> existingOutboundOrders = new java.util.ArrayList<>(
                outboundOrderRepository.findByOrderId(orderId));

        for (StockReservation reservation : reservations) {
            InventoryStock stock = inventoryStockRepository
                    .findByWarehouseIdAndSkuId(reservation.getWarehouseId(), reservation.getSkuId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "InventoryStock not found for deduction"));

            stock.setOnHandStock(stock.getOnHandStock() - reservation.getQuantity());
            stock.setReservedStock(stock.getReservedStock() - reservation.getQuantity());
            inventoryStockRepository.save(stock);
            InventorySummaryCache.evict(stock.getSkuId());

            reservation.setStatus(ReservationStatus.DEDUCTED);
            stockReservationRepository.save(reservation);

            ensureOutboundOrder(orderId, reservation, existingOutboundOrders);
        }
        log.info("Stock deducted after payment for orderId={}, reservationsCount={}",
                orderId, reservations.size());
    }

    private void ensureOutboundOrder(Long orderId,
                                     StockReservation reservation,
                                     Collection<OutboundOrder> existingOutboundOrders) {
        boolean exists = existingOutboundOrders.stream()
                .anyMatch(order -> Objects.equals(order.getOrderId(), orderId)
                        && Objects.equals(order.getSkuId(), reservation.getSkuId())
                        && Objects.equals(order.getWarehouseId(), reservation.getWarehouseId()));
        if (exists) {
            return;
        }

        OutboundOrder outboundOrder = new OutboundOrder();
        outboundOrder.setOrderNo("OB" + LocalDateTime.now().format(OUTBOUND_NO_FORMATTER));
        outboundOrder.setWarehouseId(reservation.getWarehouseId());
        outboundOrder.setSkuId(reservation.getSkuId());
        outboundOrder.setQuantity(reservation.getQuantity());
        outboundOrder.setOrderId(orderId);
        outboundOrder.setStatus("COMPLETED");
        outboundOrderRepository.save(outboundOrder);
        existingOutboundOrders.add(outboundOrder);
    }

    private static final class WarehouseCandidate {

        private final InventoryStock stock;
        private final int availableStock;
        private final boolean regionMatched;
        private final boolean sufficientStock;
        private final int distanceApproxScore;
        private final int priorityRank;

        private WarehouseCandidate(InventoryStock stock,
                                   int availableStock,
                                   boolean regionMatched,
                                   boolean sufficientStock,
                                   int distanceApproxScore,
                                   int priorityRank) {
            this.stock = stock;
            this.availableStock = availableStock;
            this.regionMatched = regionMatched;
            this.sufficientStock = sufficientStock;
            this.distanceApproxScore = distanceApproxScore;
            this.priorityRank = priorityRank;
        }

        private InventoryStock getStock() {
            return stock;
        }

        private int getAvailableStock() {
            return availableStock;
        }

        private boolean isRegionMatched() {
            return regionMatched;
        }

        private boolean hasSufficientStock() {
            return sufficientStock;
        }

        private int getDistanceApproxScore() {
            return distanceApproxScore;
        }

        private int getPriorityRank() {
            return priorityRank;
        }

        private long getWarehouseId() {
            return stock.getWarehouseId();
        }
    }
}
