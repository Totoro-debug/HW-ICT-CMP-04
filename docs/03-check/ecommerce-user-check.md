# ecommerce-user 模块 03-通用规范与非功能设计检查报告

## 检查结论

### 一致

- Match：金额计算维度未发现违规实现。已搜索范围：`code/ecommerce-user/src/main/java/com/ecommerce/user/**/*.java`、`code/ecommerce-user/pom.xml`、`code/pom.xml`；未发现 `BigDecimal` 金额字段/计算，也未发现 `double`/`float` 金额字段或金额计算逻辑。user 模块当前无金额入库、优惠金额、应付金额实现。
- Match：通用异常使用方向基本一致。user 模块依赖 `ecommerce-common`，业务代码使用了 `ResourceNotFoundException`、`BusinessException`、`AuthorizationException`、`ConflictException` 等通用异常；模块内未发现 `IllegalArgumentException` 或订单金额校验实现。示例：`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java:63`、`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java:67`、`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java:73`、`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserRegisterService.java:42`。
- Match：黑盒测试隔离维度未发现业务 REST reset/bootstrap 接口。已搜索范围：`code/ecommerce-user/src/main/java/com/ecommerce/user/**/*.java`；user 模块控制器仅发现注册、激活、登录、当前用户、地址、管理员冻结/解冻等业务接口，未发现 `reset`/`bootstrap` 暴露接口或业务代码依赖。
- Match：通知规范中的“不得直接调用 MockMailSender/MockSmsSender”满足。已搜索范围：`code/ecommerce-user/src/main/java/com/ecommerce/user/**/*.java`、`code/ecommerce-user/pom.xml`；未发现直接依赖或调用 `MockMailSender`、`MockSmsSender`。
- Match：配置文件检查完成。`code/ecommerce-user/src/main/resources/*.yml` 不存在；本次未发现 user 模块 yml 配置中的规范违背项。
- Match：模块构建依赖已纳入检查。`code/ecommerce-user/pom.xml:12` 依赖 `ecommerce-common`，可使用通用异常等公共能力；`code/pom.xml:15` 声明了 `ecommerce-user` 模块。

### 不一致

- Mismatch：登录接口未实现“同一用户名每分钟 5 次”的本地限流。
  - 代码定位：`code/ecommerce-user/src/main/java/com/ecommerce/user/controller/UserController.java:51`；`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java:60`；已搜索范围 `code/ecommerce-user/src/main/java/com/ecommerce/user/**/*.java` 未发现 `RateLimitException`、`RATE_LIMITED`、限流器、计数缓存或按用户名维度的限流逻辑。
  - 设计要求定位：`03-通用规范与非功能设计.md §4 本地限流 - 登录接口`。
  - 不一致的具体描述：`POST /api/v1/users/login` 直接调用 `userAuthService.login(request)`；`UserAuthService.login(LoginRequest request)` 直接查询用户、校验状态和密码、签发 JWT 并保存登录会话，没有按 `request.getEmail()` 或用户名构造限流 key，也没有“一分钟 5 次”的窗口计数；触发路径中没有抛出 `RateLimitException`，因此无法保证返回 429 且错误码为 `RATE_LIMITED`。
  - 原因解析：设计要求登录接口在同一用户名维度进行本地限流，并在超限时返回固定错误语义；当前实现没有任何限流入口或错误码映射调用，登录失败/成功请求均会继续执行业务逻辑，不能满足限流规则。

- Mismatch：用户冻结/解冻未记录符合字段要求的审计日志。
  - 代码定位：`code/ecommerce-user/src/main/java/com/ecommerce/user/controller/AdminUserController.java:22`、`code/ecommerce-user/src/main/java/com/ecommerce/user/controller/AdminUserController.java:28`、`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java:124`、`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java:137`。
  - 设计要求定位：`03-通用规范与非功能设计.md §6 审计日志 - 用户冻结和解冻`。
  - 不一致的具体描述：`AdminUserController.freezeUser(Long userId)` 和 `unfreezeUser(Long userId)` 只接收 `userId`，未接收/传递操作者和备注；`UserAuthService.freezeUser`、`unfreezeUser` 仅修改 `User.status`、刷新角色缓存，并用普通业务日志输出 `log.info("User frozen: id={}", userId)` / `log.info("User unfrozen: id={}", userId)`，未保存审计日志记录，未包含操作者、操作类型、业务 ID、操作前状态、操作后状态、操作时间、备注等完整字段。
  - 原因解析：普通应用日志不是结构化审计日志，且当前 API 签名和服务方法没有操作者、备注、操作前状态捕获与持久化审计记录；因此无法满足冻结/解冻操作必须可审计、字段完整的设计要求。

