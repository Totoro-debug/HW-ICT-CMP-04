# 附录D 修复实施 Checklist

- [ ] TODO | ecommerce-common | 公共事件基类未支持 `eventType`、`aggregateId`、`traceId` 通用字段（`docs/appendix_D-check/ecommerce-common-check.md:22-26`） | 对应方案：R1 | 修改摘要：扩展 `AbstractDomainEvent`，保留兼容构造器，新增完整通用字段 getter 和附录D事件专用构造器。 | 验证要点：所有附录D事件实例可读取 `eventId`、`eventType`、`occurredAt`、`aggregateId`、`traceId`；全模块测试通过。

- [ ] TODO | ecommerce-common | `OrderPaidEvent` 载荷缺少 `items`（`docs/appendix_D-check/ecommerce-common-check.md:28-32`） | 对应方案：R2 | 修改摘要：新增订单商品明细事件 DTO，给 `OrderPaidEvent` 增加 `items` 字段、构造器参数和 getter，并设置完整通用字段。 | 验证要点：构造 `OrderPaidEvent` 时 `items` 非空可序列化；logistics/loyalty/common notification 可读取该字段。

- [ ] TODO | ecommerce-common | common notification 对 `PaymentSucceededEvent` 未使用 `paidAt`（`docs/appendix_D-check/ecommerce-common-check.md:34-38`） | 对应方案：R3 | 修改摘要：修改 `CoreNotificationEventListener.buildPaymentSucceededNotification`，读取并写入 `paidAt` 通知变量，失败记录保留完整事件序列化。 | 验证要点：支付成功通知变量包含 `paymentNo`、`orderId`、`paidAmount`、`paidAt`；通知失败记录不影响支付成功。

- [ ] TODO | ecommerce-common | `ReviewApprovedEvent` 载荷缺少 `orderId`、`productId`（`docs/appendix_D-check/ecommerce-common-check.md:40-44`） | 对应方案：R8 | 修改摘要：扩展公共 `ReviewApprovedEvent` 字段和构造器，增加 `orderId`、`productId` getter，并设置完整通用字段。 | 验证要点：review 发布方和 loyalty 监听方均可读取 `reviewId`、`userId`、`orderId`、`productId`。

- [ ] TODO | ecommerce-inventory | 库存监听 `OrderPaidEvent`，未按附录D监听 `PaymentSucceededEvent`（`docs/appendix_D-check/ecommerce-inventory-check.md:21-34`） | 对应方案：R4 | 修改摘要：将 `InventoryOrderPaidEventListener` 调整为监听 `PaymentSucceededEvent`，事件类型、日志、失败 payload、replay 均使用 `PaymentSucceededEvent` 载荷和通用字段。 | 验证要点：支付成功后库存扣减；故障注入库存扣减失败时支付仍成功，失败记录可查且包含 `paidAt`。

- [ ] TODO | ecommerce-logistics | logistics-service 未实现 `PaymentSucceededEvent` 监听入口（`docs/appendix_D-check/ecommerce-logistics-check.md:20-24`） | 对应方案：R5 | 修改摘要：在物流监听器新增 `onPaymentSucceeded(PaymentSucceededEvent)`，按 `orderId` 创建发货单，失败记录 JSON 包含 `paymentNo`、`orderId`、`paidAmount`、`paidAt` 和通用字段。 | 验证要点：支付成功事件可触发发货单创建；重复事件不会重复创建；监听失败不回滚支付。

- [ ] TODO | ecommerce-logistics | logistics 消费的 `OrderPaidEvent` 缺少 `items`（`docs/appendix_D-check/ecommerce-logistics-check.md:26-30`） | 对应方案：R2 | 修改摘要：在 R2 补齐事件字段后，更新 `OrderPaidShipmentListener` 的日志/失败记录以保留 `items`，并确保只用 `orderId` 创建物流时仍兼容完整事件契约。 | 验证要点：`OrderPaidEvent` 到达物流侧时 `items` 可读取并写入失败记录；发货单创建不受影响。

- [ ] TODO | ecommerce-logistics | `ShipmentDeliveredEvent` 通用字段不齐全（`docs/appendix_D-check/ecommerce-logistics-check.md:32-36`） | 对应方案：R5 | 修改摘要：修改 `ShipmentDeliveredEvent` 构造器和 `ShipmentService.updateStatus` 发布点，设置 `eventType`、`aggregateId`、`traceId`，载荷聚焦 `orderId`、`shipmentId`、`deliveredAt`。 | 验证要点：DELIVERED 回调发布的事件含完整通用字段；order/loyalty 可按契约监听。

- [ ] TODO | ecommerce-loyalty | 本地事件通用字段实现不完整，loyalty 无法读取完整字段（`docs/appendix_D-check/ecommerce-loyalty-check.md:20-24`） | 对应方案：R1 | 修改摘要：依赖 R1 扩展公共事件基类，并更新 loyalty 各失败记录 payload 统一写入 `eventId`、`eventType`、`occurredAt`、`aggregateId`、`traceId`。 | 验证要点：loyalty 各事件失败记录均含完整通用字段。

