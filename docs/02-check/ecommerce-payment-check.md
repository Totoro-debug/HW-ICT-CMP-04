# ecommerce-payment 模块设计一致性检查

## 检查结论

本次仅依据 `design-docs/02-系统架构.md` §1-§8、`README.md` §6 API 基线与 §7 错误码检查 `ecommerce-payment` 模块；已读取父工程 POM、支付模块 POM，并检查 `code/ecommerce-payment/src/main/java/` 下 controller/service/repository/entity/dto/query/config/event 等现有源码。`code/ecommerce-payment/src/main/resources/` 目录未找到；cache、listener 包未找到。

结论：8 个指定维度均已覆盖。主要不一致 6 项，集中在支付确认事务库存扣减、`LoyaltyCommandService` 依赖、退款后置通知、退款分阶段事务、跨模块 DTO/Entity 边界、以及 README §7 冻结错误码约束。

### 一致

1. **架构风格（02-系统架构.md §1、§3）**
   - 支付模块以独立包 `com.ecommerce.payment` 组织，并存在模块自动配置：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/config/PaymentAutoConfiguration.java`。
   - 模块拥有本模块 Repository 与实体，Repository 均指向 payment 模块实体：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/repository/PaymentRecordRepository.java`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/repository/RefundRecordRepository.java`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/repository/InvoiceRecordRepository.java`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/repository/SettlementBatchRepository.java`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/repository/SettlementOrderItemRepository.java`。
   - 未发现直接注入其它模块 Repository 或直接查询其它模块表的代码；跨模块订单查询/状态更新通过 `OrderQueryService` 与 `OrderPaymentStatusUpdater` 完成：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:5-6`、`:36-45`；`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentCallbackService.java:5`、`:27-39`。

2. **模块依赖方向（02-系统架构.md §2）**
   - 父工程包含 `ecommerce-payment` 模块，位于 `order` 之后：`code/pom.xml:13-25`。
   - 支付模块 POM 依赖 `ecommerce-common` 与 `ecommerce-order`：`code/ecommerce-payment/pom.xml:12-21`，与 §4 中 payment 需要使用 order 提供的 `OrderQueryService`、`OrderPaymentStatusUpdater` 的方向一致。

3. **关键本地接口（02-系统架构.md §4）**
   - 支付模块使用 order 提供的 `OrderQueryService` 查询可支付订单：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:5-6`、`:36-45`、`:56`。
   - 支付模块使用 order 提供的 `OrderPaymentStatusUpdater` 更新订单支付状态：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentCallbackService.java:5`、`:27-39`、`:115-117`、`:140-142`。
   - 支付模块对外提供本模块查询接口 `PaymentQueryService`：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/query/PaymentQueryService.java:7-12`，并通过本模块 Repository 实现：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/query/PaymentQueryServiceImpl.java:13-26`。

4. **领域事件（02-系统架构.md §5）**
   - 支付模块定义 `PaymentSucceededEvent`：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/PaymentSucceededEvent.java:7-27`。
   - 支付成功确认后发布 `PaymentSucceededEvent`：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:91-100`。
   - 支付模块定义并发布 `RefundCompletedEvent`：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/event/RefundCompletedEvent.java:7-30`；发布位置见 `code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundService.java:169-174`。
   - `DomainEventPublisher` 包装 Spring `ApplicationEventPublisher` 并捕获发布异常，记录失败事件：`code/ecommerce-common/src/main/java/com/ecommerce/common/event/DomainEventPublisher.java:40-50`，符合非关键后置动作失败不应直接抛出导致主流程失败的方向。

