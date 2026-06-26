# 01-check 不一致点修复方案

> 范围：本文件仅汇总 `docs/01-check/` 下模块检查报告（不含本文件与 `checklist.md`）已报告的问题。设计依据来自 `README.md` 的冻结 API/错误码/修改边界，以及 `design-docs/01-项目概述.md` 的模块职责、业务原则、统一数据/响应格式和运行模式。  
> 约束：修复时不得修改 `README.md`、`design-docs/`、REST API URL/Method/Header、请求/响应字段、错误响应结构和 `/api/v1/` 前缀；不得扩展到报告外问题。

## 跨模块协调原则

1. **公共错误映射优先**：`USER_NOT_ACTIVE`、`USER_FROZEN`、`ORDER_STATUS_CONFLICT`、`REFUND_WAITING_WAREHOUSE_ACCEPT`、`REVIEW_PURCHASE_REQUIRED` 等业务码的 HTTP 状态应先在 common 层统一映射，避免各业务模块重复兜底。
2. **库存生命周期成组修复**：inventory 的预占、释放、支付后扣减语义必须联动修复；只改预占或只改释放都会造成库存口径不一致。
3. **促销有效性不得被订单/购物车吞掉**：promotion 抛出的优惠券过期等业务异常应向上保留；order/cart 不应静默降级为 0 折扣继续业务。
4. **后置动作事件化**：支付成功后的物流创建、积分发放、通知发送应通过 Spring ApplicationEvent 异步触发；失败不得阻塞或回滚支付主流程。
5. **模块边界**：payment、loyalty、logistics 等模块不得直接访问 order 表或 Repository；应通过本地接口、领域服务或事件协作。
6. **接口面收缩优先于新增 API**：凡报告指出未登记 REST 接口，应删除/下线映射；不得新增替代 reset/bootstrap 或其他非冻结接口。

---

## M1 common

### M1-1 BusinessException HTTP 状态码未覆盖冻结错误码表
- **问题来源**：`docs/01-check/common-check.md`；`GlobalExceptionHandler.handleBusiness(BusinessException)` 对普通 `BusinessException` 默认返回 400。
- **设计依据**：`README.md` 错误码表规定 `USER_NOT_ACTIVE`、`USER_FROZEN`、`REVIEW_PURCHASE_REQUIRED` 为 403，`ORDER_STATUS_CONFLICT`、`REFUND_WAITING_WAREHOUSE_ACCEPT` 为 409；统一错误体仍为 `code/message/traceId/details`。
- **实现位置**：`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java`；相关异常类 `BusinessException`、`ValidationException`、`ConflictException`。
- **修复方案**：
  1. 在 `GlobalExceptionHandler` 中新增按业务错误码解析 HTTP 状态的方法。
  2. 将上述 403/409 业务码显式映射；未特殊登记的 `BusinessException` 保持 400。
  3. 仅替换 `handleBusiness` 的状态选择逻辑，不改变 `ApiError` 结构、traceId、details。
- **影响范围**：所有抛 `BusinessException` 的模块；直接支撑 user/order/payment/review 的错误状态契约。
- **验证方式**：新增/调整全局异常处理测试；构造对应 code 的 `BusinessException`，断言 HTTP 与错误体结构。

---

## M2 user-service

### M2-1 用户侧接口未按冻结契约限制为 USER 角色
- **问题来源**：`docs/01-check/user-service-check.md`；安全配置仅对 `/api/v1/admin/**` 限制 ADMIN，其余认证请求兜底为 authenticated。
- **设计依据**：`README.md` 规定 `/api/v1/users/me`、地址增删改查接口认证为 USER；M2 职责包含账户状态、权限。
- **实现位置**：`code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java#securityFilterChain`；`JwtAuthFilter` 已将 JWT 角色映射为 `ROLE_*`。
- **修复方案**：
  1. 对 `/api/v1/users/me`、`/api/v1/users/addresses`、`/api/v1/users/addresses/**` 增加 `hasRole("USER")`。
  2. 保持注册、激活、登录匿名开放；保持 `/api/v1/admin/**` 为 ADMIN。
  3. 规则顺序置于 `.anyRequest().authenticated()` 前。
- **影响范围**：仅访问控制层；管理员 token 不再可访问 USER 专属接口。
- **验证方式**：USER token 正向通过；ADMIN token 访问用户侧接口返回 403；匿名返回 401；注册/登录仍匿名可用。

---

## M3 product-service

### M3-1 公开商品列表/搜索默认返回未上架商品
- **问题来源**：`docs/01-check/product-service-check.md`；默认搜索仅排除 DELETED，可能暴露 DRAFT/OFF_SHELF。
- **设计依据**：公开 `/api/v1/products`、`/api/v1/products/search` 应面向可售商品；M3 职责包含上下架、搜索；下单前需校验商品状态。
- **实现位置**：`ProductSearchRequest.onlyOnShelf`、`ProductSearchService.search/buildSpecification`、`ProductController.listProducts/searchProducts`。
- **修复方案**：
  1. 将公开搜索默认语义改为只返回 `SkuStatus.ON_SHELF`。
  2. DTO 默认值/Service Specification 均确保未显式传参时筛选 ON_SHELF。
  3. 不新增或改名 Query 参数，不改变分页响应结构。
