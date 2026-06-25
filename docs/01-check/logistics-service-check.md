# M9 logistics-service 一致性审查报告

审查范围：`code/ecommerce-logistics/`、`code/pom.xml`，以及题目允许读取的 `README.md` / `design-docs/01-项目概述.md` 指定行号范围。未修复代码。

发现不一致条数：4

## 1. 物流拣货冻结 API 按契约应 200，但当前实现会因空 pickerId 失败

1. 实现位置：
   - `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/controller/AdminLogisticsController.java:45-49`
   - `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/ShipmentService.java:133-163`
2. 设计依据：
   - `README.md` API 基线：`README.md:73-75` 要求冻结 URL、HTTP Method、认证、成功状态码、错误响应结构。
   - 物流 API 契约：`README.md:166-167` 规定 `POST /api/v1/admin/logistics/shipments/{shipmentId}/pick`、认证 `ADMIN`、成功状态 `200`。
   - 修改边界：`README.md:35-38` 禁止修改 REST API URL、Method、Header、Request/Response 字段和 `/api/v1/` 前缀。
3. 不一致内容：
   - 控制器实现的路径变量名为 `{id}`，但冻结契约为 `{shipmentId}`；同时该接口没有请求体或额外参数，却在 `AdminLogisticsController.java:48` 调用 `shipmentService.pick(id, null)`。
   - `ShipmentService.java:162-163` 会拼接并调用 `pickerId.toString()`，当控制器传入 `null` 时会触发空指针异常，导致冻结 API 不能稳定返回契约要求的 `200` 成功状态。
4. 原因分析与影响：
   - 冻结 API 未提供 pickerId 字段，实现却依赖 pickerId 非空，控制器与服务层参数模型不一致。
   - 黑盒调用契约中的拣货接口时可能直接得到 500，而非成功 200；同时路径变量命名与冻结契约不一致，属于 REST 契约细节不一致。

## 2. 物流回调 API 标记为“签名”认证，但实现未做签名校验

1. 实现位置：
   - `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/controller/LogisticsController.java:56-61`
   - `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/LogisticsCallbackService.java:33-39`
   - `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/dto/LogisticsCallbackRequest.java:28-29`
2. 设计依据：
   - 物流 API 契约：`README.md:170` 规定 `POST /api/v1/logistics/callback` 的认证方式为“签名”、成功状态 `200`。
   - API 基线：`README.md:73-75` 要求认证/错误响应结构属于冻结契约内容。
3. 不一致内容：
   - DTO 只把 `signature` 作为普通请求字段声明；控制器直接调用 `callbackService.processCallback(request)`。
   - `LogisticsCallbackService.processCallback` 仅记录 `request.getSignature()` 到日志，没有校验签名是否存在、是否有效，也没有在签名非法时返回认证/权限类错误。
4. 原因分析与影响：
   - 冻结契约要求该接口通过签名认证，而当前实现等同于接受任意请求体。
   - 未签名或伪造的物流回调也会被接受并返回 200，破坏物流状态变更入口的认证边界。

## 3. 物流回调未更新物流状态、签收时间和轨迹，无法支撑“物流轨迹/签收”职责

1. 实现位置：
   - `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/LogisticsCallbackService.java:33-39`
   - `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/LogisticsCallbackService.java:44-61`
   - `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/ShipmentService.java:247-263`
   - `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/ShipmentService.java:312-325`
2. 设计依据：
   - M9 职责：`design-docs/01-项目概述.md:32-44` 规定 M9 logistics-service 负责“发货、拣货、面单、物流轨迹、签收、运费模板”。
   - 关键业务原则：`design-docs/01-项目概述.md:60` 规定评价必须校验订单已签收。
   - 物流 API 契约：`README.md:166-170` 暴露订单物流查询、拣货、面单、出库、物流回调能力。
3. 不一致内容：
   - `LogisticsCallbackService.processCallback` 只记录日志，不根据 `trackingNo` 查找运单，不调用 `ShipmentService.updateStatus`，也不写入 `ShipmentTracking`。
   - `mapToShipmentStatus` 能把 `DELIVERED` 映射为签收状态，但未被 `processCallback` 使用。
   - `ShipmentService.updateStatus` 已具备在 `DELIVERED` 时设置 `deliveredAt`、并记录轨迹的能力，但物流回调入口没有接入该能力。
4. 原因分析与影响：
   - 外部承运商回调是契约中唯一明确的物流状态回写入口，但当前入口不会改变持久化状态或轨迹。
   - `GET /api/v1/logistics/order/{orderId}` 返回的 `status`、`deliveredAt`、`trackingRecords` 不能反映回调中的签收/轨迹事件；其他模块只能看到过期物流状态，无法可靠基于 logistics-service 判断订单是否已签收。

## 4. 支付成功后的物流创建未体现本地事件异步触发

1. 实现位置：
   - `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/ShipmentService.java:68-100`
   - `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/config/LogisticsConfig.java:10-15`
2. 设计依据：
   - 技术栈：`design-docs/01-项目概述.md:19` 指定事件机制为 Spring ApplicationEvent。
   - 关键业务原则：`design-docs/01-项目概述.md:55` 规定支付成功后的物流创建等后置动作通过本地事件异步触发，不得阻塞支付主流程。
   - 系统协作原则：`design-docs/01-项目概述.md:7` 规定模块间应通过公开本地接口、REST API、领域服务或 Spring ApplicationEvent 协作。
3. 不一致内容：
   - logistics-service 仅提供 `ShipmentService.createShipment(...)` 直接创建物流单的方法；模块配置仅做组件扫描。
   - 在 `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/` 范围内未发现 `ApplicationEvent`、`@EventListener`、`@TransactionalEventListener` 或 `@Async` 形式的支付成功事件监听与异步创建物流实现。
4. 原因分析与影响：
   - 文档明确要求支付成功后的物流创建是本地事件异步后置动作，而当前 logistics-service 未暴露/实现该异步事件消费入口。
   - 支付成功后无法由 logistics-service 按设计自动异步创建物流；若由其他同步调用补足，会与“不阻塞支付主流程”的原则不一致。
