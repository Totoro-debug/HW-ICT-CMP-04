# 第 1 批用户服务修复结果

## 负责模块与 R-... ID 列表
- 模块：用户服务
- 范围：R-USER-01、R-USER-02、R-USER-03、R-USER-04、R-USER-05、R-USER-06

## 修改的主要文件
- `code/ecommerce-user/src/main/java/com/ecommerce/user/entity/UserProfile.java`
- `code/ecommerce-user/src/main/java/com/ecommerce/user/repository/UserProfileRepository.java`
- `code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserRegisterService.java`
- `code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java`
- `code/ecommerce-user/src/main/java/com/ecommerce/user/service/AddressFormatter.java`
- `code/ecommerce-user/src/main/java/com/ecommerce/user/event/UserRegistrationNotificationListener.java`
- `code/ecommerce-user/src/test/java/com/ecommerce/user/service/UserRegisterServiceTest.java`
- `code/ecommerce-user/src/test/java/com/ecommerce/user/service/UserAuthServiceTest.java`
- `code/ecommerce-user/src/test/java/com/ecommerce/user/service/AddressFormatterTest.java`
- `code/ecommerce-user/src/test/java/com/ecommerce/user/event/UserRegistrationNotificationListenerTest.java`
- `code/ecommerce-user/src/test/java/com/ecommerce/user/controller/UserControllerTest.java`
- `code/ecommerce-common/src/test/java/com/ecommerce/common/exception/GlobalExceptionHandlerTest.java`

## 每个 R-... 的修复摘要
- `R-USER-01`：为 `UserProfile` 补充 `nickname` 字段，并新增 `UserProfileRepository`；注册时同步创建 `UserProfile`，保持与 `User.nickname` 一致。
- `R-USER-02`：注册初始状态从 `ACTIVE` 改为 `PENDING_ACTIVATION`。
- `R-USER-03`：注册成功后生成并持久化 `EmailActivationToken`，保存 `userId/token/expiresAt/used=false`。
- `R-USER-04`：注册通知改为激活邮件语义，`templateCode/bizType` 统一为 `EMAIL_ACTIVATION`，变量包含 `activationToken` 与 `activationLink`。
- `R-USER-05`：`AddressFormatter.format` 签名修正为 `province/city/district/detail`。
- `R-USER-06`：登录时冻结和未激活用户改抛 `AuthorizationException`，由全局异常映射返回 HTTP 403，并保留 `USER_FROZEN` / `USER_NOT_ACTIVE` 业务码。

## 已执行测试命令与结果
```bash
mvn -q -f code/pom.xml -pl ecommerce-user,ecommerce-common,ecommerce-order -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=UserRegisterServiceTest,UserAuthServiceTest,AddressFormatterTest,UserRegistrationNotificationListenerTest,UserControllerTest,GlobalExceptionHandlerTest,LocalNotificationServiceImplTest,OrderNotificationServiceTest test
```

结果：通过。
- `com.ecommerce.user.service.UserRegisterServiceTest`：`Failures: 0, Errors: 0`
- `com.ecommerce.user.service.UserAuthServiceTest`：`Failures: 0, Errors: 0`
- `com.ecommerce.user.service.AddressFormatterTest`：通过
- `com.ecommerce.user.event.UserRegistrationNotificationListenerTest`：`Failures: 0, Errors: 0`
- `com.ecommerce.user.controller.UserControllerTest`：通过
- `com.ecommerce.common.exception.GlobalExceptionHandlerTest`：`Failures: 0, Errors: 0`

## 未完成项、风险或需要后续批次协调的事项
- `UserProfile.nickname` 依赖 JPA 自动建表；若部署环境关闭 DDL 自动更新，需要后续补迁移脚本。
- 激活链接当前为相对路径 `/api/v1/users/activate?token=...`；若后续要求绝对域名，需要与部署配置对齐。
- 本批次依赖 common 模块的通知失败隔离与通知模型收敛；后续批次不得重新引入 `subject/content` 或旧模板语义。
