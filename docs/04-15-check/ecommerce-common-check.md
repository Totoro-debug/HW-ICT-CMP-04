# 电商通用模块 / 本地通知组件一致性检查报告

## 检查范围

- 设计规范：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/15-本地通知组件设计.md`
- common 源码：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/`
- common Maven：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/pom.xml`
- 父级 Maven：`D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`
- 引用模块约束：所有业务模块使用 `LocalNotificationService`（仅检查该约束）

## 不一致点

### 1. NotificationRequest 定义了设计未列出的额外字段

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/notification/NotificationRequest.java:12`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/notification/NotificationRequest.java:20`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/15-本地通知组件设计.md:15`
- 不一致原因：设计文档列出的 `NotificationRequest` 字段为 `bizType`、`bizId`、`receiver`、`channel`、`templateCode`、`variables`、`idempotencyKey`，但代码除这些字段外还定义了 `subject`、`content` 及对应 getter/setter/builder 方法。
- 详细解析：`NotificationRequest.java:12-18` 已包含设计要求的 7 个字段；`NotificationRequest.java:20-22` 又新增 `subject`、`content`，并在 `NotificationRequest.java:83-97`、`NotificationRequest.java:115-127`、`NotificationRequest.java:138-139` 暴露和构建这些字段。若按设计字段清单作为 DTO 契约，当前实现扩大了请求模型，且业务代码可绕过 `templateCode`/`variables` 模板模型直接传入正文内容。

### 2. 支付成功、发货提醒、订单状态通知渠道与设计场景不一致

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/integration/OrderNotificationService.java:64`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/integration/OrderNotificationService.java:83`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/integration/OrderNotificationService.java:160`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/15-本地通知组件设计.md:9`
- 不一致原因：设计要求 `SMS` 用于“支付成功、发货提醒”，`IN_APP` 用于“订单状态、退款状态”；但订单模块中的支付成功、发货、订单状态更新均构建为 `NotificationChannel.EMAIL`。
- 详细解析：`notifyPaymentSuccess` 在 `OrderNotificationService.java:68` 使用 `EMAIL`；`notifyOrderShipped` 在 `OrderNotificationService.java:87` 使用 `EMAIL`；`notifyStatusUpdate` 在 `OrderNotificationService.java:164` 使用 `EMAIL`。这些方法虽通过 `LocalNotificationService` 发送，但实际渠道与设计场景映射不一致，导致支付成功和发货提醒未走 SMS，订单状态更新未走 IN_APP。

### 3. 业务模块构建的多处 NotificationRequest 未填写设计要求字段

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/integration/OrderNotificationService.java:48`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/integration/OrderNotificationService.java:67`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/integration/OrderNotificationService.java:86`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/integration/OrderNotificationService.java:105`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/integration/OrderNotificationService.java:124`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/integration/OrderNotificationService.java:143`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/integration/OrderNotificationService.java:163`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/integration/OrderNotificationService.java:183`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/15-本地通知组件设计.md:15`
- 不一致原因：设计要求 `NotificationRequest` 包含 `bizType`、`bizId`、`receiver`、`channel`、`templateCode`、`variables`、`idempotencyKey`；订单模块多处构建请求时仅设置 `channel`、`recipient`、`subject`、`content`。
- 详细解析：例如 `notifyOrderCreated` 在 `OrderNotificationService.java:48-53` 只设置 `channel`、`recipient`、`subject`、`content`，未设置 `bizType`、`bizId`、`templateCode`、`variables`、`idempotencyKey`；其他列出位置同样采用该模式。由于 `LocalNotificationServiceImpl` 的去重依赖 `request.getIdempotencyKey()`（`LocalNotificationServiceImpl.java:59-64`），模板渲染依赖 `templateCode`/`variables`（`LocalNotificationServiceImpl.java:85`），这些缺失会使设计中的去重和模板渲染规则无法在这些通知上生效。

### 4. 发送失败后会向调用方抛出异常，可能影响主流程

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java:115`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java:121`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/15-本地通知组件设计.md:29`
- 不一致原因：设计要求“失败时记录失败原因，不影响主业务流程”；当前实现 catch 异常后记录日志和失败记录，但随后抛出 `NotificationSendException`。
- 详细解析：`LocalNotificationServiceImpl.java:115-120` 捕获发送异常并调用 `failureRecordService.recordFailure(request, e)` 记录失败原因；但 `LocalNotificationServiceImpl.java:121` 继续 `throw new NotificationSendException("Failed to send notification", e)`。这会把通知失败传播给调用方。虽然部分业务调用方自行 catch（如 `OrderNotificationService.java:56-58`），但服务本身没有保证“不影响主业务流程”，与设计要求不一致。

