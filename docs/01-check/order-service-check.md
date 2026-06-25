# M6 order-service 一致性审查报告

审查范围：`README.md` 指定行、`design-docs/01-项目概述.md` 指定行、`code/ecommerce-order/**`、`code/pom.xml`。

发现不一致条数：9

## 1. 创建订单成功状态码不一致

1. 实现位置：`code/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java:56-64`
2. 设计依据：`README.md` 6.5 订单模块，`128-133` 行；`README.md` API 基线，`73-75` 行
3. 不一致内容：文档冻结 `POST /api/v1/orders/create` 成功状态为 `201`，实现使用 `ResponseEntity.ok(response)` 返回 `200`。
4. 原因分析与影响：成功 HTTP 状态码属于冻结 API 契约。当前实现会导致黑盒用例按契约断言创建成功状态码时失败，并破坏调用方对创建类接口返回 `201 Created` 的约定。

## 2. 购买验证接口认证要求不一致

1. 实现位置：`code/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java:34-37`、`120-126`
2. 设计依据：`README.md` 6.5 订单模块，`128-138` 行
3. 不一致内容：文档要求 `GET /api/v1/orders/verify-purchase` 认证为 `USER/ADMIN`，实现所在控制器类统一标注 `@PreAuthorize("hasRole('USER')")`，该接口仅允许 `USER`。
4. 原因分析与影响：接口认证范围小于冻结契约，ADMIN 调用购买验证能力会被拒绝，影响评价等模块或管理场景复用订单购买验证能力。

## 3. 下单前用户状态校验及错误码映射缺失

1. 实现位置：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:134-137`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPreconditionChecker.java:31-39`
2. 设计依据：`design-docs/01-项目概述.md` 关键业务原则，`49-52` 行；`README.md` 错误码，`216-219` 行
3. 不一致内容：文档要求商品下单前必须校验用户状态，并规定 `USER_NOT_ACTIVE`、`USER_FROZEN` 为 403。实现注释明确“does not check frozen status”，`OrderPreconditionChecker` 只校验用户是否存在和商品项数量，没有根据用户状态抛出 `USER_NOT_ACTIVE` 或 `USER_FROZEN`。
4. 原因分析与影响：未激活或冻结用户仍可能进入后续下单流程；即使失败，也不会按文档返回指定错误码，导致业务规则和错误响应契约不一致。

## 4. 风控结果未参与下单决策，`ORDER_RISK_REJECTED` 映射缺失

1. 实现位置：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:167-168`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderRiskChecker.java:37-40`
2. 设计依据：`design-docs/01-项目概述.md` 关键业务原则，`49-52` 行；`README.md` 错误码，`216-224` 行
3. 不一致内容：文档要求下单前校验风控结果，并规定风控拒绝使用 `ORDER_RISK_REJECTED`。实现中 `OrderService` 的 Step 4 “Risk check”为空，未调用 `OrderRiskChecker`；`OrderRiskChecker` 虽可返回 rejected 结果，但创建订单流程没有将其转换为 `ORDER_RISK_REJECTED`。
4. 原因分析与影响：高风险订单不会被阻断，风控拒绝场景无法按文档返回指定错误码，可能导致应拒绝订单被创建。

## 5. 订单金额非法错误码不一致

1. 实现位置：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderValidator.java:24-27`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:164-199`
2. 设计依据：`README.md` 错误码，`216-224` 行；`design-docs/01-项目概述.md` 统一错误响应格式，`86-95` 行
3. 不一致内容：文档规定订单金额非法错误码为 `ORDER_INVALID_AMOUNT`，HTTP 400。实现 `validateAmount` 对非法金额抛出普通 `IllegalArgumentException`，没有携带 `ORDER_INVALID_AMOUNT` 业务错误码。
4. 原因分析与影响：金额非法场景无法形成文档要求的统一业务错误响应，可能被全局异常处理为非业务错误或错误码不匹配。

## 6. 订单状态冲突错误码不一致

1. 实现位置：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderStateMachine.java:107-110`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderCancelService.java:86-104`、`206-208`
2. 设计依据：`README.md` 错误码，`216-224` 行；`design-docs/01-项目概述.md` 统一错误响应格式，`86-95` 行
3. 不一致内容：文档规定订单状态不允许操作应返回 `ORDER_STATUS_CONFLICT`，HTTP 409。实现状态机非法流转抛出 `ORDER_INVALID_TRANSITION`，取消场景还使用 `ORDER_CANNOT_CANCEL`、`ORDER_ALREADY_CANCELLED`、`ORDER_CANCEL_REVIEWING`、`ORDER_NOT_IN_REVIEW` 等非文档错误码。
4. 原因分析与影响：状态冲突类失败不会按冻结错误码返回，调用方无法用文档中的 `ORDER_STATUS_CONFLICT` 统一处理订单状态冲突。

## 7. 已支付订单取消流程不一致

1. 实现位置：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderCancelService.java:83-85`、`166-193`；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderStateMachine.java:39-42`
2. 设计依据：`design-docs/01-项目概述.md` 关键业务原则，`49-57` 行；`design-docs/01-项目概述.md` M6 职责，`32-41` 行
3. 不一致内容：文档要求已支付订单取消必须进入商家审核流程。实现对 `PAID` 状态调用 `cancelPaidOrderDirectly`，直接将订单置为 `CANCELLED` 并标记全额退款；状态机也允许 `PAID -> CANCELLED` 直接流转。
4. 原因分析与影响：绕过商家审核，破坏已支付订单取消的业务控制点，并可能提前释放库存或触发退款相关后续流程。

## 8. 促销有效性校验不一致

1. 实现位置：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:173-175`、`378-406`
2. 设计依据：`design-docs/01-项目概述.md` 关键业务原则，`49-52` 行
3. 不一致内容：文档要求下单前必须校验促销有效性。实现调用促销计算时捕获所有异常并返回 `BigDecimal.ZERO` 折扣，继续创建订单。
4. 原因分析与影响：优惠券或促销无效、过期、计算失败等情况可能被静默降级为无折扣下单，未按“下单前必须校验促销有效性”的要求阻断或返回明确错误。

## 9. 拆单职责未体现

1. 实现位置：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:201-247`、`253-257`
2. 设计依据：`design-docs/01-项目概述.md` M6 职责，`32-41` 行
3. 不一致内容：文档将“拆单”列为 M6 order-service 职责。当前创建流程始终创建一个 `Order`，将所有请求商品项保存到同一订单，并对该订单统一预占库存；在本模块已审查范围内未发现按仓库、商家或履约维度拆分订单的实现。
4. 原因分析与影响：多商品或多履约来源订单无法形成拆分后的订单结构，后续库存履约、物流、取消和统计可能只能按单一订单处理，与模块职责不一致。
