# 附录C 修复方案汇总

## 总体说明

- 本文仅覆盖 `docs/appendix_C-check/*-check.md` 已报告的附录C数据模型不一致项，不新增报告外问题。
- 修复边界以 `README.md` 的修改边界与冻结契约为准：允许修改 Java 源码、配置、POM、测试（`README.md:28`-`README.md:33`），禁止修改冻结 REST API URL/方法/请求响应字段、`design-docs/`、API 前缀等（`README.md:35`-`README.md:40`）。验收基准为设计文档（`README.md:9`-`README.md:13`、`README.md:281`）。
- 本方案不得建议修改 `design-docs/附录C-数据模型.md` 或冻结 API 契约；所有修复均应在代码实现、JPA 映射、Repository、DTO/服务映射、初始化逻辑、测试适配范围内完成。
- 无不一致项模块：`ecommerce-app`（`docs/appendix_C-check/ecommerce-app-check.md:16`-`docs/appendix_C-check/ecommerce-app-check.md:20`）、`ecommerce-cart`（`docs/appendix_C-check/ecommerce-cart-check.md:32`-`docs/appendix_C-check/ecommerce-cart-check.md:38`）、`ecommerce-common`（`docs/appendix_C-check/ecommerce-common-check.md:15`-`docs/appendix_C-check/ecommerce-common-check.md:20`）、`ecommerce-logistics`（`docs/appendix_C-check/ecommerce-logistics-check.md:15`-`docs/appendix_C-check/ecommerce-logistics-check.md:21`）。
- 后续开发实施时，保持现有 REST DTO 字段不破坏冻结契约；如需调整实体字段名，可通过保留 getter/setter、DTO 映射或兼容方法降低影响。

## 修复方案明细

### R1. ecommerce-inventory：仓库表名与 `code` 字段不一致

- 所属模块：`ecommerce-inventory`
- 问题标题：仓库表名与字段模型不一致：设计为 `warehouses` 且包含 `code`，实现为 `warehouse` 且缺少 `code`
- 检查报告定位：`docs/appendix_C-check/ecommerce-inventory-check.md:20`-`docs/appendix_C-check/ecommerce-inventory-check.md:24`
- 设计依据定位：`design-docs/附录C-数据模型.md:58`-`design-docs/附录C-数据模型.md:66`
- 当前实现定位：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/entity/Warehouse.java:8`-`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/entity/Warehouse.java:34`
- 修复目标：仓库实体映射到 `warehouses` 表，并包含 `code VARCHAR`、`name VARCHAR`、`province VARCHAR`、`priority INT` 等附录C字段。
- 修复方案：
  1. 修改 `Warehouse` 的 `@Table(name = "warehouse")` 为 `@Table(name = "warehouses")`。
  2. 在 `Warehouse` 增加 `private String code;`，使用 `@Column(nullable = false, unique = true, length = 64)` 或至少 `@Column(length = 64)` 映射为默认列名 `code`；补充 getter/setter。
  3. 检查 `WarehouseService`、`AdminInventoryController`、创建仓库请求 DTO/测试初始化中是否已有仓库编码输入；若 API 请求体冻结且未暴露 `code`，则在服务创建逻辑中用请求编码字段（如存在）或按 `WH-<id/testRunId/sequence>` 生成内部 `code`，不得改变 REST 请求/响应字段类型。
  4. `WarehouseRepository` 继续绑定 `Warehouse`；如存在按仓库名查询的逻辑不受影响，可新增 `findByCode` 供初始化或幂等校验使用。
- 影响范围：`Warehouse` 实体、仓库创建/初始化逻辑、可能的仓库唯一性校验与测试数据；JPA 自动建表会使用新表名。
- 注意事项/风险点：表名变更会影响任何原生 SQL 或测试断言；需全文确认没有硬编码 `warehouse` 表名。不要删除当前额外字段 `city/district/detail/serviceRegions/status`，它们不与附录C冲突。
- 建议验证方式：运行 `mvn -f code/pom.xml -pl ecommerce-inventory test`，再运行 `mvn -f code/pom.xml test`；启动测试 profile 时确认 H2 建表包含 `warehouses.code`。

### R2. ecommerce-inventory：库存预警阈值列名不一致

- 所属模块：`ecommerce-inventory`
- 问题标题：库存表预警阈值字段命名不一致：设计为 `warning_threshold`，实现为 `safety_stock`
- 检查报告定位：`docs/appendix_C-check/ecommerce-inventory-check.md:26`-`docs/appendix_C-check/ecommerce-inventory-check.md:30`
- 设计依据定位：`design-docs/附录C-数据模型.md:68`-`design-docs/附录C-数据模型.md:77`
- 当前实现定位：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/entity/InventoryStock.java:21`-`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/entity/InventoryStock.java:28`
- 修复目标：`inventory_stock` 表阈值字段映射为 `warning_threshold INT`。
- 修复方案：
  1. 将 `InventoryStock.safetyStock` 字段重命名为 `warningThreshold`，注解改为 `@Column(name = "warning_threshold")`。
  2. 将 getter/setter 改为 `getWarningThreshold`/`setWarningThreshold`；为降低服务层改动风险，可暂时保留兼容方法 `getSafetyStock()`/`setSafetyStock(int)`，内部代理到 `warningThreshold`，但不得再映射 `safety_stock` 列。
  3. 修改 `InventoryService`、`StockWarningService`、`StockAdjustmentService`、DTO 映射与测试中对 `getSafetyStock`/`setSafetyStock` 的直接使用，逐步切换为 `warningThreshold` 命名。
  4. 如存在库存初始化默认值，将原安全库存默认值写入 `warning_threshold`。
