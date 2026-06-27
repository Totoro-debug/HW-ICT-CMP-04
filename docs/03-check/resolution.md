# 一致性问题修复方案汇总

### APP-001

**模块**：`ecommerce-app`
**问题描述**：设计要求登录、支付回调、商品搜索、创建订单分别按用户名、paymentNo、IP、用户进行本地限流，触发时返回 HTTP 429 且错误码为 `RATE_LIMITED`；当前 `SecurityConfig.securityFilterChain` 仅配置认证授权和 JWT 过滤器，app 装配层未启用针对这些入口的限流能力。
**修复方案**：在具体入口 controller 方法上使用 common 模块已有 `@RateLimit` 能力：用户登录方法按用户名配置每分钟 5 次；支付回调方法按 `paymentNo` 配置每分钟 20 次；商品搜索方法按客户端 IP 配置每分钟 120 次；创建订单方法按当前用户 ID 配置每分钟 20 次。若现有 key 表达式无法获取 IP 或当前用户，在 app 层新增一个轻量 `RateLimitKeyProvider`/SpEL helper bean 供注解引用；保持 `GlobalExceptionHandler.handleRateLimit` 统一返回 429/`RATE_LIMITED`。
**验证方式**：增加或调整对应接口的集成测试，分别在 1 分钟窗口内超过阈值调用登录、支付回调、商品搜索、创建订单，断言响应状态为 429、错误码为 `RATE_LIMITED`；同时验证阈值内请求仍按原业务逻辑处理。

### APP-002

**模块**：`ecommerce-app`
**问题描述**：设计要求失败事件可通过管理接口重放；当前 `EventFailureAdminController` 只提供 `GET /api/v1/admin/events/failures` 查询失败记录，未提供 replay/retry/reprocess 管理入口。
**修复方案**：在 common 模块补齐 `FailedEventReplayService` 后，在 `EventFailureAdminController` 注入该服务，新增 `POST /api/v1/admin/events/failures/{id}/replay` 用于按失败记录 ID 重放，必要时新增 `POST /api/v1/admin/events/failures/replay` 支持按 `eventType` 批量重放；controller 只负责编排入参、调用重放服务并返回重放结果，不在 app 层实现事件处理细节。
**验证方式**：构造一条失败事件记录后调用新增 ADMIN replay 接口，断言接口返回成功、失败记录状态/重放时间/错误信息按服务约定更新，并验证 `/api/v1/admin/events/failures` 可查询到更新后的记录；同时验证非 ADMIN 用户无法访问该接口。

### APP-003

**模块**：`ecommerce-app`
**问题描述**：设计要求用户冻结/解冻、商品上下架、库存人工调整、订单取消审核、退款审核和仓库验收、发票开具、结算批次生成等操作必须记录包含操作者、操作类型、业务 ID、操作前后状态、操作时间和备注的审计日志；当前 app 模块未发现审计日志统一装配或管理入口。
**修复方案**：在 common 模块提供 `AuditLog` 实体、Repository、`AuditLogService` 和查询 DTO 后，由 app 启动模块通过组件扫描自动装配；在 app 模块新增 `AuditAdminController`，例如 `GET /api/v1/admin/audit-logs`，按操作类型、业务 ID、操作者、时间范围分页查询审计日志。业务模块在各指定操作处调用 `AuditLogService.record(...)`，app 层只暴露统一 ADMIN 查询入口。
**验证方式**：对任一指定审计场景执行操作后，通过新增 ADMIN 审计查询接口断言返回记录包含操作者、操作类型、业务 ID、操作前状态、操作后状态、操作时间和备注；验证 ADMIN 权限控制生效。

### COMMON-001

**模块**：`ecommerce-common`
**问题描述**：设计要求最终入库金额保留 2 位小数并使用 `RoundingMode.HALF_UP`；当前 `MonetaryUtil.roundToCent` 注释和实现使用 `RoundingMode.HALF_DOWN`，会使 `0.005` 舍入为 `0.00`。
**修复方案**：修改 `MonetaryUtil.roundToCent(BigDecimal amount)`，将 `amount.setScale(SCALE, RoundingMode.HALF_DOWN)` 改为 `amount.setScale(SCALE, RoundingMode.HALF_UP)`，同步更新类注释和方法注释中的舍入说明。
**验证方式**：新增或调整 `MonetaryUtil` 单元测试，断言 `roundToCent(new BigDecimal("0.005"))` 返回 `0.01`，并覆盖常规正数、负数和 null 输入。

### COMMON-002

**模块**：`ecommerce-common`
**问题描述**：设计要求中间计算保留足够精度，不提前截断；当前 `MonetaryUtil.add`、`subtract`、`multiply` 每次基础运算后都调用 `roundToCent`，无法区分中间计算和最终入库金额。
**修复方案**：调整 `MonetaryUtil.add(BigDecimal, BigDecimal)`、`subtract(BigDecimal, BigDecimal)`、`multiply(BigDecimal, BigDecimal)`，使其只完成空值归零和 BigDecimal 运算，不调用 `roundToCent`；保留 `roundToCent` 作为最终入库或税额到分的显式方法。若需兼容已有调用，可新增 `addAndRoundToCent`、`subtractAndRoundToCent`、`multiplyAndRoundToCent` 并逐步迁移明确需要最终舍入的调用点。
**验证方式**：新增连续计算单元测试，例如多次乘加后验证中间结果未被截断；同时验证显式调用 `roundToCent` 时才保留 2 位小数。

### COMMON-003

**模块**：`ecommerce-common`
**问题描述**：设计要求优惠金额不得小于 0、不得大于商品金额，应付金额不得小于 0.01；当前 common 模块未提供优惠金额边界校验、应付金额下限校验或订单金额校验辅助方法。
**修复方案**：在 `MonetaryUtil` 或新增 `MoneyValidationUtil` 中提供 `validateDiscountAmount(BigDecimal discountAmount, BigDecimal itemAmount)` 和 `validatePayableAmount(BigDecimal payableAmount)` 等方法；校验失败时抛出 `OrderValidationException`，错误码区分优惠金额非法和应付金额过低；业务订单金额校验统一调用该工具。
**验证方式**：新增 common 单元测试覆盖优惠金额为负、优惠金额大于商品金额、应付金额小于 `0.01`、边界值 `0`/`0.01`/等于商品金额等场景，断言失败场景抛出 `OrderValidationException`。

### COMMON-004

**模块**：`ecommerce-common`
**问题描述**：设计要求泛化 `BusinessException` 对应 HTTP 400，权限和冲突语义应使用 `AuthorizationException`、`ConflictException`；当前 `GlobalExceptionHandler.handleBusiness` 会根据部分业务错误码返回 403 或 409。
**修复方案**：移除 `GlobalExceptionHandler` 中 `FORBIDDEN_BUSINESS_CODES`、`CONFLICT_BUSINESS_CODES` 和 `resolveBusinessHttpStatus` 的分支映射，让 `handleBusiness(BusinessException ex)` 始终返回 `HttpStatus.BAD_REQUEST`；需要 401/403 或 409 的调用点改抛 `AuthorizationException` 或 `ConflictException`。
**验证方式**：新增/调整异常处理测试，构造普通 `BusinessException` 以及 code 为 `USER_FROZEN`、`ORDER_STATUS_CONFLICT` 的 `BusinessException`，均断言 HTTP 400；分别验证 `AuthorizationException` 和 `ConflictException` 仍返回 401/403 与 409。

### COMMON-005

**模块**：`ecommerce-common`
**问题描述**：设计要求创建订单、支付回调、退款申请、物流回调、发票申请必须按指定幂等键处理，并且重复请求返回第一次处理结果；当前 common 模块除通知 `idempotencyKey` 外，没有提供通用接口幂等注解、拦截器、存储或工具。
**修复方案**：在 common 新增幂等组件：`@Idempotent` 注解声明业务类型和 key SpEL；`IdempotencyRecord` 实体/Repository 持久化幂等键、请求摘要、响应摘要、状态和过期时间；`IdempotencyAspect` 在业务方法执行前按 key 查询已完成记录并返回第一次结果，执行中记录处理中状态，成功后保存响应，失败时按策略清理或标记失败。业务模块按设计分别配置 `externalOrderNo`、`paymentNo + callbackSequence`、`refundRequestNo`、`trackingNo + eventTime + status`、`invoiceRequestNo`。
**验证方式**：为幂等切面编写单元/集成测试，验证相同 key 第二次调用不重复执行目标方法并返回第一次结果；为五类接口各增加重复请求测试，断言不重复扣款、扣库存、发积分或开票。

### COMMON-006

