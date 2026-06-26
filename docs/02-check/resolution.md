# 02-check 不一致项修复方案

## 总览

- 共检查模块：12 个。
- 共发现不一致项：58 处。
- 已修复：0 处（本次任务仅输出修复方案，不修改源代码）。
- 状态说明：除 `EIN-001` 因设计文档内部约束需先消歧而标记为 Blocked 外，其余均为 Pending。

---

## ecommerce-product

### EP-001 商品详情库存摘要未接入 inventory 提供的查询接口

- **所属模块**：ecommerce-product
- **设计要求**：`design-docs/02-系统架构.md` 第 2 章模块依赖图 product 指向 inventory；第 3 章表“模块边界规则”中“查询依赖：跨模块查询必须通过 QueryService 接口”；第 4 章表“关键本地接口”中 `InventoryQueryService | inventory | product、cart、order | 查询库存摘要`。
- **当前状态**：`code/ecommerce-product/src/main/java/com/ecommerce/product/query/InventoryQueryService.java:1-19` 在 product 包内定义库存查询接口；`code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductDetailService.java:39-52` 注入 `StockInfoFetcher`，`code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductDetailService.java:73` 调用 `stockInfoFetcher.fetch(skuId)`；`code/ecommerce-product/src/main/java/com/ecommerce/product/service/StockInfoFetcher.java:22-24` 返回硬编码 `new StockSummaryDto(999, 0)`。
- **不一致描述**：商品详情库存摘要由 product 本地硬编码实现返回，未通过 inventory 提供的 `InventoryQueryService` 获取真实库存摘要。
- **修复方案**：
  1. 删除或停止使用 product 包内 `InventoryQueryService` 与 `StockInfoFetcher` 的硬编码链路。
  2. 在 `ProductDetailService` 注入 `com.ecommerce.inventory.query.InventoryQueryService`。
  3. 在 `getProductDetail(Long skuId)` 组装详情时调用 `getStockSummary(skuId)`，使用 inventory 返回的可用库存、预占库存填充响应。
  4. 若 inventory 侧接口返回类型仍错误，应先按 `EIN-003` 修正为 inventory 包 DTO，再在 product 侧适配 DTO。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；调用 `GET /api/v1/products/{skuId}`，库存摘要应随入库、预占、释放、扣减变化；静态检查 `ProductDetailService` 不再依赖 `StockInfoFetcher`。
- **影响范围**：商品详情 API、product 与 inventory 的本地查询依赖、依赖商品详情展示库存的购物车/订单观察链路。

### EP-002 商品详情缓存未实现

- **所属模块**：ecommerce-product
- **设计要求**：`design-docs/02-系统架构.md` 第 7 章表“缓存设计”中 `商品详情 | product:detail:{skuId} | 10 分钟 | product`。
- **当前状态**：`code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductDetailService.java:58-99` 每次直接查询 Repository 并组装详情；`code/ecommerce-product/src/main/java/com/ecommerce/product/controller/ProductController.java:60-64` 直接调用该服务；模块内未找到 `product:detail:{skuId}`、`@Cacheable`、`CacheManager` 或等效缓存配置。
- **不一致描述**：商品详情未按设计使用固定 Key 与 TTL 建立缓存。
- **修复方案**：
  1. 为 product 模块增加缓存配置或复用全局缓存能力，配置商品详情 TTL 为 10 分钟。
  2. 在 `ProductDetailService#getProductDetail(Long skuId)` 上增加缓存读写，Key 语义固定为 `product:detail:{skuId}`。
  3. 在 SKU/SPU 创建、修改、上下架等改变详情展示的数据点清理或更新对应缓存。
  4. 若详情包含库存摘要，需结合库存变化策略决定是否缩短详情中库存字段缓存或在库存变更后联动失效。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；连续请求同一 SKU 详情可命中缓存；SKU 信息或上下架变化后缓存被驱逐；缓存配置显示 TTL 为 10 分钟。
- **影响范围**：商品详情接口性能与一致性、商品管理操作、商品详情中库存展示一致性。

### EP-003 管理创建接口直接返回 JPA Entity

- **所属模块**：ecommerce-product
- **设计要求**：`design-docs/02-系统架构.md` 第 3 章表“模块边界规则”中“DTO：跨模块传输使用 DTO，不暴露 JPA Entity”；`README.md` 第 6 章 API 基线要求 URL、Method、Request/Response 字段名和类型、成功状态码冻结。
- **当前状态**：`code/ecommerce-product/src/main/java/com/ecommerce/product/controller/AdminProductController.java:44-47` 的 `createSpu` 返回 `ResponseEntity<ProductSpu>`；`code/ecommerce-product/src/main/java/com/ecommerce/product/controller/AdminProductController.java:54-57` 的 `createSku` 返回 `ResponseEntity<ProductSku>`。
- **不一致描述**：管理端创建 SPU/SKU 的 REST Response 暴露 JPA Entity。
- **修复方案**：
  1. 新增或复用 `SpuResponse`、`SkuResponse`，或更明确的 `AdminSpuResponse`、`AdminSkuResponse` DTO。
  2. 在 service 或 controller 层完成 `ProductSpu/ProductSku` 到 DTO 的映射。
  3. 将 `AdminProductController#createSpu/createSku` 返回类型改为 DTO。
  4. 保持 `README.md` 第 6.2 节中的 URL、HTTP Method、认证要求、成功状态码不变，并避免破坏已冻结字段名和类型。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；创建 SPU/SKU 仍返回 HTTP 201；Controller 返回类型不再出现 `ProductSpu`、`ProductSku`。
- **影响范围**：后台商品创建 API 响应体、依赖创建接口响应字段的测试或客户端。

### EP-004 商品详情不可售错误码未按基线返回

- **所属模块**：ecommerce-product
- **设计要求**：`README.md` 第 6.2 节定义 `GET /api/v1/products/{skuId}`；第 7.2 节表“业务错误码”中 `PRODUCT_NOT_FOR_SALE | 400 | 商品不可销售`。
- **当前状态**：`code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductQueryServiceImpl.java:55-65` 的 `getSkuForSale` 对非 `ON_SHELF` 抛出 `PRODUCT_NOT_FOR_SALE`；`code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductDetailService.java:58-73` 的公开商品详情仅查询并返回 `status`，未拦截非上架 SKU。
- **不一致描述**：公开商品详情 API 未对不可售 SKU 使用冻结业务错误码。
- **修复方案**：在 `ProductDetailService#getProductDetail(Long skuId)` 查询到 SKU 后校验 `sku.getStatus()`；若不是 `SkuStatus.ON_SHELF`，抛出 `BusinessException("PRODUCT_NOT_FOR_SALE", ...)`，由统一异常处理映射为 HTTP 400。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；未上架/已下架 SKU 调用 `GET /api/v1/products/{skuId}` 返回 HTTP 400 且 code 为 `PRODUCT_NOT_FOR_SALE`；上架 SKU 仍返回 200。
- **影响范围**：商品详情公开接口、商品上下架后的前台可见性、商品浏览相关黑盒测试。

---

## ecommerce-common

### EC-COMMON-001 common notification 缺少核心事件监听适配

- **所属模块**：ecommerce-common
- **设计要求**：`design-docs/02-系统架构.md` 第 5 章表“核心事件”中 `UserRegisteredEvent | user | common notification | 失败记录日志，不回滚注册`，以及 `OrderCreatedEvent`、`OrderPaidEvent`、`PaymentSucceededEvent`、`RefundCompletedEvent` 的监听方包含 notification；失败策略均要求记录日志/补偿且不回滚主流程。
- **当前状态**：`code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationService.java:8-18` 仅定义通知发送接口；`code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java:24-43` 仅提供发送实现；common 源码中未找到针对上述核心事件的 `@EventListener` 或 `ApplicationListener`；业务事件分别存在于 user/order/payment 等模块。
- **不一致描述**：common 仅有通知发送能力，没有将核心领域事件适配为通知发送的监听器，无法自动响应设计要求的 notification 监听方职责。
- **修复方案**：
  1. 在 `code/ecommerce-common/src/main/java/com/ecommerce/common/notification/` 新增通知事件监听适配类。
  2. 使用 `@EventListener`/`ApplicationListener` 监听设计列出的通知相关事件，组装 `NotificationRequest` 并调用 `LocalNotificationService.send()`。
  3. 监听器内部捕获异常并记录日志或失败记录，禁止异常向外传播。
  4. 若 common 不能直接依赖业务模块事件类，应改为让业务模块发布 common 可见的公共通知事件契约，或用现有事件发布器适配为 common 侧可见 DTO，避免 common 反向强依赖业务模块实现。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；触发注册、下单、支付成功、退款完成链路，能查询到通知记录；启用通知故障注入后主业务链路仍成功且失败可观察。
- **影响范围**：common notification 监听适配层、user/order/payment 的事件发布契约、通知记录生成链路。

### EC-COMMON-002 notification 故障注入绕过 send 内部失败处理

- **所属模块**：ecommerce-common
- **设计要求**：`design-docs/02-系统架构.md` 第 5 章表“核心事件”要求通知失败记录日志且不回滚注册/订单/支付；第 6 章第 3 条要求支付后通知异步处理，不得使支付确认失败。
- **当前状态**：`code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java:49-52` 在 `send()` 方法开头遇到 `notification-send-failure` 直接抛出 `RuntimeException`；`code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java:74-108` 的 try/catch 只覆盖后续模板渲染与发送通道异常。
- **不一致描述**：故障注入分支绕过通知服务自身失败记录/吞掉逻辑，若监听器未额外兜底，通知失败可能向主流程传播。
- **修复方案**：将 `notification-send-failure` 检查纳入 `send()` 内部 try/catch 覆盖范围；或在该分支记录失败日志/通知失败记录后直接返回。同时，通知事件监听器必须对 `LocalNotificationService.send()` 做异常兜底捕获。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；通过 `POST /api/v1/admin/ops/fault-injections` 启用 `notification-send-failure` 后执行支付/退款/注册链路，主流程仍成功，日志或失败记录可观察到通知失败。
- **影响范围**：`LocalNotificationServiceImpl.send()` 失败语义、通知监听器异常兜底、通知故障黑盒用例。

