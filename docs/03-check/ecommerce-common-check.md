# ecommerce-common 模块 03-通用规范与非功能设计检查报告

## 检查结论

### 一致

- Match：通用异常类型已覆盖 `BusinessException`、`ResourceNotFoundException`、`AuthorizationException`、`ValidationException`、`ConflictException`、`RateLimitException`、`OrderValidationException`，并分别存在专用处理器映射到 404、401/403、400、409、429、400；见 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\exception\GlobalExceptionHandler.java:37`、`:45`、`:56`、`:64`、`:72`、`:80`。
- Match：`RateLimitException` 的错误码为 `RATE_LIMITED`，全局异常处理返回 HTTP 429；见 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\exception\RateLimitException.java:9`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\exception\GlobalExceptionHandler.java:56`。
- Match：本地限流提供 `@RateLimit` 注解与 `RateLimitAspect`，支持每分钟窗口、动态 key 和超限抛出 `RateLimitException`；见 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\ratelimit\RateLimit.java:14`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\ratelimit\RateLimitAspect.java:46`。
- Match：未找到业务 REST reset/bootstrap 控制器或接口实现；已搜索 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\**\*.java`。
- Match：通知规范中的核心类型存在，业务入口为 `LocalNotificationService.send(NotificationRequest)`，`NotificationRequest` 明确要求业务模块不得直连 `MockMailSender`/`MockSmsSender`；见 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\notification\LocalNotificationService.java:8`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\notification\NotificationRequest.java:7`。
- Match：通知实现封装了模板渲染、幂等去重、发送日志以及邮件/短信模拟器适配；见 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\notification\LocalNotificationServiceImpl.java:49`、`:75`、`:77`、`:93`。
- Match：本地事件失败处理已提供失败日志和失败记录表实体/仓储，发布失败时捕获异常并保存失败记录；见 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\event\DomainEventPublisher.java:46`、`:53`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\event\FailedEventRecord.java:19`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\event\FailedEventRecordRepository.java:10`。
- Match：模块 `pom.xml` 与根 `pom.xml` 仅体现模块依赖和 Maven 聚合关系，未发现与 03 设计冲突的配置；见 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\pom.xml:11`、`D:\Desktop\work\HW-ICT-CMP-04\code\pom.xml:13`。

### 不一致

- Mismatch：金额最终舍入模式不符合设计。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\money\MonetaryUtil.java:10`、`:25`、`:32`。
  - 设计要求定位：`03-通用规范与非功能设计.md §1 金额计算`，最终入库保留 2 位小数，舍入模式为 `RoundingMode.HALF_UP`。
  - 不一致具体描述：`MonetaryUtil.roundToCent` 注释和实现均使用 `RoundingMode.HALF_DOWN`，会使 `0.005` 舍入为 `0.00`。
  - 原因解析：通用金额工具采用了与设计相反的半舍入策略，导致所有调用 `roundToCent` 的最终金额可能低于设计预期。

- Mismatch：通用金额工具会在加、减、乘每一步后立即截断到分。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\money\MonetaryUtil.java:36`、`:45`、`:54`、`:41`、`:50`、`:59`。
  - 设计要求定位：`03-通用规范与非功能设计.md §1 金额计算`，中间计算应保留足够精度，不提前截断。
  - 不一致具体描述：`add`、`subtract`、`multiply` 方法均直接调用 `roundToCent` 返回结果，无法区分中间计算和最终入库金额。
  - 原因解析：通用工具把每次基础算术操作都当作最终金额处理，调用方在连续计算订单、税额、优惠或积分相关金额时会发生提前截断。

- Mismatch：未找到优惠金额边界和应付金额下限的通用校验实现。
  - 代码定位：未找到对应实现；已搜索范围为 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\**\*.java`，仅找到 `MonetaryUtil` 的 `roundToCent/add/subtract/multiply`。
  - 设计要求定位：`03-通用规范与非功能设计.md §1 金额计算`，优惠金额不得小于 0、不得大于商品金额，应付金额不得小于 0.01。
  - 不一致具体描述：common 模块没有提供优惠金额边界校验、应付金额最小值校验或订单金额校验辅助方法。
  - 原因解析：金额边界规则未沉淀在 common 通用工具中，业务模块容易各自实现并出现不一致。

