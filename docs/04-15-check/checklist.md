# 04-15 一致性检查修复 Checklist

## 使用说明

- `[ ]` 未开始
- `[~]` 修复中
- `[x]` 已完成
- 每一项对应 `resolution.md` 中的一个 `R-...` 修复方案。
- 修改代码时不得破坏 `README.md` 中冻结的 REST API 契约。

## 总览

> 已覆盖全部 49 项报告不一致点。

| 状态 | ID | 模块 | 问题 | 方案摘要 | 依赖项 | 验证方式 |
|---|---|---|---|---|---|---|
| [x] | R-USER-01 | 用户服务 | `UserProfile` 缺少昵称字段 | 在 `UserProfile` 新增 `nickname` 并补齐访问器；保留 `User.nickname` 兼容既有响应；资料创建/更新时明确同步 | 数据库 DDL 或 JPA 自动建表；昵称双写同步策略 | 实体/服务测试；注册或资料更新后校验昵称；`mvn -f code/pom.xml test` |
| [x] | R-USER-02 | 用户服务 | 注册用户直接为 `ACTIVE` | 注册保存用户时改为 `PENDING_ACTIVATION`，激活成功后再置为 `ACTIVE` | R-USER-03、R-USER-04 | 注册后状态断言；未激活登录 403 `USER_NOT_ACTIVE`；激活后登录成功 |
| [x] | R-USER-03 | 用户服务 | 注册未生成邮箱激活令牌 | 在注册保存用户后创建并保存 `EmailActivationToken`，设置 token、userId、expiresAt、used=false | R-USER-02、R-USER-04；token 有效期配置 | 注册后存在未使用 token；使用 token 激活；重复激活冲突 |
| [x] | R-USER-04 | 用户服务 | 注册通知不是激活邮件 | 将注册通知改为激活邮件模板，变量包含 `activationToken`/`activationLink`，继续通过 `LocalNotificationService` 发送 | R-USER-03；通知模板/链接配置 | 通知记录含激活模板和 token；通知失败不回滚注册 |
| [x] | R-USER-05 | 用户服务 | 地址格式化参数顺序错误 | 将 `AddressFormatter.format` 签名和调用点统一为 province/city/district/detail | 全量复核同类型参数调用点 | 单测输出“省市区详细地址”；地址创建查询字段不互换 |
| [x] | R-USER-06 | 用户服务 | 冻结登录未保证 HTTP 403 | 冻结/未激活登录改抛可映射 403 的异常或在全局异常映射中精确处理对应 code | 全局异常映射；避免影响普通业务错误 | 冻结登录 403 `USER_FROZEN`；未激活登录 403 `USER_NOT_ACTIVE` |
| [x] | R-PRODUCT-01 | 商品服务 | SKU 状态机缺少 DELETED 转换且上下架过宽 | 已收紧 `DRAFT -> ON_SHELF`、`ON_SHELF -> OFF_SHELF`，并新增内部 `deleteSku` 转 `DELETED` 能力 | 缓存清理、审计日志；不新增冻结外 REST | `SkuServiceTest` 合法/非法状态转换断言 |
| [x] | R-PRODUCT-02 | 商品服务 | keyword 未覆盖卖点 | 已将 keyword 扩展到 `ProductSku.name`、`ProductSpu.name`、`ProductSpu.description`，并在分页前过滤 | description 作为卖点映射 | `ProductSearchServiceDataJpaTest` 覆盖 SKU/SPU 名称与描述命中 |
| [x] | R-PRODUCT-03 | 商品服务 | categoryId 不含子类目且分页后过滤 | 已递归收集类目及后代类目 ID，并通过 SPU 子查询在分页前过滤 SKU | `CategoryRepository.findByParentId`；防止类目环 | `ProductSearchServiceDataJpaTest` 覆盖父类目命中子/孙类目与分页总数 |
| [x] | R-PRODUCT-04 | 商品服务 | brandId 分页后过滤 | 已将品牌条件移入数据库查询并在分页前执行 | 与搜索统一改造共用查询结构 | `ProductSearchServiceDataJpaTest` 覆盖品牌分页过滤 |
| [x] | R-PRODUCT-05 | 商品服务 | tags 字段未实现过滤 | 已新增 `ProductSpuTag` 关联模型并按标签名称解析 ID，在分页前过滤 SPU/SKU | 依赖 JPA 自动建表 | `ProductSearchServiceDataJpaTest` 覆盖标签命中与空页行为 |
| [x] | R-INVENTORY-01 | 库存服务 | 库存充足边界判断错误 | 已将 `InventoryService.checkAvailability` 从 `>` 改为 `>=`，等量库存判定为可用 | 无 | `InventoryServiceTest` 覆盖等量库存返回 `available=true` |
| [x] | R-INVENTORY-02 | 库存服务 | 支付后扣减未生成出库单 | 已在 `deductAfterPayment` 中同事务扣减库存、更新预占并幂等生成 `OutboundOrder` | R-PAYMENT-01；`OutboundOrder` 幂等 | `InventoryReservationServiceImplTest` 覆盖出库单生成与重复事件幂等 |
| [x] | R-INVENTORY-03 | 库存服务 | 多仓分配未按省份、距离、优先级排序 | 已按服务区域/省份匹配、可单仓满足、距离降级近似、`priority`、`warehouseId` 构造稳定候选排序 | 订单侧透传省份；真实距离字段缺失 | `InventoryReservationServiceImplTest` 覆盖区域匹配与优先级排序 |
| [x] | R-INVENTORY-04 | 库存服务 | 同一订单同一 SKU 未优先单仓 | 已优先选择可单仓满足的仓库，不足时才拆仓预占 | R-INVENTORY-03 | `InventoryReservationServiceImplTest` 覆盖单仓优先与拆仓场景 |
| [x] | R-CART-01 | 购物车服务 | 重复加入同 SKU 覆盖数量 | 已将 `CartService.addItem` 改为 `existingQuantity + requestQuantity`，并以总量重做数量和库存校验 | `CartValidationService` | `CartServiceTest` 覆盖 2+3=5 的累加与总量库存校验 |
| [x] | R-CART-02 | 购物车服务 | 查询购物车未展示库存摘要 | 已为 `CartItemResponse` 增加库存展示字段，并在构建响应时查询 `InventoryQueryService.getStockSummary` 填充 | 库存模块 Bean；逐项查询性能 | `CartServiceTest` 覆盖购物车项展示 `availableStock` / `reservedStock` |
| [x] | R-CART-03 | 购物车服务 | 价格预估缺少优惠拆分字段 | 已扩展 `CartEstimateResponse`，从完整促销结果映射满减、会员折扣、可用券，保留 `discountAmount` | 促销模块拆分字段 | `CartServiceTest` 覆盖满减、会员折扣、可用券列表与总优惠填充 |
| [x] | R-ORDER-01 | 订单服务 | 下单库存预占顺序晚于风控 | 已将库存预占前移到风控前，并在失败路径按 `reservationRef` 释放、成功后绑定真实 `orderId` | 依赖库存内部预占引用能力 | `OrderServiceTest` 覆盖先预占后风控与风险拒绝释放 |
| [x] | R-ORDER-02 | 订单服务 | 订单总价遗漏运费 | 已按商品总额+运费+包装费-优惠-积分计算 `payableAmount` | 支付、发票、统计依赖正确金额 | `OrderTotalCalculatorTest` 覆盖包含 `shippingFee` 的公式 |
| [x] | R-ORDER-03 | 订单服务 | 金额最终未 HALF_UP 到分 | 已在最终应付金额输出前调用 `MonetaryUtil.roundToCent` 并保底 0.01 | 公共金额工具 | `OrderTotalCalculatorTest` 覆盖 HALF_UP 与最低金额边界 |
| [x] | R-ORDER-04 | 订单服务 | 超时取消未释放库存 | `OrderTimeoutService` 已注入库存服务并在超时取消时调用 `release(orderId)` | 库存释放幂等 | `OrderTimeoutServiceTest` 覆盖超时取消释放库存 |
| [x] | R-ORDER-05 | 订单服务 | 批量订单单条失败可提前停止 | 已移除提前停止逻辑，无论 `continueOnError` 如何都逐条处理并记录结果 | 单条事务隔离；保留请求字段 | `BatchOrderServiceTest` 覆盖“合法-非法-合法”继续处理 |
| [x] | R-PAYMENT-01 | 支付/退款/发票/结算 | 支付成功未发布 `OrderPaidEvent` | `confirmPayment` 保留 `PaymentSucceededEvent` 并新增 `OrderPaidEvent` | 库存扣减幂等；避免后置动作双触发 | 支付回调后订单 PAID、库存扣减事件消费；重复回调不重复扣减 |
| [x] | R-PAYMENT-02 | 支付/退款/发票/结算 | 退款金额额外扣固定 1 元 | 移除固定扣减，按 `paidAmount × (1 - refundFeeRate)` 计算 | `PaymentConfig.refundFeeRate`；金额舍入 | `100.00 -> 98.00`；退款申请接口返回设计金额 |
| [x] | R-PAYMENT-03 | 支付/退款/发票/结算 | 退款缺少独立财务退款阶段 | 仓库验收只到 `WAREHOUSE_ACCEPTED`，内部财务阶段完成退款并发布事件 | 异步/调度执行；失败重试 | 仓库验收后未完成；财务退款后 COMPLETED、支付单 REFUNDED、事件发布 |
| [x] | R-PAYMENT-04 | 支付/退款/发票/结算 | 发票默认税率错误 | `InvoiceService` 默认税率改为 `0.06`，保留配置覆盖 | `RuntimeConfigRegistry` 配置隔离 | 未配置时 100 税额 6；配置覆盖时按配置计算 |
| [x] | R-PAYMENT-05 | 支付/退款/发票/结算 | 结算未限定成功且未结算订单 | 查询限定 `SUCCESS` 且未结算，批次保存后回写结算标记 | 数据库字段迁移；作废重建标记回滚 | 混合状态只纳入成功未结算；重复生成不重复纳入 |
| [x] | R-PAYMENT-06 | 支付/退款/发票/结算 | 结算退款合计固定为 0 | 注入 `RefundRecordRepository`，按完成退款自然日汇总 `totalRefundAmount` | R-PAYMENT-03；退款结算去重 | 有退款时合计正确；非完成/非当天不计入；无退款为 0 |
| [x] | R-PROMOTION-01 | 促销服务 | 折扣券公式错误 | 已改为“优惠=原价-原价×discountValue”，并保留 `maxDiscount` 封顶 | R-PROMOTION-03；金额工具 | `CouponServiceTest` 覆盖 8 折 100 元优惠 20 |
| [x] | R-PROMOTION-02 | 促销服务 | 优惠券校验顺序和项不完整 | 已重构 `CouponValidator`，按存在性→有效期→门槛→商品适用性→用户限制→已使用顺序校验，并补类目适用性 | 依赖商品模块只读类目查询 | `CouponValidatorTest` 覆盖顺序、类目适用性与非法券场景 |
| [x] | R-PROMOTION-03 | 促销服务 | 优惠叠加顺序错误 | 已调整为满减 → 优惠券 → 会员折扣，后一步基于前一步结果 | R-PROMOTION-01、R-PROMOTION-05 | `PromotionCalculationServiceTest` 覆盖 300-30 后 8 折再 95 折最终 205.20 |
| [x] | R-PROMOTION-04 | 促销服务 | 秒杀缺少用户限购校验 | 已新增秒杀购买记录并按 `userId + quantity` 校验 `perUserLimit` | 订单链路后续需接入用户/数量 | `SeckillServiceTest` 覆盖超限拒绝与购买记录保存 |
| [x] | R-PROMOTION-05 | 促销服务 | 秒杀价参与普通满减 | 已识别进行中的秒杀价行并从满减基数排除，`itemTotal` 保持全部金额 | 秒杀活动查询；R-PROMOTION-03 | `PromotionCalculationServiceTest` 覆盖秒杀价不参与普通满减 |
| [x] | R-LOGISTICS-01 | 物流服务 | DELIVERED 未同步订单物流状态 | 已将订单物流状态同步从非 DELIVERED 分支抽出，所有状态变更均同步 | 订单状态机支持 DELIVERED | `ShipmentServiceTest` 覆盖 DELIVERED 调用 `OrderLogisticsStatusUpdater` |
| [x] | R-LOGISTICS-02 | 物流服务 | 运费模板缺少件数规则 | 已在 `FreightTemplate`、请求 DTO 和服务增加可选 `itemCountRules` | 数据库列/JPA 建表；规则 JSON | `FreightTemplateServiceTest` 覆盖创建/更新持久化件数规则 |
| [x] | R-LOGISTICS-03 | 物流服务 | 运费计算未应用模板规则 | 已新增上下文式计算入口，匹配省份、重量、件数规则；订单创建可通过跨模块端口固化结果 | R-LOGISTICS-02；订单提供省份/件数，重量当前可选为空 | `FreightCalculatorTest` 覆盖默认、免邮、省份、重量、件数、非法 JSON 回退；`OrderServiceTest` 回归创建固化 |
| [x] | R-LOGISTICS-04 | 物流服务 | 额外监听支付成功事件创建发货单 | 已禁用 `PaymentSucceededEvent` 创建发货单入口，仅保留 `OrderPaidEvent` | R-PAYMENT-01；发货单幂等 | `OrderPaidShipmentListenerTest` 覆盖 `OrderPaidEvent` 创建和无 `PaymentSucceededEvent` 入口 |
| [x] | R-LOYALTY-01 | 积分与会员服务 | 订单积分赚取多乘 100 | 已将 `calcOrderPoints` 改为订单实付金额×等级倍率×活动系数，`POINTS_PER_YUAN` 仅用于抵扣 | 测试期望已更新 | `LoyaltyPointServiceTest` 覆盖 SILVER/NORMAL 赚取口径，支付监听测试按新 EARN 点数断言 |
| [x] | R-LOYALTY-02 | 积分与会员服务 | GOLD 倍率为 1.1 | 已将 `MemberLevel.GOLD` 和 `MemberBenefitService` GOLD 倍率改为 1.2 | R-LOYALTY-01 | `MemberLevelTest`、`LoyaltyPointServiceTest` 覆盖 GOLD multiplier 1.2 与实付 100 发 120 积分 |
| [x] | R-LOYALTY-03 | 积分与会员服务 | 缺少每月过期积分调度 | 已新增 `@Scheduled(cron="0 0 0 1 * *")` 调用 `PointsExpireService.expire()` | 调度开启；EXPIRE 幂等 | `PointsExpireSchedulerTest` 覆盖 scheduler 调用，`PointsExpireServiceTest` 覆盖过期扣减和流水记录 |
| [x] | R-LOYALTY-04 | 积分与会员服务 | 抵扣未排除已过期积分 | 已在抵扣估算和实际扣减前执行用户级过期处理，只使用未过期积分 | `PointsExpireService` 幂等；性能 | `LoyaltyPointServiceTest` 覆盖已过期积分估算为 0 且实际抵扣不扣减；`PointsExpireServiceTest` 覆盖用户级过期 |
| [x] | R-REVIEW-01 | 评价服务 | 敏感词完全相等匹配 | 已将 `SensitiveWordFilter` 改为 `contains` 命中和 `replace` 替换，并加空值保护 | 敏感词数据 | `badword` 和包含文本均命中，正常评价不命中 |
| [x] | R-REVIEW-02 | 评价服务 | 含敏感词评价直接拒绝 | 已移除敏感词命中抛异常，保存过滤内容并置为 `PENDING_REVIEW` | R-REVIEW-01；审核流程 | 含敏感词评价创建成功待审核，公开列表不展示，审核后状态正确 |
| [x] | R-COMMON-01 | 通用模块/本地通知组件 | `NotificationRequest` 有额外字段 | 已迁移 common/order/user 调用方并删除 `subject/content` 字段和 builder，仅保留设计字段 | R-COMMON-03 | `LocalNotificationServiceImplTest`、`UserRegistrationNotificationListenerTest`、`OrderNotificationServiceTest` |
| [x] | R-COMMON-02 | 通用模块/本地通知组件 | 通知渠道场景不一致 | 已调整支付成功/发货为 `SMS`，订单状态相关通知为 `IN_APP` | R-COMMON-03；接收人来源 | `OrderNotificationServiceTest` 渠道断言；`CoreNotificationEventListener` 对支付成功改为 `SMS` |
| [x] | R-COMMON-03 | 通用模块/本地通知组件 | 通知请求缺设计字段 | 已统一按 `bizType/bizId/receiver/channel/templateCode/variables/idempotencyKey` 构建订单/用户/common 通知请求 | R-COMMON-01/02；幂等键规则 | `OrderNotificationServiceTest`、`UserRegistrationNotificationListenerTest`、幂等测试 |
| [x] | R-COMMON-04 | 通用模块/本地通知组件 | 发送失败抛异常影响主流程 | `LocalNotificationServiceImpl` 发送失败现仅记录失败原因并吞掉异常，失败记录写入异常也二次隔离 | R-COMMON-05 | `LocalNotificationServiceImplTest#testSend_failureIsIsolatedAndNoSuccessRecordWritten` |
| [x] | R-COMMON-05 | 通用模块/本地通知组件 | 成功日志记录过早 | 成功记录已后移到模板渲染和渠道发送成功后；失败路径不写成功记录 | R-COMMON-04 | `LocalNotificationServiceImplTest` 成功/失败/幂等断言 |

