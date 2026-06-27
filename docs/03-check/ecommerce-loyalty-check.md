# ecommerce-loyalty 模块 03-通用规范与非功能设计检查报告

## 检查结论

### 一致

- Match：金额字段在积分抵扣金额、年消费金额等已找到实现中使用 `BigDecimal` 表示：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\dto\PointsEstimateRequest.java:16`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\dto\PointsEstimateResponse.java:12`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\entity\LoyaltyAccount.java:46`。
- Match：积分抵扣上限实现了“不超过可用积分、单笔 10,000 积分、订单金额 50%”的边界控制，避免抵扣金额超过订单金额：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\service\LoyaltyPointService.java:65`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\service\LoyaltyPointService.java:104`。
- Match：认证失败使用 `AuthorizationException.unauthorized(...)`，符合通用异常中未认证语义：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\controller\LoyaltyController.java:142`。
- Match：未发现业务代码暴露或依赖 `reset`/`bootstrap` 接口；已搜索范围为 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\**\*.java`。
- Match：未发现 loyalty 模块直接调用 `MockMailSender` 或 `MockSmsSender`，也未发现绕过 `LocalNotificationService` 的通知发送代码；已搜索范围为 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\**\*.java`。
- Match：支付/订单支付后的积分监听器采用 `@Async` 与 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`，主业务事务提交后异步处理，符合非强一致监听器“不回滚主业务事务”的方向：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\event\OrderPaidEventListener.java:39`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\event\PaymentSucceededEventListener.java:21`。
- Match：`OrderPaidEventListener` 在积分发放失败时记录失败日志并保存 `FailedEventRecord`：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\event\OrderPaidEventListener.java:55`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\event\OrderPaidEventListener.java:61`。

### 不一致

- Mismatch 1：积分抵扣金额舍入模式不符合金额计算规范。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\controller\LoyaltyController.java:83` 至 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\controller\LoyaltyController.java:84`。
  - 设计要求定位：`03-通用规范与非功能设计.md §1. 金额计算`，金额最终入库保留 2 位，舍入模式统一为 `RoundingMode.HALF_UP`。
  - 不一致具体描述：`redeemAmount` 使用 `divide(..., 2, RoundingMode.DOWN)` 计算积分抵扣金额，而不是统一使用 `HALF_UP`。
  - 原因解析：该实现将积分兑换金额按向下舍入处理，可能与全局金额舍入规则不一致。虽然 `100 points = 1 yuan` 的兑换在多数整数积分场景下恰好为两位小数，但代码层面没有遵循统一舍入模式。

- Mismatch 2：金额/倍率相关计算存在 `double` 使用，且积分价值计算链路混用 `double` 与 `BigDecimal`。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\service\LoyaltyPointService.java:93`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\service\LoyaltyPointService.java:192`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\service\LoyaltyPointService.java:196` 至 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\service\LoyaltyPointService.java:201`；相关倍率定义还包括 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\service\MemberBenefitService.java:24`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\service\MemberBenefitService.java:28`。
  - 设计要求定位：`03-通用规范与非功能设计.md §1. 金额计算`，所有金额使用 `BigDecimal`，禁止使用 `double` 或 `float` 表示金额。
  - 不一致具体描述：订单支付积分计算以 `BigDecimal amount` 为基数，但会员倍率、活动倍率使用 `double`，并通过 `BigDecimal.valueOf(double)` 参与积分/订单金额价值计算。
  - 原因解析：倍率本身不是金额字段，但它直接参与基于订单金额的积分价值计算，使用二进制浮点类型会扩大金额计算链路中的精度与一致性风险；该模块未统一用 `BigDecimal` 表达参与金额换算的倍率。

- Mismatch 3：订单金额校验失败未使用 `OrderValidationException`。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\dto\PointsEstimateRequest.java:14` 至 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\dto\PointsEstimateRequest.java:19`；`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\service\LoyaltyPointService.java:104` 至 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\service\LoyaltyPointService.java:109`。
  - 设计要求定位：`03-通用规范与非功能设计.md §2. 通用异常`，订单金额校验失败必须抛出 `OrderValidationException`，不得抛出 Java 标准 `IllegalArgumentException`。
  - 不一致具体描述：积分抵扣估算请求仅使用 Bean Validation 的 `@NotNull`、`@Positive` 校验 `orderAmount`，服务端 `redeemPoints` 也未显式校验 `orderAmount >= 0.01` 或在失败时抛出 `OrderValidationException`。
  - 原因解析：loyalty 模块存在与订单金额相关的积分抵扣估算/抵扣计算，但未建立设计指定的订单金额校验异常语义，导致金额校验失败无法按 `OrderValidationException` 统一归类。