- **影响范围**：商品公开浏览与搜索；cart/order 内部可售校验不依赖公开列表接口。
- **验证方式**：默认列表/搜索不含 DRAFT/OFF_SHELF；分页、品牌、类目、价格过滤仍可组合。

### M3-2 商品不可销售错误码未使用 PRODUCT_NOT_FOR_SALE
- **问题来源**：`product-service-check.md`；`ProductQueryServiceImpl.getSkuForSale` 对非上架 SKU 使用未冻结错误码。
- **设计依据**：`README.md` 规定商品不可销售错误码为 `PRODUCT_NOT_FOR_SALE`，HTTP 400。
- **实现位置**：`code/ecommerce-product/.../query/ProductQueryService.java#getSkuForSale`；`service/ProductQueryServiceImpl.java#getSkuForSale`。
- **修复方案**：
  1. 非 `ON_SHELF` 时抛 `BusinessException("PRODUCT_NOT_FOR_SALE", ...)`。
  2. SKU 不存在仍保持资源不存在语义。
  3. 同步 cart/order 相关测试或断言中的旧 code。
- **影响范围**：cart/order 通过 `ProductQueryService` 做前置校验时会透出标准业务码。
- **验证方式**：非上架 SKU 触发 `PRODUCT_NOT_FOR_SALE`；HTTP 400；错误结构不变。

---

## M4 inventory-service

### M4-1 库存预占阶段提前扣减 onHandStock
- **问题来源**：`inventory-service-check.md`；`reserve` 预占时同时减少现货。
- **设计依据**：项目概述关键原则：创建订单只预占库存，不扣减库存；支付成功后才扣减。
- **实现位置**：`InventoryReservationServiceImpl.reserve`。
- **修复方案**：预占仅增加 `reservedStock`，不修改 `onHandStock`；`availableStock` 通过 `onHand - reserved` 下降；保留按仓分摊与 `StockReservation` 落库。
- **影响范围**：订单创建、库存查询、库存校验。
- **验证方式**：`onHand=50,reserved=0` 预占 20 后为 `onHand=50,reserved=20,available=30`。

### M4-2 库存释放未与预占语义一致
- **问题来源**：`inventory-service-check.md`；`release` 与当前错误预占耦合。
- **设计依据**：M4 职责包含预占、释放、扣减；释放应撤销预占影响。
- **实现位置**：`InventoryReservationServiceImpl.release`。
- **修复方案**：释放仅减少 `reservedStock` 并将预占记录置为 `RELEASED`；不得修改 `onHandStock`；增加负数防护。
- **影响范围**：订单取消、超时取消后的可售恢复；必须与 M4-1 成组落地。
- **验证方式**：`onHand=50,reserved=20` 释放后为 `onHand=50,reserved=0,available=50`；重复释放不产生负数。

### M4-3 库存不足错误码未使用 INVENTORY_NOT_ENOUGH
- **问题来源**：`inventory-service-check.md`；预占/出库不足使用 `INSUFFICIENT_STOCK`。
- **设计依据**：`README.md` 错误码：`INVENTORY_NOT_ENOUGH`，HTTP 400。
- **实现位置**：`InventoryReservationServiceImpl.reserve`；`InventoryService.outbound`。
- **修复方案**：所有库存不足分支统一抛 `BusinessException("INVENTORY_NOT_ENOUGH", ...)`。
- **影响范围**：订单创建库存不足、管理端出库不足。
- **验证方式**：构造不足场景，REST 响应 `code=INVENTORY_NOT_ENOUGH`。

### M4-4 暴露未登记库存业务接口
- **问题来源**：`inventory-service-check.md`；存在 `GET /api/v1/admin/inventory/adjustments`、`POST /api/v1/admin/inventory/warnings/rule`。
- **设计依据**：`README.md` 6.3 仅冻结 `POST /adjustments` 与 `GET /warnings` 等接口，未登记上述接口。
- **实现位置**：`AdminInventoryController.listAdjustments`、`setWarningRule`。
- **修复方案**：删除/下线两个未登记 Controller 映射；服务层内部能力可保留；不要误删冻结接口。
- **影响范围**：仅收缩 REST 暴露面。
- **验证方式**：启动映射中不再存在两个未登记端点；冻结接口仍可访问。

---

## M5 cart-service

