# 第 3 批订单服务修复结果

## 负责模块与 R-... ID 列表
- 模块：订单服务
- 范围：R-ORDER-01、R-ORDER-02、R-ORDER-03、R-ORDER-04、R-ORDER-05

## 修改的主要文件
- `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java`
- `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderTotalCalculator.java`
- `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderTimeoutService.java`
- `code/ecommerce-order/src/main/java/com/ecommerce/order/service/BatchOrderService.java`
- `code/ecommerce-order/src/test/java/com/ecommerce/order/service/OrderServiceTest.java`
- `code/ecommerce-order/src/test/java/com/ecommerce/order/service/OrderTimeoutServiceTest.java`
- `code/ecommerce-order/src/test/java/com/ecommerce/order/service/OrderTotalCalculatorTest.java`
- `code/ecommerce-order/src/test/java/com/ecommerce/order/service/BatchOrderServiceTest.java`
- 直接相关的库存内部契约联动文件：
  - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/query/InventoryReservationService.java`
  - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/query/ReserveItem.java`
  - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/entity/StockReservation.java`
  - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java`

## 每个 R-... 的修复摘要
- `R-ORDER-01`：下单流程改为先按 `reservationRef` 预占库存，再做风控；若风控或后续步骤失败，则按同一 `reservationRef` 释放；订单成功持久化后再绑定真实 `orderId`。
- `R-ORDER-02`：`OrderTotalCalculator.calculate(...)` 已按 `itemTotal + shippingFee + packagingFee - discountAmount - pointsDeductionAmount` 计算。
- `R-ORDER-03`：最终 `payableAmount` 在完整公式后统一 `MonetaryUtil.roundToCent(...)`，并保持最低 `0.01`。
- `R-ORDER-04`：`OrderTimeoutService` 已注入 `InventoryReservationService`，超时取消时同步 `release(orderId)`。
- `R-ORDER-05`：`BatchOrderService` 删除了单条失败后的提前停止逻辑，无论 `continueOnError` 如何都继续逐条处理。

## 已执行测试命令与结果
```bash
mvn -q -f code/pom.xml -pl ecommerce-order,ecommerce-inventory -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=InventoryServiceTest,InventoryReservationServiceImplTest,OrderServiceTest,OrderTimeoutServiceTest,OrderTotalCalculatorTest,BatchOrderServiceTest,OrderNotificationServiceTest test
```

结果：通过。
- `com.ecommerce.order.service.OrderServiceTest`：2/2 通过
- `com.ecommerce.order.service.OrderTimeoutServiceTest`：1/1 通过
- `com.ecommerce.order.service.OrderTotalCalculatorTest`：3/3 通过
- `com.ecommerce.order.service.BatchOrderServiceTest`：1/1 通过
- `com.ecommerce.order.integration.OrderNotificationServiceTest`：3/3 通过（确认先前通知模型兼容问题已不再阻塞订单测试）

## 未完成项、风险或需要后续批次协调的事项
- 为满足 `R-ORDER-01`，本批引入了库存内部预占引用契约：`reservationRef / bindReservation / release(String)`；该内部约定需要后续支付/物流侧继续沿用真实 `orderId` + `reservationRef` 的语义，不影响任何冻结 REST API。
- 多仓“距离优先”当前由库存侧以“省份/服务区域匹配”近似实现；若后续物流或库存补真实距离模型，可继续增强，但不影响当前设计闭环。
- 支付成功后的 `OrderPaidEvent` 发布仍待第 4 批 `R-PAYMENT-01` 闭合，库存扣减和物流发货链在该批完成后再做全链路复核。
