# ecommerce-promotion 模块设计一致性检查

## 检查结论

本次仅依据 `design-docs/02-系统架构.md`、`README.md` 第 6 节 API 基线与第 7 节错误码、`code/pom.xml`、`code/ecommerce-promotion/pom.xml`、`code/ecommerce-promotion/src/main/java/` 下当前模块源码以及 `code/ecommerce-promotion/src/main/resources/` 配置进行核对。8 个指定维度均已覆盖。`code/ecommerce-promotion/src/main/resources/` 下未找到配置文件。

### 一致

1. **架构风格的模块化单体基本包边界**
   - 设计要求：`design-docs/02-系统架构.md` §1 要求模块化单体，各模块拥有自己的包边界、领域服务、Repository 和对外契约；§3 要求模块内数据访问限定在本模块 Repository。
   - 已确认：促销模块源码位于 `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/`，包含 `controller`、`service`、`repository`、`entity`、`dto`、`query` 等包；Repository 均位于 `com.ecommerce.promotion.repository` 并操作促销模块实体，如 `CouponTemplateRepository`、`UserCouponRepository`、`FullReductionRepository`、`SeckillRepository`（`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/repository/CouponTemplateRepository.java:13-18`、`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/repository/UserCouponRepository.java:15-35`）。未发现当前模块直接定义或注入其它模块 Repository。

2. **父工程模块关系包含 promotion 模块**
   - 设计要求：`design-docs/02-系统架构.md` §1 要求所有模块部署在同一个 Spring Boot 应用中；§2 模块依赖图包含 `promotion`。
   - 已确认：父工程 `code/pom.xml:13-25` 的 `<modules>` 中包含 `ecommerce-promotion`；促销模块 `code/ecommerce-promotion/pom.xml:10-30` 作为独立 Maven 子模块存在。

3. **领域事件职责与设计表一致：设计未发现 promotion 相关核心事件要求**
   - 设计要求：`design-docs/02-系统架构.md` §5 核心事件表列出 `UserRegisteredEvent`、`OrderCreatedEvent`、`OrderPaidEvent`、`PaymentSucceededEvent`、`ShipmentDeliveredEvent`、`ReviewApprovedEvent`、`RefundCompletedEvent`，未发现 ecommerce-promotion 作为发布方或监听方的要求。
   - 已确认：促销模块源码中未找到 `ApplicationEventPublisher`、`@EventListener`、`@TransactionalEventListener` 或 listener/event 包；与 §5 未列出促销模块事件职责相符。

4. **促销本地写操作存在事务边界**
   - 设计要求：`design-docs/02-系统架构.md` §6 要求关键业务写操作在明确事务边界内完成，其中与 promotion 直接相关的是订单创建事务包含优惠使用记录。
   - 已确认：促销模块内“领券”和管理创建操作本地写入使用事务：`CouponService.claim(...)` 使用 `@Transactional` 并在同一方法内更新模板发放数、保存用户券（`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponService.java:37-69`）；优惠券模板、满减、秒杀创建也分别使用 `@Transactional`（`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponTemplateService.java:34-58`、`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/FullReductionService.java:36-51`、`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/SeckillService.java:28-36`）。

5. **缓存设计无 promotion 专属要求**
   - 设计要求：`design-docs/02-系统架构.md` §7 仅定义购物车、商品详情、库存摘要、用户权限、运费模板缓存；设计文档未发现本模块相关缓存 Key、TTL、所属模块要求。
   - 已确认：促销模块源码中未找到 `@Cacheable`、`@CacheEvict`、Redis、显式缓存 Key 或 cache/config/cache 包；在 §7 未规定 promotion 缓存的前提下，未发现需核对的本模块缓存实现。

6. **REST API 路径、HTTP Method、成功状态码与 README §6.7 的促销接口一致**
   - 设计要求：`README.md` §6.7 定义促销接口：
     - `POST /api/v1/admin/promotions/coupons`，ADMIN，201
     - `POST /api/v1/promotions/coupons/claim`，USER，201
     - `GET /api/v1/promotions/coupons/my`，USER，200
     - `POST /api/v1/promotions/calculate`，USER，200
     - `POST /api/v1/admin/promotions/full-reductions`，ADMIN，201
     - `POST /api/v1/admin/promotions/seckill`，ADMIN，201
   - 已确认：上述路径和 HTTP Method 分别存在于 `AdminPromotionController` 与 `PromotionController`；201 接口返回 `ResponseEntity.status(HttpStatus.CREATED)`，200 接口返回 `ResponseEntity.ok(...)`（`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/AdminPromotionController.java:23-68`、`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/PromotionController.java:33-108`）。

7. **README §7 中促销相关 `COUPON_EXPIRED` 错误码存在实现**
   - 设计要求：`README.md` §7.2 定义 `COUPON_EXPIRED`，HTTP 400，优惠券已过期。
   - 已确认：优惠券模板不可用、未开始、已过期以及用户券不可用时均抛出 `BusinessException("COUPON_EXPIRED", ...)`（`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponService.java:120-129`、`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponValidator.java:40-52`）。

### 不一致

