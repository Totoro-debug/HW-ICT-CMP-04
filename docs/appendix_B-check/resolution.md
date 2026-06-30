# 附录B 修复方案汇总

## 总体说明

本方案只覆盖 `docs/appendix_B-check` 中已经报告的附录B不一致项，不新增报告外问题。修复边界以 `README.md:25`-`README.md:40` 的允许/禁止修改范围、`README.md:72`-`README.md:198` 的冻结 REST API 契约、`README.md:199`-`README.md:229` 的错误响应基线、`README.md:278`-`README.md:281` 的验收原则，以及 `design-docs/附录B-配置参考.md:3`-`design-docs/附录B-配置参考.md:81` 为准；不得修改 `README.md`、`design-docs/` 或既有检查报告。

附录B重点是配置项、默认值、配置绑定和测试 profile/运行时配置覆盖行为。后续开发实施时应优先保持 API URL、HTTP Method、请求/响应字段名和错误码结构不变，只调整配置文件、配置属性类、读取配置的 Service/Component、必要测试和 POM 依赖。

未发现附录B不一致的模块：`ecommerce-inventory`、`ecommerce-product`、`ecommerce-user`。

## 修复方案明细

### R1. 补齐启动模块 `application.yml` / `application-test.yml` 中附录B缺失和错误覆盖的配置项

- 所属模块：`ecommerce-app`
- 覆盖问题：
  - `invoice.max-title-length` 配置项缺失
  - `promotion.stack-order` 配置项缺失
  - `logistics.default-carrier` 与 `logistics.free-shipping-threshold` 配置项缺失
  - `test.reset-enabled` 配置项缺失
  - 测试 profile 中 `security.jwt` 示例值与附录B不一致
- 检查报告定位：
  - `docs/appendix_B-check/ecommerce-app-check.md:21`-`docs/appendix_B-check/ecommerce-app-check.md:25`
  - `docs/appendix_B-check/ecommerce-app-check.md:27`-`docs/appendix_B-check/ecommerce-app-check.md:31`
  - `docs/appendix_B-check/ecommerce-app-check.md:33`-`docs/appendix_B-check/ecommerce-app-check.md:37`
  - `docs/appendix_B-check/ecommerce-app-check.md:39`-`docs/appendix_B-check/ecommerce-app-check.md:43`
  - `docs/appendix_B-check/ecommerce-app-check.md:45`-`docs/appendix_B-check/ecommerce-app-check.md:49`
- 设计依据定位：
  - `design-docs/附录B-配置参考.md:22`-`design-docs/附录B-配置参考.md:26`
  - `design-docs/附录B-配置参考.md:39`-`design-docs/附录B-配置参考.md:41`
  - `design-docs/附录B-配置参考.md:55`-`design-docs/附录B-配置参考.md:63`
  - `design-docs/附录B-配置参考.md:65`-`design-docs/附录B-配置参考.md:66`
  - `README.md:25`-`README.md:40`、`README.md:278`-`README.md:281`
- 当前实现定位：
  - `code/ecommerce-app/src/main/resources/application.yml:35`-`code/ecommerce-app/src/main/resources/application.yml:52`
  - `code/ecommerce-app/src/test/resources/application-test.yml:17`-`code/ecommerce-app/src/test/resources/application-test.yml:51`
- 修复目标：启动配置和测试 profile 均承载附录B示例中要求存在的配置层级和配置项；测试 profile 不再用与附录B冲突的 `security.jwt.*` 值覆盖示例值。
- 修复方案：
  1. 修改 `code/ecommerce-app/src/main/resources/application.yml`：
     - 在 `invoice` 下新增 `max-title-length: 100`。
     - 在 `logistics` 下保留现有 `callback-secret`，新增 `default-carrier: LOCAL_EXPRESS`、`free-shipping-threshold: 199.00`。
     - 在 `loyalty` 后新增：
       ```yaml
       promotion:
         stack-order:
           - FULL_REDUCTION
           - COUPON
           - MEMBER_DISCOUNT

       test:
         reset-enabled: false
       ```
  2. 修改 `code/ecommerce-app/src/test/resources/application-test.yml`：
     - 将 `security.jwt.issuer`、`security.jwt.secret`、`security.jwt.expire-minutes` 调整为附录B示例值 `shophub`、`local-development-secret-change-me`、`120`，避免测试 profile 覆盖为报告中指出的冲突值。
     - 同步补齐 `invoice.max-title-length: 100`、`logistics.default-carrier: LOCAL_EXPRESS`、`logistics.free-shipping-threshold: 199.00`、`promotion.stack-order`、`test.reset-enabled: false`。
  3. 不修改 `logistics.callback-secret`、`payment.callback-signature` 等附录B未覆盖但现有代码需要的配置，避免破坏现有回调认证行为。
