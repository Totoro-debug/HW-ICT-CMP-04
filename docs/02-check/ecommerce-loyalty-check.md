# ecommerce-loyalty 模块设计一致性检查

## 检查结论

本次检查基于 `design-docs/02-系统架构.md`、`README.md` 第 6 节 API 基线与第 7 节错误码、`code/pom.xml`、`code/ecommerce-loyalty/pom.xml`、`code/ecommerce-loyalty/src/main/java/` 下源文件以及 `code/ecommerce-loyalty/src/main/resources/` 配置文件。已覆盖 8 个指定维度。

主要不一致数量：6。

### 一致

1. 架构风格：
   - `code/pom.xml:13-25` 将 `ecommerce-loyalty` 纳入同一个 Maven reactor，符合模块化单体中多模块统一部署的总体形态（设计文档 `design-docs/02-系统架构.md:3-12`）。
   - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/` 下按 controller/service/repository/entity/dto/query/event 分包，体现本模块包边界、领域服务、Repository 和对外契约（设计文档 `design-docs/02-系统架构.md:5`）。

2. 模块依赖方向：
   - `code/ecommerce-loyalty/pom.xml:11-25` 仅声明 `ecommerce-common`、Spring Security、Validation 依赖，未在 POM 中直接依赖其它业务模块。
   - `code/ecommerce-loyalty/src/main/java/` 未发现直接注入其它模块 Repository 或直接查询其它模块表的 import，符合“禁止跨模块直接注入对方 Repository 或直接查询对方表”（设计文档 `design-docs/02-系统架构.md:12`、`design-docs/02-系统架构.md:44-45`）。

3. 关键本地接口：
   - `LoyaltyCommandService` 存在于 `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/query/LoyaltyCommandService.java:10-56`，且 `LoyaltyPointService` 实现该接口（`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:29-30`），覆盖设计中 loyalty 提供 `LoyaltyCommandService` 给 order、payment 使用的要求（设计文档 `design-docs/02-系统架构.md:63`）。

4. 事务边界：
   - 积分写操作使用本模块本地事务，例如 `earnPaymentPoints`、`redeemPoints`、冻结/解冻/消费冻结积分方法均标注 `@Transactional`（`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:95-174`），未把本地积分事务与支付确认主事务直接合并。
   - `OrderPaidEventListener` 使用 `@Async` 与 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`（`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/OrderPaidEventListener.java:31-33`），体现支付后积分作为后置动作异步处理且不应阻塞主事务的方向（设计文档 `design-docs/02-系统架构.md:83-84`）。

5. REST API 路径与 Method：
   - 用户侧积分 API 路径与 Method 符合 README §6.7：
     - `GET /api/v1/loyalty/points`：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/LoyaltyController.java:40-61`，对应 `README.md:172`。
     - `POST /api/v1/loyalty/points/estimate-redeem`：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/LoyaltyController.java:76-77`，对应 `README.md:173`。
     - `GET /api/v1/loyalty/points/history`：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/LoyaltyController.java:96-99`，对应 `README.md:174`。
     - `GET /api/v1/loyalty/member-level`：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/LoyaltyController.java:124-125`，对应 `README.md:175`。
   - 管理侧积分过期 API 路径与 Method 符合 README §6.7：`POST /api/v1/admin/loyalty/points/expire` 位于 `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/AdminLoyaltyController.java:18-33`，对应 `README.md:176`。
   - README §6 仅列出 loyalty 相关 URL、Method、认证、成功状态，未列出 loyalty 相关 Request/Response 字段细表；代码 DTO 字段已检查，但无法与 README 中不存在的字段基线逐字段比对。

6. 错误码：
   - README §7 未定义 loyalty 专属业务错误码；本模块公开 API 可关联的通用错误码主要是 `VALIDATION_FAILED`、`UNAUTHORIZED`、`FORBIDDEN`、`RESOURCE_NOT_FOUND`、`CONFLICT`、`INTERNAL_ERROR`（`README.md:200-230`）。未发现 README §7 中明确针对 loyalty 的其它业务错误码要求。

### 不一致