1. **关键本地接口 `PromotionCalculationService` 的接口形态与设计不一致**
   - 代码定位：
     - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationService.java:23-67`
     - `code/ecommerce-common/src/main/java/com/ecommerce/common/integration/PromotionDiscountCalculator.java:10-12`
     - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/query/PromotionQueryService.java:19-55`
   - 设计要求定位：`design-docs/02-系统架构.md` §4 关键本地接口表要求 `PromotionCalculationService` 由 promotion 提供，使用方为 order、cart，用途为“计算优惠”；§3 要求跨模块查询必须通过 QueryService 接口，跨模块传输使用 DTO。
   - 具体描述：当前 `PromotionCalculationService` 是 `@Service` 具体类，不是本地接口；其跨模块抽象实际是 common 包中的 `PromotionDiscountCalculator#calculateDiscount(Long userId, List<Item> items, List<Long> couponIds)`，名称、包位置与 §4 指定的 `PromotionCalculationService` 不一致。促销模块另有 `PromotionQueryService` 接口，但该接口未按 §4 命名，且 `calculateDiscounts(List<PromotionCalculateResponse> items, Long userId, List<Long> couponIds)` 将 `items` 定义为 `List<PromotionCalculateResponse>`，不是请求项/订单项 DTO。
   - 原因解析：设计以 `PromotionCalculationService` 作为 promotion 对 order、cart 的关键本地接口名称；代码把该名称用于具体实现类，并通过另一个 common 接口暴露精简折扣金额方法，导致接口存在性、方法签名和包位置均无法与 §4 的接口契约直接对应。

2. **模块依赖方向中 cart 对 promotion 的使用关系未按 §4 直接体现**
   - 代码定位：
     - `code/ecommerce-cart/pom.xml:12-30`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:13`
     - `code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:44-49`
     - `code/ecommerce-order/pom.xml:32-36`
   - 设计要求定位：`design-docs/02-系统架构.md` §4 要求 `PromotionCalculationService` 的使用方为 order、cart；§2 模块依赖图中 promotion 与 order 存在依赖关系，且 §1 协作优先级要求同步本地接口用于查询/校验/强一致链路。
   - 具体描述：order 模块 POM 直接依赖 `ecommerce-promotion`（`code/ecommerce-order/pom.xml:32-36`），但 cart 模块 POM 未依赖 `ecommerce-promotion`，而是依赖 common 并注入 `com.ecommerce.common.integration.PromotionDiscountCalculator`。
   - 原因解析：使用 common port 可降低编译耦合，但设计文档明确列出的关键本地接口提供方/使用方关系是 promotion 提供 `PromotionCalculationService` 给 order、cart。当前 cart 未通过 promotion 模块中该接口建立本地接口依赖，和 §4 的接口使用关系不一致。

3. **订单创建事务中的“优惠使用记录”在 promotion 模块未找到对应实现**
   - 代码定位：
     - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/UserCoupon.java:32-39`
     - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationService.java:67-90`
     - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponService.java:37-69`
   - 设计要求定位：`design-docs/02-系统架构.md` §6.1 要求“创建订单事务只包含订单主数据、订单明细、库存预占记录和优惠使用记录”。
   - 具体描述：`UserCoupon` 定义了 `usedOrderId`、`usedAt` 与 `CouponStatus.USED` 所需字段/枚举，但促销模块服务中未找到将用户券标记为已使用、记录订单 ID 或使用时间的事务方法；`PromotionCalculationService.calculate(...)` 只计算折扣并返回结果，`CouponService.claim(...)` 只处理领券。
   - 原因解析：设计要求订单创建事务包含优惠使用记录，意味着 promotion 至少应提供可在订单创建强一致链路中调用的优惠使用记录能力；当前模块仅有计算与领券，缺少可核对的优惠使用记录写入边界。

4. **用户侧促销接口未在模块内体现 USER 角色认证约束**
   - 代码定位：
     - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/PromotionController.java:56-108`
     - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/PromotionController.java:114-133`
   - 设计要求定位：`design-docs/02-系统架构.md` §8.2 要求用户侧接口需要 `Authorization: Bearer <token>`；`README.md` §6.7 将 `/api/v1/promotions/coupons/claim`、`/api/v1/promotions/coupons/my`、`/api/v1/promotions/calculate` 标记为 USER。
   - 具体描述：`PromotionController` 通过 `SecurityContextHolder` 提取已认证 principal，但类/方法上未找到 `@PreAuthorize("hasRole('USER')")`、`@Secured`、`@RolesAllowed` 或其它 USER 角色约束注解；促销模块内也未找到 security/config 配置。
   - 原因解析：如果项目在其它模块用全局 SecurityFilterChain 按路径统一保护 USER 接口，则可能在模块外实现；但按本次限定的 ecommerce-promotion 模块源码，未找到本模块对 USER 角色要求的实现证据，仅能证明接口依赖已认证 principal。

