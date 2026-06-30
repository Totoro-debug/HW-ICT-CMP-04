# ecommerce-common - 附录D 一致性检查

## 检查范围
- 模块：ecommerce-common
- 附录：附录D（本地事件契约）
- 输入资料：
  - `README.md`：比赛边界、冻结契约、错误响应、检查口径相关内容（重点纳入 `README.md:9-14`、`README.md:73-76`、`README.md:184-199`、`README.md:231-238`、`README.md:277-281`）
  - `design-docs/附录D-本地事件契约.md` 全文
  - `code/ecommerce-common/src/main/java` 下源文件
  - `code/ecommerce-common/src/test/java` 下源文件
  - `code/ecommerce-common` 配置/配置类（本模块未发现 application.yml/application-test.yml；纳入配置类）
  - `code/ecommerce-common/pom.xml`
  - `code/pom.xml`

## 检查结论
- 共发现 4 处不一致。
- 已按 README 要求以设计文档为验收基准进行检查；公开用例不覆盖全部验收范围，未因当前代码行为或公开测试现状反向放宽设计要求（`README.md:237-238`、`README.md:281`）。
- “监听器失败不得回滚支付状态”语义在 common 的非强一致发布/通知发送失败隔离路径中有对应支撑，未单独计为不一致（`design-docs/附录D-本地事件契约.md:28`；`code/ecommerce-common/src/main/java/com/ecommerce/common/event/DomainEventPublisher.java:33-40`、`code/ecommerce-common/src/main/java/com/ecommerce/common/event/DomainEventPublisher.java:56-62`、`code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java:79-83`）。

## 不一致明细

### 1. 公共事件基类未支持全部附录D通用字段
- 设计要求定位：`design-docs/附录D-本地事件契约.md:3-11`
- 代码定位：`code/ecommerce-common/src/main/java/com/ecommerce/common/event/AbstractDomainEvent.java:14-20`
- 不一致说明：附录D要求本地事件具备通用字段 `eventId`、`eventType`、`occurredAt`、`aggregateId`、`traceId`；当前公共事件基类只定义并初始化 `eventId`、`occurredAt`，未提供 `eventType`、`aggregateId`、`traceId`。
- 原因分析：设计要求所有本地事件共享五个通用字段；`AbstractDomainEvent` 是 common 模块提供的公共事件基类，应承载或至少支持这些通用字段。当前实现仅有 `private final String eventId` 与 `private final LocalDateTime occurredAt`，构造函数也只初始化这两个字段，导致基于该公共基类的事件无法统一携带 `eventType`、`aggregateId`、`traceId`。类型：结构不符/缺失。

### 2. OrderPaidEvent 载荷缺少 items 字段
- 设计要求定位：`design-docs/附录D-本地事件契约.md:13-28`
- 代码定位：`code/ecommerce-common/src/main/java/com/ecommerce/common/event/OrderPaidEvent.java:11-23`
- 不一致说明：附录D要求 `OrderPaidEvent` 载荷包含 `orderId`、`userId`、`paidAmount`、`items`；当前 common 中 `OrderPaidEvent` 仅包含 `orderId`、`userId`、`paymentNo`、`paidAmount`，未包含 `items`。
- 原因分析：设计明确将 `items` 作为 `OrderPaidEvent` 的订单商品明细载荷字段；当前事件类字段和构造函数参数均未提供 `items`，使监听方（logistics-service、loyalty-service、common notification）无法从该事件契约获取订单商品明细。类型：载荷字段缺失。

### 3. common notification 对 PaymentSucceededEvent 的监听未使用设计要求的 paidAt 字段
- 设计要求定位：`design-docs/附录D-本地事件契约.md:30-44`
- 代码定位：`code/ecommerce-common/src/main/java/com/ecommerce/common/notification/CoreNotificationEventListener.java:96-103`；跨模块事件定义证据：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:9-20`
- 不一致说明：附录D要求 `PaymentSucceededEvent` 载荷包含 `paymentNo`、`orderId`、`paidAmount`、`paidAt`，且监听方包括 `common notification`；当前 common notification 的 `buildPaymentSucceededNotification` 只读取并传递 `paymentNo`、`orderId`、`userId`、`paidAmount`，未读取或使用 `paidAt`。
- 原因分析：设计要求 common notification 作为 `PaymentSucceededEvent` 监听方处理包含 `paidAt` 的事件载荷。当前监听适配逻辑没有读取 `paidAt`，通知变量中也没有 `paidAt`；跨模块事件类本身也只定义 `paymentNo`、`orderId`、`userId`、`paidAmount`，未提供 `paidAt`，导致 common notification 无法按设计契约消费支付成功时间。类型：载荷字段缺失/行为不符。

### 4. ReviewApprovedEvent 载荷缺少 orderId 和 productId 字段
- 设计要求定位：`design-docs/附录D-本地事件契约.md:59-73`
- 代码定位：`code/ecommerce-common/src/main/java/com/ecommerce/common/event/ReviewApprovedEvent.java:9-16`
- 不一致说明：附录D要求 `ReviewApprovedEvent` 载荷包含 `reviewId`、`userId`、`orderId`、`productId`；当前 common 中 `ReviewApprovedEvent` 仅包含 `reviewId`、`userId`，未包含 `orderId`、`productId`。
- 原因分析：设计明确将订单 ID 和商品 ID 作为评价审核通过事件载荷，供监听方（loyalty-service）按契约消费；当前公共事件类字段和构造函数只覆盖 `reviewId`、`userId`，缺少 `orderId`、`productId`，事件结构不能满足附录D载荷契约。类型：载荷字段缺失。
