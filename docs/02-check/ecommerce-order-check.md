# ecommerce-order 模块设计一致性检查

## 检查结论

本次仅依据 `design-docs/02-系统架构.md` §1-§8、`README.md` §6 API 基线与 §7 错误码检查 `ecommerce-order` 模块；已读取父工程 POM、订单模块 POM，并检查 `code/ecommerce-order/src/main/java/` 下 controller/service/repository/entity/dto/query/config/cache/event/listener 等现有源码。`code/ecommerce-order/src/main/resources/` 目录未找到。

结论：8 个指定维度均已覆盖。主要不一致 6 项，集中在积分接口依赖、支付确认事务/事件发布、批量订单事务边界、优惠使用记录、以及 README §7 冻结错误码约束。

### 一致

1. **架构风格（02-系统架构.md §1、§3）**
   - 模块以独立包 `com.ecommerce.order` 组织，并存在本模块配置扫描：`code/ecommerce-order/src/main/java/com/ecommerce/order/config/OrderModuleConfig.java:12-13`。
   - 订单模块拥有本模块 Repository 与实体，Repository 均指向订单模块实体：`code/ecommerce-order/src/main/java/com/ecommerce/order/repository/OrderRepository.java:21-22`、`code/ecommerce-order/src/main/java/com/ecommerce/order/repository/OrderItemRepository.java:12-13`、`code/ecommerce-order/src/main/java/com/ecommerce/order/repository/OrderEventLogRepository.java:12-13`；实体表为 `orders`、`order_items`、`order_event_logs`：`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/Order.java:20`、`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/OrderItem.java:15`、`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/OrderEventLog.java:18`。
   - 跨模块读取主要通过 QueryService/本地接口完成，而不是直接注入其它模块 Repository：如 `UserQueryService`、`ProductQueryService`、`InventoryReservationService`、`PromotionCalculationService` 分别见 `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:81-83`、`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:92`。

2. **模块依赖方向（02-系统架构.md §2）**
   - 父工程模块包含 `ecommerce-order`，且位于 cart 之后、payment/logistics/loyalty/review 之前：`code/pom.xml:13-25`。
   - 订单模块 POM 依赖 common、user、product、inventory、promotion、loyalty：`code/ecommerce-order/pom.xml:12-41`。其中 common/user/product/inventory/promotion 与 §4 所列订单创建、查询、促销计算接口方向一致；loyalty 依赖存在接口类型不一致，见“不一致”第 1 项。

3. **关键本地接口（02-系统架构.md §4）**
   - 订单模块对外提供 `OrderQueryService`，包位置为 `com.ecommerce.order.query`：`code/ecommerce-order/src/main/java/com/ecommerce/order/query/OrderQueryService.java:1-15`。
   - `OrderQueryService` 已提供查询订单、验证购买、订单金额等方法：`getOrder(Long)`、`getPayableOrder(Long)`、`verifyPurchase(Long, Long)`、`getOrderAmount(Long)` 分别见 `code/ecommerce-order/src/main/java/com/ecommerce/order/query/OrderQueryService.java:23`、`:32`、`:42`、`:51`。
   - 订单模块对外提供 `OrderPaymentStatusUpdater`，包位置为 `com.ecommerce.order.query`，方法为 `markAsPaid(Long, String)` 与 `markPaymentFailed(Long)`：`code/ecommerce-order/src/main/java/com/ecommerce/order/query/OrderPaymentStatusUpdater.java:9-25`。
   - 实现类 `OrderQueryServiceImpl` 实现 `OrderQueryService` 与 `OrderPaymentStatusUpdater`：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderQueryServiceImpl.java:38-40`。

4. **领域事件（02-系统架构.md §5）**
   - 订单模块定义并发布 `OrderCreatedEvent`，事件类位于 `com.ecommerce.order.event`：`code/ecommerce-order/src/main/java/com/ecommerce/order/event/OrderCreatedEvent.java:1-17`；创建订单后发布事件：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:276-277`。
   - 订单模块定义 `OrderPaidEvent`，字段包含 `orderId`、`userId`、`paymentNo`、`paidAmount`：`code/ecommerce-order/src/main/java/com/ecommerce/order/event/OrderPaidEvent.java:11-25`。
   - 订单模块定义 `OrderCancelledEvent`：`code/ecommerce-order/src/main/java/com/ecommerce/order/event/OrderCancelledEvent.java:10-19`。
   - 本模块存在订单事件监听器，并使用 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` 监听创建、支付、取消事件：`code/ecommerce-order/src/main/java/com/ecommerce/order/listener/OrderEventListener.java:46`、`:63`、`:85`。跨模块 logistics、loyalty、notification 监听方属于其它模块，本模块内不要求实现。

5. **事务边界（02-系统架构.md §6）**
   - 创建订单核心方法处于事务服务内：`OrderService` 类级 `@Transactional` 见 `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:72-73`。
   - 创建订单流程在同一方法内保存订单主数据、订单明细、订单事件日志并调用库存预占：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:249-274`。
   - 支付成功处理存在事务边界：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPaymentEventHandler.java:67-68`。但对 §6 的完整一致性存在问题，见“不一致”第 2、3 项。

6. **缓存设计（02-系统架构.md §7）**
   - 设计文档 §7 未发现 ecommerce-order 所属缓存 Key/TTL 要求；表中仅列 cart/product/inventory/user/logistics。
   - 当前订单模块未找到 `@Cacheable`、`@CacheEvict`、`CacheManager`、Redis/Caffeine 或设计表所列 Key 使用；这与“未发现本模块相关缓存要求”一致。
   - “订单提交后，订单模块保存订单明细”已实现：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:251-264`。

