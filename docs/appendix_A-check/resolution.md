# 附录A 修复方案汇总

## 总体说明

本文件仅覆盖 `docs/appendix_A-check` 下各 `*-check.md` 已报告的附录A不一致项，不新增报告外问题，不建议修改冻结 API 契约、`README.md` 或 `design-docs/附录A-API接口参考.md`。修复边界以 README 的冻结契约、错误码/错误响应要求，以及 `design-docs/附录A-API接口参考.md` 为准：保持 `/api/v1/` 前缀、REST URL、HTTP Method、Header、Request/Response 字段名和类型、成功状态码、错误响应 JSON 结构不变。

无问题模块：`ecommerce-cart`、`ecommerce-inventory`、`ecommerce-logistics`、`ecommerce-loyalty`、`ecommerce-payment`、`ecommerce-promotion`。

已报告问题共 8 项，建议归并为 3 个可落地修复方案：
- R1：统一 Spring Security 认证/鉴权失败的 `ApiError` JSON 响应与 401/403 状态码。
- R2：补齐 `BusinessException` 错误码到冻结 HTTP 状态的公共映射。
- R3：订单金额非法统一暴露冻结错误码 `ORDER_INVALID_AMOUNT`。

## 修复方案明细

### R1. 统一 Spring Security 认证/鉴权失败响应

- 所属模块：`ecommerce-app`、`ecommerce-user`；影响 `ecommerce-product` 管理接口的认证/鉴权失败响应。
- 覆盖的问题：
  - `ecommerce-app`：安全认证失败响应未按冻结契约返回 401/标准错误结构。
  - `ecommerce-product`：管理商品接口的认证/鉴权失败未按冻结错误响应结构返回。
  - `ecommerce-user`：Spring Security 拦截的未认证/无权限错误未按冻结错误响应契约返回。
- 检查报告定位：
  - `docs/appendix_A-check/ecommerce-app-check.md:20`-`docs/appendix_A-check/ecommerce-app-check.md:28`
  - `docs/appendix_A-check/ecommerce-product-check.md:21`-`docs/appendix_A-check/ecommerce-product-check.md:31`
  - `docs/appendix_A-check/ecommerce-user-check.md:21`-`docs/appendix_A-check/ecommerce-user-check.md:32`
- 设计依据定位：
  - `README.md:73`-`README.md:75`：API 前缀、URL/Method、请求响应字段、成功状态码、错误响应结构为冻结契约。
  - `README.md:202`-`README.md:212`：`UNAUTHORIZED` 对应 HTTP 401，`FORBIDDEN` 对应 HTTP 403。
  - `design-docs/附录A-API接口参考.md:7`-`design-docs/附录A-API接口参考.md:13`：认证 Header 与管理接口 `ADMIN` 角色要求。
  - `design-docs/附录A-API接口参考.md:15`-`design-docs/附录A-API接口参考.md:24`：错误响应固定包含 `code`、`message`、`traceId`、`details`。
- 当前实现定位：
  - `code/ecommerce-app/src/main/java/com/ecommerce/app/SecurityConfig.java:53`-`code/ecommerce-app/src/main/java/com/ecommerce/app/SecurityConfig.java:72`：仅配置 CSRF、无状态会话、路径授权规则与 JWT filter，未配置 `exceptionHandling`。
  - `code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java:39`-`code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java:57`：用户模块独立安全链同样未配置 `AuthenticationEntryPoint` / `AccessDeniedHandler`。
  - `code/ecommerce-user/src/main/java/com/ecommerce/user/security/JwtAuthFilter.java:53`-`code/ecommerce-user/src/main/java/com/ecommerce/user/security/JwtAuthFilter.java:57`：缺失或格式错误的 `Authorization` Header 继续过滤链。
  - `code/ecommerce-user/src/main/java/com/ecommerce/user/security/JwtAuthFilter.java:79`-`code/ecommerce-user/src/main/java/com/ecommerce/user/security/JwtAuthFilter.java:84`：JWT 校验失败后清空上下文并继续过滤链。
  - `code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:33`-`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:42`：只处理进入 MVC 异常链的 `AuthorizationException`，不能覆盖 Spring Security filter chain 中直接产生的 401/403。
  - `code/ecommerce-product/src/main/java/com/ecommerce/product/controller/AdminProductController.java:27`-`code/ecommerce-product/src/main/java/com/ecommerce/product/controller/AdminProductController.java:29`：商品管理接口为 `/api/v1/admin/products` 且要求 `ADMIN`，会受应用安全链影响。
