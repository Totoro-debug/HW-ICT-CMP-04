# 附录D 修复方案汇总

## 总体说明

本方案仅覆盖 `docs/appendix_D-check/*-check.md` 已报告的附录D不一致项，不新增报告外问题，不建议修改冻结 REST API 契约或 `design-docs/`。修复边界遵循 `README.md:26-40` 的修改边界、`README.md:73-76` 的冻结 API 契约、`README.md:184-199` 的事件失败查询/故障注入支撑接口，以及 `README.md:277-281` 的“后置动作失败不阻塞支付、设计文档为验收基准”。

附录D事件语义依据为：通用字段 `eventId`、`eventType`、`occurredAt`、`aggregateId`、`traceId`（`design-docs/附录D-本地事件契约.md:3-11`）；`OrderPaidEvent` 发布/监听/载荷与失败不回滚支付（`design-docs/附录D-本地事件契约.md:13-28`）；`PaymentSucceededEvent` 发布/监听/载荷（`design-docs/附录D-本地事件契约.md:30-44`）；`ShipmentDeliveredEvent` 发布/监听/载荷（`design-docs/附录D-本地事件契约.md:45-58`）；`ReviewApprovedEvent` 发布/监听/载荷（`design-docs/附录D-本地事件契约.md:59-73`）。

无不一致模块：`ecommerce-app`（`docs/appendix_D-check/ecommerce-app-check.md:15-21`）、`ecommerce-cart`（`docs/appendix_D-check/ecommerce-cart-check.md:15-23`）、`ecommerce-product`（`docs/appendix_D-check/ecommerce-product-check.md:15-23`）、`ecommerce-promotion`（`docs/appendix_D-check/ecommerce-promotion-check.md:14-24`）、`ecommerce-user`（`docs/appendix_D-check/ecommerce-user-check.md:15-25`）。

## 修复方案明细

### R1. 补齐公共事件模型的附录D通用字段

- 所属模块：`ecommerce-common`，并影响所有继承 `AbstractDomainEvent` 的附录D事件发布/监听模块。
- 覆盖问题：
  - common：公共事件基类未支持全部附录D通用字段（`docs/appendix_D-check/ecommerce-common-check.md:22-26`）。
  - order：`OrderPaidEvent` 缺少通用字段部分（`docs/appendix_D-check/ecommerce-order-check.md:20-24`）。
  - logistics：`ShipmentDeliveredEvent` 通用字段不齐全（`docs/appendix_D-check/ecommerce-logistics-check.md:32-36`）。
  - loyalty：本地事件通用字段实现不完整（`docs/appendix_D-check/ecommerce-loyalty-check.md:20-24`）。
  - payment：`PaymentSucceededEvent` 缺少部分事件通用字段（`docs/appendix_D-check/ecommerce-payment-check.md:21-25`）。
  - review：`ReviewApprovedEvent` 发布对象缺少部分通用字段（`docs/appendix_D-check/ecommerce-review-check.md:21-31`）。
- 设计依据定位：`design-docs/附录D-本地事件契约.md:3-11`。
- 当前实现定位：`code/ecommerce-common/src/main/java/com/ecommerce/common/event/AbstractDomainEvent.java:14-20` 仅有 `eventId`、`occurredAt`；getter 位于 `AbstractDomainEvent.java:23-29`。
- 修复目标：所有附录D本地事件实例均稳定暴露 `eventId`、`eventType`、`occurredAt`、`aggregateId`、`traceId`，监听器和失败记录可统一读取。
- 修复方案：
  1. 在 `AbstractDomainEvent` 增加 `private final String eventType`、`private final String aggregateId`、`private final String traceId` 及 getter。
  2. 保留现有 `AbstractDomainEvent(Object source)` 构造器用于兼容非附录D事件，并在其中默认 `eventType = getClass().getSimpleName()`、`aggregateId = null`、`traceId = null`。
  3. 新增受保护构造器 `AbstractDomainEvent(Object source, String eventType, String aggregateId, String traceId)`，由附录D事件类显式传入事件名、聚合根 ID 和链路 ID；`occurredAt` 继续在基类统一生成或允许扩展构造器传入。
  4. 附录D事件类构造时按事件语义传值：`OrderPaidEvent`/`PaymentSucceededEvent` 的 `aggregateId` 使用 `orderId` 字符串；`ShipmentDeliveredEvent` 可使用 `orderId` 字符串，必要时在 payload 保留 `shipmentId`；`ReviewApprovedEvent` 可使用 `reviewId` 或 `orderId`，建议使用发布方聚合 `reviewId` 字符串并在 payload 保留 `orderId`。
  5. `traceId` 从可用上下文获取；若项目无统一 trace 上下文，先在事件构造处生成或传入 `eventId` 派生值，保证字段非缺失，并后续可替换为请求链路 ID。