**模块**：`ecommerce-common`
**问题描述**：设计要求审计日志至少包含操作者、操作类型、业务 ID、操作前状态、操作后状态、操作时间和备注；当前 common 仅有 `BaseEntity.createdAt/updatedAt`，不存在专门审计日志模型和服务。
**修复方案**：新增 `AuditLog` 实体，字段包括 `operatorId/operatorName`、`operationType`、`bizType`、`bizId`、`beforeState`、`afterState`、`operatedAt`、`remark`；新增 `AuditLogRepository`、`AuditLogService.record(...)` 和查询 DTO。业务模块在设计列出的操作完成后调用 `AuditLogService` 写入记录，app 模块再暴露统一 ADMIN 查询接口。
**验证方式**：新增 `AuditLogService` 持久化测试，断言所有必填字段可保存和查询；对一个业务审计调用点做集成测试，验证操作成功后产生符合字段要求的审计日志。

### COMMON-007

**模块**：`ecommerce-common`
**问题描述**：设计要求通知组件负责失败记录，事件监听器失败时记录失败日志并保存失败记录；当前 `LocalNotificationServiceImpl.send` 捕获异常后只写日志、不保存失败记录且不重新抛出，导致 `CoreNotificationEventListener` 外层可能捕获不到异常并无法执行失败持久化。
**修复方案**：为通知失败新增持久化模型或复用事件失败记录能力。推荐在 `LocalNotificationServiceImpl` catch 分支调用 `NotificationFailureRecordService.recordFailure(request, e)` 保存通知失败记录，并在由事件监听器调用的路径上重新抛出自定义 `NotificationSendException`，让 `CoreNotificationEventListener` 的 `persistFailure` 能保存事件失败记录；若直连业务调用不希望中断主流程，则通过调用参数或异步包装明确失败传播策略。
**验证方式**：启用 `notification-send-failure` 故障注入后调用通知发送，断言通知失败记录被持久化；通过事件监听器触发通知失败时，断言本地事件失败表也出现对应记录，且主业务事务不被回滚。

### COMMON-008

**模块**：`ecommerce-common`
**问题描述**：设计要求失败事件可通过管理接口重放；当前 common 模块只有 `FailedEventRecordQueryService.findFailures` 查询能力，没有 replay 服务、管理接口或重放状态更新逻辑。
**修复方案**：新增 `FailedEventReplayService`，按失败记录 ID 读取 `FailedEventRecord`，反序列化事件载荷并重新发布或直接调用可注册的事件处理器；为记录增加或使用现有状态字段标记 `PENDING/REPLAYING/SUCCEEDED/FAILED`、重放次数、最后错误和重放时间。app 模块的 ADMIN controller 调用该服务暴露重放接口。
**验证方式**：构造失败记录后调用 `FailedEventReplayService.replay(id)`，断言成功时状态更新为 `SUCCEEDED` 且重放次数增加；重放再次失败时状态保持/更新为 `FAILED` 并保存最后错误；再通过 app 管理接口做端到端验证。

### COMMON-009

**模块**：`ecommerce-common`
**问题描述**：设计要求事件监听器默认不回滚主业务事务，除非该监听器被明确声明为强一致监听器；当前 common 事件处理未提供强一致监听器标记、注解、接口或发布器分支策略，无法表达强一致与非强一致差异。
**修复方案**：新增强一致声明机制，例如 `@StrongConsistencyEventListener` 注解或 `StrongConsistencyEventHandler` 标记接口；在 `DomainEventPublisher` 或事件分发层识别该标记。非强一致监听器异常时记录失败并吞掉异常以避免回滚主业务；强一致监听器异常时记录失败后重新抛出，使事务按设计回滚。
**验证方式**：新增两个测试监听器分别标记强一致和非强一致：非强一致监听器抛异常时断言发布方不抛出且失败记录被保存；强一致监听器抛异常时断言异常向上传播并可触发事务回滚，同时也保存失败记录。


### CART-001

**模块**：`ecommerce-cart`
**问题描述**：设计要求金额中间计算保留足够精度、不提前截断，最终入库/输出到分时使用 `RoundingMode.HALF_UP`；当前 `CartService` 在行小计、商品总额、优惠后金额、积分抵扣后金额等中间步骤多次调用 `MonetaryUtil.multiply/add/subtract`，而 `MonetaryUtil` 每次都会按 2 位小数舍入且当前使用 `HALF_DOWN`，存在提前截断/舍入偏差风险。
**修复方案**：在 `com.ecommerce.cart.service.CartService` 中调整估价计算边界：`estimate` 内部使用原始 `BigDecimal` 的 `add/subtract/multiply` 完成高精度中间累计，避免在 `itemTotal`、`prePointsAmount` 等中间变量上调用会立即到分的工具方法；仅在构造 `CartEstimateResponse` 或持久化/对外返回前统一调用金额规范方法到 2 位小数。同步修正 `com.ecommerce.common.money.MonetaryUtil.roundToCent` 的舍入模式为 `RoundingMode.HALF_UP`，并避免 `add/subtract/multiply` 被用于需要保留中间精度的链式计算。
**验证方式**：新增/调整 `CartService` 单元测试，构造半分或多步舍入差异用例，断言中间累计不因逐步到分产生偏差，最终响应金额统一为 2 位小数且 `.005` 场景按 `HALF_UP` 进位；补充 `MonetaryUtil.roundToCent(new BigDecimal("0.005")) == 0.01` 回归测试。

### CART-002

**模块**：`ecommerce-cart`
**问题描述**：设计要求优惠金额不得小于 0、不得大于商品金额；当前 `CartService.calculateDiscountAmount` 直接返回促销模块的 `totalDiscount`（仅将 `null` 转为 `BigDecimal.ZERO`），未对负数或超过商品总额的优惠做边界校验/裁剪，响应可能暴露不符合规范的 `discountAmount`。
**修复方案**：在 `com.ecommerce.cart.service.CartService.estimate` 取得 `discountAmount` 后增加金额边界防御，推荐新增私有方法 `normalizeDiscountAmount(BigDecimal discountAmount, BigDecimal itemTotal)`：`null` 按 0 处理，小于 0 时按 0 或抛出 `BusinessException`，大于 `itemTotal` 时按 `itemTotal` 裁剪或按业务约定抛出 `BusinessException`；优先在 cart 聚合层裁剪并记录日志，确保后续计算和响应中的优惠金额始终位于 `[0, itemTotal]`。
**验证方式**：为 `CartService.estimate` 增加促销服务 mock 返回负数、返回大于商品总额、返回正常值、返回 `null` 的测试；断言负数被归零、超额被限制为商品总额、正常值保持不变，并验证响应中的 `discountAmount` 与后续 `payableAmount` 均符合边界。

### CART-003

**模块**：`ecommerce-cart`
**问题描述**：设计要求应付金额不得小于 0.01，0 元订单不在本系统支持范围内；当前 `CartService` 对空购物车估价返回 `payableAmount = BigDecimal.ZERO`，并在优惠/积分抵扣后金额小于 0 时将应付金额置为 0，未阻断 0 元估价结果。
**修复方案**：在 `com.ecommerce.cart.service.CartService.estimate` 的应付金额计算末尾新增统一校验：若非空购物车估价后的 `payableAmount.compareTo(new BigDecimal("0.01")) < 0`，抛出 `BusinessException`（建议错误码如 `PAYABLE_AMOUNT_TOO_LOW`）或按产品规则阻断结算入口，不再返回 0 元可结算估价；`emptyEstimate` 若仅表示空购物车展示，应明确只用于空购物车响应并避免进入订单创建链路，若估价接口也被结算前置调用，则空购物车应改为抛出购物车为空的业务异常。
**验证方式**：新增空购物车估价、优惠后金额为 0、积分抵扣后金额小于 0.01、正常应付金额等测试；断言前 3 类场景被业务异常阻断或不产生可结算 0 元结果，正常场景返回的 `payableAmount >= 0.01` 且保留 2 位小数。

### INVENTORY-001

**模块**：`ecommerce-inventory`
**问题描述**：设计要求“库存人工调整”必须记录审计日志，且至少包含操作者、操作类型、业务 ID、操作前状态、操作后状态、操作时间和备注；当前 `StockAdjustmentService.create` 仅保存库存调整业务记录 `StockAdjustment`（warehouseId、skuId、beforeQty、afterQty、reason）并输出普通 `log.info`，缺少操作者、操作类型、业务 ID、操作时间等完整审计日志字段，也未接入独立审计日志能力。
**修复方案**：在 `com.ecommerce.inventory.service.StockAdjustmentService.create` 完成库存变更和 `StockAdjustment` 保存后，调用通用审计日志服务记录 `operationType=STOCK_ADJUSTMENT`、`businessId=saved.getId()` 或 `warehouseId+skuId`、`beforeState`、`afterState`、`operator`、`operationTime`、`remark=reason`。`AdminInventoryController.createAdjustment` 不改变 API 契约，可由服务层从 Spring Security 当前认证主体或管理端上下文获取操作者。
**验证方式**：新增 `StockAdjustmentService` 测试，mock/断言审计服务在库存人工调整成功后被调用一次且字段完整；新增异常路径测试，确认库存不存在或保存失败时不记录成功审计；如新增审计表，增加持久化测试验证必填字段落库。

