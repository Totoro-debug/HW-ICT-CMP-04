# ecommerce-common 模块设计一致性检查

## 检查结论

本次仅检查 `ecommerce-common` 模块与指定设计基准的一致性，未修改任何源代码或配置代码。已覆盖要求的 8 个维度：架构风格、模块依赖方向、关键本地接口、领域事件、事务边界、缓存设计、安全架构、REST API/错误码。

### 一致

1. **架构风格 / 模块化单体基础设施**
   - 设计要求：`design-docs/02-系统架构.md` §1 要求模块化单体，共享 Spring Boot 应用、JVM、事务管理器、H2 数据库连接池和本地事件总线。
   - 已确认：`code/pom.xml:13-26` 将 `ecommerce-common` 纳入同一 Maven reactor；`code/ecommerce-common/src/main/java/com/ecommerce/common/config/CommonAutoConfiguration.java:12-15` 提供 common 包组件扫描与 JPA 审计配置；`code/ecommerce-common/src/main/java/com/ecommerce/common/event/AbstractDomainEvent.java:12-20` 基于 Spring `ApplicationEvent`；`code/ecommerce-common/src/main/java/com/ecommerce/common/event/DomainEventPublisher.java:21-44` 使用 Spring `ApplicationEventPublisher`。

2. **模块依赖方向**
   - 设计要求：`design-docs/02-系统架构.md` §2 中 `common` 为公共模块，不应反向依赖业务模块。
   - 已确认：`code/ecommerce-common/pom.xml:11-32` 仅依赖 Spring Boot、JPA、Cache、Caffeine 等基础库，未声明依赖 `ecommerce-user`、`ecommerce-order`、`ecommerce-payment` 等业务模块；源码包均位于 `com.ecommerce.common.*`，未发现对其他业务模块 Repository 或 Entity 的直接依赖。

3. **模块边界规则**
   - 设计要求：`design-docs/02-系统架构.md` §3 禁止跨模块直接注入对方 Repository/查询对方表，跨模块传输使用 DTO。
   - 已确认：`code/ecommerce-common/src/main/java/com/ecommerce/common/event/FailedEventRecordRepository.java:9-10` 仅管理 common 自有 `FailedEventRecord`；`code/ecommerce-common/src/main/java/com/ecommerce/common/event/FailedEventRecord.java:19-20` 映射 common 自有表 `failed_event_records`；`code/ecommerce-common/src/main/java/com/ecommerce/common/dto/ApiError.java:9-57`、`code/ecommerce-common/src/main/java/com/ecommerce/common/dto/PageResponse.java:11-73` 为 DTO/响应对象，未暴露其他模块 JPA Entity。

4. **关键本地接口：LocalNotificationService 存在**
   - 设计要求：`design-docs/02-系统架构.md` §4 要求 `LocalNotificationService` 由 `common` 提供，供所有模块统一通知发送。
   - 已确认：`code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationService.java:8-18` 存在接口，包位置为 `com.ecommerce.common.notification`，方法签名为 `void send(NotificationRequest request)`；`code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java:24-43` 提供实现。

5. **领域事件基础设施与失败记录能力**
   - 设计要求：`design-docs/02-系统架构.md` §1、§5 要求本地事件总线，并对部分事件失败进行日志/补偿记录且不回滚主流程。
   - 已确认：`code/ecommerce-common/src/main/java/com/ecommerce/common/event/DomainEventPublisher.java:40-50` 发布事件时捕获异常并调用失败记录；`code/ecommerce-common/src/main/java/com/ecommerce/common/event/FailedEventRecord.java:19-44`、`code/ecommerce-common/src/main/java/com/ecommerce/common/event/FailedEventRecordRepository.java:9-10` 提供失败事件持久化结构。

6. **错误响应结构与 README §7 通用错误码**
   - 设计要求：`README.md` §6 要求错误响应结构冻结；`README.md` §7.1 要求通用错误码 `VALIDATION_FAILED`、`RESOURCE_NOT_FOUND`、`UNAUTHORIZED`、`FORBIDDEN`、`CONFLICT`、`RATE_LIMITED`、`INTERNAL_ERROR`；§7.2 要求业务错误码及 HTTP 状态。
   - 已确认：`code/ecommerce-common/src/main/java/com/ecommerce/common/dto/ApiError.java:11-14` 错误响应字段为 `code`、`message`、`traceId`、`details`；`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:37-114` 覆盖 404、401/403、429、409、400、500；`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:26-35` 对 `USER_NOT_ACTIVE`、`USER_FROZEN`、`REVIEW_PURCHASE_REQUIRED` 映射 403，对 `ORDER_STATUS_CONFLICT`、`REFUND_WAITING_WAREHOUSE_ACCEPT` 映射 409，其余业务异常默认 400，符合 README §7 中与 common 错误处理相关的顶层约束。