- 影响范围：`ecommerce-common` 事件基类；`ecommerce-payment`、`ecommerce-order`、`ecommerce-logistics`、`ecommerce-review` 的事件构造点；`ecommerce-inventory`、`ecommerce-loyalty`、`common notification` 的失败记录序列化。
- 注意事项/风险点：不得删除现有构造器导致非附录D事件编译失败；新增字段不能改变 REST API；如果 `traceId` 当前无基础设施，优先用兼容默认值满足事件契约，不引入外部依赖。
- 建议验证方式：运行 `mvn -f code/pom.xml test`；增加/调整事件类单元测试断言 `getEventType()`、`getAggregateId()`、`getTraceId()` 存在并与发布事件名一致；通过 `/api/v1/admin/events/failures` 验证失败记录 payload 包含通用字段。

### R2. 补齐 `OrderPaidEvent` 的 `items` 载荷并更新发布/监听消费

- 所属模块：`ecommerce-common`、`ecommerce-order`、`ecommerce-logistics`、`ecommerce-loyalty`、`ecommerce-payment`（当前也发布该事件）。
- 覆盖问题：
  - common：`OrderPaidEvent` 载荷缺少 `items`（`docs/appendix_D-check/ecommerce-common-check.md:28-32`）。
  - order：`OrderPaidEvent` 事件契约字段不完整的 `items` 部分（`docs/appendix_D-check/ecommerce-order-check.md:20-24`）。
  - logistics：消费的 `OrderPaidEvent` 载荷缺少 `items`（`docs/appendix_D-check/ecommerce-logistics-check.md:26-30`）。
  - loyalty：`OrderPaidEvent` 缺少 `items` 且 loyalty 使用非契约 `paymentNo`（`docs/appendix_D-check/ecommerce-loyalty-check.md:26-30`）。
- 设计依据定位：`design-docs/附录D-本地事件契约.md:13-28`。
- 当前实现定位：公共事件字段与构造器位于 `code/ecommerce-common/src/main/java/com/ecommerce/common/event/OrderPaidEvent.java:11-23`，只有 `orderId`、`userId`、`paymentNo`、`paidAmount`；order 别名构造器位于 `code/ecommerce-order/src/main/java/com/ecommerce/order/event/OrderPaidEvent.java:10-12`；order 发布点位于 `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPaymentEventHandler.java:129-131`；payment 额外发布点位于 `code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:103-106`；logistics 监听位于 `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:36-40`；loyalty 监听与失败记录位于 `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/OrderPaidEventListener.java:40-69`。
- 修复目标：`OrderPaidEvent` 载荷按附录D包含 `orderId`、`userId`、`paidAmount`、`items`，监听器不再依赖 `paymentNo` 作为该事件的必要契约字段，且监听失败不回滚支付状态。
- 修复方案：
  1. 在 `ecommerce-common` 新增公共事件 DTO，例如 `OrderPaidEventItem`，字段至少覆盖订单商品明细验收需要（建议 `skuId`、`productId` 如可取得、`quantity`、`unitPrice`、`payableAmount` 或行金额）；保持 Java 类型可序列化且无 REST 暴露。
  2. 修改 `com.ecommerce.common.event.OrderPaidEvent`：增加 `List<OrderPaidEventItem> items` 字段和 getter；构造器传入 `items`；调用 R1 新构造器设置 `eventType="OrderPaidEvent"`、`aggregateId=orderId`、`traceId`。
  3. 修改 `com.ecommerce.order.event.OrderPaidEvent` 别名构造器，透传 `items`。
  4. 在 `OrderPaymentEventHandler.handlePaymentSuccess` 发布前从订单明细实体/服务组装 `items`，将 `eventPublisher.publish(new OrderPaidEvent(...))` 改为传入完整 `items`。
  5. 对 `PaymentService.confirmPayment` 中当前额外发布的 `OrderPaidEvent` 做一致化处理：优先由 order-service 在订单状态更新后发布 `OrderPaidEvent`；若保留 payment 侧发布以兼容现有流程，也必须通过订单查询能力获取同样 `items` 后再发布，避免同名事件结构不一致。
  6. 修改 `OrderPaidEventListener`（loyalty）：失败记录 payload 增加 `items` 和 R1 通用字段；积分幂等引用不再强依赖 `paymentNo`，可用 `orderId` 或 `eventId` 作为 `bizId`，描述中保留 `orderId`。
  7. 修改 `OrderPaidShipmentListener`（logistics）：继续以 `orderId` 创建发货单，同时在日志/失败记录中记录 `items` 和通用字段；不要因 `items` 解析失败抛出导致主支付流程回滚。
