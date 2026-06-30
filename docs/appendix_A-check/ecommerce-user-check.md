# ecommerce-user - 附录A 一致性检查

## 检查范围
- 模块：ecommerce-user
- 附录：附录A
- 输入资料：
  - `D:/Desktop/work/HW-ICT-CMP-04/README.md` 中比赛边界、冻结 REST API 契约、错误响应、检查口径相关内容
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录A-API接口参考.md` 全文
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-user/src/main/java` 下所有源文件
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-user/src/test/java` 下所有测试源文件
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-user` 模块配置文件检查：未发现 `src/main/resources` 配置文件
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-user/pom.xml`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`
  - 为核对通用错误响应结构，读取跨模块公共错误处理实现：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/dto/ApiError.java`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java`

## 检查结论
- 共发现 1 处不一致

## 不一致明细

### 1. Spring Security 拦截的未认证/无权限错误未按冻结错误响应契约返回
- 设计要求定位：
  - `design-docs/附录A-API接口参考.md:15`-`design-docs/附录A-API接口参考.md:24` 要求错误响应固定为包含 `code`、`message`、`traceId`、`details` 的 JSON 结构。
  - `README.md:75` 要求错误响应结构不得修改。
  - `README.md:202`-`README.md:212` 定义通用错误码及 HTTP 状态，其中 `UNAUTHORIZED` 对应 401、`FORBIDDEN` 对应 403。
- 代码定位：
  - `code/ecommerce-user/src/main/java/com/ecommerce/user/security/JwtAuthFilter.java:53`-`code/ecommerce-user/src/main/java/com/ecommerce/user/security/JwtAuthFilter.java:57` 在缺失或格式错误的 `Authorization` Header 时只继续过滤链，不抛出业务异常或写入标准错误体。
  - `code/ecommerce-user/src/main/java/com/ecommerce/user/security/JwtAuthFilter.java:79`-`code/ecommerce-user/src/main/java/com/ecommerce/user/security/JwtAuthFilter.java:82` 在 JWT 校验失败时清空安全上下文后继续过滤链，也未返回标准错误体。
  - `code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java:43`-`code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java:54` 通过 Spring Security 规则拦截需要 USER/ADMIN 的接口，但未配置 `AuthenticationEntryPoint` / `AccessDeniedHandler` 来复用附录 A 规定的错误响应结构。
  - 现有测试也反映未认证请求被按 Spring Security 默认拦截处理：`code/ecommerce-user/src/test/java/com/ecommerce/user/controller/AddressControllerTest.java:117`-`code/ecommerce-user/src/test/java/com/ecommerce/user/controller/AddressControllerTest.java:124`、`code/ecommerce-user/src/test/java/com/ecommerce/user/controller/UserControllerTest.java:156`-`code/ecommerce-user/src/test/java/com/ecommerce/user/controller/UserControllerTest.java:160` 断言未认证访问返回 403，未断言附录 A 要求的 `code/message/traceId/details` 错误体。
- 不一致说明：用户模块的受保护接口（如 `/api/v1/users/me`、`/api/v1/users/addresses`、`/api/v1/admin/users/{userId}/freeze`）由 Spring Security 在 Controller/`GlobalExceptionHandler` 之前处理认证和授权失败。当前配置没有为 Spring Security 的未认证、无权限场景输出附录 A 固定的错误 JSON 结构；缺失或无效 Bearer Token 时也没有显式返回 `UNAUTHORIZED`/401 的标准错误响应，而是进入 Spring Security 默认拦截流程。
- 原因分析：设计要求所有错误响应均采用固定结构，且未认证应使用 `UNAUTHORIZED`/401、无权限应使用 `FORBIDDEN`/403；当前实现仅对进入业务异常处理链的异常由公共 `GlobalExceptionHandler` 包装为 `ApiError`，但 `ecommerce-user` 的 Spring Security 拦截路径没有配置标准错误处理器，导致认证/授权失败响应结构和未认证状态码不符合冻结契约。该问题属于结构不符和行为不符。