- 修复目标：
  - 未认证访问受保护 USER/ADMIN 接口时返回 HTTP 401，响应体为 `ApiError` 结构，`code="UNAUTHORIZED"`。
  - 已认证但角色不足访问 ADMIN 或 USER 受保护接口时返回 HTTP 403，响应体为 `ApiError` 结构，`code="FORBIDDEN"`。
  - 不改变任何 URL、HTTP Method、认证 Header 名称、成功状态码、业务 Request/Response DTO。
- 修复方案：
  1. 在 `ecommerce-common` 新增或复用公共安全错误写出组件，建议新增 `com.ecommerce.common.security.ApiSecurityErrorWriter`（或等价类）：
     - 依赖 `ApiError`，使用 `ObjectMapper` 将标准错误 JSON 写入 `HttpServletResponse`。
     - 提供 `writeUnauthorized(HttpServletRequest, HttpServletResponse, Exception)` 和 `writeForbidden(...)` 方法，分别写入 HTTP 401/403、`Content-Type: application/json;charset=UTF-8`。
     - `traceId` 可沿用 `GlobalExceptionHandler` 的格式策略，生成非空字符串；`details` 使用空 `Map`，除非后续已有 trace 上下文可取。
     - `message` 建议稳定为 `Unauthorized` / `Forbidden` 或现有异常消息的安全摘要，不暴露 token 细节。
  2. 在 `code/ecommerce-app/src/main/java/com/ecommerce/app/SecurityConfig.java` 注入该组件，并在 `HttpSecurity` 链中增加：
     - `.exceptionHandling(ex -> ex.authenticationEntryPoint(apiSecurityErrorWriter::writeUnauthorized).accessDeniedHandler(apiSecurityErrorWriter::writeForbidden))`
     - 保持 `requestMatchers` 与 `addFilterBefore(new JwtAuthFilter(...))` 原有顺序和路径规则不变。
  3. 在 `code/ecommerce-user/src/main/java/com/ecommerce/user/config/SecurityConfig.java` 同步注入并配置同一 `exceptionHandling`，保证用户模块独立测试启动时也符合附录A。
  4. `JwtAuthFilter` 不必在缺失 Header 时直接写响应，可继续让 Spring Security 根据目标路径触发 `AuthenticationEntryPoint`；但需确认无效 token 场景清空上下文后最终也进入 `AuthenticationEntryPoint` 返回 401 标准体。若现有过滤链因异常被吞导致路径落到 `AccessDeniedHandler`，则在 `JwtAuthFilter` 捕获无效 token 后设置 request attribute 供 entry point 识别，不建议在 filter 中绕过统一 writer。
  5. 更新相关测试断言：
     - `code/ecommerce-app/src/test/java/com/ecommerce/app/config/SecurityConfigTest.java:164`-`code/ecommerce-app/src/test/java/com/ecommerce/app/config/SecurityConfigTest.java:183` 中未认证 USER 接口预期由 403 改为 401，并断言 JSON 字段 `code/message/traceId/details`。
     - `code/ecommerce-user/src/test/java/com/ecommerce/user/controller/AddressControllerTest.java:117`-`code/ecommerce-user/src/test/java/com/ecommerce/user/controller/AddressControllerTest.java:124`、`code/ecommerce-user/src/test/java/com/ecommerce/user/controller/UserControllerTest.java:156`-`code/ecommerce-user/src/test/java/com/ecommerce/user/controller/UserControllerTest.java:160` 中未认证断言同步改为 401 标准错误体；补充角色不足访问 ADMIN 返回 403 标准错误体。
  6. POM：`ecommerce-common` 已有 `spring-boot-starter-web`，可使用 Jackson 和 Servlet API；`ecommerce-app`、`ecommerce-user` 已依赖 `ecommerce-common` 和 `spring-boot-starter-security`，通常无需新增 POM 依赖。如新增类使用 Security 接口类型仅放在 app/user 配置中，不要让 common 依赖 security；若公共 writer 方法签名不引用 Security 类型，仅引用 servlet，则无需改 POM。
- 影响范围：
  - 影响所有经 Spring Security filter chain 拦截的 `/api/v1/**` 认证/鉴权失败响应，包括商品管理、用户受保护接口、订单/购物车/促销/积分/评价等受保护接口。
  - 正常认证成功、匿名开放接口、业务异常响应不应受影响。