### INVENTORY-002

**模块**：`ecommerce-inventory`
**问题描述**：设计要求重复请求不得重复扣库存；当前 `InventoryReservationServiceImpl.reserve(Long orderId, List<ReserveItem> items)` 未按 `orderId` 或 `orderId + skuId + warehouseId` 检查既有预占，也未在 `stock_reservation` 上声明业务唯一约束，同一订单重复调用会重复增加 `reservedStock` 并新增 `StockReservation`，后续 `deductAfterPayment` 会处理重复 `RESERVED` 记录，存在重复扣库存风险。
**修复方案**：在 `com.ecommerce.inventory.service.InventoryReservationServiceImpl.reserve` 方法开头使用 `StockReservationRepository.findByOrderId(orderId)` 查询已有记录：若已存在且与本次请求匹配，则直接返回第一次处理结果/保持无副作用；若存在但明细不一致，则抛出 `ConflictException` 或 `BusinessException` 表示幂等键冲突。为 `StockReservation` 增加数据库唯一约束（建议 `order_id + sku_id + warehouse_id`）作为并发兜底。
**验证方式**：新增 `InventoryReservationServiceImpl` 测试：同一 `orderId` 相同 items 连续调用两次后，`reservedStock` 只增加一次、`stock_reservation` 只保留第一次的业务记录；同一 `orderId` 不同 items 重复调用返回冲突；并发重复调用验证不会产生重复预占。

### INVENTORY-003

**模块**：`ecommerce-inventory`
**问题描述**：设计要求重复请求不得重复扣库存；当前管理端 `POST /api/v1/admin/inventory/outbound` 每次都会进入 `InventoryService.outbound` 扣减 `onHandStock` 并新增 `OutboundOrder`，未按 `orderId` 或出库单号返回第一次处理结果，相同 `orderId` 重复请求会重复扣减现货库存。
**修复方案**：在 `com.ecommerce.inventory.service.InventoryService.outbound(Long warehouseId, Long skuId, int quantity, Long orderId)` 开头使用 `OutboundOrderRepository.findByOrderId(orderId)` 做幂等判断：如果已有相同 `warehouseId/skuId/quantity/orderId` 且状态为已完成的出库单，直接返回当前库存或第一次处理后的可识别结果，不再扣减库存；如果同一 `orderId` 对应不同出库参数，抛出 `ConflictException` 或 `BusinessException`。为 `OutboundOrder` 增加 `order_id` 或 `order_id + sku_id + warehouse_id` 唯一约束作为并发兜底。
**验证方式**：新增 `InventoryService.outbound` 单元/集成测试：相同 `orderId` 重复调用两次后 `onHandStock` 只减少一次、`OutboundOrder` 不重复新增；相同 `orderId` 但不同 sku/数量/仓库时返回冲突；库存不足场景仍返回原有业务异常且不创建出库单。

### INVENTORY-004

**模块**：`ecommerce-inventory`
**问题描述**：设计要求事件监听器失败时记录失败日志、保存失败记录到本地事件处理表、不回滚主业务事务，并可通过管理接口重放失败事件；当前 inventory 模块未发现事件监听器、本地事件处理失败记录实体/仓储或重放管理接口，无法满足本地事件失败处理规范。
**修复方案**：若 inventory 模块需要消费本地事件（例如订单创建后预占库存、支付成功后扣减库存、订单取消后释放库存），新增明确的事件监听器类（如 `InventoryOrderEventListener`）并将非强一致处理包装在 try/catch 中：失败时调用本地事件失败记录服务保存事件类型、事件键、payload、异常信息、处理状态和下次重试时间，同时记录失败日志且不向外抛出导致主事务回滚；新增管理端重放接口调用重放服务重试失败事件。若 inventory 模块确认不承担任何事件监听职责，则由实际承担监听职责的模块实现上述失败处理。
**验证方式**：新增监听器失败场景测试，模拟预占/扣减/释放库存处理抛异常，断言主业务事务不回滚、失败日志被记录、失败事件表写入完整 payload 和异常信息；新增管理端重放接口测试，验证失败事件可被重新处理并更新状态。

### INVENTORY-005

**模块**：`ecommerce-inventory`
**问题描述**：设计要求本地限流触发时返回 429，错误码为 `RATE_LIMITED`；inventory 模块虽不包含规范列明的登录、支付回调、商品搜索、创建订单接口，但自身暴露 `POST /api/v1/admin/inventory/outbound`、`POST /api/v1/admin/inventory/adjustments`、`POST /api/v1/inventory/check` 等接口，代码中未发现统一限流机制或明确豁免说明，无法确认接口限流行为。
**修复方案**：先在系统统一限流组件中确认 inventory 接口是否应纳入本地限流；若需纳入，在 Web 层新增/复用限流拦截器或注解覆盖 `AdminInventoryController.outbound`、`AdminInventoryController.createAdjustment` 以及库存查询/检查接口，限流维度可按管理员账号、调用方 IP 或 sku 组合设置，并统一抛出/映射 `RateLimitException`，确保 HTTP 429 和错误码 `RATE_LIMITED`。若设计仅要求列明的四类接口限流且 inventory 接口豁免，则在模块配置或代码侧集中说明豁免。
**验证方式**：新增 Web 层限流测试，连续超过阈值调用被纳入限流的 inventory 接口时返回 429 且响应错误码为 `RATE_LIMITED`；未超过阈值时正常通过；若选择豁免，增加配置/单元测试证明 inventory 接口不属于既有限流规则并由网关或统一组件覆盖。


### LOGISTICS-001

**模块**：`ecommerce-logistics`
**问题描述**：设计要求最终入库金额统一保留 2 位小数且使用 `RoundingMode.HALF_UP`；当前 `FreightCalculator`、`FreightTemplateService`、`ShipmentService` 在运费模板创建/更新、运费计算和 shipment 运费写入前未显式执行 `setScale(2, RoundingMode.HALF_UP)` 或统一金额规整，只依赖 JPA 字段 scale。
**修复方案**：在 `FreightCalculator` 中新增或调用统一的运费金额规整方法，例如 `normalizeFreightAmount(BigDecimal)`，对 `calculate(...)` 的返回值和免费/默认运费分支统一执行 `setScale(2, RoundingMode.HALF_UP)`；在 `FreightTemplateService.createTemplate(...)`、`FreightTemplateService.updateTemplate(...)` 写入 `defaultFreight`、`freeShippingThreshold` 前执行同一规整；在 `ShipmentService.createShipment(...)` 保存 `Shipment.freightAmount` 前确保使用规整后的金额。
**验证方式**：为 `FreightCalculator` 增加覆盖 `0.005`、`1.235`、免费阈值和默认运费分支的单元测试，断言结果按 `HALF_UP` 保留 2 位；为运费模板创建/更新和 shipment 创建增加持久化测试，断言入库金额 scale 为 2 且舍入结果符合设计。

### LOGISTICS-002

**模块**：`ecommerce-logistics`
**问题描述**：设计要求状态冲突、重复提交使用 `ConflictException` 并映射 HTTP 409；当前 `PickListService.completePicking` 在拣货单状态不允许完成时抛出 Java 标准 `IllegalStateException`。
**修复方案**：将 `PickListService.completePicking(...)` 中状态校验失败分支的 `IllegalStateException` 替换为 `ConflictException`，错误码和消息表达“拣货单状态冲突/当前状态不可完成”；保持其他资源不存在场景继续使用 `ResourceNotFoundException`。
**验证方式**：新增或调整 `PickListService` 单元测试，构造非可完成状态的拣货单调用 `completePicking(...)`，断言抛出 `ConflictException`；若存在 controller 集成测试，进一步断言 HTTP 状态为 409 且错误结构统一。

### LOGISTICS-003

