# ecommerce-loyalty - 附录A 一致性检查

## 检查范围
- 模块：ecommerce-loyalty
- 附录：附录A
- 输入资料：
  - `D:/Desktop/work/HW-ICT-CMP-04/README.md`：比赛边界、冻结 REST API 契约、错误响应、检查口径相关内容，重点纳入 `README.md:35-39`、`README.md:73-75`、`README.md:156-176`、`README.md:200-229`、`README.md:231-237`、`README.md:279-281`。
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录A-API接口参考.md` 全文，重点纳入通用约定 `design-docs/附录A-API接口参考.md:3-24` 和积分接口 `design-docs/附录A-API接口参考.md:280-288`。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-loyalty/pom.xml`。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-loyalty/src/main/java` 下所有源文件。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-loyalty/src/test/java` 下所有测试源文件。
  - 当前模块配置文件：已检查 `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-loyalty` 文件清单，未发现 `src/main/resources` 或 `src/test/resources` 下的模块配置文件。
  - 与错误响应和认证要求直接相关的跨模块实现：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/dto/ApiError.java`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-app/src/main/java/com/ecommerce/app/SecurityConfig.java`。

## 检查结论
- 未发现不一致。
- 共发现 0 处不一致。

## 不一致明细
未发现与当前附录相关的实现不一致项。

核对摘要：
- API 前缀和路径：设计要求所有业务接口使用 `/api/v1/` 前缀（`design-docs/附录A-API接口参考.md:5`），积分接口路径为 `GET /api/v1/loyalty/points`、`POST /api/v1/loyalty/points/estimate-redeem`、`GET /api/v1/loyalty/points/history`、`GET /api/v1/loyalty/member-level`、`POST /api/v1/admin/loyalty/points/expire`（`design-docs/附录A-API接口参考.md:280-288`；`README.md:172-176`）。代码实现对应为 `@RequestMapping("/api/v1/loyalty")` + `@GetMapping("/points")`、`@PostMapping("/points/estimate-redeem")`、`@GetMapping("/points/history")`、`@GetMapping("/member-level")`（`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/LoyaltyController.java:40-42`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/LoyaltyController.java:61-78`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/LoyaltyController.java:97-126`），以及 `@RequestMapping("/api/v1/admin/loyalty")` + `@PostMapping("/points/expire")`（`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/AdminLoyaltyController.java:18-35`）。未发现路径或 HTTP Method 不一致。
- 成功状态码：README 冻结契约要求上述积分接口成功状态均为 200（`README.md:172-176`），代码使用 `ResponseEntity.ok(...)` 返回 200（`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/LoyaltyController.java:72`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/LoyaltyController.java:92`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/LoyaltyController.java:120`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/LoyaltyController.java:138`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/AdminLoyaltyController.java:38-41`）。未发现成功状态码不一致。
- 认证要求：附录 A 要求认证 Header 为 `Authorization: Bearer <jwt>` 且管理接口需要 `ADMIN` 角色（`design-docs/附录A-API接口参考.md:7-13`），积分接口表要求用户接口为 USER、过期处理为 ADMIN（`design-docs/附录A-API接口参考.md:282-288`）。代码中应用安全配置将 `/api/v1/admin/**` 限制为 `ADMIN`、其余 `/api/v1/**` 限制为 `USER`（`code/ecommerce-app/src/main/java/com/ecommerce/app/SecurityConfig.java:57-69`），管理 Controller 亦声明 `@PreAuthorize("hasRole('ADMIN')")`（`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/AdminLoyaltyController.java:18-21`）。未发现认证要求不一致。
- 错误响应结构：附录 A 要求错误响应包含 `code`、`message`、`traceId`、`details`（`design-docs/附录A-API接口参考.md:15-24`），README 冻结错误响应结构（`README.md:73-75`）并列出通用/业务错误码（`README.md:200-229`）。跨模块错误 DTO 和全局异常处理器按 `ApiError(code, message, traceId, details)` 返回（`code/ecommerce-common/src/main/java/com/ecommerce/common/dto/ApiError.java:9-24`、`code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:25-101`）。未发现当前模块导致的错误响应结构不一致。
- 响应字段：附录 A 第 10 节未给出积分接口详细 Request/Response JSON schema，仅冻结 URL、Method、认证和说明（`design-docs/附录A-API接口参考.md:280-288`）；README 公开用例口径仅明确积分查询返回 `availablePoints`、流水支持分页（`README.md:260-261`）。代码积分查询响应包含 `availablePoints`（`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/dto/PointsResponse.java:8-13`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/LoyaltyController.java:65-72`），流水接口接收 `page`/`size` 并返回分页对象（`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/LoyaltyController.java:97-120`、`code/ecommerce-common/src/main/java/com/ecommerce/common/dto/PageResponse.java:11-25`）。未发现与已明确字段要求相冲突的不一致。
