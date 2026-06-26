package com.ecommerce.logistics.service;

import com.ecommerce.common.exception.AuthorizationException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.logistics.dto.LogisticsCallbackRequest;
import com.ecommerce.logistics.entity.Shipment;
import com.ecommerce.logistics.entity.ShipmentStatus;
import com.ecommerce.logistics.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Handles logistics status callbacks from external carrier systems.
 *
 * <p>Carriers call the callback endpoint to report shipment status changes
 * such as pickup, in-transit, delivery, or exception events.
 */
@Service
public class LogisticsCallbackService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsCallbackService.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final ShipmentRepository shipmentRepository;
    private final ShipmentService shipmentService;
    private final String callbackSecret;

    public LogisticsCallbackService(ShipmentRepository shipmentRepository,
                                    ShipmentService shipmentService,
                                    @Value("${logistics.callback-secret:shophub-logistics-callback-secret}")
                                    String callbackSecret) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentService = shipmentService;
        this.callbackSecret = callbackSecret;
    }

    /**
     * Process a logistics status callback from a carrier.
     *
     * @param request the callback request from the carrier
     */
    @Transactional
    public void processCallback(LogisticsCallbackRequest request) {
        log.info("Received logistics callback: trackingNo={}, status={}, location={}, "
                        + "description={}, eventTime={}",
                request.getTrackingNo(), request.getStatus(),
                request.getLocation(), request.getDescription(), request.getEventTime());

        validateSignature(request);

        Shipment shipment = shipmentRepository.findByTrackingNo(request.getTrackingNo())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shipment not found for trackingNo: " + request.getTrackingNo()));

        ShipmentStatus newStatus = mapToShipmentStatus(request.getStatus());
        shipmentService.updateStatus(shipment.getId(), newStatus,
                request.getLocation(), request.getDescription());
    }

    /**
     * Map a carrier status string to a ShipmentStatus enum.
     */
    private ShipmentStatus mapToShipmentStatus(String status) {
        if (status == null) {
            return ShipmentStatus.EXCEPTION;
        }
        switch (status.toUpperCase()) {
            case "COLLECTED":
                return ShipmentStatus.COLLECTED;
            case "IN_TRANSIT":
                return ShipmentStatus.IN_TRANSIT;
            case "DELIVERED":
                return ShipmentStatus.DELIVERED;
            case "EXCEPTION":
                return ShipmentStatus.EXCEPTION;
            default:
                log.warn("Unknown carrier status: {}, defaulting to IN_TRANSIT", status);
                return ShipmentStatus.IN_TRANSIT;
        }
    }

    private void validateSignature(LogisticsCallbackRequest request) {
        String signature = request.getSignature();
        if (signature == null || signature.isBlank()) {
            throw AuthorizationException.forbidden("Invalid logistics callback signature");
        }

        if (constantTimeEquals(signature, callbackSecret)
                || constantTimeEquals(signature, hmacSignature(request))) {
            return;
        }

        throw AuthorizationException.forbidden("Invalid logistics callback signature");
    }

    private String hmacSignature(LogisticsCallbackRequest request) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(callbackSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(canonicalPayload(request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to verify logistics callback signature", e);
        }
    }

    private String canonicalPayload(LogisticsCallbackRequest request) {
        LocalDateTime eventTime = request.getEventTime();
        return nullToEmpty(request.getTrackingNo()) + "|"
                + nullToEmpty(request.getStatus()) + "|"
                + nullToEmpty(request.getLocation()) + "|"
                + nullToEmpty(request.getDescription()) + "|"
                + (eventTime == null ? "" : eventTime.toString());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }
}