### EC-COMMON-003 common 能力未直接暴露黑盒管理 REST 接口

- **所属模块**：ecommerce-common
- **设计要求**：`design-docs/02-系统架构.md` 第 8 章第 5 条要求黑盒测试通过公开 REST 管理接口完成配置覆盖、故障注入和可观察结果查询；`README.md` 第 6.8 节列出运行时配置、故障注入、事件失败、通知记录、系统时钟等管理接口。
- **当前状态**：common 已有 `RuntimeConfigRegistry`、`FaultInjectionRegistry`、`SystemClockService`、`FailedEventRecordRepository`、`NotificationRecordService` 等能力；但 common 模块自身未提供 REST Controller；相关 Controller 当前位于 `code/ecommerce-app/src/main/java/com/ecommerce/app/controller/SystemAdminController.java:17-87`、`FaultInjectionAdminController.java:12-35`、`EventFailureAdminController.java:13-44`、`NotificationAdminController.java:13-36`。
- **不一致描述**：检查报告要求 common 暴露对应 REST 管理接口，但当前访问层归属 ecommerce-app。
- **修复方案**：若验收以 common 承担访问层为准，则在 `code/ecommerce-common/src/main/java/com/ecommerce/common/controller/` 新增 admin controller，提供 `README.md` 第 6.8 节列出的接口并复用现有 registry/repository/service；若保留 app-bootstrap 作为统一 REST 入口，则需确认 app Controller 被启动扫描且契约完全符合 README，并避免 common 与 app 重复注册相同路径。
- **修复后验证**：运行 `mvn -f code/pom.xml test` 和 `mvn -f test-cases/pom.xml test`；逐个验证第 6.8 节接口 URL、Method、状态码、请求/响应字段不变。
- **影响范围**：黑盒测试支撑管理接口访问层、ecommerce-common 与 ecommerce-app Controller 归属、Spring 路由冲突风险。

---

## ecommerce-user

### EU-001 用户注册欢迎通知未通过 UserRegisteredEvent 解耦

- **所属模块**：ecommerce-user
- **设计要求**：`design-docs/02-系统架构.md` 第 1 章协作优先级第 2 条“本地领域事件：用于通知等弱耦合链路”；第 3 章表“事件依赖/事务”要求后置动作优先使用 `ApplicationEvent` 且事务不依赖非关键后置监听器成功；第 5 章表“核心事件”中 `UserRegisteredEvent | user | common notification | 失败记录日志，不回滚注册`。
- **当前状态**：`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserRegisterService.java:30-39` 直接注入 `LocalNotificationService`；`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserRegisterService.java:42-73` 在注册事务内保存用户后同步构造 `NotificationRequest` 并调用 `notificationService.send(notification)`；user 模块未找到 `UserRegisteredEvent`、`ApplicationEventPublisher.publishEvent(...)` 或相关监听器。
- **不一致描述**：注册通知在主事务内同步发送，通知失败存在回滚注册风险，未使用设计要求的 `UserRegisteredEvent`。
- **修复方案**：
  1. 在 user 模块新增 `event/UserRegisteredEvent.java`，包含 `userId/email/nickname` 等通知所需字段。
  2. `UserRegisterService` 移除 `LocalNotificationService` 直接依赖，改注入 `ApplicationEventPublisher`，用户保存成功后发布 `UserRegisteredEvent`。
  3. 在 user 或 common notification 侧新增监听器构造欢迎通知并调用 `LocalNotificationService.send()`。
  4. 监听器捕获通知异常并记录日志/失败记录，不向外抛出。
  5. 保持 `POST /api/v1/users/register` 的 URL、响应字段和 201 状态不变。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；启用 `notification-send-failure` 后调用注册接口仍返回 201 且用户已保存；静态检查 `UserRegisterService` 不再直接调用 `LocalNotificationService.send`。
- **影响范围**：用户注册通知链路、注册服务单元测试、common notification 监听策略。

### EU-002 用户权限缓存未实现

- **所属模块**：ecommerce-user
- **设计要求**：`design-docs/02-系统架构.md` 第 7 章表“缓存设计”中 `用户权限 | user:roles:{userId} | 30 分钟 | user`。
- **当前状态**：`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java:72-84` 登录时直接从 `user.getRole().name()` 构造 roles 并写入 JWT；`code/ecommerce-user/src/main/java/com/ecommerce/user/security/JwtAuthFilter.java:58-68` 鉴权时直接从 JWT claims 读取 roles；`code/ecommerce-user/pom.xml:11-42` 未声明 Caffeine 等缓存依赖；user 模块未找到 `user:roles:{userId}`、缓存配置或读写/失效逻辑。
- **不一致描述**：缺少指定 Key 与 30 分钟 TTL 的用户权限缓存。
- **修复方案**：
  1. 新增 `UserRoleCacheConfig` 和 `UserRoleCacheManager`，或使用 Spring Cache 实现 key 语义 `user:roles:{userId}`、TTL 30 分钟。
  2. 若采用 Caffeine，在 `ecommerce-user/pom.xml` 补充依赖。
  3. `UserAuthService.login` 登录成功时通过缓存组件读取或写入 roles，再生成 JWT/响应。
  4. `JwtAuthFilter` 或角色解析服务按 userId 优先读取缓存，未命中时从 JWT 或数据库补齐并回填。
  5. 冻结/解冻或角色变更时刷新/失效对应缓存。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；登录后确认写入 `user:roles:{userId}` 且 TTL 30 分钟；访问 `/api/v1/users/me`、管理员接口鉴权仍正确；冻结/解冻后缓存被失效或刷新。
- **影响范围**：登录、JWT 鉴权、用户冻结/解冻后的权限一致性、SecurityConfig 构造依赖。

---

## ecommerce-inventory

### EIN-001 product/inventory 依赖方向在设计基准内存在冲突

- **所属模块**：ecommerce-inventory
- **设计要求**：`design-docs/02-系统架构.md` 第 2 章模块依赖图显示 product 指向 inventory；第 3 章表“查询依赖”要求跨模块查询通过 QueryService；第 4 章表“关键本地接口”同时列出 `ProductQueryService | product | inventory、cart、order | 查询商品、SKU、上下架状态` 与 `InventoryQueryService | inventory | product、cart、order | 查询库存摘要`。
- **当前状态**：`code/ecommerce-inventory/pom.xml:17-21` 直接依赖 `ecommerce-product`；`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryService.java:15-17` 导入 `ProductQueryService`、`SkuDto` 和 product 包 DTO；`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryService.java:46-55` 注入 `ProductQueryService`。
- **不一致描述**：库存模块依赖 product 符合第 4 章 `ProductQueryService` 使用方行，但与第 2 章依赖图的 product → inventory 方向相冲突；在不改设计文档的前提下无法同时通过单向 Maven 依赖满足两条约束。
- **修复方案**：本项标记为 **Blocked**，需先由设计基准消歧。代码层可执行方案只能在确定优先级后落地：若以第 4 章关键本地接口为准，保留 inventory 使用 `ProductQueryService`，同时禁止 product 直接依赖 inventory 或改为可共同依赖的契约；若以第 2 章依赖图为准，则移除 inventory 对 product 的 Maven/代码依赖，库存 REST 摘要不再直接查询商品名称，商品与库存组合由 product/app 层完成。
- **修复后验证**：消歧后运行 `mvn -f code/pom.xml test`；静态检查 Maven 依赖图不再出现与最终设计方向冲突的依赖或循环依赖；库存查询和商品详情链路仍通过。
- **影响范围**：product、inventory、cart、order 的本地接口依赖关系；库存摘要中的商品信息来源；后续 `EP-001`、`ECART-001` 等库存查询修复。

### EIN-002 inventory 内定义 product 表 Entity/Repository

- **所属模块**：ecommerce-inventory
- **设计要求**：`design-docs/02-系统架构.md` 第 1 章协作优先级第 4 条“禁止跨模块直接注入对方 Repository 或直接查询对方表”；第 3 章表“数据访问：只能访问本模块拥有的表和 Repository”。
- **当前状态**：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/entity/Product.java:13-15` 在库存模块定义 `@Entity @Table(name = "product")`；`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/repository/ProductRepository.java:10-12` 定义 `ProductRepository extends JpaRepository<Product, Long>`。
- **不一致描述**：inventory 模块映射并可访问 product 表，违反模块数据边界。
- **修复方案**：删除 inventory 模块内 `Product` Entity 和 `ProductRepository`；商品/SKU 信息统一通过最终确定的 `ProductQueryService` 或上层组合契约获取；确认无业务代码注入该 Repository 后移除相关类，并确保 JPA 扫描不再映射 product 表。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；搜索 `code/ecommerce-inventory` 下不再存在 `@Table(name = "product")` 和 `ProductRepository`；库存入库、出库、查询链路正常。
- **影响范围**：inventory JPA 扫描与模块边界、误用 product 表的潜在代码。

### EIN-003 InventoryQueryService 泄露 product DTO

- **所属模块**：ecommerce-inventory
- **设计要求**：`design-docs/02-系统架构.md` 第 3 章表“DTO：跨模块传输使用 DTO，不暴露 JPA Entity”；第 4 章表“关键本地接口”中 `InventoryQueryService | inventory | product、cart、order | 查询库存摘要`。
- **当前状态**：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/query/InventoryQueryService.java:10-22` 的 `getStockSummary` 返回 `com.ecommerce.product.query.StockSummaryDto`；`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/query/StockSummaryDto.java:7-35` 已存在 inventory 本地 DTO；`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryService.java:60-67` 实现返回 product 包 DTO。
- **不一致描述**：inventory 提供的查询契约绑定 product 包 DTO，扩大模块耦合。
- **修复方案**：将 `com.ecommerce.inventory.query.InventoryQueryService#getStockSummary(Long skuId)` 返回类型改为 `com.ecommerce.inventory.query.StockSummaryDto`；同步调整 `InventoryService` 实现；product/cart/order 使用方按 inventory DTO 适配。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；静态检查 inventory 查询接口不再引用 `com.ecommerce.product.query.StockSummaryDto`；库存摘要返回语义不变。
- **影响范围**：inventory 查询契约、product/cart/order 对库存摘要 DTO 的 import 与测试。

