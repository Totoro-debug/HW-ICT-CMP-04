# ecommerce-app 模块设计一致性检查

## 检查结论

ecommerce-app 作为 `app-bootstrap` 模块承担 Spring Boot 启动、全模块扫描、安全过滤链与黑盒测试支撑管理接口承载职责。已按 8 个维度完成检查：1 架构风格、2 模块依赖方向、3 关键本地接口、4 领域事件、5 事务边界、6 缓存设计、7 安全架构、8 REST API/错误码。

主要结论：启动装配、模块依赖方向、JWT/ADMIN 安全约束、黑盒测试支撑管理接口大体符合设计与 README；发现 1 项主要不一致，集中在模块边界规则下直接注入其它模块 Repository。

### 一致

1. 架构风格（设计文档 §1、§3）
   - `code/ecommerce-app/src/main/java/com/ecommerce/app/ShopHubApplication.java:12-18` 启用缓存、调度、异步处理，并通过 `@ComponentScan(basePackages = "com.ecommerce")`、`@EnableJpaRepositories(basePackages = "com.ecommerce")`、`@EntityScan(basePackages = "com.ecommerce")` 将各业务模块部署在同一个 Spring Boot 应用中，符合 `design-docs/02-系统架构.md:5` 关于“模块化单体、同一 Spring Boot 应用、共享 JVM/事务管理器/H2/本地事件总线”的要求。
   - `code/ecommerce-app/src/main/java/com/ecommerce/app/ShopHubApplication.java:15` 使用单一 Spring Boot 启动类，符合 `design-docs/02-系统架构.md:16-38` 中 `app-bootstrap` 位于模块依赖图顶层的定位。

2. 模块依赖方向（设计文档 §2）
   - `code/pom.xml:13-25` 将 `ecommerce-app` 作为 reactor 最后一个模块；`code/ecommerce-app/pom.xml:12-66` 依赖 common、user、product、inventory、cart、order、payment、promotion、logistics、loyalty、review，符合 `design-docs/02-系统架构.md:16-38` 中 `app-bootstrap` 汇聚所有业务模块的方向。
   - 未发现业务模块反向依赖 `ecommerce-app` 的 POM 声明；依赖方向符合 app-bootstrap 作为应用装配层的定位。

3. 关键本地接口（设计文档 §4）
   - `design-docs/02-系统架构.md:51-65` 未列出由 ecommerce-app 提供或消费的业务关键本地接口。ecommerce-app 中未发现 query/service 业务接口实现，符合其启动装配职责。

4. 领域事件（设计文档 §5）
   - `code/ecommerce-app/src/main/java/com/ecommerce/app/ShopHubApplication.java:14` 启用 `@EnableAsync`，为设计文档 `design-docs/02-系统架构.md:66-79` 中本地领域事件的异步后置处理提供应用级开关。
   - `code/ecommerce-app/src/main/java/com/ecommerce/app/controller/EventFailureAdminController.java:13-25` 暴露事件失败查询管理接口，支撑 `design-docs/02-系统架构.md:74-78` 中“失败记录补偿任务/可重试/告警”的可观察性要求，也符合 `README.md:184-195` 的黑盒测试支撑接口要求。

5. 事务边界（设计文档 §6）
   - ecommerce-app 源码中未发现 `@Transactional` 事务方法；启动模块未承载订单创建、支付确认、退款、批量导入等事务，符合 `design-docs/02-系统架构.md:80-86` 将事务边界放在业务模块内的要求。

6. 缓存设计（设计文档 §7）
   - `code/ecommerce-app/src/main/java/com/ecommerce/app/ShopHubApplication.java:12` 启用 Spring Cache；`code/ecommerce-app/src/main/resources/application.yml:14-15` 配置 `spring.cache.type: caffeine`，与 `design-docs/02-系统架构.md:88-97` 中本地缓存设计相容。
   - `design-docs/02-系统架构.md:90-97` 未列出 ecommerce-app 自有缓存 Key/TTL；本模块未发现自定义 cache/cache manager 实现，符合 app-bootstrap 职责。

7. 安全架构（设计文档 §8）
   - `code/ecommerce-app/src/main/java/com/ecommerce/app/SecurityConfig.java:49-68` 禁用 CSRF、使用无状态会话并接入 `JwtAuthFilter`，符合 `design-docs/02-系统架构.md:100-104` 关于 JWT 与用户侧 Bearer Token 的要求。
   - `code/ecommerce-app/src/main/java/com/ecommerce/app/SecurityConfig.java:63` 对 `/api/v1/admin/**` 使用 `hasRole("ADMIN")`，符合 `design-docs/02-系统架构.md:103` 和 `README.md:184-186` 对管理类接口 ADMIN 认证的要求。
   - `code/ecommerce-app/src/main/java/com/ecommerce/app/SecurityConfig.java:60-61` 对支付/物流回调放行 JWT，配合业务模块签名校验，符合 `design-docs/02-系统架构.md:104` 对支付回调签名头的要求。

