# ecommerce-inventory 模块设计一致性检查

## 检查结论

本次仅依据 `design-docs/02-系统架构.md`、`README.md` 第 6 节 API 基线与第 7 节错误码、父/模块 `pom.xml`、`code/ecommerce-inventory/src/main/java/` 源码以及 `code/ecommerce-inventory/src/main/resources/` 配置进行核对。8 个指定维度均已覆盖。

### 一致

1. **架构风格与模块化单体基本形态**
   - 设计要求：`design-docs/02-系统架构.md` §1 要求模块化单体，模块拥有自己的包边界、领域服务、Repository 和对外契约；§3 要求跨模块传输使用 DTO、不暴露 JPA Entity。
   - 已确认：库存模块源码位于 `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/`，包含 `controller`、`service`、`repository`、`entity`、`dto`、`query` 等包；REST 对外返回使用 `StockSummaryResponse`、`InventoryCheckResponse`、`StockWarningResponse` 等 DTO（如 `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/controller/InventoryController.java:25-33`）。

2. **关键本地接口存在性**
   - 设计要求：`design-docs/02-系统架构.md` §4 要求 `InventoryQueryService` 由 inventory 提供，供 product、cart、order 查询库存摘要；`InventoryReservationService` 由 inventory 提供，供 order 预占、释放、扣减库存。
   - 已确认：
     - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/query/InventoryQueryService.java:14-42` 存在 `InventoryQueryService`。
     - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/query/InventoryReservationService.java:10-36` 存在 `InventoryReservationService`，包含 `reserve(Long orderId, List<ReserveItem> items)`、`release(Long orderId)`、`deductAfterPayment(Long orderId)`。
     - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java:23-121` 实现库存预占、释放、支付后扣减。

3. **领域事件**
   - 设计要求：`design-docs/02-系统架构.md` §5 的核心事件表未发现 ecommerce-inventory 作为发布方或监听方的明确要求。
   - 已确认：库存模块源码中未找到 `ApplicationEventPublisher`、`@EventListener`、`@TransactionalEventListener` 等事件发布/监听实现；这与 §5 未列出库存模块事件职责相符。

4. **事务边界中的库存本地操作**
   - 设计要求：`design-docs/02-系统架构.md` §6.1 创建订单事务包含库存预占记录；§6.2 支付确认事务包含库存扣减。
   - 已确认：
     - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java:37-76` 的 `reserve(...)` 使用 `@Transactional`，在同一事务内更新 `InventoryStock.reservedStock` 并保存 `StockReservation`。
     - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java:99-120` 的 `deductAfterPayment(...)` 使用 `@Transactional`，在同一事务内扣减 `onHandStock`、`reservedStock` 并将预占记录置为 `DEDUCTED`。
     - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java:78-97` 的 `release(...)` 使用 `@Transactional`，在同一事务内释放库存预占并将预占记录置为 `RELEASED`。

5. **REST API 路径、HTTP Method、成功状态码**
   - 设计要求：`README.md` §6.3 库存模块定义：
     - `POST /api/v1/admin/warehouses`，ADMIN，201
     - `POST /api/v1/admin/inventory/inbound`，ADMIN，201
     - `POST /api/v1/admin/inventory/outbound`，ADMIN，201
     - `GET /api/v1/inventory/sku/{skuId}`，匿名，200
     - `POST /api/v1/inventory/check`，匿名，200
     - `POST /api/v1/admin/inventory/adjustments`，ADMIN，201
     - `GET /api/v1/admin/inventory/warnings`，ADMIN，200
   - 已确认：上述路径和 HTTP Method 均存在于 `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/controller/AdminInventoryController.java:25-77` 与 `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/controller/InventoryController.java:15-33`；201 接口均使用 `@ResponseStatus(HttpStatus.CREATED)`。

6. **错误码中与库存相关的要求**
   - 设计要求：`README.md` §7.2 定义 `INVENTORY_NOT_ENOUGH`，HTTP 400，库存不足。
   - 已确认：库存不足场景使用 `BusinessException("INVENTORY_NOT_ENOUGH", ...)`：
     - 预占库存不足：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java:69-72`
     - 出库库存不足：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryService.java:166-168`

### 不一致

