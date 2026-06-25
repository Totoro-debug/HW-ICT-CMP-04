# M4 inventory-service 一致性审查报告

模块目录：`code/ecommerce-inventory/`  
包名：`com.ecommerce.inventory`

## 审查结论

发现与本模块相关的文档不一致 4 条。

## 不一致项

### 1. 库存预占实现提前扣减了现货库存

1. 实现位置：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java:54-60`
2. 设计依据：`design-docs/01-项目概述.md` 第 5 章关键业务原则，`49-54` 行，尤其 `53` 行“创建订单时只预占库存，不扣减库存。支付成功后扣减库存”；`design-docs/01-项目概述.md` 模块清单 `32-39` 行，尤其 `39` 行 M4 职责包含“库存预占、释放、扣减”。
3. 不一致内容：`reserve` 在预占库存时同时执行 `stock.setOnHandStock(stock.getOnHandStock() - toReserve)` 和 `stock.setReservedStock(...)`，即订单创建阶段已经扣减 `onHandStock`。
4. 原因分析与影响：文档要求下单/创建订单阶段只做预占，库存扣减应发生在支付成功后。当前实现会在预占阶段提前减少现货库存，导致预占语义与扣减语义混淆；后续支付扣减时还会再次减少 `onHandStock`，可能造成库存被重复扣减、可售库存计算异常。

### 2. 库存释放未恢复因预占阶段提前扣减的现货库存

1. 实现位置：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java:85-100`
2. 设计依据：`design-docs/01-项目概述.md` 模块清单 `32-39` 行，尤其 `39` 行 M4 职责包含“库存预占、释放、扣减”；`design-docs/01-项目概述.md` 第 5 章关键业务原则 `49-54` 行，尤其 `53` 行要求创建订单只预占库存。
3. 不一致内容：`release` 只执行 `stock.setReservedStock(stock.getReservedStock() - reservation.getQuantity())`，没有恢复 `onHandStock`。由于同一实现的 `reserve` 已经在预占时减少了 `onHandStock`，释放预占后库存无法回到预占前状态。
4. 原因分析与影响：释放职责应取消预占影响。当前实现与预占提前扣减现货库存的行为组合后，会导致订单取消或超时释放后库存永久减少，影响后续库存查询和可售校验。

### 3. 库存不足错误码与冻结错误码不一致

1. 实现位置：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java:74-78`；`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryService.java:166-169`
2. 设计依据：`README.md` 第 7 章错误码，`200-230` 行；其中 `214-222` 行定义业务错误码，`221` 行规定库存不足错误码为 `INVENTORY_NOT_ENOUGH`，HTTP 400。
3. 不一致内容：库存预占不足和出库库存不足均抛出 `BusinessException("INSUFFICIENT_STOCK", ...)`，未使用文档冻结的 `INVENTORY_NOT_ENOUGH`。
4. 原因分析与影响：错误码属于冻结契约的一部分。黑盒调用方按 `INVENTORY_NOT_ENOUGH` 识别库存不足时，当前实现返回 `INSUFFICIENT_STOCK` 会导致错误响应契约不匹配，影响客户端处理和验收断言。

### 4. 暴露了未在库存模块冻结 API 中登记的业务接口

1. 实现位置：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/controller/AdminInventoryController.java:76-90`
2. 设计依据：`design-docs/01-项目概述.md` 第 3 章 API 版本约定，`26-30` 行，尤其 `30` 行“所有业务 API 必须在附录A中登记”；`README.md` 第 6.3 节库存模块冻结 API，`105-115` 行，仅登记了 `POST /api/v1/admin/inventory/adjustments` 和 `GET /api/v1/admin/inventory/warnings` 等库存接口。
3. 不一致内容：实现额外暴露了 `GET /api/v1/admin/inventory/adjustments` 和 `POST /api/v1/admin/inventory/warnings/rule`，但这两个业务管理接口未出现在库存模块冻结 API 列表中。
4. 原因分析与影响：文档要求业务 API 需登记且冻结契约不得改变。额外暴露未登记接口会扩大 REST API 面，造成实现 API 面与冻结契约不一致，并可能影响黑盒验收对接口集合和管理能力边界的判断。
