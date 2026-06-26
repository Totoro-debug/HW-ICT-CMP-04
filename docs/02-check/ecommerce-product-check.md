# ecommerce-product 模块设计一致性检查

## 检查结论

本次仅依据 `design-docs/02-系统架构.md`、`README.md` 第 6 节 API 基线与第 7 节错误码，以及 `code/pom.xml`、`code/ecommerce-product/pom.xml`、`code/ecommerce-product/src/main/java/` 下当前模块源码进行一致性检查；未修改任何源代码或配置代码。

结论：8 个指定维度均已覆盖。`ecommerce-product` 模块整体符合模块化单体、包边界、本模块 Repository 使用、商品 REST 路径与 ADMIN 角色保护等要求；主要不一致集中在跨模块库存查询接口使用、商品详情缓存缺失、商品不可售错误码映射不足，以及 API 返回体暴露 JPA Entity。

### 一致

1. 架构风格与模块边界
   - 设计要求：`design-docs/02-系统架构.md` §1 要求模块化单体，各模块拥有自己的包边界、领域服务、Repository 和对外契约；§3 要求只能访问本模块拥有的表和 Repository。
   - 已确认：模块源码位于 `code/ecommerce-product/src/main/java/com/ecommerce/product/` 包边界内；实体表均为商品域表，如 `product_sku`、`product_spu`、`product_category`、`product_brand`、`product_attribute_template`、`product_tag`，见：
     - `code/ecommerce-product/src/main/java/com/ecommerce/product/entity/ProductSku.java:15-17`
     - `code/ecommerce-product/src/main/java/com/ecommerce/product/entity/ProductSpu.java:12-14`
     - `code/ecommerce-product/src/main/java/com/ecommerce/product/entity/Category.java:11-13`
     - `code/ecommerce-product/src/main/java/com/ecommerce/product/entity/Brand.java:11-13`
   - 已确认：Repository 均在 `com.ecommerce.product.repository` 下且只绑定商品模块实体，见：
     - `code/ecommerce-product/src/main/java/com/ecommerce/product/repository/ProductSkuRepository.java:13-24`
     - `code/ecommerce-product/src/main/java/com/ecommerce/product/repository/ProductSpuRepository.java:9-13`
     - `code/ecommerce-product/src/main/java/com/ecommerce/product/repository/CategoryRepository.java:9-17`

2. 模块依赖方向
   - 设计要求：`design-docs/02-系统架构.md` §2 中 product 位于 app-bootstrap 下，并向 inventory 有依赖方向；§3 禁止跨模块直接注入对方 Repository 或直接查询对方表。
   - 已确认：父工程包含 `ecommerce-product` 与 `ecommerce-inventory` 等模块，见 `code/pom.xml:13-25`。
   - 已确认：`ecommerce-product` POM 只声明 `ecommerce-common` 以及 Spring 依赖，未声明其它业务模块依赖，见 `code/ecommerce-product/pom.xml:11-35`；当前源码未发现直接注入其它模块 Repository 或直接查询其它模块表。

3. 关键本地接口：ProductQueryService
   - 设计要求：`design-docs/02-系统架构.md` §4 要求 `ProductQueryService` 由 product 提供，供 inventory、cart、order 查询商品、SKU、上下架状态。
   - 已确认：存在接口 `com.ecommerce.product.query.ProductQueryService`，见 `code/ecommerce-product/src/main/java/com/ecommerce/product/query/ProductQueryService.java:13-39`。
   - 已确认：存在实现 `com.ecommerce.product.service.ProductQueryServiceImpl`，以 `@Service` 暴露，见 `code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductQueryServiceImpl.java:31-32`。
   - 已确认接口方法覆盖商品/SKU/上下架状态查询用途：
     - `SkuDto getSku(Long skuId)`：`ProductQueryService.java:18`
     - `SkuDto getSkuForSale(Long skuId)`：`ProductQueryService.java:27`
     - `List<SkuDto> listSkuByIds(Collection<Long> skuIds)`：`ProductQueryService.java:32`
     - `ProductSnapshotDto getProductSnapshot(Long skuId)`：`ProductQueryService.java:38`
   - 已确认跨模块传输使用 DTO，不暴露 JPA Entity：`SkuDto`、`ProductSnapshotDto` 位于 query 包，见 `code/ecommerce-product/src/main/java/com/ecommerce/product/query/SkuDto.java:10-18`、`code/ecommerce-product/src/main/java/com/ecommerce/product/query/ProductSnapshotDto.java:11-17`。

