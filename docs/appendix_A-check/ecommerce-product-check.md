# ecommerce-product - 附录A 一致性检查

## 检查范围
- 模块：ecommerce-product
- 附录：附录A
- 输入资料：
  - `D:/Desktop/work/HW-ICT-CMP-04/README.md`（比赛边界、冻结 REST API 契约、错误响应、检查口径）
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录A-API接口参考.md`（全文）
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/src/main/java` 下所有 Java 源文件
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/src/test/java` 下所有 Java 测试源文件
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product` 模块配置文件检查：`src/main/resources`、`src/test/resources` 目录不存在，未发现模块内配置文件
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/pom.xml`
  - `D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`
  - 为核对错误响应结构，补充读取跨模块公共/应用实现：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/dto/ApiError.java`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-app/src/main/java/com/ecommerce/app/SecurityConfig.java`

## 检查结论
- 共发现 1 处不一致。

## 不一致明细

### 1. 管理商品接口的认证/鉴权失败未按冻结错误响应结构返回
- 设计要求定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录A-API接口参考.md:15`-`24`：错误响应必须包含 `code`、`message`、`traceId`、`details` 结构。
  - `D:/Desktop/work/HW-ICT-CMP-04/README.md:73`-`75`：所有 API 前缀及 URL/Method/请求响应字段/成功状态码/错误响应结构为冻结契约。
  - `D:/Desktop/work/HW-ICT-CMP-04/README.md:200`-`212`：通用错误码要求 `UNAUTHORIZED` 为 401、`FORBIDDEN` 为 403。
- 代码定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-product/src/main/java/com/ecommerce/product/controller/AdminProductController.java:27`-`29`：商品管理接口位于 `/api/v1/admin/products`，并声明 `@PreAuthorize("hasRole('ADMIN')")`。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-app/src/main/java/com/ecommerce/app/SecurityConfig.java:57`-`72`：统一安全过滤链将 `/api/v1/admin/**` 配置为 `hasRole("ADMIN")`，但该配置片段未配置 `exceptionHandling`、`AuthenticationEntryPoint` 或 `AccessDeniedHandler` 来输出冻结的 `ApiError` JSON 结构。
  - 对照：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:33`-`42` 仅处理进入 MVC 异常处理链的 `AuthorizationException`；Spring Security 过滤链产生的未认证/无权限失败不会由该 `@RestControllerAdvice` 保证转换为上述结构。
- 不一致说明：`ecommerce-product` 的 SPU/SKU 创建、上下架等管理接口需要 ADMIN 认证。设计要求认证失败/无权限失败也必须使用统一错误响应结构，并使用 `UNAUTHORIZED`/`FORBIDDEN` 错误码；当前实现的安全过滤链只配置了访问规则，未配置安全层 401/403 的 JSON 错误响应转换，导致这些商品管理接口在未认证或权限不足时不能保证返回冻结契约中的 `code/message/traceId/details` 结构。
- 原因分析：设计要求是统一错误响应结构和通用错误码；当前实现是商品管理接口触发 Spring Security 过滤链鉴权，安全配置缺少将安全异常写成 `ApiError` 的入口/拒绝处理器。该问题属于结构不符。
