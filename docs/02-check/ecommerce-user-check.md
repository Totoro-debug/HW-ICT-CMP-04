# ecommerce-user 模块设计一致性检查

## 检查结论

已按 `design-docs/02-系统架构.md`、`README.md` 第 6 节 API 基线与第 7 节错误码、父工程 `code/pom.xml`、模块 `code/ecommerce-user/pom.xml`、`code/ecommerce-user/src/main/java/` 下全部当前源文件进行核对。`code/ecommerce-user/src/main/resources/` 目录不存在，见“检查遗漏声明”。

结论：覆盖 8 个指定维度。发现主要不一致 2 项：

1. 用户注册未按领域事件 `UserRegisteredEvent` 解耦到 common notification，且同步通知失败可能影响注册事务。
2. 未实现用户权限缓存 `user:roles:{userId}` 及 30 分钟 TTL。

### 一致

1. 架构风格（`02-系统架构.md` §1、§3）
   - `ecommerce-user` 是父工程 `code/pom.xml` 中独立 Maven 模块（`code/pom.xml:13-25`），模块自身 POM 只依赖 `ecommerce-common`、Spring Security/Validation、JWT 相关库（`code/ecommerce-user/pom.xml:11-42`），符合模块化单体、共享 JVM/本地调用的基础形态。
   - 用户模块拥有自己的 controller/service/repository/entity/dto/query/security/config 包边界，例如 `code/ecommerce-user/src/main/java/com/ecommerce/user/repository/UserRepository.java:1-22`、`code/ecommerce-user/src/main/java/com/ecommerce/user/entity/User.java:13-35`、`code/ecommerce-user/src/main/java/com/ecommerce/user/query/UserQueryService.java:1-41`。
   - 未发现当前模块直接注入其它业务模块 Repository 或直接导入 `product/inventory/cart/order/payment/promotion/logistics/loyalty/review` 包，符合 `02-系统架构.md` §1 “禁止跨模块直接注入对方 Repository 或直接查询对方表”和 §3 “跨模块查询必须通过 QueryService 接口”的方向要求。
   - 跨模块通知依赖使用 `LocalNotificationService` 接口而非 common 具体实现（`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserRegisterService.java:4-6`、`30-39`），与 `02-系统架构.md` §4 中 `LocalNotificationService | common | all` 的本地接口方向一致。

2. 模块依赖方向（`02-系统架构.md` §2）
   - 设计依赖图中 user 位于上游，可被 order、review 使用；当前 `ecommerce-user` 模块自身未声明依赖 order/review 等下游模块（`code/ecommerce-user/pom.xml:11-42`）。
   - 父工程包含 `ecommerce-user` 与其它业务模块（`code/pom.xml:13-25`），未发现 user 模块通过 POM 反向依赖下游模块。

3. 关键本地接口（`02-系统架构.md` §4）
   - 设计要求：`UserQueryService | user | order、review | 查询用户状态、冻结状态`（`design-docs/02-系统架构.md` §4）。
   - 代码存在接口 `com.ecommerce.user.query.UserQueryService`，包位置为 user 模块 query 包（`code/ecommerce-user/src/main/java/com/ecommerce/user/query/UserQueryService.java:1-8`）。
   - 代码提供状态与冻结状态查询方法：`boolean isActive(Long userId)`（`code/ecommerce-user/src/main/java/com/ecommerce/user/query/UserQueryService.java:18-24`）、`boolean isFrozen(Long userId)`（`code/ecommerce-user/src/main/java/com/ecommerce/user/query/UserQueryService.java:27-32`）。
   - 代码提供实现 `UserQueryServiceImpl implements UserQueryService`，并以只读事务暴露查询能力（`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserQueryServiceImpl.java:20-22`、`40-52`）。
   - 跨模块 DTO 使用 `UserDto`、`AddressDto`，未暴露 JPA Entity（`code/ecommerce-user/src/main/java/com/ecommerce/user/query/UserDto.java:1-65`、`code/ecommerce-user/src/main/java/com/ecommerce/user/query/AddressDto.java:1-74`），符合 `02-系统架构.md` §3 DTO 规则。