- 注意事项/风险点：
  - 不要修改 `/api/v1/admin/**`、`/api/v1/**` 授权规则本身；本方案只补错误响应写出。
  - 不要将无效 token 误判为 403；未认证或认证失败应统一 401。
  - `details` 必须序列化为 JSON 对象 `{}`，不可为字符串或省略字段。
  - 若 app 与 user 模块测试上下文同时发现多个 `SecurityFilterChain`，需保持 bean 命名/配置隔离，避免破坏当前启动方式。
- 建议验证方式：
  - 单元/切片测试：未带 Header 访问 `/api/v1/cart`、`/api/v1/users/me` 返回 401 且 body 包含 `code=UNAUTHORIZED`、`message`、非空 `traceId`、`details={}`。
  - 使用 USER token 访问 `/api/v1/admin/products/spu` 返回 403 且 body 包含 `code=FORBIDDEN`。
  - 回归匿名接口 `/api/v1/products`、`/api/v1/inventory/check`、`/api/v1/reviews/product/{productId}` 不需要认证。

### R2. 补齐 BusinessException 错误码到冻结 HTTP 状态的公共映射

- 所属模块：`ecommerce-common`；覆盖 `ecommerce-order`、`ecommerce-review` 中使用普通 `BusinessException` 承载 403/409 错误码导致的状态码不一致。
- 覆盖的问题：
  - `ecommerce-common`：通用异常处理未按冻结错误码契约返回部分业务错误 HTTP 状态。
  - `ecommerce-order`：冻结或未激活用户下单错误码要求为 403，但当前订单模块会按通用业务异常返回 400。
  - `ecommerce-order`：订单状态冲突错误码要求为 409，但当前订单模块状态冲突路径会返回 400。
  - `ecommerce-review`：评价发布相关 403 错误码通过 `BusinessException` 返回为 400。
- 检查报告定位：
  - `docs/appendix_A-check/ecommerce-common-check.md:21`-`docs/appendix_A-check/ecommerce-common-check.md:43`
  - `docs/appendix_A-check/ecommerce-order-check.md:22`-`docs/appendix_A-check/ecommerce-order-check.md:32`
  - `docs/appendix_A-check/ecommerce-review-check.md:20`-`docs/appendix_A-check/ecommerce-review-check.md:33`
- 设计依据定位：
  - `README.md:214`-`README.md:229`：业务错误码与 HTTP 状态冻结表；其中 `USER_NOT_ACTIVE`、`USER_FROZEN`、`REVIEW_PURCHASE_REQUIRED` 为 403，`ORDER_STATUS_CONFLICT`、`REFUND_WAITING_WAREHOUSE_ACCEPT` 为 409。
  - `design-docs/附录A-API接口参考.md:15`-`design-docs/附录A-API接口参考.md:24`：错误响应固定结构。
  - `design-docs/附录A-API接口参考.md:189`-`design-docs/附录A-API接口参考.md:200`：订单接口相关路径与认证维持不变。
  - `design-docs/附录A-API接口参考.md:290`-`design-docs/附录A-API接口参考.md:299`：评价接口相关路径与认证维持不变。
