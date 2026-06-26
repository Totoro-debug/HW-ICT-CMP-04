# M3 product-service 一致性审查报告

审查模块：`code/ecommerce-product/`  
包名：`com.ecommerce.product`

## 发现的不一致

### 1. 公开商品列表/搜索默认返回未上架商品

1. 实现位置：
   - `code/ecommerce-product/src/main/java/com/ecommerce/product/dto/ProductSearchRequest.java:9-11`、`code/ecommerce-product/src/main/java/com/ecommerce/product/dto/ProductSearchRequest.java:26-31`
   - `code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java:30-32`、`code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java:51-60`、`code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java:96-102`
   - `code/ecommerce-product/src/main/java/com/ecommerce/product/controller/ProductController.java:40-54`
2. 设计依据：
   - `README.md:100-101`：`GET /api/v1/products` 与 `GET /api/v1/products/search` 是匿名公开商品接口，成功状态 200。
   - `design-docs/01-项目概述.md:38`：M3 product-service 职责包含“上下架、搜索”。
   - `design-docs/01-项目概述.md:51`：下单前必须校验商品状态。
3. 不一致内容：
   - `ProductSearchRequest.onlyOnShelf` 默认值为 `false`，代码注释明确说明默认会包含 `OFF_SHELF` 和 `DRAFT`。
   - `ProductSearchService.buildSpecification()` 在 `onlyOnShelf=false` 时只排除 `DELETED`，会把 `DRAFT`、`OFF_SHELF` 一并返回。
   - 两个匿名公开接口 `GET /api/v1/products` 与 `GET /api/v1/products/search` 直接使用该默认请求对象，未强制只返回 `ON_SHELF` 商品。
4. 原因分析与影响：
   - 上下架状态是 product-service 的职责，公开浏览/搜索接口默认暴露未上架或草稿 SKU，与“商品状态校验”和“上下架”职责下的可售状态边界不一致。
   - 影响是匿名用户和依赖公开搜索结果的前端/调用方可能看到并选择不可售商品，后续下单前状态校验才失败，形成公开商品展示与可售能力不一致。

### 2. 商品不可销售错误码未使用冻结契约中的 `PRODUCT_NOT_FOR_SALE`

1. 实现位置：
   - `code/ecommerce-product/src/main/java/com/ecommerce/product/query/ProductQueryService.java:20-27`
   - `code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductQueryServiceImpl.java:56-63`
2. 设计依据：
   - `README.md:200-212`：通用错误码列表。
   - `README.md:216-221`：业务错误码定义 `PRODUCT_NOT_FOR_SALE`，HTTP 400，说明为“商品不可销售”。
   - `design-docs/01-项目概述.md:51`：下单前必须校验商品状态。
   - `design-docs/01-项目概述.md:86-94`：错误响应统一返回 `code`、`message`、`traceId`、`details`。
3. 不一致内容：
   - `ProductQueryService.getSkuForSale()` 是 product-service 对外暴露的可售 SKU 查询/校验接口；实现中当 SKU 状态不是 `ON_SHELF` 时抛出 `BusinessException("SKU_NOT_AVAILABLE", ...)`。
   - `SKU_NOT_AVAILABLE` 不在 README 冻结错误码中；与商品不可销售场景指定的 `PRODUCT_NOT_FOR_SALE` 不一致。
4. 原因分析与影响：
   - 文档已明确商品不可销售应使用 `PRODUCT_NOT_FOR_SALE`，product-service 当前实现使用了未登记错误码。
   - 影响是调用方或黑盒用例按冻结契约校验错误响应 `code` 时会收到非契约错误码，破坏统一错误响应语义，也会影响订单前商品状态校验失败场景的错误归类。
