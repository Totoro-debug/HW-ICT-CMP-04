# ecommerce-order - 附录A 一致性检查

## 检查范围
- 模块：ecommerce-order
- 附录：附录A
- 输入资料：
  - `README.md`：比赛边界、冻结 REST API 契约、错误响应、检查口径，重点包含 6.5、6.8、7 章及公开用例覆盖范围说明。
  - `design-docs/附录A-API接口参考.md` 全文，重点包含第 1、6、12 节。
  - `code/ecommerce-order/pom.xml`。
  - `code/pom.xml`。
  - `code/ecommerce-order/src/main/java` 下全部源文件。
  - `code/ecommerce-order/src/test/java` 下全部测试源文件。
  - `code/ecommerce-order/src/main/resources`、`code/ecommerce-order/src/test/resources`：检查时未发现当前模块资源配置文件。
  - 必要的跨模块错误响应实现：`code/ecommerce-common/src/main/java/com/ecommerce/common/dto/ApiError.java`、`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java`、`code/ecommerce-common/src/main/java/com/ecommerce/common/money/MoneyValidationUtil.java`。

## 检查结论
- 共发现 3 处不一致。
- 订单相关 API 路径、HTTP Method、认证注解、创建订单 Request/Response 201 字段、批量订单、购买校验、销售统计、超时取消扫描接口路径与成功状态码未发现与附录A/README冻结契约不一致。
- 不一致集中在错误码/错误 HTTP 状态契约实现。

## 不一致明细
### 1. 冻结或未激活用户下单错误码要求为 403，但当前订单模块会按通用业务异常返回 400
- 设计要求定位：`README.md:218`、`README.md:219`
- 代码定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPreconditionChecker.java:37`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPreconditionChecker.java:39`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPreconditionChecker.java:41`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPreconditionChecker.java:42`；跨模块实际映射：`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:76`、`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:81`
- 不一致说明：README 业务错误码表要求 `USER_NOT_ACTIVE` 和 `USER_FROZEN` 的 HTTP 状态均为 403。订单创建前置校验在用户状态为 `FROZEN` 时抛出 `BusinessException("USER_FROZEN", ...)`，用户非 `ACTIVE` 时抛出 `BusinessException("USER_NOT_ACTIVE", ...)`；但全局异常处理器对普通 `BusinessException` 固定返回 `HttpStatus.BAD_REQUEST`（400）。
- 原因分析：设计要求是错误码 `USER_FROZEN`/`USER_NOT_ACTIVE` 对应 HTTP 403；当前实现虽然使用了设计中的错误码，但异常类型进入 `handleBusiness` 后统一映射为 400，导致 HTTP 状态与冻结契约不一致。类型：行为不符。

### 2. 订单状态冲突错误码要求为 409，但当前订单模块状态冲突路径会返回 400
- 设计要求定位：`README.md:224`
- 代码定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderCancelService.java:88`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderCancelService.java:90`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderCancelService.java:97`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderCancelService.java:98`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderCancelService.java:192`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderCancelService.java:193`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderCancelService.java:240`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderCancelService.java:241`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderStateMachine.java:106`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderStateMachine.java:108`；跨模块实际映射：`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:76`、`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:81`
- 不一致说明：README 业务错误码表要求 `ORDER_STATUS_CONFLICT` 的 HTTP 状态为 409。订单取消、取消审核、状态机校验等状态冲突路径抛出的是 `BusinessException("ORDER_STATUS_CONFLICT", ...)`，而不是会被映射为 409 的冲突异常；全局异常处理器对普通 `BusinessException` 返回 400。
- 原因分析：设计要求是订单状态冲突返回 HTTP 409；当前实现的根因是订单模块用普通业务异常承载 `ORDER_STATUS_CONFLICT`，导致错误响应 code 虽为 `ORDER_STATUS_CONFLICT`，但 HTTP 状态为 400。类型：行为不符。

### 3. 订单金额非法错误码要求为 ORDER_INVALID_AMOUNT，但当前金额校验会暴露非冻结契约错误码
- 设计要求定位：`README.md:222`
- 代码定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:198`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:199`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:244`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:245`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderValidator.java:24`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderValidator.java:25`；跨模块实际错误码来源：`code/ecommerce-common/src/main/java/com/ecommerce/common/money/MoneyValidationUtil.java:33`、`code/ecommerce-common/src/main/java/com/ecommerce/common/money/MoneyValidationUtil.java:35`
- 不一致说明：README 业务错误码表要求订单金额非法使用 `ORDER_INVALID_AMOUNT`，HTTP 400。订单创建流程在商品金额和最终应付金额处调用 `orderValidator.validateAmount(...)`，该方法委托 `MoneyValidationUtil.validatePayableAmount(...)`；当金额为空或低于最小值时实际抛出的错误码为 `PAYABLE_AMOUNT_TOO_LOW`，不是冻结契约中的 `ORDER_INVALID_AMOUNT`。
- 原因分析：设计要求是订单金额非法统一暴露 `ORDER_INVALID_AMOUNT`；当前实现把金额非法拆成了内部错误码 `PAYABLE_AMOUNT_TOO_LOW`（并且折扣金额校验还可能使用 `DISCOUNT_AMOUNT_INVALID`），这些错误码不在 README 的订单金额非法契约中。类型：命名不符。