- 当前实现定位：
  - `code/ecommerce-common/src/main/java/com/ecommerce/common/exception/BusinessException.java:10`-`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/BusinessException.java:18`：`BusinessException` 携带错误码但不携带 HTTP 状态。
  - `code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:76`-`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:81`：所有普通 `BusinessException` 固定返回 400。
  - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPreconditionChecker.java:37`-`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPreconditionChecker.java:42`：抛出 `USER_FROZEN` / `USER_NOT_ACTIVE`。
  - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderCancelService.java:88`-`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderCancelService.java:98`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderCancelService.java:192`-`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderCancelService.java:193`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderCancelService.java:240`-`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderCancelService.java:241`：状态冲突统一抛 `ORDER_STATUS_CONFLICT`。
  - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderStateMachine.java:106`-`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderStateMachine.java:109`：非法状态转换抛 `ORDER_STATUS_CONFLICT`。
  - `code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:110`-`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:126`：发布评价前置条件抛 `USER_NOT_ACTIVE`、`USER_FROZEN`、`REVIEW_PURCHASE_REQUIRED`。
- 修复目标：
  - 保持错误响应 body 的 `code`、`message`、`traceId`、`details` 结构不变。
  - `BusinessException` 携带以下错误码时按冻结契约返回对应 HTTP 状态：
    - 403：`USER_NOT_ACTIVE`、`USER_FROZEN`、`REVIEW_PURCHASE_REQUIRED`。
    - 409：`ORDER_STATUS_CONFLICT`、`REFUND_WAITING_WAREHOUSE_ACCEPT`。
  - 其他未特殊列出的普通业务错误码继续按现有语义返回 400，避免扩大变更。
- 修复方案：
  1. 在 `code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java` 内为 `handleBusiness` 增加私有方法，例如 `private HttpStatus statusForBusinessCode(String code)`。
  2. 映射逻辑建议用 `switch` 或不可变 `Map<String, HttpStatus>`：
     - `USER_NOT_ACTIVE`、`USER_FROZEN`、`REVIEW_PURCHASE_REQUIRED` -> `HttpStatus.FORBIDDEN`。
     - `ORDER_STATUS_CONFLICT`、`REFUND_WAITING_WAREHOUSE_ACCEPT` -> `HttpStatus.CONFLICT`。
     - default -> `HttpStatus.BAD_REQUEST`。
  3. 将 `handleBusiness` 最后一行由固定 `ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)` 改为 `ResponseEntity.status(statusForBusinessCode(ex.getCode())).body(error)`。
  4. 不修改 `BusinessException` 构造函数、不修改各业务模块已抛出的冻结错误码；当前订单与评价模块已经抛出正确 `code`，根因在公共映射。
  5. 更新 `code/ecommerce-common/src/test/java/com/ecommerce/common/exception/GlobalExceptionHandlerTest.java:38`-`code/ecommerce-common/src/test/java/com/ecommerce/common/exception/GlobalExceptionHandlerTest.java:50` 中错误码状态断言：上述 403/409 错误码改为对应冻结状态，保留普通业务错误码仍为 400 的断言。
- 影响范围：
  - 统一影响所有模块通过 `BusinessException` 抛出的同名错误码，尤其订单创建前置校验、订单取消/取消审核/状态机、评价发布前置校验。
  - 不影响 `ResourceNotFoundException`、`AuthorizationException`、`ConflictException`、`ValidationException`、`OrderValidationException` 等已有专用 handler 的结构；但相同 code 若走 `BusinessException` 可获得正确冻结状态。
- 注意事项/风险点：
  - 只映射检查报告中已报告的冻结状态不一致错误码；不要顺手新增未报告错误码策略。
  - 不要将 `RESOURCE_NOT_FOUND` 的 `BusinessException` 一并改成 404，因本次检查报告未将其列为附录A不一致项；若后续另有报告再处理。
  - 保持 `ApiError` 字段名和类型不变，不要修改 `design-docs` 或 README。
- 建议验证方式：
  - `GlobalExceptionHandlerTest` 直接构造 `BusinessException("USER_NOT_ACTIVE", ...)`、`USER_FROZEN`、`REVIEW_PURCHASE_REQUIRED` 断言 HTTP 403。
  - 直接构造 `BusinessException("ORDER_STATUS_CONFLICT", ...)`、`REFUND_WAITING_WAREHOUSE_ACCEPT` 断言 HTTP 409。
  - 订单取消非法状态、评价未购买发布等业务测试断言状态码随公共映射变为 409/403，body `code` 保持原值。

### R3. 订单金额非法统一暴露 ORDER_INVALID_AMOUNT

- 所属模块：`ecommerce-order`，必要时仅调整 `ecommerce-common` 中公共金额校验的调用方式或异常转换，不改变冻结契约。
- 覆盖的问题：
  - `ecommerce-order`：订单金额非法错误码要求为 `ORDER_INVALID_AMOUNT`，但当前金额校验会暴露非冻结契约错误码。
- 检查报告定位：
  - `docs/appendix_A-check/ecommerce-order-check.md:34`-`docs/appendix_A-check/ecommerce-order-check.md:38`
- 设计依据定位：
  - `README.md:222`：`ORDER_INVALID_AMOUNT` 对应 HTTP 400，说明为订单金额非法。
  - `design-docs/附录A-API接口参考.md:202`-`design-docs/附录A-API接口参考.md:229`：创建订单 Request/Response 金额字段契约；不得修改字段名、类型或成功状态码。
- 当前实现定位：
  - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:198`-`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:199`：商品金额汇总后调用 `orderValidator.validateAmount(itemTotal)`。
  - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:240`-`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:245`：最终应付金额计算后调用 `orderValidator.validateAmount(payableAmount)`。
  - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderValidator.java:24`-`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderValidator.java:26`：订单金额校验直接委托 `MoneyValidationUtil.validatePayableAmount(amount)`。
  - `code/ecommerce-common/src/main/java/com/ecommerce/common/money/MoneyValidationUtil.java:12`-`code/ecommerce-common/src/main/java/com/ecommerce/common/money/MoneyValidationUtil.java:15`：公共金额工具定义内部错误码 `DISCOUNT_AMOUNT_INVALID`、`PAYABLE_AMOUNT_TOO_LOW`。
  - `code/ecommerce-common/src/main/java/com/ecommerce/common/money/MoneyValidationUtil.java:33`-`code/ecommerce-common/src/main/java/com/ecommerce/common/money/MoneyValidationUtil.java:36`：金额为空或低于最小值时抛 `OrderValidationException(CODE_PAYABLE_AMOUNT_TOO_LOW, ...)`。
  - `code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:60`-`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:65`：`OrderValidationException` 返回 400，但 body code 使用异常自身 code。
