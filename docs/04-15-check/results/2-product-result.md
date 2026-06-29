# 第 2 批商品服务修复结果

## 负责模块与 R-... ID 列表
- 模块：商品服务
- 范围：R-PRODUCT-01、R-PRODUCT-02、R-PRODUCT-03、R-PRODUCT-04、R-PRODUCT-05

## 修改的主要文件
- `code/ecommerce-product/src/main/java/com/ecommerce/product/service/SkuService.java`
- `code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java`
- `code/ecommerce-product/src/main/java/com/ecommerce/product/repository/ProductTagRepository.java`
- `code/ecommerce-product/src/main/java/com/ecommerce/product/entity/ProductSpuTag.java`
- `code/ecommerce-product/src/main/java/com/ecommerce/product/repository/ProductSpuTagRepository.java`
- `code/ecommerce-product/src/test/java/com/ecommerce/product/service/SkuServiceTest.java`
- `code/ecommerce-product/src/test/java/com/ecommerce/product/service/ProductSearchServiceTest.java`
- `code/ecommerce-product/src/test/java/com/ecommerce/product/service/ProductSearchServiceDataJpaTest.java`

## 每个 R-... 的修复摘要
- `R-PRODUCT-01`：收紧 SKU 状态机，仅允许 `DRAFT -> ON_SHELF`、`ON_SHELF -> OFF_SHELF`；新增内部 `deleteSku(Long skuId)`，允许 `DRAFT/OFF_SHELF -> DELETED`，未新增冻结外 REST API。
- `R-PRODUCT-02`：keyword 搜索扩展为同时匹配 `ProductSku.name`、`ProductSpu.name`、`ProductSpu.description`，并在分页前下推到数据库查询。
- `R-PRODUCT-03`：`categoryId` 支持递归子类目，且通过 SPU 子查询在分页前生效，修复分页后过滤导致的总数错误。
- `R-PRODUCT-04`：`brandId` 改为分页前数据库过滤，不再在分页后内存过滤。
- `R-PRODUCT-05`：新增 `ProductSpuTag` 内部关联模型与仓储，`tags` 现在会按标签名称解析 ID 并在分页前过滤。

## 已执行测试命令与结果
```bash
mvn -q -f code/pom.xml -pl ecommerce-product,ecommerce-promotion -am test
```

结果：通过。
- `com.ecommerce.product.service.SkuServiceTest`：15/15 通过
- `com.ecommerce.product.service.ProductSearchServiceTest`：8/8 通过
- `com.ecommerce.product.service.ProductSearchServiceDataJpaTest`：5/5 通过
- `com.ecommerce.product.service.ProductQueryServiceImplTest`：2/2 通过
- `com.ecommerce.promotion.service.CouponServiceTest$CalculateDiscount`：4/4 通过
- `com.ecommerce.promotion.service.CouponServiceTest$Claim`：3/3 通过
- `com.ecommerce.promotion.service.CouponValidatorTest`：6/6 通过
- `com.ecommerce.promotion.service.PromotionCalculationServiceTest`：5/5 通过
- `com.ecommerce.promotion.service.SeckillServiceTest`：7/7 通过

## 未完成项、风险或需要后续批次协调的事项
- `ProductSpuTag` 依赖当前 JPA 自动建表；若后续环境关闭自动 DDL，需要补迁移。
- `deleteSku` 当前仅为内部服务能力，未暴露新 REST 端点，以保持冻结 API 契约。
- promotion 后续已复用商品模块只读能力与标签/类目过滤语义；后续 order/promotion 如继续读取类目或标签信息，应复用现有内部查询与关联模型，不新增公共 REST API。
