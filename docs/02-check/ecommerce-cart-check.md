# ecommerce-cart 模块设计一致性检查

## 检查结论

本次仅依据 `design-docs/02-系统架构.md`、`README.md` 第 6 节 API 基线与第 7 节错误码，以及 `code/pom.xml`、`code/ecommerce-cart/pom.xml`、`code/ecommerce-cart/src/main/java/` 下当前模块源码进行一致性检查；未修改任何源代码或配置代码，仅写入本报告。

结论：8 个指定维度均已覆盖。`ecommerce-cart` 模块主要 REST 路径、HTTP Method、成功状态、USER 角色保护、商品/库存校验、购物车 7 天 TTL、临时购物车主流程使用本地缓存等方面与设计基本一致；主要不一致集中在库存本地接口提供方/包归属、促销计算本地接口名称与提供方、购物车缓存 Key 格式、购物车临时明细落库模型、以及 README 未定义的业务错误码使用。

### 一致

1. 架构风格与模块边界
   - 设计要求：`design-docs/02-系统架构.md` §1 要求 ShopHub 采用模块化单体架构；§3 要求模块拥有自己的包边界、领域服务、Repository 和对外契约，禁止跨模块直接注入对方 Repository 或直接查询对方表。
   - 已确认：购物车主业务代码位于 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/` 包边界内，Controller、Service、DTO、Cache、Config、Entity、Repository 分层存在，见：
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/controller/CartController.java:29-32`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:32-55`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:16-32`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/cache/CartCacheManager.java:14-23`
   - 已确认：当前源码未发现直接注入其它模块 Repository 或直接查询其它模块表；跨模块商品/库存查询通过接口注入完成，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:25-31`。

2. 模块依赖方向
   - 设计要求：`design-docs/02-系统架构.md` §2 模块依赖图中 product、inventory 指向 cart，cart 指向 order；§4 要求 cart 使用 `ProductQueryService`、`InventoryQueryService`、`PromotionCalculationService` 等本地接口完成查询/计算类协作。
   - 已确认：父工程包含 `ecommerce-cart`，并位于 product、inventory、order 等模块同一模块化单体中，见 `code/pom.xml:13-25`。
   - 已确认：`ecommerce-cart` POM 声明依赖 `ecommerce-common`、`ecommerce-product`、`ecommerce-inventory`，未发现依赖 `ecommerce-order`，见 `code/ecommerce-cart/pom.xml:11-26`；这与购物车读取商品/库存、不直接依赖订单实现的方向总体一致。

3. 关键本地接口中已符合部分
   - 设计要求：`design-docs/02-系统架构.md` §4 要求 cart 使用 product 提供的 `ProductQueryService` 查询商品、SKU、上下架状态。
   - 已确认：购物车校验服务注入 `ProductQueryService`，使用 `getSkuForSale` 查询 SKU 可售信息，并以 DTO `SkuDto` 承载跨模块数据，见：
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:6-7`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:25-31`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:41-52`
   - 已确认：购物车添加、修改、预估前均会校验 SKU 与库存，见：
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:64-66`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:107-109`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:164-166`

4. 领域事件
   - 设计要求：`design-docs/02-系统架构.md` §5 列出的核心事件包括用户、订单、支付、物流、评价、退款事件；未列出由 cart 发布或监听的领域事件。
   - 已确认：当前 `ecommerce-cart` 源码未发现 event/listener 类，也未发现 `ApplicationEventPublisher` 或 `@EventListener` 使用；在设计未要求 cart 参与领域事件的前提下，未发现本模块领域事件不一致。

