# M12 app-bootstrap 一致性审查报告

模块目录：`code/ecommerce-app/`  
包名：`com.ecommerce.app`

## 审查结论

发现 4 条与本模块相关的文档不一致。

## 不一致清单

### 1. 暴露了数据库/沙箱 reset 接口

1. 实现位置：`code/ecommerce-app/src/main/java/com/ecommerce/app/controller/SystemAdminController.java:55-95`
2. 设计依据：`README.md:35-40`（禁止暴露数据库 reset/bootstrap 接口）；`design-docs/01-项目概述.md:105`（用例隔离不依赖业务 reset/bootstrap 接口）
3. 不一致内容：实现提供了 `POST /api/v1/admin/system/reset-sandbox`，并在方法内删除所有 `JpaRepository` 数据、清空缓存、运行时配置、时钟、故障注入和通知记录。
4. 原因分析与影响：文档明确禁止暴露数据库 reset/bootstrap 接口，且黑盒测试隔离应由测试 harness 通过新 Spring Boot 上下文、随机 H2 内存库和干净缓存状态提供。该接口把用例隔离能力暴露为 REST API，既超出冻结契约，也可能被调用后破坏运行中数据状态。

### 2. 暴露了 bootstrap 管理员接口并直接返回 token

1. 实现位置：`code/ecommerce-app/src/main/java/com/ecommerce/app/controller/SystemAdminController.java:97-127`
2. 设计依据：`README.md:35-40`（禁止暴露数据库 reset/bootstrap 接口）；`design-docs/01-项目概述.md:105`（黑盒测试 seed 管理员账号后通过登录接口获取管理员 token）
3. 不一致内容：实现提供了 `POST /api/v1/admin/system/bootstrap-admin`，会创建固定管理员账号，并直接返回 `token`。
4. 原因分析与影响：文档要求管理员账号由测试 harness seed，随后通过登录接口获取 token；不应通过业务 REST bootstrap 接口创建管理员或绕过登录直接发放 token。该实现暴露了未冻结的 bootstrap 能力，并改变了管理员 token 获取路径。

### 3. 管理支撑接口认证要求不一致：reset/bootstrap 被放行匿名访问

1. 实现位置：`code/ecommerce-app/src/main/java/com/ecommerce/app/SecurityConfig.java:63-65`
2. 设计依据：`README.md:184-198`（黑盒测试支撑管理接口全部需要 ADMIN 认证）；`design-docs/01-项目概述.md:107`（`/api/v1/admin/` 下支撑接口均使用 ADMIN 认证）
3. 不一致内容：安全配置对 `/api/v1/admin/system/reset-sandbox` 和 `/api/v1/admin/system/bootstrap-admin` 配置了 `permitAll()`，随后才对 `/api/v1/admin/**` 配置 `hasRole("ADMIN")`。
4. 原因分析与影响：`/api/v1/admin/` 下的支撑接口文档要求均使用 ADMIN 认证。当前两个管理路径被显式匿名放行，未认证请求即可触发清库/清缓存或创建管理员并获取 token，违反 ADMIN 认证约束并扩大安全影响面。

### 4. 管理接口错误响应未使用统一错误响应结构

1. 实现位置：`code/ecommerce-app/src/main/java/com/ecommerce/app/controller/FaultInjectionAdminController.java:20-22`；`code/ecommerce-app/src/main/java/com/ecommerce/app/controller/SystemAdminController.java:132-135`；`code/ecommerce-app/src/main/java/com/ecommerce/app/controller/SystemAdminController.java:144-145`；`code/ecommerce-app/src/main/java/com/ecommerce/app/controller/SystemAdminController.java:159-168`
2. 设计依据：`README.md:73-75`（错误响应结构属于冻结契约）；`README.md:200-230`（通用错误码和业务错误码）；`design-docs/01-项目概述.md:86-94`（错误响应统一返回 `code`、`message`、`traceId`、`details`）
3. 不一致内容：故障注入缺少 `fault` 时返回 `{"error":"fault name is required"}`；配置覆盖缺少 `value` 时返回 `{"error":"value is required"}`；配置查询不存在时返回 404 空响应体；设置测试时钟参数错误或时间格式错误时返回 `{"error":...}`。这些响应均未包含统一的 `code`、`message`、`traceId`、`details` 字段。
4. 原因分析与影响：文档把错误响应结构列为冻结 API 契约，并给出了统一结构和通用错误码。当前管理接口错误响应字段不一致或无响应体，黑盒调用方无法按统一契约解析错误码、追踪 ID 和详情。