- 影响范围：应用启动配置、测试 profile 配置；不改变任何 REST API 契约。
- 注意事项/风险点：公开测试可能依赖测试 profile 中原 `security.jwt.secret` 生成/校验 token；改动后需确保注册、登录和 ADMIN 认证链路仍统一读取同一套配置值。若测试工具不直接假设旧 secret，则应通过登录接口获取 token，风险可控。
- 建议验证方式：
  - 运行 `mvn -f code/pom.xml test`。
  - 运行 `mvn -f code/pom.xml install -DskipTests` 后运行 `mvn -f test-cases/pom.xml test`。
  - 增加/调整配置加载测试，断言 `Environment` 中 `invoice.max-title-length`、`promotion.stack-order[0..2]`、`logistics.default-carrier`、`logistics.free-shipping-threshold`、`test.reset-enabled`、`security.jwt.*` 与附录B一致。

### R2. 完整注册附录B默认值到 `RuntimeConfigRegistry`，保障运行时配置查询和覆盖基线

- 所属模块：`ecommerce-common`，影响 `ecommerce-app` 管理接口
- 问题标题：运行时配置默认值注册不完整，导致附录B列明的部分配置项无法按默认值查询/使用
- 检查报告定位：`docs/appendix_B-check/ecommerce-common-check.md:20`-`docs/appendix_B-check/ecommerce-common-check.md:29`
- 设计依据定位：`design-docs/附录B-配置参考.md:69`-`design-docs/附录B-配置参考.md:80`；运行时配置接口冻结契约见 `README.md:183`-`README.md:190`
- 当前实现定位：
  - `code/ecommerce-common/src/main/java/com/ecommerce/common/test/RuntimeConfigRegistry.java:7`-`code/ecommerce-common/src/main/java/com/ecommerce/common/test/RuntimeConfigRegistry.java:18`
  - `code/ecommerce-app/src/main/java/com/ecommerce/app/controller/SystemAdminController.java:35`-`code/ecommerce-app/src/main/java/com/ecommerce/app/controller/SystemAdminController.java:41`
- 修复目标：`GET /api/v1/admin/system/configs/{key}` 对附录B默认值表中的键在无覆盖时返回默认值；`PUT` 覆盖后各模块可通过 `RuntimeConfigRegistry.get*` 读取覆盖值。
- 修复方案：
  1. 修改 `code/ecommerce-common/src/main/java/com/ecommerce/common/test/RuntimeConfigRegistry.java` 的 `defaults`，至少补齐：
     - `order.expire-minutes` = `60`
     - `order.max-items` = `30`
     - `payment.retry-times` = `5`（保留）
     - `payment.refund-fee-rate` = `0.02`
     - `invoice.tax-rate` = `0.06`（保留，建议统一为字符串或数值均可，但读取方需兼容）
     - `cart.ttl-days` = `7`
     - `loyalty.max-redeem-points-per-order` = `10000`
     - `loyalty.max-redeem-ratio` = `0.5`
  2. 可保留现有 `loyalty.activity-multiplier`、`member.discount-rate` 等非附录B键，除非有其他附录检查要求；本任务不建议删除，避免影响既有业务测试。
  3. 不改变 `SystemAdminController` 的 URL、HTTP Method、响应字段 `key`/`value` 或错误响应结构。
- 影响范围：黑盒测试支撑管理接口的配置查询默认值、各模块通过 `RuntimeConfigRegistry` 读取运行时覆盖的行为。
- 注意事项/风险点：`Map.of` 最多支持 10 对键值，补齐后可能超过容量；应改用 `Map.ofEntries(...)` 或初始化不可变 `LinkedHashMap` 后包装，避免编译失败。
- 建议验证方式：
  - 单元测试 `RuntimeConfigRegistry.getOrDefault` 和 `getInt`/`getBigDecimal`/`getDouble` 对补齐键的返回。
  - 黑盒或集成测试调用 `GET /api/v1/admin/system/configs/order.expire-minutes`、`cart.ttl-days`、`loyalty.max-redeem-ratio`，断言返回附录B默认值；再 `PUT` 覆盖并断言返回覆盖值。

### R3. 为购物车模块建立 `cart.*` 配置绑定并替换 TTL/最大商品种类硬编码

