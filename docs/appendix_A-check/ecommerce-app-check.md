# ecommerce-app - 附录A 一致性检查

## 检查范围
- 模块：ecommerce-app
- 附录：附录A
- 输入资料：
  - `README.md` 中比赛边界、冻结 REST API 契约、错误响应、检查口径相关内容
  - `design-docs/附录A-API接口参考.md` 全文
  - `code/ecommerce-app/src/main/java` 下源文件
  - `code/ecommerce-app/src/test/java` 下测试源文件
  - `code/ecommerce-app/src/main/resources/application.yml`
  - `code/ecommerce-app/src/test/resources/application-test.yml`
  - `code/ecommerce-app/pom.xml`
  - `code/pom.xml`

## 检查结论
- 共发现 1 处不一致。

## 不一致明细
### 1. 安全认证失败响应未按冻结契约返回 401/标准错误结构
- 设计要求定位：
  - `README.md:73`-`README.md:75`：所有 API 前缀、字段、成功状态码、错误响应结构均为冻结契约，不得修改。
  - `README.md:200`-`README.md:212`：通用错误码要求 `UNAUTHORIZED` 对应 HTTP 401，`FORBIDDEN` 对应 HTTP 403。
  - `design-docs/附录A-API接口参考.md:15`-`design-docs/附录A-API接口参考.md:24`：错误响应必须包含 `code`、`message`、`traceId`、`details`。
- 代码定位：
  - `code/ecommerce-app/src/main/java/com/ecommerce/app/SecurityConfig.java:57`-`code/ecommerce-app/src/main/java/com/ecommerce/app/SecurityConfig.java:69`：仅配置了路径授权规则，未配置认证失败/拒绝访问时返回冻结契约要求的 JSON 错误结构与 `UNAUTHORIZED`/`FORBIDDEN` 映射。
  - `code/ecommerce-app/src/test/java/com/ecommerce/app/config/SecurityConfigTest.java:164`-`code/ecommerce-app/src/test/java/com/ecommerce/app/config/SecurityConfigTest.java:183`：当前模块测试明确断言未认证访问 USER 接口返回 403。
- 不一致说明：附录A和 README 要求未认证错误使用 `UNAUTHORIZED` 且 HTTP 401，并且所有错误响应统一为包含 `code`、`message`、`traceId`、`details` 的结构。当前 `SecurityConfig` 只声明 `/api/v1/admin/**` 需要 ADMIN、`/api/v1/**` 需要 USER，未声明 Spring Security 的 `AuthenticationEntryPoint`/`AccessDeniedHandler` 来输出统一错误结构；同时模块测试表明未认证访问 USER 接口当前按 403 处理，而不是设计要求的 401。
- 原因分析：设计要求是冻结的认证错误契约；当前实现将安全异常交由默认 Spring Security 处理，并在测试中固化了未认证返回 403 的行为，导致未认证状态码与错误响应结构均不符合设计。该问题属于行为不符和结构不符。