- 影响范围：事件构造签名会影响所有 `new OrderPaidEvent(...)` 调用和相关测试；需同步更新失败记录 replay payload，但 replay 仍可主要使用 `orderId`。
- 注意事项/风险点：不要把 `paymentNo` 当作附录D必需字段；如保留兼容 getter 不应替代 `items`；避免 payment-service 和 order-service 双重发布导致物流/积分重复执行，必要时用事件幂等键 `eventType+aggregateId` 或业务幂等去重。
- 建议验证方式：编译全模块；构造支付成功链路，断言 `OrderPaidEvent.getItems()` 非空；故障注入 logistics/loyalty 后支付仍 `SUCCESS`，失败记录 payload 包含 `items` 与通用字段。

### R3. 修正 `PaymentSucceededEvent` 载荷，增加 `paidAt` 并统一发布事件

- 所属模块：`ecommerce-payment`、`ecommerce-common` notification、`ecommerce-inventory`、`ecommerce-logistics`、`ecommerce-loyalty`、`ecommerce-order`。
- 覆盖问题：
  - payment：`PaymentSucceededEvent` 缺少 `paidAt`（`docs/appendix_D-check/ecommerce-payment-check.md:27-31`）。
  - common notification：未使用设计要求的 `paidAt` 字段（`docs/appendix_D-check/ecommerce-common-check.md:34-38`）。
  - inventory：未按 `PaymentSucceededEvent` 契约消费且失败载荷无 `paidAt`/通用字段（`docs/appendix_D-check/ecommerce-inventory-check.md:21-34`）。
  - logistics：未实现 `PaymentSucceededEvent` 监听入口（`docs/appendix_D-check/ecommerce-logistics-check.md:20-24`）。
  - loyalty：`PaymentSucceededEvent` 缺少 `paidAt` 且依赖非契约 `userId`（`docs/appendix_D-check/ecommerce-loyalty-check.md:32-36`）。
  - order：未按 `PaymentSucceededEvent` 事件契约作为监听方消费事件（`docs/appendix_D-check/ecommerce-order-check.md:26-30`）。
