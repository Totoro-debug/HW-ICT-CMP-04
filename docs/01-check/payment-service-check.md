# M7 payment-service 一致性审查报告

审查范围：`code/ecommerce-payment/`、`code/pom.xml`，以及题目指定的 `README.md` / `design-docs/01-项目概述.md` 行号范围。

发现不一致条数：7

## 1. 支付模块直接访问订单表，违反模块协作边界

1. 实现位置：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:139-169`
2. 设计依据：`design-docs/01-项目概述.md` 第 1 章/第 7 行；第 4 章/第 42 行
3. 不一致内容：实现通过 `JdbcTemplate` 执行 `SELECT ... FROM orders WHERE id = ?`，在 payment-service 内直接读取订单模块数据库表；设计要求模块之间不得随意访问彼此数据库表或 Repository，应通过公开本地接口、REST API、领域服务或 Spring ApplicationEvent 协作。M7 职责是支付、退款、回调、对账、发票、结算，不包含直接读取订单表。
4. 原因分析与影响：该实现绕过了订单模块公开协作接口，使 payment-service 与订单表结构强耦合；订单表字段或语义变化会直接破坏支付校验，也削弱模块边界与一致性验收要求。

## 2. 支付金额未强制等于订单应付金额，且未使用规定错误码

1. 实现位置：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentValidator.java:48-52`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:87-89`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentCallbackService.java:78-79`
2. 设计依据：`design-docs/01-项目概述.md` 第 5 章/第 54 行；`README.md` 第 7.2 节/第 225 行
3. 不一致内容：支付校验只判断请求金额是否大于 0，没有校验 `request.getAmount()` 必须等于订单 `payableAmount`；创建支付记录时仍保存请求金额为 `paidAmount`；回调成功时又直接用回调金额覆盖 `paidAmount`。文档要求支付金额必须等于订单应付金额且不支持部分支付，金额不一致应使用 `PAYMENT_AMOUNT_MISMATCH`。
4. 原因分析与影响：用户或回调可提交小于/大于订单应付金额的金额并进入支付流程，造成部分支付或超额支付被接受；错误码也无法满足冻结错误码契约，黑盒用例按 `PAYMENT_AMOUNT_MISMATCH` 断言时会失败。

## 3. 支付回调接口未实现“签名”认证要求

1. 实现位置：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/PaymentController.java:52-57`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentCallbackService.java:41-64`
2. 设计依据：`README.md` 第 6.6 节/第 146 行；`README.md` 第 6 章/第 75 行
3. 不一致内容：冻结 API 契约规定 `POST /api/v1/payment/callback` 的认证方式为“签名”。实现中 Controller 未要求或校验签名，Service 仅记录 `request.getSignature()`，未验证签名有效性。
4. 原因分析与影响：任意调用者只要知道 `paymentNo` 即可伪造成功/失败回调，改变支付状态；同时不满足冻结契约中的认证要求。

## 4. 支付成功后置动作同步执行并可能阻塞支付主流程

1. 实现位置：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:29-38`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:113-131`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:172-202`
2. 设计依据：`design-docs/01-项目概述.md` 第 5 章/第 55 行；第 2 章/第 19 行
3. 不一致内容：`confirmPayment` 在同一个事务中同步执行物流创建、积分发放、通知发送，然后才发布 `PaymentSucceededEvent`；类注释也明确说明这些后置动作同步执行且失败会导致支付确认事务回滚。设计要求支付成功后的物流创建、积分发放、通知发送等后置动作通过本地事件异步触发，不得阻塞支付主流程。
4. 原因分析与影响：非核心后置动作失败会回滚或阻塞支付确认，导致支付主流程被物流、积分或通知能力耦合影响，不符合异步本地事件协作原则。

## 5. 退款审核通过后直接完成退款，绕过仓库验收

1. 实现位置：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundService.java:127-137`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundService.java:168-190`
2. 设计依据：`design-docs/01-项目概述.md` 第 5 章/第 57 行；`README.md` 第 6.6 节/第 149-150 行；`README.md` 第 7.2 节/第 227 行
3. 不一致内容：商家审核通过时 `approveRefund` 将状态设为 `APPROVED` 后立即调用 `processRefund`，而 `processRefund` 直接将退款设为 `COMPLETED` 并更新支付状态为 `REFUNDED`。虽然存在 `/warehouse-accept` 接口，但审核通过路径没有进入 `WAITING_WAREHOUSE_ACCEPT`，也没有使用 `REFUND_WAITING_WAREHOUSE_ACCEPT` 表达需等待仓库验收。
4. 原因分析与影响：退款可在商家审核后立即完成，未经过仓库验收；这破坏“审核 + 仓库验收后才可退款”的业务约束，使冻结的仓库验收 API 在核心流程中失去必要性。

## 6. 发票开具忽略请求金额，不支持按请求部分开票，并可能使累计开票超额

1. 实现位置：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/InvoiceService.java:51-64`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/InvoiceService.java:76-91`；请求字段位置：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/dto/InvoiceRequest.java:9-13`
2. 设计依据：`design-docs/01-项目概述.md` 第 5 章/第 58 行；`README.md` 第 7.2 节/第 229 行
3. 不一致内容：`InvoiceRequest` 定义了 `invoiceAmount`，但 `generateInvoice` 将 `invoiceAmount` 固定为成功支付的全额 `paidAmount`，没有使用请求开票金额；在已有部分开票记录时，后续仍按全额支付金额再开票，只检查“是否已完全开票”，未校验“本次开票金额 <= 剩余可开金额”。
4. 原因分析与影响：无法按请求金额进行部分开票；若已开过部分发票，下一次仍可能按全额开票，导致累计开票金额超过订单实付金额，违反发票累计金额约束。

## 7. 发票超额错误码与冻结契约不一致

1. 实现位置：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/InvoiceService.java:71-73`
2. 设计依据：`README.md` 第 7.2 节/第 229 行
3. 不一致内容：文档规定开票金额超过剩余可开金额时错误码为 `INVOICE_AMOUNT_EXCEEDED`；实现抛出 `INVOICE_LIMIT_EXCEEDED`。
4. 原因分析与影响：即使触发发票额度限制，返回错误码也不符合冻结错误码契约；按文档错误码校验的调用方或黑盒用例会无法识别该错误。
