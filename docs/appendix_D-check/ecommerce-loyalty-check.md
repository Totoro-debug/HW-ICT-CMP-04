# ecommerce-loyalty - 附录D 一致性检查

## 检查范围
- 模块：ecommerce-loyalty
- 附录：附录D（本地事件契约）
- 输入资料：
  - `README.md`：比赛边界、冻结契约、错误响应、检查口径相关内容，重点纳入 `README.md:9`、`README.md:13`、`README.md:35`、`README.md:75`、`README.md:237`、`README.md:277`、`README.md:281`
  - `design-docs/附录D-本地事件契约.md` 全文
  - `code/ecommerce-loyalty/src/main/java` 下所有源文件
  - `code/ecommerce-loyalty/src/test/java` 下所有测试源文件
  - 当前模块资源/配置目录：`code/ecommerce-loyalty/src/main/resources`、`code/ecommerce-loyalty/src/test/resources`（当前未发现资源配置文件）
  - 当前模块 POM：`code/ecommerce-loyalty/pom.xml`
  - 整体项目 POM：`code/pom.xml`

## 检查结论
- 共发现 5 处不一致

## 不一致明细

### 1. 本地事件通用字段实现不完整
- 设计要求定位：`design-docs/附录D-本地事件契约.md:3`-`design-docs/附录D-本地事件契约.md:11`
- 代码定位：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/PaymentSucceededEventListener.java:32`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/PaymentSucceededEventListener.java:40`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/PaymentSucceededEventListener.java:58`；跨模块事件基类：`code/ecommerce-common/src/main/java/com/ecommerce/common/event/AbstractDomainEvent.java:14`-`code/ecommerce-common/src/main/java/com/ecommerce/common/event/AbstractDomainEvent.java:28`
- 不一致说明：附录D要求本地事件通用字段包含 `eventId`、`eventType`、`occurredAt`、`aggregateId`、`traceId`。当前 loyalty 监听器以 `AbstractDomainEvent` 接收事件并只可稳定访问 `eventId` 等少量字段；跨模块事件基类实际只定义 `eventId` 和 `occurredAt`，没有 `eventType`、`aggregateId`、`traceId`。
- 原因分析：设计要求是所有本地事件具备完整通用字段；当前实现的事件基类缺失 3 个通用字段，loyalty 侧监听/失败记录也无法按契约读取这些字段。该问题属于字段缺失/结构不符。

### 2. OrderPaidEvent 载荷与附录D不一致，缺少 items 且 loyalty 侧使用非契约 paymentNo
- 设计要求定位：`design-docs/附录D-本地事件契约.md:13`-`design-docs/附录D-本地事件契约.md:28`
- 代码定位：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/OrderPaidEventListener.java:42`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/OrderPaidEventListener.java:47`-`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/OrderPaidEventListener.java:53`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/OrderPaidEventListener.java:66`-`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/OrderPaidEventListener.java:69`；跨模块事件定义：`code/ecommerce-common/src/main/java/com/ecommerce/common/event/OrderPaidEvent.java:11`-`code/ecommerce-common/src/main/java/com/ecommerce/common/event/OrderPaidEvent.java:38`
- 不一致说明：附录D要求 `OrderPaidEvent` 载荷为 `orderId`、`userId`、`paidAmount`、`items`，监听方包含 `loyalty-service`，且监听器失败不得回滚支付状态。当前 loyalty 监听器接收 `OrderPaidEvent` 后使用 `orderId`、`userId`、`paidAmount`，但没有读取或记录 `items`；同时在发放积分和失败记录中使用 `paymentNo`。跨模块事件定义也包含 `paymentNo` 而不包含 `items`。
- 原因分析：设计要求的订单商品明细 `items` 是事件载荷字段，当前实现缺失该字段；当前实现还将未列入 `OrderPaidEvent` 载荷契约的 `paymentNo` 作为业务幂等/失败记录字段使用。该问题属于字段缺失/结构不符。

### 3. PaymentSucceededEvent 载荷与附录D不一致，缺少 paidAt 且 loyalty 侧依赖 userId
- 设计要求定位：`design-docs/附录D-本地事件契约.md:30`-`design-docs/附录D-本地事件契约.md:43`
- 代码定位：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/PaymentSucceededEventListener.java:32`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/PaymentSucceededEventListener.java:46`-`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/PaymentSucceededEventListener.java:50`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/PaymentSucceededEventListener.java:58`-`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/PaymentSucceededEventListener.java:62`；跨模块事件定义：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:9`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:26`
- 不一致说明：附录D要求 `PaymentSucceededEvent` 载荷为 `paymentNo`、`orderId`、`paidAmount`、`paidAt`。当前 loyalty 监听器反射读取并记录 `paymentNo`、`orderId`、`userId`、`paidAmount`，没有读取或记录 `paidAt`；跨模块事件定义也包含 `userId` 而不包含 `paidAt`。
- 原因分析：设计要求支付成功事件必须携带支付时间 `paidAt`，当前实现缺失该字段；当前 loyalty 监听逻辑依赖未在附录D载荷中声明的 `userId`。该问题属于字段缺失/结构不符。

### 4. ShipmentDeliveredEvent 载荷与附录D不一致，loyalty 侧依赖非契约 userId
- 设计要求定位：`design-docs/附录D-本地事件契约.md:45`-`design-docs/附录D-本地事件契约.md:57`
- 代码定位：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ShipmentDeliveredEventListener.java:32`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ShipmentDeliveredEventListener.java:46`-`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ShipmentDeliveredEventListener.java:50`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ShipmentDeliveredEventListener.java:58`-`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ShipmentDeliveredEventListener.java:62`；跨模块事件定义：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/ShipmentDeliveredEvent.java:12`-`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/ShipmentDeliveredEvent.java:39`
- 不一致说明：附录D要求 `ShipmentDeliveredEvent` 载荷为 `orderId`、`shipmentId`、`deliveredAt`。当前 loyalty 监听器除读取这些字段外，还读取并在失败记录中写入 `userId`；跨模块事件定义也额外定义 `userId`。
- 原因分析：设计要求中的签收事件载荷未包含 `userId`，当前 loyalty 侧实现依赖并记录了非契约字段，导致监听方与附录D载荷结构不一致。该问题属于结构不符。

### 5. ReviewApprovedEvent 载荷与附录D不一致，缺少 orderId 和 productId
- 设计要求定位：`design-docs/附录D-本地事件契约.md:59`-`design-docs/附录D-本地事件契约.md:72`
- 代码定位：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEventListener.java:35`-`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEventListener.java:43`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEventListener.java:55`-`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEventListener.java:58`；跨模块事件定义：`code/ecommerce-common/src/main/java/com/ecommerce/common/event/ReviewApprovedEvent.java:9`-`code/ecommerce-common/src/main/java/com/ecommerce/common/event/ReviewApprovedEvent.java:23`
- 不一致说明：附录D要求 `ReviewApprovedEvent` 载荷为 `reviewId`、`userId`、`orderId`、`productId`。当前 loyalty 监听器只使用 `reviewId` 和 `userId` 发放评价积分，失败记录也只包含 `reviewId`、`userId`；跨模块事件定义同样只定义这两个字段。
- 原因分析：设计要求评价审核通过事件必须携带订单 ID 与商品 ID，当前实现缺少 `orderId` 和 `productId`，监听方无法按契约获取完整载荷。该问题属于字段缺失/结构不符。