4. 事务边界（`02-系统架构.md` §6）
   - `02-系统架构.md` §6 的 5 条事务边界主要约束订单、支付、批量订单、退款；未发现除用户注册事件失败策略外，针对 user 模块的明确事务范围要求。
   - 当前 user 模块写操作均标注事务边界：注册（`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserRegisterService.java:42-43`）、登录（`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java:56-57`）、激活（`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java:90-91`）、冻结/解冻（`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java:119-132`）、地址创建/更新/删除（`code/ecommerce-user/src/main/java/com/ecommerce/user/service/AddressService.java:30-31`、`58-59`、`84-85`）。
   - 查询类方法使用只读事务：`UserQueryServiceImpl` 类级 `@Transactional(readOnly = true)`（`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserQueryServiceImpl.java:20-22`）、地址列表查询（`code/ecommerce-user/src/main/java/com/ecommerce/user/service/AddressService.java:51-52`）。

5. 安全架构（`02-系统架构.md` §8）
   - 登录成功后签发 JWT：`UserAuthService.login` 调用 `jwtTokenProvider.generateToken(user.getId(), roles)`（`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java:72-84`），`JwtTokenProvider` 生成包含 subject=userId、roles claim 的 JWT（`code/ecommerce-user/src/main/java/com/ecommerce/user/service/JwtTokenProvider.java:38-50`）。
   - 用户侧接口使用 `Authorization: Bearer <token>`：`JwtAuthFilter` 从 `Authorization` 头读取并要求 `Bearer ` 前缀（`code/ecommerce-user/src/main/java/com/ecommerce/user/security/JwtAuthFilter.java:49-57`）。
   - 角色认证：`SecurityConfig` 对 `/api/v1/admin/**` 使用 `hasRole("ADMIN")`，对 `/api/v1/users/me` 和 `/api/v1/users/addresses/**` 使用 `hasRole("USER")`（`code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java:47-49`）。
   - 支付回调签名头 `X-Payment-Signature` 属于 payment 模块接口，设计文档未发现 user 模块需实现该签名头校验。