- 设计依据定位：`design-docs/附录D-本地事件契约.md:30-44`。
- 当前实现定位：事件类位于 `code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:9-26`，缺少 `paidAt`；发布点位于 `code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:98-101`，只传 `paymentNo`、`orderId`、`userId`、`paidAmount`；notification 构建通知位于 `code/ecommerce-common/src/main/java/com/ecommerce/common/notification/CoreNotificationEventListener.java:96-103`；order 仍通过 `OrderPaymentStatusUpdater.markAsPaid(Long,String)` 与 `OrderQueryServiceImpl.markAsPaid` 处理（`code/ecommerce-order/src/main/java/com/ecommerce/order/query/OrderPaymentStatusUpdater.java:11-17`，`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderQueryServiceImpl.java:123-129`）。
- 修复目标：`PaymentSucceededEvent` 载荷为 `paymentNo`、`orderId`、`paidAmount`、`paidAt`，并由 payment-service 通过 `ApplicationEventPublisher`/`DomainEventPublisher` 发布；附录D所有监听方均以该事件名监听和处理。
- 修复方案：
  1. 修改 `PaymentSucceededEvent`：增加 `private final LocalDateTime paidAt` 与 getter；构造器改为接收 `paymentNo`、`orderId`、`paidAmount`、`paidAt`，并按 R1 设置 `eventType="PaymentSucceededEvent"`、`aggregateId=orderId`、`traceId`。`userId` 不作为附录D契约字段，若为兼容保留 getter，监听器不得依赖它完成附录D语义。
  2. 在 `PaymentService.confirmPayment` 发布前确保 `payment.getPaidAt()` 已设置；若 `PaymentRecord` 的回调流程已设置支付时间，则使用该值，否则在确认成功处设置 `LocalDateTime.now()` 并保存后发布。
  3. 将 `new PaymentSucceededEvent(...)` 发布调用改为传入 `paidAt`；继续使用当前 `DomainEventPublisher`（内部基于 Spring `ApplicationEventPublisher`）或直接注入 `ApplicationEventPublisher`，但发布语义必须是本地 Spring 事件。
  4. 修改 `CoreNotificationEventListener.buildPaymentSucceededNotification`：读取 `paidAt`，通知变量增加 `paidAt`；不要把 `userId` 作为必需字段，接收人无法从事件获得时可使用订单查询或 `receiverForUser(null)` 兼容，但事件消费必须记录 `paidAt`。
  5. 修改 loyalty 的 `PaymentSucceededEventListener`：方法参数优先改为具体 `PaymentSucceededEvent`，日志和失败记录使用 `paymentNo`、`orderId`、`paidAmount`、`paidAt` 与通用字段；移除对 `userId` 的契约依赖。
  6. inventory、logistics、order 的监听入口分别按 R4、R5、R6 实施，均消费 `paidAt` 并捕获后置异常。
- 影响范围：`PaymentSucceededEvent` 构造器签名、支付成功回调、通知变量、失败记录 payload、相关测试。
- 注意事项/风险点：`paidAt` 应表示支付成功时间，不应用事件创建时间 `occurredAt` 替代；不要修改支付 REST 响应字段；确保监听器异常被捕获/记录，不传播回 payment 事务。
- 建议验证方式：支付回调成功后查询支付状态仍为 `SUCCESS`；事件监听失败故障注入后支付不回滚；失败记录和通知变量包含 `paidAt`。

### R4. 将库存扣减监听从 `OrderPaidEvent` 改为 `PaymentSucceededEvent`

- 所属模块：`ecommerce-inventory`。
- 覆盖问题：库存模块监听了 `OrderPaidEvent`，未按附录D作为 `PaymentSucceededEvent` 监听方实现（`docs/appendix_D-check/ecommerce-inventory-check.md:21-34`）。
- 设计依据定位：`design-docs/附录D-本地事件契约.md:30-44`；后置动作失败不阻塞支付参考 `README.md:277`。
- 当前实现定位：`InventoryOrderPaidEventListener` 导入 `OrderPaidEvent`（`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryOrderPaidEventListener.java:7`），事件类型常量为 `InventoryOrderPaidEventListener:OrderPaidEvent`（同文件 `:23`），监听方法参数为 `OrderPaidEvent`（同文件 `:39-40`），失败 payload 位于同文件 `:85-91`。
- 修复目标：inventory-service 监听 `PaymentSucceededEvent`，根据 `orderId` 扣减库存，失败只记录补偿，不回滚支付主流程。
- 修复方案：
  1. 将监听器类重命名或语义调整为 `InventoryPaymentSucceededEventListener`（文件可重命名），导入 `com.ecommerce.payment.event.PaymentSucceededEvent`。
  2. 将 `EVENT_TYPE` 改为如 `InventoryPaymentSucceededEventListener:PaymentSucceededEvent`，失败记录符合附录D事件名。
  3. 将 `@EventListener public void onOrderPaid(OrderPaidEvent event)` 改为 `@EventListener public void onPaymentSucceeded(PaymentSucceededEvent event)`；如需要事务后执行，可改用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 并根据当前发布事务测试是否可触发。
  4. 业务处理仍调用 `reservationService.deductAfterPayment(event.getOrderId())`；日志记录 `paymentNo`、`orderId`、`paidAmount`、`paidAt`。
  5. `serializeEvent` payload 包含 R1 通用字段及 `paymentNo`、`orderId`、`paidAmount`、`paidAt`；replay 继续读取 `orderId`。
  6. 所有异常在监听器内部捕获并保存 `FailedEventRecord`，不得向外抛出。