1. **模块依赖方向与模块依赖图不一致**
   - 代码定位：
     - `code/ecommerce-inventory/pom.xml:17-21`
     - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryService.java:15-17`
     - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryService.java:46-55`
   - 设计要求定位：`design-docs/02-系统架构.md` §2 模块依赖图中 `product` 指向 `inventory`；§3 要求跨模块查询必须通过 QueryService 接口。
   - 具体描述：库存模块 Maven 直接依赖 `ecommerce-product`，并在 `InventoryService` 中注入 `com.ecommerce.product.query.ProductQueryService`。
   - 原因解析：从 §4 看，`ProductQueryService` 的确是 product 提供、inventory 使用的关键本地接口；但 §2 依赖图表达的是 `product -> inventory`，而当前 Maven 依赖与代码注入方向是 `inventory -> product`。因此实现与 §2 模块依赖图方向不一致，同时暴露出设计文档 §2 与 §4 在 inventory/product 依赖方向上的约束冲突。

2. **库存模块包含 product 表对应 Entity/Repository，违反模块边界的数据访问要求**
   - 代码定位：
     - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/entity/Product.java:13-15`
     - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/repository/ProductRepository.java:10-12`
   - 设计要求定位：`design-docs/02-系统架构.md` §1 第 12 行要求禁止跨模块直接注入对方 Repository 或直接查询对方表；§3 数据访问要求只能访问本模块拥有的表和 Repository。
   - 具体描述：库存模块定义了 `@Entity @Table(name = "product")` 的 `Product` 实体，并定义 `ProductRepository extends JpaRepository<Product, Long>`。
   - 原因解析：`product` 表属于商品模块语义，库存模块应通过 `ProductQueryService` 获取商品/SKU 信息，而不应在本模块内声明商品表 Entity/Repository。即使当前未发现该 Repository 被业务服务注入使用，实体和 Repository 的存在仍与模块边界规则不一致。

3. **`InventoryQueryService` 返回类型泄露 product 模块 DTO，且未使用库存模块本地 DTO**
   - 代码定位：
     - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/query/InventoryQueryService.java:14-22`
     - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/query/StockSummaryDto.java:7-35`
     - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryService.java:60-67`
   - 设计要求定位：`design-docs/02-系统架构.md` §4 要求 `InventoryQueryService` 由 inventory 提供，用于查询库存摘要；§3 DTO 规则要求跨模块传输使用 DTO、不暴露 JPA Entity。
   - 具体描述：库存模块自己的 `InventoryQueryService#getStockSummary(Long skuId)` 返回 `com.ecommerce.product.query.StockSummaryDto`，而不是库存模块包内已有的 `com.ecommerce.inventory.query.StockSummaryDto`。
   - 原因解析：关键本地接口由 inventory 提供时，其契约 DTO 应属于提供方或公共契约包；当前返回 product 包 DTO，使 inventory 对外接口反向绑定到 product 模块类型，扩大了跨模块耦合，也与库存模块已定义但未使用的本地 `StockSummaryDto` 不一致。

4. **库存摘要缓存未实现指定 Key 与 TTL**
   - 代码定位：
     - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryService.java:60-67`
     - `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryService.java:93-119`
   - 设计要求定位：`design-docs/02-系统架构.md` §7 缓存设计要求库存摘要缓存 Key 为 `inventory:summary:{skuId}`，TTL 为 30 秒，所属模块为 inventory。
   - 具体描述：`getStockSummary(Long skuId)` 与 `getStockSummaryResponse(Long skuId)` 均直接查询 `InventoryStockRepository`；库存模块源码中未找到 `@Cacheable`、`@CacheEvict`、Redis 配置或显式 `inventory:summary:{skuId}` Key。
   - 原因解析：设计明确要求库存摘要缓存及 TTL；当前实现没有任何对应缓存声明或配置，因此无法满足指定 Key 格式与 30 秒 TTL。

5. **管理类库存接口未在模块内体现 ADMIN 角色认证约束**
   - 代码定位：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/controller/AdminInventoryController.java:25-77`
   - 设计要求定位：`design-docs/02-系统架构.md` §8.3 管理类接口需要 `ADMIN` 角色；`README.md` §6.3 将 `/api/v1/admin/warehouses`、`/api/v1/admin/inventory/inbound`、`/api/v1/admin/inventory/outbound`、`/api/v1/admin/inventory/adjustments`、`/api/v1/admin/inventory/warnings` 标记为 ADMIN。
   - 具体描述：`AdminInventoryController` 仅声明 `@RestController` 与 `@RequestMapping("/api/v1/admin")`，方法上无 `@PreAuthorize`、`@Secured` 或其他角色校验注解；库存模块源码中也未找到安全配置类。
   - 原因解析：如果项目通过其它模块的全局 SecurityFilterChain 按路径统一保护 `/api/v1/admin/**`，则该约束可能在模块外实现；但按本次必须检查的 ecommerce-inventory 模块范围，未找到本模块对 ADMIN 角色要求的实现证据。

