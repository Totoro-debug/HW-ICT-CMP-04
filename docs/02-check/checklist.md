# 02-check 一致性修复 Checklist

## 总览摘要

共检查了 12 个模块，发现 58 处不一致，已修复 0 处。

- Pending：57 处
- Done：0 处
- Blocked：1 处
- 说明：本次任务按要求仅输出修复方案，不修改源代码；`EIN-001` 因 `/design-docs/02-系统架构.md` 内部对 product/inventory 依赖方向存在冲突，需先消歧后实施。

## 明细

| 序号 | 所属模块 | 设计要求定位 | 不一致描述 | 修复方案概要 | 状态 | 备注 |
|---:|---|---|---|---|---|---|
| 1 | ecommerce-product | `design-docs/02-系统架构.md` §2、§3、§4 | 商品详情库存摘要由 `StockInfoFetcher` 本地硬编码返回，未通过 inventory 提供的 `InventoryQueryService` 查询。 | `ProductDetailService` 改为注入并调用 inventory 的 `InventoryQueryService#getStockSummary(Long skuId)`；移除或停用硬编码 `StockInfoFetcher`；联动修正 inventory DTO 契约。 | Pending | 对应 `EP-001`。 |
| 2 | ecommerce-product | `design-docs/02-系统架构.md` §7 | 商品详情未按 `product:detail:{skuId}`、TTL 10 分钟建立缓存。 | 为 `ProductDetailService#getProductDetail(Long skuId)` 增加商品详情缓存，并在 SKU/SPU 变更、上下架时清理或更新缓存。 | Pending | 对应 `EP-002`。 |
| 3 | ecommerce-product | `design-docs/02-系统架构.md` §3；`README.md` §6 | 管理创建 SPU/SKU 接口 Response 直接暴露 JPA Entity。 | 新增或复用 SPU/SKU 响应 DTO，`AdminProductController#createSpu/createSku` 返回 DTO，并保持 API 冻结契约不变。 | Pending | 对应 `EP-003`。 |
| 4 | ecommerce-product | `README.md` §6.2、§7.2 | 公开商品详情 API 未对非 `ON_SHELF` SKU 返回 `PRODUCT_NOT_FOR_SALE`。 | 在 `ProductDetailService#getProductDetail` 查询 SKU 后校验状态，非上架时抛 `BusinessException("PRODUCT_NOT_FOR_SALE", ...)`。 | Pending | 对应 `EP-004`。 |
| 5 | ecommerce-common | `design-docs/02-系统架构.md` §5 | common notification 缺少对注册、下单、支付、退款等核心事件的通知监听适配。 | 在 common notification 新增事件监听适配类，监听相关领域事件并调用 `LocalNotificationService`，异常只记录不外抛；避免 common 反向强依赖业务模块。 | Pending | 对应 `EC-COMMON-001`。 |
| 6 | ecommerce-common | `design-docs/02-系统架构.md` §5、§6 | `notification-send-failure` 故障注入在 `LocalNotificationServiceImpl.send()` 内部 try/catch 前直接抛出，可能影响主流程。 | 将故障注入检查纳入失败处理范围，或记录失败后返回；通知监听器也需兜底捕获异常。 | Pending | 对应 `EC-COMMON-002`。 |
| 7 | ecommerce-common | `design-docs/02-系统架构.md` §8；`README.md` §6.8 | common 拥有测试支撑能力但自身未暴露对应 REST 管理接口，当前访问层在 app。 | 若验收要求 common 承担访问层，则在 common 新增管理 Controller；若保留 app 入口，则确认扫描和契约一致，避免重复路由。 | Pending | 对应 `EC-COMMON-003`。 |
| 8 | ecommerce-user | `design-docs/02-系统架构.md` §1、§3、§5 | 用户注册在事务内同步发送欢迎通知，未发布 `UserRegisteredEvent`，通知失败可能回滚注册。 | 新增 `UserRegisteredEvent`，注册服务改为发布事件；由监听器发送通知并捕获异常记录日志/失败记录。 | Pending | 对应 `EU-001`。 |
| 9 | ecommerce-user | `design-docs/02-系统架构.md` §7 | 用户权限缓存 `user:roles:{userId}`、TTL 30 分钟未实现。 | 新增角色缓存配置/管理组件；登录和鉴权读写该缓存；冻结/解冻或角色变更时失效/刷新。 | Pending | 对应 `EU-002`。 |
| 10 | ecommerce-inventory | `design-docs/02-系统架构.md` §2、§3、§4 | inventory 依赖 product 符合 §4 `ProductQueryService` 使用方，但与 §2 依赖图 product → inventory 冲突。 | 需先消歧设计基准；确定后再按最终方向调整 Maven/代码依赖，避免循环或反向依赖。 | Blocked | 对应 `EIN-001`；设计文档内部约束冲突。 |
| 11 | ecommerce-inventory | `design-docs/02-系统架构.md` §1、§3 | inventory 模块定义 product 表 Entity 和 `ProductRepository`，违反模块数据边界。 | 删除 inventory 内 `Product` Entity 与 `ProductRepository`；商品信息通过跨模块查询契约或上层组合获取。 | Pending | 对应 `EIN-002`。 |
| 12 | ecommerce-inventory | `design-docs/02-系统架构.md` §3、§4 | `InventoryQueryService#getStockSummary` 返回 product 包 `StockSummaryDto`，契约泄露 product 类型。 | 返回类型改为 `com.ecommerce.inventory.query.StockSummaryDto`，同步调整实现和 product/cart/order 使用方。 | Pending | 对应 `EIN-003`。 |
| 13 | ecommerce-inventory | `design-docs/02-系统架构.md` §7 | 库存摘要缓存 `inventory:summary:{skuId}`、TTL 30 秒未实现。 | 为库存摘要查询增加缓存；在入库、出库、调整、预占、释放、扣减后驱逐或更新缓存。 | Pending | 对应 `EIN-004`。 |
| 14 | ecommerce-inventory | `design-docs/02-系统架构.md` §8；`README.md` §6.3 | 库存管理接口未在模块内体现 ADMIN 角色约束。 | 在 `AdminInventoryController` 类或方法添加 `@PreAuthorize("hasRole('ADMIN')")`，并确保方法级安全生效或全局规则覆盖。 | Pending | 对应 `EIN-005`。 |
| 15 | ecommerce-inventory | `README.md` §6、§6.3 | `POST /api/v1/admin/inventory/outbound` 使用 query param 承载业务字段，未使用请求体 DTO。 | 新增 `OutboundRequest` DTO，Controller 改为 `@Valid @RequestBody`，字段名和类型保持冻结契约。 | Pending | 对应 `EIN-006`。 |
| 16 | ecommerce-inventory | `README.md` §6、§6.3 | `POST /api/v1/admin/inventory/adjustments` 使用 query param 承载业务字段，未使用请求体 DTO。 | 新增 `StockAdjustmentRequest`/`AdjustmentRequest` DTO，Controller 改为 `@Valid @RequestBody`。 | Pending | 对应 `EIN-007`。 |
| 17 | ecommerce-review | `design-docs/02-系统架构.md` §2、§3、§5 | review POM 直接依赖 loyalty，与 `ReviewApprovedEvent` 由 review 发布、loyalty 监听的事件解耦方向不一致。 | 删除 `ecommerce-review` 对 `ecommerce-loyalty` 的 Maven 依赖；review 只发布事件。 | Pending | 对应 `ER-001`。 |
| 18 | ecommerce-review | `design-docs/02-系统架构.md` §4；`README.md` §7.2 | review 未使用 user `UserQueryService` 查询用户激活/冻结状态。 | 注入 `UserQueryService`，创建评价前校验 `isActive/isFrozen`，分别抛 `USER_NOT_ACTIVE`、`USER_FROZEN`。 | Pending | 对应 `ER-002`。 |
| 19 | ecommerce-review | `design-docs/02-系统架构.md` §5 | 创建评价时状态仍为 `PENDING_REVIEW` 却立即发布 `ReviewApprovedEvent`。 | 移除创建评价时的事件发布，仅在审核通过并保存 `APPROVED` 后发布。 | Pending | 对应 `ER-003`。 |
| 20 | ecommerce-review | `design-docs/02-系统架构.md` §5 | review 模块内部监听 `ReviewApprovedEvent` 并模拟发放评价积分，职责应属于 loyalty。 | 删除或停用 review 内 `ReviewApprovedEventListener`，积分发放只由 loyalty 监听器处理。 | Pending | 对应 `ER-004`。 |
| 21 | ecommerce-review | `design-docs/02-系统架构.md` §5 | `ReviewApprovedEvent` 在 review 与 loyalty 中重复定义，发布类型与监听类型不匹配。 | 将事件统一为单一公共契约，review 发布同一类型，loyalty 监听同一类型并删除重复事件类。 | Pending | 对应 `ER-005`；联动 `ELT-003`。 |
| 22 | ecommerce-order | `design-docs/02-系统架构.md` §3、§4 | 订单创建积分抵扣使用 `LoyaltyQueryService` 估算，未通过 `LoyaltyCommandService` 执行命令。 | 注入并调用 `LoyaltyCommandService` 完成积分冻结/消费/解冻或抵扣；估算仅保留为展示/校验。 | Pending | 对应 `EO-001`。 |
| 23 | ecommerce-order | `design-docs/02-系统架构.md` §4、§5、§6 | `OrderPaymentStatusUpdater.markAsPaid` 仅更新订单支付状态，未扣减库存、未发布 `OrderPaidEvent`。 | 委托统一支付成功处理服务，或在同事务内完成订单状态更新、库存扣减和事件发布。 | Pending | 对应 `EO-002`。 |
| 24 | ecommerce-order | `design-docs/02-系统架构.md` §6 | 支付成功处理吞掉库存扣减异常，仍可能发布 `OrderPaidEvent`。 | 库存扣减失败必须抛出并回滚支付确认；只有物流/积分/通知后置失败可补偿且不阻塞支付。 | Pending | 对应 `EO-003`。 |
| 25 | ecommerce-order | `design-docs/02-系统架构.md` §6 | 批量订单导入被类级事务包住整批循环，未按单条事务处理。 | 移除整批事务或通过 `REQUIRES_NEW` 单条事务方法提交每单，失败不回滚已成功订单。 | Pending | 对应 `EO-004`。 |
| 26 | ecommerce-order | `design-docs/02-系统架构.md` §6 | 创建订单只计算优惠并快照 couponIds，未写优惠使用记录。 | 在创建订单事务中调用 promotion 优惠使用命令，锁定/核销优惠券并记录订单、用户、金额。 | Pending | 对应 `EO-005`；联动 `EPRO-003`。 |
| 27 | ecommerce-order | `README.md` §7.1、§7.2 | 订单模块使用 `BATCH_ORDER_FAILED`、`ORDER_NOT_PAYABLE`、`ORDER_INVALID_STATUS` 等未冻结错误码。 | 映射到 `RESOURCE_NOT_FOUND`、`ORDER_STATUS_CONFLICT`、`PRODUCT_NOT_FOR_SALE`、`VALIDATION_FAILED`、`CONFLICT` 等冻结错误码。 | Pending | 对应 `EO-006`。 |
| 28 | ecommerce-promotion | `design-docs/02-系统架构.md` §3、§4 | `PromotionCalculationService` 当前是具体 `@Service` 类，跨模块抽象实际在 common port，接口形态不符合设计。 | 将 `PromotionCalculationService` 改为 promotion 提供的本地接口，现有逻辑迁移到实现类，order/cart 通过 DTO 调用。 | Pending | 对应 `EPRO-001`。 |
| 29 | ecommerce-promotion | `design-docs/02-系统架构.md` §1、§2、§4 | cart 未依赖 promotion，而是通过 common `PromotionDiscountCalculator` 计算优惠。 | cart 增加对 promotion 接口依赖，`CartService` 注入 `PromotionCalculationService` 并做 DTO 转换。 | Pending | 对应 `EPRO-002`；联动 `ECART-002`。 |
| 30 | ecommerce-promotion | `design-docs/02-系统架构.md` §6 | promotion 未提供订单创建事务所需的优惠使用记录写入能力。 | 新增 `CouponUsageService`/`PromotionUsageCommandService`，设置 `CouponStatus.USED`、`usedOrderId`、`usedAt` 并供 order 调用。 | Pending | 对应 `EPRO-003`。 |
| 31 | ecommerce-promotion | `design-docs/02-系统架构.md` §8；`README.md` §6.7 | 促销用户侧接口未体现 USER 角色约束。 | 在 `PromotionController` 用户侧方法或类上添加 `@PreAuthorize("hasRole('USER')")`，或确认全局鉴权覆盖。 | Pending | 对应 `EPRO-004`。 |
| 32 | ecommerce-promotion | `design-docs/02-系统架构.md` §8；`README.md` §6.7 | 促销管理端接口未体现 ADMIN 角色约束。 | 在 `AdminPromotionController` 添加 `@PreAuthorize("hasRole('ADMIN')")`，或确认 `/api/v1/admin/**` 全局规则覆盖。 | Pending | 对应 `EPRO-005`。 |
| 33 | ecommerce-promotion | `README.md` §7.1、§7.2 | promotion 使用 `COUPON_LIMIT_EXCEEDED`、`COUPON_EXHAUSTED`、`SECKILL_*` 等未冻结错误码。 | 收敛到 `COUPON_EXPIRED`、`RESOURCE_NOT_FOUND`、`CONFLICT`、`VALIDATION_FAILED` 等冻结错误码。 | Pending | 对应 `EPRO-006`。 |
| 34 | ecommerce-logistics | `design-docs/02-系统架构.md` §4 | logistics 未提供 `LogisticsCommandService`，事件监听器直接操作 Repository/Service/订单查询。 | 新增 `LogisticsCommandService` 及实现，封装按已支付订单创建发货单；监听器只依赖该接口。 | Pending | 对应 `EL-001`。 |
| 35 | ecommerce-logistics | `design-docs/02-系统架构.md` §5 | logistics 未监听 `PaymentSucceededEvent`，仅监听 `OrderPaidEvent`。 | 新增 `PaymentSucceededEvent` 监听器，复用 `LogisticsCommandService` 创建发货单，异常记录不回滚支付。 | Pending | 对应 `EL-002`。 |
| 36 | ecommerce-logistics | `design-docs/02-系统架构.md` §5 | 物流签收后未发布 `ShipmentDeliveredEvent`。 | 新增 `ShipmentDeliveredEvent`，在 `ShipmentService#updateStatus` 保存 `DELIVERED` 后发布，供 order/loyalty 监听。 | Pending | 对应 `EL-003`。 |
| 37 | ecommerce-logistics | `design-docs/02-系统架构.md` §4、§5 | 签收状态同步使用设计未列出的 `OrderLogisticsStatusUpdater` 同步接口。 | 停止签收链路同步调用该接口，由 order 监听 `ShipmentDeliveredEvent` 更新签收状态。 | Pending | 对应 `EL-004`。 |
| 38 | ecommerce-logistics | `design-docs/02-系统架构.md` §7 | 运费模板缓存 `logistics:freight:{templateId}`、TTL 30 分钟未实现。 | 为运费模板读取增加缓存，创建/更新/停用模板时清理对应缓存。 | Pending | 对应 `EL-005`。 |
| 39 | ecommerce-logistics | `README.md` §6.7 | 物流管理发货流程路径变量使用 `{id}`，与冻结契约 `{shipmentId}` 不一致。 | 将三个 `@PostMapping` 路径变量和参数改为 `{shipmentId}`/`shipmentId`。 | Pending | 对应 `EL-006`。 |
| 40 | ecommerce-logistics | `README.md` §7.1 | 物流状态冲突抛 `IllegalStateException`，会映射为 `INTERNAL_ERROR`/500，而非 `CONFLICT`/409。 | 非法状态流转改抛 `ConflictException` 或 `BusinessException("CONFLICT", ...)`，补齐不可跳步校验。 | Pending | 对应 `EL-007`。 |
| 41 | ecommerce-loyalty | `design-docs/02-系统架构.md` §4 | 会员等级统计使用 loyalty 自定义 `AnnualConsumptionQueryService`，未通过 order `OrderQueryService`。 | 扩展 order `OrderQueryService` 提供年度已支付消费查询，loyalty 强依赖并调用该接口，删除静默降级。 | Pending | 对应 `ELT-001`。 |
| 42 | ecommerce-loyalty | `design-docs/02-系统架构.md` §5 | loyalty 重复定义并监听本模块 `OrderPaidEvent`，与 order 发布类型不一致。 | 统一 `OrderPaidEvent` 契约，loyalty 监听 order/common 的同一事件类型，删除本地重复事件。 | Pending | 对应 `ELT-002`。 |
| 43 | ecommerce-loyalty | `design-docs/02-系统架构.md` §5 | loyalty 重复定义并监听本模块 `ReviewApprovedEvent`，与 review 发布类型不一致。 | 统一 `ReviewApprovedEvent` 契约，loyalty 监听 review/common 的同一事件类型，删除本地重复事件。 | Pending | 对应 `ELT-003`；联动 `ER-005`。 |
| 44 | ecommerce-loyalty | `design-docs/02-系统架构.md` §5 | loyalty 缺少 `PaymentSucceededEvent` 和 `ShipmentDeliveredEvent` 监听入口。 | 新增两个事件监听器；若缺少 `ShipmentDeliveredEvent`，先由 logistics/common 建立事件契约。 | Pending | 对应 `ELT-004`；联动 `EL-003`。 |
| 45 | ecommerce-loyalty | `design-docs/02-系统架构.md` §5、§6 | `OrderPaidEvent` 积分发放失败只记录日志，未持久化补偿任务。 | 在 listener 异常兜底中保存失败记录或补偿任务，异常仍不外抛。 | Pending | 对应 `ELT-005`。 |
| 46 | ecommerce-loyalty | `design-docs/02-系统架构.md` §8；`README.md` §6.7 | loyalty 管理接口只用注释说明 ADMIN，无可执行角色约束。 | 在 `AdminLoyaltyController` 或 `expirePoints()` 上添加 `@PreAuthorize("hasRole('ADMIN')")`。 | Pending | 对应 `ELT-006`。 |
| 47 | ecommerce-cart | `design-docs/02-系统架构.md` §3、§4 | cart 注入 product 包 `InventoryQueryService` 和 `StockSummaryDto`，而设计要求由 inventory 提供。 | `CartValidationService` 改为使用 inventory 包 `InventoryQueryService`/`StockSummaryDto`，同步测试 mock/import。 | Pending | 对应 `ECART-001`；联动 `EIN-003`。 |
| 48 | ecommerce-cart | `design-docs/02-系统架构.md` §4 | 购物车价格预估使用 common `PromotionDiscountCalculator`，未使用 promotion `PromotionCalculationService`。 | `CartService` 改为注入 promotion `PromotionCalculationService`，转换购物车项和 couponIds DTO。 | Pending | 对应 `ECART-002`；联动 `EPRO-001/002`。 |
| 49 | ecommerce-cart | `design-docs/02-系统架构.md` §7 | 购物车缓存以裸 `Long userId` 为 key，未使用 `cart:{userId}`。 | 缓存 key 类型改为 String，统一生成 `cart:{userId}`，保存/读取/删除均使用该 key。 | Pending | 对应 `ECART-003`。 |
| 50 | ecommerce-cart | `design-docs/02-系统架构.md` §7 | cart 模块仍存在 `cart`、`cart_item` JPA Entity/Repository 临时明细落库模型。 | 删除或停用 `Cart`、`CartItem`、`CartRepository`、`CartItemRepository` 等持久化模型，临时明细仅保存在缓存。 | Pending | 对应 `ECART-004`。 |
| 51 | ecommerce-cart | `README.md` §7.1、§7.2 | cart 使用 `INVALID_QUANTITY`、`CART_FULL` 等未冻结错误码。 | 映射为 `VALIDATION_FAILED` 或按状态冲突映射为 `CONFLICT`，不新增错误码。 | Pending | 对应 `ECART-005`。 |
| 52 | ecommerce-payment | `design-docs/02-系统架构.md` §3、§6；`README.md` §6.6 | 支付成功回调只更新支付单/订单状态并发布事件，缺少库存扣减。 | payment 接入 `InventoryReservationService#deductAfterPayment`，在支付确认事务内扣减库存，失败回滚。 | Pending | 对应 `EPAY-001`；需避免与 order 重复扣减。 |
| 53 | ecommerce-payment | `design-docs/02-系统架构.md` §4、§6 | payment 未依赖或调用 loyalty `LoyaltyCommandService`。 | 支付成功后的积分发放通过 `LoyaltyCommandService#earnPaymentPoints` 后置处理，异常不回滚支付。 | Pending | 对应 `EPAY-002`。 |
| 54 | ecommerce-payment | `design-docs/02-系统架构.md` §3、§5、§6 | 退款完成后 payment 服务内同步发送通知，未完全通过 `RefundCompletedEvent` 解耦。 | 移除 `RefundService` 直接通知调用，由 common notification 监听 `RefundCompletedEvent` 发送并兜底异常。 | Pending | 对应 `EPAY-003`。 |
| 55 | ecommerce-payment | `design-docs/02-系统架构.md` §6 | 退款流程未按售后单、验收单、退款单分阶段提交。 | 拆分退款生命周期和事务边界，新增/调整售后单、验收单、退款单实体或服务方法。 | Pending | 对应 `EPAY-004`。 |
| 56 | ecommerce-payment | `design-docs/02-系统架构.md` §3、§4 | payment 通过 `OrderDto` 仍依赖 `com.ecommerce.order.entity.OrderStatus`。 | 将订单状态契约移到 query DTO 专用枚举或字符串字段，payment 不再 import order entity 包。 | Pending | 对应 `EPAY-005`。 |
| 57 | ecommerce-payment | `README.md` §7.1、§7.2 | payment 使用 `ORDER_NOT_FOUND`、`PAYMENT_DUPLICATE`、`REFUND_STATUS_INVALID` 等未冻结错误码。 | 映射到 `RESOURCE_NOT_FOUND`、`ORDER_STATUS_CONFLICT`、`CONFLICT`、`VALIDATION_FAILED` 等冻结错误码，保留已冻结支付/退款/发票码。 | Pending | 对应 `EPAY-006`。 |
| 58 | ecommerce-app | `design-docs/02-系统架构.md` §1、§3；`README.md` §6.8 | `EventFailureAdminController` 直接注入 common `FailedEventRecordRepository` 并返回 JPA Entity。 | 在 common 新增失败事件查询服务和 DTO；app Controller 改为注入查询服务并返回 DTO，保持 `/api/v1/admin/events/failures` 契约不变。 | Pending | 对应 `EA-001`。 |