7. **安全架构（02-系统架构.md §8）**
   - 用户侧订单接口要求 USER 角色：`code/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java:35-37`。
   - 管理订单接口要求 ADMIN 角色：`code/ecommerce-order/src/main/java/com/ecommerce/order/controller/AdminOrderController.java:29-31`。
   - `/api/v1/orders/verify-purchase` 覆盖 USER/ADMIN：`code/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java:121-123`。
   - JWT 签发与 `Authorization: Bearer <token>` 的通用认证机制不属于订单模块源码职责；本模块通过 Spring Security 角色注解接入。
   - `X-Payment-Signature` 是支付回调接口要求，README §6.6 与架构 §8.4 均指向 payment 模块；设计文档未发现 ecommerce-order 需要实现签名头校验。

8. **REST API 路径、HTTP Method、认证、成功状态（README.md §6.5、§6.8）**
   - `POST /api/v1/orders/create`，USER，成功 201：`code/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java:57-64`，符合 README.md §6.5 行 132。
   - `GET /api/v1/orders/{orderId}`，USER，成功 200：`code/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java:70-74`，符合 README.md §6.5 行 133。
   - `GET /api/v1/orders`，USER，成功 200：`code/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java:80-89`，符合 README.md §6.5 行 134。
   - `POST /api/v1/orders/{orderId}/cancel`，USER，成功 200：`code/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java:95-102`，符合 README.md §6.5 行 135。
   - `POST /api/v1/admin/orders/{orderId}/cancel-review`，ADMIN，成功 200：`code/ecommerce-order/src/main/java/com/ecommerce/order/controller/AdminOrderController.java:52-63`，符合 README.md §6.5 行 136。
   - `POST /api/v1/orders/batch`，USER，成功 200：`code/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java:108-115`，符合 README.md §6.5 行 137。
   - `GET /api/v1/orders/verify-purchase`，USER/ADMIN，成功 200：`code/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java:121-128`，符合 README.md §6.5 行 138。
   - `GET /api/v1/admin/orders/statistics/sales`，ADMIN，成功 200：`code/ecommerce-order/src/main/java/com/ecommerce/order/controller/AdminOrderController.java:69-75`，符合 README.md §6.5 行 139。
   - `POST /api/v1/admin/orders/timeout-cancel`，ADMIN，成功 200：`code/ecommerce-order/src/main/java/com/ecommerce/order/controller/AdminOrderController.java:81-85`，符合 README.md §6.8 行 198。
   - README §6.5 仅冻结订单 API 的路径、Method、认证、成功状态，未列出订单模块 Request/Response 字段明细；当前代码字段已核对但无法与 README 中不存在的字段清单逐字段比对。当前主要 DTO 字段如下：`CreateOrderRequest` 字段 `addressId/items/couponIds/redeemPoints/externalOrderNo` 见 `code/ecommerce-order/src/main/java/com/ecommerce/order/dto/CreateOrderRequest.java:15-32`；`CreateOrderResponse` 字段 `orderId/orderNo/status/itemTotal/shippingFee/packagingFee/discountAmount/pointsDeductionAmount/payableAmount` 见 `code/ecommerce-order/src/main/java/com/ecommerce/order/dto/CreateOrderResponse.java:10-18`；`BatchCreateOrderRequest` 字段见 `code/ecommerce-order/src/main/java/com/ecommerce/order/dto/BatchCreateOrderRequest.java:13-19`；`BatchCreateOrderResponse` 字段见 `code/ecommerce-order/src/main/java/com/ecommerce/order/dto/BatchCreateOrderResponse.java:10-13`、`:55-59`；`VerifyPurchaseRequest/Response` 字段见 `code/ecommerce-order/src/main/java/com/ecommerce/order/dto/VerifyPurchaseRequest.java:10-14`、`code/ecommerce-order/src/main/java/com/ecommerce/order/dto/VerifyPurchaseResponse.java:10-12`；`SalesStatisticsRequest/Response` 字段见 `code/ecommerce-order/src/main/java/com/ecommerce/order/dto/SalesStatisticsRequest.java:12-16`、`code/ecommerce-order/src/main/java/com/ecommerce/order/dto/SalesStatisticsResponse.java:12-17`。

