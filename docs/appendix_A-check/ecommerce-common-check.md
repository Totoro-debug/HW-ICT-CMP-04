# ecommerce-common - 附录A 一致性检查

## 检查范围
- 模块：ecommerce-common
- 附录：附录A
- 输入资料：
  - `README.md`：比赛边界、冻结 REST API 契约、错误响应、错误码、检查口径；重点纳入 `/api/v1/` 前缀、禁止修改 URL/Method/Header/Request/Response 字段名和类型、成功状态码、错误响应结构、公开用例不覆盖全部验收范围、设计文档为验收基准等内容。
  - `design-docs/附录A-API接口参考.md`：全文。
  - `code/ecommerce-common/pom.xml`。
  - `code/pom.xml`。
  - `code/ecommerce-common/src/main/java` 下全部 Java 源文件。
  - `code/ecommerce-common/src/test/java` 下全部 Java 测试源文件。
  - `code/ecommerce-common/src/main/resources`、`code/ecommerce-common/src/test/resources`：检查时目录下无配置资源文件。

## 检查结论
- 共发现 1 处不一致。
- 当前模块未定义普通 REST Controller 路径映射；与附录A直接相关的实现主要集中在通用错误响应 DTO 与全局异常处理支持。

## 不一致明细

### 1. 通用异常处理未按冻结错误码契约返回部分业务错误 HTTP 状态
- 设计要求定位：
  - `README.md:75`：冻结契约包含成功 HTTP 状态码、错误响应结构，均不得修改。
  - `README.md:218`：`USER_NOT_ACTIVE` 的 HTTP 状态为 403。
  - `README.md:219`：`USER_FROZEN` 的 HTTP 状态为 403。
  - `README.md:224`：`ORDER_STATUS_CONFLICT` 的 HTTP 状态为 409。
  - `README.md:227`：`REFUND_WAITING_WAREHOUSE_ACCEPT` 的 HTTP 状态为 409。
  - `README.md:228`：`REVIEW_PURCHASE_REQUIRED` 的 HTTP 状态为 403。
  - `design-docs/附录A-API接口参考.md:15`：错误响应为统一结构；结合 README 冻结错误码表，错误码对应 HTTP 状态属于接口契约。
- 代码定位：
  - `code/ecommerce-common/src/main/java/com/ecommerce/common/exception/BusinessException.java:7`：`BusinessException` 是业务异常基类。
  - `code/ecommerce-common/src/main/java/com/ecommerce/common/exception/BusinessException.java:12`：业务异常携带 `code`。
  - `code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:76`：`BusinessException` 由 `handleBusiness` 统一处理。
  - `code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:80`：响应体保留异常 `code`。
  - `code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:81`：所有普通 `BusinessException` 固定返回 `HttpStatus.BAD_REQUEST`（400）。
  - `code/ecommerce-common/src/test/java/com/ecommerce/common/exception/GlobalExceptionHandlerTest.java:38`：测试项明确覆盖“generic business forbidden-like codes”。
  - `code/ecommerce-common/src/test/java/com/ecommerce/common/exception/GlobalExceptionHandlerTest.java:41`：`USER_NOT_ACTIVE` 被断言为 400。
  - `code/ecommerce-common/src/test/java/com/ecommerce/common/exception/GlobalExceptionHandlerTest.java:42`：`USER_FROZEN` 被断言为 400。
  - `code/ecommerce-common/src/test/java/com/ecommerce/common/exception/GlobalExceptionHandlerTest.java:43`：`REVIEW_PURCHASE_REQUIRED` 被断言为 400。
  - `code/ecommerce-common/src/test/java/com/ecommerce/common/exception/GlobalExceptionHandlerTest.java:49`：`ORDER_STATUS_CONFLICT` 被断言为 400。
  - `code/ecommerce-common/src/test/java/com/ecommerce/common/exception/GlobalExceptionHandlerTest.java:50`：`REFUND_WAITING_WAREHOUSE_ACCEPT` 被断言为 400。
- 不一致说明：README 冻结契约为多个业务错误码指定了非 400 HTTP 状态，但 `ecommerce-common` 的通用业务异常处理对所有普通 `BusinessException` 固定返回 400。对于通过 `BusinessException` 携带的 `USER_NOT_ACTIVE`、`USER_FROZEN`、`REVIEW_PURCHASE_REQUIRED`、`ORDER_STATUS_CONFLICT`、`REFUND_WAITING_WAREHOUSE_ACCEPT` 等错误码，当前公共异常处理会产生与冻结契约不同的 HTTP 状态。
- 原因分析：设计要求是“错误码与 HTTP 状态”共同构成接口契约，且错误响应结构需统一。当前实现只保证响应体包含 `code/message/traceId/details`，但 `handleBusiness` 未根据错误码映射冻结契约中的 HTTP 状态，而是将普通业务异常统一归为 400；测试也固化了这些错误码返回 400 的行为。因此该问题属于行为不符（HTTP 状态码不符），同一根因为“公共 BusinessException 到 HTTP 状态的映射缺失/过度默认化”。