7. **缓存设计**
   - 设计要求：`design-docs/02-系统架构.md` §7 指定缓存为购物车、商品详情、库存摘要、用户权限、运费模板，所属模块分别为 cart/product/inventory/user/logistics，未指定 `common` 拥有业务缓存 Key/TTL。
   - 已确认：`ecommerce-common` 未发现 `@Cacheable`、`@CachePut`、`@CacheEvict` 或 `@EnableCaching` 业务缓存实现；`code/ecommerce-common/pom.xml:24-31` 虽声明 cache/caffeine 依赖，但当前模块未实现设计文档 §7 所列业务缓存，未与 Key/TTL 要求冲突。

8. **安全架构中与 common 相关的错误处理**
   - 设计要求：`design-docs/02-系统架构.md` §8 要求用户侧 Bearer Token、管理接口 ADMIN、支付回调签名头，并由 README §7 定义 `UNAUTHORIZED`/`FORBIDDEN`。
   - 已确认：`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/AuthorizationException.java:9-22` 提供 `UNAUTHORIZED`/`FORBIDDEN` 异常；`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:45-53` 将认证失败映射为 401/403。JWT 签发、角色认证、签名头校验本身在设计文档中未指定由 `ecommerce-common` 实现。

### 不一致

1. **缺少 common notification 对核心领域事件的监听方实现**
   - 代码定位：`code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java:24-43` 仅提供通知服务实现；在 `code/ecommerce-common/src/main/java/` 下未找到 `@EventListener`、`ApplicationListener` 或针对核心事件的 notification listener。
   - 设计要求定位：`design-docs/02-系统架构.md` §5 事件驱动设计要求 `UserRegisteredEvent` 监听方为 `common notification`，`OrderCreatedEvent` 监听方为 `notification`，`OrderPaidEvent` 和 `PaymentSucceededEvent` 监听方包含 `notification`，`RefundCompletedEvent` 监听方包含 `notification`。
   - 具体描述：当前 common 模块只有 `LocalNotificationService.send(NotificationRequest)`，没有 `UserRegisteredEvent` / `OrderCreatedEvent` / `OrderPaidEvent` / `PaymentSucceededEvent` / `RefundCompletedEvent` 的监听类或监听方法。
   - 原因解析：设计将通知作为多个核心事件的后置动作监听方；common 是 `LocalNotificationService` 提供方，但模块内缺少事件到通知发送的适配层，因此无法确认 common notification 按设计自动响应这些领域事件。

2. **通知故障注入异常发生在通知服务内部 try/catch 之前，失败策略与“记录日志/不回滚”存在不一致风险**
   - 代码定位：`code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java:49-52` 在方法开头检测 `notification-send-failure` 并直接 `throw new RuntimeException(...)`；实际发送通道异常才在 `code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java:74-108` 的 try/catch 内被记录并吞掉。
   - 设计要求定位：`design-docs/02-系统架构.md` §5 要求 `UserRegisteredEvent` 通知失败“失败记录日志，不回滚注册”，`OrderCreatedEvent` 通知失败“失败记录日志，不回滚订单”，`OrderPaidEvent`/`PaymentSucceededEvent` 的 notification 后置失败不回滚支付；§6.3 要求支付后通知通过事件异步处理，不得使支付确认失败。
   - 具体描述：如果事件监听器直接调用 `LocalNotificationServiceImpl.send()` 且触发 `notification-send-failure`，异常会在第 51 行抛出，未被该服务自身记录/吞掉；是否不回滚依赖外层发布器或监听器额外捕获。
   - 原因解析：设计要求通知失败自身属于非关键后置失败，应记录且不影响主流程；当前实现的故障注入分支绕过服务内部失败处理，与通知服务作为统一发送入口的失败策略不完全一致。

3. **README §6.8 中与 common 状态相关的黑盒支撑管理接口未在 ecommerce-common 模块提供 REST Controller**
   - 代码定位：`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:21` 仅发现 `@RestControllerAdvice`；在 `code/ecommerce-common/src/main/java/` 下未找到 `@RestController`、`@RequestMapping`、`@GetMapping`、`@PostMapping`、`@PutMapping`、`@DeleteMapping` 的 REST Controller。
   - 设计要求定位：`README.md` §6.8 要求提供 `PUT /api/v1/admin/system/configs/{key}`、`GET /api/v1/admin/system/configs/{key}`、`POST /api/v1/admin/ops/fault-injections`、`DELETE /api/v1/admin/ops/fault-injections`、`GET /api/v1/admin/events/failures`、`GET /api/v1/admin/notifications`、`PUT /api/v1/admin/system/clock`、`DELETE /api/v1/admin/system/clock` 等黑盒测试支撑管理接口；`design-docs/02-系统架构.md` §8.5 也要求通过公开 REST 管理接口完成配置覆盖、故障注入、可观察结果查询。
   - 具体描述：common 模块已有相关状态/存储类：`RuntimeConfigRegistry`（`code/ecommerce-common/src/main/java/com/ecommerce/common/test/RuntimeConfigRegistry.java:7-21`）、`FaultInjectionRegistry`（`code/ecommerce-common/src/main/java/com/ecommerce/common/test/FaultInjectionRegistry.java:7-15`）、`SystemClockService`（`code/ecommerce-common/src/main/java/com/ecommerce/common/test/SystemClockService.java:7-20`）、`FailedEventRecordRepository`（`code/ecommerce-common/src/main/java/com/ecommerce/common/event/FailedEventRecordRepository.java:9-10`）、`NotificationRecordService`（`code/ecommerce-common/src/main/java/com/ecommerce/common/notification/NotificationRecordService.java:8-21`），但本模块未暴露对应 REST 路径、HTTP Method、Request/Response 字段。
   - 原因解析：这些管理接口的状态对象位于 common，属于本模块相关实现点；当前 common 模块仅提供内存注册表/Repository/记录服务，缺少 README 冻结契约所要求的 REST 访问层，若其他模块未补充同一路径 Controller，则黑盒支撑 API 无法满足契约。