- 所属模块：`ecommerce-cart`
- 问题标题：cart 配置项未绑定，TTL 与最大商品种类限制均为硬编码
- 检查报告定位：`docs/appendix_B-check/ecommerce-cart-check.md:19`-`docs/appendix_B-check/ecommerce-cart-check.md:32`
- 设计依据定位：`design-docs/附录B-配置参考.md:43`-`design-docs/附录B-配置参考.md:45`、`design-docs/附录B-配置参考.md:78`
- 当前实现定位：
  - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/config/CartCacheConfig.java:23`-`code/ecommerce-cart/src/main/java/com/ecommerce/cart/config/CartCacheConfig.java:42`
  - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:21`-`code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:23`
  - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:93`-`code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:99`
- 修复目标：`cart.ttl-days` 默认值为 7，`cart.max-items` 默认值为 100，且 `application.yml`、`application-test.yml` 或运行时覆盖能影响对应行为。
- 修复方案：
  1. 新增或复用配置属性类，例如 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/config/CartProperties.java`：
     - `@ConfigurationProperties(prefix = "cart")`
     - 字段 `ttlDays = 7`、`maxItems = 100`，提供 getter/setter；必要时加 `@Validated` 和最小值校验。
  2. 在 `CartCacheConfig` 构造注入 `CartProperties`，将 `Duration.ofDays(cartProperties.getTtlDays())` 用于 `Caffeine.expireAfterWrite(...)`，删除或停用 `CART_TTL` 常量。
  3. 在 `CartValidationService` 构造注入 `CartProperties`，`validateCartSize` 使用 `cartProperties.getMaxItems()`，错误消息中的最大值也从配置读取。
  4. 若模块未自动扫描 `@ConfigurationProperties`，在当前模块配置类或启动模块上增加 `@EnableConfigurationProperties(CartProperties.class)`；若已有 Spring Boot 配置属性扫描机制则无需额外 POM。仅当编译提示缺少元数据处理器时，才考虑在 POM 添加可选的 `spring-boot-configuration-processor`。
  5. 配置文件层面的 `cart.ttl-days`、`cart.max-items` 由 R1 保持在 `ecommerce-app` 主/测试配置中。
- 影响范围：购物车缓存 TTL、购物车最大商品种类校验；不改变购物车 API 字段。
- 注意事项/风险点：Caffeine cache Bean 在 Spring 上下文启动时读取配置，`PUT /admin/system/configs/cart.ttl-days` 运行时覆盖不应期望动态重建已创建 cache；若验收要求运行时覆盖，可在说明/测试中只覆盖 `cart.max-items` 这类运行时校验项，或设计动态 cache 重建机制但需谨慎。
- 建议验证方式：
  - 模块测试使用 `@TestPropertySource(properties = {"cart.ttl-days=1", "cart.max-items=2"})` 验证 Bean 创建和 `validateCartSize(2,1)` 拒绝。
  - 全量运行 `mvn -f code/pom.xml test`。

### R4. 为订单模块建立 `order.*` 配置绑定并替换过期时间、最大项数、包装费和包邮阈值硬编码

- 所属模块：`ecommerce-order`
- 问题标题：`order` 配置项未在订单模块进行配置绑定，部分值硬编码或未按配置约束生效
- 检查报告定位：`docs/appendix_B-check/ecommerce-order-check.md:20`-`docs/appendix_B-check/ecommerce-order-check.md:32`
- 设计依据定位：`design-docs/附录B-配置参考.md:28`-`design-docs/附录B-配置参考.md:32`、`design-docs/附录B-配置参考.md:73`-`design-docs/附录B-配置参考.md:74`
- 当前实现定位：
  - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:215`-`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:217`
  - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:261`
  - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPreconditionChecker.java:31`-`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPreconditionChecker.java:49`
  - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderTotalCalculator.java:22`-`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderTotalCalculator.java:65`
- 修复目标：`order.expire-minutes=60`、`order.max-items=30`、`order.packaging-fee=2.00`、`order.free-shipping-threshold=199.00` 通过配置绑定提供默认值并驱动订单行为。
- 修复方案：
  1. 新增 `OrderProperties`（建议路径 `code/ecommerce-order/src/main/java/com/ecommerce/order/config/OrderProperties.java`），使用 `@ConfigurationProperties(prefix = "order")`，字段：`expireMinutes=60`、`maxItems=30`、`packagingFee=BigDecimal("2.00")`、`freeShippingThreshold=BigDecimal("199.00")`。
  2. 在订单模块配置类或启动配置中启用 `OrderProperties`。
  3. `OrderService` 注入 `OrderProperties`，将 `order.setExpiresAt(SystemClockService.now().plusMinutes(60))` 改为使用 `orderProperties.getExpireMinutes()`；包装费仍调用 `OrderTotalCalculator`，但计算器应从配置读取单件/单类包装费。
  4. `OrderPreconditionChecker` 注入 `OrderProperties`，在 `itemCount <= 0` 后增加 `itemCount > orderProperties.getMaxItems()` 校验，抛出 `BusinessException("VALIDATION_FAILED", ...)`，保持 README 错误码基线，不新增 API 字段。
  5. `OrderTotalCalculator` 注入 `OrderProperties`，用 `freeShippingThreshold` 替代 `FREE_SHIPPING_THRESHOLD`，用 `packagingFee` 替代 `PACKAGING_FEE_PER_ITEM`。报告指出当前按商品种类数乘以 1.00；为了最小行为变更，可保持“每个商品种类收取一次包装费”的算法，只将单价改为配置值 `2.00`。
  6. 如需运行时覆盖行为，关键默认值同时由 R2 注册；可在 `OrderProperties` 基础上对 `RuntimeConfigRegistry.getInt("order.expire-minutes", orderProperties.getExpireMinutes())`、`RuntimeConfigRegistry.getInt("order.max-items", ...)`、`RuntimeConfigRegistry.getBigDecimal("order.packaging-fee", ...)`、`RuntimeConfigRegistry.getBigDecimal("order.free-shipping-threshold", ...)` 做读取封装。注意 R2 默认值表只明确 `expire-minutes` 和 `max-items`，`packaging-fee`、`free-shipping-threshold` 至少需通过 `application.yml` 绑定生效。