5. **事务边界（02-系统架构.md §6）**
   - 支付创建使用事务边界保存支付单：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:51-77`。
   - 支付回调使用事务边界处理支付单状态并调用订单支付状态更新：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentCallbackService.java:45-46`、`:108-119`。
   - 退款申请、审核、仓库验收方法均声明事务：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundService.java:58-92`、`:97-127`、`:132-152`。但退款完成阶段边界存在问题，见“不一致”第 4 项。

6. **缓存设计（02-系统架构.md §7）**
   - 设计文档 §7 未发现 ecommerce-payment 所属缓存 Key/TTL 要求；表中仅列 cart/product/inventory/user/logistics。
   - 当前支付模块未找到 `@Cacheable`、`@CacheEvict`、`CacheManager`、Redis/Caffeine 或设计表所列 Key 使用；与“设计文档未发现本模块相关缓存要求”一致。

7. **安全架构（02-系统架构.md §8）**
   - 用户侧支付、退款、发票接口要求 USER 角色：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/PaymentController.java:41-46`、`:67-72`；`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/RefundController.java:36-42`、`:49-54`；`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/InvoiceController.java:38-44`、`:51-56`。
   - 管理类退款、结算接口要求 ADMIN 角色：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/AdminRefundController.java:17-20`、`:34-42`、`:49-54`；`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/AdminSettlementController.java:18-20`、`:37-44`。
   - 支付回调接口读取并校验 `X-Payment-Signature`：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/PaymentController.java:53-60`；校验逻辑见 `code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentCallbackService.java:79-85`；默认签名配置见 `code/ecommerce-payment/src/main/java/com/ecommerce/payment/config/PaymentConfig.java:22-32`。

8. **REST API 路径、HTTP Method、认证、成功状态（README.md §6.6）**
   - `POST /api/v1/payment/pay`，USER，成功 201：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/PaymentController.java:22-46`，符合 README.md §6.6 行 145。
   - `POST /api/v1/payment/callback`，签名，成功 200：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/PaymentController.java:53-60`，符合 README.md §6.6 行 146。
   - `GET /api/v1/payment/{paymentNo}`，USER，成功 200：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/PaymentController.java:67-72`，符合 README.md §6.6 行 147。
   - `POST /api/v1/refunds/apply`，USER，成功 201：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/RefundController.java:20-42`，符合 README.md §6.6 行 148。
   - `POST /api/v1/admin/refunds/{refundId}/review`，ADMIN，成功 200：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/AdminRefundController.java:17-42`，符合 README.md §6.6 行 149。
   - `POST /api/v1/admin/refunds/{refundId}/warehouse-accept`，ADMIN，成功 200：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/AdminRefundController.java:49-54`，符合 README.md §6.6 行 150。
   - `GET /api/v1/refunds/{refundId}`，USER，成功 200：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/RefundController.java:49-54`，符合 README.md §6.6 行 151。
   - `POST /api/v1/invoices`，USER，成功 201：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/InvoiceController.java:22-44`，符合 README.md §6.6 行 152。
   - `GET /api/v1/invoices/order/{orderId}`，USER，成功 200：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/InvoiceController.java:51-56`，符合 README.md §6.6 行 153。
   - `POST /api/v1/admin/settlements/batches`，ADMIN，成功 201：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/AdminSettlementController.java:18-44`，符合 README.md §6.6 行 154。
   - README §6.6 仅冻结支付、退款、发票、结算 API 的路径、Method、认证、成功状态，未列出本模块 Request/Response 字段明细；当前代码字段已核对但无法与 README 中不存在的字段清单逐字段比对。主要 DTO 字段包括：`PayRequest` 字段 `orderId/amount/method/clientPaymentNo` 见 `code/ecommerce-payment/src/main/java/com/ecommerce/payment/dto/PayRequest.java:7-31`；`PayResponse` 字段 `paymentNo/orderId/status/paidAmount/createdAt` 见 `code/ecommerce-payment/src/main/java/com/ecommerce/payment/dto/PayResponse.java:8-36`；`PaymentCallbackRequest` 字段 `paymentNo/orderId/status/amount/callbackSequence/signature` 见 `code/ecommerce-payment/src/main/java/com/ecommerce/payment/dto/PaymentCallbackRequest.java:5-38`；退款、发票、结算 DTO 字段见 `code/ecommerce-payment/src/main/java/com/ecommerce/payment/dto/RefundApplyRequest.java:5-25`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/dto/RefundResponse.java:8-46`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/dto/RefundReviewRequest.java:3-19`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/dto/InvoiceRequest.java:7-36`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/dto/InvoiceResponse.java:9-56`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/dto/SettlementBatchResponse.java:9-44`。

### 不一致

1. **支付确认事务缺少库存扣减**
   - 代码定位：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentCallbackService.java:45-46`、`:108-119`；`code/ecommerce-payment/pom.xml:12-21`。
   - 设计要求定位：`design-docs/02-系统架构.md` §6 行 83：支付确认事务只包含支付单状态、订单支付状态和库存扣减；§3 行 46：跨模块命令必须通过领域服务接口或事件。
   - 具体不一致描述：支付回调成功事务只更新支付单状态、调用 `OrderPaymentStatusUpdater.markAsPaid(...)` 并发布支付成功事件，未发现调用库存扣减接口；支付模块 POM 也未依赖 `ecommerce-inventory`，无法直接使用库存预占/扣减本地接口。
   - 原因解析：设计把库存扣减列为支付确认事务内的关键一致性动作，区别于物流、积分、通知等后置动作。当前支付确认路径没有库存扣减，可能导致支付单与订单已成功但库存未扣减，事务边界与 §6 不一致。