### M5-1 购物车主流程未使用 Caffeine + 7 天 TTL
- **问题来源**：`cart-service-check.md`；购物车仍以 JPA/H2 持久化为主。
- **设计依据**：项目概述关键原则：购物车是临时数据，存储在 Caffeine 本地缓存中，TTL 7 天。
- **实现位置**：`CartService`；复用 `CartCacheConfig`、`CartCacheManager`、`CartData`、`CartItemData`。
- **修复方案**：
  1. `addItem/getCart/updateItem/removeItem/clearCart` 主流程改为读写 `CartCacheManager`。
  2. 以 `userId` 为 key，缓存 miss 返回空购物车。
  3. 最后一个商品删除或清空时移除缓存。
  4. Repository/实体可暂保留但不再作为主流程依赖。
- **影响范围**：购物车 REST 行为不变，存储实现变化。
- **验证方式**：增删改查、清空、用户隔离；`PUB-006`、`PUB-007`。

### M5-2 购物车校验错误码未使用冻结码
- **问题来源**：`cart-service-check.md`；使用 `SKU_NOT_AVAILABLE`、`INSUFFICIENT_STOCK`。
- **设计依据**：商品不可售为 `PRODUCT_NOT_FOR_SALE`；库存不足为 `INVENTORY_NOT_ENOUGH`。
- **实现位置**：`CartValidationService.validateSku/validateStock`。
- **修复方案**：非可售 SKU 抛 `PRODUCT_NOT_FOR_SALE`；库存不足抛 `INVENTORY_NOT_ENOUGH`；估价阶段复用同样校验。
- **影响范围**：添加、修改、估价的错误语义。
- **验证方式**：下架/库存不足场景响应 code 与 HTTP 400。

### M5-3 估价忽略 couponIds/redeemPoints
- **问题来源**：`cart-service-check.md`；`/api/v1/cart/estimate` 接收但不使用优惠券和积分字段。
- **设计依据**：M5 负责价格预估；下单前必须校验促销有效性；积分抵扣受 10000 上限和订单金额 50% 上限。
- **实现位置**：`CartEstimateRequest`；`CartService.estimate`；`ecommerce-cart/pom.xml`；可复用 `PromotionCalculationService`、`LoyaltyQueryService`。
- **修复方案**：
  1. cart 模块增加 promotion/loyalty 依赖并注入服务。
  2. 用购物车项构造 `PromotionCalculateRequest`，传入 `couponIds`。
  3. 用 `LoyaltyQueryService` 估算可抵扣积分；实际抵扣取请求值与上限较小值。
  4. `payableAmount = itemTotal + shippingFee + packagingFee - discountAmount - pointsDeductionAmount`。
- **影响范围**：购物车估价、促销/积分模块协作；不改 DTO 字段。
- **验证方式**：优惠券折扣、积分抵扣、无优惠场景；`PUB-101`、`PUB-104` 相关金额口径。

### M5-4 估价阶段未重新校验商品可售与库存
- **问题来源**：`cart-service-check.md`；加入购物车后商品/库存变化，估价仍可能成功。
- **设计依据**：M5 职责含商品有效性预校验；下单前必须校验商品状态和库存。
- **实现位置**：`CartService.estimate`；复用 `CartValidationService`。
- **修复方案**：估价循环中逐项调用 `validateSku` 和 `validateStock`，并用最新 SKU 价格计算。
- **影响范围**：估价失败分支更早暴露标准错误码。
- **验证方式**：加入购物车后下架/降库存，估价分别返回 `PRODUCT_NOT_FOR_SALE`/`INVENTORY_NOT_ENOUGH`。

---

## M6 order-service

### M6-1 创建订单成功状态码应为 201
- **问题来源**：`order-service-check.md`；`POST /api/v1/orders/create` 当前返回 200。
- **设计依据**：`README.md` 订单 API 基线规定成功状态 201。
- **实现位置**：`OrderController.createOrder`。
- **修复方案**：返回 `ResponseEntity.status(HttpStatus.CREATED).body(response)`；不改响应体。
- **验证方式**：创建订单成功 HTTP 201，body.status 仍为 CREATED。

### M6-2 verify-purchase 应允许 USER/ADMIN
- **问题来源**：`order-service-check.md`；类级 USER 限制导致 ADMIN 被拒。
- **设计依据**：`README.md` 规定 `GET /api/v1/orders/verify-purchase` 认证为 USER/ADMIN。
- **实现位置**：`OrderController.verifyPurchase`。
- **修复方案**：方法级 `@PreAuthorize("hasRole('USER') or hasRole('ADMIN')")` 覆盖；其他用户接口仍 USER。
- **验证方式**：USER/ADMIN 均 200；匿名失败。

