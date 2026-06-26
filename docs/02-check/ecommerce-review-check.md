# ecommerce-review 模块设计一致性检查

## 检查结论

已按指定输入核对 `ecommerce-review` 模块与设计文档/API 基线的一致性，覆盖 8 个检查维度：架构风格、模块依赖方向、关键本地接口、领域事件、事务边界、缓存设计、安全架构、REST API 与错误码。

结论：发现 5 项与设计要求不一致；其余已核对的一致项见下。

### 一致

1. 架构风格与模块边界（02-系统架构.md §1、§3）
   - 当前模块位于独立包边界 `com.ecommerce.review`，包含 controller/service/repository/entity/dto/query/event 等分层目录。
   - 代码未发现跨模块直接注入其它模块 Repository 或直接查询其它模块表；评价购买校验通过 `OrderQueryService` 查询接口完成：`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:7`、`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:43`、`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:112`。
   - 跨模块传输使用 DTO，例如 `VerifyPurchaseResponse`、`ReviewResponse`，未向外暴露本模块 JPA Entity 作为跨模块契约。

2. 关键本地接口中的订单查询接口（02-系统架构.md §4）
   - 设计要求：`OrderQueryService` 由 order 提供、review 使用，用于查询订单和验证购买记录。
   - 实现使用 `com.ecommerce.order.query.OrderQueryService`，调用签名为 `verifyPurchase(Long userId, Long productId)`：
     - `code/ecommerce-order/src/main/java/com/ecommerce/order/query/OrderQueryService.java:35-42`
     - `code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:111-118`

3. REST API 路径、HTTP Method、认证角色、成功状态（README.md §6.7）
   - `POST /api/v1/reviews`：`@PostMapping` + `@PreAuthorize("hasRole('USER')")` + 201，见 `code/ecommerce-review/src/main/java/com/ecommerce/review/controller/ReviewController.java:46-54`。
   - `POST /api/v1/reviews/{reviewId}/append`：`@PostMapping("/{reviewId}/append")` + USER + 201，见 `code/ecommerce-review/src/main/java/com/ecommerce/review/controller/ReviewController.java:65-73`。
   - `GET /api/v1/reviews/product/{productId}`：匿名访问 + 200，见 `code/ecommerce-review/src/main/java/com/ecommerce/review/controller/ReviewController.java:85-92`。
   - `GET /api/v1/reviews/my`：USER + 200，见 `code/ecommerce-review/src/main/java/com/ecommerce/review/controller/ReviewController.java:103-111`。
   - `POST /api/v1/admin/reviews/{reviewId}/approve`：ADMIN + 200，见 `code/ecommerce-review/src/main/java/com/ecommerce/review/controller/AdminReviewController.java:23-24`、`code/ecommerce-review/src/main/java/com/ecommerce/review/controller/AdminReviewController.java:43-51`。
   - `POST /api/v1/admin/reviews/{reviewId}/reject`：ADMIN + 200，见 `code/ecommerce-review/src/main/java/com/ecommerce/review/controller/AdminReviewController.java:23-24`、`code/ecommerce-review/src/main/java/com/ecommerce/review/controller/AdminReviewController.java:62-70`。

4. README.md §7 与本模块直接相关错误码
   - README.md §7.2 对评价模块明确列出 `REVIEW_PURCHASE_REQUIRED`（HTTP 403，未购买商品不可评价）。代码在购买校验失败时使用该错误码：`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:111-118`。

5. 安全架构（02-系统架构.md §8、README.md §6.7）
   - 用户侧评价接口通过 `Authorization: Bearer <token>` 对应的角色注解限制为 USER：`code/ecommerce-review/src/main/java/com/ecommerce/review/controller/ReviewController.java:47`、`code/ecommerce-review/src/main/java/com/ecommerce/review/controller/ReviewController.java:66`、`code/ecommerce-review/src/main/java/com/ecommerce/review/controller/ReviewController.java:104`。
   - 管理类评价审核接口限制为 ADMIN：`code/ecommerce-review/src/main/java/com/ecommerce/review/controller/AdminReviewController.java:23-24`。
   - 设计中的支付回调签名头 `X-Payment-Signature` 与本模块无直接接口要求。

6. 事务边界中的本模块实现现状
   - 设计文档 §6 未发现针对 review 模块的专门事务边界要求。
   - 代码中评价创建、追评、审核通过、审核拒绝均以本模块本地事务包裹：`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:64`、`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:129`、`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewModerationService.java:42`、`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewModerationService.java:74`。

7. 缓存设计
   - 02-系统架构.md §7 的缓存清单未发现 review 模块相关缓存要求。
   - 当前模块源代码中未发现 `@Cacheable`、Cache 配置或缓存 Key 使用；与“设计未要求 review 缓存”一致。

### 不一致

1. 模块依赖方向不一致：review 直接依赖 loyalty
   - 代码定位：`code/ecommerce-review/pom.xml:22-26`
   - 设计要求定位：02-系统架构.md §2 模块依赖图、§3 模块边界规则、§5 事件驱动设计。
   - 具体描述：`ecommerce-review` 的 POM 声明了对 `ecommerce-loyalty` 的 Maven 依赖；设计中 `ReviewApprovedEvent` 的发布方为 review、监听方为 loyalty，review 到 loyalty 的协作应通过本地领域事件完成，而不是让 review 模块依赖 loyalty 模块。
   - 原因解析：该依赖把事件监听方所在模块引入发布方模块，扩大了 review 模块对后置积分模块的编译期依赖，不符合设计中“后置动作优先使用 ApplicationEvent”和模块依赖方向的弱耦合意图。