2. **未按设计使用 loyalty 提供的 `LoyaltyCommandService`**
   - 代码定位：`code/ecommerce-payment/pom.xml:12-21`；`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:38-46`；`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentCallbackService.java:32-40`。
   - 设计要求定位：`design-docs/02-系统架构.md` §4 行 63：`LoyaltyCommandService | loyalty | order、payment | 积分抵扣、发放`。
   - 具体不一致描述：支付模块 POM 仅依赖 common、order，未依赖 loyalty；支付服务与回调服务构造器中未注入或调用 `LoyaltyCommandService`。
   - 原因解析：积分发放属于跨模块命令依赖，设计明确 payment 是 `LoyaltyCommandService` 使用方之一。当前实现只发布 `PaymentSucceededEvent`，没有按 §4 的关键本地接口执行 payment→loyalty 的命令调用，导致本地接口契约缺失。

3. **退款完成后同步发送通知，未按事件后置动作解耦**
   - 代码定位：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundService.java:40-52`、`:169-177`、`:191-202`。
   - 设计要求定位：`design-docs/02-系统架构.md` §5 行 78：`RefundCompletedEvent | payment | order、notification | 更新售后状态、通知用户`；§3 行 47：后置动作优先使用 ApplicationEvent；§3 行 49：不允许一个模块的事务依赖非关键后置监听器成功。
   - 具体不一致描述：代码在发布 `RefundCompletedEvent` 后，仍在 `RefundService` 内直接调用 `LocalNotificationService.send(...)` 发送退款完成通知。
   - 原因解析：设计要求退款完成通知由 `RefundCompletedEvent` 的 notification 监听方处理。当前同步调用通知服务把非关键后置动作耦合进退款流程；若通知发送抛出异常，可能影响当前退款事务或接口结果，不符合事件驱动解耦和非关键后置动作不影响主事务的要求。

4. **退款流程未体现“售后单、验收单、退款单”分阶段提交**
   - 代码定位：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundService.java:97-127`、`:132-160`、`:162-174`。
   - 设计要求定位：`design-docs/02-系统架构.md` §6 行 86：退款流程按售后单、验收单、退款单分阶段提交。
   - 具体不一致描述：代码中审核通过把同一个 `RefundRecord` 置为 `WAITING_WAREHOUSE_ACCEPT`；仓库验收方法在同一个 `@Transactional` 方法内先置为 `WAREHOUSE_ACCEPTED`，随后立即调用 `processRefund(...)` 把同一个记录改为 `COMPLETED`、更新支付单为 `REFUNDED` 并发布事件，未发现独立的售后单、验收单、退款单实体或分阶段提交边界。
   - 原因解析：设计要求退款链路分阶段提交，以隔离售后申请、仓库验收、退款完成各阶段状态与事务风险。当前实现把仓库验收与退款完成合并在一个事务和一张记录上，无法体现阶段提交边界。

