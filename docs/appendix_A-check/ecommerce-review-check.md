# ecommerce-review - 附录A 一致性检查

## 检查范围
- 模块：ecommerce-review
- 附录：附录A
- 输入资料：
  - `D:/Desktop/work/HW-ICT-CMP-04/README.md`：比赛边界、冻结 REST API 契约、错误响应、检查口径相关内容，重点纳入第 6.7 节和第 7 章。
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录A-API接口参考.md`：全文，重点纳入第 1 节通用约定和第 11 节评价接口。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java` 下所有源文件。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/test/java` 下所有测试源文件。
  - 当前模块配置文件：已检查 `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review`，未发现 `src/main/resources` 或 `src/test/resources` 配置文件。
  - 当前模块 POM：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/pom.xml`。
  - 整个项目 POM：`D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`。
  - 必要跨模块实现：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/dto/ApiError.java`，用于核对错误响应结构和异常到 HTTP 状态码的映射。

## 检查结论
- 共发现 1 处不一致。

## 不一致明细
### 1. 评价发布相关 403 错误码通过 BusinessException 返回为 400
- 设计要求定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/README.md:200`（第 7 章错误码）
  - `D:/Desktop/work/HW-ICT-CMP-04/README.md:218`（`USER_NOT_ACTIVE` 要求 HTTP 403）
  - `D:/Desktop/work/HW-ICT-CMP-04/README.md:219`（`USER_FROZEN` 要求 HTTP 403）
  - `D:/Desktop/work/HW-ICT-CMP-04/README.md:228`（`REVIEW_PURCHASE_REQUIRED` 要求 HTTP 403）
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录A-API接口参考.md:15`（错误响应结构）
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录A-API接口参考.md:294`（发布评价接口）
- 代码定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:110` 至 `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:116`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:119` 至 `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:126`
  - 必要跨模块映射：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:76` 至 `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:81`
- 不一致说明：发布评价时，当前模块在用户未激活、用户被冻结、未满足购买评价条件时分别抛出 `BusinessException("USER_NOT_ACTIVE", ...)`、`BusinessException("USER_FROZEN", ...)`、`BusinessException("REVIEW_PURCHASE_REQUIRED", ...)`；公共异常处理器将所有 `BusinessException` 固定映射为 HTTP 400。README 第 7 章要求这三个错误码的 HTTP 状态均为 403。
- 原因分析：设计要求是错误响应保持统一结构，并且 `USER_NOT_ACTIVE`、`USER_FROZEN`、`REVIEW_PURCHASE_REQUIRED` 对应 HTTP 403；当前实现虽然错误响应字段结构由 `ApiError` 保持为 `code/message/traceId/details`，但在评价发布链路中使用会被统一映射为 400 的 `BusinessException` 表达这些 403 错误，导致 HTTP 状态码不符合冻结契约。该问题属于行为不符。