**模块**：`ecommerce-logistics`
**问题描述**：设计要求物流回调按 `trackingNo + eventTime + status` 作为幂等键，重复请求返回第一次处理结果；当前 `LogisticsController`/`LogisticsCallbackService` 只按 `trackingNo` 查找 shipment 并执行状态更新，`eventTime` 未参与幂等查询或唯一约束，`ShipmentTrackingRepository` 也未提供幂等记录查询与首次结果缓存。
**修复方案**：在物流回调处理链路引入幂等记录，优先复用 common 的 `@Idempotent`/幂等记录组件；若模块内实现，则新增 `LogisticsCallbackIdempotencyRecord` 实体与 Repository，对 `trackingNo`、`eventTime`、`status` 建唯一索引，并保存首次处理响应摘要。`LogisticsCallbackService.handleCallback(...)` 进入业务更新前先按该键查询已完成记录，命中时直接返回首次结果；首次请求完成状态更新、`ShipmentTracking` 写入和事件发布后保存处理结果。
**验证方式**：增加物流回调集成测试，使用相同 `trackingNo + eventTime + status` 连续调用两次，断言第二次返回第一次结果，`ShipmentTracking` 只新增一次，shipment 状态更新和后续事件副作用只发生一次；不同 `eventTime` 或不同 `status` 应作为新事件正常处理。

### LOGISTICS-004

**模块**：`ecommerce-logistics`
**问题描述**：设计要求黑盒测试隔离由测试 harness 提供，业务代码不得依赖测试支撑接口；当前 `ShipmentService.createShipment` 直接调用 `com.ecommerce.common.test.FaultInjectionRegistry.isActive(...)` 并在生产业务路径中根据测试开关注入失败。
**修复方案**：从 `ShipmentService.createShipment(...)` 移除对 `FaultInjectionRegistry` 的直接依赖和故障注入分支；如确需测试失败场景，通过测试 profile 下的 mock bean、测试专用配置或 harness 层拦截来制造异常，避免 `com.ecommerce.common.test` 包出现在 main 业务代码依赖中。
**验证方式**：静态检查 `code/ecommerce-logistics/src/main/java` 下不再引用 `com.ecommerce.common.test.FaultInjectionRegistry`；运行 `ShipmentService` 创建 shipment 的正常路径测试，确认移除测试开关后业务行为不变；失败场景测试改由 harness/mock 注入异常并保持可覆盖。

### LOGISTICS-005

**模块**：`ecommerce-logistics`
**问题描述**：设计要求仓库验收必须记录审计日志，字段至少包含操作者、操作类型、业务 ID、操作前状态、操作后状态、操作时间和备注；当前 logistics 模块未找到审计日志实体、服务或调用，现有 `ShipmentTracking` 仅记录物流轨迹，不包含审计操作类型和操作前后状态等字段。
**修复方案**：在仓库验收对应的业务方法中注入 common 审计能力，例如 `AuditLogService.record(...)`；验收前读取并保存业务对象原状态，验收成功后以操作类型 `WAREHOUSE_ACCEPTANCE`、业务 ID（shipment/order/pick list ID）、操作者、beforeState、afterState、operatedAt、remark 写入审计日志。若当前仓库验收入口尚未独立建模，应在实现该入口时同步加入审计调用，而不是用 `ShipmentTracking` 替代。
**验证方式**：新增仓库验收服务/接口集成测试，执行验收后查询审计日志，断言包含操作者、操作类型、业务 ID、操作前状态、操作后状态、操作时间和备注；同时断言物流轨迹记录与审计日志各自独立存在。

### LOGISTICS-006

**模块**：`ecommerce-logistics`
**问题描述**：设计要求支付成功后的物流监听器失败时记录失败日志、保存失败记录到本地事件处理表，并可通过管理接口重放；当前 `OrderPaidShipmentListener` 失败时只 `log.error`，未发现失败事件实体、Repository 或保存调用。
**修复方案**：在 `OrderPaidShipmentListener.handleOrderPaid(...)` 的 catch 分支注入并调用本地失败事件记录能力，例如 `FailedEventRecordRepository`/`FailedEventRecordService`，保存事件类型、事件 ID 或 orderId、事件载荷、异常信息、状态、失败时间和重试次数；保持 `@Async` + `AFTER_COMMIT` 非强一致语义，不向主支付事务传播异常；配合 app/common 的失败事件 replay 服务提供重放入口。
**验证方式**：构造 `ShipmentService.createShipment(...)` 抛异常的监听器测试，触发支付成功事件后断言主流程不回滚、日志记录存在且本地事件失败表新增一条物流发货失败记录；调用重放服务或管理接口后验证可重新处理并更新失败记录状态。

### LOYALTY-001

**模块**：`ecommerce-loyalty`
**问题描述**：设计要求金额最终入库或输出统一按 `RoundingMode.HALF_UP` 保留 2 位；当前 `LoyaltyController` 计算积分抵扣金额 `redeemAmount` 时使用 `divide(..., 2, RoundingMode.DOWN)`，与统一舍入模式不一致。
**修复方案**：将 `LoyaltyController` 中 `redeemAmount` 的积分除以 100 计算改为 `RoundingMode.HALF_UP`，或抽取到 `LoyaltyPointService`/金额工具中的 `calculateRedeemAmount(...)` 并统一调用 `setScale(2, RoundingMode.HALF_UP)`；避免 controller 中散落金额舍入规则。
**验证方式**：新增 controller 或服务层测试覆盖可产生第三位小数的积分换算输入，断言抵扣金额按 `HALF_UP` 保留 2 位；保留 100 积分等于 1 元等常规场景回归测试。

### LOYALTY-002

**模块**：`ecommerce-loyalty`
**问题描述**：设计要求所有金额使用 `BigDecimal`，禁止使用 `double` 或 `float` 表示金额；当前 `LoyaltyPointService` 和 `MemberBenefitService` 使用 `double` 表达会员倍率、活动倍率，并通过 `BigDecimal.valueOf(double)` 参与基于订单金额的积分价值计算链路。
**修复方案**：将 `LoyaltyPointService` 中参与金额/积分价值计算的倍率变量和方法返回值改为 `BigDecimal`，例如会员倍率、活动倍率统一返回 `BigDecimal`；将 `MemberBenefitService` 的倍率常量由 `double` 改为字符串构造的 `BigDecimal`（如 `new BigDecimal("1.2")`），计算时使用 `amount.multiply(multiplier)` 并只在最终积分取整处执行明确规则。
**验证方式**：静态检查 loyalty 主代码中不再存在参与金额换算的 `double`/`float` 倍率；新增积分计算测试覆盖不同会员等级和活动倍率，断言结果与十进制精确计算一致且无二进制浮点误差。

### LOYALTY-003

**模块**：`ecommerce-loyalty`
**问题描述**：设计要求订单金额校验失败必须抛出 `OrderValidationException`，不得抛出 Java 标准 `IllegalArgumentException`；当前积分抵扣估算只依赖 `PointsEstimateRequest` 的 Bean Validation，`LoyaltyPointService.redeemPoints` 未显式校验 `orderAmount >= 0.01` 并在失败时抛出 `OrderValidationException`。
**修复方案**：在 `LoyaltyPointService.redeemPoints(...)` 和积分估算入口调用统一订单金额校验方法，校验 `orderAmount` 非空且不小于 `0.01`；失败时抛出 `OrderValidationException` 并设置明确错误码。`PointsEstimateRequest` 可保留 Bean Validation 作为入参基础校验，但业务金额语义以 `OrderValidationException` 为准。
**验证方式**：新增服务层测试分别传入 `null`、`0`、`0.009`、负数和 `0.01`，断言非法金额抛出 `OrderValidationException`，边界值 `0.01` 可继续处理；controller 集成测试断言错误响应结构符合订单金额校验失败规范。

### LOYALTY-004

**模块**：`ecommerce-loyalty`
**问题描述**：设计要求支付回调按 `paymentNo + callbackSequence` 幂等，重复请求不得重复发积分并应返回第一次处理结果；当前 `OrderPaidEventListener` 直接调用 `LoyaltyPointService.earnPoints`，`earnPoints` 直接累加账户积分并保存交易记录，未按支付或订单业务键判断是否已处理。
**修复方案**：在支付成功积分发放链路增加幂等保护：事件对象应携带或映射出 `paymentNo` 与 `callbackSequence`，`OrderPaidEventListener` 调用前先通过 common 幂等组件或 `PointsTransactionRepository.existsByTypeAndBizTypeAndBizId(...)` 检查已完成记录；`earnPoints(...)` 保存交易时使用稳定的 `bizType`（如 `PAYMENT`/`ORDER_PAID`）和 `bizId`（如 `paymentNo:callbackSequence` 或 orderId），并在数据库层增加唯一约束。重复事件命中时返回首次发放结果，不再累加积分。
**验证方式**：新增事件监听器/服务集成测试，发送两次相同 `paymentNo + callbackSequence` 的支付成功事件，断言账户 `totalPoints`、`availablePoints` 只增加一次，积分交易记录只保存一条，第二次返回首次处理结果；不同 sequence 应可正常发放或按业务规则处理。

### LOYALTY-005

