# M2 user-service 一致性审查报告

模块：M2 user-service  
模块目录：`code/ecommerce-user/`  
包名：`com.ecommerce.user`

## 审查结论

发现 1 项与本模块相关的文档不一致。

## 不一致项

### 1. 用户侧接口仅要求已认证，未按冻结 API 契约限制为 USER 角色

1. 实现位置：`code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java:47-48`
2. 设计依据：`README.md` API 基线 / 用户模块 `84-88` 行；`README.md` 修改边界 `35-38` 行
3. 不一致内容：
   - 文档冻结 API 要求 `GET /api/v1/users/me`、`POST /api/v1/users/addresses`、`GET /api/v1/users/addresses`、`PUT /api/v1/users/addresses/{addressId}`、`DELETE /api/v1/users/addresses/{addressId}` 的认证要求均为 `USER`。
   - 当前 user-service 安全配置仅将 `/api/v1/admin/**` 限制为 `ADMIN`，其余请求使用 `.anyRequest().authenticated()`，未对上述用户侧接口执行 `USER` 角色校验。
4. 原因分析与影响：
   - `JwtAuthFilter` 会从 JWT 中生成 `ROLE_<role>` 权限，但 `SecurityConfig` 对用户侧接口没有使用 `hasRole("USER")` 或等价规则，导致任意已认证角色均可能访问应限定为 USER 的用户资料和地址接口。
   - 这与冻结 API 契约中的认证要求不一致，并可能削弱 M2 职责中“权限”的实现边界。