1. 会员等级统计所需订单数据未按设计通过 `OrderQueryService` 获取。
   - 代码定位：
     - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/query/AnnualConsumptionQueryService.java:5-18`
     - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/MemberLevelService.java:28-33`
     - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/MemberLevelService.java:80-86`
   - 设计要求定位：`design-docs/02-系统架构.md:59`，`OrderQueryService` 由 order 提供，loyalty 使用，用途包含“提供会员等级统计所需订单数据”。
   - 具体不一致描述：loyalty 模块定义并依赖的是本模块内的 `AnnualConsumptionQueryService`，而不是设计指定的 `OrderQueryService`。并且该依赖通过 `ObjectProvider` 可选注入，缺失时直接返回 `BigDecimal.ZERO`。
   - 原因解析：设计要求 loyalty 的会员等级统计通过 order 提供的本地查询接口获取订单数据；当前实现绕开了设计命名和契约，且在接口不可用时静默降级为 0，会导致会员等级统计链路不具备设计要求的跨模块查询契约保证。

2. `OrderPaidEvent` 监听类型位于 loyalty 本模块包内，无法体现监听 order 发布的领域事件契约。
   - 代码定位：
     - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/OrderPaidEvent.java:1-13`
     - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/OrderPaidEventListener.java:31-33`
   - 设计要求定位：`design-docs/02-系统架构.md:73-75`，`OrderPaidEvent` 发布方为 order，监听方包含 loyalty；`PaymentSucceededEvent` 发布方为 payment，监听方包含 loyalty。
   - 具体不一致描述：loyalty 模块声明了自己的 `com.ecommerce.loyalty.event.OrderPaidEvent`，监听器方法参数也是该本模块事件类型。设计要求监听 order 发布的 `OrderPaidEvent`，而不是 loyalty 自行定义同名事件。
   - 原因解析：Spring 事件按实际 Java 类型分发；本模块私有同名事件与 order 发布的事件不是同一类型时，loyalty 监听器不会响应设计中 order 发布的 `OrderPaidEvent`，积分发放链路会与领域事件契约脱节。

3. `ReviewApprovedEvent` 监听类型位于 loyalty 本模块包内，无法体现监听 review 发布的领域事件契约。
   - 代码定位：
     - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEvent.java:1-11`
     - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEventListener.java:26-27`
   - 设计要求定位：`design-docs/02-系统架构.md:77`，`ReviewApprovedEvent` 发布方为 review，监听方为 loyalty，用途为发放评价积分。
   - 具体不一致描述：loyalty 模块声明并监听 `com.ecommerce.loyalty.event.ReviewApprovedEvent`，而不是监听 review 模块发布的事件契约。
   - 原因解析：本模块私有同名事件与 review 发布的事件类型不一致时，评价审核通过后的积分发放无法通过设计要求的本地领域事件完成。

4. 设计要求 loyalty 监听的 `PaymentSucceededEvent` 与 `ShipmentDeliveredEvent` 在本模块未找到对应监听实现。
   - 代码定位：`code/ecommerce-loyalty/src/main/java/` 下未找到 `PaymentSucceededEvent` listener；未找到 `ShipmentDeliveredEvent` listener。
   - 设计要求定位：
     - `design-docs/02-系统架构.md:75`，`PaymentSucceededEvent` 监听方包含 loyalty。
     - `design-docs/02-系统架构.md:76`，`ShipmentDeliveredEvent` 监听方包含 loyalty。
   - 具体不一致描述：本模块仅存在 `OrderPaidEventListener` 与 `ReviewApprovedEventListener`，没有支付成功事件或物流签收事件的 loyalty 侧监听器。
   - 原因解析：设计将支付成功后置动作、签收后的 loyalty 行为纳入领域事件协作；缺少监听器会导致这些事件发生时 loyalty 模块没有设计要求的响应入口。

5. `OrderPaidEvent` 积分发放失败未按设计记录补偿任务。
   - 代码定位：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/OrderPaidEventListener.java:47-49`
   - 设计要求定位：`design-docs/02-系统架构.md:74`，`OrderPaidEvent` 对 logistics、loyalty、notification 的失败策略为“失败记录补偿任务，不回滚支付”；同时 `design-docs/02-系统架构.md:83-84` 要求支付后积分异步处理且不得使支付确认失败。
   - 具体不一致描述：当前 catch 块仅记录错误日志，代码注释明确为“Failure only logged, never persisted for retry”，没有记录补偿任务。
   - 原因解析：虽然当前实现通过异步 after-commit 和异常捕获避免回滚支付，但缺少补偿任务记录，未满足设计中对 `OrderPaidEvent` 后置失败可补偿的要求。

6. 管理侧 loyalty 接口未在本模块代码中体现 `ADMIN` 角色约束。
   - 代码定位：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/AdminLoyaltyController.java:17-33`
   - 设计要求定位：
     - `design-docs/02-系统架构.md:103`，管理类接口需要 `ADMIN` 角色。
     - `README.md:176`，`POST /api/v1/admin/loyalty/points/expire` 认证要求为 `ADMIN`。
   - 具体不一致描述：`AdminLoyaltyController` 仅通过路径 `/api/v1/admin/loyalty` 和注释说明 Requires ADMIN role，方法或类上未发现 `@PreAuthorize`、`@Secured` 等本模块内的角色约束声明。
   - 原因解析：在本次限定输入范围内，无法从 ecommerce-loyalty 模块自身确认该接口强制 `ADMIN` 角色；若依赖全局安全配置，模块代码未显式体现 README 和设计对管理接口认证级别的要求。

## 检查遗漏声明

1. 架构风格（设计文档 §1、§3）：已检查。controller/service/repository/entity/dto/query/event 等源文件存在；本模块 `config`、`cache` 包未找到。
2. 模块依赖方向（设计文档 §2）：已检查 `code/pom.xml`、`code/ecommerce-loyalty/pom.xml` 和本模块 Java import；未发现直接依赖其它业务模块或其它模块 Repository。
3. 关键本地接口（设计文档 §4）：已检查。`LoyaltyCommandService` 找到；设计要求 loyalty 使用 order 提供的 `OrderQueryService`，本模块未找到，详见不一致 1。
4. 领域事件（设计文档 §5）：已检查。`OrderPaidEventListener`、`ReviewApprovedEventListener` 找到；`PaymentSucceededEvent` listener 未找到；`ShipmentDeliveredEvent` listener 未找到；本模块事件类与设计发布方事件契约不一致，详见不一致 2、3、4。
5. 事务边界（设计文档 §6）：已检查。积分写操作本地事务与支付后异步处理找到；补偿任务记录未找到，详见不一致 5。
6. 缓存设计（设计文档 §7）：设计文档 §7 未发现 ecommerce-loyalty 本模块相关缓存要求；本模块 `cache` 包和 `src/main/resources/` 配置文件未找到。
7. 安全架构（设计文档 §8）：已检查。用户侧接口通过 `SecurityContextHolder` 获取认证用户；管理侧 `ADMIN` 角色约束在本模块代码中未找到，详见不一致 6。
8. REST API 路径/Method/Request/Response 字段与错误码（README §6、§7）：已检查 loyalty 相关 5 个 API 路径和 Method，均与 README §6.7 一致；README §6 未发现 loyalty 相关 Request/Response 字段细表，无法逐字段对照；README §7 未发现 loyalty 专属业务错误码要求。