6. **`POST /api/v1/admin/inventory/outbound` 的请求字段承载方式与冻结 URL 契约存在偏差**
   - 代码定位：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/controller/AdminInventoryController.java:56-62`
   - 设计要求定位：`README.md` §6 第 75 行要求 URL 路径、HTTP Method、Request Header、Request/Response Body 字段名和类型不得修改；§6.3 固定 URL 为 `POST /api/v1/admin/inventory/outbound`。
   - 具体描述：该接口未定义 `@RequestBody` DTO，而是要求 `warehouseId`、`skuId`、`quantity`、`orderId` 作为 `@RequestParam` 查询参数传入。
   - 原因解析：README 固定的是无查询占位的 REST URL，并强调 Request Body 字段名和类型为冻结契约；当前实现将业务字段放在 query param 中，导致调用方必须追加查询字符串，和“请求体字段冻结”的契约表达不一致。README §6.3 未列出具体字段表，因此字段名/类型只能按代码观察，无法进一步逐字段对照。

7. **`POST /api/v1/admin/inventory/adjustments` 的请求字段承载方式与冻结 URL 契约存在偏差**
   - 代码定位：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/controller/AdminInventoryController.java:65-71`
   - 设计要求定位：`README.md` §6 第 75 行要求 URL 路径、HTTP Method、Request Header、Request/Response Body 字段名和类型不得修改；§6.3 固定 URL 为 `POST /api/v1/admin/inventory/adjustments`。
   - 具体描述：该接口未定义 `@RequestBody` DTO，而是要求 `warehouseId`、`skuId`、`afterQty`、`reason` 作为 `@RequestParam` 查询参数传入。
   - 原因解析：同上，当前实现将业务字段放入 query param，和 README 对 Request Body 字段冻结的顶层约束不一致；README §6.3 未列出具体字段表，因此只能确认承载方式不一致，不能进一步确认字段类型是否符合冻结契约。

## 检查遗漏声明

1. **架构风格（§1、§3）**：已检查包边界、服务/Repository/DTO 结构、跨模块 Repository/表访问迹象。未找到 `config` 包。发现库存模块内存在 `Product` Entity 与 `ProductRepository`，已列为不一致。

2. **模块依赖方向（§2）**：已检查父工程模块列表与库存模块 POM。父工程包含 `ecommerce-inventory`（`code/pom.xml:13-25`）。发现 `ecommerce-inventory` 依赖 `ecommerce-product` 与 §2 图方向不一致，已列为不一致。

3. **关键本地接口（§4）**：已检查 `query` 包。`InventoryQueryService`、`InventoryReservationService` 均找到；`ProductQueryService` 使用方也找到。`InventoryQueryService` 返回 product 包 DTO 的签名问题已列为不一致。

4. **领域事件（§5）**：设计文档未发现 ecommerce-inventory 作为核心事件发布方或监听方的相关要求；代码中未找到 event/listener 目录，也未找到 `ApplicationEventPublisher`、`@EventListener`、`@TransactionalEventListener`。失败策略未找到本模块实现要求。

5. **事务边界（§6）**：已检查库存预占、释放、扣减、入库、出库、调整的 `@Transactional`。本模块能确认库存本地写操作在各自服务方法事务内；支付状态与订单支付状态是否和库存扣减处于同一个跨模块事务，需要检查 order/payment 模块，超出本次限定输入范围，未在本模块内找到可直接证明。

6. **缓存设计（§7）**：设计文档存在库存摘要缓存要求；代码中未找到 cache/config/cache 目录、缓存注解、Redis 使用或 `inventory:summary:{skuId}` Key。已列为不一致。

7. **安全架构（§8）**：JWT 签发属于登录/用户模块要求；`X-Payment-Signature` 属于支付回调要求，设计文档未发现 ecommerce-inventory 相关签名头要求。库存模块涉及 ADMIN 管理接口与匿名查询接口；本模块内未找到安全注解或配置，ADMIN 角色约束已列为不一致/缺少本模块证据。

8. **REST API 与错误码（README §6、§7）**：已检查 README §6.3 库存模块 7 个接口的路径、Method、成功状态码；均找到。README §6.3 未提供库存接口 Request/Response 字段明细表，无法按 README 逐字段验证字段名和类型，只能基于代码列出 DTO/参数承载方式。README §7 中与本模块直接相关的业务错误码仅发现 `INVENTORY_NOT_ENOUGH`，代码中已使用。

9. **资源配置文件**：`code/ecommerce-inventory/src/main/resources/` 下未找到配置文件，因此未发现库存模块本地 `application.yml`、缓存 TTL、安全或其它资源配置。