- 影响范围：`InventoryStock` 实体、库存入库/调整/预警读取逻辑、库存预警 DTO 映射。
- 注意事项/风险点：这是列名修复，不是业务规则变更；不要改变 `on_hand_stock`、`reserved_stock`、可用库存计算语义。
- 建议验证方式：运行库存模块测试和公开库存相关用例，确认 `availableStock = onHandStock - reservedStock` 不变，预警查询仍使用阈值判断。

### R3. ecommerce-inventory：库存预占表表名不一致

- 所属模块：`ecommerce-inventory`
- 问题标题：库存预占表表名不一致：设计为 `stock_reservations`，实现为 `stock_reservation`
- 检查报告定位：`docs/appendix_C-check/ecommerce-inventory-check.md:32`-`docs/appendix_C-check/ecommerce-inventory-check.md:36`
- 设计依据定位：`design-docs/附录C-数据模型.md:79`-`design-docs/附录C-数据模型.md:88`
- 当前实现定位：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/entity/StockReservation.java:13`-`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/entity/StockReservation.java:39`
- 修复目标：库存预占实体映射到 `stock_reservations` 表，保留 `order_id`、`sku_id`、`warehouse_id`、`quantity`、`status` 及枚举 `RESERVED/RELEASED/DEDUCTED`。
- 修复方案：
  1. 修改 `StockReservation` 的 `@Table(name = "stock_reservation")` 为 `@Table(name = "stock_reservations")`，保留唯一约束列集合。
  2. 检查 `StockReservationRepository` 是否存在原生 SQL 或 `@Query(nativeQuery = true)`；若有硬编码表名，同步改为 `stock_reservations`。
  3. `InventoryReservationServiceImpl` 等服务无需改变字段语义，仅确保保存/查询仍通过 Repository。
- 影响范围：`StockReservation` 实体、预占/释放/扣减库存流程、任何原生查询。
- 注意事项/风险点：表名变更后，H2 自动建表可直接通过；若存在非自动迁移环境，应补迁移脚本，但当前仓库未见 Flyway/Liquibase 报告项，不主动引入新迁移框架。
- 建议验证方式：运行库存预占相关单元测试，执行下单库存预占、取消释放、支付后扣减链路。

### R4. ecommerce-loyalty：积分数据模型未实现为 `loyalty_points`

- 所属模块：`ecommerce-loyalty`
- 问题标题：积分数据模型未实现为设计要求的 `loyalty_points` 表/字段结构
- 检查报告定位：`docs/appendix_C-check/ecommerce-loyalty-check.md:19`-`docs/appendix_C-check/ecommerce-loyalty-check.md:32`
- 设计依据定位：`design-docs/附录C-数据模型.md:188`-`design-docs/附录C-数据模型.md:197`
- 当前实现定位：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/entity/LoyaltyAccount.java:19`-`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/entity/LoyaltyAccount.java:46`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/entity/PointsTransaction.java:20`-`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/entity/PointsTransaction.java:49`
- 修复目标：实现附录C要求的 `loyalty_points` 表，字段包含 `user_id BIGINT`、`points INT`、`available_points INT`、`expire_date DATE`、`source_type VARCHAR`。
- 修复方案：
  1. 新增或重构积分实体为 `LoyaltyPoint`（建议新增，避免一次性破坏会员等级账户逻辑），映射 `@Table(name = "loyalty_points")`；字段：`Long userId`→`user_id`、`int points`→`points`、`int availablePoints`→`available_points`、`LocalDate expireDate`→`expire_date`、`String sourceType`→`source_type`。
  2. 新增 `LoyaltyPointRepository extends JpaRepository<LoyaltyPoint, Long>`，提供 `findByUserId`、按过期日期查询、按用户汇总可用积分的方法；保留现有 `LoyaltyAccountRepository` 与 `PointsTransactionRepository` 作为内部账户/流水实现，或在后续重构中迁移，但必须保证 `loyalty_points` 表承载设计字段。
  3. 修改 `LoyaltyPointService` 写入逻辑：积分发放时同时写入/更新 `loyalty_points.points` 与 `available_points`，设置 `expireDate`（由原 `PointsTransaction.expiresAt` 转为 `LocalDate`）和 `sourceType`（由原 `bizType` 或积分来源枚举映射）。积分消费/过期时扣减 `available_points`，并保持账户余额与流水一致。
  4. 修改 `PointsExpireService`：过期扫描以 `loyalty_points.expire_date` 为准，按 DATE 维度处理，不再仅依赖 `PointsTransaction.expiresAt LocalDateTime`。
  5. DTO/API 输出仍保持 README 冻结字段；`LoyaltyQueryService` 可继续返回 `availablePoints`，但数据来源应可由 `loyalty_points.available_points` 汇总或同步结果提供。
- 影响范围：新增实体与 Repository、积分发放/抵扣/过期服务、会员等级服务对可用积分的读取、测试初始化数据。
- 注意事项/风险点：设计要求单表字段结构，但现有账户+流水模型承载额外业务能力；为降低风险，优先采用“新增设计表并同步写入”的落地方式，不删除原表。需确保事务中三者一致，避免积分余额与设计表不同步。
- 建议验证方式：运行 `ecommerce-loyalty` 测试；覆盖积分查询、积分历史、积分过期、下单抵扣积分链路；检查 H2 schema 中存在 `loyalty_points.expire_date` 且类型为 DATE。

### R5. ecommerce-order：`orders` 金额字段精度不一致

- 所属模块：`ecommerce-order`
- 问题标题：`orders` 金额字段精度与附录C要求不一致
- 检查报告定位：`docs/appendix_C-check/ecommerce-order-check.md:20`-`docs/appendix_C-check/ecommerce-order-check.md:24`
- 设计依据定位：`design-docs/附录C-数据模型.md:101`-`design-docs/附录C-数据模型.md:107`
- 当前实现定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/Order.java:47`-`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/Order.java:72`
- 修复目标：`orders` 表的 `item_total`、`shipping_fee`、`packaging_fee`、`discount_amount`、`points_deduction_amount`、`payable_amount`、`paid_amount` 均为 `DECIMAL(18,2)`。
- 修复方案：
  1. 将 `Order` 中上述七个 `BigDecimal` 字段的 `@Column(precision = 12, scale = 2)` 改为 `precision = 18, scale = 2`。
  2. 保持字段名、Java 类型、DTO/API 响应不变，不改变订单金额计算公式。
  3. 检查 `OrderPricingService`、`CustomerOrderService`、`SalesStatisticsService` 中金额计算和汇总是否有 `setScale(2)` 或常量精度假设；仅需保证写入字段仍为两位小数。