- Mismatch：泛化 `BusinessException` 的 HTTP 状态映射不完全符合设计。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\exception\GlobalExceptionHandler.java:88`、`:116`、`:120`、`:123`。
  - 设计要求定位：`03-通用规范与非功能设计.md §2 通用异常`，`BusinessException` 为通用业务异常，HTTP 状态码为 400。
  - 不一致具体描述：`handleBusiness` 对部分 `BusinessException` code 返回 403 或 409，而不是统一返回 400。
  - 原因解析：实现把部分业务错误码提升为权限或冲突语义；但设计已经为权限和冲突提供专用 `AuthorizationException`、`ConflictException`，泛化 `BusinessException` 应保持 400。

- Mismatch：未找到 03 设计指定接口幂等键的 common 通用实现。
  - 代码定位：未找到对应实现；已搜索范围为 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\**\*.java`，仅在通知组件中找到 `idempotencyKey`，见 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\notification\LocalNotificationServiceImpl.java:49`。
  - 设计要求定位：`03-通用规范与非功能设计.md §3 幂等规范`，创建订单、支付回调、退款申请、物流回调、发票申请必须分别使用指定幂等键。
  - 不一致具体描述：common 模块未提供接口幂等注解、拦截器、存储或工具来约束 `externalOrderNo`、`paymentNo + callbackSequence`、`refundRequestNo`、`trackingNo + eventTime + status`、`invoiceRequestNo`。
  - 原因解析：目前仅通知模块具备本地幂等去重，无法覆盖设计中跨业务接口的幂等约定。

- Mismatch：未找到审计日志实体/服务/DTO 覆盖设计要求字段。
  - 代码定位：未找到对应实现；已搜索范围为 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\**\*.java`。仅发现 JPA 创建/更新时间审计字段，见 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\model\BaseEntity.java:27`、`:31`。
  - 设计要求定位：`03-通用规范与非功能设计.md §6 审计日志`，审计日志至少包含操作者、操作类型、业务 ID、操作前状态、操作后状态、操作时间和备注。
  - 不一致具体描述：`BaseEntity` 只有 `createdAt`、`updatedAt`，不存在专门审计日志模型记录操作者、操作类型、业务 ID、前后状态和备注。
  - 原因解析：JPA auditing 只能记录实体时间戳，不能满足业务操作审计日志的字段和语义要求。

- Mismatch：通知发送失败记录未由 `LocalNotificationServiceImpl` 持久化，事件通知失败也可能被吞掉而不进入失败表。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\notification\LocalNotificationServiceImpl.java:105`、`:106`；`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\notification\CoreNotificationEventListener.java:47`、`:48`、`:154`。
  - 设计要求定位：`03-通用规范与非功能设计.md §7 通知规范`，通知组件负责失败记录；`§8 本地事件失败处理`，事件监听器失败时记录失败日志并保存失败记录。
  - 不一致具体描述：`LocalNotificationServiceImpl` 捕获异常后只写日志，不保存失败记录且不重新抛出；因此 `CoreNotificationEventListener` 外层 `catch` 很可能捕获不到通知发送失败，`persistFailure` 不会执行。
  - 原因解析：通知服务内部吞掉异常但未落失败记录，破坏了“通知组件负责失败记录”和“监听器失败保存失败记录”的闭环。

- Mismatch：本地事件失败处理缺少重放失败事件的管理能力。
  - 代码定位：未找到对应实现；已搜索范围为 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\event\**\*.java`，仅找到查询服务 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\event\FailedEventRecordQueryService.java:19`。
  - 设计要求定位：`03-通用规范与非功能设计.md §8 本地事件失败处理`，可通过管理接口重放失败事件。
  - 不一致具体描述：common 模块只有失败记录查询能力，没有 replay 服务、管理接口或重放状态更新逻辑。
  - 原因解析：失败记录可被保存和查询，但无法按设计通过管理能力重放处理失败事件。

- Mismatch：本地事件处理缺少强一致监听器的显式声明机制。
  - 代码定位：未找到对应实现；已搜索范围为 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\event\**\*.java` 与 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\notification\CoreNotificationEventListener.java`。
  - 设计要求定位：`03-通用规范与非功能设计.md §8 本地事件失败处理`，不回滚主业务事务，除非该监听器是明确声明的强一致监听器。
  - 不一致具体描述：未找到强一致监听器标记、注解、接口或发布器分支策略，无法区分强一致与非强一致监听器。
  - 原因解析：当前实现仅在发布异常时统一捕获并持久化失败，缺少“明确声明强一致监听器”的模型，未来强一致监听器无法按设计表达。

## 检查遗漏声明

- 配置文件：未找到 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\resources\*.yml`，且 `src\main\resources` 目录不存在。
- 幂等规范：未找到创建订单 `externalOrderNo`、支付回调 `paymentNo + callbackSequence`、退款申请 `refundRequestNo`、物流回调 `trackingNo + eventTime + status`、发票申请 `invoiceRequestNo` 的 common 幂等注解、拦截器、存储或工具。
- 本地限流：未找到登录、支付回调、商品搜索、创建订单等具体业务接口上的限流应用；common 仅提供通用 `@RateLimit` 和切面能力。
- 黑盒测试隔离：未找到业务 REST reset/bootstrap 接口；但 common 主代码中存在测试支撑类 `FaultInjectionRegistry`、`RuntimeConfigRegistry`、`SystemClockService` 以及静态 `clear/reset` 方法，未发现其暴露为 REST API。
- 审计日志：未找到满足 §6 字段要求的 `AuditLog` 实体、审计日志服务或 DTO；仅找到 JPA auditing 基础时间字段。
- 通知规范：未找到持久化通知失败记录表或服务；仅找到发送记录内存记录与日志。
- 本地事件失败处理：未找到失败事件 replay 管理接口；未找到强一致监听器显式声明机制。
