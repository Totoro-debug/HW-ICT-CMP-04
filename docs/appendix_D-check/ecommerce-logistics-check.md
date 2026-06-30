# ecommerce-logistics - 附录D 一致性检查

## 检查范围
- 模块：ecommerce-logistics
- 附录：附录D
- 输入资料：
  - `README.md` 中比赛边界、冻结契约、错误响应、检查口径相关内容，包括设计文档为验收基准、公开用例不覆盖全部验收范围、后置动作失败不阻塞支付等要求。
  - `design-docs/附录D-本地事件契约.md` 全文。
  - `code/ecommerce-logistics/src/main/java` 下所有源文件。
  - `code/ecommerce-logistics/src/test/java` 下所有测试源文件。
  - `code/ecommerce-logistics/src/main/resources` 配置文件（当前未发现资源配置文件）。
  - `code/ecommerce-logistics/pom.xml`。
  - `code/pom.xml`。

## 检查结论
- 共发现 3 处不一致。

## 不一致明细

### 1. logistics-service 未实现 `PaymentSucceededEvent` 监听入口
- 设计要求定位：`design-docs/附录D-本地事件契约.md:30`、`design-docs/附录D-本地事件契约.md:32`、`design-docs/附录D-本地事件契约.md:34`、`design-docs/附录D-本地事件契约.md:36`-`design-docs/附录D-本地事件契约.md:43`
- 代码定位：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:7`、`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:36`-`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:40`、`code/ecommerce-logistics/src/test/java/com/ecommerce/logistics/event/OrderPaidShipmentListenerTest.java:63`-`code/ecommerce-logistics/src/test/java/com/ecommerce/logistics/event/OrderPaidShipmentListenerTest.java:68`
- 不一致说明：附录D要求 `PaymentSucceededEvent` 由 `payment-service` 发布，监听方包含 `logistics-service`，载荷字段为 `paymentNo`、`orderId`、`paidAmount`、`paidAt`。当前 logistics 模块的事件监听器仅导入并监听 `com.ecommerce.common.event.OrderPaidEvent`，`onOrderPaid(OrderPaidEvent event)` 只处理 `OrderPaidEvent`；测试中还明确断言 `OrderPaidShipmentListener` 不存在 `onPaymentSucceeded` 入口。
- 原因分析：设计要求 logistics-service 直接监听 `PaymentSucceededEvent` 并按该事件契约消费支付成功事件；当前实现没有 `PaymentSucceededEvent` 监听方法，也没有消费其 `paymentNo`、`orderId`、`paidAmount`、`paidAt` 载荷。因此属于缺失/行为不符。

### 2. logistics-service 消费的 `OrderPaidEvent` 载荷与附录D不匹配，缺少 `items`
- 设计要求定位：`design-docs/附录D-本地事件契约.md:13`、`design-docs/附录D-本地事件契约.md:15`-`design-docs/附录D-本地事件契约.md:18`、`design-docs/附录D-本地事件契约.md:19`-`design-docs/附录D-本地事件契约.md:27`
- 代码定位：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:38`-`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:40`、`code/ecommerce-logistics/src/test/java/com/ecommerce/logistics/event/OrderPaidShipmentListenerTest.java:38`-`code/ecommerce-logistics/src/test/java/com/ecommerce/logistics/event/OrderPaidShipmentListenerTest.java:43`；跨模块事件定义：`code/ecommerce-common/src/main/java/com/ecommerce/common/event/OrderPaidEvent.java:11`-`code/ecommerce-common/src/main/java/com/ecommerce/common/event/OrderPaidEvent.java:17`、`code/ecommerce-common/src/main/java/com/ecommerce/common/event/OrderPaidEvent.java:25`-`code/ecommerce-common/src/main/java/com/ecommerce/common/event/OrderPaidEvent.java:39`
- 不一致说明：附录D要求 `OrderPaidEvent` 载荷包含 `orderId`、`userId`、`paidAmount`、`items`。当前 logistics 监听器接收的 `OrderPaidEvent` 只在处理时读取 `event.getOrderId()`；测试构造事件时参数为 `orderId`、`userId`、`paymentNo`、`paidAmount`，没有 `items`。跨模块事件定义也只有 `orderId`、`userId`、`paymentNo`、`paidAmount` 字段和 getter，未提供 `items`。
- 原因分析：设计要求事件载荷必须包含订单商品明细 `items`，当前实现所消费的事件契约缺少该字段且额外使用 `paymentNo` 替代了设计中的商品明细位置；logistics 侧也未按 `items` 载荷消费。该问题属于载荷字段缺失/结构不符。

### 3. logistics-service 发布的 `ShipmentDeliveredEvent` 通用字段不齐全
- 设计要求定位：`design-docs/附录D-本地事件契约.md:3`-`design-docs/附录D-本地事件契约.md:11`、`design-docs/附录D-本地事件契约.md:45`-`design-docs/附录D-本地事件契约.md:57`
- 代码定位：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/ShipmentDeliveredEvent.java:10`-`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/ShipmentDeliveredEvent.java:24`、`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/ShipmentService.java:300`-`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/ShipmentService.java:304`；跨模块基类：`code/ecommerce-common/src/main/java/com/ecommerce/common/event/AbstractDomainEvent.java:14`-`code/ecommerce-common/src/main/java/com/ecommerce/common/event/AbstractDomainEvent.java:29`
- 不一致说明：附录D要求所有本地事件通用字段包含 `eventId`、`eventType`、`occurredAt`、`aggregateId`、`traceId`；并要求 `ShipmentDeliveredEvent` 载荷包含 `orderId`、`shipmentId`、`deliveredAt`。当前 `ShipmentDeliveredEvent` 载荷包含 `shipmentId`、`orderId`、`deliveredAt`，但事件类仅继承基类提供的 `eventId`、`occurredAt`，自身没有 `eventType`、`aggregateId`、`traceId` 字段；发布处也只传入 `shipmentId`、`orderId`、`userId`、`deliveredAt`，未传入 `eventType`、`aggregateId`、`traceId`。
- 原因分析：设计要求 `ShipmentDeliveredEvent` 作为 logistics-service 发布事件时必须具备完整通用字段。当前实现只有部分通用字段，缺少 `eventType`、`aggregateId`、`traceId`，因此属于通用字段缺失/结构不符。