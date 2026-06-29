package com.ecommerce.inventory.query;

import java.util.List;

/**
 * Cross-module interface for inventory reservation operations.
 * The order module uses this interface to reserve, release, and deduct stock
 * during the order lifecycle.
 */
public interface InventoryReservationService {

    /**
     * Reserves stock for the given order items.
     * Called when an order is created.
     *
     * @param orderId the order id
     * @param items   the items to reserve
     */
    void reserve(Long orderId, List<ReserveItem> items);

    /**
     * Reserves stock for the given order items before the order is persisted.
     * The reservation is tracked by an internal reference and can later be bound
     * to the real order id or released on failure.
     *
     * @param reservationRef internal reservation reference
     * @param items          the items to reserve
     */
    void reserve(String reservationRef, List<ReserveItem> items);

    /**
     * Binds an existing reservation reference to the persisted order id.
     *
     * @param reservationRef internal reservation reference
     * @param orderId        persisted order id
     */
    void bindReservation(String reservationRef, Long orderId);

    /**
     * Releases all stock reservations for the given order.
     * Called when an order is cancelled or times out.
     *
     * @param orderId the order id
     */
    void release(Long orderId);

    /**
     * Releases all stock reservations for the given reservation reference.
     *
     * @param reservationRef internal reservation reference
     */
    void release(String reservationRef);

    /**
     * Deducts reserved stock after payment is confirmed.
     * Decreases both on-hand stock and reserved stock, and creates outbound orders.
     *
     * @param orderId the order id
     */
    void deductAfterPayment(Long orderId);
}