### EIN-004 库存摘要缓存未实现

- **所属模块**：ecommerce-inventory
- **设计要求**：`design-docs/02-系统架构.md` 第 7 章表“缓存设计”中 `库存摘要 | inventory:summary:{skuId} | 30 秒 | inventory`。
- **当前状态**：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryService.java:60-67` 的 `getStockSummary` 直接查 `InventoryStockRepository`；`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryService.java:93-119` 的 REST 摘要也直接查库；模块内未找到 `inventory:summary`、`@Cacheable`、`@CacheEvict`、`CacheManager` 或 Redis 配置。
- **不一致描述**：未按设计实现库存摘要缓存 Key 与 30 秒 TTL。
- **修复方案**：新增 inventory 缓存配置或复用全局缓存，在库存摘要查询上使用 `inventory:summary:{skuId}`、TTL 30 秒；在入库、出库、库存调整、预占、释放、扣减等改变库存的方法后驱逐或更新对应 SKU 缓存。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；重复查询同一 SKU 命中缓存；库存变更后缓存失效；TTL 为 30 秒。
- **影响范围**：库存查询性能与一致性、`InventoryService`、`StockAdjustmentService`、`InventoryReservationServiceImpl`、缓存配置。

### EIN-005 库存管理接口缺少 ADMIN 角色约束

- **所属模块**：ecommerce-inventory
- **设计要求**：`design-docs/02-系统架构.md` 第 8 章第 3 条“管理类接口需要 ADMIN 角色”；`README.md` 第 6.3 节库存管理接口认证列为 ADMIN。
- **当前状态**：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/controller/AdminInventoryController.java:25-77` 只有 `@RestController`、`@RequestMapping("/api/v1/admin")`，无 `@PreAuthorize`/`@Secured`；inventory 模块内未找到方法级安全配置或角色约束。
- **不一致描述**：库存管理端接口未在模块代码中体现 ADMIN 鉴权。
- **修复方案**：在 `AdminInventoryController` 类或方法上添加 `@PreAuthorize("hasRole('ADMIN')")`，并确保应用启用方法级安全；若采用全局路径鉴权，也需确认 `/api/v1/admin/**` 规则覆盖库存接口。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；匿名/USER 调用库存管理接口返回 401/403；ADMIN 调用仍符合 README 成功状态码。
- **影响范围**：仓库创建、入库、出库、调整、库存预警查询等库存管理 REST 接口。

### EIN-006 出库接口使用 query param 而非请求体 DTO

- **所属模块**：ecommerce-inventory
- **设计要求**：`README.md` 第 6 章 API 基线要求 Request/Response 字段名和类型冻结；第 6.3 节固定 `POST /api/v1/admin/inventory/outbound` 为 ADMIN、成功 201。
- **当前状态**：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/controller/AdminInventoryController.java:56-62` 的 `outbound` 使用 `@RequestParam Long warehouseId`、`@RequestParam Long skuId`、`@RequestParam int quantity`、`@RequestParam(required = false) Long orderId`；模块内未找到 `OutboundRequest` DTO。
- **不一致描述**：出库业务字段通过 query param 承载，与报告要求的冻结 Request Body 契约存在偏差。
- **修复方案**：新增 `OutboundRequest` DTO，字段保持 `warehouseId: Long`、`skuId: Long`、`quantity: int`、`orderId: Long`；`AdminInventoryController#outbound` 改为 `@Valid @RequestBody OutboundRequest request`，再调用现有 `InventoryService.outbound(...)`。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；使用 JSON Body 调用 `POST /api/v1/admin/inventory/outbound` 返回 201；URL、Method 不变。
- **影响范围**：库存出库管理接口调用方、Controller 参数绑定；服务层可保持不变。

### EIN-007 库存调整接口使用 query param 而非请求体 DTO

- **所属模块**：ecommerce-inventory
- **设计要求**：`README.md` 第 6 章 API 基线；第 6.3 节固定 `POST /api/v1/admin/inventory/adjustments` 为 ADMIN、成功 201。
- **当前状态**：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/controller/AdminInventoryController.java:65-71` 的 `createAdjustment` 使用 `@RequestParam Long warehouseId`、`@RequestParam Long skuId`、`@RequestParam int afterQty`、`@RequestParam String reason`；模块内未找到调整请求 DTO。
- **不一致描述**：库存调整业务字段通过 query param 承载，与冻结 Request Body 契约存在偏差。
- **修复方案**：新增 `StockAdjustmentRequest` 或 `AdjustmentRequest` DTO，字段保持 `warehouseId: Long`、`skuId: Long`、`afterQty: int`、`reason: String`；Controller 改为 `@Valid @RequestBody DTO`，再调用 `StockAdjustmentService.create(...)`。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；使用 JSON Body 调用 `POST /api/v1/admin/inventory/adjustments` 返回 201；URL、Method、成功状态码不变。
- **影响范围**：库存调整管理接口调用方、Controller 参数绑定；服务层可保持不变。

---

## ecommerce-review

### ER-001 review 直接依赖 loyalty

- **所属模块**：ecommerce-review
- **设计要求**：`design-docs/02-系统架构.md` 第 2 章模块依赖图；第 3 章模块边界规则；第 5 章表“核心事件”中 `ReviewApprovedEvent | review | loyalty | 发放评价积分`。
- **当前状态**：`code/ecommerce-review/pom.xml:22-26` 声明对 `ecommerce-loyalty` 的 Maven 依赖。
- **不一致描述**：review 直接依赖 loyalty，与“review 发布事件、loyalty 监听”的事件解耦方向不一致。
- **修复方案**：删除 `ecommerce-review/pom.xml` 中 `ecommerce-loyalty` 依赖；review 仅发布审核通过事件，不直接依赖 loyalty 实现。若事件契约需共享，按 `ER-005/ELT-003` 统一为共同可见的事件类型。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；检查 `ecommerce-review/pom.xml` 不再包含 `ecommerce-loyalty`；审核通过仍能发布事件。
- **影响范围**：review 模块依赖关系、loyalty 监听 import、事件契约统一。

### ER-002 review 未使用 UserQueryService 校验用户状态

- **所属模块**：ecommerce-review
- **设计要求**：`design-docs/02-系统架构.md` 第 4 章表“关键本地接口”中 `UserQueryService | user | order、review | 查询用户状态、冻结状态`；`README.md` 第 7.2 节 `USER_NOT_ACTIVE`、`USER_FROZEN`。
- **当前状态**：`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:39-55` 仅注入 `OrderQueryService`；`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:111-119` 仅校验购买记录；可用接口为 `code/ecommerce-user/src/main/java/com/ecommerce/user/query/UserQueryService.java:8-41`。
- **不一致描述**：创建评价等用户入口未按设计查询用户激活/冻结状态。
- **修复方案**：在 `ReviewService` 构造函数中注入 `com.ecommerce.user.query.UserQueryService`；在创建评价前调用 `isActive(userId)`、`isFrozen(userId)`；未激活抛 `USER_NOT_ACTIVE`，冻结抛 `USER_FROZEN`；必要时在 `ecommerce-review/pom.xml` 增加对 user 查询接口所在模块依赖。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；冻结用户创建评价返回 403/`USER_FROZEN`，未激活用户返回 403/`USER_NOT_ACTIVE`；正常用户仍可创建评价。
- **影响范围**：评价创建流程、review 与 user 查询依赖、相关测试。

### ER-003 创建评价时提前发布 ReviewApprovedEvent

- **所属模块**：ecommerce-review
- **设计要求**：`design-docs/02-系统架构.md` 第 5 章表“核心事件”中 `ReviewApprovedEvent | review | loyalty | 发放评价积分`，语义为评价审核通过后触发。
- **当前状态**：`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:98-107` 创建评价时状态为 `PENDING_REVIEW` 后立即发布 `ReviewApprovedEvent`；`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewModerationService.java:52-64` 审核通过后也发布事件。
- **不一致描述**：创建评价时提前发布审核通过事件，可能提前触发积分发放。
- **修复方案**：移除 `ReviewService#createReview` 中发布 `ReviewApprovedEvent` 的逻辑；仅保留 `ReviewModerationService#approve` 在状态更新为 `APPROVED` 后发布。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；创建评价后不产生评价积分；管理员审核通过后才发布事件并触发积分。
- **影响范围**：评价创建流程、审核通过流程、积分发放时机。

### ER-004 review 内部承担评价积分监听职责

- **所属模块**：ecommerce-review
- **设计要求**：`design-docs/02-系统架构.md` 第 5 章表“核心事件”中 `ReviewApprovedEvent` 监听方为 loyalty。
- **当前状态**：`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewApprovedEventListener.java:12-55` 在 review 模块内监听并模拟发放评价积分；loyalty 侧已有 `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEventListener.java:12-44`。
- **不一致描述**：review 模块承担了 loyalty 的监听和积分发放职责。
- **修复方案**：删除或停用 review 模块内 `ReviewApprovedEventListener`；积分发放只由 loyalty 监听统一 `ReviewApprovedEvent` 后调用 `LoyaltyPointService`。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；静态检查 review 模块不再存在监听 `ReviewApprovedEvent` 的 `@EventListener`；审核通过后积分流水只由 loyalty 产生。
- **影响范围**：review 事件监听类、loyalty 积分发放链路。

### ER-005 ReviewApprovedEvent 重复定义导致事件类型不匹配

