package com.ecommerce.logistics.service;

import com.ecommerce.logistics.entity.Shipment;

/**
 * Command interface exposed by logistics for event listeners.
 */
public interface LogisticsCommandService {

    /**
     * Create a shipment for a paid order if one does not already exist.
     *
     * @param orderId paid order ID
     * @return existing or newly created shipment
     */
    Shipment createShipmentForPaidOrder(Long orderId);
}