4. 事务边界
   - 设计要求：`design-docs/02-系统架构.md` §6 未发现针对 product 模块创建/上架/查询的专门事务边界要求；§3 仅要求“不允许一个模块的事务依赖非关键后置监听器成功”。
   - 已确认：本模块写操作使用本地事务：
     - 创建 SKU 在单个 `@Transactional` 内保存 SKU，见 `code/ecommerce-product/src/main/java/com/ecommerce/product/service/SkuService.java:40-72`。
     - SKU 上架在单个 `@Transactional` 内更新状态，见 `code/ecommerce-product/src/main/java/com/ecommerce/product/service/SkuService.java:78-86`。
     - SKU 下架在单个 `@Transactional` 内更新状态，见 `code/ecommerce-product/src/main/java/com/ecommerce/product/service/SkuService.java:92-100`。
     - 创建 SPU 在单个 `@Transactional` 内保存 SPU，见 `code/ecommerce-product/src/main/java/com/ecommerce/product/service/SpuService.java:34-60`。
   - 已确认：查询类服务使用只读事务，见 `ProductDetailService.java:58-99`、`ProductSearchService.java:52-82`、`ProductQueryServiceImpl.java:45-91`。

5. 安全架构
   - 设计要求：`design-docs/02-系统架构.md` §8.2 用户侧接口需要 `Authorization: Bearer <token>`，§8.3 管理类接口需要 `ADMIN` 角色；README §6.2 商品模块要求管理商品接口为 ADMIN，公开商品浏览接口为匿名。
   - 已确认：管理商品 Controller 使用 `@PreAuthorize("hasRole('ADMIN')")`，见 `code/ecommerce-product/src/main/java/com/ecommerce/product/controller/AdminProductController.java:25-28`。
   - 已确认：公开商品与分类接口未声明角色限制，路径分别为 `/api/v1/products`、`/api/v1/categories`，见 `code/ecommerce-product/src/main/java/com/ecommerce/product/controller/ProductController.java:21-23`、`code/ecommerce-product/src/main/java/com/ecommerce/product/controller/CategoryController.java:17-19`，与 README §6.2 匿名访问要求一致。
   - 设计要求中 JWT 签发与 `X-Payment-Signature` 支付签名头不属于 product 模块职责；本模块未发现相关实现要求。

6. REST API 路径、HTTP Method、成功状态
   - 设计要求：README §6.2 商品模块要求：
     - `POST /api/v1/admin/products/spu`，ADMIN，201
     - `POST /api/v1/admin/products/sku`，ADMIN，201
     - `POST /api/v1/admin/products/sku/{skuId}/on-shelf`，ADMIN，200
     - `POST /api/v1/admin/products/sku/{skuId}/off-shelf`，ADMIN，200
     - `GET /api/v1/products`，匿名，200
     - `GET /api/v1/products/search`，匿名，200
     - `GET /api/v1/products/{skuId}`，匿名，200
     - `GET /api/v1/categories/tree`，匿名，200
   - 已确认路径、HTTP Method、成功状态与代码一致：
     - `AdminProductController` 基础路径 `/api/v1/admin/products`：`code/ecommerce-product/src/main/java/com/ecommerce/product/controller/AdminProductController.java:25-27`
     - 创建 SPU：`@PostMapping("/spu")` 且返回 `HttpStatus.CREATED`，见 `AdminProductController.java:43-47`
     - 创建 SKU：`@PostMapping("/sku")` 且返回 `HttpStatus.CREATED`，见 `AdminProductController.java:53-57`
     - 上架：`@PostMapping("/sku/{skuId}/on-shelf")` 且返回 200，见 `AdminProductController.java:63-67`
     - 下架：`@PostMapping("/sku/{skuId}/off-shelf")` 且返回 200，见 `AdminProductController.java:73-77`
     - 商品列表：`GET /api/v1/products`，见 `ProductController.java:21-44`
     - 商品搜索：`GET /api/v1/products/search`，见 `ProductController.java:50-54`
     - 商品详情：`GET /api/v1/products/{skuId}`，见 `ProductController.java:60-64`
     - 分类树：`GET /api/v1/categories/tree`，见 `CategoryController.java:17-36`

