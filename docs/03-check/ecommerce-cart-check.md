# ecommerce-cart 模块 03-通用规范与非功能设计检查报告

## 检查结论

### 一致

- Match：金额字段类型使用 `BigDecimal`。在已搜索范围 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/**/*.java` 中，购物车价格、行小计、总金额、运费、包装费、优惠金额、积分抵扣金额、应付金额均使用 `BigDecimal`，未发现 `double` 或 `float` 表示金额；代表位置：`CartItemData.java:13`、`CartItemResponse.java:12-14`、`CartResponse.java:13`、`CartEstimateResponse.java:10-15`、`CartService.java:163-200`。
- Match：通用异常类型基本沿用公共异常体系。购物车业务校验使用 `BusinessException`、资源缺失使用 `ResourceNotFoundException`、认证主体解析失败使用 `AuthorizationException`，符合通用异常分类；代表位置：`CartValidationService.java:41-51`、`CartValidationService.java:62-69`、`CartService.java:225-239`、`CartController.java:114-122`。
- Match：幂等规范中列出的创建订单、支付回调、退款申请、物流回调、发票申请接口未在 cart 模块实现；购物车 `add/update/remove/clear/estimate` 未发现扣款、扣库存、发积分、开票等提交型副作用，库存仅查询校验；代表位置：`CartService.java:66-88`、`CartService.java:109-121`、`CartValidationService.java:62-70`。
- Match：本地限流规范列出的登录、支付回调、商品搜索、创建订单接口未在 cart 模块实现；未发现 cart 模块声明这些接口类别，故本模块未触发该设计项的直接实现要求。
- Match：黑盒测试隔离方面，`CartController` 仅暴露 `/api/v1/cart` 正式购物车接口，未发现 reset/bootstrap 业务 REST API；代表位置：`CartController.java:29-107`。配置文件搜索范围 `code/ecommerce-cart/src/main/resources/*.yml` 不存在，未发现通过配置暴露 reset/bootstrap。
- Match：审计日志规范列出的用户冻结/解冻、商品上下架、库存人工调整、订单取消审核、退款审核和仓库验收、发票开具、结算批次生成均未在 cart 模块实现；未发现 cart 模块承担这些审计必记操作。
- Match：通知规范方面，已搜索范围内未发现 `MockMailSender`、`MockSmsSender` 直接调用，也未发现 cart 模块发送通知；因此不存在绕过 `LocalNotificationService` 的直接通知调用。
- Match：本地事件失败处理方面，已搜索范围内未发现 cart 模块事件监听器；未发现支付成功后的物流、积分、通知监听器由 cart 模块实现，因此本模块无对应失败处理实现点。

### 不一致

- Mismatch：金额中间计算存在提前截断/舍入风险。
  - 代码定位：`code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:174-175` 使用 `MonetaryUtil.multiply` 和 `MonetaryUtil.add` 累加商品行金额；`CartService.java:190-200` 多次使用 `MonetaryUtil.add/subtract` 计算优惠前后金额和应付金额；`CartService.java:256-258` 使用 `MonetaryUtil.multiply` 计算行小计。已搜索范围：`code/ecommerce-cart/src/main/java/com/ecommerce/cart/**/*.java`。
  - 设计要求定位：`03-通用规范与非功能设计.md §1 金额计算`，要求“中间计算保留足够精度，不提前截断”，最终入库保留 2 位小数，舍入模式为 `RoundingMode.HALF_UP`。
  - 不一致具体描述：cart 模块在行小计、商品总额、优惠后金额、积分抵扣后金额等中间步骤即调用金额工具方法，存在每一步都按工具方法进行分位舍入/截断的风险；这不符合“中间计算不提前截断”的要求。并且 cart 模块未在自身代码中显式保证最终使用 `RoundingMode.HALF_UP`。
  - 原因解析：`CartService` 将中间计算拆成多次工具方法调用，缺少“内部高精度累计、最终结果统一按 HALF_UP 到分”的边界控制，导致折扣、积分、运费叠加时可能因多次中间舍入产生偏差。

- Mismatch：优惠金额未限制在 `[0, 商品金额]` 范围内。
  - 代码定位：`code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:190` 取得 `discountAmount`；`CartService.java:276-285` 直接返回促销模块的 `totalDiscount` 或 `BigDecimal.ZERO`；`CartService.java:209` 将该值写入响应。已搜索范围：`code/ecommerce-cart/src/main/java/com/ecommerce/cart/**/*.java`。
  - 设计要求定位：`03-通用规范与非功能设计.md §1 金额计算`，要求“优惠金额不得小于 0，不得大于商品金额”。
  - 不一致具体描述：cart 模块没有对促销返回的 `discountAmount` 做下限 0 和上限 `itemTotal` 校验或裁剪；若促销服务返回负数或大于商品总额的优惠，响应中的 `discountAmount` 会直接暴露不符合规范的金额。
  - 原因解析：`calculateDiscountAmount` 信任外部促销计算结果，仅处理 `null`，没有在 cart 估价聚合层执行金额边界防御，因此无法保证通用金额边界规范在本模块成立。

- Mismatch：应付金额可能为 0，未保证不小于 0.01。
  - 代码定位：`code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:158-161` 空购物车直接返回空估价；`CartService.java:200-203` 应付金额小于 0 时被置为 `BigDecimal.ZERO`；`CartService.java:265-273` 空估价响应将 `payableAmount` 设置为 `BigDecimal.ZERO`。已搜索范围：`code/ecommerce-cart/src/main/java/com/ecommerce/cart/**/*.java`。
  - 设计要求定位：`03-通用规范与非功能设计.md §1 金额计算`，要求“应付金额不得小于 0.01，0 元订单不在本系统支持范围内”。
  - 不一致具体描述：cart 估价接口可返回 `payableAmount = 0`，包括空购物车估价以及优惠/积分抵扣后金额小于或等于 0 的场景，未抛出业务异常或将流程阻断。
  - 原因解析：当前实现将负数应付金额归零，并将空购物车视为合法 0 元估价；缺少对“本系统不支持 0 元订单/应付金额至少 0.01”的统一校验。虽然 cart 不是订单提交模块，但该接口输出了应付金额，属于金额计算结果，仍需遵守通用金额边界。

## 检查遗漏声明

- 配置文件：未找到 `code/ecommerce-cart/src/main/resources/*.yml`；已检查 `code/ecommerce-cart/src/main/resources`，目录不存在。
- 订单金额校验失败的 `OrderValidationException`：未找到对应实现；已搜索范围为 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/**/*.java`。cart 模块未实现订单金额校验逻辑。
- 创建订单幂等键 `externalOrderNo`：未找到对应实现；已搜索范围为 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/**/*.java`。cart 模块未实现创建订单接口。
- 支付回调幂等键 `paymentNo + callbackSequence`：未找到对应实现；已搜索范围为 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/**/*.java`。cart 模块未实现支付回调接口。
- 退款申请幂等键 `refundRequestNo`：未找到对应实现；已搜索范围为 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/**/*.java`。cart 模块未实现退款申请接口。
- 物流回调幂等键 `trackingNo + eventTime + status`：未找到对应实现；已搜索范围为 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/**/*.java`。cart 模块未实现物流回调接口。
- 发票申请幂等键 `invoiceRequestNo`：未找到对应实现；已搜索范围为 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/**/*.java`。cart 模块未实现发票申请接口。
- 本地限流：未找到登录、支付回调、商品搜索、创建订单接口的限流实现；已搜索范围为 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/**/*.java`。这些接口类别不属于已发现的 cart 模块接口。
- reset/bootstrap：未找到 reset/bootstrap 接口或配置；已搜索范围为 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/**/*.java` 与 `code/ecommerce-cart/src/main/resources/*.yml`（资源目录不存在）。
- 审计日志：未找到用户冻结和解冻、商品上下架、库存人工调整、订单取消审核、退款审核和仓库验收、发票开具、结算批次生成实现；已搜索范围为 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/**/*.java`。这些审计场景不属于已发现的 cart 模块职责。
- 通知发送：未找到 `LocalNotificationService`、`NotificationRequest`、`MockMailSender`、`MockSmsSender` 调用；已搜索范围为 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/**/*.java`。cart 模块未实现通知发送。
- 本地事件监听器及失败处理：未找到事件监听器、事件失败日志、本地事件处理失败表写入或失败事件重放管理接口；已搜索范围为 `code/ecommerce-cart/src/main/java/com/ecommerce/cart/**/*.java`。cart 模块未实现相关监听器。
