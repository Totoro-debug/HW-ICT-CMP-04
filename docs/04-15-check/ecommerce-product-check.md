# 电商商品服务一致性检查报告

## 检查范围

- 设计文档：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/05-商品服务设计.md`
- 商品服务源码：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/src/main/java/com/ecommerce/product/`
- 商品服务 POM：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/pom.xml`
- 父级 Maven 配置：`D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`
- 引用模块：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory`

## 不一致点数量

5

## 不一致点

### 1. SKU 状态机未完整覆盖 DELETED 转换，且上下架转换约束过宽

- 代码定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/src/main/java/com/ecommerce/product/entity/SkuStatus.java:8`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/src/main/java/com/ecommerce/product/service/SkuService.java:108`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/src/main/java/com/ecommerce/product/service/SkuService.java:125`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/05-商品服务设计.md:18`
- 不一致原因：设计要求商品 SKU 状态包含 `DRAFT`、`ON_SHELF`、`OFF_SHELF`、`DELETED` 并作为状态机管理；当前代码只提供创建草稿、上架、下架能力，没有删除/转为 `DELETED` 的服务方法或 REST 入口，同时 `onShelf`/`offShelf` 只禁止 `DELETED`，没有限制必须按状态机流转。
- 详细解析：`SkuStatus` 枚举本身声明了四个状态，但 `SkuService.createSku` 只设置初始 `DRAFT`，`onShelf` 可将任意非 `DELETED` 状态改为 `ON_SHELF`，`offShelf` 可将任意非 `DELETED` 状态改为 `OFF_SHELF`。代码中没有将 SKU 转为 `DELETED` 的实现，因此 `DELETED` 状态不可达；同时允许例如 `DRAFT -> OFF_SHELF`、`OFF_SHELF -> ON_SHELF` 等宽松转换，不能体现四状态状态机的完整生命周期约束。

### 2. 商品搜索 keyword 未覆盖卖点模糊匹配

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java:101`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/05-商品服务设计.md:46`
- 不一致原因：设计要求 `keyword` 支持“商品名称、卖点模糊匹配”；当前实现只对 `ProductSku.name` 做 `like` 查询。
- 详细解析：`buildSpecification` 中 `keyword` 条件仅构造 `cb.like(cb.lower(root.get("name")), ...)`。该 `root` 是 `ProductSku`，没有联查或过滤 SPU 的卖点/描述字段，也没有在 `ProductSpu` 上做名称或卖点匹配。因此当用户按商品卖点搜索时，当前实现不会返回符合设计要求的结果。

### 3. 商品搜索 categoryId 未包含子类目，且在分页后过滤会导致结果不完整

- 代码定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java:65`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java:77`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java:121`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/05-商品服务设计.md:47`
- 不一致原因：设计要求 `categoryId` 过滤“包含子类目”；当前实现只比较 SPU 的 `categoryId` 是否等于请求的 `categoryId`，没有查询类目树子节点。同时 category 过滤发生在 `skuRepository.findAll(spec, pageRequest)` 分页之后，分页总数和结果集都不是按 category 条件计算。
- 详细解析：`matchesCategory` 的实现是 `categoryId.equals(spu.getCategoryId())`，没有基于 `Category.parentId` 查找后代类目，也没有将后代类目 ID 纳入过滤集合。并且第 65 行先按仅 SKU 条件分页，第 77 行才做 category 过滤，这会造成某页内大量 SKU 被过滤掉，而真正匹配 category 的 SKU 可能在后续页，返回结果和 `totalElements` 均不符合按 `categoryId` 搜索的语义。

### 4. 商品搜索 brandId 在分页后过滤，不能完整实现品牌过滤条件

- 代码定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java:65`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java:78`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java:132`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/05-商品服务设计.md:48`
- 不一致原因：设计要求支持 `brandId` 品牌过滤；当前实现虽然有 `matchesBrand`，但品牌条件不是查询条件的一部分，而是在 SKU 分页结果上做内存过滤。
- 详细解析：`skuRepository.findAll(spec, pageRequest)` 执行时，`spec` 不包含 `brandId`，第 78 行才对当前页内容执行 `matchesBrand`。这会导致分页总数仍按未过滤品牌的 SKU 计算，并且符合品牌条件的 SKU 可能因为未出现在当前页而被漏掉。因此当前实现不能完整满足 `brandId` 搜索条件。

### 5. 商品搜索 tags 条件只有 DTO 字段，没有实际过滤实现

- 代码定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/src/main/java/com/ecommerce/product/dto/ProductSearchRequest.java:24`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java:89`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/05-商品服务设计.md:50`
- 不一致原因：设计要求支持 `tags` 标签过滤；当前请求 DTO 有 `tags` 字段，但 `ProductSearchService.buildSpecification` 和后续过滤逻辑均未读取 `request.getTags()`。
- 详细解析：`ProductSearchRequest` 声明了 `private List<String> tags`，但搜索实现只处理状态、keyword、minPrice、maxPrice，并在内存中处理 category、brand。全文件未对 tags 构造查询条件，也没有基于 `ProductTag` 或商品-标签关系进行过滤。因此传入 tags 参数不会影响搜索结果。

## 未发现不一致的检查点

1. 领域模型实体：`ProductSpu`、`ProductSku`、`Category`、`Brand`、`AttributeTemplate`、`ProductTag` 均存在对应 JPA Entity。
2. 商品详情库存摘要：`ProductDetailService` 通过 `InventoryQueryService.getStockSummary(skuId)` 获取库存摘要，未发现商品详情直接访问库存表或注入库存模块 Repository。
3. `ProductQueryService` 方法签名：已定义 `getSku(Long skuId)`、`getSkuForSale(Long skuId)`、`listSkuByIds(Collection<Long> skuIds)`、`getProductSnapshot(Long skuId)`。
4. 商品搜索 `minPrice/maxPrice`：已在 `ProductSearchService.buildSpecification` 中实现价格区间过滤。
5. 商品搜索 `onlyOnShelf`：`ProductSearchRequest` 默认 `onlyOnShelf = true`，搜索实现默认只返回 `ON_SHELF`。
6. REST API：设计要求的 8 个端点均存在对应 Controller 映射：创建 SPU、创建 SKU、SKU 上架、SKU 下架、商品列表、商品搜索、商品详情、类目树。
7. 跨模块约束：库存模块通过 `ProductQueryService` 获取商品信息，未发现库存模块内重复定义 Product JPA Entity。

## 无法确认项

无