- 影响范围：`Order` 实体映射、H2 DDL、订单创建/支付/统计链路。
- 注意事项/风险点：精度扩大通常向后兼容；不要将 Java 类型改为 `double` 或 `long`。
- 建议验证方式：运行订单模块测试、公开创建订单/支付/销售统计用例；可检查生成 DDL 中金额列为 `numeric(18,2)` 或等价类型。

### R6. ecommerce-order：`order_items` 字段命名与金额/CLOB 类型不一致

- 所属模块：`ecommerce-order`
- 问题标题：`order_items` 商品快照与金额字段命名/类型与附录C要求不一致
- 检查报告定位：`docs/appendix_C-check/ecommerce-order-check.md:26`-`docs/appendix_C-check/ecommerce-order-check.md:30`
- 设计依据定位：`design-docs/附录C-数据模型.md:110`-`design-docs/附录C-数据模型.md:121`
- 当前实现定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/OrderItem.java:29`-`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/OrderItem.java:51`
- 修复目标：`order_items` 表使用 `product_name VARCHAR`、`sku_specs CLOB`、`unit_price DECIMAL(18,2)`、`item_amount DECIMAL(18,2)`。
- 修复方案：
  1. 将实体字段映射调整为：`skuName` 改名或至少 `@Column(name = "product_name", nullable = false, length = 256)`；`productSnapshot` 改为 `skuSpecs` 并使用 `@Column(name = "sku_specs", columnDefinition = "CLOB")`；`price` 改为 `unitPrice` 并使用 `@Column(name = "unit_price", nullable = false, precision = 18, scale = 2)`；`subtotal` 改为 `itemAmount` 并使用 `@Column(name = "item_amount", nullable = false, precision = 18, scale = 2)`。
  2. 更新 `CustomerOrderService`/`OrderService` 中创建订单明细的赋值逻辑：商品名称写入 `productName`，规格 JSON 快照写入 `skuSpecs`，单价写入 `unitPrice`，单行金额写入 `itemAmount`。
  3. 更新 `OrderQueryServiceImpl`、`OrderDto`/明细 DTO 映射和销售统计读取逻辑，保持对外 REST 字段不变；如对外 DTO 仍叫 `skuName`/`price`/`subtotal`，可由新实体字段映射得到，不改变 API 契约。
  4. 可暂时保留兼容 getter/setter（如 `getSkuName()` 代理 `getProductName()`、`getPrice()` 代理 `getUnitPrice()`、`getSubtotal()` 代理 `getItemAmount()`），但 JPA 列名必须为附录C要求。
- 影响范围：`OrderItem` 实体、订单创建、订单查询 DTO、统计/导出/对账读取明细金额逻辑、相关测试。
- 注意事项/风险点：`sku_code` 是额外快照字段，报告未要求删除，可保留；核心是新增/更名设计字段并确保数据写入正确。
- 建议验证方式：运行订单模块测试和公开基础订单、批量订单、销售统计、评价购买校验链路。

### R7. ecommerce-payment：支付数据模型表名、金额字段、精度与状态枚举不一致

- 所属模块：`ecommerce-payment`
- 问题标题：支付数据模型表名、金额字段、金额精度与状态枚举不一致
- 检查报告定位：`docs/appendix_C-check/ecommerce-payment-check.md:20`-`docs/appendix_C-check/ecommerce-payment-check.md:24`
- 设计依据定位：`design-docs/附录C-数据模型.md:125`-`design-docs/附录C-数据模型.md:135`
- 当前实现定位：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/PaymentRecord.java:14`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/PaymentRecord.java:51`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/PaymentStatus.java:3`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/PaymentStatus.java:8`
- 修复目标：支付实体映射到 `payments` 表，包含 `payment_no`、`order_id`、`amount DECIMAL(18,2)`、`method`、`status CREATED/SUCCESS/FAILED/CLOSED`、`paid_at`。
- 修复方案：
  1. 将 `PaymentRecord` 的表名改为 `@Table(name = "payments", ...)`，并修正索引列名为实际数据库列名（如 `payment_no`、`order_id`）。
  2. 将 `paymentNo`、`orderId` 显式标注 `@Column(name = "payment_no")`、`@Column(name = "order_id")`，避免物理命名策略差异。
  3. 用 `amount` 字段承载设计支付金额：将 `orderAmount`/`paidAmount` 至少统一映射出 `@Column(name = "amount", nullable = false, precision = 18, scale = 2)`；建议重命名为 `amount`，保留兼容 getter `getOrderAmount()`/`getPaidAmount()` 返回 `amount`，或将非设计扩展字段改为非核心附加列但确保 `amount` 存在且服务读写以它为准。
  4. 修改 `PaymentStatus` 为 `CREATED, SUCCESS, FAILED, CLOSED`；将原 `PENDING` 语义映射为 `CREATED`，原 `REFUNDED` 不属于支付表状态，退款状态由 `refunds` 管理。
  5. 修改 `PaymentService` 创建支付单初始状态为 `CREATED`；`PaymentCallbackService` 成功回调置 `SUCCESS`，失败置 `FAILED`，关闭/超时置 `CLOSED`（如有）。DTO 返回仍按冻结 API 需要输出当前状态字符串，公开用例期望创建为 `CREATED`。
- 影响范围：`PaymentRecord`、`PaymentStatus`、`PaymentRecordRepository`、支付创建/回调/查询/结算逻辑、测试断言。
- 注意事项/风险点：状态枚举重命名会影响所有 `PaymentStatus.PENDING`、`PaymentStatus.REFUNDED` 引用，必须一次性替换并明确退款不反写支付状态为 `REFUNDED`。
- 建议验证方式：运行支付模块测试、公开支付单创建与支付回调用例；检查表名 `payments`、列 `amount`、初始状态 `CREATED`。

### R8. ecommerce-payment：退款数据模型表名、字段、精度与状态枚举不一致

- 所属模块：`ecommerce-payment`
- 问题标题：退款数据模型表名、字段、金额精度与状态枚举不一致
- 检查报告定位：`docs/appendix_C-check/ecommerce-payment-check.md:26`-`docs/appendix_C-check/ecommerce-payment-check.md:30`
- 设计依据定位：`design-docs/附录C-数据模型.md:137`-`design-docs/附录C-数据模型.md:146`
- 当前实现定位：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/RefundRecord.java:14`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/RefundRecord.java:48`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/RefundStatus.java:3`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/RefundStatus.java:10`
- 修复目标：退款实体映射到 `refunds` 表，包含 `refund_no`、`order_id`、`paid_amount DECIMAL(18,2)`、`refund_amount DECIMAL(18,2)`、状态 `APPLIED/REVIEWED/ACCEPTED/REFUNDED/REJECTED`。
- 修复方案：
  1. 将 `RefundRecord` 表名改为 `@Table(name = "refunds", ...)`，索引列名同步为 `refund_no`、`order_id`、`status` 等实际列名。
  2. 显式标注 `refundNo`→`refund_no`、`orderId`→`order_id`、`refundAmount`→`refund_amount`，并将金额字段精度改为 `precision = 18, scale = 2`。
  3. 增加 `private BigDecimal paidAmount;`，映射 `@Column(name = "paid_amount", nullable = false, precision = 18, scale = 2)`；在 `RefundService.apply` 读取原支付/订单实付金额后写入。
  4. 修改 `RefundStatus` 为 `APPLIED, REVIEWED, ACCEPTED, REFUNDED, REJECTED`；状态流转映射：原 `PENDING_REVIEW`→`APPLIED`，原 `APPROVED`→`REVIEWED`，原 `WAREHOUSE_ACCEPTED`→`ACCEPTED`，原 `COMPLETED`→`REFUNDED`，原 `REJECTED` 保持；原 `WAITING_WAREHOUSE_ACCEPT` 属于过程语义，不作为最终设计枚举，可由业务条件或单独非设计扩展字段表达等待仓库验收。
  5. 修改 `RefundService`、`RefundStageService`、管理审核/仓库验收控制器的状态判断与 DTO 映射，保证冻结 API 不变，业务错误码 `REFUND_WAITING_WAREHOUSE_ACCEPT` 仍可通过服务判断抛出。
- 影响范围：退款实体、状态枚举、退款申请/审核/仓库验收/查询/结算逻辑、测试断言。
- 注意事项/风险点：枚举收敛后要避免丢失“等待仓库验收”的流程判断；可使用 `reviewed` 后未 `accepted` 的状态和业务上下文表达，或新增内部布尔/时间字段，但不得影响附录C核心状态集合。
- 建议验证方式：运行退款相关单元测试和公开/隐藏退款流程；检查 `refunds.paid_amount` 必填且精度为 18,2。

### R9. ecommerce-payment：发票数据模型表名、字段命名、精度与状态枚举不一致

- 所属模块：`ecommerce-payment`
- 问题标题：发票数据模型表名、字段命名、金额/税率精度与状态枚举不一致
- 检查报告定位：`docs/appendix_C-check/ecommerce-payment-check.md:32`-`docs/appendix_C-check/ecommerce-payment-check.md:36`
- 设计依据定位：`design-docs/附录C-数据模型.md:148`-`design-docs/附录C-数据模型.md:159`
- 当前实现定位：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/InvoiceRecord.java:14`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/InvoiceRecord.java:56`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/InvoiceStatus.java:3`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/InvoiceStatus.java:6`
- 修复目标：发票实体映射到 `invoices` 表，字段包含 `invoice_no`、`order_id`、`title`、`amount DECIMAL(18,2)`、`tax_rate DECIMAL(6,4)`、`tax_amount DECIMAL(18,2)`、状态 `ISSUED/VOIDED`。
- 修复方案：
  1. 将 `InvoiceRecord` 表名改为 `@Table(name = "invoices", ...)`，索引列名同步为 `invoice_no`、`order_id`、`status`。
  2. 将 `invoiceTitle` 映射为 `@Column(name = "title")`，或重命名为 `title` 并保留兼容 getter/setter。
  3. 将 `invoiceAmount` 映射为 `@Column(name = "amount", precision = 18, scale = 2)`；`taxAmount` 改为 `precision = 18, scale = 2`；`taxRate` 改为 `precision = 6, scale = 4`。
  4. 修改 `InvoiceStatus` 为 `ISSUED, VOIDED`，将原 `CANCELLED` 全部替换为 `VOIDED`；修改 `InvoiceService` 创建、查询、作废逻辑的状态判断。
  5. REST DTO 字段若仍为 `invoiceTitle`/`invoiceAmount`，由实体 `title`/`amount` 映射输出，不改变冻结 API 请求/响应字段。
- 影响范围：发票实体、状态枚举、发票开具/查询/作废、结算或剩余可开票金额计算。
- 注意事项/风险点：`remainingInvoiceableAmount`、`invoiceType`、`taxId` 是额外字段，可保留；但附录C核心列必须按设计命名和精度落库。
- 建议验证方式：运行发票相关测试和公开发票全额开具用例；检查税率可保存四位小数。

### R10. ecommerce-promotion：优惠券数据模型未按 `coupons` 表/字段实现

- 所属模块：`ecommerce-promotion`
- 问题标题：优惠券数据模型未按附录C定义实现为 `coupons` 表/实体，字段被拆分并改名
- 检查报告定位：`docs/appendix_C-check/ecommerce-promotion-check.md:19`-`docs/appendix_C-check/ecommerce-promotion-check.md:32`
- 设计依据定位：`design-docs/附录C-数据模型.md:161`-`design-docs/附录C-数据模型.md:174`
- 当前实现定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/CouponTemplate.java:17`-`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/CouponTemplate.java:47`、`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/UserCoupon.java:15`-`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/UserCoupon.java:30`、`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/CouponType.java:6`-`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/CouponType.java:20`
- 修复目标：实现 `coupons` 表，字段为 `coupon_code`、`type`、`discount_rate DECIMAL(6,4)`、`amount DECIMAL(18,2)`、`threshold_amount DECIMAL(18,2)`、`valid_from TIMESTAMP`、`valid_to TIMESTAMP`；枚举保持 `DISCOUNT/AMOUNT_OFF/THRESHOLD_OFF`。
- 修复方案：
  1. 新增 `Coupon` 实体映射 `@Table(name = "coupons")`，字段按附录C命名和精度定义；`type` 使用现有 `CouponType`。
  2. 新增 `CouponRepository`，或将 `CouponTemplateRepository` 逐步改绑 `Coupon`；为降低风险，可保留 `CouponTemplate`/`UserCoupon` 作为模板与用户领取扩展表，但创建优惠券时必须同步写入 `coupons`。
  3. `CouponTemplate.discountValue` 的语义拆分映射：当 `type=DISCOUNT` 时写入 `coupons.discount_rate`；当 `AMOUNT_OFF` 或 `THRESHOLD_OFF` 时写入 `coupons.amount`；`thresholdAmount` 写入 `threshold_amount` 且精度改为 18,2；`startTime/endTime` 分别写入 `valid_from/valid_to`。
  4. `UserCoupon.couponCode` 当前在用户券表，需在发券/领券时保证 `coupons.coupon_code` 有值；如果 `coupon_code` 表示模板券编码，则由创建券模板时生成；如果表示用户券实例编码，则领取时同步 `coupons` 记录或将 `Coupon` 作为用户券实例实体。选择一种后保持 Repository 查询与优惠计算一致。
  5. 修改 `CouponService`、`CouponTemplateService`、`PromotionCalculationServiceImpl`、`CouponUsageService` 的读取逻辑，优先使用 `Coupon` 的设计字段进行优惠计算；对外 API DTO 不变。