- 影响范围：订单创建过期时间、订单商品种类上限、运费/包装费金额计算。
- 注意事项/风险点：公开用例 `PUB-104` 可能断言非包邮订单 payableAmount 包含包装费；将包装费从 1.00 调整为 2.00 后需要同步更新期望或确认黑盒验收以设计文档为准。不得修改 API 响应字段，只允许金额值随设计变化。
- 建议验证方式：
  - 单元测试 `OrderTotalCalculator` 在 `order.packaging-fee=2.00` 时 `itemCount=2` 返回 `4.00`，`itemTotal >= 199.00` 时运费为 `0.00`。
  - 集成测试创建 31 个商品种类的订单返回 `VALIDATION_FAILED`，30 个通过。
  - 验证订单 `expiresAt` 与 `SystemClockService.now()` 相差配置分钟数。

### R5. 为支付/发票模块补齐 `payment.*` 与 `invoice.max-title-length` 绑定和业务使用

- 所属模块：`ecommerce-payment`
- 覆盖问题：
  - `payment` 配置绑定缺少 `retry-times` 和 `callback-timeout-seconds`
  - `invoice.max-title-length` 配置未绑定且未用于发票抬头长度约束
- 检查报告定位：
  - `docs/appendix_B-check/ecommerce-payment-check.md:20`-`docs/appendix_B-check/ecommerce-payment-check.md:32`
  - `docs/appendix_B-check/ecommerce-payment-check.md:34`-`docs/appendix_B-check/ecommerce-payment-check.md:45`
- 设计依据定位：
  - `design-docs/附录B-配置参考.md:34`-`design-docs/附录B-配置参考.md:37`
  - `design-docs/附录B-配置参考.md:39`-`design-docs/附录B-配置参考.md:41`
  - `design-docs/附录B-配置参考.md:75`-`design-docs/附录B-配置参考.md:77`
