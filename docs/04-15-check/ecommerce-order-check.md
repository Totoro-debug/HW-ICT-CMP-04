# 电商订单服务一致性检查报告

## 检查范围

- 设计文档：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/08-订单服务设计.md`
- 代码目录：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/`
- 模块配置：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/pom.xml`
- 父级配置：`D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`
- 仅检查提示指定的 10 项订单服务设计要求。

## 不一致点汇总

共发现 5 个不一致点。

## 不一致点详情

### 1. 下单流程中库存预占顺序不符合设计流程

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:193`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:299`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/08-订单服务设计.md:32`、`D:/Desktop/work/HW-ICT-CMP-04/design-docs/08-订单服务设计.md:33`
- 不一致原因：设计要求第 4 步为“校验库存可用并预占库存”，第 5 步才执行风控校验；代码在风控校验之后、订单持久化之后才调用库存预占。
- 详细解析：`OrderService.createOrder` 在代码第 193 行进入“Risk check before persistence and inventory reservation”，第 197 行执行风控；库存预占在第 299-303 行才执行。该实现虽然最终调用了 `inventoryReservationService.reserve(...)`，但没有按设计流程在风控前完成库存可用校验和预占，且是在订单与明细保存之后才预占，流程顺序与设计文档第 4、5 步不一致。

### 2. 订单总价计算公式遗漏运费

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderTotalCalculator.java:78`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/08-订单服务设计.md:47`
- 不一致原因：设计公式要求“订单总价 = 商品总金额 + 运费 + 包装费 - 优惠抵扣金额 - 积分抵扣金额”，代码计算时没有把 `shippingFee` 加入应付金额。
- 详细解析：`OrderTotalCalculator.calculate(...)` 方法签名接收了 `shippingFee` 参数，但第 81 行从 `itemTotal + packagingFee` 开始计算，第 82-83 行只扣减优惠和积分，未执行 `+ shippingFee`。因此当存在运费时，最终 `payableAmount` 会少算运费，与设计文档第 47 行公式不一致。

### 3. 金额最终未统一使用 HALF_UP 四舍五入到分

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderTotalCalculator.java:78`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/money/MonetaryUtil.java:34`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/08-订单服务设计.md:52`
- 不一致原因：设计要求所有金额最终使用 `RoundingMode.HALF_UP` 四舍五入到分；订单总价计算返回前未调用按分取整方法。
- 详细解析：`MonetaryUtil.roundToCent(...)` 在公共工具中确实使用 `RoundingMode.HALF_UP`（`MonetaryUtil.java:24-28`），但 `OrderTotalCalculator.calculate(...)` 第 81-83 行使用的是 `MonetaryUtil.add/subtract`，这些方法第 34-46 行明确是不进行四舍五入的普通加减。`calculate(...)` 第 95 行直接返回 `payableAmount`，未在最终返回前调用 `roundToCent(...)`，因此订单总价最终精度不符合设计文档第 52 行要求。

### 4. 订单超时自动取消未释放预占库存

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderTimeoutService.java:69`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/08-订单服务设计.md:56`
- 不一致原因：设计要求 60 分钟未支付自动取消并释放预占库存；超时取消代码只修改订单状态并发布取消事件，没有调用库存释放接口。
- 详细解析：`OrderTimeoutService.cancelExpiredOrder(...)` 第 72-75 行将订单状态改为 `CANCELLED` 并保存，第 78-83 行记录事件并发布 `OrderCancelledEvent`，但该类没有注入 `InventoryReservationService`，方法体内也没有类似 `inventoryReservationService.release(order.getId())` 的调用。对比用户主动取消 CREATED 订单的 `OrderCancelService.cancelCreatedOrder(...)` 在第 119-121 行会释放库存，超时取消路径缺失该操作，与设计文档第 56 行不一致。

### 5. 批量导入可因单条失败提前停止，未保证非法订单跳过后继续处理剩余订单

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/service/BatchOrderService.java:41`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/dto/BatchCreateOrderRequest.java:18`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/08-订单服务设计.md:72`、`D:/Desktop/work/HW-ICT-CMP-04/design-docs/08-订单服务设计.md:74`
- 不一致原因：设计要求逐条校验、合法订单逐条创建、非法订单记录失败原因并跳过，任何一条失败不得导致整批订单回滚；代码引入 `continueOnError` 开关，允许在单条失败后中断后续订单处理。
- 详细解析：`BatchOrderService.createBatch(...)` 第 49 行逐条处理订单，第 60-65 行确实会捕获失败并记录失败原因；但第 67-70 行在 `request.isContinueOnError()` 为 `false` 时直接 `break`，后续合法订单不会被创建。`BatchCreateOrderRequest` 第 18-19 行定义了该开关。设计文档并未提供可中止批处理的选项，而是明确要求非法订单“跳过”且失败不得影响整批处理，因此该实现与设计文档第 72、74 行不一致。

## 未发现不一致的检查点

1. 订单状态枚举：`OrderStatus` 包含 `CREATED`、`PAYING`、`PAID`、`PICKING`、`SHIPPED`、`DELIVERED`、`COMPLETED`、`CANCEL_REVIEWING`、`CANCELLED`、`REFUNDING`、`REFUNDED`、`CLOSED`，与设计状态清单一致。
2. 取消规则：`CREATED` 可直接取消并释放库存，`PAID` 进入 `CANCEL_REVIEWING`，`SHIPPED` 不可取消；符合本次要求中列出的取消规则。
3. OrderQueryService 方法签名：存在 `getOrder(Long orderId)`、`getPayableOrder(Long orderId)`、`verifyPurchase(Long userId, Long productId)`、`getOrderAmount(Long orderId)`。
4. REST API：设计列出的 8 个端点均有实现：创建订单、订单详情、我的订单列表、取消订单、取消审核、批量创建、验证购买记录、销售统计。
5. 跨模块约束：支付服务代码通过 `OrderQueryService` 查询订单信息，未发现直接访问订单表或订单仓储的实现。
6. 订单总价下限：`OrderTotalCalculator` 将应付金额小于 `0.01` 的结果修正为 `0.01`，未发现与“订单总价不得小于 0.01”不一致。

## 无法确认项

无。