## 按模块 Checklist

### 用户服务

- [x] `R-USER-01`：`UserProfile` 未完整实现“昵称”字段 —— 新增 `nickname` 字段并明确与 `User.nickname` 的同步；验证：实体/注册资料测试；依赖：数据库字段策略。
- [x] `R-USER-02`：注册后用户状态被直接置为 `ACTIVE` —— 初始状态改为 `PENDING_ACTIVATION`；验证：注册状态、未激活登录、激活后登录；依赖：`R-USER-03`、`R-USER-04`。
- [x] `R-USER-03`：注册流程未生成并保存邮箱激活令牌 —— 注册后保存 `EmailActivationToken`；验证：token 存在、激活消费、重复激活；依赖：`R-USER-02`。
- [x] `R-USER-04`：注册通知不是激活邮件 —— 激活模板和 token/链接变量经 `LocalNotificationService` 发送；验证：通知记录和失败隔离；依赖：`R-USER-03`。
- [x] `R-USER-05`：地址格式化方法签名参数顺序与设计不一致 —— 签名和调用点统一为省、市、区、详细地址；验证：单测和地址黑盒字段；依赖：调用点复核。
- [x] `R-USER-06`：冻结用户登录错误码正确但 HTTP 状态未保证 403 —— 使用 403 异常或精确异常映射；验证：冻结/未激活登录 403；依赖：全局异常处理。

