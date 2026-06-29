package com.ecommerce.order.service;

import com.ecommerce.order.dto.BatchCreateOrderRequest;
import com.ecommerce.order.dto.BatchCreateOrderResponse;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.CreateOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchOrderService")
class BatchOrderServiceTest {

    @Mock
    private OrderService orderService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private BatchOrderService batchOrderService;
    private CreateOrderRequest orderRequest1;
    private CreateOrderRequest orderRequest2;
    private CreateOrderRequest orderRequest3;
    private CreateOrderResponse successResponse1;
    private CreateOrderResponse successResponse3;

    @BeforeEach
    void setUp() {
        batchOrderService = new BatchOrderService(orderService, transactionManager);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        orderRequest1 = buildRequest("EXT-001", 10L);
        orderRequest2 = buildRequest("EXT-002", 20L);
        orderRequest3 = buildRequest("EXT-003", 30L);
        successResponse1 = buildResponse(100L, "SO-100");
        successResponse3 = buildResponse(300L, "SO-300");
    }

    @Test
    @DisplayName("continues processing after failure even when continueOnError is false")
    void testCreateBatch_continueAfterFailureRegardlessOfFlag() {
        when(orderService.createOrder(eq(1L), any(CreateOrderRequest.class)))
                .thenReturn(successResponse1)
                .thenThrow(new RuntimeException("invalid order"))
                .thenReturn(successResponse3);

        BatchCreateOrderRequest batchRequest = new BatchCreateOrderRequest();
        batchRequest.setOrders(Arrays.asList(orderRequest1, orderRequest2, orderRequest3));
        batchRequest.setContinueOnError(false);

        BatchCreateOrderResponse response = batchOrderService.createBatch(1L, batchRequest);

        verify(orderService, times(3)).createOrder(eq(1L), any(CreateOrderRequest.class));
        assertThat(response.getTotalCount()).isEqualTo(3);
        assertThat(response.getSuccessCount()).isEqualTo(2);
        assertThat(response.getFailureCount()).isEqualTo(1);
        assertThat(response.getResults().get(0).isSuccess()).isTrue();
        assertThat(response.getResults().get(1).isSuccess()).isFalse();
        assertThat(response.getResults().get(2).isSuccess()).isTrue();
    }

    private CreateOrderRequest buildRequest(String externalOrderNo, Long skuId) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(1L);
        request.setExternalOrderNo(externalOrderNo);
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setSkuId(skuId);
        item.setQuantity(1);
        request.setItems(List.of(item));
        return request;
    }

    private CreateOrderResponse buildResponse(Long orderId, String orderNo) {
        CreateOrderResponse response = new CreateOrderResponse();
        response.setOrderId(orderId);
        response.setOrderNo(orderNo);
        response.setStatus("CREATED");
        response.setItemTotal(new BigDecimal("50.00"));
        response.setPayableAmount(new BigDecimal("59.00"));
        return response;
    }
}
