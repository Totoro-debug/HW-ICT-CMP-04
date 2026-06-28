# 电商物流服务一致性检查报告

## 检查范围

- 设计文档：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/11-物流服务设计.md`
- 代码目录：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/`
- 模块 POM：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/pom.xml`
- 父级 POM：`D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`
- 引用模块：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order`（仅核对 `OrderPaidEvent` 监听相关）

## 不一致点

### 1. DELIVERED 状态变更后未通过 OrderLogisticsStatusUpdater 更新订单物流状态

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/ShipmentService.java:293`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/ShipmentService.java:299`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/11-物流服务设计.md:36`
- 不一致原因：设计要求“物流状态变更后，必须通过 `OrderLogisticsStatusUpdater` 更新对应订单的物流状态”，但代码在 `newStatus == ShipmentStatus.DELIVERED` 时只发布 `ShipmentDeliveredEvent`，`OrderLogisticsStatusUpdater.updateLogisticsStatus(...)` 位于 `else` 分支，仅覆盖非 `DELIVERED` 状态。
- 详细解析：`updateStatus(...)` 会先将发货单状态设置为 `newStatus` 并保存；当新状态为 `DELIVERED` 时，代码进入 `if (newStatus == ShipmentStatus.DELIVERED)` 分支发布事件，随后跳过 `else` 中的订单物流状态同步调用。由于 `DELIVERED` 是设计文档列出的物流状态之一，且属于物流状态变更，因此也应通过 `OrderLogisticsStatusUpdater` 同步到订单物流状态。目前实现无法保证订单侧物流状态在签收时被该接口更新。

### 2. 运费模板缺少“商品件数”配置能力

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/entity/FreightTemplate.java:39`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/entity/FreightTemplate.java:46`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/dto/FreightTemplateRequest.java:22`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/dto/FreightTemplateRequest.java:25`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/11-物流服务设计.md:42`
- 不一致原因：设计要求运费模板可按省份、重量和商品件数配置；代码中的 `FreightTemplate` 与 `FreightTemplateRequest` 只提供 `provinceRules`、`weightRules`，未提供商品件数规则字段或对应请求参数。
- 详细解析：实体层能持久化省份规则和重量规则，接口请求也只能提交省份规则和重量规则。由于没有类似 `itemCountRules`、`quantityRules` 或其他商品件数字段，管理员无法创建或维护“按商品件数”的运费模板配置，导致设计要求中的商品件数维度缺失。

### 3. 运费计算未按省份、重量、商品件数应用模板规则

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/FreightCalculator.java:44`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/FreightCalculator.java:68`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/FreightCalculator.java:78`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/11-物流服务设计.md:40`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/11-物流服务设计.md:42`
- 不一致原因：设计要求默认运费 8 元、满 199 元免运费，并且运费模板可按省份、重量、商品件数配置；代码计算方法只接收 `itemTotal` 和可选 `templateId`，`calculateWithTemplate(...)` 只读取模板的免邮门槛和默认运费，没有省份、重量、商品件数输入，也没有解析或匹配 `provinceRules`、`weightRules`。
- 详细解析：`FreightCalculator.calculateFreight(BigDecimal itemTotal)` 和 `calculateFreight(BigDecimal itemTotal, Long templateId)` 的入参不足以表达省份、重量、商品件数。即使 `FreightTemplate` 中保存了 `provinceRules`、`weightRules`，`calculateWithTemplate(...)` 也只使用 `freeShippingThreshold` 与 `defaultFreight`，不会根据省份或重量差异计算运费；商品件数维度更没有字段和计算入口。因此模板配置能力与最终运费计算逻辑不闭环，无法满足“按省份/重量/商品件数配置”的运费规则。

### 4. 除 OrderPaidEvent 外还监听 PaymentSucceededEvent 创建发货单

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:37`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:43`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/11-物流服务设计.md:7`
- 不一致原因：设计明确要求订单支付成功后物流服务通过监听 `OrderPaidEvent` 创建发货单；代码除监听 `OrderPaidEvent` 外，还监听 `PaymentSucceededEvent` 并调用同一创建发货单逻辑。
- 详细解析：`onOrderPaid(OrderPaidEvent event)` 符合设计要求，但 `onPaymentSucceeded(PaymentSucceededEvent event)` 也会在支付成功事件后执行 `createShipmentForPaidOrder(...)`。虽然当前创建逻辑有按订单查询既有发货单的幂等保护，但该额外入口并非设计指定的 `OrderPaidEvent` 通道，扩大了发货单创建触发源，与“通过监听 OrderPaidEvent 创建发货单”的专用设计要求不完全一致。

## 未发现不一致的检查点

1. 发货流程：代码实现了创建发货单、生成拣货单、打印面单、扫码出库、物流回调更新状态等主要步骤；未发现与指定流程顺序相反的实现。
2. 流程约束：`outbound(...)` 仅允许 `LABEL_PRINTED` 或已 `OUTBOUND` 状态出库，`printLabel(...)` 仅允许 `PICKING` 或已 `LABEL_PRINTED` 状态打印面单，未发现可跳过拣货单和面单直接出库的入口。
3. 物流状态：`ShipmentStatus` 完整包含 `CREATED`、`PICKING`、`LABEL_PRINTED`、`OUTBOUND`、`COLLECTED`、`IN_TRANSIT`、`DELIVERED`、`EXCEPTION`。
4. 状态更新：除“不一致点 1”中的 `DELIVERED` 特例外，`CREATED`、`PICKING`、`LABEL_PRINTED`、`OUTBOUND` 以及回调中的非 `DELIVERED` 状态变更均存在调用 `OrderLogisticsStatusUpdater` 的实现。
5. 运费默认规则：默认运费 8 元、订单商品金额满 199 元免运费在 `FreightCalculator` 与 `FreightTemplateService` 中有对应实现。
6. 事件驱动：物流模块存在 `OrderPaidEvent` 监听器创建发货单；在本次限定检查范围内，未发现订单服务同步调用物流服务创建发货单。
7. REST API：设计列出的 6 个端点均有控制器映射实现：查询订单物流、生成拣货单、打印面单、扫码出库、物流状态回调、创建运费模板。

## 无法确认项

无。
