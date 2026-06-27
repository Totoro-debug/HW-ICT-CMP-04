# ecommerce-payment 模块 03-通用规范与非功能设计检查报告

## 检查结论

### 一致

- Match：金额字段总体使用 `BigDecimal` 表示，支付、退款、发票、结算相关实体金额字段均未使用 `double`/`float` 作为入库金额类型；`PaymentRecord.orderAmount/paidAmount`、`RefundRecord.refundAmount`、`SettlementBatch` 汇总金额、`SettlementOrderItem` 金额字段均声明了 `scale = 2`。
- Match：支付回调请求包含 `paymentNo` 与 `callbackSequence` 字段，且当前实现至少对“当前记录已保存的同一 `callbackSequence`”做了重复回调短路处理。
- Match：黑盒测试隔离相关禁用项未发现业务 `reset`/`bootstrap` REST 接口或业务代码依赖；搜索范围为 `code/ecommerce-payment/src/main/java/com/ecommerce/payment/**/*.java`。
- Match：通知规范的禁止项未发现违规直连；搜索范围内未发现直接调用 `MockMailSender` 或 `MockSmsSender`。
- Match：支付成功后会发布 `PaymentSucceededEvent`，积分监听器捕获异常并记录错误日志，避免积分异常直接回滚支付主流程。

### 不一致

- Mismatch：金额舍入模式不符合 `HALF_UP`。
- Mismatch：应付金额校验只要求大于 0，未落实不得小于 `0.01`。
- Mismatch：退款金额计算可能产生 0 或负数并入库，缺少退款金额边界保护。
- Mismatch：订单/支付金额校验失败抛出 `BusinessException`，未抛 `OrderValidationException`。
- Mismatch：支付回调签名认证失败使用 `BusinessException("UNAUTHORIZED")`，HTTP 语义会落到通用业务异常而非 401/403。
- Mismatch：多处状态冲突/重复提交使用 `BusinessException("CONFLICT")`，未使用 `ConflictException`，HTTP 语义不能稳定返回 409。
- Mismatch：支付回调幂等键实现不完整，未以 `paymentNo + callbackSequence` 保存/查询处理结果并返回第一次处理结果。
- Mismatch：退款申请接口未包含 `refundRequestNo`，也未按该键实现幂等与重复请求返回第一次结果。
- Mismatch：支付回调接口未实现同一 `paymentNo` 每分钟 20 次本地限流，未发现 429/`RATE_LIMITED` 返回路径。
- Mismatch：退款审核未记录审计日志；结算批次生成也未记录审计日志。
- Mismatch：支付成功后的本地事件监听失败未保存失败记录到本地事件处理表；`PaymentLoyaltyEventListener` 捕获并吞掉异常导致公共发布器无法持久化失败事件。

## 详细检查项

### 1. 金额计算

#### 【Match】金额字段使用 `BigDecimal` 且主要入库字段声明 2 位小数

- 代码定位：
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\entity\PaymentRecord.java:28`、`:31`
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\entity\RefundRecord.java:36`
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\entity\SettlementBatch.java:28`、`:31`、`:34`
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\entity\SettlementOrderItem.java:27`、`:32`
- 设计要求定位：`03-通用规范与非功能设计.md §1`（第 5 行、第 12 行）。
- 一致描述：支付金额、退款金额、结算汇总金额、结算明细金额均使用 `BigDecimal`，JPA 金额列声明了 `scale = 2`。

#### 【Mismatch】舍入模式为 `HALF_DOWN`，不是 `HALF_UP`

- 代码定位：
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\money\MonetaryUtil.java:27-32`：`roundToCent` 使用 `RoundingMode.HALF_DOWN`。
  - payment 模块调用位置：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\RefundCalculator.java:37-38`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\InvoiceService.java:63`、`:84-85`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\SettlementBatchService.java:81-84`、`:94-97`。
- 设计要求定位：`03-通用规范与非功能设计.md §1`（第 12-13 行）。
- 不一致具体描述：payment 模块金额计算依赖 `MonetaryUtil`，该工具最终入库/分位舍入采用 `HALF_DOWN`，与设计要求的 `HALF_UP` 不一致。
- 原因解析：公共金额工具实现与通用设计冲突，payment 模块未在调用侧覆盖舍入策略，因此退款、发票税额、结算汇总等 payment 相关金额均会继承错误舍入模式。

#### 【Mismatch】应付金额未校验不得小于 `0.01`

