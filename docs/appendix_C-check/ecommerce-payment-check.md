# ecommerce-payment - 附录C 一致性检查

## 检查范围
- 模块：ecommerce-payment
- 附录：附录C
- 输入资料：
  - `README.md` 中比赛边界、修改边界、冻结契约、检查口径相关内容（设计文档为验收基准、公开用例不覆盖全部验收范围等）
  - `design-docs/附录C-数据模型.md` 全文
  - `code/ecommerce-payment/src/main/java` 下所有源文件
  - `code/ecommerce-payment/src/test/java` 下所有测试源文件
  - `code/ecommerce-payment/src/main/resources`、`code/ecommerce-payment/src/test/resources`（当前模块未发现资源配置文件目录/文件）
  - `code/ecommerce-payment/pom.xml`
  - `code/pom.xml`

## 检查结论
- 共发现 3 处不一致

## 不一致明细

### 1. 支付数据模型表名、金额字段、金额精度与状态枚举不一致
- 设计要求定位：`design-docs/附录C-数据模型.md:125`、`design-docs/附录C-数据模型.md:129`-`design-docs/附录C-数据模型.md:135`
- 代码定位：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/PaymentRecord.java:14`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/PaymentRecord.java:20`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/PaymentRecord.java:28`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/PaymentRecord.java:40`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/PaymentStatus.java:3`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/PaymentStatus.java:8`
- 不一致说明：附录C要求支付表为 `payments`，包含 `amount DECIMAL(18,2)`、`method VARCHAR`、`status VARCHAR` 且状态值为 `CREATED/SUCCESS/FAILED/CLOSED`、`paid_at TIMESTAMP`；当前实现实体映射到 `payment_records`，金额拆为 `orderAmount`/`paidAmount` 且均为 `precision = 12, scale = 2`，未实现设计字段 `amount DECIMAL(18,2)`，状态枚举为 `PENDING/SUCCESS/FAILED/REFUNDED`。
- 原因分析：设计要求明确了支付域的表名、字段名、金额类型精度和状态集合；当前实现的表名、金额字段命名/结构、金额精度和状态集合均与设计不一致，属于结构不符、命名不符、类型不符、状态枚举不符。

### 2. 退款数据模型表名、字段、金额精度与状态枚举不一致
- 设计要求定位：`design-docs/附录C-数据模型.md:137`、`design-docs/附录C-数据模型.md:141`-`design-docs/附录C-数据模型.md:146`
- 代码定位：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/RefundRecord.java:14`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/RefundRecord.java:23`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/RefundRecord.java:25`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/RefundRecord.java:48`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/RefundStatus.java:3`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/RefundStatus.java:10`
- 不一致说明：附录C要求退款表为 `refunds`，包含 `refund_no`、`order_id`、`paid_amount DECIMAL(18,2)`、`refund_amount DECIMAL(18,2)`、`status`，状态值为 `APPLIED/REVIEWED/ACCEPTED/REFUNDED/REJECTED`；当前实现实体映射到 `refund_records`，未实现 `paidAmount/paid_amount` 字段，`refundAmount` 精度为 `precision = 12, scale = 2`，状态枚举为 `PENDING_REVIEW/APPROVED/WAITING_WAREHOUSE_ACCEPT/WAREHOUSE_ACCEPTED/COMPLETED/REJECTED`。
- 原因分析：设计要求明确了退款域表名、必备字段、金额精度和状态集合；当前实现表名不同，缺失 `paid_amount`，金额精度不是 `DECIMAL(18,2)`，状态集合和命名与设计不一致，属于结构不符、缺失、类型不符、状态枚举不符。

### 3. 发票数据模型表名、字段命名、金额/税率精度与状态枚举不一致
- 设计要求定位：`design-docs/附录C-数据模型.md:148`、`design-docs/附录C-数据模型.md:152`-`design-docs/附录C-数据模型.md:159`
- 代码定位：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/InvoiceRecord.java:14`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/InvoiceRecord.java:21`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/InvoiceRecord.java:23`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/InvoiceRecord.java:56`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/InvoiceStatus.java:3`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/InvoiceStatus.java:6`
- 不一致说明：附录C要求发票表为 `invoices`，字段包含 `title VARCHAR`、`amount DECIMAL(18,2)`、`tax_rate DECIMAL(6,4)`、`tax_amount DECIMAL(18,2)`，状态值为 `ISSUED/VOIDED`；当前实现实体映射到 `invoice_records`，字段使用 `invoiceTitle`、`invoiceAmount`，金额和税额精度为 `precision = 12, scale = 2`，税率为 `precision = 12, scale = 2`，状态枚举为 `ISSUED/CANCELLED`。
- 原因分析：设计要求明确了发票域表名、字段命名、金额精度、税率精度和状态集合；当前实现表名不同，字段命名不符合 `title/amount`，金额和税率精度不符合 `DECIMAL(18,2)`、`DECIMAL(6,4)`，状态值 `CANCELLED` 与设计要求 `VOIDED` 不一致，属于结构不符、命名不符、类型不符、状态枚举不符。