**模块**：`ecommerce-loyalty`
**问题描述**：设计要求重复请求不得重复扣减、冻结、消费积分；当前 `redeemPoints` 交易记录 `bizId` 为 `null`，`freezePoints`、`unfreezePoints`、`consumeFrozenPoints` 虽接收 `bizType/bizId`，但未基于幂等键做已处理判断或返回首次处理结果。
**修复方案**：为积分兑换、冻结、解冻、消费写操作定义必填幂等键，推荐统一使用 `bizType + bizId + operationType`；修改 `redeemPoints(...)` 签名或请求 DTO 以接收业务幂等 ID，不再保存 `null` bizId；在 `freezePoints(...)`、`unfreezePoints(...)`、`consumeFrozenPoints(...)` 开始处通过 `PointsTransactionRepository` 或 common 幂等组件查询已完成记录，命中时直接返回首次结果；为交易表增加唯一约束防止并发重复处理。
**验证方式**：新增四类积分写操作的重复调用测试，相同幂等键调用两次时账户余额、冻结余额和交易记录只变化一次，并返回第一次结果；不同幂等键调用应按新请求处理；并发重复请求测试断言唯一约束或事务逻辑可防重复扣减。

### LOYALTY-006

**模块**：`ecommerce-loyalty`
**问题描述**：设计要求事件监听器失败时记录失败日志、保存失败记录到本地事件处理表，并可重放；当前 `PaymentSucceededEventListener`、`ShipmentDeliveredEventListener`、`ReviewApprovedEventListener` 失败时仅记录错误日志，未调用 `FailedEventRecordRepository`，其中 `ReviewApprovedEventListener` 注释还说明失败不会持久化重试。
**修复方案**：参照 `OrderPaidEventListener` 的失败持久化方式，在 `PaymentSucceededEventListener`、`ShipmentDeliveredEventListener`、`ReviewApprovedEventListener` 注入 `FailedEventRecordRepository` 或统一 `FailedEventRecordService`；catch 分支保存事件类型、业务 ID、载荷、异常堆栈摘要、失败时间和状态，保留非强一致监听器不回滚主业务事务的行为；移除“never persisted for retry”注释并接入统一重放管理接口。
**验证方式**：分别构造三个监听器的下游服务异常，触发对应事件后断言本地事件失败表各新增一条记录，主业务事务不回滚；通过失败事件查询/重放接口验证这些记录可被发现并重新处理。

### ORDER-001

**模块**：`ecommerce-order`
**问题描述**：设计要求优惠金额不得小于 0、不得大于商品金额；当前 `OrderService.createOrder` 将 `calculateDiscounts(...)` 返回值直接作为 `discountAmount`，`calculateDiscounts` 直接返回促销模块 `calcResponse.getTotalDiscount()`，未校验其是否位于 `[0, itemTotal]`。
**修复方案**：在 `com.ecommerce.order.service.OrderService.calculateDiscounts` 或其调用点新增边界校验：`null` 按 0 处理，负数或大于 `itemTotal` 时抛 `OrderValidationException`（或调用 common `MoneyValidationUtil.validateDiscountAmount`），不要继续创建订单；合法值在订单写入前调用 `MonetaryUtil.roundToCent`。
**验证方式**：新增 `OrderService.createOrder` 测试，mock 促销模块返回负优惠、超额优惠和合法优惠；断言非法优惠抛 `OrderValidationException` 且不保存订单、不冻结积分、不预占库存，合法优惠正常创建订单。

### ORDER-002

**模块**：`ecommerce-order`
**问题描述**：设计要求订单金额校验失败必须抛出 `OrderValidationException`；当前 `OrderValidator.validateAmount` 与 `OrderValidationUtils` 的金额校验失败路径抛 `BusinessException("ORDER_INVALID_AMOUNT", ...)`，`OrderService` 虽导入 `OrderValidationException` 但未实际使用。
**修复方案**：修改 `com.ecommerce.order.service.OrderValidator.validateAmount` 和 `com.ecommerce.order.util.OrderValidationUtils` 的金额校验失败分支，统一抛 `OrderValidationException`，错误码保持 `ORDER_INVALID_AMOUNT` 或按 common 约定细分为 `PAYABLE_AMOUNT_TOO_LOW`、`DISCOUNT_AMOUNT_INVALID`；`OrderService.createOrder` 中商品总额、应付金额等校验均通过该异常路径。
**验证方式**：新增/调整 `OrderValidatorTest` 与 `OrderValidationUtils` 测试，金额为 null、0、负数、小于 0.01 时断言抛 `OrderValidationException`；Web/服务集成测试确认不再抛泛化 `BusinessException`。

### ORDER-003

**模块**：`ecommerce-order`
**问题描述**：设计要求创建订单以 `externalOrderNo` 为幂等键，重复请求返回第一次处理结果且不得重复扣库存、发积分或使用优惠券；当前 `OrderService.createOrder` 未先按 `externalOrderNo` 查询已有订单，`CreateOrderRequest.externalOrderNo` 还是可选字段，重复提交会再次执行完整创建流程。
**修复方案**：将 `CreateOrderRequest.externalOrderNo` 调整为创建订单必填校验（不修改 API 路径/契约结构，只强化字段约束）。在 `OrderService.createOrder` 开头调用 `OrderRepository.findByExternalOrderNoAndUserId`：已存在时直接 `buildCreateResponse(existingOrder)` 返回首次订单结果，不再冻结积分、标记优惠券、预占库存或发布事件；不存在时正常创建。为 `Order` 增加 `(user_id, external_order_no)` 唯一约束并处理并发唯一键冲突。
**验证方式**：新增重复创建订单集成测试：相同用户与 `externalOrderNo` 请求两次只生成一张订单，库存预占、积分冻结、优惠券使用和事件发布各执行一次，第二次响应与第一次订单一致；同一 key 参数不一致时返回冲突或首次结果。

### ORDER-004

**模块**：`ecommerce-order`
**问题描述**：设计要求创建订单按同一用户每分钟 20 次限流，触发时返回 429 且错误码 `RATE_LIMITED`；当前 `OrderController.createOrder` 直接调用 `orderService.createOrder`，未发现 `RateLimit`、`RATE_LIMITED` 或本地限流逻辑。
**修复方案**：在 `com.ecommerce.order.controller.OrderController.createOrder` 上接入 common 限流能力，例如添加 `@RateLimit(key = "#userId", limit = 20, window = 60)` 或调用统一 `LocalRateLimiter`，key 使用当前认证用户 ID；超限时抛 `RateLimitException`，由 `GlobalExceptionHandler` 返回 429/`RATE_LIMITED`。若限流在 app 层统一切入，确保该创建订单入口被纳入规则。
**验证方式**：新增 controller 集成测试，在同一用户 1 分钟窗口内连续创建超过 20 次，断言第 21 次返回 429 且错误码为 `RATE_LIMITED`；不同用户互不影响，阈值内请求仍进入业务逻辑。

### ORDER-005

**模块**：`ecommerce-order`
**问题描述**：设计要求事件监听器失败时记录失败日志、保存失败记录到本地事件处理表，非强一致监听器不回滚主业务事务；当前 `OrderEventListener` 多个监听方法只覆盖正常日志/状态处理，未捕获失败并保存本地失败记录，模块内也未发现事件失败表实体或 repository。
**修复方案**：在 `com.ecommerce.order.listener.OrderEventListener` 的各个 `@TransactionalEventListener`/异步 fallback 监听方法中接入统一失败处理包装：对非强一致监听器 catch 异常后调用 `FailedEventRecordService.recordFailure(event, handlerName, ex)` 保存事件类型、业务键、payload、异常信息和状态，并记录 error 日志后吞掉异常；如存在强一致监听器，则标注强一致并在记录失败后重新抛出。重放能力复用 common/app 的 `FailedEventReplayService`。
**验证方式**：新增监听器失败测试，模拟每个监听方法内部依赖抛异常，断言失败记录被保存、非强一致监听器不向发布方传播异常；通过重放服务可重新处理并更新状态。

### PAYMENT-001

**模块**：`ecommerce-payment`
**问题描述**：设计要求金额最终入库保留 2 位小数且使用 `RoundingMode.HALF_UP`；当前 payment 模块的退款、发票税额、结算汇总等调用 `MonetaryUtil.roundToCent`，而该工具当前使用 `HALF_DOWN`。
**修复方案**：先按 COMMON-001 修改 `MonetaryUtil.roundToCent` 为 `HALF_UP`；随后检查 `RefundCalculator.calculate`、`InvoiceService`、`SettlementBatchService` 等 payment 调用点，确保中间计算不提前到分，只有最终退款金额、税额、结算金额入库/返回前调用 `roundToCent`。
**验证方式**：新增/调整 payment 金额测试，覆盖 `0.005`、税额半分、结算累加半分场景，断言退款、发票和结算最终金额均按 `HALF_UP`；同时运行 common 金额工具测试。

