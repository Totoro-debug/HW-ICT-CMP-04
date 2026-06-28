# 电商购物车服务一致性检查报告

## 检查范围

- 设计文档：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/07-购物车服务设计.md`
- 源码目录：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/`
- 模块 POM：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/pom.xml`
- 父级 POM：`D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`

## 不一致点

### 1. 同一 SKU 重复加入时未按设计要求累加数量

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:78`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:82`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/07-购物车服务设计.md:25`

**不一致原因：** 设计要求“同一个 SKU 重复加入购物车时，数量累加”，但代码在发现已有购物车项后直接将数量设置为本次请求数量。

**详细解析：** `CartService.addItem` 在 `existingItem.isPresent()` 分支中取得已有 `CartItemData` 后，执行 `item.setQuantity(request.getQuantity())`。这会覆盖原购物车中的数量，而不是执行“已有数量 + 本次加入数量”。例如购物车中已有 SKU 数量 2，再次加入数量 3，设计要求结果应为 5，当前实现结果为 3。同时，当前重复加入分支只对本次请求数量进行 `validateQuantity` 和 `validateStock`，没有基于累加后的总数量重新校验 1-999 范围和库存。

### 2. 购物车库存展示未体现通过 InventoryQueryService 查询的库存信息

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:248`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/dto/CartItemResponse.java:10`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:62`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/07-购物车服务设计.md:22`

**不一致原因：** 设计要求“库存展示”通过 `InventoryQueryService` 查询；当前代码只在校验库存时调用 `InventoryQueryService.getStockSummary`，购物车查询返回 DTO 中没有库存展示字段，构建购物车响应时也未查询库存摘要用于展示。

**详细解析：** `CartValidationService.validateStock` 使用 `InventoryQueryService.getStockSummary(skuId)` 做库存校验，满足“校验库存”的用途。但 `CartService.buildCartResponse` 仅遍历缓存中的购物车项并调用 `toCartItemResponse`，`CartItemResponse` 字段只有 `skuId`、`skuName`、`price`、`quantity`、`subtotal`，没有可用库存、库存状态或库存摘要等展示信息。因此 REST 查询购物车时无法按设计提供通过 `InventoryQueryService` 查询得到的库存展示内容。

### 3. 价格预估返回字段不完整，缺少优惠券可用列表和会员折扣等设计字段

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/dto/CartEstimateResponse.java:10`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/dto/CartEstimateResponse.java:15`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:212`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:218`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/07-购物车服务设计.md:29`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/07-购物车服务设计.md:31`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/07-购物车服务设计.md:36`

**不一致原因：** 设计要求价格预估返回 6 项：商品原价合计、满减优惠、优惠券可用列表、会员折扣、预计运费、预计应付金额。当前 `CartEstimateResponse` 只包含 `itemTotal`、`shippingFee`、`packagingFee`、`discountAmount`、`pointsDeductionAmount`、`payableAmount`，缺少“优惠券可用列表”和“会员折扣”的独立返回字段，“满减优惠”也被合并为泛化的 `discountAmount`，无法区分优惠类型。

**详细解析：** `CartService.estimate` 通过 `PromotionCalculationService.calculate` 取得 `totalDiscount` 并设置为 `discountAmount`，但响应模型没有承载可用优惠券列表、会员折扣或满减优惠拆分信息的字段。由于接口响应结构本身缺失这些字段，即使促销服务内部能够计算相关结果，购物车服务也无法按设计向调用方返回完整的价格预估信息。

## 未发现不一致的检查点

1. 存储要求：购物车使用本地 Caffeine Cache，Key 为 `cart:{userId}`，TTL 为 7 天。
   - 代码依据：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/config/CartCacheConfig.java:37`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/config/CartCacheConfig.java:41`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/cache/CartCacheManager.java:63`
   - 依赖依据：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/pom.xml:32`

2. 购物车规则：最大商品种类 100、单项数量 1-999、SKU 状态必须为 `ON_SHELF`。
   - 代码依据：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:21`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:22`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:23`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:46`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:79`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:93`

3. 价格预估调用 `PromotionCalculationService` 计算。
   - 代码依据：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:48`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:290`

4. REST API 6 个端点完整实现。
   - 代码依据：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/controller/CartController.java:45`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/controller/CartController.java:57`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/controller/CartController.java:68`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/controller/CartController.java:80`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/controller/CartController.java:91`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/controller/CartController.java:102`

## 无法确认项

无。

## 汇总

- 不一致点数量：3
- 无法确认项：无