2. 关键本地接口缺失：review 未使用 `UserQueryService`
   - 代码定位：`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:43-54`
   - 设计要求定位：02-系统架构.md §4 关键本地接口：`UserQueryService | user | order、review | 查询用户状态、冻结状态`。
   - 具体描述：`ReviewService` 仅注入 `OrderQueryService`，未注入或调用 `UserQueryService`；在 `code/ecommerce-review/src/main/java/` 下也未找到 `UserQueryService` 或 `com.ecommerce.user` 相关引用。
   - 原因解析：设计明确要求 review 作为 `UserQueryService` 使用方查询用户状态、冻结状态；当前实现只依赖 Spring Security 角色获取 userId，未按设计通过 user 模块本地接口校验用户状态/冻结状态。

3. 领域事件发布时机不一致：创建评价时提前发布 `ReviewApprovedEvent`
   - 代码定位：`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:98-107`
   - 设计要求定位：02-系统架构.md §5 核心事件：`ReviewApprovedEvent | review | loyalty | 发放评价积分`。
   - 具体描述：`createReview` 将新评价状态设置为 `PENDING_REVIEW` 后立即发布 `ReviewApprovedEvent`；事件名称和设计语义均表示“评价审核通过后”才应发布。
   - 原因解析：PENDING_REVIEW 不是 APPROVED。提前发布会使监听方按“已审核通过”处理未审核评价，破坏 `ReviewApprovedEvent` 的领域语义，并可能提前触发评价积分发放。

4. 领域事件监听方放置不一致：review 模块内部存在 `ReviewApprovedEventListener`
   - 代码定位：`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewApprovedEventListener.java:12-24`
   - 设计要求定位：02-系统架构.md §5 核心事件：`ReviewApprovedEvent | review | loyalty | 发放评价积分`。
   - 具体描述：review 模块内定义了 `@Component("reviewReviewApprovedEventListener")`，并通过 `@EventListener` 监听本模块的 `ReviewApprovedEvent`；设计要求监听方为 loyalty，而不是 review 自身。
   - 原因解析：评价积分发放属于 loyalty 模块职责。review 模块内部监听并模拟积分发放，会让发布方承担监听方职责，违反事件驱动设计的模块职责划分。

5. 领域事件契约不一致：ReviewApprovedEvent 类型在 review 与 loyalty 中重复定义，导致发布/监听类型不一致
   - 代码定位：
     - `code/ecommerce-review/src/main/java/com/ecommerce/review/event/ReviewApprovedEvent.java:1-14`
     - `code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewModerationService.java:62-63`
     - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEvent.java:1-16`
     - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEventListener.java:26-35`
   - 设计要求定位：02-系统架构.md §5 核心事件：`ReviewApprovedEvent | review | loyalty | 发放评价积分`。
   - 具体描述：review 发布的是 `com.ecommerce.review.event.ReviewApprovedEvent`，loyalty 监听的是 `com.ecommerce.loyalty.event.ReviewApprovedEvent`；二者类名相同但包名不同、不是同一事件类型。
   - 原因解析：Spring `@EventListener` 按事件对象类型匹配。发布方和监听方使用不同包下的事件类时，loyalty 监听器无法接收到 review 实际发布的事件，导致“review 发布、loyalty 监听”的设计链路不成立。

## 检查遗漏声明

1. 源码目录检查
   - 已检查 `code/ecommerce-review/src/main/java/` 下当前模块所有 Java 源文件，包括 controller、service、repository、entity、dto、query、event。
   - 未找到 `config`、`cache`、`listener` 独立目录；事件监听器位于 `service` 目录下。

2. 资源配置目录检查
   - 未找到 `code/ecommerce-review/src/main/resources/` 下配置文件；该目录无匹配资源文件。

3. 架构风格维度
   - 已检查 02-系统架构.md §1、§3 中与 review 相关的模块化单体、包边界、Repository 边界、QueryService、事件、DTO、事务后置监听规则。

4. 模块依赖方向维度
   - 已检查父工程模块列表与 `ecommerce-review` POM。
   - 发现 `ecommerce-review` 对 `ecommerce-loyalty` 的直接依赖不一致；见“不一致”第 1 项。

5. 关键本地接口维度
   - 已检查 `OrderQueryService` 使用，确认存在且签名匹配购买校验用途。
   - 未找到 `UserQueryService` 使用；见“不一致”第 2 项。
   - 02-系统架构.md §4 未发现要求 review 模块必须提供 `ReviewQueryService` 给其它模块使用；代码虽存在 `ReviewQueryService` 接口，但不作为本次不一致项。

6. 领域事件维度
   - 已检查 ReviewApprovedEvent 发布方、监听方和失败处理现状。
   - 发现创建评价提前发布事件、review 内部自监听、review/loyalty 事件类型重复定义三项不一致；见“不一致”第 3、4、5 项。

7. 事务边界维度
   - 02-系统架构.md §6 未发现针对 review 模块的专门事务边界要求。
   - 已列出代码中存在的本地 `@Transactional` 操作；未额外报告设计未指定的事务问题。

8. 缓存设计维度
   - 02-系统架构.md §7 未发现 review 模块相关缓存要求。
   - 代码中未找到 review 缓存配置、缓存 Key 或 TTL 实现；按“设计未发现本模块相关要求”记录。

9. 安全架构维度
   - 已检查 JWT/角色认证相关注解；签名头要求仅涉及支付回调，设计文档未发现 review 模块相关签名头要求。

10. REST API 与错误码维度
   - 已按 README.md §6.7 核对评价相关 API 的 URL、HTTP Method、认证、成功状态。
   - README.md §6 未给出评价 API 的详细 Request/Response 字段表；因此仅能核对代码中 DTO 字段存在性，未对未声明字段作不一致判定。
   - 已按 README.md §7.2 核对与 review 直接相关的业务错误码 `REVIEW_PURCHASE_REQUIRED`。