### PAYMENT-002

**模块**：`ecommerce-payment`
**问题描述**：设计要求应付金额不得小于 0.01；当前 `PaymentValidator.validateAmount` 仅判断 `amount <= 0`，允许 `0.001` 等大于 0 但小于 0.01 的金额进入支付流程。
**修复方案**：修改 `com.ecommerce.payment.service.PaymentValidator.validateAmount`，将金额下限改为 `new BigDecimal("0.01")`，小于该值时抛 `OrderValidationException`；保持 null、负数和 0 的失败语义，并在支付发起与回调金额校验路径统一复用。
**验证方式**：新增 `PaymentValidatorTest` 覆盖 null、负数、0、0.001、0.01、正常金额；断言小于 0.01 均抛 `OrderValidationException`，0.01 及以上通过。

### PAYMENT-003

**模块**：`ecommerce-payment`
**问题描述**：设计要求金额不得产生非法负值，退款金额也不应小于 0 或超过已支付金额；当前 `RefundCalculator` 对 `paidAmount <= 0` 返回 0，并按公式可能得到 0 或负数，`RefundService.applyRefund` 未再次校验即保存 `RefundRecord.refundAmount`。
**修复方案**：修改 `com.ecommerce.payment.service.RefundCalculator.calculate`：对 `paidAmount <= 0` 直接抛 `OrderValidationException` 或业务校验异常；计算结果小于等于 0 时抛 `BusinessException/OrderValidationException`（按金额校验语义优先使用 `OrderValidationException`），大于 `paidAmount` 时限制或抛异常；最终合法退款金额调用 `MonetaryUtil.roundToCent`。在 `RefundService.applyRefund` 保存前再次调用 `validateRefundAmount(refundAmount, payment.getPaidAmount())` 做防御校验。
**验证方式**：新增 `RefundCalculatorTest` 和 `RefundService` 测试，覆盖 paidAmount 为 0、负数、小额导致负退款、正常金额、退款金额超过支付金额等场景，断言非法金额不会保存退款单。

### PAYMENT-004

**模块**：`ecommerce-payment`
**问题描述**：设计要求订单金额校验失败必须抛出 `OrderValidationException`；当前 `PaymentValidator` 与 `PaymentCallbackService` 在支付金额与订单应付金额、回调金额与支付金额不一致时抛 `BusinessException("PAYMENT_AMOUNT_MISMATCH", ...)`。
**修复方案**：修改 `com.ecommerce.payment.service.PaymentValidator` 和 `PaymentCallbackService` 的金额不一致分支，统一抛 `OrderValidationException`，错误码可保留 `PAYMENT_AMOUNT_MISMATCH` 或映射为订单金额校验错误；确保全局异常处理仍返回 400，但类型可被调用方识别为订单金额校验失败。
**验证方式**：新增支付发起和支付回调金额不一致测试，断言抛出 `OrderValidationException` 而不是泛化 `BusinessException`；Web 层响应错误码保持可兼容。

### PAYMENT-005

**模块**：`ecommerce-payment`
**问题描述**：设计要求未认证或无权限使用 `AuthorizationException`，对应 401/403；当前 `PaymentCallbackService` 签名无效时抛 `BusinessException("UNAUTHORIZED", ...)`，无法稳定映射为认证/授权语义。
**修复方案**：修改 `com.ecommerce.payment.service.PaymentCallbackService.validateSignature` 或调用处，在签名无效时抛 `AuthorizationException.unauthorized(...)` 或 `AuthorizationException.forbidden(...)`；保留原错误码/消息语义但使用统一授权异常类型。
**验证方式**：新增回调签名无效的 service/controller 测试，断言异常类型为 `AuthorizationException`，Web 层返回 401/403 且响应结构符合统一异常规范；有效签名仍正常处理。

### PAYMENT-006

**模块**：`ecommerce-payment`
**问题描述**：设计要求状态冲突、重复提交使用 `ConflictException` 并返回 409；当前 `PaymentCallbackService`、`PaymentValidator`、`RefundService`、`RefundStageService` 多处状态冲突使用 `BusinessException("CONFLICT", ...)`。
**修复方案**：逐一修改 `PaymentCallbackService`、`PaymentValidator`、`RefundService.applyRefund/reviewRefund`、`RefundStageService` 的冲突分支，将 `BusinessException("CONFLICT", ...)` 替换为 `ConflictException` 或项目现有冲突工厂方法；错误码按场景细化为 `PAYMENT_STATUS_CONFLICT`、`REFUND_STATUS_CONFLICT` 等。
**验证方式**：新增/调整对应测试：重复支付回调、非成功支付申请退款、非待审核退款审核、仓库验收状态不符等场景均抛 `ConflictException`，Web 层 HTTP 409；非冲突业务错误仍为 400。

### PAYMENT-007

**模块**：`ecommerce-payment`
**问题描述**：设计要求支付回调幂等键为 `paymentNo + callbackSequence`，重复请求返回第一次处理结果；当前仅在 `PaymentRecord` 上保存一个当前 `callbackSequence` 并对相同当前序列短路，没有独立组合键幂等记录和首次处理结果。
**修复方案**：在 `PaymentCallbackService.processCallback` 开头构造 `paymentNo + callbackSequence` 幂等键，复用 common `@Idempotent` 或新增 `PaymentCallbackRecord`（paymentNo、callbackSequence、requestHash、responseBody、processedAt、status）并加唯一约束。命中已完成记录时直接返回第一次处理结果；首次处理成功后保存响应摘要。控制器 `PaymentController.callback` 从固定 `"OK"` 调整为返回 service 的处理结果（不改变路径和请求契约）。
**验证方式**：新增 `PaymentCallbackServiceTest`：同一 `paymentNo/callbackSequence` 重复回调只更新一次支付状态、只发布一次事件、第二次返回首次结果；旧 sequence 重放仍能命中历史幂等记录；并发重复回调不产生重复副作用。

### PAYMENT-008

**模块**：`ecommerce-payment`
**问题描述**：设计要求退款申请以 `refundRequestNo` 为幂等键；当前 `RefundApplyRequest`、`RefundRecord`、Repository 与 `RefundService.applyRefund` 均无 `refundRequestNo`，每次申请都会生成新的 `refundNo` 和退款单。
**修复方案**：在不改变接口路径的前提下为 `RefundApplyRequest` 增加必填字段 `refundRequestNo`，在 `RefundRecord` 增加同名字段并建立唯一约束（建议 userId + refundRequestNo 或全局唯一）；在 `RefundRecordRepository` 增加 `findByRefundRequestNo`/`findByUserIdAndRefundRequestNo`；`RefundService.applyRefund` 开头按该键查询，已存在则返回第一次 `RefundResponse`，不存在才创建退款单。参数不一致时返回 `ConflictException`。
**验证方式**：新增退款申请幂等测试：相同 `refundRequestNo` 连续请求只创建一条退款单且返回同一 `refundNo`；相同 key 不同 payment/order 参数返回冲突；并发重复请求由唯一约束兜底。

### PAYMENT-009

**模块**：`ecommerce-payment`
**问题描述**：设计要求支付回调按同一 `paymentNo` 每分钟 20 次限流，触发返回 429/`RATE_LIMITED`；当前 `PaymentController.callback` 与 `PaymentCallbackService` 未发现限流器、`RateLimitException` 或错误码路径。
**修复方案**：在 `com.ecommerce.payment.controller.PaymentController.callback` 接入 common 限流注解/组件，key 使用请求体 `paymentNo`，阈值 20，窗口 60 秒；超限抛 `RateLimitException` 并由全局异常处理返回 429/`RATE_LIMITED`。若限流在 app 层统一实现，确保 payment 回调入口被规则覆盖。
**验证方式**：新增支付回调限流 Web 测试，同一 `paymentNo` 在一分钟内超过 20 次时返回 429/`RATE_LIMITED`；不同 paymentNo 分别计数；阈值内仍进入签名和业务校验。

### PAYMENT-010

**模块**：`ecommerce-payment`
**问题描述**：设计要求退款审核、仓库验收和结算批次生成必须记录包含操作者、操作类型、业务 ID、前后状态、时间、备注的审计日志；当前 `RefundService.reviewRefund`、`RefundService.warehouseAccept`/`RefundStageService`、`SettlementBatchService` 只更新业务实体并写普通日志，未调用审计日志服务。
**修复方案**：在 `RefundService.reviewRefund` 成功审批/拒绝后调用 `AuditLogService.record`，记录 `operationType=REFUND_REVIEW`、refundId/refundNo、审批前后状态、reviewerId、备注；在仓库验收成功后记录 `operationType=REFUND_WAREHOUSE_ACCEPT`、acceptorId、前后状态；在 `SettlementBatchService` 生成批次成功后记录 `operationType=SETTLEMENT_BATCH_GENERATED`、batchId/batchNo、生成前后状态或统计摘要、操作者（若当前方法无操作者参数，则从安全上下文或系统账号获取）、备注。
**验证方式**：新增退款审核、仓库验收、结算批次生成测试，断言成功路径写入审计日志且字段完整；失败/冲突路径不写成功审计；通过审计查询接口可检索记录。

