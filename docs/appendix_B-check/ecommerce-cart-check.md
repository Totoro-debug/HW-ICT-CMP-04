# ecommerce-cart - 附录B 一致性检查

## 检查范围
- 模块：ecommerce-cart
- 附录：附录B
- 输入资料：
  - `README.md`：比赛边界、修改边界、冻结契约、检查口径相关内容（设计文档是验收基准、公开用例不覆盖全部验收范围、配置文件允许修改但不得破坏契约）
  - `design-docs/附录B-配置参考.md` 全文
  - `code/ecommerce-cart/src/main/java` 下所有源文件
  - `code/ecommerce-cart/src/test/java` 下所有测试源文件
  - `code/ecommerce-cart` 下配置文件：未发现 `application.yml`、`application-test.yml`、`application.properties` 等模块内配置文件
  - `code/ecommerce-cart/pom.xml`
  - `code/pom.xml`

## 检查结论
- 共发现 1 处不一致

## 不一致明细
### 1. cart 配置项未绑定，TTL 与最大商品种类限制均为硬编码
- 设计要求定位：
  - `design-docs/附录B-配置参考.md:43`-`design-docs/附录B-配置参考.md:45`：示例配置层级要求 `cart.ttl-days: 7`、`cart.max-items: 100`。
  - `design-docs/附录B-配置参考.md:78`：配置默认值要求 `cart.ttl-days` 默认值为 7，说明为购物车本地缓存 TTL。
  - `README.md:9`-`README.md:14`：设计文档为验收基准，代码实现需与设计文档一致，不可反向修改设计文档。
  - `README.md:28`-`README.md:33`：允许修改配置文件和 POM，但不得破坏 API 契约。
  - `README.md:237`：公开用例不覆盖全部验收范围。
- 代码定位：
  - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/config/CartCacheConfig.java:23`-`code/ecommerce-cart/src/main/java/com/ecommerce/cart/config/CartCacheConfig.java:26`：购物车 TTL 使用 `private static final Duration CART_TTL = Duration.ofDays(7)` 硬编码。
  - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/config/CartCacheConfig.java:37`-`code/ecommerce-cart/src/main/java/com/ecommerce/cart/config/CartCacheConfig.java:42`：缓存 Bean 直接使用硬编码 `CART_TTL`。
  - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:21`-`code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:23`：最大商品种类使用 `private static final int MAX_ITEM_TYPES = 100` 硬编码。
  - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:93`-`code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:96`：购物车大小校验直接使用硬编码 `MAX_ITEM_TYPES`。
- 不一致说明：附录B将购物车 TTL 与最大商品种类定义为 `cart` 层级下的配置项，其中 `cart.ttl-days` 还明确列入默认值表；当前模块没有 `application.yml`/`application-test.yml` 中的 `cart` 配置，也没有 `@ConfigurationProperties` 或 `@Value` 等配置绑定，实际运行仅依赖 Java 常量。虽然当前硬编码数值与示例/默认值相同，但配置项名称、层级和绑定行为没有实现，外部配置无法按附录B的 `cart.ttl-days`、`cart.max-items` 生效。
- 原因分析：设计要求是以 `cart.ttl-days` 和 `cart.max-items` 作为可配置项，并至少保证 `cart.ttl-days` 默认值为 7；当前实现是 `CartCacheConfig` 与 `CartValidationService` 内部常量驱动，未从 Spring 配置体系读取对应键。该问题属于配置绑定缺失，同时导致配置项层级/名称在当前模块内不可生效。
