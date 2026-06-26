# cart-service 一致性审查报告

审查模块：M5 cart-service（`code/ecommerce-cart/`，包名 `com.ecommerce.cart`）

## 发现的不一致

### 1. 购物车主流程使用 JPA/H2 持久化，而非 Caffeine 临时缓存 7 天 TTL

1. 实现位置：
   - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:33-35`：类注释明确说明使用 JPA entities/repositories 将购物车持久化到 H2。
   - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:45-49`：主服务依赖 `CartRepository`、`CartItemRepository`。
   - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:126-129`：查询购物车时从 Repository 读取购物车和购物车项。
   - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/repository/CartRepository.java:12-15`、`code/ecommerce-cart/src/main/java/com/ecommerce/cart/repository/CartItemRepository.java:12-18`：购物车和购物车项 Repository 均为 JPA Repository。
2. 设计依据：
   - `design-docs/01-项目概述.md:40`：M5 职责包含“TTL”。
   - `design-docs/01-项目概述.md:52`：购物车是临时数据，存储在 Caffeine 本地缓存中，TTL 为 7 天。
   - `design-docs/01-项目概述.md:101-102`：运行模式要求启用本地缓存，测试模式需要干净缓存状态。
3. 不一致内容：
   - 模块虽然存在 `CartCacheConfig` 和 `CartCacheManager`，但 `CartService` 的增删改查主流程使用数据库 Repository 持久化购物车数据，未使用 Caffeine 缓存作为购物车存储。
4. 原因分析与影响：
   - 数据库持久化不符合“购物车是临时数据”的设计定位；`CartCacheConfig` 中 7 天 TTL 只作用于未接入主流程的缓存组件，实际购物车数据不会按 Caffeine TTL 自动过期。测试或运行中的购物车状态也可能跨预期生命周期保留，偏离文档要求的本地缓存/TTL 运行语义。

### 2. 商品不可销售、库存不足错误码未使用 README 冻结业务错误码

1. 实现位置：
   - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:46-49`：SKU 非上架时抛出 `SKU_NOT_AVAILABLE`。
   - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:62-69`：库存不足时抛出 `INSUFFICIENT_STOCK`。
2. 设计依据：
   - `README.md:200-212`：错误响应使用统一错误码集合。
   - `README.md:220-221`：商品不可销售错误码为 `PRODUCT_NOT_FOR_SALE`，库存不足错误码为 `INVENTORY_NOT_ENOUGH`。
   - `README.md:75`：冻结 API 包含错误响应结构。
3. 不一致内容：
   - 购物车商品有效性预校验中使用了未在 README 相关业务错误码中登记的 `SKU_NOT_AVAILABLE` 和 `INSUFFICIENT_STOCK`，而不是文档明确列出的 `PRODUCT_NOT_FOR_SALE`、`INVENTORY_NOT_ENOUGH`。
4. 原因分析与影响：
   - 购物车场景的商品不可销售和库存不足属于 README 已冻结的相关业务错误边界。使用未登记错误码会导致客户端和黑盒用例无法按冻结契约识别错误类型，破坏错误响应语义一致性。

### 3. 价格预估未校验促销有效性，且忽略请求中的优惠券/积分参数

1. 实现位置：
   - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/dto/CartEstimateRequest.java:11-14`：估价请求包含 `couponIds` 与 `redeemPoints`。
   - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:189-190`：注释说明促销计算服务尚未集成，折扣和积分抵扣返回 0。
   - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:230-232`：`discountAmount` 与 `pointsDeductionAmount` 固定为 `BigDecimal.ZERO`。
2. 设计依据：
   - `design-docs/01-项目概述.md:40`：M5 职责包含价格预估、商品有效性预校验。
   - `design-docs/01-项目概述.md:51`：商品下单前必须校验促销有效性。
   - `README.md:226`：相关业务错误码包含 `COUPON_EXPIRED`。
3. 不一致内容：
   - `/api/v1/cart/estimate` 接收优惠券和积分抵扣参数，但实现未基于这些参数校验促销/优惠券有效性，也未进行对应折扣或积分抵扣估算。
4. 原因分析与影响：
   - 价格预估接口无法提供文档要求的下单前促销有效性预校验能力，且返回金额可能与实际下单应付金额不一致；过期或无效优惠券也无法在购物车估价阶段按冻结错误码边界暴露。

### 4. 价格预估阶段未重新校验购物车商品库存可用性

1. 实现位置：
   - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:193-221`：估价流程读取购物车并用当前 SKU 价格计算商品总额，仅处理 SKU 不可用为空的情况。
   - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:45-49`：服务持有 `InventoryQueryService`，但估价方法未使用它校验库存。
2. 设计依据：
   - `design-docs/01-项目概述.md:40`：M5 职责包含商品有效性预校验。
   - `design-docs/01-项目概述.md:51`：商品下单前必须校验库存可用性。
   - `README.md:221`：库存不足错误码为 `INVENTORY_NOT_ENOUGH`。
3. 不一致内容：
   - 购物车估价接口未在下单前预估/预校验阶段对购物车内每个 SKU 的当前库存可用性进行校验。
4. 原因分析与影响：
   - 加入购物车后的库存可能变化，仅在新增或修改数量时校验库存不能覆盖“下单前”预校验要求。估价结果可能对缺货商品仍返回可支付金额，导致后续下单阶段才失败，削弱 cart-service 的预校验职责。
