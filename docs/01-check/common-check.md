# M1 common 一致性审查报告

## 审查结论

发现 1 处与本模块相关的文档不一致。

## 不一致项

### 1. 通用业务异常的 HTTP 状态码映射未覆盖冻结错误码表

1. 实现位置：`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:76-81`
2. 设计依据：`README.md` 7.2 业务错误码，行 `216-229`；`README.md` 6 API 基线，行 `73-75`；`design-docs/01-项目概述.md` 7 统一响应格式，行 `86-94`
3. 不一致内容：
   - 实现中 `handleBusiness(BusinessException ex)` 对所有未被更具体异常处理器捕获的 `BusinessException` 均返回 `HttpStatus.BAD_REQUEST`。
   - 冻结错误码表明确规定部分业务错误码应返回非 400 状态，例如 `USER_NOT_ACTIVE`、`USER_FROZEN`、`REVIEW_PURCHASE_REQUIRED` 为 403，`ORDER_STATUS_CONFLICT`、`REFUND_WAITING_WAREHOUSE_ACCEPT` 为 409。
   - README 同时规定错误响应结构和错误码/HTTP 映射属于冻结契约，不得修改。
4. 原因分析与影响：
   - common 模块承担异常与错误响应基础设施职责，当前基础处理器只按异常类型统一落到 400，未按冻结错误码表中的业务错误码映射 HTTP 状态。
   - 当业务模块抛出普通 `BusinessException` 并携带上述业务错误码时，响应体的 `code/message/traceId/details` 结构仍可保持，但 HTTP 状态会与冻结契约不一致，可能导致黑盒用例按错误码校验状态码时失败。