### 商品服务

- [x] `R-PRODUCT-01`：SKU 状态机未完整覆盖 `DELETED` 转换且上下架约束过宽 —— 已收紧状态转换并补齐内部 `deleteSku`；验证：`SkuServiceTest`；依赖：缓存/审计和 API 边界确认。
- [x] `R-PRODUCT-02`：商品搜索 keyword 未覆盖卖点模糊匹配 —— 已用 SPU 名称/描述和 SKU 名称共同匹配；验证：`ProductSearchServiceDataJpaTest`；依赖：使用 `description` 作为卖点落点。
- [x] `R-PRODUCT-03`：商品搜索 categoryId 未包含子类目且分页后过滤 —— 已递归子类目并分页前过滤；验证：`ProductSearchServiceDataJpaTest`；依赖：类目树正确性。
- [x] `R-PRODUCT-04`：商品搜索 brandId 在分页后过滤 —— 已将品牌条件下推数据库查询；验证：`ProductSearchServiceDataJpaTest`；依赖：与搜索统一改造合并。
- [x] `R-PRODUCT-05`：商品搜索 tags 条件只有 DTO 字段没有实现 —— 已补齐 `ProductSpuTag` 关联并分页前过滤；验证：`ProductSearchServiceDataJpaTest`；依赖：内部关联模型/迁移。

