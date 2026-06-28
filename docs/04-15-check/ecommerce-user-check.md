# 电商用户服务一致性检查报告

## 检查范围

- 设计文档：`D:\Desktop\work\HW-ICT-CMP-04\design-docs\04-用户服务设计.md`
- 用户服务源码：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-user\src\main\java\com\ecommerce\user\`
- Maven 配置：
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-user\pom.xml`
  - `D:\Desktop\work\HW-ICT-CMP-04\code\pom.xml`

## 发现的不一致点

### 1. `UserProfile` 未完整实现“昵称”字段

- 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-user\src\main\java\com\ecommerce\user\entity\UserProfile.java:17-27`
- 设计要求定位：`D:\Desktop\work\HW-ICT-CMP-04\design-docs\04-用户服务设计.md:14`
- 不一致原因：设计要求 `UserProfile` 包含昵称、头像、生日、性别；当前 `UserProfile` 仅包含 `userId`、`avatar`、`birthday`、`gender`，缺少昵称字段。
- 详细解析：虽然 `User` 实体中存在 `nickname` 字段（`User.java:26-27`），但设计文档在领域模型表中明确将“昵称”归入 `UserProfile` 的职责。按“领域模型实体是否完整实现”的检查口径，`UserProfile` 自身未完整覆盖设计字段。

### 2. 注册后用户状态被直接置为 `ACTIVE`，未进入 `PENDING_ACTIVATION`

- 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-user\src\main\java\com\ecommerce\user\service\UserRegisterService.java:52-58`
- 设计要求定位：
  - `D:\Desktop\work\HW-ICT-CMP-04\design-docs\04-用户服务设计.md:23-24`
  - `D:\Desktop\work\HW-ICT-CMP-04\design-docs\04-用户服务设计.md:30-40`
- 不一致原因：设计要求注册保存用户时状态为 `PENDING_ACTIVATION`，并在用户点击激活链接后变更为 `ACTIVE`；当前注册流程在保存前直接执行 `user.setStatus(UserStatus.ACTIVE)`。
- 详细解析：当前实现跳过了“待邮箱激活”状态，使新注册用户立即成为可登录状态。这破坏了设计文档中的状态机语义：`PENDING_ACTIVATION` 表示“已注册，待邮箱激活”，`ACTIVE` 表示“正常可登录、可下单”。

### 3. 注册流程未生成并保存邮箱激活令牌

- 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-user\src\main\java\com\ecommerce\user\service\UserRegisterService.java:42-67`
- 设计要求定位：
  - `D:\Desktop\work\HW-ICT-CMP-04\design-docs\04-用户服务设计.md:16`
  - `D:\Desktop\work\HW-ICT-CMP-04\design-docs\04-用户服务设计.md:36`
- 不一致原因：设计要求注册流程生成邮箱激活令牌；当前 `UserRegisterService.register` 只保存 `User` 并发布注册通知事件，没有创建 `EmailActivationToken`，也没有调用 `EmailActivationTokenRepository` 保存令牌。
- 详细解析：代码中存在 `EmailActivationToken` 实体和激活接口依赖 token 查询（`UserAuthService.java:102-121`），但注册阶段没有令牌生成与持久化，导致后续 `/api/v1/users/activate` 缺少可用 token 来源，邮箱激活闭环不完整。

### 4. 注册通知不是设计要求的“激活邮件”

- 代码定位：
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-user\src\main\java\com\ecommerce\user\service\UserRegisterService.java:63-80`
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-user\src\main\java\com\ecommerce\user\event\UserRegistrationNotificationListener.java:33-39`
- 设计要求定位：`D:\Desktop\work\HW-ICT-CMP-04\design-docs\04-用户服务设计.md:36-38`
- 不一致原因：设计要求“生成邮箱激活令牌 → 通过 LocalNotificationService 发送激活邮件”；当前通知使用 `bizType("USER_REGISTERED")`、`templateCode("USER_REGISTERED")`，变量中没有激活 token 或激活链接。
- 详细解析：监听器确实通过 `LocalNotificationService.send` 发送邮件通知，但通知内容语义是注册通知，不是激活邮件。由于注册阶段没有生成激活 token，通知也无法携带用户点击激活链接所需的信息。

### 5. 地址格式化方法签名参数顺序与设计不一致

- 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-user\src\main\java\com\ecommerce\user\service\AddressFormatter.java:20-21`
- 设计要求定位：
  - `D:\Desktop\work\HW-ICT-CMP-04\design-docs\04-用户服务设计.md:57-61`
  - `D:\Desktop\work\HW-ICT-CMP-04\design-docs\04-用户服务设计.md:63-69`
- 不一致原因：设计要求签名为 `format(String province, String city, String district, String detail)`，且参数顺序不得调整；当前实现为 `format(String city, String province, String district, String detail)`。
- 详细解析：当前方法体返回 `province + city + district + detail`，输出拼接格式本身符合设计；但方法参数顺序已调整，调用方按设计顺序传参时会导致省、市互换，违反“参数顺序不得调整”的明确要求。

### 6. 冻结用户登录错误码正确，但用户服务代码未明确保证 HTTP 403

- 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-user\src\main\java\com\ecommerce\user\service\UserAuthService.java:71-75`
- 设计要求定位：`D:\Desktop\work\HW-ICT-CMP-04\design-docs\04-用户服务设计.md:47-53`
- 不一致原因：设计要求冻结用户登录返回 403，错误码为 `USER_FROZEN`；当前代码在冻结状态下抛出 `new BusinessException("USER_FROZEN", ...)`，错误码符合要求，但在用户服务控制器或服务代码内没有体现或保证 HTTP 状态为 403。
- 详细解析：`UserAuthService.login` 对 `FROZEN` 状态进行了识别，且错误码为 `USER_FROZEN`。但它使用的是通用业务异常，`UserController.login` 也只是直接返回成功响应或让异常向外抛出；在本次限定的用户服务源码范围内，看不到冻结登录被映射为 `HttpStatus.FORBIDDEN` 的实现，因此无法确认满足“返回 403”的设计要求。

## 未发现不一致的检查点

- `User`、`UserAddress`、`EmailActivationToken`、`LoginSession` 实体字段覆盖了设计文档中的核心说明：见设计文档 `04-用户服务设计.md:11-17`，对应代码分别为 `User.java:17-35`、`UserAddress.java:15-37`、`EmailActivationToken.java:17-27`、`LoginSession.java:17-30`。
- 用户状态枚举完整包含 `PENDING_ACTIVATION`、`ACTIVE`、`FROZEN`、`CLOSED`：设计文档 `04-用户服务设计.md:19-26`，代码 `UserStatus.java:6-12`。
- 登录流程包含邮箱查询、ACTIVE 状态校验、密码哈希校验、JWT 签发、登录会话记录：设计文档 `04-用户服务设计.md:45-51`，代码 `UserAuthService.java:67-95`、`JwtTokenProvider.java:38-50`。
- `UserQueryService` 方法签名完整：设计文档 `04-用户服务设计.md:71-80`，代码 `UserQueryService.java:8-40`；实现见 `UserQueryServiceImpl.java:33-62`。
- REST API 设计表中列出的端点均已实现：设计文档 `04-用户服务设计.md:84-97`，代码 `UserController.java:40-64`、`AddressController.java:32-61`、`AdminUserController.java:22-31`。

## 无法确认的项

- 冻结用户登录的最终 HTTP 状态码需要结合全局异常映射确认；在本次限定的用户服务源码范围内，只能确认错误码 `USER_FROZEN`，不能确认最终一定返回 403。

## 汇总

- 不一致点数量：6
- 无法确认的项：1