- 影响范围：库存事件监听类、失败事件 replay 类型、单元测试中事件构造。
- 注意事项/风险点：若 order 模块仍在支付事务中强一致扣减库存（`OrderPaymentEventHandler.java:118-121`），需评估是否与事件监听重复扣减；本方案仅针对报告项要求 inventory 成为 `PaymentSucceededEvent` 监听方，具体避免重复扣减可通过 `deductAfterPayment` 幂等或移除强一致重复路径实现，但不得破坏支付成功。
- 建议验证方式：支付成功发布 `PaymentSucceededEvent` 后库存扣减；故障注入库存监听失败后支付状态仍成功，`/api/v1/admin/events/failures` 可查到 `PaymentSucceededEvent` 失败记录。

### R5. 物流模块同时按契约监听 `PaymentSucceededEvent` 并修正 `ShipmentDeliveredEvent` 发布字段

- 所属模块：`ecommerce-logistics`。
- 覆盖问题：
  - 未实现 `PaymentSucceededEvent` 监听入口（`docs/appendix_D-check/ecommerce-logistics-check.md:20-24`）。
  - `ShipmentDeliveredEvent` 通用字段不齐全（`docs/appendix_D-check/ecommerce-logistics-check.md:32-36`）。
  - `OrderPaidEvent` 载荷缺少 `items` 的 logistics 侧消费由 R2 覆盖（`docs/appendix_D-check/ecommerce-logistics-check.md:26-30`）。
- 设计依据定位：`PaymentSucceededEvent` 为 `design-docs/附录D-本地事件契约.md:30-44`；`ShipmentDeliveredEvent` 为 `design-docs/附录D-本地事件契约.md:45-58`；通用字段为 `design-docs/附录D-本地事件契约.md:3-11`。
- 当前实现定位：`OrderPaidShipmentListener` 仅监听 `OrderPaidEvent`（`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:36-40`）；`ShipmentDeliveredEvent` 当前字段含额外 `userId` 且缺通用字段传参（`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/ShipmentDeliveredEvent.java:12-24`）；发布点传入 `shipmentId`、`orderId`、`userId`、`deliveredAt`（`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/ShipmentService.java:300-304`）。
- 修复目标：logistics-service 按附录D消费 `PaymentSucceededEvent` 创建发货单；发布的 `ShipmentDeliveredEvent` 载荷为 `orderId`、`shipmentId`、`deliveredAt` 且具备 R1 通用字段。
- 修复方案：
  1. 在 `OrderPaidShipmentListener` 增加 `@Async @TransactionalEventListener(phase = AFTER_COMMIT) public void onPaymentSucceeded(PaymentSucceededEvent event)`，调用 `createShipmentForPaidOrder(event.getOrderId(), "PaymentSucceededEvent")`。
  2. 将失败记录 payload 从单一 `orderId` 字符串改为 JSON，包含通用字段和 `paymentNo`、`orderId`、`paidAmount`、`paidAt`；`replay` 兼容旧字符串和新 JSON 两种格式。
  3. 保留或调整 `onOrderPaid(OrderPaidEvent)` 时，确保 R2 后不再因缺 `items` 不匹配；若保留双监听，必须由 `LogisticsCommandService.createShipmentForPaidOrder` 或监听器使用 `orderId` 幂等避免重复创建发货单。
  4. 修改 `ShipmentDeliveredEvent` 构造器为 `(source, orderId, shipmentId, deliveredAt, traceId)` 或等价形式，调用 R1 基类设置 `eventType="ShipmentDeliveredEvent"`、`aggregateId=orderId`、`traceId`；载荷 getter 保留 `getOrderId()`、`getShipmentId()`、`getDeliveredAt()`。
  5. `userId` 不作为附录D载荷字段；如保留兼容 getter，不得作为 loyalty/order 监听的必需字段，失败记录不再写入 `userId`。
  6. 修改 `ShipmentService.updateStatus` 发布点，构造 `new ShipmentDeliveredEvent(this, shipment.getOrderId(), shipment.getId(), shipment.getDeliveredAt(), traceId)`，不再传 `userId` 作为契约载荷。