### PAYMENT-011

**模块**：`ecommerce-payment`
**问题描述**：设计要求支付成功后的物流、积分、通知监听器为非强一致，失败时记录日志并保存本地事件失败记录；当前 `PaymentLoyaltyEventListener` catch 异常后只写日志并吞掉，公共 `DomainEventPublisher` 无法感知失败并持久化。
**修复方案**：在 `com.ecommerce.payment.service.PaymentLoyaltyEventListener` 注入 `FailedEventRecordService`，catch 分支保存 `PaymentSucceededEvent` 的事件类型、paymentNo/orderId、payload、处理器名、异常信息和状态 `FAILED`，然后继续吞掉异常以保证不回滚支付主流程；或改为使用 common 的非强一致事件处理包装器统一处理。
**验证方式**：新增 listener 测试，mock 积分服务抛异常后断言失败记录落库、支付主流程不回滚/不抛出；通过失败事件重放服务重放后可重新调用积分处理并更新状态。

### PRODUCT-001

**模块**：`ecommerce-product`
**问题描述**：设计要求最终入库金额保留 2 位小数并使用 `RoundingMode.HALF_UP`；当前 `SkuService.createSku` 直接 `setPrice(request.getPrice())`、`setMarketPrice(request.getMarketPrice())`，只依赖 `ProductSku` 的 JPA `scale = 2`。
**修复方案**：修改 `com.ecommerce.product.service.SkuService.createSku`，在设置 `price`、`marketPrice` 前调用 `MonetaryUtil.roundToCent`（common 修正为 `HALF_UP` 后）；若存在 SKU 更新价格方法，也同步在保存前规范化。金额为 null 的 marketPrice 按现有业务规则处理，非 null 必须规范化。
**验证方式**：新增 `SkuServiceTest`，创建 SKU 时传入 `10.005`、`10.004`、两位小数和 null marketPrice，断言保存到实体的价格按 `HALF_UP` 保留 2 位。

### PRODUCT-002

**模块**：`ecommerce-product`
**问题描述**：设计要求应付金额不得小于 0.01，0 元订单不支持；当前 `SkuCreateRequest.price` 使用 `@PositiveOrZero`，允许 API 创建价格为 0 的 SKU。
**修复方案**：将 `SkuCreateRequest.price` 的校验从 `@PositiveOrZero` 调整为 `@DecimalMin(value = "0.01", inclusive = true)`，必要时 `marketPrice` 也增加非负/不低于 0.01 或不小于销售价的业务校验；在 `SkuService.createSku` 增加服务层兜底校验，非法价格抛 `ValidationException`。
**验证方式**：新增 controller 参数校验和 service 测试，价格为 0、0.001、负数时创建失败，0.01 及以上成功；验证后续订单获取可售 SKU 不会拿到 0 价新 SKU。

### PRODUCT-003

**模块**：`ecommerce-product`
**问题描述**：设计要求商品搜索按同一 IP 每分钟 120 次限流，触发返回 429/`RATE_LIMITED`；当前 `ProductController.searchProducts(ProductSearchRequest)` 未接收 IP/`HttpServletRequest`，`ProductSearchService.search` 内也无本地计数或限流异常。
**修复方案**：在 `com.ecommerce.product.controller.ProductController.searchProducts` 增加 `HttpServletRequest` 参数或使用统一 key provider 获取客户端 IP，并添加 common 限流注解/调用 `LocalRateLimiter`，key 为 IP，阈值 120，窗口 60 秒；超限抛 `RateLimitException`。若限流由 app 层统一处理，也需确保该 controller 方法被纳入商品搜索规则。
**验证方式**：新增商品搜索 Web 测试，同一 IP 1 分钟内第 121 次请求返回 429 且错误码 `RATE_LIMITED`，不同 IP 独立计数，阈值内搜索仍正常返回。

### PRODUCT-004

**模块**：`ecommerce-product`
**问题描述**：设计要求商品上下架必须记录审计日志，字段包含操作者、操作类型、业务 ID、前后状态、操作时间和备注；当前 `SkuService.onShelf/offShelf` 只更新 `SkuStatus` 并写普通日志，`AdminProductController` 也未传递操作者/备注。
**修复方案**：在 `SkuService.onShelf(Long skuId)` 和 `offShelf(Long skuId)` 中捕获变更前状态，状态保存成功后调用 common `AuditLogService.record`：`operationType=SKU_ON_SHELF`/`SKU_OFF_SHELF`，`bizId=skuId`，`beforeState`/`afterState` 为状态名，操作者从 Spring Security 上下文获取或由 controller 传入，备注可来自新增可选请求参数或默认说明；不要用普通 `log.info` 代替审计。
**验证方式**：新增 `SkuService` 上下架测试，断言成功上架/下架后审计服务被调用且字段完整；状态冲突或 SKU 不存在时不写成功审计；通过审计查询接口可检索记录。

### PROMOTION-001

**模块**：`ecommerce-promotion`
**问题描述**：设计要求应付金额不得小于 0.01，0 元订单不支持；当前 `PromotionCalculationServiceImpl` 的 `cap` 允许优惠等于当前剩余金额，`applyCoupon` 后 `currentAmount` 可变为 `BigDecimal.ZERO` 并作为 `finalAmount` 返回。
**修复方案**：修改 `com.ecommerce.promotion.service.PromotionCalculationServiceImpl`：在叠加优惠裁剪时保留最低应付金额 `MIN_PAYABLE = 0.01`，即最大可优惠金额为 `currentAmount.subtract(MIN_PAYABLE)`（小于 0 时不再优惠）；或在最终 `calculate` 返回前调用 `MoneyValidationUtil.validatePayableAmount(finalAmount)`，对 0 元促销组合抛 `OrderValidationException/BusinessException`。推荐在计算阶段限制优惠，避免返回 0 元结果。
**验证方式**：新增促销计算测试，构造满减/优惠券等优惠等于或超过商品金额的场景，断言 `finalAmount >= 0.01` 或抛出明确金额校验异常；正常优惠场景不受影响。

### PROMOTION-002

**模块**：`ecommerce-promotion`
**问题描述**：设计要求最终入库金额保留 2 位小数并使用 `RoundingMode.HALF_UP`；当前 `CouponTemplateService`、`FullReductionService`、`SeckillService` 创建时直接保存请求中的 `discountValue`、`thresholdAmount`、`maxDiscount`、`reductionAmount`、`seckillPrice` 等金额，未调用 `setScale(2, HALF_UP)` 或金额工具。
**修复方案**：在 `CouponTemplateService.create`/`validateCreateRequest`、`FullReductionService.create`、`SeckillService.create` 中，对所有要入库的金额字段调用 `MonetaryUtil.roundToCent`；中间计算保持高精度，只有保存实体或响应最终金额时到分。同步对更新接口（如有）应用同样逻辑。
**验证方式**：新增服务测试，创建优惠券模板、满减、秒杀活动时输入半分金额，断言实体字段保存为 2 位且按 `HALF_UP`；非法金额仍按校验失败处理。

### PROMOTION-003

**模块**：`ecommerce-promotion`
**问题描述**：设计要求优惠金额不得小于 0、不得大于商品金额；当前 `CouponCreateRequest`、`FullReductionCreateRequest` 金额字段缺少 `@Positive`/`@DecimalMin`，`CouponTemplateService.validateCreateRequest` 未完整校验优惠值非负、折扣券区间、满减金额与门槛关系，`FullReductionService.create` 未校验 `reductionAmount` 非负或不超过适用金额。
**修复方案**：为 `CouponCreateRequest`、`FullReductionCreateRequest` 的金额字段增加 Bean Validation（如 `@DecimalMin("0.01")`，阈值可按业务允许 0 时单独处理）；在 `CouponTemplateService.validateCreateRequest` 校验固定金额券金额大于 0、折扣券折扣率在合理区间、`maxDiscount` 非负且不超过可推导上限；在 `FullReductionService.create` 校验 `reductionAmount > 0` 且 `reductionAmount <= thresholdAmount`。非法规则抛 `ValidationException`。
**验证方式**：新增创建优惠券模板和满减活动的参数化测试，覆盖负数、0、折扣率越界、满减金额大于门槛、合法边界等场景；断言非法请求不会保存实体。

### PROMOTION-004