- 当前实现定位：
  - `code/ecommerce-payment/src/main/java/com/ecommerce/payment/config/PaymentConfig.java:11`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/config/PaymentConfig.java:33`
  - `code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentCallbackService.java:48`
  - `code/ecommerce-payment/src/main/java/com/ecommerce/payment/dto/InvoiceRequest.java:12`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/dto/InvoiceRequest.java:33`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/dto/InvoiceRequest.java:34`
  - `code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/InvoiceService.java:83`-`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/InvoiceService.java:99`
- 修复目标：`payment.retry-times=5`、`payment.refund-fee-rate=0.02`、`payment.callback-timeout-seconds=5` 完整绑定；`invoice.tax-rate=0.06`、`invoice.max-title-length=100` 完整绑定并用于发票抬头校验。
- 修复方案：
  1. 扩展 `PaymentConfig`：新增字段 `private int retryTimes = 5;`、`private int callbackTimeoutSeconds = 5;` 及 getter/setter；保留现有 `refundFeeRate` 和 `callbackSignature`。
  2. 检查 `PaymentCallbackService`：若有回调处理异步/等待/重试逻辑，将超时读取改为 `paymentConfig.getCallbackTimeoutSeconds()`；若当前无实际等待逻辑，至少在回调入口注入 `PaymentConfig` 并在签名/处理日志或可测试访问器中使用该配置，避免“绑定但不用”。支付失败重试逻辑若存在，应使用 `paymentConfig.getRetryTimes()` 或 `RuntimeConfigRegistry.getInt("payment.retry-times", paymentConfig.getRetryTimes())`；若不存在重试流程，可新增最小内部重试封装但不得改变 `/api/v1/payment/callback` 契约。
  3. 新增 `InvoiceProperties`（`@ConfigurationProperties(prefix = "invoice")`），字段 `taxRate=BigDecimal("0.06")`、`maxTitleLength=100`。或在 `InvoiceService` 直接用 `@Value` 绑定两个键；优先推荐 `@ConfigurationProperties`。
  4. `InvoiceService` 注入 `InvoiceProperties`，用 `invoiceProperties.getTaxRate()` 作为 `RuntimeConfigRegistry.getBigDecimal("invoice.tax-rate", ...)` 的 fallback；保存发票前校验 `request.getInvoiceTitle()` 非空时长度不得超过 `RuntimeConfigRegistry.getInt("invoice.max-title-length", invoiceProperties.getMaxTitleLength())`，超出时抛 `ValidationException("invoiceTitle", ...)`，对应 HTTP 400 / `VALIDATION_FAILED`，不改变响应结构。
  5. R1 已负责在主/测试配置中补齐 `invoice.max-title-length`；R2 已负责默认表中已有 `payment.retry-times`、`payment.refund-fee-rate`、`invoice.tax-rate`，如需运行时覆盖 `invoice.max-title-length` 和 `payment.callback-timeout-seconds`，可额外注册但本报告未要求默认值表注册。
- 影响范围：支付配置绑定、支付回调内部处理参数、发票税率 fallback 和发票抬头长度校验。
- 注意事项/风险点：`invoiceTitle` 当前 DTO 没有 Bean Validation 注解；使用 Service 层校验能避免新增 API 字段。若使用 `@Size`，必须确保错误响应仍符合 README 通用错误结构。
- 建议验证方式：
  - 配置属性绑定测试断言 `PaymentConfig.retryTimes=5`、`callbackTimeoutSeconds=5`。
  - 发票集成测试：标题长度 100 成功，101 返回 400 且错误码为 `VALIDATION_FAILED`。
  - 运行支付回调公开链路测试，确保签名和成功状态不受影响。

### R6. 为物流模块建立 `logistics.*` 配置绑定并替换默认承运商/包邮阈值硬编码

- 所属模块：`ecommerce-logistics`
- 问题标题：logistics 配置项未按附录B命名空间绑定，默认承运商实现值与配置示例不一致
- 检查报告定位：`docs/appendix_B-check/ecommerce-logistics-check.md:19`-`docs/appendix_B-check/ecommerce-logistics-check.md:29`
- 设计依据定位：`design-docs/附录B-配置参考.md:61`-`design-docs/附录B-配置参考.md:63`
- 当前实现定位：
  - `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/config/LogisticsConfig.java:10`-`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/config/LogisticsConfig.java:15`
  - `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/controller/AdminLogisticsController.java:55`-`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/controller/AdminLogisticsController.java:59`
  - `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/FreightCalculator.java:33`-`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/FreightCalculator.java:35`、`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/FreightCalculator.java:102`-`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/FreightCalculator.java:115`
  - `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/FreightTemplateService.java:27`-`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/FreightTemplateService.java:51`
- 修复目标：`logistics.default-carrier=LOCAL_EXPRESS`、`logistics.free-shipping-threshold=199.00` 通过配置绑定生效，打印面单默认承运商不再是硬编码 `DEFAULT`。
- 修复方案：
  1. 新增 `LogisticsProperties`（`@ConfigurationProperties(prefix = "logistics")`），字段 `defaultCarrier="LOCAL_EXPRESS"`、`freeShippingThreshold=BigDecimal("199.00")`，保留现有 `callbackSecret` 对应字段或继续由原代码读取，避免与 `callback-secret` 配置冲突。
  2. 在 `LogisticsConfig` 增加 `@EnableConfigurationProperties(LogisticsProperties.class)` 或确保被配置属性扫描。
  3. `AdminLogisticsController` 注入 `LogisticsProperties`，将 `shipmentService.printLabel(shipmentId, "DEFAULT")` 改为 `shipmentService.printLabel(shipmentId, logisticsProperties.getDefaultCarrier())`。
  4. `FreightCalculator` 注入 `LogisticsProperties`，用 `logisticsProperties.getFreeShippingThreshold()` 替代默认计算和模板兜底中的 `DEFAULT_FREE_SHIPPING_THRESHOLD`；`DEFAULT_FREIGHT=8.00` 不属于本报告问题，可保留。
  5. `FreightTemplateService` 注入 `LogisticsProperties`，创建模板时 `request.getFreeShippingThreshold() == null` 的兜底值改为 `logisticsProperties.getFreeShippingThreshold()`。
  6. R1 已在主/测试配置中补齐对应键。
- 影响范围：物流面单默认承运商、默认运费计算、运费模板创建缺省包邮阈值。
- 注意事项/风险点：`FreightCalculator` 目前存在用于测试的包可见构造函数 `FreightCalculator(..., ObjectMapper)`；新增配置依赖时应保留测试友好构造或提供默认 `LogisticsProperties`，避免破坏既有单元测试。
- 建议验证方式：
  - 单元测试打印面单时默认 carrier 为 `LOCAL_EXPRESS`。
  - 单元测试配置 `logistics.free-shipping-threshold=50.00` 后 `itemTotal=60.00` 运费为 0。
  - 模板创建请求不传 `freeShippingThreshold` 时保存值为配置值。

### R7. 为积分模块建立 `loyalty.*` 配置绑定并替换硬编码/非附录B键名

- 所属模块：`ecommerce-loyalty`
- 问题标题：`loyalty.*` 配置项未建立配置绑定，当前实现以硬编码常量/非附录B键名替代
- 检查报告定位：`docs/appendix_B-check/ecommerce-loyalty-check.md:19`-`docs/appendix_B-check/ecommerce-loyalty-check.md:23`
- 设计依据定位：`design-docs/附录B-配置参考.md:47`-`design-docs/附录B-配置参考.md:53`、`design-docs/附录B-配置参考.md:79`-`design-docs/附录B-配置参考.md:80`
- 当前实现定位：
  - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:35`-`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:44`
  - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:218`-`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:237`
  - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:253`-`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:280`
  - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEventListener.java:22`-`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEventListener.java:45`
- 修复目标：`loyalty.points-per-yuan`、`loyalty.redeem-rate`、`loyalty.max-redeem-points-per-order`、`loyalty.max-redeem-ratio`、`loyalty.expire-months`、`loyalty.review-reward-points` 均通过配置绑定提供默认值并驱动积分行为；运行时覆盖至少对默认值表中 `max-redeem-points-per-order`、`max-redeem-ratio` 生效。
- 修复方案：
  1. 新增 `LoyaltyProperties`（`@ConfigurationProperties(prefix = "loyalty")`），字段默认值：`pointsPerYuan=1`、`redeemRate=100`、`maxRedeemPointsPerOrder=10000`、`maxRedeemRatio=BigDecimal("0.5")`、`expireMonths=12`、`reviewRewardPoints=20`。
  2. 明确语义：附录B示例中 `points-per-yuan: 1` 用于支付后积分发放（每 1 元基础得 1 积分）；`redeem-rate: 100` 用于抵扣换算（100 积分 = 1 元）。因此：
     - `calcOrderPoints` 应将金额乘以 `pointsPerYuan`、会员倍率、请求活动倍率；不应继续依赖附录B未定义的 `loyalty.activity-multiplier` 作为唯一配置来源。若为兼容旧测试，可保留 `loyalty.activity-multiplier` 作为额外乘数，但不能替代附录B键。
     - `calculateRedeemablePoints` 中金额换算应使用 `redeemRate`、`maxRedeemRatio`、`maxRedeemPointsPerOrder`，其中后两者优先通过 `RuntimeConfigRegistry.getInt/getDouble` 支持运行时覆盖。
  3. `earnPoints` 中积分过期时间使用 `loyalty.expire-months` 替代 `DEFAULT_EXPIRE_MONTHS`。
  4. `ReviewApprovedEventListener` 注入 `LoyaltyProperties`，调用 `loyaltyPointService.earnPoints(..., loyaltyProperties.getReviewRewardPoints(), ...)` 替代 `REVIEW_REWARD_POINTS` 常量。
  5. R1 已补齐 `loyalty.*` 配置承载，R2 已补齐 `loyalty.max-redeem-points-per-order` 和 `loyalty.max-redeem-ratio` 运行时默认值。
- 影响范围：支付积分发放、积分抵扣上限、积分过期时间、评价奖励积分。
- 注意事项/风险点：当前代码常量 `POINTS_PER_YUAN=100` 实际用于抵扣换算，不等同于附录B `points-per-yuan=1`；实施时应避免简单改名导致积分发放/抵扣语义混淆。建议用两个明确字段：`pointsPerYuan` 和 `redeemRate`。
- 建议验证方式：
  - 单元测试 `points-per-yuan=2` 时 10 元订单基础积分为 20（再乘会员/活动倍率）。
  - 单元测试 `redeem-rate=100`、`max-redeem-ratio=0.5`、`max-redeem-points-per-order=10000` 时 200 元订单最多抵扣 10000 或 100 元对应积分中的较小值。
  - 运行时 `PUT /admin/system/configs/loyalty.max-redeem-ratio` 后估算抵扣结果变化。

### R8. 将 review 模块遗留评价奖励监听器的奖励积分改为读取 `loyalty.review-reward-points`

- 所属模块：`ecommerce-review`
- 问题标题：评价奖励积分在 review 模块中硬编码，未绑定附录B定义的 `loyalty.review-reward-points`
- 检查报告定位：`docs/appendix_B-check/ecommerce-review-check.md:19`-`docs/appendix_B-check/ecommerce-review-check.md:23`
- 设计依据定位：`design-docs/附录B-配置参考.md:47`-`design-docs/附录B-配置参考.md:53`
- 当前实现定位：`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewApprovedEventListener.java:19`-`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewApprovedEventListener.java:20`、`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewApprovedEventListener.java:75`-`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewApprovedEventListener.java:81`
- 修复目标：review 模块中与评价奖励积分相关的遗留逻辑也不再硬编码 `20`，而是绑定 `loyalty.review-reward-points`，与 loyalty 模块保持一致。
- 修复方案：
  1. 该类注释说明 “kept out of Spring registration”，不是当前主事件处理 Bean。为了最小化风险，可通过构造函数注入/传入奖励分值，并保留无参构造的默认值从配置常量类或 `@Value` 适配器读取；若要成为 Spring Bean，必须确认不会与 `ecommerce-loyalty` 的 `ReviewApprovedEventListener` 双重发放积分。
  2. 推荐方案：抽取共享配置属性类不可跨模块直接依赖时，在 review 模块新增轻量 `ReviewRewardProperties` 或 `@Value("${loyalty.review-reward-points:20}")` 的 Spring 适配构造；将 `REVIEW_REWARD_POINTS` 常量替换为实例字段 `reviewRewardPoints`。
  3. `awardReviewPoints` 和日志均使用实例字段；默认值保持 20。
  4. 不改变评价 API，不新增实际发放路径；若该类仍非 Spring Bean，可新增单元测试验证显式传入配置值时日志/调用使用该值。
- 影响范围：review 模块遗留事件监听器的奖励积分来源。
- 注意事项/风险点：不得因为绑定配置而把该遗留类注册为新的事件监听 Bean，除非同时移除重复发放风险；当前主奖励流程已在 `ecommerce-loyalty` 中，R7 会修复主路径。
- 建议验证方式：
  - 单元测试构造 `ReviewApprovedEventListener(..., 30)` 或测试属性 `loyalty.review-reward-points=30`，断言 `awardReviewPoints` 使用 30。
  - 回归评价审核链路，确认只产生一次积分奖励。

### R9. 为促销模块承载并绑定 `promotion.stack-order`，消除叠加顺序纯硬编码

- 所属模块：`ecommerce-promotion`
- 问题标题：`promotion.stack-order` 配置项缺失配置承载与绑定，促销叠加顺序被代码硬编码
- 检查报告定位：`docs/appendix_B-check/ecommerce-promotion-check.md:20`-`docs/appendix_B-check/ecommerce-promotion-check.md:34`
- 设计依据定位：`design-docs/附录B-配置参考.md:55`-`design-docs/附录B-配置参考.md:59`
- 当前实现定位：
  - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:74`-`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:80`
  - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:139`-`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:140`
  - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:207`
  - `code/ecommerce-promotion/pom.xml:11`-`code/ecommerce-promotion/pom.xml:35`
  - 配置承载缺失见 `code/ecommerce-app/src/main/resources/application.yml:45`-`code/ecommerce-app/src/main/resources/application.yml:52` 和 `code/ecommerce-app/src/test/resources/application-test.yml:45`-`code/ecommerce-app/src/test/resources/application-test.yml:52`