### 库存服务

- [x] `R-INVENTORY-01`：库存充足判断使用 `>` —— 已改为 `availableStock >= requestQuantity`；验证：`InventoryServiceTest`；依赖：无。
- [x] `R-INVENTORY-02`：支付后扣减流程未生成 `OutboundOrder` —— 已在扣减每条预占时同步生成幂等出库单；验证：`InventoryReservationServiceImplTest`；依赖：`R-PAYMENT-01` 的 `OrderPaidEvent`。
- [x] `R-INVENTORY-03`：多仓分配未实现省份匹配、距离、仓库优先级排序 —— 已构造候选仓排序，按服务区域/省份、可用库存、距离降级、优先级选择；验证：`InventoryReservationServiceImplTest`；依赖：订单/地址省份输入。
- [x] `R-INVENTORY-04`：同一订单同一 SKU 未显式优先选择可满足数量的单仓 —— 已先找足量单仓，不存在才拆仓；验证：`InventoryReservationServiceImplTest`；依赖：`R-INVENTORY-03`。

### 购物车服务

- [x] `R-CART-01`：同一 SKU 重复加入时未累加数量 —— 已按原数量加本次数量更新，并按总量校验范围和库存；验证：`CartServiceTest`；依赖：库存校验服务。
- [x] `R-CART-02`：购物车库存展示未体现 `InventoryQueryService` 查询信息 —— 已补库存展示字段并在构建响应时查询库存摘要；验证：`CartServiceTest`；依赖：库存模块查询服务。
- [x] `R-CART-03`：价格预估返回字段不完整 —— 已保留既有金额字段并新增满减、会员折扣、可用券列表映射；验证：`CartServiceTest`；依赖：促销模块完整响应。

