# ecommerce-logistics 模块设计一致性检查

## 检查结论

已覆盖 8 个维度。总体结论：ecommerce-logistics 基本具备模块化单体内的独立包边界、Repository/Service/Controller 分层、主要物流 REST API 和支付后异步创建发货单能力；但在关键本地接口命名/暴露、领域事件覆盖、运费模板缓存、部分 REST 路径变量契约和状态冲突错误码方面与设计文档/README 存在不一致。

### 一致

1. 架构风格（设计文档 `design-docs/02-系统架构.md:3-12`、`:40-49`）
   - 模块位于独立包 `com.ecommerce.logistics`，并通过 `LogisticsConfig` 扫描本模块组件：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/config/LogisticsConfig.java:10-12`。
   - 本模块拥有独立 Controller/Service/Repository/Entity/DTO/Query 包，符合模块化单体中“每个模块拥有自己的包边界、领域服务、Repository 和对外契约”的要求。
   - 数据访问集中在本模块 Repository，例如 `ShipmentRepository`、`FreightTemplateRepository` 只操作物流模块实体：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/repository/ShipmentRepository.java:13-29`、`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/repository/FreightTemplateRepository.java:10-12`。

2. 模块依赖方向（设计文档 `design-docs/02-系统架构.md:14-38`、`:42-49`）
   - `code/ecommerce-logistics/pom.xml:12-21` 仅依赖 `ecommerce-common` 与 `ecommerce-order`，用于公共基础能力和订单查询/事件类型，未发现直接依赖 user/product/inventory/payment/loyalty/review 等其它业务模块。
   - 跨订单查询使用 `OrderQueryService`，未发现直接注入订单 Repository 或直接查询订单表：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:4-5`、`:27-34`、`:50-53`。

3. 关键本地接口（设计文档 `design-docs/02-系统架构.md:51-64`）
   - 物流模块通过 `OrderQueryService` 查询订单信息，符合 `OrderQueryService | order | payment、review、logistics、loyalty` 的要求：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:4-5`、`:27-34`、`:50-53`。

4. 领域事件（设计文档 `design-docs/02-系统架构.md:66-78`）
   - 支付后物流创建使用本地事件监听，且在事务提交后异步执行：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:37-40`。
   - 监听器内部捕获异常并记录日志，避免物流后置动作失败回滚支付链路：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:43-58`。

5. 事务边界（设计文档 `design-docs/02-系统架构.md:80-86`）
   - `ShipmentService` 使用本模块事务管理物流状态和物流表数据：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/ShipmentService.java:32-34`。
   - 支付后物流创建在 `AFTER_COMMIT` 且 `@Async` 执行，符合“支付后物流、积分、通知通过事件异步处理，不得使支付确认失败”：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:37-40`。

6. 安全架构（设计文档 `design-docs/02-系统架构.md:100-106`、README `README.md:156-172`）
   - 用户侧物流查询接口需要 USER 角色：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/controller/LogisticsController.java:42-47`。
   - 管理类物流接口需要 ADMIN 角色：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/controller/AdminLogisticsController.java:26-29`。
   - 物流回调接口不依赖 JWT，并在服务层校验签名：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/controller/LogisticsController.java:56-61`、`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/LogisticsCallbackService.java:59-67`、`:92-104`。

7. REST API 主体路径/Method/状态码（README `README.md:156-172`）
   - `GET /api/v1/logistics/order/{orderId}` 返回 200，代码实现一致：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/controller/LogisticsController.java:25-47`。
   - `POST /api/v1/logistics/callback` 返回 200，代码实现一致：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/controller/LogisticsController.java:56-61`。
   - `POST /api/v1/admin/logistics/freight-templates` 返回 201，代码实现一致：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/controller/AdminLogisticsController.java:75-80`。

### 不一致

1. 未按设计提供 `LogisticsCommandService` 关键本地接口
   - 代码定位：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:25-34`、`:50-53`
   - 设计要求定位：`design-docs/02-系统架构.md:51-64`，其中 `LogisticsCommandService | logistics | event listener | 创建发货单`。
   - 具体不一致描述：设计要求物流模块提供 `LogisticsCommandService` 供事件监听器创建发货单；代码中事件监听器直接注入 `ShipmentRepository`、`ShipmentService` 和 `OrderQueryService`，未发现 `LogisticsCommandService` 接口或实现。
   - 原因解析：当前实现把“事件监听适配层”和“物流命令契约”耦合到具体 Service/Repository，缺少设计中明确的本地命令接口边界，导致关键本地接口契约无法被其它模块或监听器稳定依赖。

2. 物流模块未监听 `PaymentSucceededEvent`
   - 代码定位：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:3`、`:37-40`
   - 设计要求定位：`design-docs/02-系统架构.md:70-76`，其中 `PaymentSucceededEvent | payment | order、logistics、loyalty、notification`。
   - 具体不一致描述：代码仅导入并监听 `OrderPaidEvent`，未发现针对 `PaymentSucceededEvent` 的物流监听器。
   - 原因解析：设计将支付模块发布的 `PaymentSucceededEvent` 也列为物流监听的核心事件；当前只处理订单模块的 `OrderPaidEvent`，当支付成功事件作为驱动源时，物流后置动作不会由该事件触发。

