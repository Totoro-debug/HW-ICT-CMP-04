# ecommerce-review 模块 03-通用规范与非功能设计检查报告

## 检查结论

### 一致

- Match：金额计算（`03-通用规范与非功能设计.md §1`）。已搜索 `code/ecommerce-review/src/main/java/com/ecommerce/review/**/*.java`、`code/ecommerce-review/pom.xml`、根 `code/pom.xml`；review 模块未找到金额字段、金额计算、`BigDecimal` 使用场景，也未找到以 `double`/`float` 表示金额的实现。
- Match：通用异常的部分使用（`03-通用规范与非功能设计.md §2`）。`ResourceNotFoundException` 用于 Review 不存在场景（`ReviewService.java:139`、`ReviewModerationService.java:45`、`ReviewModerationService.java:77`）；`AuthorizationException` 用于无法解析当前 principal 的未授权场景（`ReviewController.java:123`、`AdminReviewController.java:82`）；Bean Validation 注解用于请求参数校验（如 `ReviewCreateRequest.java:15`、`ReviewCreateRequest.java:24`、`ReviewCreateRequest.java:28`）。
- Match：幂等规范（`03-通用规范与非功能设计.md §3`）。已搜索 `code/ecommerce-review/src/main/java/com/ecommerce/review/**/*.java`；review 模块未找到创建订单、支付回调、退款申请、物流回调、发票申请接口，因此未触发该节列出的幂等键要求。
- Match：本地限流（`03-通用规范与非功能设计.md §4`）。已搜索 `code/ecommerce-review/src/main/java/com/ecommerce/review/**/*.java`；review 模块未找到登录、支付回调、商品搜索、创建订单接口，因此未触发该节列出的限流规则；同时未找到 `RateLimitException` 或 `RATE_LIMITED` 相关实现。
- Match：黑盒测试隔离（`03-通用规范与非功能设计.md §5`）。已搜索 `code/ecommerce-review/src/main/java/com/ecommerce/review/**/*.java`；未找到 `reset`、`bootstrap` 或用于清理环境的业务 REST API，业务代码未暴露 reset/bootstrap 接口。
- Match：审计日志（`03-通用规范与非功能设计.md §6`）。已搜索 `code/ecommerce-review/src/main/java/com/ecommerce/review/**/*.java`；review 模块未找到用户冻结/解冻、商品上下架、库存人工调整、订单取消审核、退款审核和仓库验收、发票开具、结算批次生成等该节列出的审计操作。
- Match：通知规范（`03-通用规范与非功能设计.md §7`）。已搜索 `code/ecommerce-review/src/main/java/com/ecommerce/review/**/*.java`；未找到直接调用 `MockMailSender`、`MockSmsSender`，也未找到通知发送逻辑；因此未发现违反“业务模块只能提交 `NotificationRequest` 给 `LocalNotificationService`”的直接发送实现。
- Match：配置文件检查。`code/ecommerce-review/src/main/resources/*.yml` 不存在；未发现 review 模块级 yml 配置引入与 03 通用规范冲突的配置项。

### 不一致

- Mismatch 1：状态冲突和重复提交未使用 `ConflictException`。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-review\src\main\java\com\ecommerce\review\service\ReviewService.java:75-79` 对重复评价抛出 `BusinessException("DUPLICATE_REVIEW", ...)`；`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-review\src\main\java\com\ecommerce\review\service\ReviewModerationService.java:47-50`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-review\src\main\java\com\ecommerce\review\service\ReviewModerationService.java:79-82` 对非 `PENDING_REVIEW` 状态审核抛出 `BusinessException("INVALID_REVIEW_STATUS", ...)`。
  - 设计要求定位：`03-通用规范与非功能设计.md §2`，`ConflictException` 用于“状态冲突、重复提交”，HTTP 状态码为 409；`BusinessException` 是通用业务异常，HTTP 状态码为 400。
  - 不一致具体描述：重复评价属于重复提交，非待审核状态的审核/驳回属于状态冲突，但实现均抛出 `BusinessException`，按设计语义会落到通用业务异常而非 409 冲突语义。
  - 原因解析：review 服务将具体冲突类业务错误统一归入 `BusinessException`，没有按通用异常规范区分 `ConflictException`。

