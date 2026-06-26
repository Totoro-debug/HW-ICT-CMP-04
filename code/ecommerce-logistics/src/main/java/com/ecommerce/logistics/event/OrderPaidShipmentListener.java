package com.ecommerce.logistics.event;

import com.ecommerce.order.event.OrderPaidEvent;
import com.ecommerce.order.query.OrderDto;
import com.ecommerce.order.query.OrderQueryService;
import com.ecommerce.logistics.repository.ShipmentRepository;
import com.ecommerce.logistics.service.ShipmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

/**
 * Creates logistics shipments asynchronously after order payment succeeds.
 */
@Component
public class OrderPaidShipmentListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidShipmentListener.class);

    private final ShipmentRepository shipmentRepository;
    private final ShipmentService shipmentService;
    private final OrderQueryService orderQueryService;

    public OrderPaidShipmentListener(ShipmentRepository shipmentRepository,
                                     ShipmentService shipmentService,
                                     OrderQueryService orderQueryService) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentService = shipmentService;
        this.orderQueryService = orderQueryService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        createShipmentForPaidOrder(event.getOrderId());
    }

    private void createShipmentForPaidOrder(Long orderId) {
        try {
            if (shipmentRepository.findByOrderId(orderId).isPresent()) {
                log.info("Shipment already exists for paid orderId={}, skipping", orderId);
                return;
            }

            OrderDto order = orderQueryService.getOrder(orderId);
            BigDecimal freightAmount = order.getShippingFee() == null ? BigDecimal.ZERO : order.getShippingFee();
            shipmentService.createShipment(orderId, order.getUserId(),
                    freightAmount, order.getAddressSnapshot());
            log.info("Shipment created asynchronously for paid orderId={}", orderId);
        } catch (Exception e) {
            log.error("Failed to create shipment for paid orderId={}: {}",
                    orderId, e.getMessage(), e);
        }
    }
}