8. REST API 路径/Method/Request/Response 字段与错误码（README §6、§7）
   - 黑盒测试支撑管理接口路径和 Method 与 `README.md:184-198` 基本一致：
     - `code/ecommerce-app/src/main/java/com/ecommerce/app/controller/SystemAdminController.java:23-32`：`PUT /api/v1/admin/system/configs/{key}`，返回 `key`、`value`。
     - `code/ecommerce-app/src/main/java/com/ecommerce/app/controller/SystemAdminController.java:35-41`：`GET /api/v1/admin/system/configs/{key}`，返回 `key`、`value`。
     - `code/ecommerce-app/src/main/java/com/ecommerce/app/controller/FaultInjectionAdminController.java:18-27`：`POST /api/v1/admin/ops/fault-injections`，成功状态 200。
     - `code/ecommerce-app/src/main/java/com/ecommerce/app/controller/FaultInjectionAdminController.java:29-34`：`DELETE /api/v1/admin/ops/fault-injections`，成功状态 204。
     - `code/ecommerce-app/src/main/java/com/ecommerce/app/controller/EventFailureAdminController.java:25-42`：`GET /api/v1/admin/events/failures`，成功状态 200。
     - `code/ecommerce-app/src/main/java/com/ecommerce/app/controller/NotificationAdminController.java:14-34`：`GET /api/v1/admin/notifications`，成功状态 200。
     - `code/ecommerce-app/src/main/java/com/ecommerce/app/controller/SystemAdminController.java:44-71`：`PUT /api/v1/admin/system/clock`，成功状态 200。
     - `code/ecommerce-app/src/main/java/com/ecommerce/app/controller/SystemAdminController.java:81-85`：`DELETE /api/v1/admin/system/clock`，成功状态 200。
   - 与 ecommerce-app 管理接口直接相关的通用错误码使用情况符合 `README.md:200-212`：`SystemAdminController.java:27-29`、`SystemAdminController.java:47-78` 和 `FaultInjectionAdminController.java:20-23` 使用 `ValidationException` 对应 `VALIDATION_FAILED`；`SystemAdminController.java:37-40` 使用 `ResourceNotFoundException` 对应 `RESOURCE_NOT_FOUND`。`README.md:214-229` 未发现 ecommerce-app 专属业务错误码要求。

### 不一致

1. ecommerce-app 直接注入 common 模块 Repository，违反模块边界中“禁止跨模块直接注入 Repository/只能访问本模块 Repository”的要求。
   - 代码定位：`code/ecommerce-app/src/main/java/com/ecommerce/app/controller/EventFailureAdminController.java:3-4`、`code/ecommerce-app/src/main/java/com/ecommerce/app/controller/EventFailureAdminController.java:19-22`、`code/ecommerce-app/src/main/java/com/ecommerce/app/controller/EventFailureAdminController.java:30-37`
   - 设计要求定位：`design-docs/02-系统架构.md:12`、`design-docs/02-系统架构.md:40-49`
   - 具体不一致描述：`EventFailureAdminController` 位于 ecommerce-app 模块，但直接依赖并调用 `com.ecommerce.common.event.FailedEventRecordRepository`，通过 `findAll()` 查询 common 模块的事件失败记录 Repository。设计文档要求禁止跨模块直接注入对方 Repository 或直接查询对方表，且数据访问只能访问本模块拥有的表和 Repository。
   - 原因解析：ecommerce-app 是启动/管理接口装配层，本身没有事件失败记录的领域归属。为了实现 `README.md:194` 的 `GET /api/v1/admin/events/failures` 可观察接口，当前实现绕过了 common 模块的服务/查询接口，直接访问 common Repository，导致 app-bootstrap 与 common 持久化实现耦合。更符合设计的方式应由 common 提供查询服务或 DTO 契约，ecommerce-app 只调用服务接口并返回结果。

## 检查遗漏声明

1. 架构风格：已检查 `ShopHubApplication`、`CorsConfig`、`SecurityConfig`、POM 与 resources；未找到 ecommerce-app 自有 entity/dto/query/cache/event/listener 包。
2. 模块依赖方向：已检查 `code/pom.xml` 与 `code/ecommerce-app/pom.xml`；未发现业务模块对 ecommerce-app 的反向依赖声明。
3. 关键本地接口：设计文档未发现 ecommerce-app 作为提供方或使用方的关键本地接口要求；本模块未找到 QueryService/CommandService 业务接口实现。
4. 领域事件：设计文档未发现 ecommerce-app 作为领域事件发布方或监听方的要求；本模块未找到事件类或 `@EventListener` 监听器，仅发现事件失败查询管理接口。
5. 事务边界：设计文档未发现 ecommerce-app 自有事务流程要求；本模块未找到 `@Transactional`。
6. 缓存设计：设计文档未发现 ecommerce-app 自有缓存 Key/TTL 要求；本模块未找到自定义缓存管理器，仅发现应用级 `@EnableCaching` 与 `application.yml` 缓存类型配置。
7. 安全架构：已检查 JWT、ADMIN、回调放行与 `/api/v1/**` 授权规则；支付回调签名的具体校验位于 payment 模块，不在 ecommerce-app 模块源码中。
8. REST API/错误码：已检查 README §6.8 中 ecommerce-app 承载的黑盒测试支撑管理接口及 README §7 通用错误码；`POST /api/v1/admin/orders/timeout-cancel` 在 ecommerce-app 源码中未找到，属于 order 管理能力，非 ecommerce-app 自有实现；README §7.2 未发现 ecommerce-app 专属业务错误码要求。