6. REST API 路径、HTTP Method、认证、成功状态（`README.md` §6.1 用户模块）
   - `POST /api/v1/users/register` 匿名，201：`UserController.register` 使用 `@PostMapping` 且返回 `HttpStatus.CREATED`（`code/ecommerce-user/src/main/java/com/ecommerce/user/controller/UserController.java:39-42`），SecurityConfig 放行该路径（`code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java:40-42`）。
   - `POST /api/v1/users/activate` 匿名，200：`UserController.activate`（`code/ecommerce-user/src/main/java/com/ecommerce/user/controller/UserController.java:45-48`），SecurityConfig 放行该路径（`code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java:40-42`）。
   - `POST /api/v1/users/login` 匿名，200：`UserController.login`（`code/ecommerce-user/src/main/java/com/ecommerce/user/controller/UserController.java:51-54`），SecurityConfig 放行该路径（`code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java:40-42`）。
   - `GET /api/v1/users/me` USER，200：`UserController.getCurrentUser`（`code/ecommerce-user/src/main/java/com/ecommerce/user/controller/UserController.java:57-63`），SecurityConfig 要求 USER（`code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java:48`）。
   - `POST /api/v1/users/addresses` USER，201：`AddressController.createAddress`（`code/ecommerce-user/src/main/java/com/ecommerce/user/controller/AddressController.java:32-38`），SecurityConfig 要求 USER（`code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java:49`）。
   - `GET /api/v1/users/addresses` USER，200：`AddressController.listAddresses`（`code/ecommerce-user/src/main/java/com/ecommerce/user/controller/AddressController.java:40-44`），SecurityConfig 要求 USER（`code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java:49`）。
   - `PUT /api/v1/users/addresses/{addressId}` USER，200：`AddressController.updateAddress`（`code/ecommerce-user/src/main/java/com/ecommerce/user/controller/AddressController.java:47-53`），SecurityConfig 要求 USER（`code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java:49`）。
   - `DELETE /api/v1/users/addresses/{addressId}` USER，204：`AddressController.deleteAddress` 返回 `noContent()`（`code/ecommerce-user/src/main/java/com/ecommerce/user/controller/AddressController.java:56-61`），SecurityConfig 要求 USER（`code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java:49`）。
   - `POST /api/v1/admin/users/{userId}/freeze` ADMIN，200：`AdminUserController.freezeUser`（`code/ecommerce-user/src/main/java/com/ecommerce/user/controller/AdminUserController.java:22-25`），SecurityConfig 对 `/api/v1/admin/**` 要求 ADMIN（`code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java:47`）。
   - `POST /api/v1/admin/users/{userId}/unfreeze` ADMIN，200：`AdminUserController.unfreezeUser`（`code/ecommerce-user/src/main/java/com/ecommerce/user/controller/AdminUserController.java:28-31`），SecurityConfig 对 `/api/v1/admin/**` 要求 ADMIN（`code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java:47`）。
   - README §6.1 未列出用户模块 Request/Response 字段明细；代码中用户相关 DTO 字段已确认存在：注册请求 `email/phone/password/nickname`（`code/ecommerce-user/src/main/java/com/ecommerce/user/dto/RegisterRequest.java:13-27`），激活请求 `token`（`code/ecommerce-user/src/main/java/com/ecommerce/user/dto/ActivateRequest.java:10-11`），登录请求 `email/password`（`code/ecommerce-user/src/main/java/com/ecommerce/user/dto/LoginRequest.java:10-14`），登录响应 `token/userId/roles`（`code/ecommerce-user/src/main/java/com/ecommerce/user/dto/LoginResponse.java:10-12`），用户响应 `userId/email/phone/nickname/status/role`（`code/ecommerce-user/src/main/java/com/ecommerce/user/dto/UserResponse.java:12-17`），地址请求/响应字段 `province/city/district/detail/receiverName/receiverPhone/isDefault` 与 `addressId`（`code/ecommerce-user/src/main/java/com/ecommerce/user/dto/AddressRequest.java:10-28`、`code/ecommerce-user/src/main/java/com/ecommerce/user/dto/AddressResponse.java:10-17`）。

7. README §7 错误码中与 user 模块相关要求
   - `USER_NOT_ACTIVE | 403`：登录时非 ACTIVE 且非 FROZEN 用户抛出 `BusinessException("USER_NOT_ACTIVE", ...)`（`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java:61-66`）。
   - `USER_FROZEN | 403`：登录时 FROZEN 用户抛出 `BusinessException("USER_FROZEN", ...)`（`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java:61-64`）。
   - 通用 `UNAUTHORIZED`：密码错误抛出 `AuthorizationException("UNAUTHORIZED", ...)`（`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java:68-70`）。
   - 通用 `RESOURCE_NOT_FOUND`、`CONFLICT` 在用户查找、重复邮箱/手机号、激活 token 冲突等场景有对应异常使用（`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserRegisterService.java:45-50`、`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java:92-104`、`code/ecommerce-user/src/main/java/com/ecommerce/user/service/AddressService.java:60-65`）。

### 不一致