- **所属模块**：ecommerce-review
- **设计要求**：`design-docs/02-系统架构.md` 第 5 章表“核心事件”中 `ReviewApprovedEvent | review | loyalty | 发放评价积分`。
- **当前状态**：review 事件类型为 `code/ecommerce-review/src/main/java/com/ecommerce/review/event/ReviewApprovedEvent.java:1-27`；review 发布点为 `code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewModerationService.java:62-63`；loyalty 另有同名事件 `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEvent.java:1-29`，监听器监听 loyalty 包类型 `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEventListener.java:26-35`。
- **不一致描述**：review 发布与 loyalty 监听的不是同一 Java 类型，Spring 事件无法按类型匹配。
- **修复方案**：统一 `ReviewApprovedEvent` 为单一公共事件契约；review 发布该统一类型，loyalty 监听同一类型；删除或替换 loyalty 本地重复事件类并同步 import。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；发布 review 的审核通过事件后，loyalty listener 能收到并调用积分服务。
- **影响范围**：review 事件类、loyalty 事件类、loyalty 监听器 import、review 发布点。

---

## ecommerce-order

### EO-001 订单创建积分抵扣未使用 LoyaltyCommandService

- **所属模块**：ecommerce-order
- **设计要求**：`design-docs/02-系统架构.md` 第 3 章表“命令依赖：跨模块命令必须通过领域服务接口或事件”；第 4 章表“关键本地接口”中 `LoyaltyCommandService | loyalty | order、payment | 积分抵扣、发放`。
- **当前状态**：`code/ecommerce-order/pom.xml:37-41` 依赖 `ecommerce-loyalty`；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:11,84,201` 注入并调用 `LoyaltyQueryService.estimateRedeemPoints(...)`；`code/ecommerce-order/src/main/java/com/ecommerce/order/integration/LoyaltyIntegrationService.java:4,41-44` 也仅依赖 `LoyaltyQueryService`；loyalty 命令接口位于 `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/query/LoyaltyCommandService.java:10-55`。
- **不一致描述**：积分抵扣使用查询接口估算，未通过命令接口执行抵扣/冻结。
- **修复方案**：订单创建链路注入 `LoyaltyCommandService`；按事务语义选择创建订单时 `freezePoints`、支付成功后 `consumeFrozenPoints`、取消订单时 `unfreezePoints`，或直接 `redeemPoints`；估算逻辑可保留为展示/校验，但不能替代命令。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；创建带 `redeemPoints` 的订单后积分可用/冻结/已兑换值按预期变化；取消或支付成功后积分状态正确。
- **影响范围**：订单创建、取消、支付成功后的积分处理、order 与 loyalty 本地接口协作。

### EO-002 OrderPaymentStatusUpdater 未完成支付确认事务和事件发布

- **所属模块**：ecommerce-order
- **设计要求**：`design-docs/02-系统架构.md` 第 4 章表中 `OrderPaymentStatusUpdater | order | payment | 更新订单支付状态`；第 5 章表中 `OrderPaidEvent | order | logistics、loyalty、notification`；第 6 章第 2 条“支付确认事务只包含支付单状态、订单支付状态和库存扣减”。
- **当前状态**：`code/ecommerce-order/src/main/java/com/ecommerce/order/query/OrderPaymentStatusUpdater.java:17` 定义 `markAsPaid(Long orderId, String paymentNo)`；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderQueryServiceImpl.java:124-146` 实现只更新订单状态、`paymentNo`、`paidAt`、`paidAmount` 并保存订单。
- **不一致描述**：payment 调用 `markAsPaid` 后未扣减库存、未发布 `OrderPaidEvent`。
- **修复方案**：将 `markAsPaid` 委托到统一支付成功处理服务，或在该方法内同一事务完成订单支付状态更新、库存扣减、事件记录并发布 `OrderPaidEvent`；避免与 `OrderPaymentEventHandler.handlePaymentSuccess` 形成两套路径。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；payment 回调调用 `OrderPaymentStatusUpdater.markAsPaid` 后订单为 PAID、库存扣减、`OrderPaidEvent` 可被物流/积分/通知监听。
- **影响范围**：支付成功链路、库存扣减、物流/积分/通知事件触发。

### EO-003 库存扣减失败被当作非关键后置动作吞掉

- **所属模块**：ecommerce-order
- **设计要求**：`design-docs/02-系统架构.md` 第 6 章第 2 条“支付确认事务只包含支付单状态、订单支付状态和库存扣减”；第 6 章第 3 条“支付后物流、积分、通知通过事件异步处理，不得使支付确认失败”。
- **当前状态**：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPaymentEventHandler.java:67-127` 处理支付成功；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPaymentEventHandler.java:114-123` 捕获 `inventoryReservationService.deductAfterPayment(orderId)` 异常，仅记录日志后继续。
- **不一致描述**：关键库存扣减失败被吞掉，订单仍可能变为 PAID 并发布事件。
- **修复方案**：调整 `OrderPaymentEventHandler.handlePaymentSuccess`，库存扣减异常必须向外抛出并回滚支付确认事务；只允许物流、积分、通知监听器失败被记录补偿且不阻塞支付。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；模拟库存扣减异常时订单不提交为 PAID、`OrderPaidEvent` 不发布；模拟后置物流/积分/通知失败时支付仍成功。
- **影响范围**：支付成功事务一致性、库存数据一致性、后置事件发布时机。

### EO-004 批量订单导入未按单条事务处理

- **所属模块**：ecommerce-order
- **设计要求**：`design-docs/02-系统架构.md` 第 6 章第 4 条“批量订单导入按单条事务处理，一条失败不得回滚整批”。
- **当前状态**：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/BatchOrderService.java:19-20` 类级 `@Transactional` 包住整个服务；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/BatchOrderService.java:38-70` 在同一事务循环中调用 `orderService.createOrder(...)`；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:72-73` 类级事务会参与外层事务。
- **不一致描述**：整批共享一个事务，单条订单没有独立提交/回滚边界。
- **修复方案**：移除整批外层事务；或新增单条创建方法并通过 Spring 代理以 `@Transactional(propagation = REQUIRES_NEW)` 调用；`continueOnError=false` 只停止后续处理，不回滚前序成功单。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；批量请求中前一单成功、后一单失败时数据库保留前一单；`continueOnError=true` 返回逐条结果；`continueOnError=false` 停止后续且不回滚已成功订单。
- **影响范围**：批量下单事务边界、批量接口错误处理、订单与库存预占一致性。

### EO-005 创建订单事务缺少优惠使用记录

- **所属模块**：ecommerce-order
- **设计要求**：`design-docs/02-系统架构.md` 第 6 章第 1 条“创建订单事务只包含订单主数据、订单明细、库存预占记录和优惠使用记录”。
- **当前状态**：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:190-191` 计算促销优惠；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:242-247` 仅把 couponIds 快照到订单；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:415-418` 调用 `PromotionCalculationService.calculate(...)`；`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationService.java:67-89,129-171` 主要做优惠计算，未写使用记录。
- **不一致描述**：订单创建未生成优惠使用记录，只保存优惠券 ID 快照。
- **修复方案**：在创建订单事务中新增优惠使用记录写入。优先在 promotion 模块提供 `PromotionUsageCommandService`，用于锁定/核销所选优惠券并记录订单号、用户、优惠券、优惠金额；order 创建订单时调用该接口；若 promotion 尚无对应实体/Repository，按 `EPRO-003` 新增。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；使用优惠券创建订单后 promotion 侧存在使用记录；重复使用同一券被拒绝或按设计处理；订单创建失败时使用记录不残留。
- **影响范围**：订单创建、促销券状态/使用记录、优惠金额一致性。

### EO-006 订单模块使用未冻结错误码

- **所属模块**：ecommerce-order
- **设计要求**：`README.md` 第 7.1 节“通用错误码”和第 7.2 节“业务错误码”定义对外错误码冻结清单。
- **当前状态**：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/BatchOrderService.java:66-68` 使用 `BATCH_ORDER_FAILED`；`OrderPaymentEventHandler.java:85-87` 使用 `ORDER_NOT_PAYABLE`；`OrderQueryServiceImpl.java:71-73,131-133` 使用 `ORDER_NOT_PAYABLE`、`ORDER_INVALID_STATUS`；`ProductIntegrationService.java:67` 使用 `PRODUCT_VALIDATION_FAILED`；`OrderPreconditionChecker.java:34,46` 使用 `USER_NOT_FOUND`、`ORDER_EMPTY`；同类还有 `OrderValidator.java:47`、`OrderValidationUtils.java:56`。
- **不一致描述**：订单模块抛出多个 README 第 7 章未定义错误码。
- **修复方案**：统一映射为冻结错误码：资源不存在使用 `RESOURCE_NOT_FOUND`；订单状态不可操作使用 `ORDER_STATUS_CONFLICT`；商品不可售使用 `PRODUCT_NOT_FOR_SALE`；参数校验、空订单使用 `VALIDATION_FAILED`；批量失败按具体失败项或 `CONFLICT`/`VALIDATION_FAILED` 表达。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；触发对应异常场景，响应 `code` 均属于 README 第 7 章；重点验证订单状态冲突返回 `ORDER_STATUS_CONFLICT`。
- **影响范围**：订单 REST/跨模块可见异常、黑盒错误码断言、统一异常处理契约。

---

## ecommerce-promotion

### EPRO-001 PromotionCalculationService 接口形态与设计不一致

- **所属模块**：ecommerce-promotion
- **设计要求**：`design-docs/02-系统架构.md` 第 3 章要求跨模块查询/命令通过接口且使用 DTO；第 4 章表“关键本地接口”中 `PromotionCalculationService | promotion | order、cart | 计算优惠`。
- **当前状态**：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationService.java:23-67` 是 `@Service` 具体类；`code/ecommerce-common/src/main/java/com/ecommerce/common/integration/PromotionDiscountCalculator.java:10-12` 才是当前跨模块折扣接口；`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/query/PromotionQueryService.java:19-55` 另有接口但命名和入参不符合设计语义。
- **不一致描述**：设计要求的关键本地接口名被具体类占用，跨模块抽象不在 promotion 提供的设计接口上。
- **修复方案**：在 promotion 模块建立 `PromotionCalculationService` 接口；将现有实现重命名为 `PromotionCalculationServiceImpl` 或等效实现类；接口方法使用跨模块 DTO 表达订单/购物车项和优惠券选择，供 order/cart 调用；逐步移除或适配 common `PromotionDiscountCalculator`。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；确认 order/cart 注入 promotion 提供的 `PromotionCalculationService`；`/api/v1/promotions/calculate` 路径、方法、状态码不变。
- **影响范围**：promotion 服务接口与实现、cart/order 注入点、common 集成 port 迁移。