- 修复目标：`promotion.stack-order` 以列表配置承载，默认顺序为 `FULL_REDUCTION`、`COUPON`、`MEMBER_DISCOUNT`，促销计算服务按配置列表调度对应步骤。
- 修复方案：
  1. 新增 `PromotionProperties`（`@ConfigurationProperties(prefix = "promotion")`），字段 `List<String> stackOrder` 默认 `List.of("FULL_REDUCTION", "COUPON", "MEMBER_DISCOUNT")`；可定义 enum `PromotionStackStep` 提升类型安全。
  2. 在 `PromotionCalculationServiceImpl` 注入 `PromotionProperties`，将当前固定调用顺序替换为遍历 `stackOrder`：
     - `FULL_REDUCTION` 调用 `applyFullReduction(...)`
     - `COUPON` 调用 `calculateCouponDiscount(..., context.currentAmount())` 后 `applyCoupon(...)`
     - `MEMBER_DISCOUNT` 调用 `applyMemberDiscount(...)`
     - `TIER_PRICE` 当前不在附录B列表中，可保留为配置外固定尾部步骤，或仅当配置显式出现时调用；为严格对齐附录B，默认配置只包含前三项。
  3. 对未知 stack step 抛 `ValidationException` 不合适，因为这是启动配置错误而非 API 请求错误；建议启动时校验并抛 `IllegalStateException`，或忽略未知值并记录错误。验收以默认附录B顺序为主。
  4. R1 已负责在主/测试配置中添加 `promotion.stack-order`。
  5. POM 通常无需改动；只有需要配置元数据提示时添加可选 `spring-boot-configuration-processor`。