- 影响范围：新增实体/Repository、优惠券创建/领取/查询/计算/核销逻辑、测试初始化数据。
- 注意事项/风险点：当前“模板+用户券”模型比附录C更细；不要破坏领取与核销流程。必须明确 `discount_rate` 表示折后比例（现有 `CouponType` 注释说明 0.8 表示八折），避免影响公开 8 折用例。
- 建议验证方式：运行促销模块测试、公开 8 折优惠券计算用例；检查 `coupons` 表字段精度与 `valid_from/valid_to` 写入。

### R11. ecommerce-product：`product_sku.price` 精度不一致

- 所属模块：`ecommerce-product`
- 问题标题：`product_sku.price` 精度与设计 `DECIMAL(18,2)` 不一致
- 检查报告定位：`docs/appendix_C-check/ecommerce-product-check.md:20`-`docs/appendix_C-check/ecommerce-product-check.md:24`
- 设计依据定位：`design-docs/附录C-数据模型.md:44`-`design-docs/附录C-数据模型.md:53`
- 当前实现定位：`code/ecommerce-product/src/main/java/com/ecommerce/product/entity/ProductSku.java:25`-`code/ecommerce-product/src/main/java/com/ecommerce/product/entity/ProductSku.java:29`
- 修复目标：`product_sku.price` 为 `DECIMAL(18,2)`。
- 修复方案：
  1. 将 `ProductSku.price` 注解从 `@Column(precision = 12, scale = 2)` 改为 `@Column(precision = 18, scale = 2)`。
  2. 保持 Java 类型 `BigDecimal`、SKU 创建/查询 DTO 字段不变。
  3. 检查商品创建服务和购物车/订单对 SKU 价格的读取，确保仍按两位小数处理。
