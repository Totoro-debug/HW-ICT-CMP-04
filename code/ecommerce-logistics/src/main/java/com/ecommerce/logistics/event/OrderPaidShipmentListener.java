package com.ecommerce.logistics.event;

import com.ecommerce.logistics.service.LogisticsCommandService;
import com.ecommerce.order.event.OrderPaidEvent;
import com.ecommerce.payment.event.PaymentSucceededEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Creates logistics shipments asynchronously after payment succeeds.
 */
@Component
public class OrderPaidShipmentListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidShipmentListener.class);

    private final LogisticsCommandService logisticsCommandService;

    public OrderPaidShipmentListener(LogisticsCommandService logisticsCommandService) {
        this.logisticsCommandService = logisticsCommandService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        createShipmentForPaidOrder(event.getOrderId(), "OrderPaidEvent");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        createShipmentForPaidOrder(event.getOrderId(), "PaymentSucceededEvent");
    }

    private void createShipmentForPaidOrder(Long orderId, String eventType) {
        try {
            logisticsCommandService.createShipmentForPaidOrder(orderId);
            log.info("Shipment creation command handled for orderId={} from {}", orderId, eventType);
        } catch (Exception e) {
            log.error("Failed to create shipment for paid orderId={} from {}: {}",
                    orderId, eventType, e.getMessage(), e);
        }
    }
}
