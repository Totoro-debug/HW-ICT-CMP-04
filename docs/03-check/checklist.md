## 问题跟踪清单

| 序号 | 模块 | 不一致点描述 | 严重程度 | 修复方案摘要 | 状态 |
|------|------|--------------|----------|--------------|------|
| APP-001 | ecommerce-app | 本地限流未在 app 装配层实现 | 高 | 在登录、支付回调、商品搜索、创建订单入口应用 common `@RateLimit`，按用户名/paymentNo/IP/用户配置阈值并复用 429/`RATE_LIMITED` 异常映射 | 已修复 |
| APP-002 | ecommerce-app | 事件失败管理接口缺少失败事件重放能力 | 中 | 在 `EventFailureAdminController` 注入失败事件重放服务，新增按 ID 或条件重放的 ADMIN POST 接口 | 已修复 |
| APP-003 | ecommerce-app | 审计日志统一装配/管理入口未找到 | 高 | 装配 common 审计日志服务并新增 ADMIN 审计日志查询 controller，业务模块在指定操作处写入审计记录 | 已修复 |
| COMMON-001 | ecommerce-common | 金额最终舍入模式不符合设计 | 高 | 将 `MonetaryUtil.roundToCent` 的舍入模式由 `HALF_DOWN` 改为 `HALF_UP` 并更新注释 | 已修复 |
| COMMON-002 | ecommerce-common | 通用金额工具会在加、减、乘每一步后立即截断到分 | 高 | 调整 `add`、`subtract`、`multiply` 保留中间精度，仅由 `roundToCent` 执行最终入库舍入 | 已修复 |
| COMMON-003 | ecommerce-common | 未找到优惠金额边界和应付金额下限的通用校验实现 | 中 | 在金额工具或校验工具中新增优惠金额边界、应付金额下限校验，失败时抛 `OrderValidationException` | 已修复 |
| COMMON-004 | ecommerce-common | 泛化 `BusinessException` 的 HTTP 状态映射不完全符合设计 | 中 | 让 `handleBusiness` 始终返回 400，权限和冲突场景改用 `AuthorizationException`、`ConflictException` | 已修复 |
| COMMON-005 | ecommerce-common | 未找到 03 设计指定接口幂等键的 common 通用实现 | 高 | 新增 `@Idempotent`、幂等记录实体/Repository 和切面，按五类接口指定 key 返回首次处理结果 | 已修复 |
| COMMON-006 | ecommerce-common | 未找到审计日志实体/服务/DTO 覆盖设计要求字段 | 高 | 新增 `AuditLog`、Repository、`AuditLogService` 和查询 DTO，覆盖操作者、操作类型、业务 ID、前后状态、时间、备注 | 已修复 |
| COMMON-007 | ecommerce-common | 通知发送失败记录未持久化，事件通知失败可能被吞掉 | 高 | 通知失败 catch 分支持久化失败记录，并在事件监听路径重新抛出自定义异常以触发事件失败记录保存 | 已修复 |
| COMMON-008 | ecommerce-common | 本地事件失败处理缺少重放失败事件的管理能力 | 中 | 新增 `FailedEventReplayService`、重放状态/次数/错误更新逻辑，并由 app ADMIN 接口调用 | 已修复 |
| COMMON-009 | ecommerce-common | 本地事件处理缺少强一致监听器的显式声明机制 | 中 | 新增强一致监听器注解或标记接口，分发层对强一致异常重新抛出、非强一致异常记录后吞掉 | 已修复 |
| CART-001 | ecommerce-cart | 金额中间计算存在提前截断/舍入风险，且工具方法当前未显式符合 HALF_UP 要求 | 高 | 调整 CartService 为高精度中间累计、最终统一到分，并修正 MonetaryUtil 使用 HALF_UP | 已修复 |
| CART-002 | ecommerce-cart | 优惠金额未限制在 [0, 商品金额] 范围内 | 中 | 在 CartService 中对促销返回优惠做下限归零、上限裁剪或业务异常处理 | 已修复 |
| CART-003 | ecommerce-cart | 应付金额可能为 0，未保证不小于 0.01 | 高 | 在 CartService 估价末尾阻断 0 元/低于 0.01 的可结算结果，空购物车避免进入结算链路 | 已修复 |
| INVENTORY-001 | ecommerce-inventory | 库存人工调整未记录包含操作者、操作类型、业务 ID、前后状态、时间、备注的审计日志 | 高 | 在 StockAdjustmentService.create 成功后调用通用审计日志服务记录完整审计字段 | 已修复 |
| INVENTORY-002 | ecommerce-inventory | 库存预占接口重复请求可能重复增加 reservedStock 并导致后续重复扣库存 | 高 | 在 InventoryReservationServiceImpl.reserve 按 orderId 做幂等判断并增加唯一约束兜底 | 已修复 |
| INVENTORY-003 | ecommerce-inventory | 管理端直接出库接口缺少幂等保护，重复 orderId 会重复扣减 onHandStock | 高 | 在 InventoryService.outbound 使用 OutboundOrderRepository.findByOrderId 做幂等/冲突判断并增加唯一约束 | 已修复 |
| INVENTORY-004 | ecommerce-inventory | 未实现事件监听失败日志、失败记录落库、非回滚处理和管理端重放能力 | 中 | 为实际 inventory 事件监听边界新增失败记录服务、失败事件表和管理端重放接口 | 已修复 |
| INVENTORY-005 | ecommerce-inventory | inventory 暴露接口未发现统一限流实现或明确豁免说明 | 中 | 复用/新增限流拦截器或注解覆盖库存接口并统一返回 429/RATE_LIMITED，或代码侧明确豁免 | 已修复 |
| LOGISTICS-001 | ecommerce-logistics | 运费金额最终处理未显式按 `HALF_UP` 保留 2 位 | 高 | 在 `FreightCalculator`、`FreightTemplateService`、`ShipmentService` 写入/返回运费金额前统一 `setScale(2, RoundingMode.HALF_UP)` | 已修复 |
| LOGISTICS-002 | ecommerce-logistics | 拣货单状态冲突抛出 `IllegalStateException` 而非通用冲突异常 | 中 | 将 `PickListService.completePicking` 状态冲突分支改抛 `ConflictException` 并保持 409 映射 | 已修复 |
| LOGISTICS-003 | ecommerce-logistics | 物流回调未按 `trackingNo + eventTime + status` 实现幂等且未返回首次结果 | 高 | 为物流回调增加幂等记录/唯一约束和首次响应缓存，重复回调直接返回首次处理结果 | 已修复 |
| LOGISTICS-004 | ecommerce-logistics | 主业务代码依赖 `FaultInjectionRegistry` 测试故障注入入口 | 中 | 从 `ShipmentService.createShipment` 移除 `common.test` 依赖，失败场景改由测试 harness/mock 注入 | 已修复 |
| LOGISTICS-005 | ecommerce-logistics | 未找到仓库验收审计日志实现及设计要求字段 | 高 | 在仓库验收业务方法调用 `AuditLogService.record`，记录操作者、操作类型、业务 ID、前后状态、时间、备注 | 已修复 |
| LOGISTICS-006 | ecommerce-logistics | 支付成功后的物流监听器失败只写日志，未保存本地失败事件 | 高 | 在 `OrderPaidShipmentListener` catch 分支保存 `FailedEventRecord` 并接入失败事件重放能力 | 已修复 |
| LOYALTY-001 | ecommerce-loyalty | 积分抵扣金额使用 `RoundingMode.DOWN` 而非 `HALF_UP` | 中 | 将 `LoyaltyController` 抵扣金额计算改为统一 `HALF_UP`，最好抽取到服务/金额工具 | 已修复 |
| LOYALTY-002 | ecommerce-loyalty | 金额/倍率计算链路使用 `double` 并混用 `BigDecimal` | 高 | 将会员倍率、活动倍率等参与金额换算的值改为 `BigDecimal`，避免二进制浮点参与计算 | 已修复 |
| LOYALTY-003 | ecommerce-loyalty | 订单金额校验失败未使用 `OrderValidationException` | 中 | 在积分估算和 `redeemPoints` 中统一校验 `orderAmount >= 0.01`，失败抛 `OrderValidationException` | 已修复 |
| LOYALTY-004 | ecommerce-loyalty | 支付成功后积分发放缺少幂等保护，重复事件会重复发积分 | 高 | 按 `paymentNo + callbackSequence` 或稳定业务键建立幂等记录/唯一约束，重复事件返回首次结果 | 已修复 |
| LOYALTY-005 | ecommerce-loyalty | 积分兑换、冻结、解冻、消费写操作缺少幂等键 | 高 | 为积分写操作使用 `bizType + bizId + operationType` 幂等键，查询首次记录并增加唯一约束 | 已修复 |
| LOYALTY-006 | ecommerce-loyalty | 部分事件监听器失败未保存本地事件处理表记录 | 高 | 参照 `OrderPaidEventListener` 为三个监听器注入失败事件记录服务并保存失败载荷 | 已修复 |

