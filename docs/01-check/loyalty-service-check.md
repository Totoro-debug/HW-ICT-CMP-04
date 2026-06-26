# M10 loyalty-service 一致性审查报告

审查范围：`code/ecommerce-loyalty/`、`code/pom.xml`，以及指定文档行号范围。

发现不一致条数：6

## 1. 支付成功后的积分发放未体现异步事件处理，存在阻塞支付主流程风险

1. 实现位置：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/OrderPaidEventListener.java:29-30`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/OrderPaidEventListener.java:34-43`
2. 设计依据：`design-docs/01-项目概述.md` 第 5 章关键业务原则，`49-60` 行，特别是 `55` 行：“支付成功后的物流创建、积分发放、通知发送等后置动作通过本地事件异步触发，不得阻塞支付主流程。”
3. 不一致内容：积分发放监听器使用普通 `@EventListener`，方法 `onOrderPaid` 内同步计算并写入积分，未在本模块中体现异步触发机制。
4. 原因分析与影响：Spring 普通事件监听默认随发布线程同步执行；若积分计算或入账耗时/异常，将占用支付成功后的事件发布链路时间，不符合“异步触发、不得阻塞支付主流程”的设计原则。

## 2. 积分过期职责/API 仅记录日志，未实际执行过期处理

1. 实现位置：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/PointsExpireService.java:20-22`；调用入口 `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/AdminLoyaltyController.java:32-39`
2. 设计依据：`design-docs/01-项目概述.md` 第 4 章模块清单，`32-45` 行，特别是 `45` 行 M10 职责包含“过期”；`README.md` API 基线 `156-176` 行，特别是 `176` 行规定 `POST /api/v1/admin/loyalty/points/expire`，ADMIN，成功状态 200。
3. 不一致内容：管理端过期 API 会调用 `pointsExpireService.expire()`，但该方法仅输出日志，没有查询待过期积分、扣减可用积分、增加过期积分或记录 `EXPIRE` 流水。
4. 原因分析与影响：虽然 API URL、Method 和成功状态存在，但业务职责未落地；管理员触发积分过期后用户积分余额不会变化，积分过期规则不可观察、不可生效。

## 3. 积分冻结职责未实现为业务能力

1. 实现位置：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/entity/LoyaltyAccount.java:32-33`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:212-223`（其中 `218` 行仅初始化为 0）、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/LoyaltyController.java:59-66`（其中 `63` 行仅返回冻结积分）
2. 设计依据：`design-docs/01-项目概述.md` 第 4 章模块清单，`32-45` 行，特别是 `45` 行 M10 职责包含“冻结”。
3. 不一致内容：实现中只有 `frozenPoints` 字段、账户创建时初始化和查询展示，未见冻结/解冻积分的服务方法、命令接口或业务流程。
4. 原因分析与影响：冻结积分作为 M10 明确职责没有可执行路径，涉及订单/售后等需要暂不可用积分的场景时，系统无法把积分从可用态转为冻结态，也无法解除冻结。

## 4. 会员权益职责未实现

1. 实现位置：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/entity/MemberLevel.java:7-23`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/dto/MemberLevelResponse.java:8-14`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/MemberLevelService.java:52-77`
2. 设计依据：`design-docs/01-项目概述.md` 第 4 章模块清单，`32-45` 行，特别是 `45` 行 M10 职责包含“会员等级、权益”。
3. 不一致内容：会员等级实现仅包含等级枚举、积分倍率、年度消费和升级条件；未见会员权益模型、权益计算、权益返回字段或权益服务。
4. 原因分析与影响：代码只覆盖“会员等级”和积分倍率，未覆盖“权益”职责；依赖会员权益的业务或接口无法从 loyalty-service 获得设计要求的权益能力。

## 5. loyalty-service 直接查询订单表，违反模块协作边界

1. 实现位置：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/repository/OrderDataFetcher.java:27-36`，特别是 `29-33` 行通过 `JdbcTemplate` 查询 `orders` 表。
2. 设计依据：`design-docs/01-项目概述.md` 第 1 章系统定位，`3-8` 行，特别是 `7` 行：“模块之间不得随意访问彼此的数据库表或 Repository，应通过公开的本地接口、REST API、领域服务或 Spring ApplicationEvent 完成协作。”
3. 不一致内容：loyalty-service 为计算会员年度消费直接访问订单模块数据库表 `orders`，而不是通过公开本地接口、REST API、领域服务或事件协作。
4. 原因分析与影响：该实现把会员等级计算耦合到订单表结构和状态字段，破坏模块边界；订单表结构或状态变更会直接影响 loyalty-service，增加跨模块变更风险。

## 6. 积分抵扣预估接口缺少请求参数校验，错误码边界可能不符合统一约定

1. 实现位置：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/dto/PointsEstimateRequest.java:8-11`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/LoyaltyController.java:71-79`
2. 设计依据：`README.md` 第 7 章错误码，`200-230` 行，特别是 `206` 行规定 `VALIDATION_FAILED` 对应 400 “请求参数校验失败”；`design-docs/01-项目概述.md` 第 7 章统一响应格式，`86-95` 行规定错误响应结构。
3. 不一致内容：`PointsEstimateRequest` 未声明任何参数校验约束；控制器直接读取 `request.getOrderAmount()`、`request.getRedeemPoints()` 并参与计算。对缺失、空值或非法数值请求，本模块没有把参数校验失败明确映射为 `VALIDATION_FAILED` 400。
4. 原因分析与影响：积分抵扣预估属于积分场景请求参数处理；缺少校验会导致非法请求绕过统一校验边界，空金额等情况可能进入业务计算并触发内部异常，从而返回非预期错误码/状态，影响 API 契约一致性。