- 影响范围：促销金额计算顺序；默认顺序保持与当前前三步一致，减少行为变化。
- 注意事项/风险点：当前代码无论附录B如何都执行 `applyTierPrice`，但附录B默认 `stack-order` 未列 `TIER_PRICE`。为避免新增报告外问题，建议默认仍在前三步后保留现有 tier price 兜底且当前方法返回 0，不影响金额；后续若实现阶梯价，再单独评估。
- 建议验证方式：
  - 配置绑定测试断言默认列表顺序。
  - 促销计算测试在默认配置下结果与现有 `FULL_REDUCTION -> COUPON -> MEMBER_DISCOUNT` 顺序一致。
  - 自定义测试配置交换顺序后，断言服务按配置顺序调用并产生相应金额差异。

### R10. 统一跨模块配置属性启用、测试覆盖和 POM 处理原则

- 所属模块：跨 `ecommerce-cart`、`ecommerce-order`、`ecommerce-payment`、`ecommerce-logistics`、`ecommerce-loyalty`、`ecommerce-promotion`、`ecommerce-review`
- 覆盖问题：所有报告中涉及“配置绑定缺失/配置承载缺失/测试配置覆盖行为”的落地共性工作，具体问题仍分别由 R3-R9 处理。
- 检查报告定位：
  - `docs/appendix_B-check/ecommerce-cart-check.md:31`-`docs/appendix_B-check/ecommerce-cart-check.md:32`
  - `docs/appendix_B-check/ecommerce-order-check.md:31`-`docs/appendix_B-check/ecommerce-order-check.md:32`
  - `docs/appendix_B-check/ecommerce-payment-check.md:31`-`docs/appendix_B-check/ecommerce-payment-check.md:45`
  - `docs/appendix_B-check/ecommerce-logistics-check.md:28`-`docs/appendix_B-check/ecommerce-logistics-check.md:29`
  - `docs/appendix_B-check/ecommerce-loyalty-check.md:22`-`docs/appendix_B-check/ecommerce-loyalty-check.md:23`
  - `docs/appendix_B-check/ecommerce-promotion-check.md:33`-`docs/appendix_B-check/ecommerce-promotion-check.md:34`
  - `docs/appendix_B-check/ecommerce-review-check.md:22`-`docs/appendix_B-check/ecommerce-review-check.md:23`