- 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\PaymentValidator.java:45-48`。
- 设计要求定位：`03-通用规范与非功能设计.md §1`（第 16 行）。
- 不一致具体描述：当前仅校验 `request.getAmount().compareTo(BigDecimal.ZERO) <= 0`，允许 `0.001` 等大于 0 但小于 `0.01` 的应付金额进入后续校验。
- 原因解析：实现将“正数”误等同于“本系统支持的最小应付金额”，缺少显式 `BigDecimal("0.01")` 下限约束。

#### 【Mismatch】退款金额缺少边界保护，可能为 0 或负数

- 代码定位：
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\RefundCalculator.java:29-42`
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\RefundService.java:63-74`
- 设计要求定位：`03-通用规范与非功能设计.md §1`（第 15-16 行；payment 模块需重点检查退款金额）。
- 不一致具体描述：`RefundCalculator` 对 `paidAmount <= 0` 返回 `BigDecimal.ZERO`，并按 `paidAmount * (1 - feeRate) - 1` 计算退款金额；当支付金额较小时可能得到 0 或负值，`RefundService` 未再次校验即保存为 `RefundRecord.refundAmount`。
- 原因解析：退款计算未设置退款金额下限、未防止负数，也未校验退款金额不得超过支付金额；金额边界只依赖公式结果。

### 2. 通用异常

#### 【Mismatch】订单/支付金额校验失败未抛 `OrderValidationException`

- 代码定位：
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\PaymentValidator.java:51-54`
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\PaymentCallbackService.java:98-104`
- 设计要求定位：`03-通用规范与非功能设计.md §2`（第 29 行）。
- 不一致具体描述：支付发起金额与订单应付金额不一致、支付回调金额与订单/支付金额不一致时，当前抛出 `BusinessException("PAYMENT_AMOUNT_MISMATCH", ...)`，未抛 `OrderValidationException`。
- 原因解析：实现将订单金额校验失败归类为普通业务异常，未使用设计指定的订单校验异常类型，导致调用方无法按订单金额校验语义区分处理。

#### 【Mismatch】支付回调认证失败 HTTP 语义不符合 401/403

- 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\PaymentCallbackService.java:78-84`。
- 设计要求定位：`03-通用规范与非功能设计.md §2`（第 24 行）。
- 不一致具体描述：签名无效时抛出 `BusinessException("UNAUTHORIZED", ...)`，而不是 `AuthorizationException`。
- 原因解析：`BusinessException` 默认按通用业务异常处理；在已检查的公共异常映射中，`BusinessException` 未将 `UNAUTHORIZED` 解析为 401/403，因此该认证失败路径不能满足 `AuthorizationException` 的 HTTP 语义。

#### 【Mismatch】状态冲突/重复提交未稳定映射为 409

- 代码定位：
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\PaymentCallbackService.java:126-128`
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\PaymentValidator.java:67-70`
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\RefundService.java:58-60`、`:92-94`
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\RefundStageService.java:66-75`
- 设计要求定位：`03-通用规范与非功能设计.md §2`（第 26 行）。
- 不一致具体描述：多处状态冲突和重复提交场景使用 `BusinessException("CONFLICT", ...)`，而不是 `ConflictException`。
- 原因解析：公共异常映射只对 `ConflictException` 明确返回 409；`BusinessException("CONFLICT")` 未被稳定映射为 409，导致设计中的冲突 HTTP 语义不能保证。

### 3. 幂等规范

#### 【Mismatch】支付回调未完整按 `paymentNo + callbackSequence` 实现幂等

- 代码定位：
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\dto\PaymentCallbackRequest.java:7-12`
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\entity\PaymentRecord.java:45-46`
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\PaymentCallbackService.java:52-61`、`:107-115`
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\controller\PaymentController.java:53-60`
- 设计要求定位：`03-通用规范与非功能设计.md §3`（第 38 行、第 43 行）。
- 不一致具体描述：当前只在 `PaymentRecord` 上保存一个 `callbackSequence`，并仅当请求序列等于当前保存值时直接 `return`；没有以组合键 `paymentNo + callbackSequence` 建立幂等记录，也没有保存并返回第一次处理结果。若同一支付号经历不同序列后，旧序列重放无法按原组合键查询历史处理结果。
- 原因解析：实现把回调序列作为支付记录的单一状态字段，而不是独立幂等键记录；控制器固定返回 `"OK"`，无法表达“重复请求返回第一次处理结果”的契约。

#### 【Mismatch】退款申请未按 `refundRequestNo` 实现幂等

- 代码定位：
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\dto\RefundApplyRequest.java:7-9`（未包含 `refundRequestNo`）
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\entity\RefundRecord.java:24-37`（未包含 `refundRequestNo`）
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\RefundService.java:48-78`
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\repository\RefundRecordRepository.java:14-22`
- 设计要求定位：`03-通用规范与非功能设计.md §3`（第 39 行、第 43 行）。
- 不一致具体描述：退款申请 DTO、实体、仓储与服务均未出现 `refundRequestNo`；`applyRefund` 每次调用都会生成新的 `refundNo` 并保存新退款单，无法按 `refundRequestNo` 返回第一次处理结果。
- 原因解析：接口契约缺少设计指定的客户端幂等请求号，服务层也没有唯一索引/查询逻辑支撑退款申请幂等。

### 4. 本地限流

#### 【Mismatch】支付回调接口未实现同一 `paymentNo` 每分钟 20 次限流