5. 事务边界
   - 设计要求：`design-docs/02-系统架构.md` §6 主要规定创建订单、支付确认、支付后动作、批量订单、退款流程事务边界；未发现针对 cart 添加、修改、删除、预估的专门事务边界要求。
   - 已确认：购物车主流程操作 Caffeine 缓存，不开启跨模块事务；商品/库存校验为查询类接口调用，未发现“一个模块的事务依赖非关键后置监听器成功”的实现，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:60-86`、`code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:152-213`。

6. 缓存设计中已符合部分
   - 设计要求：`design-docs/02-系统架构.md` §7 要求购物车缓存 TTL 为 7 天，所属模块为 cart；并要求购物车不得落库保存临时明细。
   - 已确认：模块提供 `CartCacheConfig` 创建 Caffeine 缓存，TTL 为 7 天，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/config/CartCacheConfig.java:23-45`。
   - 已确认：购物车主业务 `CartService` 通过 `CartCacheManager` 读写/删除购物车，见：
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:42-55`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:68-86`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:92-99`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:142-145`

7. 安全架构
   - 设计要求：`design-docs/02-系统架构.md` §8.2 用户侧接口需要 `Authorization: Bearer <token>`；README §6.4 购物车模块 6 个接口认证均为 USER。
   - 已确认：`CartController` 使用 `@PreAuthorize("hasRole('USER')")` 保护整个 Controller，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/controller/CartController.java:29-32`。
   - 已确认：当前用户 ID 从 Spring Security 上下文取得，符合用户侧接口依赖认证上下文的要求，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/controller/CartController.java:114-123`。
   - 设计文档 §8.1 JWT 签发、§8.3 ADMIN 角色、§8.4 支付回调签名、§8.5 黑盒测试管理接口均未发现 cart 模块专属实现要求。

8. REST API 路径、HTTP Method、成功状态
   - 设计要求：README §6.4 购物车模块要求：
     - `POST /api/v1/cart/items`，USER，201
     - `GET /api/v1/cart`，USER，200
     - `PUT /api/v1/cart/items/{skuId}`，USER，200
     - `DELETE /api/v1/cart/items/{skuId}`，USER，204
     - `DELETE /api/v1/cart`，USER，204
     - `POST /api/v1/cart/estimate`，USER，200
   - 已确认路径、HTTP Method、成功状态与代码一致：
     - Controller 基础路径 `/api/v1/cart`：`code/ecommerce-cart/src/main/java/com/ecommerce/cart/controller/CartController.java:29-31`
     - 添加购物车项：`@PostMapping("/items")` 且返回 `HttpStatus.CREATED`，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/controller/CartController.java:45-51`
     - 查询购物车：`@GetMapping` 且返回 200，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/controller/CartController.java:57-62`
     - 修改购物车项：`@PutMapping("/items/{skuId}")` 且返回 200，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/controller/CartController.java:68-74`
     - 删除单项：`@DeleteMapping("/items/{skuId}")` 且返回 204，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/controller/CartController.java:80-85`
     - 清空购物车：`@DeleteMapping` 且返回 204，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/controller/CartController.java:91-96`
     - 价格预估：`@PostMapping("/estimate")` 且返回 200，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/controller/CartController.java:102-107`
   - README §6.4 未列出购物车 Request/Response Body 字段明细；当前仅能确认代码 DTO 字段存在：`AddCartItemRequest` 包含 `skuId`、`quantity`，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/dto/AddCartItemRequest.java:11-15`；`UpdateCartItemRequest` 包含 `quantity`，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/dto/UpdateCartItemRequest.java:11-13`；`CartResponse` 包含 `items`、`totalItems`、`totalAmount`，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/dto/CartResponse.java:11-13`；`CartEstimateResponse` 包含 `itemTotal`、`shippingFee`、`packagingFee`、`discountAmount`、`pointsDeductionAmount`、`payableAmount`，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/dto/CartEstimateResponse.java:10-15`。
   - README §7 与本模块相关的已符合错误码：商品不可售使用 `PRODUCT_NOT_FOR_SALE`，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:46-49`；库存不足使用 `INVENTORY_NOT_ENOUGH`，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:63-69`；未认证主路径受 Spring Security 保护，Controller 解析 principal 失败时使用 `UNAUTHORIZED`，见 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/controller/CartController.java:114-122`。

### 不一致

1. 库存查询本地接口的提供方/包归属不符合设计
   - 代码定位：
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:5-8`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:25-31`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:62-70`
   - 设计要求定位：`design-docs/02-系统架构.md` §3 “跨模块查询必须通过 QueryService 接口”；§4 `InventoryQueryService` 提供方为 inventory，使用方为 product、cart、order，用途为查询库存摘要。
   - 具体不一致描述：`CartValidationService` 导入并注入的是 `com.ecommerce.product.query.InventoryQueryService`，而不是 inventory 模块提供的库存查询接口；库存摘要 DTO 也来自 `com.ecommerce.product.query.StockSummaryDto`。
   - 原因解析：购物车需要查询库存摘要时，设计要求通过 inventory 提供的 `InventoryQueryService`；当前接口类型归属在 product 包下，会把库存查询契约错误地绑定到 product 命名空间，削弱 inventory 作为库存摘要提供方的模块边界。