- Mismatch：涉及通知的用户注册实现未按“业务模块只提交 NotificationRequest”的形式提交通知请求。
  - 代码定位：`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserRegisterService.java:28`、`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserRegisterService.java:59`、`code/ecommerce-user/src/main/java/com/ecommerce/user/event/UserRegisteredEvent.java:6`；已搜索范围 `code/ecommerce-user/src/main/java/com/ecommerce/user/**/*.java` 未发现 `LocalNotificationService` 或 `NotificationRequest`。
  - 设计要求定位：`03-通用规范与非功能设计.md §7 通知规范`。
  - 不一致的具体描述：注册成功后通过 `ApplicationEventPublisher` 发布 `new UserRegisteredEvent(...)`；事件注释说明“Common notification listens to this event by simpleName and reflective fields”。user 模块未构造或提交 `NotificationRequest`，也未通过 `LocalNotificationService` 的规范入口表达通知意图。
  - 原因解析：设计约束要求通知统一抽象为 `NotificationRequest` 并由 `LocalNotificationService` 处理，以集中模板渲染、幂等去重、发送日志和失败记录；当前 user 模块使用领域事件和反射字段约定驱动通知，依赖事件名称/字段约定而非通知请求契约，不符合“业务模块只提交 NotificationRequest”的接口边界。

- Mismatch：user 模块发布本地事件，但未在模块内体现事件失败处理契约，且未声明监听器强一致/非强一致边界。
  - 代码定位：`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserRegisterService.java:59`；`code/ecommerce-user/src/main/java/com/ecommerce/user/event/UserRegisteredEvent.java:9`；已搜索范围 `code/ecommerce-user/src/main/java/com/ecommerce/user/**/*.java` 未发现 `@EventListener`、`TransactionalEventListener`、本地事件处理失败表写入、失败重放接口或监听器强一致声明。
  - 设计要求定位：`03-通用规范与非功能设计.md §8 本地事件失败处理`。
  - 不一致的具体描述：`UserRegisterService.register` 在事务方法内直接 `eventPublisher.publishEvent(new UserRegisteredEvent(...))`；user 模块只定义并发布事件，未体现监听器失败时记录失败日志、保存失败记录到本地事件处理表、非强一致监听器不回滚主业务事务、管理接口可重放失败事件等机制。
  - 原因解析：只发布 Spring 应用事件不能自动满足本地事件失败处理规范；如果后续通知或其他监听器消费 `UserRegisteredEvent`，当前模块边界没有可见的失败记录与非回滚语义约束，无法证明事件处理失败可追踪、可重放且不影响主业务事务。

## 检查遗漏声明

- 金额计算：未找到 user 模块中的金额字段、金额计算、最终金额入库、优惠金额边界或应付金额校验实现。已搜索范围：`code/ecommerce-user/src/main/java/com/ecommerce/user/**/*.java`、`code/ecommerce-user/pom.xml`、`code/pom.xml`。因此仅能确认 user 模块未出现 `double`/`float` 金额违规，无法验证 2 位入库、`RoundingMode.HALF_UP`、优惠金额边界、应付金额不得小于 0.01 的正向实现。
- 订单金额校验异常：未找到 user 模块中的订单金额校验实现，也未发现 `OrderValidationException` 或 `IllegalArgumentException` 用于订单金额校验。已搜索范围：`code/ecommerce-user/src/main/java/com/ecommerce/user/**/*.java`。该要求与 user 模块当前职责未直接相关，不作为不一致项。
- 幂等规范：未找到 user 模块实现创建订单 `externalOrderNo`、支付回调 `paymentNo + callbackSequence`、退款申请 `refundRequestNo`、物流回调 `trackingNo + eventTime + status`、发票申请 `invoiceRequestNo` 等接口。已搜索范围：`code/ecommerce-user/src/main/java/com/ecommerce/user/**/*.java`。因此不对这些非 user 职责接口判定不一致。
- 本地限流：user 模块未找到支付回调、商品搜索、创建订单接口；已搜索范围：`code/ecommerce-user/src/main/java/com/ecommerce/user/**/*.java`。这些限流项不属于当前 user 模块实现范围。本报告仅将 user 模块相关的登录限流判定为不一致。
- 黑盒测试隔离：未找到 user 模块 reset/bootstrap 接口或对 reset/bootstrap 的依赖。已搜索范围：`code/ecommerce-user/src/main/java/com/ecommerce/user/**/*.java`。
- 审计日志：user 模块仅发现用户冻结/解冻属于审计日志要求的相关操作；未找到商品上下架、库存人工调整、订单取消审核、退款审核和仓库验收、发票开具、结算批次生成实现。已搜索范围：`code/ecommerce-user/src/main/java/com/ecommerce/user/**/*.java`。这些非 user 职责操作不作为 user 模块不一致项。
- 通知规范：user 模块未找到直接调用 `MockMailSender`、`MockSmsSender`；但也未找到 `LocalNotificationService` 或 `NotificationRequest`。发现的相关实现是注册后发布 `UserRegisteredEvent`，已在不一致项中说明。
- 本地事件失败处理：user 模块未找到事件监听器实现；仅找到 `UserRegisteredEvent` 定义和发布。未找到支付成功后的物流、积分、通知监听器实现。已搜索范围：`code/ecommerce-user/src/main/java/com/ecommerce/user/**/*.java`。支付成功后的监听器要求不属于当前 user 模块实现范围。
- 配置文件：`code/ecommerce-user/src/main/resources/*.yml` 不存在；因此没有可检查的 user 模块 yml 配置项。