5. **管理类促销接口未在模块内体现 ADMIN 角色认证约束**
   - 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/AdminPromotionController.java:23-68`
   - 设计要求定位：`design-docs/02-系统架构.md` §8.3 要求管理类接口需要 `ADMIN` 角色；`README.md` §6.7 将 `/api/v1/admin/promotions/coupons`、`/api/v1/admin/promotions/full-reductions`、`/api/v1/admin/promotions/seckill` 标记为 ADMIN。
   - 具体描述：`AdminPromotionController` 仅声明 `@RestController` 和 `@RequestMapping("/api/v1/admin/promotions")`，方法上无 `@PreAuthorize`、`@Secured`、`@RolesAllowed` 或其它 ADMIN 角色校验注解；类注释写明“Requires ADMIN role (enforced at gateway or security layer)”，但促销模块内未找到对应实现。
   - 原因解析：设计要求管理接口需要 ADMIN 角色。模块内只有注释，没有可执行的角色认证约束；若依赖全局安全配置，则该实现不在本模块可核对范围内。

6. **README §7 错误码约束中未定义的 promotion 业务错误码被使用**
   - 代码定位：
     - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponService.java:47-54`
     - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponValidator.java:32-34`
     - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/SeckillService.java:56-70`
   - 设计要求定位：`README.md` §7.1-§7.2 定义通用错误码与业务错误码，其中与促销直接相关的业务错误码仅列出 `COUPON_EXPIRED`。
   - 具体描述：代码除 `COUPON_EXPIRED` 外还抛出 `COUPON_LIMIT_EXCEEDED`、`COUPON_EXHAUSTED`、`COUPON_INVALID`、`SECKILL_NOT_STARTED`、`SECKILL_ENDED`、`SECKILL_SOLD_OUT`，这些错误码未出现在 README §7 的错误码表中。
   - 原因解析：README §7 是顶层错误码约束；促销模块使用未列入冻结错误码表的业务错误码，会使错误响应 code 超出设计约定范围。

## 检查遗漏声明

1. **架构风格（§1、§3）**：已检查 `controller`、`service`、`repository`、`entity`、`dto`、`query` 包和跨模块 Repository 访问迹象。未找到 `config`、`cache`、`event`、`listener` 目录。未发现当前模块直接访问其它模块 Repository；发现关键本地接口形态问题已列为不一致。

2. **模块依赖方向（§2）**：已检查 `code/pom.xml`、`code/ecommerce-promotion/pom.xml`，并核对 order/cart 对促销能力的使用关系。父工程包含 `ecommerce-promotion`；促销模块 POM 仅依赖 `ecommerce-common`。cart 对 promotion 关键本地接口的使用关系未按 §4 直接体现，已列为不一致。§2 图中 promotion 与 order 的箭头方向表达较简略，具体以 §4 的提供方/使用方表进行核对。

3. **关键本地接口（§4）**：已检查 `service` 与 `query` 包。找到 `PromotionCalculationService` 具体类、`PromotionQueryService` 接口以及 common 中 `PromotionDiscountCalculator` 接口；未找到与 §4 完全一致的 promotion 本地接口形态和签名，已列为不一致。

4. **领域事件（§5）**：设计文档未发现 ecommerce-promotion 作为核心事件发布方或监听方的相关要求；代码中未找到 event/listener 目录，也未找到 `ApplicationEventPublisher`、`@EventListener`、`@TransactionalEventListener`。失败策略未找到本模块实现要求。

5. **事务边界（§6）**：已检查促销模块内 `@Transactional`。领券、模板创建、满减创建、秒杀创建/购买记录具备本地事务；订单创建事务中的“优惠使用记录”未在 promotion 模块找到对应写入能力，已列为不一致。订单主数据、订单明细、库存预占是否同事务属于 order/inventory 模块实现，不在本模块源码中可直接证明。

6. **缓存设计（§7）**：设计文档未发现 ecommerce-promotion 相关缓存 Key、TTL、所属模块要求；代码中未找到 cache/config/cache 目录、缓存注解、Redis 使用或促销缓存 Key。

7. **安全架构（§8）**：JWT 签发属于登录/用户模块要求；`X-Payment-Signature` 属于支付回调要求，设计文档未发现 ecommerce-promotion 相关签名头要求。促销模块涉及 USER 与 ADMIN 接口；本模块内未找到角色校验注解或安全配置，已列为不一致/缺少本模块证据。

8. **REST API 与错误码（README §6、§7）**：已检查 README §6.7 中 6 个促销接口的路径、Method、认证标记与成功状态码；路径、Method、成功状态码均找到。README §6.7 未提供促销接口 Request/Response 字段明细表，因此无法按 README 逐字段验证字段名和类型，只能确认代码中实际请求/响应类型：`CouponCreateRequest`、`CouponClaimRequest`、`CouponResponse`、`PromotionCalculateRequest`、`PromotionCalculateResponse`、`FullReductionCreateRequest`、`SeckillActivity`、`CouponTemplate`、`UserCoupon`、`FullReductionActivity`。README §7 中促销相关 `COUPON_EXPIRED` 已实现，但也发现未列入 README §7 的促销/秒杀错误码，已列为不一致。

9. **资源配置文件**：`code/ecommerce-promotion/src/main/resources/` 下未找到配置文件，因此未发现本模块本地 `application.yml`、缓存 TTL、安全或其它资源配置。