### M6-3 下单前缺少用户状态校验
- **问题来源**：`order-service-check.md`；只校验用户存在，不校验未激活/冻结。
- **设计依据**：下单前必须校验用户状态；错误码 `USER_NOT_ACTIVE`、`USER_FROZEN` 为 403。
- **实现位置**：`OrderPreconditionChecker.check`；`UserDto.status`；依赖 common M1-1。
- **修复方案**：在用户存在后判断状态；未激活抛 `USER_NOT_ACTIVE`，冻结抛 `USER_FROZEN`。
- **影响范围**：订单创建前置校验。
- **验证方式**：未激活/冻结用户下单返回 403 + 对应 code；ACTIVE 不受影响。

### M6-4 风控结果未参与下单决策
- **问题来源**：`order-service-check.md`；`OrderRiskChecker` 有能力但未接入。
- **设计依据**：下单前必须校验风控结果；`ORDER_RISK_REJECTED` HTTP 400。
- **实现位置**：`OrderService.createOrder`；`OrderRiskChecker`。
- **修复方案**：在订单落库和库存预占前调用风险校验；拒绝时抛 `ORDER_RISK_REJECTED`，中断后续流程。
- **验证方式**：高风险订单失败，无订单落库、无库存预占、无事件发布。

### M6-5 金额非法错误码不一致
- **问题来源**：`order-service-check.md`；金额非法抛 `IllegalArgumentException`。
- **设计依据**：`ORDER_INVALID_AMOUNT` HTTP 400；统一错误响应。
- **实现位置**：`OrderValidator.validateAmount`；`OrderValidationException`。
- **修复方案**：替换为 `OrderValidationException` 或 `BusinessException("ORDER_INVALID_AMOUNT", ...)` 且由 common 映射 400。
- **验证方式**：0/负数金额返回 400 + `ORDER_INVALID_AMOUNT`，不落 500。

### M6-6 订单状态冲突错误码不一致
- **问题来源**：`order-service-check.md`；状态机/取消流程使用多种未冻结 code。
- **设计依据**：订单状态不允许操作统一 `ORDER_STATUS_CONFLICT`，HTTP 409。
- **实现位置**：`OrderStateMachine.validateTransition`；`OrderCancelService`；依赖 common M1-1。
- **修复方案**：非法流转、重复取消、审核状态不匹配等统一抛 `ORDER_STATUS_CONFLICT`；保留 message 细分。
- **验证方式**：非法状态操作返回 409 + `ORDER_STATUS_CONFLICT`。

### M6-7 已支付订单取消应进入商家审核
- **问题来源**：`order-service-check.md`；PAID 订单用户取消被直接置为 CANCELLED。
- **设计依据**：已支付订单取消必须进入商家审核；存在 `POST /api/v1/admin/orders/{orderId}/cancel-review`。
- **实现位置**：`OrderCancelService.cancel/reviewCancel`；`OrderStateMachine`；`AdminOrderController`。
- **修复方案**：PAID 用户取消流转为 `CANCEL_REVIEWING`；管理员审核通过后转 `CANCELLED`，拒绝后回 `PAID`；移除 `PAID -> CANCELLED` 直接迁移。
- **影响范围**：取消、退款触发时点、订单状态查询。
- **验证方式**：PAID 取消后状态为 CANCEL_REVIEWING；审核通过/拒绝状态正确。

### M6-8 促销计算失败被静默降级
- **问题来源**：`order-service-check.md`；`calculateDiscounts` 捕获异常后返回 0。
- **设计依据**：下单前必须校验促销有效性；`COUPON_EXPIRED` HTTP 400。
- **实现位置**：`OrderService.calculateDiscounts`；`PromotionCalculationService`。
- **修复方案**：不要吞掉促销业务异常；过期券等原样向上抛；未知促销异常也应中断下单。
- **验证方式**：过期券下单返回 `COUPON_EXPIRED`；无订单落库和库存预占。

### M6-9 拆单职责未体现
- **问题来源**：`order-service-check.md`；M6 职责包含拆单，但创建流程始终单订单。
- **设计依据**：项目概述 M6 职责包含拆单；但冻结 `CreateOrderResponse` 只支持单个订单返回。
- **实现位置**：`OrderService.createOrder`；`Order`、`OrderItem`、Repository。
- **修复方案**：以不改变外部 API 为前提，先抽出内部订单分组/拆单策略服务；默认策略返回单组，保持现有响应。若后续必须真实多子单，应另行协调支付、库存、物流、统计口径。
- **影响范围**：本阶段建议作为内部扩展点，避免破坏冻结契约。
- **验证方式**：默认单组策略下现有创建订单行为完全不变；增加服务层策略测试。

---

## M7 payment-service

### M7-1 payment 直接访问订单表
- **问题来源**：`payment-service-check.md`；`PaymentService.queryOrderDirectly` 使用 JDBC 查 `orders` 表。
- **设计依据**：模块不得直接访问彼此数据库表；应通过 order 本地查询接口。
- **实现位置**：`PaymentService.pay/queryOrderDirectly`；`OrderQueryService.getPayableOrder/getOrder`。
- **修复方案**：改用 `OrderQueryService.getPayableOrder`；删除 `JdbcTemplate` 注入和 SQL 私有方法；测试改 mock 查询接口。
- **验证方式**：支付创建不再依赖 JDBC；订单不存在/不可支付由订单接口返回标准错误。