- 修复目标：
  - 订单创建流程中 itemTotal 或 payableAmount 非法时，对外错误响应 `code` 必须为 `ORDER_INVALID_AMOUNT`，HTTP 仍为 400。
  - 不改变订单创建接口 URL、Method、Request 字段、Response 字段或成功 201 状态。
  - 尽量不影响非订单模块对 `MoneyValidationUtil` 的既有内部错误码使用。
- 修复方案：
  1. 优先在 `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderValidator.java` 局部转换错误码，避免扩大公共工具影响：
     - 保持 `validateAmount(BigDecimal amount)` 方法签名不变。
     - 调用 `MoneyValidationUtil.validatePayableAmount(amount)` 时捕获 `OrderValidationException`（或在调用前直接判断 `amount == null || amount.compareTo(MoneyValidationUtil.MIN_PAYABLE_AMOUNT) < 0`）。
     - 对订单上下文重新抛出 `new OrderValidationException("ORDER_INVALID_AMOUNT", ex.getMessage())`，或抛出可由现有 handler 返回 400 的等价异常，确保 body code 为 `ORDER_INVALID_AMOUNT`。
     - 如需保留诊断信息，可用 `addDetail` 记录原内部 code，但 `code` 字段必须为 `ORDER_INVALID_AMOUNT`；若 `OrderValidationException` 不支持 details，则不强行扩展。
  2. 不建议直接修改 `MoneyValidationUtil.CODE_PAYABLE_AMOUNT_TOO_LOW` 常量为 `ORDER_INVALID_AMOUNT`，以免影响公共工具在非订单上下文中的语义；本次报告只要求订单金额非法对外契约。
  3. 检查 `OrderService` 中两处 `orderValidator.validateAmount(...)` 不需要改调用点；通过 `OrderValidator` 统一封装即可覆盖 itemTotal 与 payableAmount 两条路径。
  4. 更新或补充 `ecommerce-order` 测试：构造金额为空、0、负数或折扣/积分抵扣后 `payableAmount < 0.01` 的订单创建场景，断言 HTTP 400 且 body `code=ORDER_INVALID_AMOUNT`，不再出现 `PAYABLE_AMOUNT_TOO_LOW` 或 `DISCOUNT_AMOUNT_INVALID`。
- 影响范围：
  - 仅影响订单模块通过 `OrderValidator.validateAmount` 进入的金额非法响应；公共 `MoneyValidationUtil` 可保持原行为。
  - 与 R2 不冲突：`OrderValidationException` 仍由专用 handler 返回 400，符合 README 对 `ORDER_INVALID_AMOUNT` 的 HTTP 状态要求。
- 注意事项/风险点：
  - 不要修改 `design-docs/附录A-API接口参考.md` 中订单金额字段。
  - 不要让 `ORDER_INVALID_AMOUNT` 变为 409/403；README 固定为 400。
  - 若订单折扣金额校验在其他路径直接调用 `MoneyValidationUtil.validateDiscountAmount` 并暴露 `DISCOUNT_AMOUNT_INVALID`，本报告仅定位订单创建金额校验链路；除非测试证明该链路同样属于本报告问题，否则不要扩大修改。
- 建议验证方式：
  - `OrderValidatorTest`：`validateAmount(null)`、`validateAmount(BigDecimal.ZERO)` 抛出 code 为 `ORDER_INVALID_AMOUNT` 的异常。
  - `OrderService` 创建订单测试：非法最终应付金额返回 HTTP 400，响应体字段为 `code/message/traceId/details`，`code=ORDER_INVALID_AMOUNT`。
  - 回归正常订单创建仍返回 201，响应字段保持 `orderId/orderNo/status/itemTotal/shippingFee/packagingFee/discountAmount/pointsDeductionAmount/payableAmount`。