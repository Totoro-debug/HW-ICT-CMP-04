# ecommerce-order - 附录D 一致性检查

## 检查范围
- 模块：ecommerce-order
- 附录：附录D
- 输入资料：
  - `README.md` 中比赛边界、冻结契约、错误响应、检查口径相关内容（尤其 `README.md:9`、`README.md:35`-`README.md:40`、`README.md:73`-`README.md:75`、`README.md:237`、`README.md:277`、`README.md:281`）
  - `design-docs/附录D-本地事件契约.md` 全文
  - `code/ecommerce-order/src/main/java` 下全部源文件
  - `code/ecommerce-order/src/test/java` 下全部测试源文件
  - 当前模块配置文件检查：`code/ecommerce-order` 下未发现 `application*.yml` / `application*.yaml` / `application*.properties`
  - 当前模块 POM：`code/ecommerce-order/pom.xml`
  - 整个项目 POM：`code/pom.xml`

## 检查结论
- 共发现 3 处不一致。

## 不一致明细

### 1. OrderPaidEvent 事件契约字段不完整
- 设计要求定位：`design-docs/附录D-本地事件契约.md:3`-`design-docs/附录D-本地事件契约.md:11` 要求本地事件通用字段包含 `eventId`、`eventType`、`occurredAt`、`aggregateId`、`traceId`；`design-docs/附录D-本地事件契约.md:13`-`design-docs/附录D-本地事件契约.md:28` 要求 `OrderPaidEvent` 由 `order-service` 发布，载荷包含 `orderId`、`userId`、`paidAmount`、`items`，且监听器失败不得回滚支付状态。
- 代码定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/event/OrderPaidEvent.java:8`-`code/ecommerce-order/src/main/java/com/ecommerce/order/event/OrderPaidEvent.java:13` 仅作为 `com.ecommerce.common.event.OrderPaidEvent` 的别名并透传 `orderId`、`userId`、`paymentNo`、`paidAmount`；其父类 `code/ecommerce-common/src/main/java/com/ecommerce/common/event/AbstractDomainEvent.java:14`-`code/ecommerce-common/src/main/java/com/ecommerce/common/event/AbstractDomainEvent.java:29` 仅提供 `eventId`、`occurredAt`；父事件 `code/ecommerce-common/src/main/java/com/ecommerce/common/event/OrderPaidEvent.java:11`-`code/ecommerce-common/src/main/java/com/ecommerce/common/event/OrderPaidEvent.java:23` 仅包含 `orderId`、`userId`、`paymentNo`、`paidAmount`；发布点 `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPaymentEventHandler.java:129`-`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPaymentEventHandler.java:131` 也只传入上述字段。
- 不一致说明：当前 `OrderPaidEvent` 实现缺少设计要求的通用字段 `eventType`、`aggregateId`、`traceId`，并缺少载荷字段 `items`。当前额外包含 `paymentNo` 不构成问题本身，但不能替代设计要求字段。
- 原因分析：设计要求 `OrderPaidEvent` 必须携带完整通用字段和订单商品明细；当前事件模型只继承到 `eventId`、`occurredAt`，事件载荷也只建模为订单、用户、支付单号和金额。该问题属于字段缺失导致的结构不符。

### 2. order-service 未按 PaymentSucceededEvent 事件契约作为监听方消费事件
- 设计要求定位：`design-docs/附录D-本地事件契约.md:30`-`design-docs/附录D-本地事件契约.md:43` 要求 `PaymentSucceededEvent` 由 `payment-service` 发布、`order-service` 监听，载荷包含 `paymentNo`、`orderId`、`paidAmount`、`paidAt`。
- 代码定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/listener/OrderEventListener.java:53`-`code/ecommerce-order/src/main/java/com/ecommerce/order/listener/OrderEventListener.java:104` 只监听 `OrderCreatedEvent`、`OrderPaidEvent`、`OrderCancelledEvent` 及其 fallback，没有 `PaymentSucceededEvent` 监听方法；`code/ecommerce-order/src/main/java/com/ecommerce/order/query/OrderPaymentStatusUpdater.java:17` 仅暴露 `markAsPaid(Long orderId, String paymentNo)`；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderQueryServiceImpl.java:125`-`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderQueryServiceImpl.java:128` 通过直接接口调用处理支付成功，未接收 `paidAmount`、`paidAt` 事件载荷；支付事件类本身 `code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:9`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:26` 也没有 `paidAt` 字段。
- 不一致说明：当前订单模块没有 `PaymentSucceededEvent` 的 Spring 事件监听器，支付成功状态更新是通过跨模块接口 `OrderPaymentStatusUpdater` 直接调用完成；该接口和实现未按设计事件载荷消费 `paidAmount`、`paidAt`，其中 `paidAt` 在支付事件类中也不存在。
- 原因分析：设计要求 order-service 是 `PaymentSucceededEvent` 的监听方，并按事件名及载荷处理支付成功；当前实现采用同步接口而非事件监听，且事件/接口载荷不完整。该问题属于监听方缺失、载荷字段缺失和事件语义不符。

### 3. order-service 未按 ShipmentDeliveredEvent 事件契约作为监听方消费事件
- 设计要求定位：`design-docs/附录D-本地事件契约.md:45`-`design-docs/附录D-本地事件契约.md:57` 要求 `ShipmentDeliveredEvent` 由 `logistics-service` 发布、`order-service` 监听，载荷包含 `orderId`、`shipmentId`、`deliveredAt`。
- 代码定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/listener/OrderEventListener.java:1`-`code/ecommerce-order/src/main/java/com/ecommerce/order/listener/OrderEventListener.java:18` 的导入只涉及订单自身事件监听相关类型，没有导入 `ShipmentDeliveredEvent`；`code/ecommerce-order/src/main/java/com/ecommerce/order/listener/OrderEventListener.java:53`-`code/ecommerce-order/src/main/java/com/ecommerce/order/listener/OrderEventListener.java:104` 只监听 `OrderCreatedEvent`、`OrderPaidEvent`、`OrderCancelledEvent`，没有 `ShipmentDeliveredEvent` 监听方法；当前模块源文件中也未发现 `ShipmentDeliveredEvent` 引用。
- 不一致说明：当前订单模块没有作为 `ShipmentDeliveredEvent` 监听方消费物流签收事件，也没有读取设计要求的 `orderId`、`shipmentId`、`deliveredAt` 载荷来驱动订单侧签收相关状态或记录。
- 原因分析：设计要求 order-service 监听物流模块发布的签收事件；当前实现没有对应事件订阅入口。该问题属于监听方缺失和事件语义不符。
