# 02 系统架构一致性检查汇总

## 汇总结论

本次按模块分批启动子代理完成 `code/` 下 12 个 Maven 模块的设计一致性检查，并已为每个模块写入独立检查报告。检查基准仅限：

- `design-docs/02-系统架构.md` §1-§8
- `README.md` §6 API 基线（冻结契约）
- `README.md` §7 错误码
- 各模块源码、模块 `pom.xml`、父工程 `code/pom.xml` 与可用配置文件

所有模块报告均覆盖 8 个指定维度：架构风格、模块依赖方向、关键本地接口、领域事件、事务边界、缓存设计、安全架构、REST API/错误码；各报告均包含检查遗漏声明。

总体结果：共发现 **58** 项主要不一致。高频问题集中在关键本地接口契约偏移、领域事件发布/监听契约不一致、事务边界未按设计落地、缓存 Key/TTL 缺失、部分接口安全约束在模块内不可见，以及 README §7 未冻结错误码被使用。

## 模块报告清单

| 模块 | 报告 | 主要不一致数量 | 8 维度覆盖 | 主要不一致摘要 |
| --- | --- | ---: | --- | --- |
| ecommerce-app | [ecommerce-app-check.md](./ecommerce-app-check.md) | 1 | 是 | app-bootstrap 直接注入 common 的事件失败 Repository，违反跨模块 Repository 边界。 |
| ecommerce-common | [ecommerce-common-check.md](./ecommerce-common-check.md) | 3 | 是 | 缺少通知事件监听器；通知故障注入失败策略存在回滚风险；common 状态对象相关 REST 管理接口未在本模块提供。 |
| ecommerce-user | [ecommerce-user-check.md](./ecommerce-user-check.md) | 2 | 是 | 用户注册未通过 `UserRegisteredEvent` 解耦通知；未实现 `user:roles:{userId}` 30 分钟用户权限缓存。 |
| ecommerce-product | [ecommerce-product-check.md](./ecommerce-product-check.md) | 4 | 是 | 库存查询接口未按 inventory 提供方接入；商品详情缓存缺失；管理创建接口返回 JPA Entity；`PRODUCT_NOT_FOR_SALE` 覆盖不足。 |
| ecommerce-inventory | [ecommerce-inventory-check.md](./ecommerce-inventory-check.md) | 7 | 是 | 与 product 依赖方向/DTO 边界冲突；定义 product Entity/Repository；库存摘要缓存缺失；管理接口 ADMIN 约束在模块内不可见；部分请求字段承载方式偏离冻结契约。 |
| ecommerce-order | [ecommerce-order-check.md](./ecommerce-order-check.md) | 6 | 是 | 未使用 `LoyaltyCommandService`；支付确认/库存扣减/`OrderPaidEvent` 链路不完整；批量订单非单条事务；优惠使用记录缺失；存在未冻结错误码。 |
| ecommerce-payment | [ecommerce-payment-check.md](./ecommerce-payment-check.md) | 6 | 是 | 支付确认事务缺少库存扣减；未使用 `LoyaltyCommandService`；退款完成同步通知；退款阶段提交不足；依赖 order entity 枚举；存在未冻结错误码。 |
| ecommerce-promotion | [ecommerce-promotion-check.md](./ecommerce-promotion-check.md) | 6 | 是 | `PromotionCalculationService` 接口形态不符；cart 对 promotion 使用关系未按设计体现；优惠使用记录能力缺失；USER/ADMIN 角色约束在模块内不可见；存在未冻结促销错误码。 |
| ecommerce-loyalty | [ecommerce-loyalty-check.md](./ecommerce-loyalty-check.md) | 6 | 是 | 会员等级统计未使用 order 的 `OrderQueryService`；多个事件类型在本模块重复定义导致监听契约不成立；缺少 `PaymentSucceededEvent`/`ShipmentDeliveredEvent` 监听；补偿任务记录缺失；ADMIN 约束在模块内不可见。 |
| ecommerce-review | [ecommerce-review-check.md](./ecommerce-review-check.md) | 5 | 是 | review 直接依赖 loyalty；未使用 `UserQueryService`；创建评价时提前发布 `ReviewApprovedEvent`；review 内部自监听；review/loyalty 重复事件类型导致监听不成立。 |
| ecommerce-cart | [ecommerce-cart-check.md](./ecommerce-cart-check.md) | 5 | 是 | 库存查询接口包归属错误；未使用 promotion 的 `PromotionCalculationService`；购物车缓存 Key 非 `cart:{userId}`；存在购物车临时明细落库模型；存在未冻结错误码。 |
| ecommerce-logistics | [ecommerce-logistics-check.md](./ecommerce-logistics-check.md) | 7 | 是 | 缺少 `LogisticsCommandService`；未监听 `PaymentSucceededEvent`；签收未发布 `ShipmentDeliveredEvent`；同步订单状态接口不在设计内；运费模板缓存缺失；路径变量名与 README 不一致；状态冲突未映射 `CONFLICT`。 |