1. 用户注册领域事件缺失，且同步通知失败策略不符合设计。
   - 设计要求定位：`02-系统架构.md` §5 “`UserRegisteredEvent | user | common notification | 失败记录日志，不回滚注册`”；§1 协作优先级要求“本地领域事件：用于……通知……弱耦合链路”；§3 “事件依赖：后置动作优先使用 ApplicationEvent”“事务：不允许一个模块的事务依赖非关键后置监听器成功”。
   - 代码定位：`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserRegisterService.java:42-73`。
   - 具体描述：`register(RegisterRequest request)` 在 `@Transactional` 方法内直接构造 `NotificationRequest` 并调用 `notificationService.send(notification)`；当前 user 模块未找到 `UserRegisteredEvent` 类、`ApplicationEventPublisher.publishEvent(...)`、`@EventListener` 监听器或事件失败记录逻辑。
   - 原因解析：设计将“用户注册后通知”定义为 `UserRegisteredEvent`，发布方为 user，监听方为 common notification，失败策略为记录日志且不回滚注册。当前实现是事务内同步调用 `LocalNotificationService`，不是通过领域事件解耦；若通知调用在进入自身异常吞吐前抛出运行时异常，注册事务存在被回滚风险，不满足“失败记录日志，不回滚注册”的设计语义。

2. 用户权限缓存未实现。
   - 设计要求定位：`02-系统架构.md` §7 缓存设计表：“用户权限 | `user:roles:{userId}` | 30 分钟 | user”。
   - 代码定位：`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java:72-84`、`code/ecommerce-user/src/main/java/com/ecommerce/user/service/JwtTokenProvider.java:38-50`、`code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java:33-56`、`code/ecommerce-user/src/main/java/com/ecommerce/user/security/JwtAuthFilter.java:49-68`。
   - 具体描述：登录时直接从 `user.getRole().name()` 生成 roles 列表并写入 JWT；安全过滤器直接从 JWT claims 读取 roles；未找到 `@Cacheable/@CachePut/@CacheEvict`、缓存配置、缓存服务、`user:roles:{userId}` Key 或 30 分钟 TTL 配置。
   - 原因解析：设计明确 user 模块拥有“用户权限”缓存，Key 格式和 TTL 均固定。当前实现没有任何用户角色/权限缓存层，因此不满足缓存设计要求。

## 检查遗漏声明

1. 架构风格 —— 已检查 `02-系统架构.md` §1、§3；未发现 user 模块跨模块直接注入其它模块 Repository 或直接访问其它模块表。
2. 模块依赖方向 —— 已检查 `02-系统架构.md` §2、父工程 `code/pom.xml` 和 `code/ecommerce-user/pom.xml`；未发现 user 模块对下游业务模块的 POM 依赖。
3. 关键本地接口 —— 已检查 `02-系统架构.md` §4；找到 `UserQueryService` 及实现。设计文档未发现 user 模块需提供除 `UserQueryService`、使用 `LocalNotificationService` 外的其它关键本地接口。
4. 领域事件 —— 已检查 `02-系统架构.md` §5；未找到 `UserRegisteredEvent`、事件发布方实现、事件监听器、事件失败记录/失败策略实现。user 模块没有 `event` 或 `listener` 目录。
5. 事务边界 —— 已检查 `02-系统架构.md` §6；设计文档未发现除注册事件失败不得回滚外的 user 模块专属事务边界要求。代码中找到注册、登录、激活、冻结/解冻、地址写操作事务；同步通知处与事件失败策略存在不一致，已列入“不一致”。
6. 缓存设计 —— 已检查 `02-系统架构.md` §7；未找到 user 模块 `cache` 目录、缓存配置、`user:roles:{userId}` Key 或 30 分钟 TTL 实现。
7. 安全架构 —— 已检查 `02-系统架构.md` §8；JWT、Bearer 头、USER/ADMIN 角色认证已找到。支付回调签名头 `X-Payment-Signature` 非 user 模块职责，设计文档未发现本模块相关实现要求。
8. REST API 与错误码 —— 已检查 `README.md` §6.1 用户模块及 §7 错误码；10 个用户 API 的路径、HTTP Method、认证与成功状态均找到。README §6.1 未提供用户模块 Request/Response 字段冻结明细，只能按当前 DTO 字段列出实现情况。README §7 与本模块直接相关的 `USER_NOT_ACTIVE`、`USER_FROZEN` 已找到。
9. 配置资源目录 —— `code/ecommerce-user/src/main/resources/` 不存在，未找到本模块 application 配置文件或资源配置文件。
