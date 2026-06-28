# 电商支付服务一致性检查报告

- 检查代理：agent-ecommerce-payment
- 检查范围：`design-docs/09-支付服务设计.md`、`design-docs/14-发票与结算设计.md`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/`、`code/ecommerce-payment/pom.xml`、`code/pom.xml`，以及引用模块 `code/ecommerce-order` 的 `OrderQueryService`/订单支付状态更新约束
- 输出文件：`D:/Desktop/work/HW-ICT-CMP-04/docs/04-15-check/ecommerce-payment-check.md`

## 不一致点

### 1. 支付成功流程未发布 `OrderPaidEvent`，导致设计要求的库存扣减事件链不完整

- 代码定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentCallbackService.java:112`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentCallbackService.java:119`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentCallbackService.java:120`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:96`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:97`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:100`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryOrderPaidEventListener.java:39`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryOrderPaidEventListener.java:42`
- 设计要求定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/09-支付服务设计.md:23` 至 `D:/Desktop/work/HW-ICT-CMP-04/design-docs/09-支付服务设计.md:32`
- 不一致原因：设计要求支付成功后流程为“更新支付单 SUCCESS → 更新订单 PAID → 触发库存扣减 → 发布 PaymentSucceededEvent 和 OrderPaidEvent”。当前代码在成功回调中更新支付单状态并调用 `markAsPaid` 后，只通过 `PaymentService.confirmPayment` 发布 `PaymentSucceededEvent`，未发布 `OrderPaidEvent`。
- 详细解析：`PaymentCallbackService` 在 `112` 行设置支付单 `SUCCESS`，`119` 行更新订单支付状态，`120` 行调用 `confirmPayment`；`PaymentService.confirmPayment` 在 `96` 至 `100` 行仅构造并发布 `PaymentSucceededEvent`。库存扣减监听器 `InventoryOrderPaidEventListener` 在 `39` 至 `42` 行监听的是 `OrderPaidEvent` 并调用 `deductAfterPayment`。由于支付模块没有发布 `OrderPaidEvent`，设计中“触发库存支付后扣减”和“发布 PaymentSucceededEvent 和 OrderPaidEvent”的事件链不完整。

### 2. 退款金额额外扣除固定 1 元，违反默认公式 `refund = paidAmount × 0.98` 且不得额外扣固定费用

- 代码定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundCalculator.java:35`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundCalculator.java:38`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundCalculator.java:39`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/config/PaymentConfig.java:20`
- 设计要求定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/09-支付服务设计.md:51` 至 `D:/Desktop/work/HW-ICT-CMP-04/design-docs/09-支付服务设计.md:65`
- 不一致原因：设计要求退款金额只按“实付金额 × (1 - 手续费率)”计算，默认手续费率 2%，且不得额外扣除固定费用。当前 `RefundCalculator` 在按费率计算后又减去了 `BigDecimal.ONE`。
- 详细解析：`PaymentConfig` 默认退款手续费率为 `0.02`，`RefundCalculator` 在 `35` 至 `38` 行计算 `paidAmount × (1 - feeRate)`，但 `39` 行执行 `MonetaryUtil.subtract(baseRefund, BigDecimal.ONE)`，使实际退款金额变成 `paidAmount × 0.98 - 1`。这与设计文档 `refund = paidAmount × 0.98` 不一致，并直接违反“不得额外扣除固定费用”。

### 3. 退款流程缺少独立“财务退款”步骤，仓库验收后由仓库接口直接完成退款

- 代码定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundService.java:141`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundService.java:147`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundService.java:149`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundStageService.java:66`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundStageService.java:84`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundStageService.java:93`
- 设计要求定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/09-支付服务设计.md:37` 至 `D:/Desktop/work/HW-ICT-CMP-04/design-docs/09-支付服务设计.md:49`
- 不一致原因：设计要求退款流程为“用户申请 → 商家审核退货 → 买家寄回商品 → 仓库验收 → 财务退款 → 发布 RefundCompletedEvent”，且商家审核后不得直接退款，必须等待仓库验收。当前代码虽等待仓库验收，但仓库验收接口内部立即调用 `completeRefund`，没有财务退款角色、接口或独立状态步骤。
- 详细解析：`RefundService.warehouseAccept` 在 `147` 行完成仓库验收后，`149` 行立即调用 `refundStageService.completeRefund(refundId)`；`RefundStageService.completeRefund` 在 `84` 行将退款状态设为 `COMPLETED`，并在 `93` 行发布 `RefundCompletedEvent`。这把“仓库验收”和“财务退款”合并在同一仓库验收调用中，未体现设计要求的财务退款阶段。

### 4. 发票税率默认值为 13%，不是设计要求的 6%