### 不一致

1. **积分本地接口类型与设计不一致**
   - 代码定位：`code/ecommerce-order/pom.xml:37-41`；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:11`、`:84`、`:201`；`code/ecommerce-order/src/main/java/com/ecommerce/order/integration/LoyaltyIntegrationService.java:4`、`:32`。
   - 设计要求定位：`design-docs/02-系统架构.md` §4 行 63：`LoyaltyCommandService | loyalty | order、payment | 积分抵扣、发放`。
   - 具体描述：设计要求订单模块使用 loyalty 提供的 `LoyaltyCommandService` 进行积分抵扣/发放；代码中订单创建使用的是 `LoyaltyQueryService.estimateRedeemPoints(...)`，且未发现订单创建时调用 `LoyaltyCommandService` 完成积分抵扣命令。
   - 原因解析：积分抵扣属于跨模块命令依赖，按 §3 “跨模块命令必须通过领域服务接口或事件”、§4 应落到 `LoyaltyCommandService`；当前代码只做查询估算，未把积分抵扣作为命令接口纳入订单创建链路。

2. **`OrderPaymentStatusUpdater.markAsPaid` 未满足支付确认事务边界，也未发布 `OrderPaidEvent`**
   - 代码定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/query/OrderPaymentStatusUpdater.java:17`；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderQueryServiceImpl.java:124-146`。
   - 设计要求定位：`design-docs/02-系统架构.md` §4 行 60：`OrderPaymentStatusUpdater | order | payment | 更新订单支付状态`；§5 行 74：`OrderPaidEvent | order | logistics、loyalty、notification | 失败记录补偿任务，不回滚支付`；§6 行 83：支付确认事务只包含支付单状态、订单支付状态和库存扣减。
   - 具体描述：`OrderQueryServiceImpl.markAsPaid(Long orderId, String paymentNo)` 只更新订单状态、`paymentNo`、`paidAt`、`paidAmount` 并保存订单；未调用库存扣减接口，未发布 `OrderPaidEvent`。
   - 原因解析：支付模块按 §4 通过 `OrderPaymentStatusUpdater` 通知订单支付状态时，当前实现无法保证支付确认链路中的库存扣减，也不会触发 §5 要求的订单支付后物流/积分/通知事件链路。虽然另有 `OrderPaymentEventHandler.handlePaymentSuccess(...)` 实现了部分逻辑，但它不是 `OrderPaymentStatusUpdater` 接口实现方法，不能替代该本地接口契约。

3. **支付成功处理吞掉库存扣减失败，与“支付确认事务包含库存扣减”不一致**
   - 代码定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPaymentEventHandler.java:67-127`，尤其 `inventoryReservationService.deductAfterPayment(orderId)` 外层 `try/catch` 见 `:114-123`。
   - 设计要求定位：`design-docs/02-系统架构.md` §6 行 83：支付确认事务只包含支付单状态、订单支付状态和库存扣减；§6 行 84：支付后物流、积分、通知通过事件异步处理，不得使支付确认失败。
   - 具体描述：代码在同一事务方法中更新订单为 PAID 后调用库存扣减，但捕获库存扣减异常并继续发布 `OrderPaidEvent`。
   - 原因解析：§6 将库存扣减列为支付确认事务内的关键操作，区别于物流/积分/通知等非关键后置监听器。当前代码把库存扣减失败降级为日志，可能导致订单已支付但库存未扣减，支付确认事务边界不符合设计。

4. **批量订单导入不是按单条事务处理**
   - 代码定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/BatchOrderService.java:19-20`、`:38-70`；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:72-73`。
   - 设计要求定位：`design-docs/02-系统架构.md` §6 行 85：批量订单导入按单条事务处理，一条失败不得回滚整批。
   - 具体描述：`BatchOrderService` 类级 `@Transactional` 包裹整个批量处理循环，循环内调用同一事务传播下的 `orderService.createOrder(...)`；当 `continueOnError=false` 时直接抛出 `BATCH_ORDER_FAILED`，当 `continueOnError=true` 时也未看到每单 `REQUIRES_NEW` 或等价单条事务边界。
   - 原因解析：设计要求每条订单独立事务，以避免一条失败影响整批。当前批量服务是整批一个外层事务，单条订单并未被显式隔离成独立事务。

