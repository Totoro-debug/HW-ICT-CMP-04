# ecommerce-product 模块 03-通用规范与非功能设计检查报告

## 检查结论

### 一致

- Match：金额字段在 product 模块中均使用 `BigDecimal`，未在 `code/ecommerce-product/src/main/java/com/ecommerce/product/**/*.java` 中发现使用 `double` 或 `float` 表示商品价格/金额；例如 `ProductSku.price`、`ProductSku.marketPrice` 为 `BigDecimal`，且实体列定义为 `scale = 2`。
- Match：product 模块中已发现的资源不存在、参数校验、不可售业务异常分别使用 `ResourceNotFoundException`、`ValidationException`、`BusinessException`，未发现订单金额校验实现，也未发现 `OrderValidationException` 适用场景。
- Match：黑盒测试隔离方面，未在 product 模块业务代码中发现 `reset`、`bootstrap` 接口或相关依赖；已搜索范围为 `code/ecommerce-product/src/main/java/com/ecommerce/product/**/*.java`。
- Match：通知规范方面，未在 product 模块业务代码中发现直接调用 `MockMailSender` 或 `MockSmsSender`；也未发现 product 模块发起通知的实现。
- Match：幂等规范中创建订单、支付回调、退款申请、物流回调、发票申请等接口未在 product 模块中发现对应实现；不将这些非 product 职责项判定为不一致。
- Match：配置文件检查中，`code/ecommerce-product/src/main/resources/*.yml` 不存在；product 模块未提供单独 yml 配置。

### 不一致

- Mismatch：商品价格最终入库未显式执行 2 位小数与 `RoundingMode.HALF_UP` 规范化。
  - 代码定位：`code/ecommerce-product/src/main/java/com/ecommerce/product/service/SkuService.java:57`、`code/ecommerce-product/src/main/java/com/ecommerce/product/service/SkuService.java:58`；金额实体列见 `code/ecommerce-product/src/main/java/com/ecommerce/product/entity/ProductSku.java:28`、`code/ecommerce-product/src/main/java/com/ecommerce/product/entity/ProductSku.java:31`。
  - 设计要求定位：`03-通用规范与非功能设计.md §1 金额计算`。
  - 不一致的具体描述：`SkuService.createSku` 直接执行 `sku.setPrice(request.getPrice())` 与 `sku.setMarketPrice(request.getMarketPrice())`，未调用 `setScale(2, RoundingMode.HALF_UP)` 或等价金额规范化逻辑；`ProductSku` 虽然通过 `@Column(precision = 12, scale = 2)` 定义数据库列小数位，但业务层没有明确 HALF_UP 舍入规则。
  - 原因解析：设计要求“最终入库保留 2 位小数”且“舍入模式为 `RoundingMode.HALF_UP`”。当前实现依赖 JPA/数据库列 scale 的隐式处理，无法保证业务入库前按 HALF_UP 统一舍入，也无法在服务层形成一致的金额处理语义。

- Mismatch：商品价格允许 0，不满足应付/金额不得小于 0.01 的通用金额底线要求。
  - 代码定位：`code/ecommerce-product/src/main/java/com/ecommerce/product/dto/SkuCreateRequest.java:27`、`code/ecommerce-product/src/main/java/com/ecommerce/product/dto/SkuCreateRequest.java:28`、`code/ecommerce-product/src/main/java/com/ecommerce/product/dto/SkuCreateRequest.java:29`。
  - 设计要求定位：`03-通用规范与非功能设计.md §1 金额计算`。
  - 不一致的具体描述：`SkuCreateRequest.price` 使用 `@PositiveOrZero(message = "price must be non-negative")`，API 入参允许 `0`；未发现 `@DecimalMin(value = "0.01")` 或服务层等价校验。
  - 原因解析：设计明确 0 元订单不在本系统支持范围内，应付金额不得小于 0.01。product 模块的商品价格是订单金额计算基础，允许 SKU 价格为 0 会使后续订单应付金额可能不满足该底线。

- Mismatch：商品搜索接口未实现同一 IP 每分钟 120 次的本地限流，且未体现触发时返回 429、错误码 `RATE_LIMITED`。
  - 代码定位：`code/ecommerce-product/src/main/java/com/ecommerce/product/controller/ProductController.java:50`、`code/ecommerce-product/src/main/java/com/ecommerce/product/controller/ProductController.java:51`、`code/ecommerce-product/src/main/java/com/ecommerce/product/controller/ProductController.java:52`、`code/ecommerce-product/src/main/java/com/ecommerce/product/controller/ProductController.java:53`、`code/ecommerce-product/src/main/java/com/ecommerce/product/controller/ProductController.java:54`；搜索服务入口见 `code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java:52`、`code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java:53`。
  - 设计要求定位：`03-通用规范与非功能设计.md §4 本地限流 - 商品搜索接口`。
  - 不一致的具体描述：`ProductController.searchProducts(ProductSearchRequest request)` 仅调用 `productSearchService.search(request)`，方法签名未接收 IP、`HttpServletRequest` 或限流 key；`ProductSearchService.search` 内未发现本地计数、Caffeine 限流桶、`RateLimitException` 或 `RATE_LIMITED` 错误码处理。
  - 原因解析：设计要求商品搜索按“同一 IP 每分钟 120 次”限流，并在触发时返回 429 且错误码为 `RATE_LIMITED`。当前 product 模块仅实现搜索查询，没有任何按 IP 维度的限流判断与异常返回路径。