| ORDER-001 | ecommerce-order | 创建订单优惠金额缺少 0 到商品金额的边界校验 | 高 | 在 `OrderService.calculateDiscounts`/创建订单边界校验优惠金额并对非法值抛 `OrderValidationException` | 已修复 |
| ORDER-002 | ecommerce-order | 订单金额校验失败抛 `BusinessException`，未使用 `OrderValidationException` | 高 | 将订单金额校验组件的失败异常统一改为 `OrderValidationException` | 已修复 |
| ORDER-003 | ecommerce-order | 创建订单未按 `externalOrderNo` 实现幂等 | 高 | 在 `OrderService.createOrder` 开头按 `externalOrderNo` 查重返回首次订单，并增加唯一约束兜底 | 已修复 |
| ORDER-004 | ecommerce-order | 创建订单接口未实现同一用户每分钟 20 次本地限流 | 高 | 在 `OrderController.createOrder` 接入按用户维度的 20 次/分钟限流并统一抛 `RateLimitException` | 已修复 |
| ORDER-005 | ecommerce-order | order 事件监听器失败未落库失败记录 | 高 | 为 `OrderEventListener` 增加统一 try/catch 失败持久化并接入失败事件重放服务 | 已修复 |
| PAYMENT-001 | ecommerce-payment | payment 金额舍入依赖 `HALF_DOWN`，不符合 `HALF_UP` | 高 | 修正 common 金额工具为 `HALF_UP`，并校验 payment 退款/发票/结算最终边界调用 | 已修复 |
| PAYMENT-002 | ecommerce-payment | 支付应付金额仅校验大于 0，未校验不得小于 0.01 | 高 | 将 `PaymentValidator.validateAmount` 下限改为 `0.01` 并抛 `OrderValidationException` | 已修复 |
| PAYMENT-003 | ecommerce-payment | 退款金额可能为 0 或负数并入库，缺少边界保护 | 高 | 在 `RefundCalculator` 和 `RefundService.applyRefund` 双层校验退款金额大于 0 且不超过已支付金额 | 已修复 |
| PAYMENT-004 | ecommerce-payment | 订单/支付金额校验失败未抛 `OrderValidationException` | 高 | 将支付金额不一致校验路径从 `BusinessException` 改为 `OrderValidationException` | 已修复 |
| PAYMENT-005 | ecommerce-payment | 支付回调签名认证失败使用 `BusinessException`，未使用授权异常 | 中 | 将支付回调签名无效分支改抛 `AuthorizationException` 并验证 401/403 | 已修复 |
| PAYMENT-006 | ecommerce-payment | 支付/退款状态冲突和重复提交使用 `BusinessException("CONFLICT")` | 中 | 将 payment/退款状态冲突分支统一替换为 `ConflictException` | 已修复 |
| PAYMENT-007 | ecommerce-payment | 支付回调未完整按 `paymentNo + callbackSequence` 保存/返回首次处理结果 | 高 | 新增支付回调幂等记录表/切面，按 `paymentNo+callbackSequence` 返回首次处理结果 | 已修复 |
| PAYMENT-008 | ecommerce-payment | 退款申请未包含并按 `refundRequestNo` 实现幂等 | 高 | 为退款申请 DTO/实体/Repository 增加 `refundRequestNo` 并在 `applyRefund` 按键返回首次结果 | 已修复 |
| PAYMENT-009 | ecommerce-payment | 支付回调接口未实现同一 `paymentNo` 每分钟 20 次限流 | 高 | 在 `PaymentController.callback` 按 `paymentNo` 接入 20 次/分钟限流 | 已修复 |
| PAYMENT-010 | ecommerce-payment | 退款审核和结算批次生成未记录审计日志 | 高 | 在退款审核、仓库验收和结算批次生成成功后调用 `AuditLogService.record` | 已修复 |
| PAYMENT-011 | ecommerce-payment | 支付成功事件监听失败未保存本地失败记录 | 高 | 在 `PaymentLoyaltyEventListener` catch 分支记录失败事件到本地事件处理表 | 已修复 |
| PRODUCT-001 | ecommerce-product | 商品价格入库前未显式按 2 位小数和 `HALF_UP` 规范化 | 高 | 在 `SkuService.createSku` 设置 price/marketPrice 前调用 `MonetaryUtil.roundToCent` | 已修复 |
| PRODUCT-002 | ecommerce-product | 商品价格允许 0，可能导致 0 元订单 | 中 | 将 `SkuCreateRequest.price` 改为最小 0.01，并在 `SkuService` 做服务层兜底校验 | 已修复 |
| PRODUCT-003 | ecommerce-product | 商品搜索接口未实现同一 IP 每分钟 120 次限流 | 高 | 在 `ProductController.searchProducts` 按客户端 IP 接入 120 次/分钟限流 | 已修复 |
| PRODUCT-004 | ecommerce-product | 商品上下架未记录符合字段要求的审计日志 | 高 | 在 `SkuService.onShelf/offShelf` 成功后写入 `AuditLogService.record` 完整审计日志 | 已修复 |
| PROMOTION-001 | ecommerce-promotion | 促销计算最终应付金额可能为 0.00 | 高 | 在促销叠加裁剪或最终返回前保留最低应付 0.01，阻断 0 元结果 | 已修复 |
| PROMOTION-002 | ecommerce-promotion | 促销金额入库前未显式按 2 位小数和 `HALF_UP` 归一化 | 高 | 在促销模板/活动创建保存前对所有金额字段调用 `MonetaryUtil.roundToCent` | 已修复 |
| PROMOTION-003 | ecommerce-promotion | 优惠规则创建端缺少非负和不超过商品/门槛的边界校验 | 高 | 在促销创建 DTO 和 service 校验中补齐金额非负、折扣区间、满减金额不超过门槛等规则 | 已修复 |
| PROMOTION-004 | ecommerce-promotion | 优惠券不存在场景使用 `BusinessException`，未使用 404 资源异常 | 中 | 将 `CouponValidator.validate(null)` 从 `BusinessException` 改为 `ResourceNotFoundException` | 已修复 |
| PROMOTION-005 | ecommerce-promotion | 订单创建中优惠券使用标记不具备幂等语义 | 高 | 在 `CouponUsageService.markCouponsUsed` 对已由同一订单使用的优惠券直接幂等成功，不同订单冲突 | 已修复 |
| REVIEW-001 | ecommerce-review | 重复评价和审核状态冲突未使用 `ConflictException` | 中 | 将 review 重复提交/状态冲突分支从 `BusinessException` 改为 `ConflictException` | 已修复 |
| REVIEW-002 | ecommerce-review | 追加非本人评价的权限禁止场景未使用 `AuthorizationException` | 中 | 将 `ReviewService.appendReview` 非本人操作分支改抛 `AuthorizationException` | 已修复 |
| REVIEW-003 | ecommerce-review | ReviewApproved 事件监听失败未保存失败记录 | 高 | 在 `ReviewApprovedEventListener` catch 分支持久化失败事件记录并接入重放 | 已修复 |
| USER-001 | ecommerce-user | 登录接口未实现同一用户名每分钟 5 次本地限流 | 高 | 在登录入口按 `LoginRequest.email` 接入 5 次/分钟限流并统一返回 `RATE_LIMITED` | 已修复 |
| USER-002 | ecommerce-user | 用户冻结/解冻未记录符合字段要求的审计日志 | 高 | 在 `UserAuthService.freezeUser/unfreezeUser` 状态变更成功后写入完整审计日志 | 已修复 |
| USER-003 | ecommerce-user | 用户注册通知未按 `NotificationRequest`/`LocalNotificationService` 提交 | 中 | 让 `UserRegisterService.register` 注册后提交明确的 `NotificationRequest` 给 `LocalNotificationService` | 已修复 |
| USER-004 | ecommerce-user | user 发布本地事件但未体现失败处理和强/非强一致边界 | 中 | 将注册后事件/通知处理调整为 AFTER_COMMIT 非强一致并持久化失败记录 | 已修复 |
