# 电商库存服务一致性检查报告

## 检查范围

- 设计文档：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/06-库存服务设计.md`
- 库存服务代码：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/`
- 模块依赖配置：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/pom.xml`
- 父级 Maven 配置：`D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`
- 引用模块：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product`

## 不一致点

### 1. 库存充足判断使用了 `>`，不符合 `availableStock >= requestQuantity`

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryService.java:68`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryService.java:71`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/06-库存服务设计.md:27`
- 不一致原因：设计要求当 `availableStock >= requestQuantity` 时库存充足，但实现使用 `totalAvailable > quantity`，当可用库存刚好等于请求数量时会返回不可用。
- 详细解析：`InventoryService.checkAvailability(Long skuId, int quantity)` 中先取得 `totalAvailable`，随后以 `totalAvailable > quantity` 赋值给 `available`。例如可用库存为 10、请求数量为 10 时，设计应判定为库存充足，但当前实现返回 `false`。该行为会影响 REST API `/api/v1/inventory/check` 以及所有通过 `InventoryQueryService.checkAvailability` 调用的跨模块库存校验。

### 2. 支付后扣减流程未生成 `OutboundOrder`

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java:125`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java:135`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java:136`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java:139`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/06-库存服务设计.md:40-48`
- 不一致原因：设计要求支付成功后流程为接收 `OrderPaidEvent` 或支付确认命令，查找预占记录，减少 `onHandStock`，减少 `reservedStock`，并生成 `OutboundOrder`；当前 `deductAfterPayment` 只减少库存并将预占记录状态改为 `DEDUCTED`，没有创建或保存 `OutboundOrder`。
- 详细解析：库存服务确实通过 `InventoryOrderPaidEventListener.onOrderPaid` 接收 `OrderPaidEvent` 并调用 `deductAfterPayment`（`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryOrderPaidEventListener.java:39-43`）。但 `InventoryReservationServiceImpl` 未注入 `OutboundOrderRepository`，方法体内也没有实例化 `OutboundOrder` 或调用出库单保存逻辑。`InventoryService.outbound` 中存在手工出库生成 `OutboundOrder` 的逻辑（`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryService.java:181-188`），但支付后扣减路径没有复用该逻辑，因此支付成功后的自动出库单缺失。

### 3. 多仓分配未实现省份匹配、距离、仓库优先级排序

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java:52-64`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/repository/InventoryStockRepository.java:15`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/06-库存服务设计.md:57-65`
- 不一致原因：设计要求库存分配优先级为“省份匹配仓库服务区域 → 可用库存充足 → 距离优先 → 仓库优先级”，但当前预占库存只按 `inventoryStockRepository.findBySkuId(item.getSkuId())` 返回的库存列表顺序遍历，没有读取用户省份、仓库服务区域、距离或仓库优先级进行排序。
- 详细解析：`reserve` 方法对每个 `ReserveItem` 直接查询 SKU 的所有库存记录，然后按 repository 返回顺序计算 `available` 并预占。该流程没有关联 `Warehouse`，也没有使用 `Warehouse.serviceRegions`、`Warehouse.province` 或 `Warehouse.priority` 字段；同时 `InventoryStockRepository.findBySkuId` 只是普通查询方法，没有声明排序条件。结果是预占仓库取决于数据库返回顺序，不符合设计中明确的多仓分配优先级。

### 4. 同一订单同一 SKU 未显式优先选择可满足数量的单仓

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java:52-80`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/06-库存服务设计.md:66`
- 不一致原因：设计要求单个 SKU 可拆分到多个仓库，但同一订单的同一 SKU 优先单仓发出；当前实现从库存列表第一个可用仓开始按 `Math.min(remaining, available)` 逐仓预占，没有先筛选是否存在单个仓库可满足整项数量。
- 详细解析：如果 repository 返回的第一个仓库库存不足，而后续某个仓库库存足以满足整项 SKU 数量，当前实现会先在第一个仓库部分预占，再继续预占后续仓库，形成拆仓。设计要求“优先单仓”意味着应先寻找符合优先级且可一次满足数量的仓库，只有没有单仓满足时才拆分。当前实现仅在数据库返回的第一个可用仓刚好足够时才表现为单仓，并非设计要求的优先单仓策略。

## 未发现不一致的检查点

1. 领域模型：已发现 `Warehouse`、`InventoryStock`、`StockReservation`、`InboundOrder`、`OutboundOrder`、`StockAdjustment`、`StockWarningRule` 实体。
2. 库存公式：`InventoryStock.getAvailableStock()` 实现为 `onHandStock - reservedStock`，符合设计公式。
3. 下单库存处理：`reserve` 流程会创建 `StockReservation`、增加 `reservedStock`，未减少 `onHandStock`。
4. 取消/超时释放：`release` 流程会减少 `reservedStock` 并将 `StockReservation` 状态改为 `RELEASED`。
5. `InventoryQueryService` 方法签名：已包含 `getStockSummary(Long skuId)`、`checkAvailability(Long skuId, int quantity)`、`listAvailableWarehouses(Long skuId)`。
6. `InventoryReservationService` 方法签名：已包含 `reserve(Long orderId, List<ReserveItem> items)`、`release(Long orderId)`、`deductAfterPayment(Long orderId)`。
7. REST API：设计列出的 7 个端点均已实现：
   - `POST /api/v1/admin/warehouses`
   - `POST /api/v1/admin/inventory/inbound`
   - `POST /api/v1/admin/inventory/outbound`
   - `GET /api/v1/inventory/sku/{skuId}`
   - `POST /api/v1/inventory/check`
   - `POST /api/v1/admin/inventory/adjustments`
   - `GET /api/v1/admin/inventory/warnings`
8. 跨模块约束：库存模块依赖 `ecommerce-product`，通过 `ProductQueryService.getSku` 获取商品/SKU 信息，未发现库存模块内重复定义 Product JPA Entity。

## 无法确认项

无。

## 汇总

- 不一致点数量：4
- 无法确认项：无