- Mismatch：商品上下架操作未记录符合字段要求的审计日志。
  - 代码定位：上架接口 `code/ecommerce-product/src/main/java/com/ecommerce/product/controller/AdminProductController.java:65`、`code/ecommerce-product/src/main/java/com/ecommerce/product/controller/AdminProductController.java:66`、`code/ecommerce-product/src/main/java/com/ecommerce/product/controller/AdminProductController.java:67`、`code/ecommerce-product/src/main/java/com/ecommerce/product/controller/AdminProductController.java:68`；下架接口 `code/ecommerce-product/src/main/java/com/ecommerce/product/controller/AdminProductController.java:75`、`code/ecommerce-product/src/main/java/com/ecommerce/product/controller/AdminProductController.java:76`、`code/ecommerce-product/src/main/java/com/ecommerce/product/controller/AdminProductController.java:77`、`code/ecommerce-product/src/main/java/com/ecommerce/product/controller/AdminProductController.java:78`；状态变更实现 `code/ecommerce-product/src/main/java/com/ecommerce/product/service/SkuService.java:83`、`code/ecommerce-product/src/main/java/com/ecommerce/product/service/SkuService.java:88`、`code/ecommerce-product/src/main/java/com/ecommerce/product/service/SkuService.java:91`、`code/ecommerce-product/src/main/java/com/ecommerce/product/service/SkuService.java:98`、`code/ecommerce-product/src/main/java/com/ecommerce/product/service/SkuService.java:103`、`code/ecommerce-product/src/main/java/com/ecommerce/product/service/SkuService.java:106`。
  - 设计要求定位：`03-通用规范与非功能设计.md §6 审计日志 - 商品上下架`。
  - 不一致的具体描述：`SkuService.onShelf` 与 `SkuService.offShelf` 只更新 `SkuStatus` 并输出普通 `log.info`，未发现审计日志服务、审计日志实体/表、审计记录保存调用，也未记录操作者、操作类型、业务 ID、操作前状态、操作后状态、操作时间、备注等完整字段。控制器 `AdminProductController` 方法签名仅接收 `skuId`，没有显式接收或解析操作者与备注。
  - 原因解析：设计要求商品上下架必须记录审计日志，且字段至少包含七项。普通应用日志不具备结构化审计字段、持久化查询能力和完整前后状态记录，因此不符合审计日志规范。

## 检查遗漏声明

- 金额计算：已检查 `code/ecommerce-product/src/main/java/com/ecommerce/product/**/*.java` 中商品价格相关字段与创建 SKU 流程；未找到优惠金额字段或优惠金额计算实现，因此“优惠金额不得小于 0、不得大于商品金额”在 product 模块无对应实现可判定。
- 通用异常：未找到 product 模块内订单金额校验实现；已搜索 `IllegalArgumentException` 与 `OrderValidationException`，均未命中，因此“订单金额校验失败必须抛 `OrderValidationException`”在 product 模块无对应实现，不作为不一致项。
- 通用异常：未找到 product 模块显式抛出 `AuthorizationException`、`ConflictException`、`RateLimitException` 的实现；其中限流相关缺失已在“不一致”中按商品搜索限流单独列出。认证/授权目前主要通过 `AdminProductController` 上的 `@PreAuthorize("hasRole('ADMIN')")` 声明，product 模块内未实现 401/403 异常映射。
- 幂等规范：未找到创建订单 `externalOrderNo`、支付回调 `paymentNo + callbackSequence`、退款 `refundRequestNo`、物流 `trackingNo + eventTime + status`、发票 `invoiceRequestNo` 等接口或字段；已搜索范围为 `code/ecommerce-product/src/main/java/com/ecommerce/product/**/*.java`。这些接口不属于当前 product 模块已发现职责，不默认判定为一致实现。
- 本地限流：未找到登录接口、支付回调接口、创建订单接口；商品搜索接口已找到但缺少同一 IP 每分钟 120 次限流，已列为不一致。
- 黑盒测试隔离：未找到 `reset` 或 `bootstrap` 业务接口；已搜索范围为 `code/ecommerce-product/src/main/java/com/ecommerce/product/**/*.java`。
- 审计日志：仅找到商品上下架相关实现；未找到库存人工调整、订单取消审核、退款审核和仓库验收、发票开具、结算批次生成等实现，这些不属于当前 product 模块已发现职责。商品上下架审计日志缺失已列为不一致。
- 通知规范：未找到 product 模块创建 `NotificationRequest` 或调用 `LocalNotificationService` 的通知实现；同时未找到直接调用 `MockMailSender`、`MockSmsSender`。
- 本地事件失败处理：未找到 product 模块事件监听器、`@EventListener`、`TransactionalEventListener`、本地事件处理表或失败记录保存实现；已搜索范围为 `code/ecommerce-product/src/main/java/com/ecommerce/product/**/*.java`。支付成功后的物流、积分、通知监听器未在 product 模块发现，不作为 product 模块不一致项。
- 配置文件：`code/ecommerce-product/src/main/resources/*.yml` 不存在，未检查到 product 模块独立 yml 配置。
