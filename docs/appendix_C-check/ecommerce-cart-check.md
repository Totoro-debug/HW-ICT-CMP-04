# ecommerce-cart - 附录C 一致性检查

## 检查范围
- 模块：ecommerce-cart
- 附录：附录C（数据模型）
- 输入资料：
  - `D:/Desktop/work/HW-ICT-CMP-04/README.md`：比赛边界、修改边界、冻结契约、检查口径相关内容，包含设计文档作为验收基准、公开用例不覆盖全部验收范围等要求。
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录C-数据模型.md` 全文。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/pom.xml`。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java` 下全部源文件：
    - `com/ecommerce/cart/cache/CartCacheManager.java`
    - `com/ecommerce/cart/cache/CartData.java`
    - `com/ecommerce/cart/cache/CartItemData.java`
    - `com/ecommerce/cart/config/CartCacheConfig.java`
    - `com/ecommerce/cart/controller/CartController.java`
    - `com/ecommerce/cart/dto/AddCartItemRequest.java`
    - `com/ecommerce/cart/dto/CartEstimateRequest.java`
    - `com/ecommerce/cart/dto/CartEstimateResponse.java`
    - `com/ecommerce/cart/dto/CartItemResponse.java`
    - `com/ecommerce/cart/dto/CartResponse.java`
    - `com/ecommerce/cart/dto/UpdateCartItemRequest.java`
    - `com/ecommerce/cart/service/CartService.java`
    - `com/ecommerce/cart/service/CartValidationService.java`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/test/java` 下全部测试源文件：
    - `com/ecommerce/cart/cache/CartCacheManagerTest.java`
    - `com/ecommerce/cart/controller/CartControllerTest.java`
    - `com/ecommerce/cart/service/CartServiceTest.java`
    - `com/ecommerce/cart/service/CartValidationServiceTest.java`
  - 当前模块未发现 `src/main/resources` / `src/test/resources` 下的 `application.yml`、`application-test.yml` 等模块配置文件。

## 检查结论
- 当前模块无与本附录直接相关的购物车域数据模型检查项。
- 对当前模块直接复用的附录C已有模型字段/类型/枚举进行核对后，未发现不一致。
- 共发现 0 处不一致。

## 不一致明细
当前模块无与本附录直接相关的检查项；未发现与当前附录相关的实现不一致项。

## 核对依据摘要
- README 明确 `design-docs/` 为验收基准，且代码必须对比设计文档修正：`D:/Desktop/work/HW-ICT-CMP-04/README.md:9`、`D:/Desktop/work/HW-ICT-CMP-04/README.md:12`、`D:/Desktop/work/HW-ICT-CMP-04/README.md:281`。
- README 明确公开黑盒用例不覆盖全部验收范围：`D:/Desktop/work/HW-ICT-CMP-04/README.md:237`。
- 附录C列出的数据模型域为用户、商品、库存、订单、支付、促销、物流/积分/评价，未显式列出购物车域表或实体：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录C-数据模型.md:3`、`D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录C-数据模型.md:32`、`D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录C-数据模型.md:56`、`D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录C-数据模型.md:90`、`D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录C-数据模型.md:123`、`D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录C-数据模型.md:161`、`D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录C-数据模型.md:176`。
- 当前模块声明依赖并复用商品、库存、促销模块，但自身未声明数据库表实体；购物车数据为缓存 POJO：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/pom.xml:17`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/pom.xml:22`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/pom.xml:28`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/cache/CartData.java:10`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/cache/CartItemData.java:9`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/config/CartCacheConfig.java:37`。
- 当前模块直接复用的商品 SKU 字段与附录C商品模型字段方向一致：附录C要求 `product_sku.id` 为 BIGINT、`name` 为 VARCHAR、`price` 为 DECIMAL(18,2)、`status` 枚举包含 `ON_SHELF`（`D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录C-数据模型.md:48`、`D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录C-数据模型.md:51`、`D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录C-数据模型.md:52`、`D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录C-数据模型.md:53`）；当前模块以 `Long skuId`、`String skuName`、`BigDecimal price`、`ON_SHELF` 状态进行使用（`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/cache/CartItemData.java:11`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/cache/CartItemData.java:12`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/cache/CartItemData.java:13`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:21`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:46`）。
- 当前模块直接复用的库存数量字段与附录C库存模型字段方向一致：附录C要求 `inventory_stock.sku_id` 为 BIGINT、`reserved_stock` 为 INT（`D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录C-数据模型.md:74`、`D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录C-数据模型.md:76`）；当前模块以 `Long skuId` 查询库存并以 `Integer reservedStock` 暴露库存数量（`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:62`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-cart/src/main/java/com/ecommerce/cart/dto/CartItemResponse.java:16`）。