- 影响范围：`ProductSku` 实体、商品创建/上架/查询、购物车估价、订单创建价格快照。
- 注意事项/风险点：`marketPrice` 不属于本报告问题，不在本方案要求内强制修改。
- 建议验证方式：运行商品模块测试、公开商品创建上架查询、购物车估价、订单创建用例；检查 DDL 中 `price` 精度。

### R12. ecommerce-product：`product_sku.specs_json` 字段命名和 CLOB 类型不一致

- 所属模块：`ecommerce-product`
- 问题标题：`product_sku.specs_json` 字段命名和类型与设计不一致
- 检查报告定位：`docs/appendix_C-check/ecommerce-product-check.md:26`-`docs/appendix_C-check/ecommerce-product-check.md:30`
- 设计依据定位：`design-docs/附录C-数据模型.md:44`-`design-docs/附录C-数据模型.md:54`
- 当前实现定位：`code/ecommerce-product/src/main/java/com/ecommerce/product/entity/ProductSku.java:34`-`code/ecommerce-product/src/main/java/com/ecommerce/product/entity/ProductSku.java:35`
- 修复目标：规格 JSON 落库列名为 `specs_json`，类型为 `CLOB`。
- 修复方案：
  1. 将 `ProductSku.specs` 字段改为 `@Column(name = "specs_json", columnDefinition = "CLOB")`；可重命名 Java 字段为 `specsJson`，并保留 `getSpecs()`/`setSpecs()` 兼容当前服务和 DTO。
  2. 修改商品创建/查询 DTO 映射：外部冻结字段若为 `specs`，继续通过 `getSpecs()` 暴露；内部落库列必须为 `specs_json`。
  3. 检查 `ProductIntegrationService`、订单快照构造是否读取 `specs`；如改名，更新为兼容 getter 或新字段。