### EPRO-002 cart 对 promotion 使用关系未按设计体现

- **所属模块**：ecommerce-promotion
- **设计要求**：`design-docs/02-系统架构.md` 第 1 章同步本地接口用于查询/校验/强一致链路；第 2 章模块依赖图；第 4 章表中 `PromotionCalculationService` 使用方为 order、cart。
- **当前状态**：`code/ecommerce-cart/pom.xml:12-30` 未依赖 `ecommerce-promotion`；`code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:13,44-49,162-188,274-277` 注入并使用 common 包 `PromotionDiscountCalculator`；`code/ecommerce-order/pom.xml:32-36` 已直接依赖 promotion。
- **不一致描述**：cart 未通过 promotion 提供的设计接口计算优惠。
- **修复方案**：在 `ecommerce-cart/pom.xml` 增加对 promotion 接口所在模块依赖；`CartService` 注入 `PromotionCalculationService` 并进行 DTO 转换；若保留 common port，仅作为适配层，仍需确保 promotion 提供的接口可追踪。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；调用 `POST /api/v1/cart/estimate`，优惠金额仍正确；静态检查 `CartService` 不再直接依赖不符合第 4 章命名的 common port。
- **影响范围**：cart POM、`CartService`、promotion 本地接口契约、order/promotion 接口统一。

### EPRO-003 promotion 缺少优惠使用记录写入能力

- **所属模块**：ecommerce-promotion
- **设计要求**：`design-docs/02-系统架构.md` 第 6 章第 1 条“创建订单事务只包含订单主数据、订单明细、库存预占记录和优惠使用记录”。
- **当前状态**：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/UserCoupon.java:32-39,80-101` 有 `usedOrderId`、`usedAt` 字段和 setter；`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationService.java:67-90` 仅计算优惠；`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponService.java:37-69` 仅处理领券；未找到将用户券标记为已使用并记录订单 ID/时间的方法。
- **不一致描述**：promotion 模块未提供订单创建事务所需的优惠使用记录能力。
- **修复方案**：新增 `CouponUsageService` 或 `PromotionUsageCommandService`；校验用户券归属和可用状态后设置 `CouponStatus.USED`、`usedOrderId`、`usedAt` 并保存；提供本地命令接口供 order 在创建订单事务中调用。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；使用优惠券创建订单后 `user_coupon.status=USED`、`used_order_id`、`used_at` 被写入；订单创建失败时回滚使用记录。
- **影响范围**：promotion 优惠券状态流转、order 创建订单事务、用户券重复使用校验。

### EPRO-004 促销用户侧接口缺少 USER 角色约束

- **所属模块**：ecommerce-promotion
- **设计要求**：`design-docs/02-系统架构.md` 第 8 章第 2 条“用户侧接口需要 Authorization: Bearer <token>”；`README.md` 第 6.7 节中 `/api/v1/promotions/coupons/claim`、`/api/v1/promotions/coupons/my`、`/api/v1/promotions/calculate` 认证为 USER。
- **当前状态**：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/PromotionController.java:56-108,114-133` 通过 `SecurityContextHolder` 提取 principal，但类/方法无 USER 角色约束；模块内未找到 `@PreAuthorize`、`@Secured`、`@RolesAllowed`、方法级安全或 `SecurityFilterChain`。
- **不一致描述**：用户侧促销接口未在模块内体现可执行 USER 鉴权约束。
- **修复方案**：在 `PromotionController` 类或对应用户方法上添加 `@PreAuthorize("hasRole('USER')")`，并确保方法级安全生效；若采用全局路径鉴权，则确认 `/api/v1/promotions/**` 要求认证 USER 且覆盖这些接口。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；匿名请求返回 401，非 USER 返回 403，USER token 保持成功。
- **影响范围**：promotion 用户侧 REST 鉴权、全局安全配置或方法级安全配置。

### EPRO-005 促销管理端接口缺少 ADMIN 角色约束

