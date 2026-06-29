# 第4批支付/退款/发票/结算修复结果

## 负责模块与 R-ID

- 支付/退款/发票/结算：`R-PAYMENT-01` ~ `R-PAYMENT-06`

## 修改的主要文件

- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\PaymentService.java`
- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\RefundCalculator.java`
- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\RefundService.java`
- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\RefundStageService.java`
- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\InvoiceService.java`
- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\SettlementBatchService.java`
- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\entity\PaymentRecord.java`
- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\entity\RefundRecord.java`
- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\repository\PaymentRecordRepository.java`
- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\repository\RefundRecordRepository.java`
- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\test\java\com\ecommerce\payment\service\PaymentServiceTest.java`
- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\test\java\com\ecommerce\payment\service\RefundCalculatorTest.java`
- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\test\java\com\ecommerce\payment\service\RefundServiceTest.java`
- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\test\java\com\ecommerce\payment\service\SettlementBatchServiceTest.java`
- `D:\Desktop\work\HW-ICT-CMP-04\docs\04-15-check\checklist.md`

## 修复摘要

- `R-PAYMENT-01`：`PaymentService.confirmPayment` 现在同时发布 `PaymentSucceededEvent` 和统一的 `com.ecommerce.common.event.OrderPaidEvent`。支付回调层仍在 `SUCCESS` 时短路，避免重复回调重复发布或重复扣减；事件发布使用非强一致发布，监听器失败不回滚支付确认。
- `R-PAYMENT-02`：`RefundCalculator` 移除固定 1 元扣减，退款金额只按 `paidAmount × (1 - refundFeeRate)` 计算并落分。
- `R-PAYMENT-03`：仓库验收接口返回 `WAREHOUSE_ACCEPTED`，并将财务退款拆为内部异步阶段 `executeFinanceRefund`；财务阶段成功后才将退款置为 `COMPLETED`、支付单置为 `REFUNDED` 并发布 `RefundCompletedEvent`。
- `R-PAYMENT-04`：`InvoiceService` 默认税率改为 `0.06`，保留 `RuntimeConfigRegistry.getBigDecimal("invoice.tax-rate", ...)` 运行时配置覆盖。
- `R-PAYMENT-05`：结算批次改为查询 `SUCCESS` 且 `settledAt is null` 的支付记录；批次生成并写明细后回写支付记录的 `settledAt` 和 `settlementBatchNo`，防止重复纳入。
- `R-PAYMENT-06`：结算批次注入 `RefundRecordRepository`，按 `COMPLETED`、完成时间自然日且未结算退款汇总 `totalRefundAmount`，并回写退款结算标记。

## 已执行测试命令与结果

- `mvn -f D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml -pl ecommerce-payment -Dtest=PaymentServiceTest,RefundCalculatorTest,RefundServiceTest,InvoiceServiceTest,SettlementBatchServiceTest test`
  - 结果：BUILD SUCCESS，20 tests，0 failures，0 errors，0 skipped。
- `mvn -f D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml -pl ecommerce-payment test`
  - 结果：BUILD SUCCESS，50 tests，0 failures，0 errors，0 skipped。

## 未完成项、风险与后续协调

- `OrderPaidEvent` 已由支付侧发布；物流侧需保持仅监听 `OrderPaidEvent` 创建发货单，避免同时监听 `PaymentSucceededEvent` 造成双触发。
- 财务退款当前作为内部异步阶段触发；如异步执行失败，仓库验收仍保持已提交。后续可结合失败事件记录或定时重试增强长期停留 `WAREHOUSE_ACCEPTED` 的恢复能力。
- 结算新增支付/退款内部结算标记字段。当前生成批次会防重；未来如果支持批次作废重建，需要同时回滚或清理对应支付/退款结算标记。