**模块**：`ecommerce-promotion`
**问题描述**：设计要求资源不存在使用 `ResourceNotFoundException`，HTTP 404；当前 `CouponValidator.validate(null)` 抛 `BusinessException("RESOURCE_NOT_FOUND", "Coupon not found")`，同方法中模板不存在才使用 `ResourceNotFoundException`。
**修复方案**：修改 `com.ecommerce.promotion.service.CouponValidator.validate`，当 `userCoupon == null` 时抛 `new ResourceNotFoundException("UserCoupon", ...)` 或项目现有资源不存在工厂方法；保留模板不存在分支的 `ResourceNotFoundException`。
**验证方式**：新增 `CouponValidator` 测试，传入 null 优惠券断言抛 `ResourceNotFoundException`，Web 层返回 404；状态不可用等业务校验仍抛业务/冲突异常。

### PROMOTION-005

**模块**：`ecommerce-promotion`
**问题描述**：设计要求创建订单以 `externalOrderNo` 幂等，重复请求返回第一次处理结果且不重复副作用；当前 `CouponUsageService.markCouponsUsed(userId, orderId, userCouponIds)` 首次调用后将优惠券置为 USED，重复调用会再次 validate 并因状态不是 AVAILABLE 抛异常，而不是识别同一订单的首次处理。
**修复方案**：修改 `com.ecommerce.promotion.service.CouponUsageService.markCouponsUsed`：在 validate 前先检查 `UserCoupon` 是否已为 `USED` 且 `usedOrderId` 等于当前 `orderId`，若是则视为幂等成功并跳过重复写入；若已使用但订单不同则抛 `ConflictException`。建议扩展命令接口接收 `externalOrderNo` 或订单幂等键，并为 `user_coupon_id + used_order_id`/幂等记录增加唯一约束，配合 order 创建幂等。
**验证方式**：新增重复订单创建调用 promotion 的测试，同一 `orderId` 重复 `markCouponsUsed` 不报错、不重复变更；同一优惠券被不同订单重复使用返回冲突；与 order 创建幂等集成测试联动验证。

### REVIEW-001

**模块**：`ecommerce-review`
**问题描述**：设计要求状态冲突、重复提交使用 `ConflictException` 并返回 409；当前 `ReviewService` 对重复评价抛 `BusinessException("DUPLICATE_REVIEW")`，`ReviewModerationService` 对非待审核状态审核/驳回抛 `BusinessException("INVALID_REVIEW_STATUS")`。
**修复方案**：修改 `com.ecommerce.review.service.ReviewService.createReview` 的重复评价分支和 `ReviewModerationService.approve/reject` 的状态不符分支，统一抛 `ConflictException`，错误码可为 `DUPLICATE_REVIEW`、`REVIEW_STATUS_CONFLICT`；保留资源不存在仍使用 `ResourceNotFoundException`。
**验证方式**：新增 review service/controller 测试，重复评价和非 PENDING_REVIEW 审核返回 `ConflictException`/HTTP 409；普通参数校验仍为 400。

### REVIEW-002

**模块**：`ecommerce-review`
**问题描述**：设计要求未认证或无权限使用 `AuthorizationException`，HTTP 401/403；当前 `ReviewService` 在用户追加非本人评价时抛 `BusinessException("FORBIDDEN")`。
**修复方案**：修改 `com.ecommerce.review.service.ReviewService.appendReview` 的所有权校验失败分支，抛 `AuthorizationException.forbidden("Cannot append to others review")` 或项目现有 403 工厂方法；controller 不再包装该异常。
**验证方式**：新增追加评价权限测试，当前用户不是评价所有者时断言抛 `AuthorizationException`，Web 层返回 403；本人追加仍正常。

### REVIEW-003

**模块**：`ecommerce-review`
**问题描述**：设计要求事件监听器失败时记录失败日志、保存失败记录到本地事件处理表并可重放；当前 `ReviewApprovedEventListener.onReviewApproved` catch 异常后仅 `log.error`，注释明确 “Failure only logged, never persisted for retry”。
**修复方案**：在 `com.ecommerce.review.service.ReviewApprovedEventListener` 注入统一 `FailedEventRecordService`，catch 分支保存事件类型 `ReviewApprovedEvent`、reviewId/orderId/userId 等业务键、payload、处理器名、异常信息、状态 `FAILED`，然后吞掉异常保持非强一致；重放由 common/app 的 `FailedEventReplayService` 根据记录重新调用处理。
**验证方式**：新增 listener 测试，mock 下游处理抛异常时断言失败记录落库且异常不传播；调用重放服务后可重新处理并更新状态。

### USER-001

**模块**：`ecommerce-user`
**问题描述**：设计要求登录接口按同一用户名每分钟 5 次限流，触发时返回 429/`RATE_LIMITED`；当前 `UserController.login` 直接调用 `UserAuthService.login`，服务只查询用户、校验状态/密码、签发 JWT 和保存会话，未按 email/用户名限流。
**修复方案**：在 `com.ecommerce.user.controller.UserController.login` 或 `UserAuthService.login` 入口接入 common 限流能力，key 使用 `LoginRequest.email`（或用户名规范化值），阈值 5，窗口 60 秒；超限抛 `RateLimitException` 并由全局异常处理返回 429/`RATE_LIMITED`。为避免用户枚举，限流应在用户存在性和密码校验前执行。
**验证方式**：新增登录限流 Web 测试，同一 email 在一分钟内第 6 次登录返回 429/`RATE_LIMITED`，不同 email 独立计数；阈值内成功/失败登录仍按原逻辑返回。

### USER-002

**模块**：`ecommerce-user`
**问题描述**：设计要求用户冻结和解冻必须记录审计日志，字段包括操作者、操作类型、业务 ID、前后状态、操作时间和备注；当前 `AdminUserController.freezeUser/unfreezeUser` 只接收 userId，`UserAuthService.freezeUser/unfreezeUser` 仅修改状态并写普通日志。
**修复方案**：在 `UserAuthService.freezeUser` 和 `unfreezeUser` 中保存变更前状态，状态保存成功后调用 common `AuditLogService.record`：`operationType=USER_FREEZE`/`USER_UNFREEZE`，`bizId=userId`，`beforeState`/`afterState` 为用户状态，操作者从 Spring Security 当前认证主体获取或由 controller 传入，备注可从新增可选请求体/参数或默认说明获取；普通日志保留但不替代审计。
**验证方式**：新增冻结/解冻测试，断言成功操作后审计服务被调用且字段完整；用户不存在或状态未变更失败时不写成功审计；通过审计查询接口可查到记录。

### USER-003

**模块**：`ecommerce-user`
**问题描述**：设计要求所有通知必须通过 `LocalNotificationService` 发送，业务模块只能提交 `NotificationRequest`；当前 `UserRegisterService.register` 注册成功后发布 `UserRegisteredEvent`，依赖 common 通过事件 simpleName 和反射字段触发通知，user 模块未构造 `NotificationRequest`。
**修复方案**：修改 `com.ecommerce.user.service.UserRegisterService.register`：注册成功后构造 `NotificationRequest`（包含接收人 email/phone、模板编码、业务键如 userId/email、幂等 key 如 `USER_REGISTERED:{userId}`、模板变量）并调用 `LocalNotificationService.send(request)`，或发布明确携带 `NotificationRequest` 的通知命令事件；移除依赖 simpleName/反射字段的通知约定。通知发送应在事务提交后或非强一致包装中执行，避免通知失败回滚注册。
**验证方式**：新增注册成功通知测试，mock `LocalNotificationService` 断言收到 `NotificationRequest` 且模板、接收人、幂等 key 完整；模拟通知失败时注册主流程不回滚且失败记录按 common 通知/事件机制保存。

### USER-004

**模块**：`ecommerce-user`
**问题描述**：设计要求事件监听器失败时记录失败日志、保存失败记录、不回滚非强一致主事务并可重放；当前 `UserRegisterService.register` 在事务内直接发布 `UserRegisteredEvent`，user 模块未体现失败记录、重放接口或监听器强一致/非强一致声明。
**修复方案**：将用户注册后的通知/后续处理改为明确的非强一致事件：通过 common `DomainEventPublisher` 或 `@TransactionalEventListener(phase = AFTER_COMMIT)` 发布/处理 `UserRegisteredEvent`，监听器使用统一失败处理包装，catch 中保存 `FailedEventRecord` 并吞掉异常；若采用直接 `NotificationRequest`，也应放在事务提交后执行并复用通知失败记录。为事件处理器添加非强一致默认标记或避免在注册事务内同步执行可能失败的监听逻辑。
**验证方式**：新增注册事件失败测试，模拟注册后监听器/通知处理失败，断言用户注册事务已提交、失败事件/通知失败记录落库、可通过重放服务再次处理；强一致/非强一致声明在测试中可见。