- 影响范围：`ProductSku` 实体、商品创建/查询、订单明细 SKU 规格快照来源。
- 注意事项/风险点：H2 对 `CLOB` 支持正常；不要改动 REST 字段名。
- 建议验证方式：运行商品模块测试与订单创建链路，确认规格 JSON 可保存并读取。

### R13. ecommerce-user：`users.roles` 字段命名与列表语义不一致

- 所属模块：`ecommerce-user`
- 问题标题：`users` 表角色字段命名与结构不一致
- 检查报告定位：`docs/appendix_C-check/ecommerce-user-check.md:20`-`docs/appendix_C-check/ecommerce-user-check.md:24`
- 设计依据定位：`design-docs/附录C-数据模型.md:5`-`design-docs/附录C-数据模型.md:16`
- 当前实现定位：`code/ecommerce-user/src/main/java/com/ecommerce/user/entity/User.java:29`-`code/ecommerce-user/src/main/java/com/ecommerce/user/entity/User.java:35`
- 修复目标：`users` 表包含 `roles VARCHAR`，表达角色列表；不破坏现有认证授权 API。
- 修复方案：
  1. 将 `User.role` 的列映射改为 `@Column(name = "roles", nullable = false, length = 128)`；为体现列表语义，建议新增 `private String roles;` 持久化字段，保存如 `USER` 或 `USER,ADMIN`，并保留 `getRole()`/`setRole(UserRole)` 作为兼容方法读取/写入首个角色。
  2. 修改 `UserRegisterService`、管理员 seed/初始化逻辑：创建普通用户写入 `roles="USER"`，管理员写入 `roles="ADMIN"` 或包含多个角色的逗号分隔字符串。
  3. 修改 `UserAuthService`、`JwtTokenProvider` 调用处：由 `user.getRoles()` 解析为 `List<String>`，替代 `Collections.singletonList(user.getRole().name())`；保持 JWT 与登录响应中的 roles 列表不变。
  4. 修改 `UserResponse`、`UserDto` 映射：如外部仍需要单个 `role` 字段，则从 roles 的首个角色映射；如已有 `roles` 响应字段则直接输出列表，不改变 README 冻结契约。
  5. `UserRole` 枚举可继续作为单角色兼容和解析校验使用，不必删除。