- 影响范围：物流监听器、发货单创建幂等、签收事件类、签收发布点、相关测试。
- 注意事项/风险点：物流当前可能已通过 `OrderPaidEvent` 创建发货单；新增 `PaymentSucceededEvent` 入口时必须防重复；`@TransactionalEventListener(AFTER_COMMIT)` 对无事务发布场景可能不触发，可保留 `@EventListener` fallback 或确认 payment 发布在事务内。
- 建议验证方式：支付成功后可查询物流发货单；重复收到 `OrderPaidEvent`/`PaymentSucceededEvent` 不重复创建；物流回调 DELIVERED 后发布事件包含 `orderId`、`shipmentId`、`deliveredAt` 和通用字段。

### R6. 订单模块按附录D监听 `PaymentSucceededEvent` 与 `ShipmentDeliveredEvent`

- 所属模块：`ecommerce-order`。
- 覆盖问题：
  - order-service 未按 `PaymentSucceededEvent` 作为监听方消费事件（`docs/appendix_D-check/ecommerce-order-check.md:26-30`）。
  - order-service 未按 `ShipmentDeliveredEvent` 作为监听方消费事件（`docs/appendix_D-check/ecommerce-order-check.md:32-36`）。
- 设计依据定位：`PaymentSucceededEvent` 监听方包含 order-service（`design-docs/附录D-本地事件契约.md:30-44`）；`ShipmentDeliveredEvent` 监听方包含 order-service（`design-docs/附录D-本地事件契约.md:45-58`）。
- 当前实现定位：`OrderEventListener` 当前只监听订单自身事件（`code/ecommerce-order/src/main/java/com/ecommerce/order/listener/OrderEventListener.java:53-104`）；`OrderPaymentStatusUpdater.markAsPaid` 只接收 `orderId`、`paymentNo`（`code/ecommerce-order/src/main/java/com/ecommerce/order/query/OrderPaymentStatusUpdater.java:11-17`）；`OrderQueryServiceImpl.markAsPaid` 同步调用 `paymentEventHandler.handlePaymentSuccess(orderId, paymentNo, order.getPayableAmount())`（`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderQueryServiceImpl.java:123-129`）。
- 修复目标：order-service 通过本地事件监听消费 `PaymentSucceededEvent(paymentNo, orderId, paidAmount, paidAt)` 更新订单支付状态；消费 `ShipmentDeliveredEvent(orderId, shipmentId, deliveredAt)` 更新订单签收/物流状态或记录订单事件。
- 修复方案：
  1. 新增或扩展订单监听器，例如 `OrderPaymentSucceededEventListener`，声明 `@TransactionalEventListener(phase = AFTER_COMMIT)` 或 `@EventListener` 监听 `PaymentSucceededEvent`。
  2. 监听方法调用 `OrderPaymentEventHandler.handlePaymentSuccess(event.getOrderId(), event.getPaymentNo(), event.getPaidAmount())`；同时扩展 `handlePaymentSuccess` 支持使用 `event.getPaidAt()` 设置 `order.setPaidAt(paidAt)`，避免当前固定 `LocalDateTime.now()`（`OrderPaymentEventHandler.java:107-111`）。
  3. 将现有 `OrderPaymentStatusUpdater.markAsPaid` 直接调用保留为兼容入口，但支付成功主链路应以 `PaymentSucceededEvent` 为准；若 payment-service 当前仍调用接口，应调整为发布事件后由监听处理，避免双处理。订单状态更新必须幂等处理已 `PAID` 状态。
  4. 新增监听 `ShipmentDeliveredEvent` 的方法，读取 `orderId`、`shipmentId`、`deliveredAt`；根据现有订单状态模型将订单物流状态更新为 `DELIVERED`/订单状态推进到已签收（若状态机允许），并调用 `orderService.recordEvent` 记录 `SHIPMENT_DELIVERED`，描述包含 `shipmentId` 与 `deliveredAt`。
  5. 两类监听器均使用 `handleNonStrongEvent` 类似模式捕获异常并持久化 `FailedEventRecord`，payload 包含 R1 通用字段和设计载荷，避免异常传播到发布方事务。
