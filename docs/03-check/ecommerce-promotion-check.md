# ecommerce-promotion 模块 03-通用规范与非功能设计检查报告

## 检查结论

### 一致

- Match：金额字段整体使用 `BigDecimal`，在已检查的 `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/**/*.java` 中未找到 `double` 或 `float` 表示促销、优惠券、折扣、满减、减免金额等金额字段或计算逻辑；金额实体字段如 `CouponTemplate.thresholdAmount/maxDiscount`、`FullReductionActivity.thresholdAmount/reductionAmount`、`SeckillActivity.seckillPrice` 使用 `BigDecimal` 并声明数据库 `scale = 2`（设计要求定位：`03-通用规范与非功能设计.md §1`）。
- Match：促销叠加计算在 `PromotionCalculationServiceImpl.StackingContext.cap` 中将每一步优惠裁剪到当前剩余金额，避免单步优惠金额大于当前商品/应付金额；`CouponService.calculateDiscount` 对固定金额券和门槛券也在部分分支将优惠裁剪到 `price`（设计要求定位：`03-通用规范与非功能设计.md §1`）。
- Match：promotion 模块业务代码使用项目通用异常类型，包括 `AuthorizationException`、`ValidationException`、`ConflictException`、`ResourceNotFoundException`、`BusinessException`；在已检查主代码范围内未找到 `IllegalArgumentException`（设计要求定位：`03-通用规范与非功能设计.md §2`）。
- Match：黑盒测试隔离方面，在已检查的 `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/**/*.java` 中未找到业务 REST API 暴露或依赖 `reset`/`bootstrap` 接口（设计要求定位：`03-通用规范与非功能设计.md §5`）。
- Match：通知规范方面，在 promotion 主代码中未找到直接调用 `MockMailSender` 或 `MockSmsSender` 的实现（设计要求定位：`03-通用规范与非功能设计.md §7`）。
- Match：模块依赖方面，`code/ecommerce-promotion/pom.xml` 依赖 `ecommerce-common`，为通用异常与金额工具的复用提供了模块依赖；根 `code/pom.xml` 已包含 `ecommerce-promotion` module。

### 不一致

- Mismatch：最终应付金额可能被计算为 `0.00`，不满足应付金额不得小于 `0.01`。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-promotion\src\main\java\com\ecommerce\promotion\service\PromotionCalculationServiceImpl.java:217-220`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-promotion\src\main\java\com\ecommerce\promotion\service\PromotionCalculationServiceImpl.java:243-250`。
  - 设计要求定位：`03-通用规范与非功能设计.md §1`，应付金额不得小于 `0.01`，0 元订单不在本系统支持范围内。
  - 不一致具体描述：`cap` 允许优惠等于 `currentAmount`，`applyCoupon` 等步骤会把 `currentAmount` 减到 `BigDecimal.ZERO`，`calculate` 随后将该值作为 `finalAmount` 返回。
  - 原因解析：实现只防止优惠超过剩余金额，但没有在促销计算结果层保留最低应付 `0.01`，也没有在产生 0 元结果时抛出校验异常或拒绝该优惠组合。