- 影响范围：`User` 实体、注册/管理员初始化、登录 JWT roles、用户查询 DTO、角色缓存 `UserRoleCacheManager`。
- 注意事项/风险点：认证授权高度依赖角色；修复时必须保证管理员 token 仍具备 ADMIN，普通用户仍具备 USER。不要改变登录/注册 REST 字段。
- 建议验证方式：运行用户模块测试、公开注册激活登录、冻结用户、管理员接口鉴权用例；检查 `users.roles` 列存在且 JWT roles 正确。

### R14. ecommerce-user：`user_addresses.default_address` 列名不一致

- 所属模块：`ecommerce-user`
- 问题标题：`user_addresses` 表默认地址字段命名不一致
- 检查报告定位：`docs/appendix_C-check/ecommerce-user-check.md:26`-`docs/appendix_C-check/ecommerce-user-check.md:30`
- 设计依据定位：`design-docs/附录C-数据模型.md:18`-`design-docs/附录C-数据模型.md:30`
- 当前实现定位：`code/ecommerce-user/src/main/java/com/ecommerce/user/entity/UserAddress.java:30`-`code/ecommerce-user/src/main/java/com/ecommerce/user/entity/UserAddress.java:37`
- 修复目标：默认地址列名为 `default_address BOOLEAN`。
- 修复方案：
  1. 将 `UserAddress.isDefault` 注解从 `@Column(name = "is_default", nullable = false)` 改为 `@Column(name = "default_address", nullable = false)`。
  2. 保持 Java Bean 方法 `isDefault()`/`setDefault(boolean)` 不变，避免影响 `AddressService` 和 DTO 映射。
  3. 检查 `UserAddressRepository` 是否有原生 SQL 硬编码 `is_default`；如有改为 `default_address`。
