# ecommerce-inventory - 附录D 一致性检查

## 检查范围
- 模块：ecommerce-inventory
- 附录：附录D（本地事件契约）
- 输入资料：
  - `README.md`：比赛边界、冻结契约、错误响应、检查口径相关内容（尤其 `README.md:9`、`README.md:35`、`README.md:73`、`README.md:200`、`README.md:237`、`README.md:277`、`README.md:281`）
  - `design-docs/附录D-本地事件契约.md` 全文
  - `code/ecommerce-inventory/pom.xml`
  - `code/pom.xml`
  - `code/ecommerce-inventory/src/main/java` 下全部源文件
  - `code/ecommerce-inventory/src/test/java` 下全部源文件
  - `code/ecommerce-inventory/src/main/resources`、`code/ecommerce-inventory/src/test/resources`：当前模块未发现资源配置文件
  - 必要跨模块事件定义/发布实现：`code/ecommerce-common/src/main/java/com/ecommerce/common/event/AbstractDomainEvent.java`、`code/ecommerce-common/src/main/java/com/ecommerce/common/event/OrderPaidEvent.java`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java`

## 检查结论
- 共发现 1 处不一致

## 不一致明细

### 1. 库存模块监听了 `OrderPaidEvent`，未按附录D作为 `PaymentSucceededEvent` 监听方实现
- 设计要求定位：
  - `design-docs/附录D-本地事件契约.md:3`-`design-docs/附录D-本地事件契约.md:11`：本地事件通用字段应包含 `eventId`、`eventType`、`occurredAt`、`aggregateId`、`traceId`。
  - `design-docs/附录D-本地事件契约.md:30`-`design-docs/附录D-本地事件契约.md:43`：`PaymentSucceededEvent` 发布方为 `payment-service`，监听方包含 `inventory-service`，载荷字段应包含 `paymentNo`、`orderId`、`paidAmount`、`paidAt`。
  - `README.md:9`-`README.md:14`、`README.md:237`、`README.md:281`：设计文档是验收基准，公开用例不覆盖全部验收范围，不能按当前实现或公开测试反向放宽设计要求。
  - `README.md:277`：后置动作失败不阻塞支付。
- 代码定位：
  - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryOrderPaidEventListener.java:7`：库存监听器导入 `com.ecommerce.common.event.OrderPaidEvent`。
  - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryOrderPaidEventListener.java:23`：失败记录事件类型为 `InventoryOrderPaidEventListener:OrderPaidEvent`，不是附录D事件名 `PaymentSucceededEvent`。
  - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryOrderPaidEventListener.java:39`-`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryOrderPaidEventListener.java:40`：`@EventListener` 方法参数为 `OrderPaidEvent`，不是 `PaymentSucceededEvent`。
  - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryOrderPaidEventListener.java:85`-`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryOrderPaidEventListener.java:91`：失败载荷序列化字段包含 `eventId`、`orderId`、`userId`、`paymentNo`、`paidAmount`，未包含附录D要求的 `paidAt`，也未包含通用字段 `eventType`、`occurredAt`、`aggregateId`、`traceId`。
  - 必要跨模块证据：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:7`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:26` 当前 `PaymentSucceededEvent` 类包含 `paymentNo`、`orderId`、`userId`、`paidAmount`，但无 `paidAt`；`code/ecommerce-common/src/main/java/com/ecommerce/common/event/AbstractDomainEvent.java:14`-`code/ecommerce-common/src/main/java/com/ecommerce/common/event/AbstractDomainEvent.java:28` 基类仅提供 `eventId`、`occurredAt`，未提供 `eventType`、`aggregateId`、`traceId`；`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:98`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:106` 支付确认同时发布 `PaymentSucceededEvent` 和 `OrderPaidEvent`，但库存模块实际绑定的是后者。
- 不一致说明：附录D明确要求 `inventory-service` 是 `PaymentSucceededEvent` 的监听方，且该事件载荷包含 `paymentNo`、`orderId`、`paidAmount`、`paidAt`，事件还应具备通用字段。当前 `ecommerce-inventory` 的唯一事件监听实现是 `InventoryOrderPaidEventListener`，监听对象为 `OrderPaidEvent`，失败记录事件名也绑定为 `InventoryOrderPaidEventListener:OrderPaidEvent`，序列化载荷没有 `paidAt`，也没有完整通用字段。
- 原因分析：设计要求库存模块按 `PaymentSucceededEvent` 契约消费支付成功事件；当前实现以 `OrderPaidEvent` 作为库存扣减触发源，属于事件监听关系和事件名称不符。同时，当前可见的监听失败载荷与跨模块事件定义均不能满足附录D对 `PaymentSucceededEvent` 的 `paidAt` 载荷字段及完整通用字段要求，属于载荷字段缺失/结构不符。监听器内部捕获异常并持久化失败记录（`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryOrderPaidEventListener.java:41`-`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryOrderPaidEventListener.java:49`），未发现“监听器失败回滚支付主流程”的直接证据；本项不一致聚焦于事件契约绑定与字段契约不一致。