7. README §7 错误码中与本模块相关的已符合项
   - 设计要求：README §7.1 包含通用 `RESOURCE_NOT_FOUND`、`VALIDATION_FAILED` 等错误码；§7.2 包含 product 相关业务错误码 `PRODUCT_NOT_FOR_SALE`。
   - 已确认：商品不存在场景使用 `ResourceNotFoundException`，见 `code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductDetailService.java:60-64`、`code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductQueryServiceImpl.java:57-58`。
   - 已确认：创建 SKU/SPU 重复编码、非法状态等校验使用 `ValidationException`，见 `code/ecommerce-product/src/main/java/com/ecommerce/product/service/SkuService.java:46-48`、`SkuService.java:80-83`、`code/ecommerce-product/src/main/java/com/ecommerce/product/service/SpuService.java:36-38`。

### 不一致

1. Product 依赖 Inventory 的本地接口未按设计接入，商品详情库存摘要被本地硬编码替代
   - 代码定位：
     - `code/ecommerce-product/src/main/java/com/ecommerce/product/query/InventoryQueryService.java:1-19`
     - `code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductDetailService.java:39-52`
     - `code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductDetailService.java:73`
     - `code/ecommerce-product/src/main/java/com/ecommerce/product/service/StockInfoFetcher.java:8-25`
   - 设计要求定位：`design-docs/02-系统架构.md` §2 模块依赖图 product 指向 inventory；§3 “跨模块查询必须通过 QueryService 接口”；§4 `InventoryQueryService` 提供方为 inventory、使用方为 product，用途为查询库存摘要。
   - 具体描述：代码虽然定义了 `com.ecommerce.product.query.InventoryQueryService#getStockSummary(Long skuId)`，但 `ProductDetailService` 注入的是 `StockInfoFetcher` 而不是 `InventoryQueryService`；`StockInfoFetcher.fetch(Long skuId)` 直接返回 `new StockSummaryDto(999, 0)`。
   - 原因解析：商品模块需要库存摘要时，应通过 inventory 提供的 `InventoryQueryService` 查询；当前实现把接口定义放在 product 包内且未使用，并以本地硬编码库存摘要替代跨模块 QueryService，导致模块依赖方向和查询依赖规则未落到实现上。

2. 商品详情缓存未实现
   - 代码定位：
     - `code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductDetailService.java:58-99`
     - `code/ecommerce-product/src/main/java/com/ecommerce/product/controller/ProductController.java:60-64`
   - 设计要求定位：`design-docs/02-系统架构.md` §7 缓存设计要求商品详情缓存 Key 为 `product:detail:{skuId}`，TTL 为 10 分钟，所属模块为 product。
   - 具体描述：当前模块源码中未找到 `@Cacheable`、`CacheManager`、`CacheConfig`、Redis/Caffeine 配置或字符串 `product:detail:{skuId}`；`getProductDetail(Long skuId)` 每次直接查询 Repository 并组装响应。
   - 原因解析：设计明确规定 product 模块拥有商品详情缓存，并指定 Key 格式与 TTL；当前实现缺少缓存注解/配置/Key 生成策略/TTL 配置，无法满足商品详情缓存契约。

3. 管理创建接口 Response 暴露 JPA Entity，不符合跨模块/REST DTO 边界要求
   - 代码定位：
     - `code/ecommerce-product/src/main/java/com/ecommerce/product/controller/AdminProductController.java:44-47`
     - `code/ecommerce-product/src/main/java/com/ecommerce/product/controller/AdminProductController.java:54-57`
   - 设计要求定位：`design-docs/02-系统架构.md` §3 DTO 规则要求“跨模块传输使用 DTO，不暴露 JPA Entity”；README §6 要求冻结 Request/Response Body 字段名和类型。
   - 具体描述：`createSpu` 返回类型为 `ResponseEntity<ProductSpu>`，`createSku` 返回类型为 `ResponseEntity<ProductSku>`；二者直接把 JPA Entity 作为 REST Response Body。
   - 原因解析：REST API 是外部客户端访问契约，响应体字段名和类型属于冻结契约；直接暴露 Entity 会把持久化模型字段、继承字段和未来 Entity 变更泄露给 API 契约，不符合 DTO 边界规则。