- 影响范围：`UserAddress` 实体、地址创建/更新/默认地址切换、地址查询 DTO。
- 注意事项/风险点：只改列名，不改对外 DTO 中可能存在的 `defaultAddress` 字段。
- 建议验证方式：运行用户地址测试与公开创建地址并查询用例，确认默认地址状态正确。

### R15. ecommerce-review：`reviews.content` 类型不一致

- 所属模块：`ecommerce-review`
- 问题标题：`reviews.content` 字段类型与附录C不一致
- 检查报告定位：`docs/appendix_C-check/ecommerce-review-check.md:19`-`docs/appendix_C-check/ecommerce-review-check.md:23`
- 设计依据定位：`design-docs/附录C-数据模型.md:199`-`design-docs/附录C-数据模型.md:209`
- 当前实现定位：`code/ecommerce-review/src/main/java/com/ecommerce/review/entity/Review.java:32`-`code/ecommerce-review/src/main/java/com/ecommerce/review/entity/Review.java:36`
- 修复目标：`reviews.content` 使用 `VARCHAR`，不再显式映射为 `TEXT`。
- 修复方案：
  1. 将 `Review.content` 的 `@Column(columnDefinition = "TEXT")` 改为 `@Column(length = 1000)` 或不指定 `columnDefinition` 的 `@Column`，使数据库列为 VARCHAR；建议保留合理长度约束以匹配评论内容上限。
  2. 检查 `ReviewService`/DTO 校验中内容长度限制；如无长度校验，增加 Bean Validation 或服务校验，避免超出 VARCHAR 长度导致数据库异常，但不得改变 REST 字段名。
  3. 保留 `images`、`reviewerResponse` 等额外 TEXT 字段，因为本报告只涉及 `content`。
- 影响范围：`Review` 实体、评价发布/查询/审核 DTO 校验。
- 注意事项/风险点：若公开或隐藏测试提交长评论，需要错误处理为通用校验失败而非数据库异常。
- 建议验证方式：运行评价模块测试和公开评价发布基础链路；检查建表中 `content` 为 VARCHAR。