## 检查遗漏声明

1. **架构风格 —— 已检查。** 已读取 `design-docs/02-系统架构.md` §1、§3，`code/pom.xml`，`code/ecommerce-common/pom.xml` 和 common 全部 Java 源文件；未发现 common 直接访问其他模块表/Repository。

2. **模块依赖方向 —— 已检查。** 已确认 `code/ecommerce-common/pom.xml` 未依赖其他业务模块。设计文档 §2 未要求 `common` 依赖任何业务模块。

3. **关键本地接口 —— 已检查。** 设计文档 §4 中与本模块直接相关的是 `LocalNotificationService | common | all | 统一通知发送`；已找到接口与实现。设计文档 §4 未发现要求 `ecommerce-common` 提供 `UserQueryService`、`ProductQueryService`、`InventoryQueryService`、`OrderQueryService` 等其他业务接口。

4. **领域事件 —— 已检查。** 已找到事件基类、发布器、失败记录 Entity/Repository；未找到 common notification 对 §5 核心事件的监听器，已列为不一致。未找到 `@EventListener` 或 `ApplicationListener`。

5. **事务边界 —— 已检查。** 在 `ecommerce-common` Java 源码中未找到 `@Transactional`。设计文档 §6 的订单创建、支付确认、批量导入、退款分阶段提交主要属于 order/payment/inventory/promotion/logistics 等业务模块；未发现要求 common 自身开启事务边界。与 common 相关的要求是后置通知失败不得回滚主流程，已在事件/通知维度检查并列出风险。

6. **缓存设计 —— 已检查。** 设计文档 §7 未发现 `ecommerce-common` 所属缓存 Key/TTL 要求；源码未找到 `cache` 目录，也未找到 Spring Cache 注解。`LocalNotificationServiceImpl` 的 `idempotencyCache` 为通知幂等内存 Map，设计文档未定义其 Key 格式或 TTL，因此不按 §7 业务缓存判定为不一致。

7. **安全架构 —— 已检查。** 设计文档 §8 未发现 JWT 签发、角色认证、支付签名头校验必须由 `ecommerce-common` 实现的要求；当前模块仅提供认证/授权异常与全局错误响应。未找到 JWT 工具类、安全过滤器、`Authorization: Bearer` 解析或 `X-Payment-Signature` 校验实现；因设计未指定 common 承担这些职责，未单独判定为 common 模块不一致。

8. **REST API 路径、HTTP Method、Request/Response 字段 —— 已检查。** README §6.1-§6.7 为业务模块 API，未发现 `ecommerce-common` 专属业务 API；README §6.8 的系统配置、故障注入、事件失败、通知记录、系统时钟接口与 common 当前状态/记录类相关，但本模块未找到 REST Controller，已列为不一致。除 `GlobalExceptionHandler` 外，未找到 controller 目录或 `@RestController`。

9. **README §7 错误码 —— 已检查。** 与 common 相关的通用错误码和全局异常映射已检查；未发现缺少 README §7 中指定错误码的映射要求。业务错误码由各业务模块抛出时，common 的 `BusinessException`/`GlobalExceptionHandler` 可承接。

10. **源码目录覆盖声明。** 已检查 `code/ecommerce-common/src/main/java/` 下当前所有 Java 源文件。存在目录/包：`config`、`dto`、`event`、`exception`、`integration`、`model`、`money`、`notification`、`ratelimit`、`test`。未找到 `controller`、`service`（除 notification service 类名所在包外无独立 service 包）、`repository`（除 `event` 包内 `FailedEventRecordRepository` 外无独立 repository 包）、`entity`（除 `FailedEventRecord` 和 `BaseEntity` 外无独立 entity 包）、`query`、`cache`、独立 `event listener` 包。

11. **资源配置覆盖声明。** `code/ecommerce-common/src/main/resources/` 目录不存在，未找到本模块资源配置文件。设计文档未发现要求 `ecommerce-common` 必须提供 resources 配置文件。