- Mismatch 2：权限禁止场景未使用 `AuthorizationException`。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-review\src\main\java\com\ecommerce\review\service\ReviewService.java:141-144`，用户给非本人评价追加内容时抛出 `BusinessException("FORBIDDEN", ...)`。
  - 设计要求定位：`03-通用规范与非功能设计.md §2`，`AuthorizationException` 用于“未认证或无权限”，HTTP 状态码为 401/403；`BusinessException` 是通用业务异常，HTTP 状态码为 400。
  - 不一致具体描述：该场景是“只能追加自己的评价”的权限不足，应采用 `AuthorizationException` 或等价 403 语义；当前使用 `BusinessException` 会偏离 401/403 的通用异常语义。
  - 原因解析：业务层将权限校验失败表达为普通业务异常，未按通用异常规范映射到授权异常类型。

- Mismatch 3：本地事件失败处理未保存失败记录。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-review\src\main\java\com\ecommerce\review\service\ReviewApprovedEventListener.java:24-35`，`onReviewApproved` 捕获异常后仅 `log.error`；`ReviewApprovedEventListener.java:32` 注释明确为“Failure only logged, never persisted for retry”。已搜索范围：`code/ecommerce-review/src/main/java/com/ecommerce/review/**/*.java`，未找到本地事件处理表、失败记录 repository 或重放管理接口实现。
  - 设计要求定位：`03-通用规范与非功能设计.md §8`，事件监听器失败时必须记录失败日志、保存失败记录到本地事件处理表，非强一致监听器不回滚主业务事务，并可通过管理接口重放失败事件。
  - 不一致具体描述：监听器失败时只记录日志，没有保存失败记录到本地事件处理表，也未提供失败事件重放所需的本地记录支撑。
  - 原因解析：该监听器被作为 legacy review-side handler 保留，代码仅做日志兜底，没有接入通用本地事件失败处理机制。

## 检查遗漏声明

- 金额计算：未找到 review 模块中的金额字段、金额计算、最终入库金额、优惠金额、应付金额实现；未找到 `BigDecimal`、`double`、`float` 金额相关代码。
- 订单金额校验：未找到 review 模块中的订单金额校验实现；未找到 `OrderValidationException` 使用场景。
- 幂等接口：未找到创建订单、支付回调、退款申请、物流回调、发票申请接口；未找到 `externalOrderNo`、`paymentNo + callbackSequence`、`refundRequestNo`、`trackingNo + eventTime + status`、`invoiceRequestNo` 幂等键实现。
- 本地限流接口：未找到登录、支付回调、商品搜索、创建订单接口；未找到 `RATE_LIMITED` 返回码和 `RateLimitException` 使用。
- 黑盒测试隔离：未找到 reset/bootstrap 业务 REST API。
- 审计日志：未找到设计列出的用户冻结和解冻、商品上下架、库存人工调整、订单取消审核、退款审核和仓库验收、发票开具、结算批次生成操作；也未找到这些操作对应的审计日志字段实现。
- 通知规范：未找到 review 模块发送通知的实现；未找到 `LocalNotificationService`、`NotificationRequest`、`MockMailSender`、`MockSmsSender` 调用。
- 本地事件失败处理：找到 `ReviewApprovedEventListener`，但未找到本地事件处理表实体/repository、失败记录保存实现、失败事件管理重放接口；未找到支付成功后的物流、积分、通知监听器（这些监听器不属于 review 模块当前实现范围）。
- 配置文件：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-review\src\main\resources\*.yml` 未找到。