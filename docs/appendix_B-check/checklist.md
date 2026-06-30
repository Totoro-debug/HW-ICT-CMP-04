# 附录B 修复 TODO Checklist

- [ ] TODO | ecommerce-app | `invoice.max-title-length` 配置项缺失 | 对应方案：R1 | 修改摘要：在 `code/ecommerce-app/src/main/resources/application.yml` 与 `code/ecommerce-app/src/test/resources/application-test.yml` 的 `invoice` 层级新增 `max-title-length: 100`。 | 验证要点：配置加载测试可读取 `invoice.max-title-length=100`；发票模块 R5 的标题长度校验使用该值。
- [ ] TODO | ecommerce-app | `promotion.stack-order` 配置项缺失 | 对应方案：R1、R9 | 修改摘要：在主配置和测试配置新增 `promotion.stack-order`，顺序为 `FULL_REDUCTION`、`COUPON`、`MEMBER_DISCOUNT`；促销模块按该列表绑定读取。 | 验证要点：`Environment`/`PromotionProperties` 中列表顺序与 `design-docs/附录B-配置参考.md:55`-`59` 一致；促销计算默认顺序不再依赖纯硬编码。
- [ ] TODO | ecommerce-app | `logistics.default-carrier` 与 `logistics.free-shipping-threshold` 配置项缺失 | 对应方案：R1、R6 | 修改摘要：在主配置和测试配置的 `logistics` 下保留 `callback-secret`，新增 `default-carrier: LOCAL_EXPRESS`、`free-shipping-threshold: 199.00`。 | 验证要点：`LogisticsProperties` 可绑定两个新增键；打印面单默认 carrier 为 `LOCAL_EXPRESS`，默认包邮阈值为 `199.00`。
- [ ] TODO | ecommerce-app | `test.reset-enabled` 配置项缺失 | 对应方案：R1、R10 | 修改摘要：在主配置和测试配置新增 `test.reset-enabled: false`，仅作为配置项承载，不新增 reset/bootstrap 接口。 | 验证要点：配置可读取且默认 false；不出现违反 `README.md:34`-`40` 的数据库 reset/bootstrap API。
- [ ] TODO | ecommerce-app | 测试 profile 中 `security.jwt` 示例值与附录B不一致 | 对应方案：R1 | 修改摘要：将 `code/ecommerce-app/src/test/resources/application-test.yml` 中 `security.jwt.issuer`、`secret`、`expire-minutes` 调整为 `shophub`、`local-development-secret-change-me`、`120`。 | 验证要点：测试 profile 下登录签发与认证校验使用同一配置；公开黑盒用户登录/ADMIN 认证链路仍通过。

- [ ] TODO | ecommerce-common | 运行时配置默认值注册不完整 | 对应方案：R2 | 修改摘要：在 `RuntimeConfigRegistry.defaults` 补齐 `order.expire-minutes`、`order.max-items`、`payment.refund-fee-rate`、`cart.ttl-days`、`loyalty.max-redeem-points-per-order`、`loyalty.max-redeem-ratio`，保留既有必要键；超过 10 项时改用 `Map.ofEntries` 等安全初始化方式。 | 验证要点：`GET /api/v1/admin/system/configs/{key}` 对附录B默认值表所有键无覆盖时返回默认值，`PUT` 后返回覆盖值；`mvn -f code/pom.xml test` 编译通过。

- [ ] TODO | ecommerce-cart | `cart.ttl-days` 与 `cart.max-items` 未绑定、TTL 和最大商品种类硬编码 | 对应方案：R3 | 修改摘要：新增 `CartProperties` 绑定 `cart.ttl-days=7`、`cart.max-items=100`；`CartCacheConfig` 用配置创建 Caffeine TTL；`CartValidationService` 用配置校验最大商品种类并更新错误消息。 | 验证要点：`@TestPropertySource(cart.ttl-days=1, cart.max-items=2)` 下 cache TTL 创建无误，`validateCartSize(2,1)` 返回 `VALIDATION_FAILED`。