3. 签收后未发布 `ShipmentDeliveredEvent`
   - 代码定位：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/ShipmentService.java:260-277`
   - 设计要求定位：`design-docs/02-系统架构.md:70-78`，其中 `ShipmentDeliveredEvent | logistics | order、loyalty | 更新订单签收状态，失败可重试`。
   - 具体不一致描述：`updateStatus` 在 `DELIVERED` 时仅设置 `deliveredAt`、保存 shipment、记录 tracking 并同步调用订单状态更新接口，未发现发布 `ShipmentDeliveredEvent`。
   - 原因解析：设计要求物流签收通过领域事件通知 order、loyalty，并支持失败可重试；当前实现没有事件发布点，且用同步接口更新订单状态，不能满足事件驱动解耦和可重试语义。

4. 物流到订单的签收状态同步使用了设计未列出的同步接口，而非设计事件
   - 代码定位：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/query/OrderLogisticsStatusUpdater.java:1-19`、`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/ShipmentService.java:272-277`
   - 设计要求定位：`design-docs/02-系统架构.md:51-64`、`:70-78`
   - 具体不一致描述：设计的关键本地接口列表未包含 `OrderLogisticsStatusUpdater`；针对物流签收，设计要求通过 `ShipmentDeliveredEvent` 由 order、loyalty 监听。代码定义并注入 `OrderLogisticsStatusUpdater`，在物流状态变化后同步调用订单模块更新。
   - 原因解析：该同步命令接口改变了设计中物流签收事件驱动的依赖形态，增加物流模块对订单更新命令的直接同步依赖，弱化了失败可重试和后置动作隔离。

5. 运费模板缓存未实现
   - 代码定位：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/FreightCalculator.java:45-58`、`:79-91`；`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/config/LogisticsConfig.java:10-15`
   - 设计要求定位：`design-docs/02-系统架构.md:88-97`，其中 `运费模板 | logistics:freight:{templateId} | 30 分钟 | logistics`。
   - 具体不一致描述：运费计算直接从 `FreightTemplateRepository` 查询模板，未发现 `@Cacheable`、`@CacheEvict`、CacheManager 或 key `logistics:freight:{templateId}` 的配置/使用。
   - 原因解析：设计明确要求物流模块缓存运费模板并使用固定 key 与 TTL；当前实现每次从数据库读取模板，缺少缓存 key 和 30 分钟 TTL 约束。

6. 管理端发货流程 REST 路径变量名与 README 冻结契约不一致
   - 代码定位：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/controller/AdminLogisticsController.java:45-49`、`:55-59`、`:65-69`
   - 设计要求定位：README `README.md:167-169`
   - 具体不一致描述：README 冻结 URL 为 `/api/v1/admin/logistics/shipments/{shipmentId}/pick`、`.../{shipmentId}/print-label`、`.../{shipmentId}/outbound`；代码声明为 `/shipments/{id}/pick`、`/shipments/{id}/print-label`、`/shipments/{id}/outbound`。
   - 原因解析：虽然 Spring 路由可匹配同一位置的路径变量，但 README 明确冻结 URL 路径，路径变量名属于文档中展示的契约表达；当前代码注解与契约文本不一致，容易导致基于 OpenAPI/反射/文档校验的契约检查失败。

7. 物流状态冲突未按 README 通用错误码返回 `CONFLICT`/409
   - 代码定位：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/ShipmentService.java:144-149`；通用异常处理 `code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:108-113`
   - 设计要求定位：README `README.md:202-212`，其中 `CONFLICT | 409 | 状态冲突或重复请求`。
   - 具体不一致描述：`pick` 在状态不允许时抛出 `IllegalStateException`，全局异常处理会把普通 `Exception` 转为 `INTERNAL_ERROR`/500，而不是 README 中状态冲突对应的 `CONFLICT`/409。
   - 原因解析：物流发货流程存在状态机，状态不允许操作属于“状态冲突”；当前异常类型未映射到业务冲突错误码，导致 API 错误码和 HTTP 状态不符合 README §7 的通用错误码语义。

## 检查遗漏声明

1. 架构风格：已检查设计文档 §1、§3 和本模块 Java 包/配置/Repository/Service/Controller；未发现 `code/ecommerce-logistics/src/main/resources/`，配置文件检查结果为“未找到”。
2. 模块依赖方向：已检查 `code/pom.xml`、`code/ecommerce-logistics/pom.xml` 和本模块跨模块注入；未发现直接注入其它模块 Repository 或直接查询其它模块表。
3. 关键本地接口：已检查本模块 `query`、`service`、`event` 包；未找到设计要求的 `LogisticsCommandService`。
4. 领域事件：已检查本模块 `event` 包和物流状态更新代码；未找到 `PaymentSucceededEvent` 监听器；未找到 `ShipmentDeliveredEvent` 发布实现。
5. 事务边界：已检查 `@Transactional`、`@TransactionalEventListener`、`@Async` 使用；设计文档 §6 未发现除“支付后物流异步处理、不回滚支付”外更多 ecommerce-logistics 专属事务要求。
6. 缓存设计：已检查服务、配置、资源目录；未找到 `logistics:freight:{templateId}`、TTL 30 分钟或 Spring Cache/缓存配置实现。
7. 安全架构：已检查用户/管理员/回调接口认证注解与签名校验；设计文档 §8 未发现物流模块专属签名头名称要求。
8. REST API 与错误码：已检查 README §6.7 中物流相关 API 和 README §7 通用/业务错误码；README §6 未提供物流接口详细 Request/Response 字段表，因此字段级检查只能基于现有 DTO 做存在性核对；README §7 未发现物流模块专属业务错误码。