- Mismatch：金额最终入库前未在 promotion 服务层显式按 2 位小数、`HALF_UP` 统一归一化。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-promotion\src\main\java\com\ecommerce\promotion\service\CouponTemplateService.java:38-58`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-promotion\src\main\java\com\ecommerce\promotion\service\FullReductionService.java:38-51`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-promotion\src\main\java\com\ecommerce\promotion\service\SeckillService.java:30-37`。
  - 设计要求定位：`03-通用规范与非功能设计.md §1`，最终入库金额保留 2 位小数，舍入模式为 `RoundingMode.HALF_UP`。
  - 不一致具体描述：创建优惠券模板、满减活动、秒杀活动时直接把请求中的 `discountValue`、`thresholdAmount`、`maxDiscount`、`reductionAmount`、`seckillPrice` 等金额写入实体并保存；promotion 模块主代码搜索范围内未找到 `RoundingMode.HALF_UP` 或 `setScale(2, ...)`。
  - 原因解析：实体字段的 `scale = 2` 只声明数据库列尺度，不能体现业务层统一舍入策略；服务层没有在保存前调用金额工具或显式 `setScale(2, RoundingMode.HALF_UP)`，因此无法保证跨数据库/跨调用路径的一致入库规则。

- Mismatch：优惠金额非负与不大于商品金额的规则在创建端校验不完整。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-promotion\src\main\java\com\ecommerce\promotion\dto\CouponCreateRequest.java:22-27`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-promotion\src\main\java\com\ecommerce\promotion\dto\FullReductionCreateRequest.java:18-22`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-promotion\src\main\java\com\ecommerce\promotion\service\CouponTemplateService.java:69-79`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-promotion\src\main\java\com\ecommerce\promotion\service\FullReductionService.java:37-51`。
  - 设计要求定位：`03-通用规范与非功能设计.md §1`，优惠金额不得小于 0 且不得大于商品金额。
  - 不一致具体描述：`CouponCreateRequest` 与 `FullReductionCreateRequest` 的金额字段没有 `@Positive`/`@DecimalMin` 等非负约束；`CouponTemplateService.validateCreateRequest` 只校验部分必填字段，未校验优惠值非负、折扣券取值区间、满减金额与门槛/商品金额关系；`FullReductionService.create` 未校验 `reductionAmount` 非负或不超过适用金额。
  - 原因解析：计算阶段有部分裁剪逻辑，但管理端可以持久化负数或异常优惠规则，导致后续计算依赖兜底逻辑，不能从源头满足优惠金额边界规则。

- Mismatch：资源不存在场景使用了 `BusinessException`，未使用 `ResourceNotFoundException` 的 404 语义。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-promotion\src\main\java\com\ecommerce\promotion\service\CouponValidator.java:32-38`。
  - 设计要求定位：`03-通用规范与非功能设计.md §2`，`ResourceNotFoundException` 表示资源不存在，对应 HTTP 404；`BusinessException` 为通用业务异常，对应 HTTP 400。
  - 不一致具体描述：`validate(null)` 在 `userCoupon == null` 时抛出 `new BusinessException("RESOURCE_NOT_FOUND", "Coupon not found")`，而同一方法中模板不存在才使用 `ResourceNotFoundException`。
  - 原因解析：异常类型和错误码语义混用，会使资源不存在场景被映射为通用业务错误而非 404，破坏通用异常 HTTP 语义一致性。

- Mismatch：订单创建相关的优惠券使用标记不具备幂等语义，重复调用不会返回第一次处理结果。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-promotion\src\main\java\com\ecommerce\promotion\service\CouponUsageService.java:30-53`；已搜索范围：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/**/*.java`，未找到 `externalOrderNo`、幂等记录表或重复请求返回首次结果的实现。
  - 设计要求定位：`03-通用规范与非功能设计.md §3`，创建订单必须以 `externalOrderNo` 支持幂等，重复请求应返回第一次处理结果，不得重复扣款、扣库存、发积分或开票。
  - 不一致具体描述：promotion 模块提供给订单创建事务调用的 `markCouponsUsed(Long userId, Long orderId, List<Long> userCouponIds)` 只按 `orderId` 写入优惠券使用状态；首次调用后优惠券状态变为 `USED`，重复调用会再次执行 `couponValidator.validate(userCoupon)` 并因状态不是 `AVAILABLE` 抛业务异常，而不是识别同一订单/同一幂等键并返回第一次处理结果。
  - 原因解析：接口缺少 `externalOrderNo` 或等价幂等键，也没有判断 `usedOrderId` 已等于当前订单时直接视为成功；因此 promotion 侧的优惠券使用副作用无法配合创建订单幂等规范。

## 检查遗漏声明

- 未找到：配置文件 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-promotion\src\main\resources\*.yml`。已通过模块文件遍历确认该模块主资源目录下未发现 `.yml` 配置文件。
- 未找到：订单金额校验失败抛出 `OrderValidationException` 的对应实现。已搜索范围：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/**/*.java`；promotion 模块只发现促销金额计算与优惠券使用逻辑，未找到订单金额校验实现或 `OrderValidationException` 引用。
- 未找到：支付回调、退款申请、物流回调、发票申请相关 promotion 实现。已搜索范围：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/**/*.java`；这些接口类别不属于当前 promotion 主代码实现。
- 未找到：promotion 相关本地限流实现。已搜索范围：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/**/*.java`；未找到 `RateLimitException`、`RATE_LIMITED`、限流注解/过滤器/拦截器。设计列出的登录、支付回调、商品搜索、创建订单限流均未在 promotion 模块中实现。
- 未找到：审计日志设计列出的用户冻结/解冻、商品上下架、库存人工调整、订单取消审核、退款审核和仓库验收、发票开具、结算批次生成操作在 promotion 模块中的实现。已搜索范围：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/**/*.java`；promotion 管理端仅发现优惠券模板、满减、秒杀创建接口，不属于 §6 明确列出的审计操作。
- 未找到：promotion 模块发送通知的实现。已搜索范围：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/**/*.java`；未找到 `LocalNotificationService`、`NotificationRequest`、`MockMailSender`、`MockSmsSender` 引用。
- 未找到：promotion 模块本地事件监听器、事件失败记录、失败事件重放或支付成功后物流/积分/通知监听器实现。已搜索范围：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/**/*.java`；未找到 `Listener`、`Event`、本地事件处理表或失败记录保存逻辑。