- **所属模块**：ecommerce-promotion
- **设计要求**：`design-docs/02-系统架构.md` 第 8 章第 3 条“管理类接口需要 ADMIN 角色”；`README.md` 第 6.7 节促销管理接口认证为 ADMIN。
- **当前状态**：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/AdminPromotionController.java:19-68` 只有注释说明 ADMIN 由网关或安全层执行，类/方法无可执行角色校验。
- **不一致描述**：管理促销接口未在模块内体现 ADMIN 鉴权约束。
- **修复方案**：在 `AdminPromotionController` 类或方法上添加 `@PreAuthorize("hasRole('ADMIN')")`，并确保方法级安全生效；若采用全局路径鉴权，则确认 `/api/v1/admin/promotions/**` 被 ADMIN 规则覆盖。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；非管理员访问返回 403；管理员访问仍返回 201。
- **影响范围**：promotion 管理端 REST 鉴权、全局安全配置或方法级安全配置。

### EPRO-006 promotion 使用未冻结错误码

- **所属模块**：ecommerce-promotion
- **设计要求**：`README.md` 第 7.1-7.2 节定义冻结错误码；促销直接相关业务错误码仅列出 `COUPON_EXPIRED`，通用错误码包含 `VALIDATION_FAILED`、`RESOURCE_NOT_FOUND`、`CONFLICT` 等。
- **当前状态**：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponService.java:47-54,122-128` 抛出 `COUPON_LIMIT_EXCEEDED`、`COUPON_EXHAUSTED`、`COUPON_EXPIRED`；`CouponValidator.java:32-52` 抛出 `COUPON_INVALID`、`COUPON_EXPIRED`；`SeckillService.java:56-70` 抛出 `SECKILL_NOT_STARTED`、`SECKILL_ENDED`、`SECKILL_SOLD_OUT`。
- **不一致描述**：promotion 暴露多个 README 未定义业务错误码。
- **修复方案**：错误码收敛到 README 第 7 章：过期/未开始/不可用等使用 `COUPON_EXPIRED`；资源不存在用 `RESOURCE_NOT_FOUND`；重复、超限、售罄等状态冲突用 `CONFLICT`；参数问题用 `VALIDATION_FAILED`；不新增未列错误码。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；触发领券超限、优惠券无效、秒杀未开始/结束/售罄，响应 code 均属于 README 第 7 章。
- **影响范围**：promotion 服务异常码、全局异常映射、黑盒错误码断言。

---

## ecommerce-logistics

### EL-001 logistics 缺少 LogisticsCommandService

- **所属模块**：ecommerce-logistics
- **设计要求**：`design-docs/02-系统架构.md` 第 4 章表“关键本地接口”中 `LogisticsCommandService | logistics | event listener | 创建发货单`。
- **当前状态**：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:25-34` 直接注入 `ShipmentRepository`、`ShipmentService`、`OrderQueryService`；`OrderPaidShipmentListener.java:43-53` 在监听器内完成幂等检查、查询订单和创建发货单；未找到 `LogisticsCommandService`。
- **不一致描述**：事件监听器直接耦合 Repository/Service/订单查询，未通过设计要求的本地命令接口封装。
- **修复方案**：新增 `LogisticsCommandService` 接口及实现，封装“按已支付订单创建发货单”；事件监听器只依赖该接口；实现内部通过 `OrderQueryService#getOrder` 查询订单并调用 `ShipmentService#createShipment`，保留已存在发货单时跳过的幂等逻辑。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；Spring 容器能注入 `LogisticsCommandService`；支付后发货单异步创建且重复事件不重复创建。
- **影响范围**：logistics 事件监听与服务层、新增命令接口及实现。

### EL-002 logistics 未监听 PaymentSucceededEvent

- **所属模块**：ecommerce-logistics
- **设计要求**：`design-docs/02-系统架构.md` 第 5 章表“核心事件”中 `PaymentSucceededEvent | payment | order、logistics、loyalty、notification | 订单状态更新失败应告警，非关键后置动作不回滚支付`。
- **当前状态**：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:3` 仅导入 `OrderPaidEvent`；`OrderPaidShipmentListener.java:37-40` 仅监听 `OrderPaidEvent`；payment 事件类存在于 `code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:7-14`。
- **不一致描述**：payment 发布 `PaymentSucceededEvent` 时 logistics 不会触发发货单创建。
- **修复方案**：在 logistics 事件适配层新增 `PaymentSucceededEvent` 的 `@Async` + `@TransactionalEventListener(phase = AFTER_COMMIT)` 监听方法或独立监听器，并复用 `LogisticsCommandService` 创建发货单；异常捕获并记录，不回滚支付。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；发布 `PaymentSucceededEvent` 后可创建 shipment；监听失败不影响支付成功。
- **影响范围**：logistics 事件包、payment 事件契约依赖、发货单创建触发源。

### EL-003 物流签收未发布 ShipmentDeliveredEvent

- **所属模块**：ecommerce-logistics
- **设计要求**：`design-docs/02-系统架构.md` 第 5 章表“核心事件”中 `ShipmentDeliveredEvent | logistics | order、loyalty | 更新订单签收状态，失败可重试`。
- **当前状态**：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/ShipmentService.java:254-277` 在 `updateStatus` 状态为 `DELIVERED` 时仅设置 `deliveredAt`、保存 shipment、记录 tracking，并同步调用订单物流状态更新接口；仓库内未找到 `ShipmentDeliveredEvent`。
- **不一致描述**：签收后未发布设计要求的领域事件。
- **修复方案**：新增 `ShipmentDeliveredEvent`，字段至少包含 `shipmentId`、`orderId`、`userId`、`deliveredAt`；在 `ShipmentService#updateStatus` 保存 `DELIVERED` 状态和轨迹后通过 `ApplicationEventPublisher` 发布；order/loyalty 监听处理并支持失败记录/重试。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；签收回调后发布事件；order/loyalty 监听失败不回滚物流签收，并可被失败记录观察。
- **影响范围**：logistics 服务层和事件契约、order/loyalty 监听实现。

### EL-004 签收状态同步使用设计未列出的同步接口

- **所属模块**：ecommerce-logistics
- **设计要求**：`design-docs/02-系统架构.md` 第 4 章关键本地接口未列出 `OrderLogisticsStatusUpdater`；第 5 章要求 `ShipmentDeliveredEvent` 通知 order、loyalty。
- **当前状态**：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/query/OrderLogisticsStatusUpdater.java:1-19` 定义同步订单物流状态更新接口；`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/ShipmentService.java:272-277` 在物流状态更新后同步调用该接口。
- **不一致描述**：物流签收使用同步接口更新订单，而非通过设计事件实现失败可重试。
- **修复方案**：移除或停止在签收链路同步调用 `OrderLogisticsStatusUpdater`；由 order 监听 `ShipmentDeliveredEvent` 更新订单签收/物流状态，loyalty 按需监听；若保留非签收中间状态同步，`DELIVERED` 最终状态仍以事件为准。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；签收后订单状态由事件监听更新；模拟 order/loyalty 监听失败时物流签收不回滚。
- **影响范围**：`ShipmentService`、`OrderLogisticsStatusUpdater`、order 对同步接口实现的迁移。

### EL-005 运费模板缓存未实现

- **所属模块**：ecommerce-logistics
- **设计要求**：`design-docs/02-系统架构.md` 第 7 章表“缓存设计”中 `运费模板 | logistics:freight:{templateId} | 30 分钟 | logistics`。
- **当前状态**：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/FreightCalculator.java:45-58` 默认计算直接读取活跃模板；`FreightCalculator.java:79-91` 指定 templateId 时直接 `findById`；`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/config/LogisticsConfig.java:10-15` 只有组件扫描配置；模块内未找到缓存实现或 `logistics:freight`。
- **不一致描述**：运费模板没有固定 Key 和 30 分钟 TTL 缓存。
- **修复方案**：为运费模板读取增加缓存，Key 为 `logistics:freight:{templateId}`、TTL 30 分钟；指定模板计算走缓存；默认活跃模板查询也缓存或映射到明确模板 Key；创建/更新/停用模板时清理对应缓存。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；重复计算同一模板第二次命中缓存；修改模板后缓存失效；运费计算仍符合公开用例。
- **影响范围**：运费计算、运费模板服务、缓存配置。

### EL-006 物流管理接口路径变量名不符合冻结契约

- **所属模块**：ecommerce-logistics
- **设计要求**：`README.md` 第 6.7 节固定 `POST /api/v1/admin/logistics/shipments/{shipmentId}/pick`、`/print-label`、`/outbound`。
- **当前状态**：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/controller/AdminLogisticsController.java:45-69` 使用 `@PostMapping("/shipments/{id}/pick")`、`@PostMapping("/shipments/{id}/print-label")`、`@PostMapping("/shipments/{id}/outbound")`。
- **不一致描述**：路径变量名使用 `{id}`，与 README 冻结契约 `{shipmentId}` 不一致。
- **修复方案**：将三个 `@PostMapping` 的路径变量改为 `{shipmentId}`，方法参数同步使用 `@PathVariable("shipmentId") Long shipmentId` 或同名参数；不改变 URL 层级、HTTP Method、认证和成功状态码。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；契约/OpenAPI/MockMvc 检查变量名为 `shipmentId`；三个接口仍返回 200。
- **影响范围**：logistics 管理端 Controller 契约表达，不影响数据库和业务流程。

### EL-007 物流状态冲突未返回 CONFLICT/409

- **所属模块**：ecommerce-logistics
- **设计要求**：`README.md` 第 7.1 节通用错误码中 `CONFLICT | 409 | 状态冲突或重复请求`。
- **当前状态**：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/ShipmentService.java:139-149` 的 `pick` 对非法状态抛 `IllegalStateException`；`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:108-113` 会将普通异常转为 `INTERNAL_ERROR`/500；common 已有 `ConflictException`。
- **不一致描述**：物流状态机非法流转会返回 500，而不是冻结通用错误码 `CONFLICT`/409。
- **修复方案**：将 `pick`、`printLabel`、`outbound` 等发货流程中的非法状态流转改为抛 `ConflictException` 或 `BusinessException("CONFLICT", ...)`；补齐不可跳步状态校验，统一返回 HTTP 409、code=`CONFLICT`。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；支付后直接 outbound 等不可跳步场景返回 409/`CONFLICT`；正常 PICKING → LABEL_PRINTED → OUTBOUND 流程仍成功。
- **影响范围**：logistics 发货状态机、REST 错误响应、相关黑盒断言。

---

## ecommerce-loyalty

### ELT-001 会员等级统计未使用 order 的 OrderQueryService

- **所属模块**：ecommerce-loyalty
- **设计要求**：`design-docs/02-系统架构.md` 第 4 章表“关键本地接口”中 `OrderQueryService | order | payment、review、logistics、loyalty | 查询订单、验证购买记录、提供会员等级统计所需订单数据`。
- **当前状态**：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/query/AnnualConsumptionQueryService.java:5-18` 在 loyalty 本模块定义年度消费查询接口；`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/MemberLevelService.java:28-33` 可选注入该接口；`MemberLevelService.java:80-86` 接口缺失时返回 `BigDecimal.ZERO`。
- **不一致描述**：会员等级统计未通过 order 提供的 `OrderQueryService` 获取订单数据，且缺失时静默降级为 0。
- **修复方案**：在 order 模块扩展 `OrderQueryService` 提供年度已支付消费查询方法；loyalty 依赖 order 查询契约，`MemberLevelService` 强依赖 `OrderQueryService` 并调用该方法；删除或停用 `AnnualConsumptionQueryService` 和返回 0 的降级逻辑。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；会员等级达到 SILVER/GOLD/PLATINUM 的统计来自 order 查询结果；缺少 order 查询 Bean 时启动或测试失败而非静默降级。
- **影响范围**：会员等级计算、order 查询接口契约、Maven 模块依赖方向。

### ELT-002 OrderPaidEvent 在 loyalty 中重复定义

- **所属模块**：ecommerce-loyalty
- **设计要求**：`design-docs/02-系统架构.md` 第 5 章表“核心事件”中 `OrderPaidEvent | order | logistics、loyalty、notification | 失败记录补偿任务，不回滚支付`。
- **当前状态**：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/OrderPaidEvent.java:1-37` 在 loyalty 本模块重复定义事件；`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/OrderPaidEventListener.java:31-39` 监听该本模块事件；order 实际事件为 `code/ecommerce-order/src/main/java/com/ecommerce/order/event/OrderPaidEvent.java:1-42`。
- **不一致描述**：loyalty 监听类型与 order 发布类型不同，Spring 事件无法匹配。
- **修复方案**：统一 `OrderPaidEvent` 契约；优先放入 common 可见事件包，或让 loyalty 监听 order 发布的唯一事件类型；`OrderPaidEventListener` 改为监听同一类型并按其字段计算积分；删除/替换 loyalty 本地同名事件类及测试引用。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；发布 order 的 `OrderPaidEvent` 后 loyalty listener 被触发并发放积分。
- **影响范围**：order 支付成功事件契约、loyalty 积分发放监听器、相关测试和模块依赖。

### ELT-003 ReviewApprovedEvent 在 loyalty 中重复定义

- **所属模块**：ecommerce-loyalty
- **设计要求**：`design-docs/02-系统架构.md` 第 5 章表“核心事件”中 `ReviewApprovedEvent | review | loyalty | 发放评价积分`。
- **当前状态**：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEvent.java:1-29` 在 loyalty 本模块重复定义事件；`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEventListener.java:26-35` 监听该本模块事件；review 事件为 `code/ecommerce-review/src/main/java/com/ecommerce/review/event/ReviewApprovedEvent.java:1-27`。
- **不一致描述**：loyalty 监听类型与 review 发布类型不同，评价审核通过事件无法触达 loyalty。
- **修复方案**：统一 `ReviewApprovedEvent` 为单一类型；loyalty 监听 review 发布的同一公共事件；删除/替换 loyalty 本地同名事件类和测试引用。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；review 审核通过后 loyalty 发放评价积分。
- **影响范围**：review 审核通过事件契约、loyalty 评价积分监听器。

### ELT-004 loyalty 缺少 PaymentSucceededEvent 和 ShipmentDeliveredEvent 监听

- **所属模块**：ecommerce-loyalty
- **设计要求**：`design-docs/02-系统架构.md` 第 5 章表“核心事件”中 `PaymentSucceededEvent | payment | order、logistics、loyalty、notification` 与 `ShipmentDeliveredEvent | logistics | order、loyalty`。
- **当前状态**：`code/ecommerce-loyalty/src/main/java/` 下未找到 `PaymentSucceededEvent` listener 或 `ShipmentDeliveredEvent` listener；payment 事件位于 `code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:1-27`；仓库内未找到 `ShipmentDeliveredEvent` 类型。
- **不一致描述**：loyalty 未实现设计要求的支付成功和物流签收事件监听入口。
- **修复方案**：新增 `PaymentSucceededEventListener` 监听 payment 发布的 `PaymentSucceededEvent`，执行支付成功后的 loyalty 后置动作并捕获/记录失败；新增 `ShipmentDeliveredEventListener` 监听 logistics 发布的 `ShipmentDeliveredEvent`；若 logistics 尚未提供事件类，先按 `EL-003` 建立事件契约。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；事件发布测试验证两个 listener 被触发；listener 异常不回滚支付或物流主流程。
- **影响范围**：loyalty 事件监听层、payment 支付成功事件、logistics 签收事件契约。

### ELT-005 积分发放失败未持久化补偿任务

- **所属模块**：ecommerce-loyalty
- **设计要求**：`design-docs/02-系统架构.md` 第 5 章表“核心事件”中 `OrderPaidEvent` 失败策略为“失败记录补偿任务，不回滚支付”；第 6 章第 3 条要求支付后积分异步处理且不得使支付确认失败。
- **当前状态**：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/OrderPaidEventListener.java:47-49` catch 块仅记录错误日志，注释说明 “Failure only logged, never persisted for retry”；common 已有 `code/ecommerce-common/src/main/java/com/ecommerce/common/event/FailedEventRecord.java:19-44` 和 `DomainEventPublisher.java:53-62`。
- **不一致描述**：积分发放失败不可持久化重试，缺少补偿任务/失败记录。
- **修复方案**：在 `OrderPaidEventListener` 异常兜底中保存补偿或失败记录，可复用 common `FailedEventRecordRepository/FailedEventRecord` 或新增专用补偿任务实体/服务；记录事件类型、订单 ID、用户 ID、金额 payload、错误信息、发生时间、重试状态；异常仍不外抛。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；启用 loyalty 积分故障注入后支付仍成功，且 `/api/v1/admin/events/failures` 或 Repository 可查到补偿/失败记录。
- **影响范围**：loyalty 积分发放失败处理、common 失败事件记录、黑盒可观察结果。

### ELT-006 loyalty 管理接口缺少 ADMIN 角色约束

- **所属模块**：ecommerce-loyalty
- **设计要求**：`design-docs/02-系统架构.md` 第 8 章第 3 条“管理类接口需要 ADMIN 角色”；`README.md` 第 6.7 节 `POST /api/v1/admin/loyalty/points/expire | ADMIN | 200`。
- **当前状态**：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/AdminLoyaltyController.java:13-18` 只有注释说明 Requires ADMIN role；`AdminLoyaltyController.java:32-40` 方法上无 `@PreAuthorize`、`@Secured` 或 `@RolesAllowed`。
- **不一致描述**：loyalty 管理接口未在代码中体现可执行 ADMIN 约束。
- **修复方案**：在 `AdminLoyaltyController` 类或 `expirePoints()` 方法上添加 `@PreAuthorize("hasRole('ADMIN')")` 或等效注解，并确保方法级安全启用；若依赖全局 `/api/v1/admin/**` 鉴权，也建议显式补齐以满足契约可追踪性。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；匿名/USER 返回 401/403，ADMIN 返回 200。
- **影响范围**：loyalty 管理接口鉴权、Spring Security 方法级安全配置、controller 测试。

---

## ecommerce-cart

### ECART-001 cart 使用 product 包库存查询接口

- **所属模块**：ecommerce-cart
- **设计要求**：`design-docs/02-系统架构.md` 第 3 章表“查询依赖：跨模块查询必须通过 QueryService 接口”；第 4 章表中 `InventoryQueryService | inventory | product、cart、order | 查询库存摘要`。
- **当前状态**：`code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:5-8` 导入 `com.ecommerce.product.query.InventoryQueryService` 和 product 包 `StockSummaryDto`；`CartValidationService.java:25-31` 注入该接口；`CartValidationService.java:62-70` 调用 `getStockSummary` 校验库存；inventory 模块已有 `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/query/InventoryQueryService.java` 与 `StockSummaryDto`。
- **不一致描述**：cart 使用了 product 包下库存查询契约，而非 inventory 提供方接口。
- **修复方案**：将 `CartValidationService` import 和注入类型改为 `com.ecommerce.inventory.query.InventoryQueryService`，DTO 改为 `com.ecommerce.inventory.query.StockSummaryDto`；同步更新测试 mock/import；若 inventory 接口返回类型仍为 product DTO，先修复 `EIN-003`。
- **修复后验证**：运行 `mvn -f code/pom.xml -pl ecommerce-cart -am test`；购物车添加、修改、预估时库存不足仍返回 `INVENTORY_NOT_ENOUGH`。
- **影响范围**：cart 库存校验、cart 测试、inventory 查询接口返回类型、product 适配。

### ECART-002 cart 价格预估未使用 PromotionCalculationService

- **所属模块**：ecommerce-cart
- **设计要求**：`design-docs/02-系统架构.md` 第 4 章表中 `PromotionCalculationService | promotion | order、cart | 计算优惠`。
- **当前状态**：`code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:42-55` 注入 `PromotionDiscountCalculator`；`CartService.java:162-188` 构造 `PromotionDiscountCalculator.Item`；`CartService.java:274-278` 调用 `promotionDiscountCalculator.calculateDiscount(...)`；import 位于同文件 `:12-15`。
- **不一致描述**：购物车优惠预估使用 common 包 port，未使用 promotion 提供的设计接口。
- **修复方案**：在 promotion 提供 `PromotionCalculationService` 接口后，cart 依赖并注入该接口；`CartService` 将购物车项和 couponIds 转换为 promotion DTO；停止直接依赖 `com.ecommerce.common.integration.PromotionDiscountCalculator`。
- **修复后验证**：运行 cart 与 promotion 相关测试；通过 `POST /api/v1/cart/estimate` 验证 `discountAmount`、`payableAmount` 正确。
- **影响范围**：cart 价格预估、promotion 计算接口、order/cart 对促销计算的统一接入。

### ECART-003 购物车缓存 Key 未使用 cart:{userId}

- **所属模块**：ecommerce-cart
- **设计要求**：`design-docs/02-系统架构.md` 第 7 章表“缓存设计”中 `购物车 | cart:{userId} | 7 天 | cart`。
- **当前状态**：`code/ecommerce-cart/src/main/java/com/ecommerce/cart/cache/CartCacheManager.java:11-19` 使用 `Cache<Long, CartData>`；`CartCacheManager.java:31-38` 用裸 `userId` 读取；`CartCacheManager.java:46-49` 用裸 `cart.getUserId()` 写入；`CartCacheManager.java:57-59` 用裸 `userId` 删除；`code/ecommerce-cart/src/main/java/com/ecommerce/cart/config/CartCacheConfig.java:34-45` 也创建 `Cache<Long, CartData>`。
- **不一致描述**：TTL 7 天已实现，但 Key 未按设计使用命名空间格式 `cart:{userId}`。
- **修复方案**：将缓存 Bean 和 `CartCacheManager` key 类型改为 `String`；新增私有方法生成 `cart:{userId}`；`getCart/saveCart/removeCart` 全部使用该 key；同步更新单元测试。
- **修复后验证**：运行 `CartCacheManagerTest`、`CartServiceTest` 或 `mvn -f code/pom.xml -pl ecommerce-cart -am test`；保存、读取、删除正常，TTL 仍为 7 天。
- **影响范围**：cart 缓存管理、cart 服务、缓存相关测试；不影响 REST API。

### ECART-004 cart 模块仍存在临时明细落库模型

- **所属模块**：ecommerce-cart
- **设计要求**：`design-docs/02-系统架构.md` 第 7 章缓存设计后说明：“购物车不得落库保存临时明细。订单提交后，订单模块保存订单明细。”
- **当前状态**：`code/ecommerce-cart/src/main/java/com/ecommerce/cart/entity/Cart.java:13-22` 定义 `cart` JPA Entity；`code/ecommerce-cart/src/main/java/com/ecommerce/cart/entity/CartItem.java:17-35` 定义 `cart_item` Entity；`code/ecommerce-cart/src/main/java/com/ecommerce/cart/repository/CartRepository.java:12-16` 与 `CartItemRepository.java:12-18` 定义对应 Repository。
- **不一致描述**：模块仍存在购物车临时明细 JPA 落库模型，与设计存储边界冲突。
- **修复方案**：删除或停用 `Cart`、`CartItem`、`CartRepository`、`CartItemRepository` 及无业务使用的 `CartStatus`；确保购物车临时明细仅存于缓存；订单提交后由 order 模块保存订单明细。
- **修复后验证**：运行 `mvn -f code/pom.xml -pl ecommerce-cart -am test`；全工程编译无残留引用；购物车添加、查询、修改、删除、预估仍通过缓存工作。
- **影响范围**：cart entity/repository 包、JPA 扫描范围、相关测试或编译引用。

### ECART-005 cart 使用未冻结错误码

- **所属模块**：ecommerce-cart
- **设计要求**：`README.md` 第 7.1-7.2 节冻结错误码集合。
- **当前状态**：`code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:79-83` 数量范围错误抛 `INVALID_QUANTITY`；`CartValidationService.java:93-98` 购物车条目数超限抛 `CART_FULL`。
- **不一致描述**：`INVALID_QUANTITY`、`CART_FULL` 不在 README 第 7 章错误码基线中。
- **修复方案**：将 `INVALID_QUANTITY` 映射为 `VALIDATION_FAILED`；将 `CART_FULL` 按参数/业务限制映射为 `VALIDATION_FAILED`，或按状态冲突语义映射为 `CONFLICT`；不新增错误码。
- **修复后验证**：运行 cart 测试；数量非法、购物车超限场景响应 code 均属于 README 第 7 章。
- **影响范围**：cart 校验异常、REST 错误响应、相关断言。

---

## ecommerce-payment

### EPAY-001 支付确认事务缺少库存扣减

- **所属模块**：ecommerce-payment
- **设计要求**：`design-docs/02-系统架构.md` 第 3 章“命令依赖”；第 6 章第 2 条“支付确认事务只包含支付单状态、订单支付状态和库存扣减”；`README.md` 第 6.6 节 `POST /api/v1/payment/callback` 使用签名认证、成功 200。
- **当前状态**：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentCallbackService.java:45-46` 支付回调整体事务；`PaymentCallbackService.java:108-119` 成功回调只更新支付单、订单支付状态并发布事件；`code/ecommerce-payment/pom.xml:12-21` 未依赖 inventory；库存接口为 `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/query/InventoryReservationService.java:29-35`。
- **不一致描述**：payment 支付确认强一致链路没有库存扣减。
- **修复方案**：为 payment 增加对 inventory 查询/命令接口所在模块依赖；在 `PaymentCallbackService#processSuccessCallback` 或委托确认服务中同一事务调用 `InventoryReservationService#deductAfterPayment(orderId)`；扣减失败必须抛出并回滚支付确认；避免与 order 侧已有扣减逻辑重复执行。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；支付成功回调后支付单 SUCCESS、订单已支付、预占库存被扣减；扣减异常时支付确认不提交。
- **影响范围**：payment POM、支付回调事务、inventory 本地接口、order 侧库存扣减路径去重。

### EPAY-002 payment 未使用 LoyaltyCommandService

- **所属模块**：ecommerce-payment
- **设计要求**：`design-docs/02-系统架构.md` 第 4 章表中 `LoyaltyCommandService | loyalty | order、payment | 积分抵扣、发放`；第 6 章第 3 条支付后积分异步处理且不得使支付确认失败。
- **当前状态**：`code/ecommerce-payment/pom.xml:12-21` 未依赖 loyalty；`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:33-46` 未注入 `LoyaltyCommandService`；`PaymentService.java:91-100` 仅发布 `PaymentSucceededEvent`；`PaymentCallbackService.java:27-40,108-119` 未调用 loyalty 命令接口；loyalty 接口位于 `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/query/LoyaltyCommandService.java:10-55`。
- **不一致描述**：payment 未按设计接入积分命令接口。
- **修复方案**：增加 payment 对 loyalty 接口所在模块依赖；支付成功后的积分发放通过 `LoyaltyCommandService#earnPaymentPoints(userId, orderAmount, activityMultiplier)`，建议由 `PaymentSucceededEvent` 后置监听器或单独适配服务执行并捕获异常，确保积分失败不回滚支付确认；涉及积分抵扣时也通过该命令接口完成。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；支付成功后用户积分增加或生成积分历史；模拟 loyalty 故障时支付回调仍成功。
- **影响范围**：payment POM、支付成功事件/监听链路、loyalty 命令接口调用。

### EPAY-003 退款完成通知未通过事件解耦

- **所属模块**：ecommerce-payment
- **设计要求**：`design-docs/02-系统架构.md` 第 3 章“事件依赖/事务”；第 5 章表中 `RefundCompletedEvent | payment | order、notification | 更新售后状态、通知用户`；第 6 章要求非关键后置监听器不影响主事务。
- **当前状态**：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundService.java:37-52` 直接依赖 `LocalNotificationService`；`RefundService.java:169-177` 发布 `RefundCompletedEvent` 后同步发送通知；`RefundService.java:191-202` 构造并发送退款完成通知。
- **不一致描述**：退款完成后仍在 payment 服务内同步通知，通知异常可能影响退款流程。
- **修复方案**：从 `RefundService#processRefund` 移除直接通知依赖和 `send` 调用；由 common/notification 侧监听 `RefundCompletedEvent` 发送退款通知；监听器捕获异常并记录失败，不向退款主流程传播。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；退款完成仍发布 `RefundCompletedEvent`；启用通知故障注入时退款仍完成且失败被记录。
- **影响范围**：payment 退款流程、common notification 事件监听、通知记录生成位置。

### EPAY-004 退款流程未分阶段提交

- **所属模块**：ecommerce-payment
- **设计要求**：`design-docs/02-系统架构.md` 第 6 章第 5 条“退款流程按售后单、验收单、退款单分阶段提交”。
- **当前状态**：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundService.java:97-127` 审核阶段仅更新同一个 `RefundRecord`；`RefundService.java:132-160` 仓库验收方法在同一事务中设置验收状态后立即处理退款完成；`RefundService.java:162-174` 同一流程内更新支付单为 REFUNDED 并发布事件；`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/RefundRecord.java:14-53` 仅有一张记录，未找到独立售后单、验收单、退款单实体。
- **不一致描述**：仓库验收与退款完成合并在同一事务和同一记录上，未体现分阶段提交。
- **修复方案**：拆分退款生命周期为独立阶段和事务边界；新增或调整售后单、验收单、退款单实体/服务：申请创建售后单，审核后创建/推进验收单，仓库验收提交后再由独立退款单事务完成支付退款与事件发布；`warehouseAccept` 不应同事务立即完成退款。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；退款申请、审核、仓库验收、退款完成可分别提交；仓库验收成功但退款执行失败时不回滚已提交验收状态。
- **影响范围**：payment 退款领域模型、Repository、Service、DTO 映射、退款查询状态流转。

### EPAY-005 payment 依赖 order entity 枚举

- **所属模块**：ecommerce-payment
- **设计要求**：`design-docs/02-系统架构.md` 第 3 章表“查询依赖”和“DTO：跨模块传输使用 DTO，不暴露 JPA Entity”；第 4 章 `OrderQueryService` 由 order 提供给 payment 查询订单。
- **当前状态**：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentValidator.java:5` 直接导入 `com.ecommerce.order.entity.OrderStatus`；`PaymentValidator.java:41-46` 用该枚举判断 CREATED/PAYING；`code/ecommerce-order/src/main/java/com/ecommerce/order/query/OrderDto.java:1-19,71-77` 的状态字段类型仍是 `OrderStatus`。
- **不一致描述**：跨模块 DTO 泄露 order entity 包枚举，payment 编译依赖 order 实体类型。
- **修复方案**：将状态契约从 order entity 包剥离；在 order 查询契约包提供 DTO 专用状态枚举或字符串状态字段（如 `OrderDto#getStatusCode()`）；`PaymentValidator` 只依赖 query/DTO 契约判断 CREATED/PAYING；移除 payment 对 `com.ecommerce.order.entity.OrderStatus` 的 import。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；检索 payment 模块不再 import `com.ecommerce.order.entity.*`；支付创建仍能拒绝非可支付订单。
- **影响范围**：order 查询 DTO 契约、payment 校验逻辑、其他使用 `OrderDto#getStatus()` 的模块。

### EPAY-006 payment 使用未冻结错误码

- **所属模块**：ecommerce-payment
- **设计要求**：`README.md` 第 7.1-7.2 节错误码冻结清单。
- **当前状态**：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentValidator.java:36-45` 使用 `ORDER_NOT_FOUND`、`ORDER_STATUS_INVALID`；`PaymentValidator.java:71-75` 使用 `PAYMENT_DUPLICATE`；`PaymentCallbackService.java:130-132` 使用 `PAYMENT_STATUS_CONFLICT`；`RefundService.java:69-71` 使用 `REFUND_NOT_ALLOWED`；`RefundService.java:105-107` 使用 `REFUND_STATUS_INVALID`；`InvoiceService.java:60-61` 使用 `NO_PAID_PAYMENT`。
- **不一致描述**：payment 暴露多个 README 未定义错误码。
- **修复方案**：资源不存在用 `RESOURCE_NOT_FOUND`；订单状态不可支付用 `ORDER_STATUS_CONFLICT`；重复请求或状态冲突用 `CONFLICT`；参数校验用 `VALIDATION_FAILED`；保留已冻结的 `PAYMENT_AMOUNT_MISMATCH`、`REFUND_WAITING_WAREHOUSE_ACCEPT`、`INVOICE_AMOUNT_EXCEEDED`。
- **修复后验证**：运行 `mvn -f code/pom.xml test`；支付重复、回调冲突、退款状态错误、无已支付记录开票等场景响应 code 属于 README 第 7 章；检索不再出现未冻结错误码。
- **影响范围**：payment 校验、回调、退款、发票异常响应、测试断言。

---

## ecommerce-app

### EA-001 app 直接查询 common Repository 并返回 Entity

- **所属模块**：ecommerce-app
- **设计要求**：`design-docs/02-系统架构.md` 第 1 章第 4 条“禁止跨模块直接注入对方 Repository 或直接查询对方表”；第 3 章表“数据访问：只能访问本模块拥有的表和 Repository”“查询依赖：跨模块查询必须通过 QueryService 接口”“DTO：跨模块传输使用 DTO，不暴露 JPA Entity”；`README.md` 第 6.8 节 `GET /api/v1/admin/events/failures`。
- **当前状态**：`code/ecommerce-app/src/main/java/com/ecommerce/app/controller/EventFailureAdminController.java:3-4` 直接 import `FailedEventRecord` 和 `FailedEventRecordRepository`；`EventFailureAdminController.java:19-22` 直接注入 common Repository；`EventFailureAdminController.java:30-37` 调用 `failedEventRecordRepository.findAll()` 并按 `eventType` 过滤；`code/ecommerce-common/src/main/java/com/ecommerce/common/event/FailedEventRecordRepository.java:9-10` 为 common Repository。
- **不一致描述**：app-bootstrap Controller 直接访问 common Repository，并直接返回 JPA Entity 列表，违反模块边界和 DTO 规则。
- **修复方案**：在 common 模块新增失败事件记录查询服务/本地查询接口，例如 `FailedEventRecordQueryService`；新增响应 DTO，如 `FailedEventRecordItem`；由 common 服务内部封装 `findAll()` 与 `eventType` 过滤；`EventFailureAdminController` 改为注入查询服务并返回 DTO；保持 `GET /api/v1/admin/events/failures` 的 URL、Method、成功状态码以及 `count`、`records` 顶层字段不变。
- **修复后验证**：运行 `mvn -f code/pom.xml test` 和 `mvn -f test-cases/pom.xml test`；接口无参和带 `eventType` 查询均返回 200；静态检查 app 不再 import/inject `FailedEventRecordRepository` 或 `FailedEventRecord`。
- **影响范围**：ecommerce-app 管理查询入口、ecommerce-common 事件失败记录查询封装、REST 响应 DTO。