### 5. 发送日志记录时机早于模板渲染和发送调用

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java:66`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java:85`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java:87`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/15-本地通知组件设计.md:31`
- 不一致原因：设计发送顺序为“idempotencyKey 去重 → 渲染模板 → 调用 MockMailSender/MockSmsSender → 记录发送日志 → 失败记录原因但不影响主流程”；当前 `sentRecords` 在模板渲染和发送调用前就被写入。
- 详细解析：`LocalNotificationServiceImpl.java:59-64` 完成幂等去重后，`LocalNotificationServiceImpl.java:66-74` 立即添加 `NotificationRecord`；随后才在 `LocalNotificationServiceImpl.java:85` 渲染模板，并在 `LocalNotificationServiceImpl.java:87-101` 调用实际渠道发送。若渲染或发送失败，`sentRecords` 中仍已存在记录，和设计中“调用发送后记录发送日志”的顺序不一致。虽然 `NotificationRecordService.record(...)` 在 `LocalNotificationServiceImpl.java:106-113` 是成功后记录，但当前实现同时存在一个提前记录的发送日志/记录通道，容易把失败请求也纳入已发送观测记录。

## 未发现不一致的检查点

1. 模块位置：本地通知组件源码位于 `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/notification/`，父级 Maven 在 `D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml:14` 声明 `ecommerce-common` 模块，common 模块 `pom` 的 `artifactId` 为 `ecommerce-common`（`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/pom.xml:10`）。
2. 通知渠道枚举：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/notification/NotificationChannel.java:6` 定义了 `EMAIL`、`SMS`、`IN_APP` 三类渠道。
3. 发送规则中的幂等去重：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java:59-64` 根据 `idempotencyKey` 去重。
4. 发送规则中的模板渲染和渠道调用：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java:85-98` 执行模板渲染，并对 `EMAIL` 调用 `MockMailSender`、对 `SMS` 调用 `MockSmsSender`、对 `IN_APP` 记录站内信模拟发送。
5. 统一处理约束：源码检索未发现业务模块直接调用 `MockMailSender`、`MockSmsSender`、`sendEmail(...)` 或 `sendSms(...)`；已发现的业务模块通知发送均通过 `LocalNotificationService`（如 `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/integration/OrderNotificationService.java:36`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-user/src/main/java/com/ecommerce/user/event/UserRegistrationNotificationListener.java:24`）。

## 无法确认项

1. “EMAIL（注册激活、发票通知）”中的发票通知场景：在本次限定检查范围内未定位到明确的发票通知发送实现，无法确认该场景是否由其他模块或尚未实现的业务流程覆盖。
2. “SMS（支付成功、发货提醒）”的短信发送场景：common 组件支持 `SMS` 渠道，但订单模块现有支付成功、发货相关通知使用 `EMAIL`；未发现其他业务模块实现对应 SMS 场景。

## 汇总

- 输出文件：`D:/Desktop/work/HW-ICT-CMP-04/docs/04-15-check/ecommerce-common-check.md`
- 不一致点数量：5
- 无法确认项数量：2