2. 购物车价格预估未按设计使用 promotion 提供的 `PromotionCalculationService`
   - 代码定位：
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:12-15`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:42-55`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:175-188`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:274-278`
   - 设计要求定位：`design-docs/02-系统架构.md` §4 `PromotionCalculationService` 提供方为 promotion，使用方为 order、cart，用途为计算优惠。
   - 具体不一致描述：购物车价格预估注入并调用的是 `com.ecommerce.common.integration.PromotionDiscountCalculator`，而非设计指定的 promotion 模块 `PromotionCalculationService`。
   - 原因解析：设计把优惠计算能力归属于 promotion 模块，并明确 cart 是使用方；当前代码把优惠计算接口放在 common integration 命名空间，接口名称、提供方边界均与设计表不一致，导致购物车到促销模块的关键本地接口契约未按设计落地。

3. 购物车缓存 Key 未按设计使用 `cart:{userId}` 格式
   - 代码定位：
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/cache/CartCacheManager.java:11-19`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/cache/CartCacheManager.java:31-38`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/cache/CartCacheManager.java:46-49`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/cache/CartCacheManager.java:57-59`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/config/CartCacheConfig.java:34-45`
   - 设计要求定位：`design-docs/02-系统架构.md` §7 缓存设计要求购物车缓存 Key 为 `cart:{userId}`，TTL 为 7 天，所属模块为 cart。
   - 具体不一致描述：当前缓存 Bean 类型为 `Cache<Long, CartData>`，`CartCacheManager` 直接以 `Long userId` 作为缓存 key；类注释也写明 `Key format: userId`，未体现 `cart:{userId}` 命名空间格式。
   - 原因解析：虽然 TTL 满足 7 天要求，但设计明确给出缓存 Key 格式。直接使用裸 `userId` 会丢失模块命名空间，若后续统一缓存管理或迁移到共享缓存，将无法满足 `cart:{userId}` 的键空间隔离契约。

4. 存在购物车临时明细落库模型，与“购物车不得落库保存临时明细”要求冲突
   - 代码定位：
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/entity/Cart.java:13-22`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/entity/CartItem.java:17-35`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/repository/CartRepository.java:12-16`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/repository/CartItemRepository.java:12-18`
   - 设计要求定位：`design-docs/02-系统架构.md` §7 “购物车不得落库保存临时明细。订单提交后，订单模块保存订单明细。”
   - 具体不一致描述：模块定义了 `cart`、`cart_item` 两个 JPA Entity 及对应 Repository，其中 `CartItem` 包含 `skuId`、`skuName`、`price`、`quantity` 等临时购物车明细字段。
   - 原因解析：当前主业务流虽使用 Caffeine 缓存，但 JPA Entity/Repository 的存在表明模块仍建模了可落库的购物车临时明细；这与设计中“购物车不得落库保存临时明细”的存储边界不一致，容易导致实现或后续扩展把临时购物车明细持久化到 cart 模块表。

5. 使用了 README §7 未定义的 cart 相关业务错误码
   - 代码定位：
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:79-83`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:93-98`
   - 设计要求定位：README §7.1 通用错误码仅列出 `VALIDATION_FAILED`、`RESOURCE_NOT_FOUND`、`UNAUTHORIZED`、`FORBIDDEN`、`CONFLICT`、`RATE_LIMITED`、`INTERNAL_ERROR`；README §7.2 业务错误码列出 `PRODUCT_NOT_FOR_SALE`、`INVENTORY_NOT_ENOUGH` 等固定业务错误码，未列出 `INVALID_QUANTITY`、`CART_FULL`。
   - 具体不一致描述：购物车数量范围校验失败时抛出 `BusinessException("INVALID_QUANTITY", ...)`；购物车条目数量超限时抛出 `BusinessException("CART_FULL", ...)`。这两个错误码均不在 README §7 冻结错误码表中。
   - 原因解析：README §7 是错误码基线；本模块新增未列出的业务错误码，会导致黑盒客户端无法按冻结错误码契约识别错误。数量校验类错误更适合映射到 README 已定义的 `VALIDATION_FAILED`，资源/状态冲突类错误也应使用已定义错误码集合。

## 检查遗漏声明

1. 架构风格（设计文档 §1、§3）：已检查 `controller/service/repository/entity/dto/config/cache` 等源码；未发现 query、event、listener 包或类。当前主流程未发现跨模块直接注入对方 Repository 或直接查询对方表；但存在购物车临时明细 JPA Entity/Repository 与缓存设计冲突，详见“不一致”第 4 项。
2. 模块依赖方向（设计文档 §2）：已检查 `code/pom.xml` 与 `code/ecommerce-cart/pom.xml`；`ecommerce-cart` 模块存在且依赖 common/product/inventory。未发现依赖 order；库存接口包归属不一致详见“不一致”第 1 项。
3. 关键本地接口（设计文档 §4）：已检查 `CartValidationService` 与 `CartService` 注入依赖。`ProductQueryService` 使用符合设计；`InventoryQueryService` 提供方/包归属不一致，`PromotionCalculationService` 未按设计使用，详见“不一致”第 1、2 项。未找到本模块自有 query 包。
4. 领域事件（设计文档 §5）：设计文档未发现 cart 作为发布方或监听方的本模块相关要求；代码中未找到 event/listener 类、`ApplicationEventPublisher` 或 `@EventListener`。
5. 事务边界（设计文档 §6）：设计文档未发现 cart 添加、修改、删除、预估的本模块专门事务边界要求；代码中未找到 `@Transactional`，主流程为缓存读写。
6. 缓存设计（设计文档 §7）：已检查 `cache` 与 `config` 包。TTL 7 天已实现；Key 格式与临时明细落库模型存在不一致，详见“不一致”第 3、4 项。
7. 安全架构（设计文档 §8）：已检查 `CartController`；USER 角色保护已实现。JWT 签发、ADMIN 管理接口、支付签名、测试管理接口设计文档未发现 cart 模块相关要求。
8. REST API 与错误码（README §6、§7）：已检查 6 个购物车 REST 端点，路径、Method、认证、成功状态一致；README §6.4 未提供购物车 Request/Response 字段明细，字段级契约无法与 README 做进一步逐字段比对。README §7 中 `PRODUCT_NOT_FOR_SALE`、`INVENTORY_NOT_ENOUGH`、`UNAUTHORIZED` 已见实现；`INVALID_QUANTITY`、`CART_FULL` 未在 README §7 定义，详见“不一致”第 5 项。
9. 配置文件：`code/ecommerce-cart/src/main/resources/` 目录未找到，因此本模块 resources 下配置文件未找到。