## 横向问题归类

### 1. 关键本地接口契约偏移

- `InventoryQueryService` 在 product/cart/inventory 之间存在包归属和 DTO 类型不一致问题。
- `PromotionCalculationService` 在 promotion 中为具体类，cart 实际使用 common integration 接口，与设计表中的接口名称、提供方、使用方不一致。
- `LoyaltyCommandService` 虽在 loyalty 存在，但 order/payment 未按设计使用。
- `LogisticsCommandService` 在 logistics 未找到。
- loyalty 会员等级统计未使用 order 提供的 `OrderQueryService`。

### 2. 领域事件链路不完整或类型不一致

- user 注册未发布 `UserRegisteredEvent`，common notification 侧也缺少核心事件监听器。
- order/payment/logistics/loyalty/review 多处事件发布、监听、补偿任务记录未完全符合 §5。
- review 与 loyalty、loyalty 与 order/review 之间存在同名事件类重复定义但包不同的问题，Spring 事件类型无法匹配。
- logistics 签收未发布 `ShipmentDeliveredEvent`，loyalty 缺少 `PaymentSucceededEvent` 与 `ShipmentDeliveredEvent` 监听器。

### 3. 事务边界未完全落地

- 支付确认链路中，库存扣减在 payment/order 两侧均未形成符合 §6 的强一致事务语义。
- order 批量订单导入未按单条事务隔离。
- order/promotion 未体现订单创建事务中的“优惠使用记录”。
- payment 退款流程未体现售后单、验收单、退款单分阶段提交。

### 4. 缓存设计缺失或 Key 不一致

- user 缺少 `user:roles:{userId}` 30 分钟权限缓存。
- product 缺少 `product:detail:{skuId}` 10 分钟商品详情缓存。
- inventory 缺少 `inventory:summary:{skuId}` 30 秒库存摘要缓存。
- cart TTL 为 7 天，但 Key 使用裸 `userId`，不是 `cart:{userId}`。
- logistics 缺少 `logistics:freight:{templateId}` 30 分钟运费模板缓存。

### 5. 安全约束在部分模块内不可见

- inventory、promotion、loyalty 的部分 USER/ADMIN 接口缺少模块内 `@PreAuthorize`、`@Secured` 或等价角色约束证据。
- app 全局安全配置可覆盖部分 `/api/v1/admin/**` 场景，但各模块报告按本模块源码范围如实记录“模块内未找到”。

### 6. REST/API 与错误码冻结契约问题

- 多个模块使用 README §7 未列出的错误码，例如 order/payment/promotion/cart/logistics 中的状态、数量、秒杀、支付、退款类自定义错误码。
- product 管理创建接口返回 JPA Entity，不符合 DTO/Response 边界要求。
- inventory 部分管理接口用 query param 承载业务字段，与 README 对 Request/Response Body 字段冻结的顶层约束存在偏差。
- logistics 管理发货路径变量名与 README 展示契约不一致。
- 多个模块报告声明：README §6 只列出路径、Method、认证、成功状态，未提供完整 Request/Response 字段明细，因此只能基于现有 DTO 字段做存在性记录，无法逐字段与 README 缺失字段表比对。

## 输出文件完整性

已生成以下 13 个检查输出文件：

1. [ecommerce-app-check.md](./ecommerce-app-check.md)
2. [ecommerce-common-check.md](./ecommerce-common-check.md)
3. [ecommerce-user-check.md](./ecommerce-user-check.md)
4. [ecommerce-product-check.md](./ecommerce-product-check.md)
5. [ecommerce-inventory-check.md](./ecommerce-inventory-check.md)
6. [ecommerce-order-check.md](./ecommerce-order-check.md)
7. [ecommerce-payment-check.md](./ecommerce-payment-check.md)
8. [ecommerce-promotion-check.md](./ecommerce-promotion-check.md)
9. [ecommerce-loyalty-check.md](./ecommerce-loyalty-check.md)
10. [ecommerce-review-check.md](./ecommerce-review-check.md)
11. [ecommerce-cart-check.md](./ecommerce-cart-check.md)
12. [ecommerce-logistics-check.md](./ecommerce-logistics-check.md)
13. [summary.md](./summary.md)

## 说明

- 本次任务只写入检查文档，未修改 `code/` 下源代码或配置代码。
- 各模块的详细代码定位、设计要求定位、原因解析与检查遗漏声明以对应模块报告为准。
