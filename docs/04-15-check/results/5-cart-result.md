# 第 5 批购物车服务修复结果

## 负责模块与 R-... ID 列表
- 模块：购物车服务
- 范围：R-CART-01、R-CART-02、R-CART-03

## 修改的主要文件
- `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java`
- `code/ecommerce-cart/src/main/java/com/ecommerce/cart/dto/CartItemResponse.java`
- `code/ecommerce-cart/src/main/java/com/ecommerce/cart/dto/CartEstimateResponse.java`
- `code/ecommerce-cart/src/test/java/com/ecommerce/cart/service/CartServiceTest.java`
- `docs/04-15-check/checklist.md`

## 每个 R-... 的修复摘要
- `R-CART-01`：同一 SKU 重复加入购物车时，不再覆盖数量；已改为 `existingQuantity + requestQuantity`，并对累加后的总量执行数量与库存校验。
- `R-CART-02`：`GET /api/v1/cart` 的购物车项响应新增库存展示字段 `availableStock`、`reservedStock`，由 `InventoryQueryService.getStockSummary(skuId)` 填充。
- `R-CART-03`：`CartEstimateResponse` 新增 `fullReductionDiscount`、`memberDiscount`、`applicableCoupons`，并从 `PromotionCalculateResponse` 映射拆分优惠；既有 `discountAmount` 继续保留为总优惠兼容字段。

## 已执行测试命令与结果
```bash
mvn -q -f code/pom.xml -pl ecommerce-cart -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=CartServiceTest,CartValidationServiceTest test
```
- 结果：通过。
- `com.ecommerce.cart.service.CartServiceTest`：14/14 通过
- `com.ecommerce.cart.service.CartValidationServiceTest`：12/12 通过

## 未完成项、风险或需要后续批次协调的事项
- 购物车库存展示当前按单项调用 `InventoryQueryService.getStockSummary` 填充；购物车项数上限为 100，后续若有性能压力可再做批量化优化，但不属于本次 49 项修复范围。
- 购物车预估中的优惠拆分依赖促销模块已修复的 `PromotionCalculateResponse` 字段；当前已和第 2 批促销修复结果对齐。
- 本次为补齐被前面批次遗漏的 `R-CART-01 ~ 03`，未新增任何冻结外 REST API。