- 设计依据定位：`design-docs/附录B-配置参考.md:3`-`design-docs/附录B-配置参考.md:81`；修改边界见 `README.md:25`-`README.md:40`
- 当前实现定位：多模块当前多数无模块内 `application.yml`/`application-test.yml`，统一由 `code/ecommerce-app/src/main/resources/application.yml` 和 `code/ecommerce-app/src/test/resources/application-test.yml` 承载；运行时配置查询入口为 `code/ecommerce-app/src/main/java/com/ecommerce/app/controller/SystemAdminController.java:35`-`code/ecommerce-app/src/main/java/com/ecommerce/app/controller/SystemAdminController.java:41`。
- 修复目标：每个业务配置项同时满足“配置文件承载”“配置属性绑定”“业务读取使用”“必要运行时覆盖”“测试 profile 不冲突”五类验收视角。
- 修复方案：
  1. 配置文件承载统一在 `ecommerce-app` 的 `application.yml` 与 `application-test.yml` 补齐（R1），子模块不必各自新增资源文件，除非其单元测试需要独立启动。
  2. 每个模块新增 `@ConfigurationProperties` 类后，必须确认被 Spring Boot 扫描：可在各模块已有 `*Config` 上 `@EnableConfigurationProperties(...)`，或在应用启动类集中 `@ConfigurationPropertiesScan`。优先局部启用，降低扫描范围变化风险。
  3. 业务读取优先顺序：运行时覆盖项使用 `RuntimeConfigRegistry.get*`，fallback 到 `@ConfigurationProperties` 当前值；非运行时覆盖项直接使用 `@ConfigurationProperties`。
  4. 测试覆盖逻辑：为每个属性类增加最小绑定测试；为关键业务点增加 `@TestPropertySource` 或 `application-test.yml` 场景，证明覆盖值能改变行为。
  5. POM：仅在需要生成配置元数据时添加可选 `spring-boot-configuration-processor`，不要为配置绑定引入不必要依赖；Spring Boot starter 已支持 `@ConfigurationProperties` 绑定。
- 影响范围：配置绑定基础设施和测试组织。
- 注意事项/风险点：不要修改冻结 REST API、不要把仅测试支撑的 `test.reset-enabled` 暴露成数据库 reset/bootstrap 接口，避免违反 `README.md:34`-`README.md:40`。
- 建议验证方式：
  - 全量编译测试：`mvn -f code/pom.xml test`。
  - 黑盒回归：`mvn -f code/pom.xml install -DskipTests`，再 `mvn -f test-cases/pom.xml test`。
  - 针对 R1-R9 的配置绑定和业务行为测试全部通过。