### M7-2 支付金额未强制等于订单应付金额
- **问题来源**：`payment-service-check.md`；只校验金额大于 0，回调金额还会覆盖 paidAmount。
- **设计依据**：支付金额必须等于订单应付金额；错误码 `PAYMENT_AMOUNT_MISMATCH`。
- **实现位置**：`PaymentValidator.validate`；`PaymentService.pay`；`PaymentCallbackService.processSuccessCallback`。
- **修复方案**：创建支付和成功回调均校验金额等于订单/支付单应付金额；不一致抛 `PAYMENT_AMOUNT_MISMATCH`；成功写入以内部应付金额为准。
- **验证方式**：少付/多付/回调金额不一致返回 400 + `PAYMENT_AMOUNT_MISMATCH`。

### M7-3 支付回调未实现签名认证
- **问题来源**：`payment-service-check.md`；`PaymentController.callback` 未校验签名。
- **设计依据**：`POST /api/v1/payment/callback` 认证为签名；API body 不可变。
- **实现位置**：`PaymentController.callback`；`PaymentCallbackService.processCallback`；配置 `PaymentConfig`/`application*.yml`。
- **修复方案**：读取 `X-Payment-Signature` header，使用配置密钥/测试有效签名校验；缺失或非法拒绝；验签通过后处理回调。
- **验证方式**：有效签名成功；缺失/非法签名不更新支付状态，返回统一错误体。

### M7-4 支付成功后置动作同步执行并阻塞主流程
- **问题来源**：`payment-service-check.md`；`confirmPayment` 同步创建物流、发积分、通知。
- **设计依据**：支付成功后置动作通过本地事件异步触发，不阻塞支付。
- **实现位置**：`PaymentService.confirmPayment/createLogistics/earnPoints/sendNotifications`；`PaymentSucceededEvent` 监听器。
- **修复方案**：`confirmPayment` 只更新支付主状态并发布事件；后置动作迁移到异步事件监听器，异常被记录但不回滚支付。
- **影响范围**：logistics/loyalty/notification 协作；依赖 app-bootstrap 异步能力。
- **验证方式**：故障注入后支付仍 SUCCESS；事件失败有记录。

### M7-5 退款审核通过后绕过仓库验收
- **问题来源**：`payment-service-check.md`；`approveRefund` 立即 `processRefund`。
- **设计依据**：退款必须经过商家审核和仓库验收；等待验收错误码 `REFUND_WAITING_WAREHOUSE_ACCEPT`。
- **实现位置**：`RefundService.reviewRefund/approveRefund/warehouseAccept/processRefund`；`RefundStatus`。
- **修复方案**：审核通过后状态转 `WAITING_WAREHOUSE_ACCEPT`，不退款；`warehouseAccept` 仅允许等待验收状态，验收后再退款完成。
- **验证方式**：审核后状态非 COMPLETED，仓库验收后才完成；未验收完成退款返回 409 + `REFUND_WAITING_WAREHOUSE_ACCEPT`。

### M7-6 发票忽略请求金额，不支持部分开票且可能超额
- **问题来源**：`payment-service-check.md`；`InvoiceService.generateInvoice` 固定使用支付全额。
- **设计依据**：发票支持部分开票，多张累计不得超过实付金额。
- **实现位置**：`InvoiceRequest.invoiceAmount`；`InvoiceService.generateInvoice`。
- **修复方案**：使用请求 `invoiceAmount`；校验非空大于 0；计算剩余额度，申请额不得超过剩余；税额和 remaining 基于本次金额。
- **验证方式**：部分开票、多次累计、超额申请、全额开票。

### M7-7 发票超额错误码不一致
- **问题来源**：`payment-service-check.md`；使用 `INVOICE_LIMIT_EXCEEDED`。
- **设计依据**：正确错误码为 `INVOICE_AMOUNT_EXCEEDED`。
- **实现位置**：`InvoiceService.generateInvoice`。
- **修复方案**：已完全开票和本次超过剩余均抛 `INVOICE_AMOUNT_EXCEEDED`。
- **验证方式**：超额场景返回 400 + `INVOICE_AMOUNT_EXCEEDED`。

---

## M8 promotion-service

### M8-1 USER 接口固定 userId=1
- **问题来源**：`promotion-service-check.md`；`PromotionController.extractUserId()` 固定返回 1。
- **设计依据**：领券、我的券、促销计算均为 USER 认证接口；应使用 JWT 当前用户。
- **实现位置**：`PromotionController.claimCoupon/getMyCoupons/calculate/extractUserId`。
- **修复方案**：从 `SecurityContextHolder`/`Authentication.getName()` 获取当前用户 ID；非法主体抛统一认证异常；`calculate` 未传 userId 时注入真实用户。
- **验证方式**：不同用户数据隔离；mock 认证主体断言 service 入参。