5. **跨模块 DTO/Entity 边界存在泄露：payment 直接使用 order 的 entity 包枚举**
   - 代码定位：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentValidator.java:5`、`:41-46`。
   - 设计要求定位：`design-docs/02-系统架构.md` §3 行 48：跨模块传输使用 DTO，不暴露 JPA Entity；§3 行 45：跨模块查询必须通过 QueryService 接口。
   - 具体不一致描述：支付模块通过 `OrderQueryService` 获取 `OrderDto` 后，又直接导入 `com.ecommerce.order.entity.OrderStatus` 并用 order 模块 entity 包下的状态类型判断可支付状态。
   - 原因解析：虽然查询入口使用了 QueryService，但 payment 模块仍编译依赖 order 模块 entity 包类型，跨模块契约没有完全收敛到 DTO/接口层。状态类型应由 order 查询 DTO 或本地接口契约稳定暴露，而不是让 payment 感知 order 的实体包实现细节。

6. **支付模块抛出多个 README §7 未冻结的错误码**
   - 代码定位：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentValidator.java:36-45`（`ORDER_NOT_FOUND`、`ORDER_STATUS_INVALID`）、`:71-75`（`PAYMENT_DUPLICATE`）；`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentCallbackService.java:130-132`（`PAYMENT_STATUS_CONFLICT`）；`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundService.java:69-71`（`REFUND_NOT_ALLOWED`）、`:105-107`（`REFUND_STATUS_INVALID`）；`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/InvoiceService.java:60-61`（`NO_PAID_PAYMENT`）。
   - 设计要求定位：`README.md` §7.1 行 204-212 与 §7.2 行 216-229。与本模块相关的冻结业务错误码包括 `PAYMENT_AMOUNT_MISMATCH`、`REFUND_WAITING_WAREHOUSE_ACCEPT`、`INVOICE_AMOUNT_EXCEEDED`，以及通用 `VALIDATION_FAILED`、`RESOURCE_NOT_FOUND`、`UNAUTHORIZED`、`FORBIDDEN`、`CONFLICT` 等。
   - 具体不一致描述：上述代码使用了 README §7 未列出的错误码；同类场景设计中已有通用或业务错误码，例如资源不存在应使用 `RESOURCE_NOT_FOUND`，状态冲突或重复请求应使用 `CONFLICT`，订单状态不允许操作可映射到 `ORDER_STATUS_CONFLICT`。
   - 原因解析：README §7 是冻结错误码契约。本模块对 REST API 调用方暴露未冻结错误码，会破坏统一错误响应约束；其中 `PAYMENT_AMOUNT_MISMATCH`、`REFUND_WAITING_WAREHOUSE_ACCEPT`、`INVOICE_AMOUNT_EXCEEDED` 已在代码中出现并与 README 相关错误码一致，但其它未列错误码仍需收敛。

## 检查遗漏声明

1. **架构风格**：已检查模块化单体、包边界、本模块 Repository/Entity、跨模块接口调用。未找到跨模块直接注入其它模块 Repository 的实现；发现对 order entity 包枚举的直接依赖，已列入不一致。
2. **模块依赖方向**：已检查 `code/pom.xml` 与 `code/ecommerce-payment/pom.xml`。未发现支付模块依赖 cart/product/user/logistics/review；发现未依赖 inventory、loyalty 导致 §6 库存扣减与 §4 `LoyaltyCommandService` 要求无法落实，已列入不一致。
3. **关键本地接口**：已找到 payment 使用 `OrderQueryService`、`OrderPaymentStatusUpdater`。未找到 payment 使用 `LoyaltyCommandService`；已列入不一致。未找到 payment 直接调用库存扣减本地接口；已在事务边界不一致中说明。
4. **领域事件**：已找到 `PaymentSucceededEvent`、`RefundCompletedEvent`、`PaymentFailedEvent`。未找到 payment 模块内 listener 包或事件监听器；按设计 payment 是 `PaymentSucceededEvent`、`RefundCompletedEvent` 发布方，其监听方 order/logistics/loyalty/notification 属于其它模块，本报告不判定其它模块实现。退款完成后同步通知已列入不一致。
5. **事务边界**：已检查支付创建、支付回调、退款申请、退款审核、仓库验收/退款完成事务。未找到支付确认事务中的库存扣减；未找到退款“售后单、验收单、退款单”三阶段独立提交实现；均已列入不一致。
6. **缓存设计**：设计文档 §7 未发现 ecommerce-payment 所属缓存要求；当前模块也未找到 cache 包、缓存注解或资源配置。
7. **安全架构**：已检查 USER/ADMIN 角色注解与 `X-Payment-Signature` 回调签名头校验。JWT 签发/解析与 `Authorization: Bearer <token>` 的通用认证机制不属于 ecommerce-payment 模块源码职责；本模块通过 Spring Security 角色注解接入。
8. **REST API 与错误码**：已检查 README §6.6 支付、退款、发票、结算接口路径、Method、认证、成功状态；README §6 未提供本模块 Request/Response 字段明细，因此已列出现有 DTO 字段并声明无法与缺失字段清单逐项比对。已检查 README §7 相关错误码，发现未冻结错误码并列入不一致。
9. **资源目录**：`code/ecommerce-payment/src/main/resources/` 未找到，故未检查到支付模块专属 `application.yml`、缓存配置或其它资源配置文件。
10. **源码分类目录**：controller/service/repository/entity/dto/query/config/event 均已找到；cache、listener 目录未找到。