- Mismatch 4：支付成功后积分发放缺少幂等保护，存在重复发积分风险。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\event\OrderPaidEventListener.java:45` 至 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\event\OrderPaidEventListener.java:54`；`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\service\LoyaltyPointService.java:215` 至 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\service\LoyaltyPointService.java:236`；`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\repository\PointsTransactionRepository.java:28` 至 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\repository\PointsTransactionRepository.java:30`。
  - 设计要求定位：`03-通用规范与非功能设计.md §3. 幂等规范`，重复请求应返回第一次处理结果，不得重复发积分；支付回调幂等键为 `paymentNo + callbackSequence`。
  - 不一致具体描述：`OrderPaidEventListener` 收到事件后直接调用 `earnPoints`，`earnPoints` 直接累加账户积分并保存交易记录，未先按 `paymentNo`、`callbackSequence`、`orderId` 或 `bizType + bizId` 判断是否已处理；`PointsTransactionRepository` 虽有 `existsByTypeAndBizTypeAndBizId`，但当前仅在积分过期中使用，支付积分入账未使用。
  - 原因解析：积分交易记录没有唯一幂等键约束，服务逻辑也没有重复处理返回第一次结果的分支；同一支付/订单支付事件重复投递时会再次增加 `totalPoints` 与 `availablePoints`。

- Mismatch 5：积分兑换/冻结/解冻/消费等写操作缺少幂等键，重复请求可能重复扣减或重复变更积分余额。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\service\LoyaltyPointService.java:104` 至 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\service\LoyaltyPointService.java:124`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\service\LoyaltyPointService.java:129` 至 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\service\LoyaltyPointService.java:170`。
  - 设计要求定位：`03-通用规范与非功能设计.md §3. 幂等规范`，重复请求不得重复扣款、重复扣库存、重复发积分或重复开票；对 loyalty 模块对应为不得重复扣减/冻结/消费积分。
  - 不一致具体描述：`redeemPoints` 记录交易时 `bizId` 为 `null`，`freezePoints`、`unfreezePoints`、`consumeFrozenPoints` 虽接收 `bizType/bizId`，但没有基于它们做已处理判断或返回首次处理结果。
  - 原因解析：积分写操作的事务只保证单次原子性，不保证请求级幂等性；调用方重试或重复消息会造成余额重复扣减、重复冻结、重复解冻或重复消费。

- Mismatch 6：部分事件监听器失败时未保存失败记录到本地事件处理表。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\event\PaymentSucceededEventListener.java:27` 至 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\event\PaymentSucceededEventListener.java:37`；`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\event\ShipmentDeliveredEventListener.java:27` 至 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\event\ShipmentDeliveredEventListener.java:37`；`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\event\ReviewApprovedEventListener.java:27` 至 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\event\ReviewApprovedEventListener.java:43`。
  - 设计要求定位：`03-通用规范与非功能设计.md §8. 本地事件失败处理`，事件监听器失败时记录失败日志、保存失败记录到本地事件处理表；非强一致监听器不回滚主业务事务。
  - 不一致具体描述：这些监听器仅记录错误日志，没有注入或调用 `FailedEventRecordRepository` 保存失败记录；`ReviewApprovedEventListener` 的注释还明确写明 “Failure only logged, never persisted for retry”。
  - 原因解析：当前只有 `OrderPaidEventListener` 实现了失败持久化；其他事件监听器不满足统一的本地事件失败处理规范，失败后无法通过本地事件处理表追踪和重放。

## 检查遗漏声明

- 配置文件：未找到 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\resources\*.yml`，且 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\resources` 目录不存在。
- 金额计算：未找到 loyalty 模块中“最终入库金额”为兑换金额/抵扣金额单独入库的实现；已搜索 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\**\*.java`。已找到 `LoyaltyAccount.annualConsumption` 数据库列声明为 `precision = 14, scale = 2`。
- 金额计算：未找到税额计算实现；loyalty 模块无税额场景。
- 金额计算：未找到优惠金额计算实现；loyalty 模块仅找到积分抵扣金额/积分价值估算相关实现。
- 金额计算：未找到应付金额不得小于 `0.01` 的显式校验实现；已搜索 `PointsEstimateRequest`、`LoyaltyPointService` 及 loyalty 控制器。
- 通用异常：未找到 `OrderValidationException` 在 loyalty 模块中的任何使用；已搜索 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-loyalty\src\main\java\com\ecommerce\loyalty\**\*.java`。
- 通用异常：未找到 loyalty 模块中 `ResourceNotFoundException`、`ValidationException`、`ConflictException`、`RateLimitException` 的直接使用；已搜索同一范围。已找到 `BusinessException` 与 `AuthorizationException` 使用。
- 幂等规范：未找到创建订单、退款申请、物流回调、发票申请接口实现；这些接口不在 loyalty 模块中。未找到 `callbackSequence` 字段或处理逻辑。
- 本地限流：未找到登录、支付回调、商品搜索、创建订单接口实现；这些接口不在 loyalty 模块中。未找到 `RateLimitException` 或 `RATE_LIMITED` 使用。
- 黑盒测试隔离：未找到 `reset` 或 `bootstrap` 业务接口。
- 审计日志：未找到用户冻结/解冻、商品上下架、库存人工调整、订单取消审核、退款审核和仓库验收、发票开具、结算批次生成的业务操作实现；这些设计列出的审计操作不在 loyalty 模块中。未找到专用 `Audit`/审计日志组件调用。
- 通知规范：未找到 loyalty 模块发送通知的实现；未找到 `LocalNotificationService`、`NotificationRequest`、`MockMailSender`、`MockSmsSender` 使用。
- 本地事件失败处理：未找到 `PaymentSucceededEventListener`、`ShipmentDeliveredEventListener`、`ReviewApprovedEventListener` 保存失败记录到本地事件处理表的实现；已找到 `OrderPaidEventListener` 的失败记录保存实现。