### M8-2 优惠券有效性校验缺失
- **问题来源**：`promotion-service-check.md`；过期/未生效/非 AVAILABLE 未被拒绝。
- **设计依据**：下单前必须校验促销有效性；错误码 `COUPON_EXPIRED`。
- **实现位置**：`CouponValidator.validate`；`CouponService.claim`；`PromotionCalculationService.calculateCouponDiscount`。
- **修复方案**：校验 `UserCoupon.status=AVAILABLE`、模板 `ACTIVE`、当前时间在 `[startTime,endTime]`；过期/未生效抛 `COUPON_EXPIRED`；领券也校验时间窗。
- **验证方式**：过期/未生效领券或计算返回 400 + `COUPON_EXPIRED`。

### M8-3 满减活动未校验有效时间
- **问题来源**：`promotion-service-check.md`；`FullReductionService` 只按 ACTIVE，不看时间。
- **设计依据**：促销有效性校验；实体已有 start/end。
- **实现位置**：`FullReductionService.listActive/calculateBestReduction`。
- **修复方案**：计算时仅纳入 ACTIVE 且当前时间位于活动窗口内的活动；算法仍取最大减免。
- **验证方式**：未开始/已结束不参与；多个有效活动取最大。

### M8-4 阶梯价职责未体现
- **问题来源**：`promotion-service-check.md`；无阶梯价规则入口。
- **设计依据**：M8 职责包含阶梯价；但冻结 DTO 无阶梯价字段。
- **实现位置**：`PromotionCalculationService`；可新增内部策略接口/规则类。
- **修复方案**：不改外部 DTO；抽出促销计算管线，为阶梯价增加内部计算挂载点，折扣并入 `totalDiscount/finalAmount`。若无规则来源，先作为内部扩展点，不新增 REST/API 字段。
- **验证方式**：现有接口字段不变；计算管线有可测试的阶梯价步骤或默认 no-op 策略。

### M8-5 叠加规则未明确建模
- **问题来源**：`promotion-service-check.md`；会员折扣/满减/多券硬编码叠加，多券可能无条件累加。
- **设计依据**：M8 职责包含叠加规则；外部只消费汇总结果。
- **实现位置**：`PromotionCalculationService.calculate/calculateCouponDiscount`。
- **修复方案**：将叠加顺序和多券策略集中建模；推荐默认单券最优或显式串行叠加，防止折扣超过商品金额；`applicableCoupons` 只输出实际生效券。
- **验证方式**：多券不无条件超额叠加；顺序稳定；order 消费汇总金额一致。

---

## M9 logistics-service

### M9-1 拣货接口空 pickerId 导致 500
- **问题来源**：`logistics-service-check.md`；控制器调用 `shipmentService.pick(id,null)`，服务层 `pickerId.toString()`。
- **设计依据**：`POST /api/v1/admin/logistics/shipments/{shipmentId}/pick` 无请求体，成功 200。
- **实现位置**：`AdminLogisticsController.pick`；`ShipmentService.pick`。
- **修复方案**：路径变量命名对齐 `shipmentId`；服务层允许 `pickerId == null`，轨迹 operator 使用 `SYSTEM/ADMIN`；不因日志拼接 NPE。
- **验证方式**：无请求体 pick 返回 200，状态进入 PICKING，轨迹新增。

### M9-2 物流回调未落实签名认证
- **问题来源**：`logistics-service-check.md`；`LogisticsCallbackService` 未验证 signature。
- **设计依据**：`POST /api/v1/logistics/callback` 认证为签名。
- **实现位置**：`LogisticsController.receiveCallback`；`LogisticsCallbackService.processCallback`；`LogisticsCallbackRequest.signature`。
- **修复方案**：保持 body 字段不变；在服务入口校验 signature 非空且匹配配置密钥/约定摘要；非法返回统一认证/权限错误。
- **验证方式**：有效签名成功；缺失/错误签名不更新状态。

### M9-3 物流回调未回写状态、签收时间与轨迹
- **问题来源**：`logistics-service-check.md`；回调只记录日志。
- **设计依据**：M9 职责含物流轨迹、签收；现有 `ShipmentService.updateStatus` 已能写状态/轨迹/订单物流状态。
- **实现位置**：`LogisticsCallbackService.processCallback/mapToShipmentStatus`；`ShipmentRepository`；`ShipmentService.updateStatus`。
- **修复方案**：新增 `ShipmentRepository.findByTrackingNo`；回调按 trackingNo 查 shipment，映射 status 后委托 `updateStatus`，传入 location/description；DELIVERED 写 deliveredAt。
- **影响范围**：订单物流状态、评价签收前提。
- **验证方式**：COLLECTED/IN_TRANSIT/DELIVERED 回调后查询状态/轨迹/签收时间；未知 trackingNo 返回统一 404。