4. `PRODUCT_NOT_FOR_SALE` 错误码仅在本地查询接口中处理，公开商品详情未按该业务错误码拦截不可售 SKU
   - 代码定位：
     - `code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductQueryServiceImpl.java:55-65`
     - `code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductDetailService.java:58-73`
   - 设计要求定位：README §7.2 业务错误码 `PRODUCT_NOT_FOR_SALE`，HTTP 400，说明为“商品不可销售”；README §6.2 商品详情 `GET /api/v1/products/{skuId}` 属于商品模块公开 API。
   - 具体描述：`ProductQueryServiceImpl#getSkuForSale(Long skuId)` 在状态非 `ON_SHELF` 时抛出 `BusinessException("PRODUCT_NOT_FOR_SALE", ...)`；但公开商品详情 `ProductDetailService#getProductDetail(Long skuId)` 只按 id 查询并返回状态，没有对非 `ON_SHELF` SKU 映射 `PRODUCT_NOT_FOR_SALE`。
   - 原因解析：README 将 `PRODUCT_NOT_FOR_SALE` 定义为商品相关业务错误码；当前该错误码只覆盖跨模块“可售 SKU”查询，不覆盖公开商品详情 API 中的不可售访问场景，错误码设计要求在商品模块 REST 行为上实现不足。

## 检查遗漏声明

1. 架构风格：已检查 `design-docs/02-系统架构.md` §1、§3；未发现 product 模块跨模块直接注入对方 Repository 或直接查询对方表。
2. 模块依赖方向：已检查 `design-docs/02-系统架构.md` §2 及 Maven 关系；发现 product 到 inventory 的库存摘要查询未按 `InventoryQueryService` 接入，详见“不一致”第 1 项。
3. 关键本地接口：
   - 已找到 product 提供的 `ProductQueryService` 及实现。
   - 已找到名为 `InventoryQueryService` 的接口，但包位置在 `com.ecommerce.product.query`，且未找到 product 模块对该接口的注入使用；按设计其提供方应为 inventory、使用方为 product。
   - 设计文档未发现 product 模块需要提供其它关键本地接口。
4. 领域事件：`design-docs/02-系统架构.md` §5 核心事件表未发现由 product 发布或由 product 监听的事件要求；代码中也未找到 event/listener 目录、`ApplicationEvent`、`@EventListener`、`@TransactionalEventListener` 或 `publishEvent`。因此本模块领域事件发布方、监听方、失败策略均为“设计文档未发现本模块相关要求；代码未找到”。
5. 事务边界：`design-docs/02-系统架构.md` §6 未发现 product 模块专属事务边界要求；已检查现有创建/上架/下架/查询事务注解，未发现与 §6 支付、订单、退款等事务要求直接相关的 product 实现。
6. 缓存设计：`design-docs/02-系统架构.md` §7 明确存在 product 模块商品详情缓存要求；代码未找到对应缓存实现，详见“不一致”第 2 项。
7. 安全架构：已检查 `design-docs/02-系统架构.md` §8；product 管理接口存在 ADMIN 角色控制，公开接口保持匿名。JWT 签发与支付签名头不是 product 模块职责，设计文档未发现本模块相关实现要求。
8. REST API 与错误码：已检查 README §6.2 商品 API 路径、Method、认证、成功状态；路径/Method/状态一致。README §6 未提供商品模块各接口的详细 Request/Response 字段表，因此除源码中可见 DTO/Entity 返回类型外，无法进一步逐字段核对冻结字段名和类型；已对 README §7 中 product 相关 `PRODUCT_NOT_FOR_SALE` 进行核对并发现覆盖不足。 
9. 配置资源目录：`code/ecommerce-product/src/main/resources/` 不存在，未找到本模块独立配置文件。
10. 代码目录存在性：当前模块源码下未发现 config、cache、event、listener 子目录；controller/service/repository/entity/dto/query 目录存在并已检查。