5. **创建订单事务未体现“优惠使用记录”写入**
   - 代码定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:190-191`、`:242-247`、`:415-418`。
   - 设计要求定位：`design-docs/02-系统架构.md` §6 行 82：创建订单事务只包含订单主数据、订单明细、库存预占记录和优惠使用记录。
   - 具体描述：代码通过 `PromotionCalculationService.calculate(...)` 计算优惠，并把优惠券 ID 字符串快照到订单 `couponIds`；未发现创建订单事务中调用 promotion 的优惠使用/锁定/核销记录命令，也未在订单模块内保存“优惠使用记录”。
   - 原因解析：计算优惠和保存优惠券 ID 快照不能等同于“优惠使用记录”。该记录属于创建订单强一致事务的一部分，当前实现缺少对应写入或跨模块命令调用。

6. **订单模块抛出多个 README §7 未冻结的错误码**
   - 代码定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/BatchOrderService.java:66-68`（`BATCH_ORDER_FAILED`）；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPaymentEventHandler.java:85-87`（`ORDER_NOT_PAYABLE`）；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderQueryServiceImpl.java:71-73`（`ORDER_NOT_PAYABLE`）、`:131-133`（`ORDER_INVALID_STATUS`）；`code/ecommerce-order/src/main/java/com/ecommerce/order/integration/ProductIntegrationService.java:67`（`PRODUCT_VALIDATION_FAILED`）；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPreconditionChecker.java:34`（`USER_NOT_FOUND`）。
   - 设计要求定位：`README.md` §7.1 行 204-212 与 §7.2 行 216-229。与订单模块相关的冻结业务错误码包括 `USER_NOT_ACTIVE`、`USER_FROZEN`、`PRODUCT_NOT_FOR_SALE`、`INVENTORY_NOT_ENOUGH`、`ORDER_INVALID_AMOUNT`、`ORDER_RISK_REJECTED`、`ORDER_STATUS_CONFLICT` 等。
   - 具体描述：上述代码使用了 README §7 未列出的错误码；同类场景设计中已有通用或业务错误码，如资源不存在应使用 `RESOURCE_NOT_FOUND`，订单状态不允许操作应使用 `ORDER_STATUS_CONFLICT`。
   - 原因解析：README §7 是顶层错误码冻结约束。订单模块对外 REST 或跨模块接口抛出未列错误码，会破坏冻结错误响应契约。

## 检查遗漏声明

1. **架构风格**：已检查模块化单体、包边界、本模块 Repository/Entity、跨模块接口调用。未找到跨模块直接注入其它模块 Repository 的实现。
2. **模块依赖方向**：已检查 `code/pom.xml` 与 `code/ecommerce-order/pom.xml`。未发现订单模块依赖 cart/payment/logistics/review；发现 loyalty 依赖存在，但接口类型与 §4 的 `LoyaltyCommandService` 要求不一致，已列入不一致。
3. **关键本地接口**：已找到 `OrderQueryService` 与 `OrderPaymentStatusUpdater`。未找到订单模块内提供 `LoyaltyCommandService`，该接口按设计由 loyalty 模块提供；订单模块未找到对该接口的调用。
4. **领域事件**：已找到 `OrderCreatedEvent`、`OrderPaidEvent`、`OrderCancelledEvent` 与本模块 `OrderEventListener`。未在 `OrderPaymentStatusUpdater.markAsPaid` 路径找到 `OrderPaidEvent` 发布；已列入不一致。logistics、loyalty、notification 的跨模块监听器不属于本模块源码范围，本报告不判定其实现。
5. **事务边界**：已检查创建订单、支付成功、批量订单事务。未找到批量订单单条事务隔离（如每单 `REQUIRES_NEW`）；已列入不一致。退款流程属于 payment/refund 模块，设计文档未发现 ecommerce-order 对退款阶段提交的实现要求。
6. **缓存设计**：设计文档 §7 未发现 ecommerce-order 所属缓存要求；当前模块也未找到 cache 包、缓存注解或资源配置。
7. **安全架构**：已检查订单用户侧、管理侧角色注解。JWT 签发/解析与支付签名头校验不在 ecommerce-order 模块内；设计文档未发现本模块需单独实现。
8. **REST API 与错误码**：已检查 README §6.5 订单接口与 §6.8 `timeout-cancel` 接口路径、Method、认证、成功状态；README §6 未提供订单模块 Request/Response 字段明细，因此已列出现有 DTO 字段并声明无法与缺失字段清单逐项比对。已检查 README §7 相关错误码并列出未冻结错误码不一致。
9. **资源目录**：`code/ecommerce-order/src/main/resources/` 未找到，故未检查到订单模块专属 `application.yml`、缓存配置或其它资源配置文件。