- [ ] TODO | ecommerce-loyalty | `OrderPaidEvent` 缺少 `items` 且 loyalty 使用非契约 `paymentNo`（`docs/appendix_D-check/ecommerce-loyalty-check.md:26-30`） | 对应方案：R7 | 修改摘要：`OrderPaidEventListener` 读取/记录 `items`，积分幂等 `bizId` 改用 `orderId` 或 `eventId`，不再依赖 `paymentNo` 作为附录D契约字段。 | 验证要点：支付奖励积分仍幂等发放；失败记录包含 `items` 且不要求 `paymentNo`。

- [ ] TODO | ecommerce-loyalty | `PaymentSucceededEvent` 缺少 `paidAt` 且 loyalty 依赖非契约 `userId`（`docs/appendix_D-check/ecommerce-loyalty-check.md:32-36`） | 对应方案：R7 | 修改摘要：`PaymentSucceededEventListener` 改为读取 `paymentNo`、`orderId`、`paidAmount`、`paidAt` 和通用字段；移除对 `userId` 的事件载荷依赖，必要用户信息通过订单查询获得。 | 验证要点：失败记录包含 `paidAt`；事件中无 `userId` 时监听不失败。

- [ ] TODO | ecommerce-loyalty | `ShipmentDeliveredEvent` loyalty 侧依赖非契约 `userId`（`docs/appendix_D-check/ecommerce-loyalty-check.md:38-42`） | 对应方案：R7 | 修改摘要：`ShipmentDeliveredEventListener` 日志和失败 payload 仅使用 `orderId`、`shipmentId`、`deliveredAt` 与通用字段，删除 `getUserId` 反射依赖。 | 验证要点：签收事件无 `userId` 时 loyalty 监听正常；失败记录字段与附录D一致。

- [ ] TODO | ecommerce-loyalty | `ReviewApprovedEvent` 缺少 `orderId`、`productId`（`docs/appendix_D-check/ecommerce-loyalty-check.md:44-48`） | 对应方案：R7 | 修改摘要：在 R8 补齐事件结构后，`ReviewApprovedEventListener` 日志/失败 payload 增加 `orderId`、`productId`，积分发放继续以 `reviewId` 幂等。 | 验证要点：审核通过事件触发积分；失败记录含 `reviewId`、`userId`、`orderId`、`productId`。

- [ ] TODO | ecommerce-order | `OrderPaidEvent` 事件契约字段不完整（`docs/appendix_D-check/ecommerce-order-check.md:20-24`） | 对应方案：R2 | 修改摘要：更新 order 包别名事件构造器和 `OrderPaymentEventHandler` 发布点，从订单明细组装 `items`，发布完整 `OrderPaidEvent`。 | 验证要点：支付成功后 order-service 发布的 `OrderPaidEvent` 含 `items` 和完整通用字段；监听失败不影响支付状态。

- [ ] TODO | ecommerce-order | order-service 未按 `PaymentSucceededEvent` 作为监听方消费事件（`docs/appendix_D-check/ecommerce-order-check.md:26-30`） | 对应方案：R6 | 修改摘要：新增/扩展订单监听器监听 `PaymentSucceededEvent`，调用支付成功处理并消费 `paymentNo`、`orderId`、`paidAmount`、`paidAt`；保留直接接口为兼容但避免双处理。 | 验证要点：支付成功事件后订单状态为 `PAID`，`paidAt` 使用事件时间；重复事件幂等。

- [ ] TODO | ecommerce-order | order-service 未按 `ShipmentDeliveredEvent` 作为监听方消费事件（`docs/appendix_D-check/ecommerce-order-check.md:32-36`） | 对应方案：R6 | 修改摘要：新增订单侧 `ShipmentDeliveredEvent` 监听方法，读取 `orderId`、`shipmentId`、`deliveredAt`，更新订单签收/物流状态或记录订单事件，并持久化监听失败。 | 验证要点：物流签收后订单侧可观察到签收状态/事件；失败记录含签收事件载荷。

- [ ] TODO | ecommerce-payment | `PaymentSucceededEvent` 缺少 `eventType`、`aggregateId`、`traceId` 通用字段（`docs/appendix_D-check/ecommerce-payment-check.md:21-25`） | 对应方案：R1 | 修改摘要：在 `PaymentSucceededEvent` 构造器中调用 R1 的完整通用字段构造器，设置 `eventType="PaymentSucceededEvent"`、`aggregateId=orderId`、`traceId`。 | 验证要点：支付成功事件 getter 返回完整通用字段；失败记录序列化含这些字段。

- [ ] TODO | ecommerce-payment | `PaymentSucceededEvent` 载荷缺少 `paidAt`（`docs/appendix_D-check/ecommerce-payment-check.md:27-31`） | 对应方案：R3 | 修改摘要：扩展 `PaymentSucceededEvent` 增加 `paidAt` 字段；`PaymentService.confirmPayment` 使用支付成功时间构造并发布事件。 | 验证要点：支付成功事件 `paidAt` 非空且等于支付记录成功时间；各监听器可读取。

- [ ] TODO | ecommerce-review | `ReviewApprovedEvent` 发布对象缺少通用字段及 `orderId`、`productId`（`docs/appendix_D-check/ecommerce-review-check.md:21-31`） | 对应方案：R8 | 修改摘要：`ReviewModerationService.approve` 从 `Review` 实体取 `orderId`、`productId`，发布完整 `ReviewApprovedEvent`。 | 验证要点：审核通过后事件 payload 含 `reviewId`、`userId`、`orderId`、`productId` 和通用字段；loyalty 监听成功。
