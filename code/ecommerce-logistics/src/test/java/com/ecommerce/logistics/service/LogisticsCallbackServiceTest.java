package com.ecommerce.logistics.service;

import com.ecommerce.common.exception.AuthorizationException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.idempotency.Idempotent;
import com.ecommerce.logistics.dto.LogisticsCallbackRequest;
import com.ecommerce.logistics.entity.Shipment;
import com.ecommerce.logistics.entity.ShipmentStatus;
import com.ecommerce.logistics.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LogisticsCallbackService}.
 */
@ExtendWith(MockitoExtension.class)
class LogisticsCallbackServiceTest {

    private static final String SECRET = "shophub-logistics-callback-secret";

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipmentService shipmentService;

    private LogisticsCallbackService callbackService;

    @BeforeEach
    void setUp() {
        callbackService = new LogisticsCallbackService(shipmentRepository, shipmentService, SECRET);
    }

    @Test
    void processCallback_validSignature_updatesShipmentStatus() {
        LogisticsCallbackRequest request = callback("TN12345", "DELIVERED");
        Shipment shipment = new Shipment();
        shipment.setId(1L);
        shipment.setTrackingNo("TN12345");
        when(shipmentRepository.findByTrackingNo("TN12345")).thenReturn(Optional.of(shipment));

        String response = callbackService.processCallback(request);

        assertEquals("OK", response);
        verify(shipmentService).updateStatus(1L, ShipmentStatus.DELIVERED,
                "Shanghai Distribution Center", "Package delivered to recipient");
    }

    @Test
    void processCallback_missingSignature_rejectsAndDoesNotUpdate() {
        LogisticsCallbackRequest request = callback("TN99999", "IN_TRANSIT");
        request.setSignature(null);

        assertThrows(AuthorizationException.class, () -> callbackService.processCallback(request));

        verify(shipmentRepository, never()).findByTrackingNo("TN99999");
        verify(shipmentService, never()).updateStatus(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void processCallback_invalidSignature_rejectsAndDoesNotUpdate() {
        LogisticsCallbackRequest request = callback("TN99999", "IN_TRANSIT");
        request.setSignature("bad-signature");

        assertThrows(AuthorizationException.class, () -> callbackService.processCallback(request));

        verify(shipmentRepository, never()).findByTrackingNo("TN99999");
    }

    @Test
    void processCallback_unknownTrackingNo_throwsNotFound() {
        LogisticsCallbackRequest request = callback("TN404", "COLLECTED");
        when(shipmentRepository.findByTrackingNo("TN404")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> callbackService.processCallback(request));
    }

    @Test
    void processCallback_usesTrackingNoEventTimeStatusIdempotencyKey() throws NoSuchMethodException {
        Method method = LogisticsCallbackService.class.getMethod("processCallback", LogisticsCallbackRequest.class);
        Idempotent idempotent = method.getAnnotation(Idempotent.class);

        assertEquals("LOGISTICS_CALLBACK", idempotent.businessType());
        assertEquals("#request.trackingNo + ':' + #request.eventTime + ':' + #request.status", idempotent.key());
    }

    private LogisticsCallbackRequest callback(String trackingNo, String status) {
        LogisticsCallbackRequest request = new LogisticsCallbackRequest();
        request.setTrackingNo(trackingNo);
        request.setStatus(status);
        request.setLocation("Shanghai Distribution Center");
        request.setDescription("Package delivered to recipient");
        request.setEventTime(LocalDateTime.of(2024, 6, 1, 12, 0));
        request.setSignature(SECRET);
        return request;
    }
}