- 代码定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/InvoiceService.java:36`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/InvoiceService.java:83`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/InvoiceService.java:84`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/InvoiceService.java:85`
- 设计要求定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/09-支付服务设计.md:67` 至 `D:/Desktop/work/HW-ICT-CMP-04/design-docs/09-支付服务设计.md:76`
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/14-发票与结算设计.md:25` 至 `D:/Desktop/work/HW-ICT-CMP-04/design-docs/14-发票与结算设计.md:35`
- 不一致原因：设计要求发票税率从 `invoice.tax-rate` 读取，默认 6%。当前 `InvoiceService` 的默认税率常量为 `0.13`，即 13%。
- 详细解析：`InvoiceService` 在 `36` 行定义 `private static final BigDecimal TAX_RATE = new BigDecimal("0.13")`，并在 `83` 行用该常量作为读取 `invoice.tax-rate` 的默认值。虽然 `84` 至 `85` 行按税率计算税额并保留两位，但默认税率与设计要求的 6% 不一致。

### 5. 结算批次未限定“支付成功且未结算订单”，且未记录/排除已结算订单

- 代码定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/SettlementBatchService.java:70`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/repository/PaymentRecordRepository.java:23`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/repository/PaymentRecordRepository.java:25`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/PaymentRecord.java:38`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/PaymentRecord.java:51`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/AdminSettlementController.java:35`
- 设计要求定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/14-发票与结算设计.md:37` 至 `D:/Desktop/work/HW-ICT-CMP-04/design-docs/14-发票与结算设计.md:41`
- 不一致原因：设计要求结算批次包含“支付成功且未结算的订单”。当前结算查询使用 `findByPaidAtBetween`，只按支付时间查询，没有限定 `PaymentStatus.SUCCESS`，也没有付款记录的已结算标记或生成批次后标记已结算的逻辑。
- 详细解析：`SettlementBatchService` 在 `70` 行调用 `paymentRecordRepository.findByPaidAtBetween(startOfDay, endOfDay)`；仓库中虽然存在 `findByStatusAndPaidAtBetween`（`PaymentRecordRepository.java:23`），但结算服务未使用。`PaymentRecord` 只包含状态和 `paidAt` 等字段，没有结算标记字段；生成批次后也未更新支付记录为已结算。`AdminSettlementController` 注释还写明“generated batch includes non-PAID orders”（`35` 行），与“支付成功且未结算订单”的设计口径相反。

### 6. 结算批次未包含退款数据，总退款金额固定传入 0

- 代码定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/SettlementBatchService.java:38`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/SettlementBatchService.java:44`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/SettlementBatchService.java:105`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/SettlementBatchService.java:106`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/SettlementBatchService.java:141`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/SettlementBatchService.java:142`
- 设计要求定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/14-发票与结算设计.md:37` 至 `D:/Desktop/work/HW-ICT-CMP-04/design-docs/14-发票与结算设计.md:41`
- 不一致原因：设计要求结算批次包含退款数据。当前 `SettlementBatchService` 依赖中没有 `RefundRecordRepository`，生成批次时未查询退款记录，`totalRefundAmount` 固定为 `BigDecimal.ZERO`。
- 详细解析：`SettlementBatchService` 字段和构造函数只注入了结算批次、结算订单项、支付记录、发票记录和审计服务（`38` 至 `48` 行），没有退款仓库。生成有支付记录的批次时，`105` 至 `106` 行调用 `createBatchEntity(batchDate, totalPaymentAmount, BigDecimal.ZERO, totalInvoiceAmount, orderCount)`，使退款合计恒为 0；`141` 至 `142` 行再将传入的 `totalRefund` 写入批次。因此实际结算批次不包含退款数据。

## 未发现不一致的检查点

1. 支付规则：发起支付通过 `PaymentValidator` 校验支付金额必须等于订单应付金额，未发现“部分支付/超额支付被接受”的实现。
2. 支付校验：订单存在、状态可支付、金额一致、支付方式必填且枚举支持、未重复成功支付等校验均有对应实现。
3. 支付回调签名校验和幂等键：支付回调处理开始时校验签名，并使用 `@Idempotent` 及回调序列处理重复回调，未发现与本检查范围内设计要求冲突的实现。
4. 发票部分开票和累计金额：单张发票金额会与剩余可开票金额比较，累计已开票成功金额不会超过实付金额。
5. 发票税额舍入：代码按税额计算并通过金额工具保留两位；除默认税率错误外，未发现舍入规则与检查要求冲突。
6. REST API：设计列出的 10 个支付/退款/发票/结算端点均有控制器方法实现。
7. 跨模块约束：支付发起通过 `OrderQueryService` 查询订单，支付状态更新通过 `OrderPaymentStatusUpdater`；未发现支付模块直接查询或更新订单数据库表。

## 无法确认项

1. 结算批次“生成后不可修改”只能从当前未发现更新 API 间接推断；实体仍有公开 setter，仓库也可保存同一实体，代码层未看到明确的不可修改状态校验或作废后重建约束，因此无法确认该要求已被强制保证。

## 汇总

- 不一致点数量：6
- 无法确认项数量：1
