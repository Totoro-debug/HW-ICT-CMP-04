# 第 1 批通用模块 / 本地通知组件修复结果

## 负责模块与 R-... ID 列表
- 模块：通用模块 / 本地通知组件
- 范围：R-COMMON-01、R-COMMON-02、R-COMMON-03、R-COMMON-04、R-COMMON-05

## 修改的主要文件
- `code/ecommerce-common/src/main/java/com/ecommerce/common/notification/NotificationRequest.java`
- `code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java`
- `code/ecommerce-common/src/main/java/com/ecommerce/common/notification/CoreNotificationEventListener.java`
- `code/ecommerce-order/src/main/java/com/ecommerce/order/integration/OrderNotificationService.java`
- `code/ecommerce-common/src/test/java/com/ecommerce/common/notification/LocalNotificationServiceImplTest.java`
- `code/ecommerce-order/src/test/java/com/ecommerce/order/integration/OrderNotificationServiceTest.java`
- `code/ecommerce-user/src/test/java/com/ecommerce/user/event/UserRegistrationNotificationListenerTest.java`

## 每个 R-... 的修复摘要
- `R-COMMON-01`：`NotificationRequest` 删除设计外的 `subject/content` 字段及 builder，仅保留 `bizType/bizId/receiver/channel/templateCode/variables/idempotencyKey`。
- `R-COMMON-02`：订单支付成功通知与发货通知改为 `SMS`，订单状态类通知改为 `IN_APP`；`CoreNotificationEventListener` 中支付成功通知渠道同步改为 `SMS`。
- `R-COMMON-03`：订单通知统一按完整设计字段构建，补齐 `bizType/bizId/templateCode/variables/idempotencyKey`，并保持幂等键稳定。
- `R-COMMON-04`：`LocalNotificationServiceImpl.send(...)` 发送失败只记录失败原因，不再向调用方抛异常；失败记录持久化异常也二次隔离。
- `R-COMMON-05`：成功记录后移到模板渲染和实际发送成功之后；失败路径不写成功记录，幂等去重继续生效。

## 已执行测试命令与结果
```bash
mvn -q -f code/pom.xml -pl ecommerce-user,ecommerce-common,ecommerce-order -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=UserRegisterServiceTest,UserAuthServiceTest,AddressFormatterTest,UserRegistrationNotificationListenerTest,UserControllerTest,GlobalExceptionHandlerTest,LocalNotificationServiceImplTest,OrderNotificationServiceTest test
```

结果：通过。
- `com.ecommerce.common.notification.LocalNotificationServiceImplTest`：`Failures: 0, Errors: 0`
- `com.ecommerce.order.integration.OrderNotificationServiceTest`：`Failures: 0, Errors: 0`
- `com.ecommerce.user.event.UserRegistrationNotificationListenerTest`：`Failures: 0, Errors: 0`

## 未完成项、风险或需要后续批次协调的事项
- `CoreNotificationEventListener` 中支付成功接收人仍是 `user:{id}` 形式；后续若接入真实手机号，需要 payment/order 模块协同补接收人来源，但不属于本批修复范围。
- 后续模块新增通知时必须继续遵守当前通知请求模型，不得恢复 `subject/content` 或空 `idempotencyKey` 方案。
- 订单、支付、物流后续批次若改事件链，需保持 `SMS/SMS/IN_APP` 场景映射不回退。