- [ ] TODO | ecommerce-logistics | `logistics` 配置项未绑定且默认承运商为 `DEFAULT` | 对应方案：R6 | 修改摘要：新增 `LogisticsProperties` 绑定 `default-carrier=LOCAL_EXPRESS`、`free-shipping-threshold=199.00`；`AdminLogisticsController.printLabel` 使用配置承运商；`FreightCalculator` 和 `FreightTemplateService` 使用配置包邮阈值。 | 验证要点：打印面单默认 carrier 为 `LOCAL_EXPRESS`；自定义 `logistics.free-shipping-threshold` 能影响默认运费和模板缺省值。

- [ ] TODO | ecommerce-loyalty | `loyalty.*` 配置项未绑定，硬编码/非附录B键名替代 | 对应方案：R7 | 修改摘要：新增 `LoyaltyProperties` 绑定 `points-per-yuan`、`redeem-rate`、`max-redeem-points-per-order`、`max-redeem-ratio`、`expire-months`、`review-reward-points`；积分发放、抵扣上限、过期时间、评价奖励均改为读取配置；运行时覆盖至少支持默认值表中的两个 loyalty 键。 | 验证要点：配置覆盖 `points-per-yuan` 改变支付积分发放；覆盖 `loyalty.max-redeem-ratio`/`max-redeem-points-per-order` 改变抵扣估算；评价审核只按配置奖励一次积分。

- [ ] TODO | ecommerce-order | `order.*` 未绑定，过期时间、最大项数、包装费、包邮阈值硬编码/未生效 | 对应方案：R4 | 修改摘要：新增 `OrderProperties` 绑定 `expire-minutes=60`、`max-items=30`、`packaging-fee=2.00`、`free-shipping-threshold=199.00`；`OrderService` 用配置设置 `expiresAt`；`OrderPreconditionChecker` 校验最大商品种类；`OrderTotalCalculator` 用配置计算包邮和包装费。 | 验证要点：31 个商品种类下单返回 `VALIDATION_FAILED`，30 个通过；包装费按配置 `2.00 × 商品种类数`；订单过期时间随配置分钟数变化。

- [ ] TODO | ecommerce-payment | `payment` 配置绑定缺少 `retry-times` 和 `callback-timeout-seconds` | 对应方案：R5 | 修改摘要：扩展 `PaymentConfig`，新增 `retryTimes=5`、`callbackTimeoutSeconds=5` getter/setter；支付回调/失败重试相关内部逻辑读取配置，必要时通过 `RuntimeConfigRegistry.getInt("payment.retry-times", ...)` 支持运行时覆盖。 | 验证要点：配置属性绑定测试通过；支付回调成功链路保持 HTTP 200 和原响应契约；覆盖 `payment.retry-times` 后读取值变化。
- [ ] TODO | ecommerce-payment | `invoice.max-title-length` 未绑定且未用于发票抬头长度约束 | 对应方案：R5 | 修改摘要：新增 `InvoiceProperties` 或等效绑定，包含 `tax-rate=0.06`、`max-title-length=100`；`InvoiceService` 使用配置税率 fallback，并在保存前校验 `invoiceTitle` 长度，超长抛 `ValidationException`。 | 验证要点：标题长度 100 可开票，101 返回 400 / `VALIDATION_FAILED`；`invoice.tax-rate` 运行时覆盖仍生效。

- [ ] TODO | ecommerce-promotion | `promotion.stack-order` 缺失配置承载与绑定，促销叠加顺序硬编码 | 对应方案：R9 | 修改摘要：新增 `PromotionProperties.stackOrder` 默认 `FULL_REDUCTION`、`COUPON`、`MEMBER_DISCOUNT`；`PromotionCalculationServiceImpl` 遍历配置列表调度满减、优惠券、会员折扣步骤，保留现有 API 和默认金额行为。 | 验证要点：默认配置下结果与附录B顺序一致；测试配置调整顺序后服务按配置执行；未知配置值不会破坏 REST 错误响应契约。

- [ ] TODO | ecommerce-review | 评价奖励积分在 review 模块中硬编码，未绑定 `loyalty.review-reward-points` | 对应方案：R8 | 修改摘要：将 review 模块遗留 `ReviewApprovedEventListener` 的 `REVIEW_REWARD_POINTS=20` 替换为实例配置值，绑定 `loyalty.review-reward-points`，默认 20；不要因修复将遗留类注册成第二个实际发放监听器。 | 验证要点：配置为 30 时该类日志/调用使用 30；评价审核链路只发放一次积分，主路径与 R7 的 loyalty 监听器一致。
