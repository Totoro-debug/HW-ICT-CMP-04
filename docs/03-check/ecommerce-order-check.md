# ecommerce-order 模块 03-通用规范与非功能设计检查报告

## 检查结论

### 一致

- Match：金额字段在订单实体中使用 `BigDecimal`，主要入库金额字段声明为 `precision = 12, scale = 2`：`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/Order.java:46`、`50`、`54`、`58`、`62`、`66`、`70`，符合 `03-通用规范与非功能设计.md §1 金额计算` 中“金额使用 BigDecimal、最终入库金额 2 位”的主要要求。
- Match：创建订单金额计算链路中商品小计、运费、包装费、优惠、积分抵扣、应付金额均使用 `BigDecimal`：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:158`、`174`、`178`、`194`、`198`、`201`、`219`，未发现用 `double`/`float` 表示订单金额。
- Match：订单取消审核会写入订单事件日志，日志实体包含业务 ID、操作前状态、操作后状态、操作类型、操作者、操作时间、备注：`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/OrderEventLog.java:23`、`28`、`33`、`38`、`42`、`46`、`50`；取消审核通过/拒绝均调用记录逻辑：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderCancelService.java:211`、`223`，符合 `03-通用规范与非功能设计.md §6 审计日志`。
- Match：订单通知通过 `LocalNotificationService` 提交 `NotificationRequest`，未在 order 模块 Java 源码中发现直接调用 `MockMailSender` 或 `MockSmsSender`：`code/ecommerce-order/src/main/java/com/ecommerce/order/integration/OrderNotificationService.java:3`、`4`、`36`、`48`、`54`、`67`、`73`、`86`、`92`、`105`、`111`、`124`、`130`、`143`、`149`、`163`、`170`、`183`、`190`，符合 `03-通用规范与非功能设计.md §7 通知规范`。
- Match：未在 `code/ecommerce-order/src/main/java/com/ecommerce/order/controller/**/*.java` 范围内发现 `reset` 或 `bootstrap` REST 接口；业务创建、取消等控制器通过正式订单 REST API 暴露，未发现为黑盒隔离暴露的 reset/bootstrap 接口，符合 `03-通用规范与非功能设计.md §5 黑盒测试隔离`。注意：存在管理/对账用途的服务方法 `OrderLifecycleService.resetToCreated`，见检查遗漏声明。

### 不一致

- Mismatch：优惠金额缺少边界校验。
  - 代码定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:198` 将 `calculateDiscounts(...)` 的结果直接作为 `discountAmount`；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:415` 至 `438` 直接返回促销模块 `calcResponse.getTotalDiscount()`；未找到对优惠金额 `>= 0` 且 `<= itemTotal` 的校验。
  - 设计要求定位：`03-通用规范与非功能设计.md §1 金额计算`，第 15 行要求“优惠金额不得小于 0，不得大于商品金额”。
  - 不一致具体描述：order 模块创建订单时信任促销返回值，没有在订单边界重新校验优惠金额上下限。
  - 原因解析：订单服务缺少针对 `discountAmount` 的防御性校验，导致促销结果异常时可能生成负优惠或优惠大于商品金额的订单。

- Mismatch：订单金额校验失败未抛 `OrderValidationException`。
  - 代码定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderValidator.java:24` 至 `28` 的 `validateAmount` 抛出 `BusinessException("ORDER_INVALID_AMOUNT", ...)`；`code/ecommerce-order/src/main/java/com/ecommerce/order/util/OrderValidationUtils.java:83` 至 `91` 的金额校验也抛出 `BusinessException`；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:5` 虽导入 `OrderValidationException`，但在该文件中未使用。
  - 设计要求定位：`03-通用规范与非功能设计.md §2 通用异常`，第 29 行要求“订单金额校验失败必须抛出 `OrderValidationException`，不得抛出 Java 标准 `IllegalArgumentException`”。
  - 不一致具体描述：金额校验失败当前抛通用 `BusinessException`，不符合必须使用订单金额校验专用异常的要求。
  - 原因解析：校验组件沿用了通用业务异常，未将金额校验失败语义映射为 `OrderValidationException`。