- 代码定位：
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\controller\PaymentController.java:53-60`
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\PaymentCallbackService.java:45-72`
  - 已搜索范围：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\**\*.java`，未找到 `RateLimitException`、`RATE_LIMITED`、`429` 或限流器相关实现。
- 设计要求定位：`03-通用规范与非功能设计.md §4`（第 50 行、第 54 行）。
- 不一致具体描述：支付回调接口没有按 `paymentNo` 计数的本地限流逻辑，触发路径也未返回 429 与错误码 `RATE_LIMITED`。
- 原因解析：控制器和服务直接处理回调，缺少限流组件/拦截器/异常抛出点。

### 5. 黑盒测试隔离

#### 【Match】未发现业务 `reset`/`bootstrap` 接口或依赖

- 代码定位：已搜索 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\**\*.java`，未找到 `reset`/`bootstrap` 相关业务 REST 接口或业务依赖。
- 设计要求定位：`03-通用规范与非功能设计.md §5`（第 67 行）。
- 一致描述：payment 模块当前没有通过业务 REST API 暴露环境清理/初始化接口来满足黑盒测试隔离。

### 6. 审计日志

#### 【Mismatch】退款审核和结算批次生成未记录审计日志

- 代码定位：
  - 退款审核：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\RefundService.java:84-113`
  - 结算批次生成：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\SettlementBatchService.java:55-132`
  - 已搜索范围：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\**\*.java`，未找到 `Audit`/`audit` 审计日志服务、实体或调用。
- 设计要求定位：`03-通用规范与非功能设计.md §6`（第 77 行、第 79 行、第 81 行）。
- 不一致具体描述：退款审核只更新 `RefundRecord` 并写普通业务日志；结算批次生成只创建结算批次和明细并写普通业务日志。两者均未记录包含操作者、操作类型、业务 ID、操作前状态、操作后状态、操作时间、备注的审计日志。
- 原因解析：payment 模块缺少审计日志模型或审计服务调用，普通 `log.info` 不能满足可查询、结构化、字段完整的审计日志要求。

### 7. 通知规范

#### 【Match】未发现直接调用 `MockMailSender`/`MockSmsSender`

- 代码定位：已搜索 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\**\*.java`，未找到 `MockMailSender` 或 `MockSmsSender`。
- 设计要求定位：`03-通用规范与非功能设计.md §7`（第 85 行）。
- 一致描述：payment 模块当前未发现绕过 `LocalNotificationService` 直连邮件/短信模拟器的违规调用。

### 8. 本地事件失败处理

#### 【Match】支付成功事件已发布，积分监听器异常不会回滚支付主流程

- 代码定位：
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\PaymentService.java:91-102`
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\PaymentLoyaltyEventListener.java:24-38`
- 设计要求定位：`03-通用规范与非功能设计.md §8`（第 97-104 行）。
- 一致描述：支付确认会发布 `PaymentSucceededEvent`；积分监听器捕获异常并记录 `log.error`，不会把积分异常继续抛出到支付主流程。

#### 【Mismatch】监听器失败未保存失败记录到本地事件处理表

- 代码定位：
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\java\com\ecommerce\payment\service\PaymentLoyaltyEventListener.java:35-38`
  - `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-common\src\main\java\com\ecommerce\common\event\DomainEventPublisher.java:40-50`、`:53-65`
- 设计要求定位：`03-通用规范与非功能设计.md §8`（第 97-102 行、第 104 行）。
- 不一致具体描述：`PaymentLoyaltyEventListener` 捕获异常后只写错误日志并吞掉异常，没有保存失败记录到本地事件处理表；公共 `DomainEventPublisher` 只有在 `publishEvent` 抛出异常时才会 `persistFailure`，但当前监听器吞掉异常后发布器无法感知失败。
- 原因解析：失败处理职责被拆散且未闭环：监听器负责捕获，发布器负责持久化，但监听器没有把失败传递给发布器或直接写失败表，导致本地事件失败记录缺失。

## 检查遗漏声明

- 配置文件：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-payment\src\main\resources\*.yml` 未找到；`src\main\resources` 目录不存在。
- 订单金额校验：找到支付金额与订单应付金额一致性校验，但未找到抛出 `OrderValidationException` 的实现。
- 退款申请幂等：未找到 `refundRequestNo` 字段、唯一索引、仓储查询或按 `refundRequestNo` 返回首次结果的实现。
- 支付回调限流：未找到同一 `paymentNo` 每分钟 20 次的本地限流实现；未找到 `RATE_LIMITED` 返回路径。
- 审计日志：未找到 refund 审核审计日志实现；未找到 settlement batch 生成审计日志实现。
- 通知发送：payment 模块未找到 `LocalNotificationService` 或 `NotificationRequest` 调用；同时也未找到直接调用 `MockMailSender`/`MockSmsSender`。
- 支付成功后的物流/通知监听器：payment 模块只找到积分监听器 `PaymentLoyaltyEventListener`，未找到物流监听器或通知监听器。
- 本地事件失败处理：payment 模块未找到监听失败记录表写入逻辑；公共发布器存在失败持久化逻辑，但当前 payment 积分监听器吞掉异常，未找到能将该失败落表的实现。
