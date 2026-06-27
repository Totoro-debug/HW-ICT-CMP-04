# ecommerce-app 模块 03-通用规范与非功能设计检查报告

## 检查结论

### 一致

- Match：启动装配层未实现金额计算业务逻辑，`src/main/java/com/ecommerce/app/**/*.java` 中未发现 `BigDecimal`、`double`、`float` 金额计算代码；`application.yml` 仅提供金额类配置值（如 `order.packaging-fee`、`payment.refund-fee-rate`、`invoice.tax-rate`），未执行金额运算或入库舍入。
- Match：模块内 REST 管理控制器使用通用异常类型表达参数校验和资源不存在语义：`SystemAdminController` 使用 `ValidationException` 与 `ResourceNotFoundException`，`FaultInjectionAdminController` 使用 `ValidationException`；未发现订单金额校验逻辑或 `IllegalArgumentException`。
- Match：启动类启用缓存、调度、异步、跨模块组件扫描、仓库扫描和实体扫描，能够装配通用非功能能力所在的各业务模块，见 `ShopHubApplication`。
- Match：黑盒测试隔离方面，app 模块未发现用于清理业务环境的 reset/bootstrap REST API；现有 ADMIN 接口为 `/api/v1/admin/**`，由 `SecurityConfig` 要求 ADMIN 角色访问，且 `SystemAdminController` 的 `DELETE /clock` 仅重置系统时钟，不清理业务数据。
- Match：通知规范方面，app 模块未发现直接调用 `MockMailSender` 或 `MockSmsSender`；`NotificationAdminController` 仅通过 `NotificationRecordService` 查询发送记录，不绕过 `LocalNotificationService` 发送通知。
- Match：事件失败查询方面，app 模块提供 `EventFailureAdminController` 查询失败事件记录，并依赖 `FailedEventRecordQueryService` 获取本地失败记录。
- Match：模块配置文件存在，检查范围内发现 `application.yml`，未发现额外 `src/main/resources/*.yml`。

### 不一致

- Mismatch：本地限流未在 app 装配层实现。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-app\src\main\java\com\ecommerce\app\SecurityConfig.java:49`-`72` 仅配置 Spring Security 访问控制和 JWT 过滤器；已搜索范围为 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-app\src\main\java\com\ecommerce\app\**\*.java` 与 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-app\src\main\resources\*.yml`，未找到限流过滤器、拦截器、`RateLimitException` 或 `RATE_LIMITED` 映射配置。
  - 设计要求定位：`03-通用规范与非功能设计.md §4 本地限流`。
  - 不一致具体描述：设计要求登录、支付回调、商品搜索、创建订单分别按用户名、paymentNo、IP、用户限流，并在触发时返回 429，错误码为 `RATE_LIMITED`；app 模块作为统一启动与安全装配层，放行或保护这些 API，但未装配任何本地限流能力。
  - 原因解析：当前 `SecurityConfig` 只处理认证授权，`application.yml` 也没有限流阈值或开关，导致相关入口在 app 层无法保证统一 429/`RATE_LIMITED` 行为。

- Mismatch：事件失败管理接口缺少失败事件重放能力。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-app\src\main\java\com\ecommerce\app\controller\EventFailureAdminController.java:25`-`36` 仅提供 `GET /api/v1/admin/events/failures` 查询失败记录；已搜索范围为 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-app\src\main\java\com\ecommerce\app\**\*.java`，未找到 replay/retry/reprocess 类接口或服务调用。
  - 设计要求定位：`03-通用规范与非功能设计.md §8 本地事件失败处理`。
  - 不一致具体描述：设计要求事件监听失败后保存失败记录，并可通过管理接口重放失败事件；app 模块只暴露失败记录查询，未暴露重放失败事件的管理入口。
  - 原因解析：现有 controller 仅注入 `FailedEventRecordQueryService`，没有注入事件重放服务或提供对应 POST/PUT 操作，导致运维侧只能查看失败事件，无法按设计从 app 管理接口触发重放。

- Mismatch：审计日志统一装配/管理入口未找到。
  - 代码定位：未找到对应实现；已搜索范围为 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-app\src\main\java\com\ecommerce\app\**\*.java`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-app\src\main\resources\*.yml`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-app\pom.xml`，未发现 Audit/AuditLog 相关配置、实体、服务或管理接口。
  - 设计要求定位：`03-通用规范与非功能设计.md §6 审计日志`。
  - 不一致具体描述：设计列出的用户冻结/解冻、商品上下架、库存人工调整、订单取消审核、退款审核和仓库验收、发票开具、结算批次生成等操作必须记录至少包含操作者、操作类型、业务 ID、操作前后状态、操作时间和备注的审计日志；app 模块作为整合启动模块未发现任何统一审计日志装配或查询支撑。
  - 原因解析：当前 app 仅装配安全、CORS、库存查询桥接和少量 ADMIN 查询/测试支撑接口，没有审计日志相关 bean 或配置，因此无法从 app 层确认上述跨模块审计能力被统一启用或暴露。

## 检查遗漏声明

- 金额计算：未找到 app 模块内金额计算实现。已搜索 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-app\src\main\java\com\ecommerce\app\**\*.java`，未发现 `BigDecimal`、`double`、`float` 金额运算；`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-app\src\main\resources\application.yml:23`-`39` 仅包含金额相关配置值。
- 订单金额校验失败：未找到 app 模块内订单金额校验逻辑，也未找到 `OrderValidationException` 使用点。
- 全局异常处理器/错误码映射：未找到 app 模块内 `@ControllerAdvice`、全局异常处理器或错误码映射实现；仅在 controller 中发现通用异常抛出点。
- 幂等规范：未找到 app 模块内创建订单、支付回调、退款申请、物流回调、发票申请的幂等键处理或重复请求返回第一次处理结果的实现；`SecurityConfig.java:64`-`65` 仅放行支付/物流回调 URL。
- 本地限流：未找到登录、支付回调、商品搜索、创建订单的限流过滤器、拦截器、配置或 `RATE_LIMITED` 错误码映射。
- 黑盒测试隔离：未找到 reset/bootstrap 业务环境清理接口；仅发现 ADMIN 测试支撑/运维接口，包括 `SystemAdminController` 运行时配置和时钟控制、`FaultInjectionAdminController` 故障注入控制。
- 审计日志：未找到 app 模块内审计统一配置、审计实体、审计服务或审计管理接口。
- 通知发送：未找到 app 模块内通知发送入口；仅发现 `NotificationAdminController` 查询通知记录，未发现 `LocalNotificationService`、`NotificationRequest`、`MockMailSender`、`MockSmsSender` 调用。
- 本地事件失败处理：未找到 app 模块内事件监听器、失败日志记录逻辑、失败记录保存逻辑、强一致/非强一致监听器声明，亦未找到支付成功后物流/积分/通知监听器；仅发现失败事件查询接口，未找到失败事件重放接口。