### 订单服务

- [x] `R-ORDER-01`：下单流程中库存预占顺序不符合设计流程 —— 已将库存校验/预占前移到风控前，并补偿风险拒绝/失败路径释放；验证：`OrderServiceTest`；依赖：库存预占接口支持预占前移。
- [x] `R-ORDER-02`：订单总价计算公式遗漏运费 —— 已将 `shippingFee` 纳入应付金额公式；验证：`OrderTotalCalculatorTest`；依赖：支付、发票、统计金额链路。
- [x] `R-ORDER-03`：金额最终未统一使用 HALF_UP 四舍五入到分 —— 最终 `payableAmount` 已调用 `MonetaryUtil.roundToCent`；验证：`OrderTotalCalculatorTest`；依赖：公共金额工具。
- [x] `R-ORDER-04`：订单超时自动取消未释放预占库存 —— 超时取消时已调用 `inventoryReservationService.release(orderId)`；验证：`OrderTimeoutServiceTest`；依赖：库存释放幂等。
- [x] `R-ORDER-05`：批量导入可因单条失败提前停止 —— 已忽略 `continueOnError` 停止语义，始终逐条处理；验证：`BatchOrderServiceTest`；依赖：单条事务隔离。

### 支付/退款/发票/结算

- [x] `R-PAYMENT-01`：支付成功流程未发布 `OrderPaidEvent` —— 支付确认同时发布 `PaymentSucceededEvent` 和 `OrderPaidEvent`；验证：库存扣减事件链；依赖：库存扣减幂等。
- [x] `R-PAYMENT-02`：退款金额额外扣除固定 1 元 —— 移除固定扣减，只按手续费率计算；验证：默认 2% 费率 `100.00 -> 98.00`；依赖：金额舍入。
- [x] `R-PAYMENT-03`：退款流程缺少独立“财务退款”步骤 —— 仓库验收和财务退款拆成两个内部阶段；验证：财务退款完成后才发布事件；依赖：异步/调度和失败重试。
- [x] `R-PAYMENT-04`：发票税率默认值为 13% —— 默认税率改为 6%，保留配置覆盖；验证：默认和配置覆盖税额；依赖：运行时配置隔离。
- [x] `R-PAYMENT-05`：结算批次未限定支付成功且未结算订单 —— 查询成功未结算支付单并回写结算标记；验证：混合状态和重复生成；依赖：内部结算字段和作废重建策略。
- [x] `R-PAYMENT-06`：结算批次未包含退款数据 —— 按已完成退款自然日汇总退款金额；验证：退款合计正确；依赖：`R-PAYMENT-03` 设置 `completedAt`。