- Mismatch：创建订单接口未按 `externalOrderNo` 实现幂等。
  - 代码定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java:57` 至 `64` 直接调用 `orderService.createOrder`；`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:140` 至 `304` 的创建流程未先按 `externalOrderNo` 查询已有订单；`code/ecommerce-order/src/main/java/com/ecommerce/order/repository/OrderRepository.java:43` 虽定义 `findByExternalOrderNoAndUserId`，但在 `OrderService.createOrder` 中未使用；`code/ecommerce-order/src/main/java/com/ecommerce/order/dto/CreateOrderRequest.java:30` 至 `32` 中 `externalOrderNo` 也是可选字段。
  - 设计要求定位：`03-通用规范与非功能设计.md §3 幂等规范`，第 37 行要求创建订单幂等键为 `externalOrderNo`，第 43 行要求重复请求返回第一次处理结果且不得重复扣款、扣库存、发积分或开票。
  - 不一致具体描述：重复提交相同 `externalOrderNo` 的创建订单请求会重新执行订单创建、积分冻结、优惠券使用、库存预占和事件发布流程，而不是返回第一次处理结果。
  - 原因解析：创建订单服务只保存 `externalOrderNo`，没有强制请求携带、唯一约束或请求入口幂等查询/返回逻辑。

- Mismatch：创建订单接口未实现本地限流。
  - 代码定位：已搜索 `code/ecommerce-order/src/main/java/com/ecommerce/order/**/*.java`，未找到 `RateLimit` 或 `RATE_LIMITED`；`code/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java:57` 至 `64` 的创建订单入口没有限流判断。
  - 设计要求定位：`03-通用规范与非功能设计.md §4 本地限流`，第 52 行要求“创建订单：同一用户每分钟 20 次”，第 54 行要求触发时返回 429 且错误码为 `RATE_LIMITED`。
  - 不一致具体描述：order 模块没有创建订单按用户每分钟 20 次的本地限流实现，也没有返回 `RATE_LIMITED` 错误码的路径。
  - 原因解析：控制器和服务层均未引入本地计数器、限流组件或限流异常映射。

- Mismatch：本地事件失败处理未落库失败记录。
  - 代码定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/listener/OrderEventListener.java:46` 至 `57`、`63` 至 `79`、`85` 至 `96` 为事件监听器实现；这些监听器仅记录正常日志，未捕获处理失败、未保存失败记录到本地事件处理表；异步 fallback 监听器 `code/ecommerce-order/src/main/java/com/ecommerce/order/listener/OrderEventListener.java:103` 至 `128` 也没有失败处理。已搜索 `code/ecommerce-order/src/main/java/com/ecommerce/order/**/*.java`，未找到本地事件处理失败表实体或 repository。
  - 设计要求定位：`03-通用规范与非功能设计.md §8 本地事件失败处理`，第 97 至 104 行要求事件监听器失败时记录失败日志、保存失败记录到本地事件处理表，非强一致监听器不回滚主业务事务，支付成功后的物流、积分、通知监听器均为非强一致监听器。
  - 不一致具体描述：order 模块相关事件监听器没有统一失败处理和失败记录落库；支付成功后的 order 内部监听器为 `AFTER_COMMIT`，但失败记录要求未实现。
  - 原因解析：当前监听器只覆盖正常处理路径，缺少本地事件处理表、失败记录服务和监听器异常兜底逻辑。

## 检查遗漏声明

- 未找到 `code/ecommerce-order/src/main/resources` 目录，因此未找到 `code/ecommerce-order/src/main/resources/*.yml` 配置文件。
- 未找到创建订单本地限流实现：搜索范围为 `code/ecommerce-order/src/main/java/com/ecommerce/order/**/*.java`，关键词 `RateLimit`、`RATE_LIMITED` 均未命中。
- 未找到创建订单以 `externalOrderNo` 返回第一次处理结果的幂等实现：搜索范围为 `code/ecommerce-order/src/main/java/com/ecommerce/order/**/*.java`，仅找到 DTO/实体字段及 repository 方法，未在创建订单主流程中找到使用。
- 未找到 `OrderValidationException` 在金额校验失败路径中的实际抛出；仅在 `OrderService.java` 发现未使用导入。
- 未找到本地事件处理失败表实体、repository 或保存失败记录逻辑；`OrderEventLog` 是订单状态流转/审计日志，不是事件处理失败表。
- 未找到 order 模块内支付成功后的物流、积分、通知监听器实现；只找到 `OrderEventListener.onOrderPaid` 的订单内部支付时间更新逻辑，物流/积分/通知监听器可能位于其他模块，本次按要求仅检查 order 模块。
- 未找到业务 REST API 暴露的 reset/bootstrap 接口；但找到 `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderLifecycleService.java:236` 的 `resetToCreated` 管理/对账服务方法，未发现其作为黑盒测试隔离接口暴露。
