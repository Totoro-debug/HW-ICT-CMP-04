# ecommerce-payment - 附录D 一致性检查

## 检查范围
- 模块：ecommerce-payment
- 附录：附录D
- 输入资料：
  - `README.md` 中比赛边界、冻结契约、错误响应、检查口径相关内容：包括设计文档为验收基准、公开用例不覆盖全部验收范围、PUB-108 后置动作失败不阻塞支付等要求。
  - `design-docs/附录D-本地事件契约.md` 全文。
  - `code/ecommerce-payment/src/main/java` 下全部源文件。
  - `code/ecommerce-payment/src/test/java` 下全部测试源文件。
  - `code/ecommerce-payment` 当前未发现 `src/main/resources` / `src/test/resources` 配置文件目录；已检查配置类 `PaymentConfig`、`PaymentAutoConfiguration`。
  - 当前模块 POM：`code/ecommerce-payment/pom.xml`。
  - 整个项目 POM：`code/pom.xml`。

## 检查结论
- 共发现 2 处不一致。
- `PaymentSucceededEvent` 的发布方确为 payment-service，当前模块通过 `PaymentService.confirmPayment` 发布该事件；后置监听器失败不阻塞支付主流程方面，未发现与附录D/README 直接要求相冲突的实现。

## 不一致明细

### 1. PaymentSucceededEvent 缺少部分事件通用字段
- 设计要求定位：`design-docs/附录D-本地事件契约.md:3`、`design-docs/附录D-本地事件契约.md:5`-`design-docs/附录D-本地事件契约.md:11`、`design-docs/附录D-本地事件契约.md:30`-`design-docs/附录D-本地事件契约.md:34`
- 代码定位：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:7`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:9`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:13`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:14`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:21`
- 不一致说明：附录D要求本地事件具备通用字段 `eventId`、`eventType`、`occurredAt`、`aggregateId`、`traceId`，且 `PaymentSucceededEvent` 是 payment-service 发布的本地事件。当前 `PaymentSucceededEvent` 只在自身声明 `paymentNo`、`orderId`、`userId`、`paidAmount`，构造函数也只接收并赋值这些字段；其父类可提供 `eventId`、`occurredAt`，但当前事件类未提供 `eventType`、`aggregateId`、`traceId`。
- 原因分析：设计要求的事件通用字段应在 `PaymentSucceededEvent` 实例上齐全；当前实现缺少 `eventType`、`aggregateId`、`traceId` 三个通用字段，属于字段缺失/结构不符。

### 2. PaymentSucceededEvent 载荷缺少 paidAt
- 设计要求定位：`design-docs/附录D-本地事件契约.md:30`-`design-docs/附录D-本地事件契约.md:43`
- 代码定位：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:9`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:13`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:23`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:26`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:98`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:101`
- 不一致说明：附录D要求 `PaymentSucceededEvent` 载荷包含 `paymentNo`、`orderId`、`paidAmount`、`paidAt`。当前事件类只有 `paymentNo`、`orderId`、`userId`、`paidAmount` 字段及 getter，没有 `paidAt` 字段或 getter；发布事件时也只传入 `paymentNo`、`orderId`、`userId`、`paidAmount`，未传入支付成功时间。
- 原因分析：设计要求支付成功事件载荷必须携带支付时间 `paidAt`；当前支付记录虽然在回调成功处理中设置支付时间，但事件发布对象未承载该字段，属于载荷字段缺失/结构不符。