### 促销服务

- [x] `R-PROMOTION-01`：折扣券优惠金额计算公式与设计不一致 —— 已修正 8 折等折扣券优惠金额公式；验证：`CouponServiceTest`；依赖：金额工具和叠加顺序。
- [x] `R-PROMOTION-02`：优惠券校验顺序和校验项不完整 —— 已按设计顺序重构校验器并补门槛/商品适用性；验证：`CouponValidatorTest`；依赖：商品类目查询能力。
- [x] `R-PROMOTION-03`：优惠叠加顺序与设计固定顺序不一致 —— 已调整为满减、优惠券、会员折扣；验证：`PromotionCalculationServiceTest`；依赖：折扣公式和秒杀满减基数。
- [x] `R-PROMOTION-04`：秒杀用户限购校验缺失 —— 已增加用户购买记录和按用户限购校验；验证：`SeckillServiceTest`；依赖：订单链路用户/数量和并发控制。
- [x] `R-PROMOTION-05`：秒杀价格不参与普通满减规则未体现 —— 已在满减基数中排除进行中秒杀价行；验证：`PromotionCalculationServiceTest`；依赖：秒杀活动查询。

### 物流服务

- [x] `R-LOGISTICS-01`：`DELIVERED` 状态变更后未同步订单物流状态 —— 所有物流状态变更均调用 `OrderLogisticsStatusUpdater`；验证：`ShipmentServiceTest` 覆盖签收同步；依赖：订单状态机。
- [x] `R-LOGISTICS-02`：运费模板缺少商品件数配置能力 —— 增加 `itemCountRules` 字段和服务保存/更新；验证：`FreightTemplateServiceTest` 覆盖模板创建更新；依赖：数据库字段和规则格式。
- [x] `R-LOGISTICS-03`：运费计算未按省份、重量、商品件数应用模板规则 —— 新增上下文计算入口并解析模板规则，订单创建可通过跨模块端口固化运费；验证：`FreightCalculatorTest` 和 `OrderServiceTest`；依赖：订单提供计算上下文。
- [x] `R-LOGISTICS-04`：除 `OrderPaidEvent` 外还监听 `PaymentSucceededEvent` 创建发货单 —— 移除支付成功事件入口，只保留订单已支付事件；验证：`OrderPaidShipmentListenerTest`；依赖：`R-PAYMENT-01`。

