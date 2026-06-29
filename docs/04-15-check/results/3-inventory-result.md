# 第 3 批库存服务修复结果

## 负责模块与 R-... ID 列表
- 模块：库存服务
- 范围：R-INVENTORY-01、R-INVENTORY-02、R-INVENTORY-03、R-INVENTORY-04

## 修改的主要文件
- `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryService.java`
- `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java`
- `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/query/ReserveItem.java`
- `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/query/InventoryReservationService.java`
- `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/entity/StockReservation.java`
- `code/ecommerce-inventory/src/test/java/com/ecommerce/inventory/service/InventoryServiceTest.java`
- `code/ecommerce-inventory/src/test/java/com/ecommerce/inventory/service/InventoryReservationServiceImplTest.java`

## 每个 R-... 的修复摘要
- `R-INVENTORY-01`：`InventoryService.checkAvailability` 已改为 `availableStock >= requestQuantity`。
- `R-INVENTORY-02`：`InventoryReservationServiceImpl.deductAfterPayment(orderId)` 现在会在同一事务内扣减 `onHandStock` / `reservedStock`、把预占置为 `DEDUCTED`，并按 `{orderId, skuId, warehouseId}` 幂等生成 `OutboundOrder`。
- `R-INVENTORY-03`：库存预占前新增候选仓排序，按“服务区域/省份匹配 → 可单仓满足 → 距离降级近似 → `priority` → `warehouseId`”稳定排序。
- `R-INVENTORY-04`：同一订单同一 SKU 预占时优先选择足量单仓；不存在足量单仓时才拆仓。

## 已执行测试命令与结果
```bash
mvn -q -f code/pom.xml -pl ecommerce-order,ecommerce-inventory -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=InventoryServiceTest,InventoryReservationServiceImplTest,OrderServiceTest,OrderTimeoutServiceTest,OrderTotalCalculatorTest,BatchOrderServiceTest,OrderNotificationServiceTest test
```

结果：通过。
- `com.ecommerce.inventory.service.InventoryServiceTest`：20/20 通过
- `com.ecommerce.inventory.service.InventoryReservationServiceImplTest`：13/13 通过
- `com.ecommerce.order.service.OrderServiceTest`：2/2 通过（用于校验与订单的内部契约联动）

## 未完成项、风险或需要后续批次协调的事项
- 为配合订单预占前移，本批引入了内部 Java 契约扩展：`reserve(String reservationRef, ...)`、`bindReservation(...)`、`release(String reservationRef)`、`ReserveItem.province`、`StockReservation.reservationRef`；未改任何公共 REST API。
- 距离排序当前按要求做了“同省/服务区域命中”的近似降级，尚无真实经纬度或距离服务；后续如物流侧提供距离能力，可直接增强排序实现。
- 支付侧 `R-PAYMENT-01` 尚未完成；库存侧已为 `OrderPaidEvent` 消费做好幂等扣减与出库单生成准备。
