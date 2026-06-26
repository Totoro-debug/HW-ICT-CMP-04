package com.ecommerce.logistics.service;

import com.ecommerce.logistics.entity.Shipment;
import com.ecommerce.logistics.repository.ShipmentRepository;
import com.ecommerce.order.query.OrderDto;
import com.ecommerce.order.query.OrderQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Default logistics command implementation.
 */
@Service
@Transactional
public class LogisticsCommandServiceImpl implements LogisticsCommandService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsCommandServiceImpl.class);

    private final ShipmentRepository shipmentRepository;
    private final ShipmentService shipmentService;
    private final OrderQueryService orderQueryService;

    public LogisticsCommandServiceImpl(ShipmentRepository shipmentRepository,
                                       ShipmentService shipmentService,
                                       OrderQueryService orderQueryService) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentService = shipmentService;
        this.orderQueryService = orderQueryService;
    }

    @Override
    public Shipment createShipmentForPaidOrder(Long orderId) {
        Shipment existing = shipmentRepository.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            log.info("Shipment already exists for paid orderId={}, shipmentId={}, skipping",
                    orderId, existing.getId());
            return existing;
        }

        OrderDto order = orderQueryService.getOrder(orderId);
        BigDecimal freightAmount = order.getShippingFee() == null
                ? BigDecimal.ZERO : order.getShippingFee();
        Shipment shipment = shipmentService.createShipment(orderId, order.getUserId(),
                freightAmount, order.getAddressSnapshot());
        log.info("Shipment created for paid orderId={}, shipmentId={}", orderId, shipment.getId());
        return shipment;
    }
}