### 积分与会员服务

- [x] `R-LOYALTY-01`：订单积分赚取公式多乘 100 积分/元换算系数 —— 已将赚取公式去掉 `POINTS_PER_YUAN`，仅抵扣换算继续使用 100 积分/元；验证：`LoyaltyPointServiceTest`、`OrderPaidEventListenerTest`；依赖：测试期望已更新。
- [x] `R-LOYALTY-02`：GOLD 会员等级积分倍率实现为 1.1 —— GOLD 倍率已统一改为 1.2，权益码同步为 `POINTS_MULTIPLIER_1_2`；验证：`MemberLevelTest` 和 GOLD 积分计算；依赖：`R-LOYALTY-01`。
- [x] `R-LOYALTY-03`：缺少每月 1 号凌晨过期积分调度 —— 已新增 scheduler 调用 `PointsExpireService.expire()`；验证：`PointsExpireSchedulerTest` 和过期流水测试；依赖：调度配置与幂等。
- [x] `R-LOYALTY-04`：积分抵扣未按流水过期时间排除过期积分 —— 估算/抵扣前已执行用户级过期处理；验证：过期积分不可估算、不可实际扣减，重复处理依赖 EXPIRE 幂等。

### 评价服务

- [x] `R-REVIEW-01`：敏感词检测采用完全相等匹配 —— 已改为包含匹配和包含替换，并保留空值保护；验证：嵌入敏感词命中；依赖：敏感词数据。
- [x] `R-REVIEW-02`：含敏感词评价被直接拒绝提交 —— 已保存过滤后内容并进入 `PENDING_REVIEW`；验证：含敏感词评价创建成功且待审核；依赖：`R-REVIEW-01`。

### 通用模块/本地通知组件

- [x] `R-COMMON-01`：`NotificationRequest` 定义了设计未列出的额外字段 —— 已迁移 common/order/user 调用方并删除 `subject/content` 与 builder；验证：定向搜索已无 `NotificationRequest.builder`、`setSubject`、`setContent` 遗留；依赖：`R-COMMON-03`。
- [x] `R-COMMON-02`：支付成功、发货提醒、订单状态通知渠道与设计场景不一致 —— 已调整为 SMS/SMS/IN_APP；验证：`OrderNotificationServiceTest` 渠道断言，`CoreNotificationEventListener` 支付成功为 `SMS`；依赖：接收人来源和 `R-COMMON-03`。
- [x] `R-COMMON-03`：业务模块构建多处通知未填写设计字段 —— 已统一构建完整 `NotificationRequest`；验证：`OrderNotificationServiceTest`、`UserRegistrationNotificationListenerTest`、幂等测试；依赖：`R-COMMON-01`、`R-COMMON-02`。
- [x] `R-COMMON-04`：发送失败后会向调用方抛出异常 —— 已改为失败记录后返回，不影响主流程；验证：`LocalNotificationServiceImplTest#testSend_failureIsIsolatedAndNoSuccessRecordWritten`；依赖：失败记录隔离。
- [x] `R-COMMON-05`：发送日志记录时机早于模板渲染和发送调用 —— 已将成功记录后移到实际发送成功后；验证：`LocalNotificationServiceImplTest` 成功/失败/幂等断言；依赖：`R-COMMON-04`。