- 影响范围：订单状态更新幂等、支付回调后订单状态可见时序、物流签收后订单状态/事件记录、失败记录查询。
- 注意事项/风险点：支付事件由 payment-service 发布，但 order-service 更新订单支付状态可能是支付成功链路的关键动作；若采用 AFTER_COMMIT 异步监听，公开用例可能立即查询订单状态，需要确保同步/事务后时序满足验收，或在支付回调完成前同步发布并监听但捕获后置异常。
- 建议验证方式：支付成功后订单状态变为 `PAID` 且 `paidAt` 等于事件 `paidAt`；物流 DELIVERED 后订单侧记录签收；监听故障注入时失败记录可查，支付状态不回滚。

### R7. loyalty 监听器按附录D事件载荷消费并清理非契约依赖

- 所属模块：`ecommerce-loyalty`。
- 覆盖问题：
  - `OrderPaidEvent` 缺 `items` 且使用非契约 `paymentNo`（`docs/appendix_D-check/ecommerce-loyalty-check.md:26-30`，由 R2 负责事件结构，本项负责监听消费）。
  - `PaymentSucceededEvent` 缺 `paidAt` 且 loyalty 依赖 `userId`（`docs/appendix_D-check/ecommerce-loyalty-check.md:32-36`，由 R3 负责事件结构，本项负责监听消费）。
  - `ShipmentDeliveredEvent` loyalty 侧依赖非契约 `userId`（`docs/appendix_D-check/ecommerce-loyalty-check.md:38-42`）。
  - `ReviewApprovedEvent` 缺 `orderId`、`productId` 的 loyalty 侧消费（`docs/appendix_D-check/ecommerce-loyalty-check.md:44-48`，由 R8 负责事件结构，本项负责监听消费）。
- 设计依据定位：`OrderPaidEvent` 为 `design-docs/附录D-本地事件契约.md:13-28`；`PaymentSucceededEvent` 为 `design-docs/附录D-本地事件契约.md:30-44`；`ShipmentDeliveredEvent` 为 `design-docs/附录D-本地事件契约.md:45-58`；`ReviewApprovedEvent` 为 `design-docs/附录D-本地事件契约.md:59-73`。
- 当前实现定位：`OrderPaidEventListener` 使用 `paymentNo` 幂等/失败记录（`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/OrderPaidEventListener.java:47-69`）；`PaymentSucceededEventListener` 反射读取 `userId` 且无 `paidAt`（`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/PaymentSucceededEventListener.java:45-62`）；`ShipmentDeliveredEventListener` 读取并记录 `userId`（`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ShipmentDeliveredEventListener.java:45-62`）；`ReviewApprovedEventListener` 只使用 `reviewId`、`userId`（`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEventListener.java:34-58`）。
- 修复目标：loyalty-service 作为附录D监听方时，仅依赖并记录契约字段和 R1 通用字段；所需用户信息若不在事件契约中，不从非契约字段读取。
- 修复方案：
  1. `OrderPaidEventListener`：读取 `items` 并写入失败记录；积分业务仍可用 `userId`、`paidAmount`，幂等 `bizId` 改为 `String.valueOf(orderId)` 或 `eventId`，不再依赖 `paymentNo`。
  2. `PaymentSucceededEventListener`：方法参数改为具体 `PaymentSucceededEvent` 或至少反射读取 `paidAt`；日志/失败 payload 使用 `paymentNo`、`orderId`、`paidAmount`、`paidAt`，移除 `userId` 依赖。若业务需要用户维度，使用订单查询服务按 `orderId` 查询，而不是要求事件载荷提供 `userId`。
  3. `ShipmentDeliveredEventListener`：日志/失败 payload 只记录 `orderId`、`shipmentId`、`deliveredAt` 和通用字段；移除 `getUserId` 反射读取。
  4. `ReviewApprovedEventListener`：在 R8 事件结构补齐后，日志/失败 payload 增加 `orderId`、`productId`；发放评价积分仍使用 `userId`，幂等 `bizId` 可保留 `reviewId`。
  5. 所有监听器异常继续 catch 并保存 `FailedEventRecord`，补齐 `status`/`lastError` 如当前公共失败查询需要，避免失败记录不完整。