### M9-4 支付成功后物流创建未通过异步事件触发
- **问题来源**：`logistics-service-check.md`；物流模块未监听支付/订单已支付事件创建运单。
- **设计依据**：支付成功后的物流创建应通过本地事件异步触发，不阻塞支付。
- **实现位置**：新增 logistics 事件监听器；复用 `ShipmentService.createShipment`；事件来源 `OrderPaidEvent`。
- **修复方案**：新增 `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` 监听 `OrderPaidEvent`；通过订单本地查询接口获取地址/运费；按 orderId 幂等创建 shipment；异常捕获记录。
- **影响范围**：logistics/order/payment 事件协作；依赖 async 配置。
- **验证方式**：支付成功后无需手动调用物流 API 即可异步查到 shipment；重复事件不重复创建；失败不回滚支付。

---

## M10 loyalty-service

### M10-1 积分发放事件处理非异步，可能阻塞支付
- **问题来源**：`loyalty-service-check.md`；`OrderPaidEventListener` 普通同步监听，app 未启用 async。
- **设计依据**：积分发放是支付后置动作，应本地事件异步触发，不阻塞支付。
- **实现位置**：`OrderPaidEventListener`；`ShopHubApplication` 或 app config。
- **修复方案**：监听改为 `@Async + @TransactionalEventListener(AFTER_COMMIT)`；app-bootstrap 启用 `@EnableAsync`；监听器捕获异常。
- **验证方式**：积分异常不影响支付成功；事件最终到账。

### M10-2 积分过期 API 仅记录日志
- **问题来源**：`loyalty-service-check.md`；`PointsExpireService` no-op。
- **设计依据**：M10 职责包含过期；冻结 `POST /api/v1/admin/loyalty/points/expire`。
- **实现位置**：`PointsExpireService`；`PointsTransactionRepository`；`LoyaltyAccountRepository`。
- **修复方案**：基于 `PointsTransaction.expiresAt` 找到到期未处理 EARN 流水，扣减账户 `availablePoints/expiredPoints/totalPoints`，记录 `EXPIRE` 流水，并保证幂等。
- **验证方式**：到期处理、未到期不处理、重复触发不重复扣减；配合测试时钟。

### M10-3 积分冻结职责未实现
- **问题来源**：`loyalty-service-check.md`；有 `frozenPoints` 字段但无冻结/解冻/核销业务。
- **设计依据**：M10 职责包含积分冻结。
- **实现位置**：`LoyaltyPointService`；`LoyaltyCommandService`；`PointsTransactionType`。
- **修复方案**：增加内部领域能力 `freezePoints/unfreezePoints/consumeFrozenPoints`；冻结扣 available 加 frozen，解冻反向，核销扣 frozen 并增加 redeemed；可增加内部流水类型，不新增 REST API。
- **验证方式**：冻结、解冻、核销、超额失败、幂等。

### M10-4 会员权益职责未实现
- **问题来源**：`loyalty-service-check.md`；仅有等级 multiplier，未建模权益。
- **设计依据**：M10 职责包含会员等级、权益。
- **实现位置**：`MemberLevelService`；`MemberLevel`；可新增权益定义/解析服务。
- **修复方案**：在模块内将权益与等级解耦建模，提供权益查询/解析服务；对外 `member-level` 响应字段不变，现有 multiplier 来源改为权益服务。
- **验证方式**：不同等级映射权益正确；现有响应字段兼容。

### M10-5 loyalty 直接查询订单表
- **问题来源**：`loyalty-service-check.md`；`OrderDataFetcher` 用 JDBC 查 orders。
- **设计依据**：模块不得直接访问彼此表/Repository。
- **实现位置**：`OrderDataFetcher`；`MemberLevelService`；order 模块提供查询实现。
- **修复方案**：删除 JDBC 直查；在 loyalty 定义年消费查询端口，由 order 模块实现；`MemberLevelService` 注入抽象接口。
- **影响范围**：loyalty/order Maven 依赖与 Spring Bean 装配。
- **验证方式**：loyalty 无 orders SQL；会员等级评估仍能取得年消费。

### M10-6 积分抵扣预估缺少参数校验
- **问题来源**：`loyalty-service-check.md`；`PointsEstimateRequest` 缺 Bean Validation。
- **设计依据**：参数校验失败应 `VALIDATION_FAILED`/400，统一错误结构。
- **实现位置**：`PointsEstimateRequest`；`LoyaltyController.estimateRedeem`。
- **修复方案**：`orderAmount` 加 `@NotNull`/正数约束，`redeemPoints` 非负；控制器入参加 `@Valid`。
- **验证方式**：空/非正金额、负积分返回 400 + `VALIDATION_FAILED`；正常计算不变。

---

## M11 review-service