- 影响范围：loyalty 事件监听、积分幂等键、失败记录 JSON、单元测试。
- 注意事项/风险点：移除 `paymentNo` 或 `userId` 依赖时必须保证积分不重复发放；建议对 `earnPoints` 使用 `bizType+bizId` 幂等约束。
- 建议验证方式：四类事件分别触发 loyalty 监听；失败记录 payload 与附录D载荷一致；积分发放幂等；故障注入不阻塞支付。

### R8. 补齐 `ReviewApprovedEvent` 的 `orderId`、`productId` 并更新发布/监听

- 所属模块：`ecommerce-common`、`ecommerce-review`、`ecommerce-loyalty`。
- 覆盖问题：
  - common：`ReviewApprovedEvent` 载荷缺少 `orderId`、`productId`（`docs/appendix_D-check/ecommerce-common-check.md:40-44`）。
  - review：发布对象缺少附录D要求的通用字段和载荷字段（`docs/appendix_D-check/ecommerce-review-check.md:21-31`）。
  - loyalty：`ReviewApprovedEvent` 载荷缺少 `orderId`、`productId`（`docs/appendix_D-check/ecommerce-loyalty-check.md:44-48`）。
- 设计依据定位：`design-docs/附录D-本地事件契约.md:59-73`。
- 当前实现定位：公共事件类只有 `reviewId`、`userId`（`code/ecommerce-common/src/main/java/com/ecommerce/common/event/ReviewApprovedEvent.java:9-24`）；review 发布点只传 `reviewId`、`userId`（`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewModerationService.java:62-63`）；loyalty 监听只读取/记录 `reviewId`、`userId`（`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEventListener.java:34-58`）。
- 修复目标：review-service 发布的 `ReviewApprovedEvent` 载荷为 `reviewId`、`userId`、`orderId`、`productId`，loyalty-service 按完整载荷监听。
- 修复方案：
  1. 修改 `com.ecommerce.common.event.ReviewApprovedEvent`：增加 `orderId`、`productId` 字段、构造器参数和 getter；调用 R1 构造器设置 `eventType="ReviewApprovedEvent"`、`aggregateId=reviewId`、`traceId`。
  2. 在 `ReviewModerationService.approve` 中从 `Review` 实体读取 `review.getOrderId()`、`review.getProductId()`（若实体字段名不同，按现有评价创建字段映射），发布 `new ReviewApprovedEvent(this, reviewId, review.getUserId(), review.getOrderId(), review.getProductId())`。
  3. 修改 `ReviewApprovedEventListener`：日志、积分描述和失败记录 payload 增加 `orderId`、`productId` 及通用字段；业务发放积分仍用 `reviewId` 做幂等标识。
  4. 更新相关测试构造器参数，确保审核通过后事件对象可被监听方获取订单与商品信息。
- 影响范围：事件构造器签名、review 审核通过路径、loyalty 监听和失败记录。
- 注意事项/风险点：`Review` 实体必须已有 `orderId`、`productId` 来源；若当前只存 `skuId`，需通过评价创建时已有商品/订单字段补齐，但不得修改 REST API。
- 建议验证方式：审核通过评价后触发 `ReviewApprovedEvent`；loyalty 记录/失败 payload 包含 `reviewId`、`userId`、`orderId`、`productId`；积分发放仍成功。