### M11-1 创建评价未校验已购买且订单已签收
- **问题来源**：`review-service-check.md`；`ReviewService.createReview` 信任请求中的 order/product 字段。
- **设计依据**：评价必须校验用户已购买且订单已签收；模块间通过公开接口协作。
- **实现位置**：`ReviewService.createReview`；复用 `OrderQueryService.verifyPurchase`。
- **修复方案**：创建前调用订单购买验证；未购买/未签收或 orderId 不匹配时拒绝；不直接查订单表。
- **验证方式**：已签收可评；未购买、未签收、伪造订单失败。

### M11-2 REVIEW_PURCHASE_REQUIRED 错误码/HTTP 状态缺失
- **问题来源**：`review-service-check.md`；未返回 `REVIEW_PURCHASE_REQUIRED`，common 默认 BusinessException=400。
- **设计依据**：`REVIEW_PURCHASE_REQUIRED` HTTP 403。
- **实现位置**：`ReviewService.createReview`；依赖 common M1-1。
- **修复方案**：评价准入失败抛 `BusinessException("REVIEW_PURCHASE_REQUIRED", ...)`；common 映射 403。
- **验证方式**：未购买/未签收评价返回 403 + `REVIEW_PURCHASE_REQUIRED`。

### M11-3 评价分页响应多出非统一字段
- **问题来源**：`review-service-check.md`；`ReviewListResponse` 多 `averageRating`、`totalReviews`。
- **设计依据**：统一分页结构仅 `page/size/total/items`；响应字段冻结。
- **实现位置**：`ReviewListResponse`；`ReviewService.getProductReviews/getMyReviews`；`PageResponse`。
- **修复方案**：删除/停止序列化额外字段；移除服务层赋值和无用平均分计算；控制器路径不变。
- **验证方式**：商品评价/我的评价响应仅含统一分页字段。

---

## M12 app-bootstrap

### M12-1 移除 reset-sandbox 接口
- **问题来源**：`app-bootstrap-check.md`；暴露 `POST /api/v1/admin/system/reset-sandbox`。
- **设计依据**：README 禁止暴露数据库 reset/bootstrap 接口；测试隔离由 harness 新上下文和随机 H2 提供；冻结 API 未登记。
- **实现位置**：`SystemAdminController.resetSandbox/deletePriority` 及专用注入/import。
- **修复方案**：删除 reset 映射和仅为其服务的清理逻辑/import/依赖；保留已冻结 configs/clock 等接口。
- **验证方式**：该路径不再注册；访问 404/未映射；冻结管理接口仍可用。

### M12-2 移除 bootstrap-admin 接口和直接发 token 逻辑
- **问题来源**：`app-bootstrap-check.md`；暴露 `POST /api/v1/admin/system/bootstrap-admin` 创建固定管理员并返回 token。
- **设计依据**：管理员由 harness seed，再通过登录接口获取 token；冻结 API 未登记 bootstrap 接口。
- **实现位置**：`SystemAdminController.bootstrapAdmin`；`UserRepository`、`PasswordEncoder`、`JwtTokenProvider` 等专用依赖。
- **修复方案**：删除 bootstrap 映射、固定管理员常量、专用注入/import；不新增替代 REST 接口。
- **验证方式**：该路径不再返回 token；管理员仍可通过 seed+`/api/v1/users/login` 获取 token。

### M12-3 收紧 /api/v1/admin/** 认证规则
- **问题来源**：`app-bootstrap-check.md`；reset/bootstrap 被 `permitAll()`。
- **设计依据**：黑盒测试支撑管理接口均需 ADMIN 认证；`/api/v1/admin/` 下管理接口均 ADMIN。
- **实现位置**：`code/ecommerce-app/src/main/java/com/ecommerce/app/SecurityConfig.java#securityFilterChain`。
- **修复方案**：删除 reset/bootstrap 的匿名放行规则；保留 `/api/v1/admin/**.hasRole("ADMIN")`；不改变其他匿名业务路径。
- **验证方式**：admin configs/clock/fault-injections 匿名或 USER 失败，ADMIN 成功；删除路径不命中逻辑。

### M12-4 管理支撑接口错误体未统一
- **问题来源**：`app-bootstrap-check.md`；部分接口返回 `{error:...}` 或 404 空 body。
- **设计依据**：统一错误体为 `code/message/traceId/details`；通用错误码 `VALIDATION_FAILED`、`RESOURCE_NOT_FOUND`。
- **实现位置**：`FaultInjectionAdminController.injectFault`；`SystemAdminController.putConfig/getConfig/setClock`；复用 `GlobalExceptionHandler`。
- **修复方案**：移除手写 error map/空 404；参数缺失/格式非法抛 `ValidationException`；配置不存在抛 `ResourceNotFoundException`；details 放字段/key 信息；成功响应不变。
- **验证方式**：缺 fault/value、非法 clock、配置不存在分别返回统一错误结构与正确 code/status。
