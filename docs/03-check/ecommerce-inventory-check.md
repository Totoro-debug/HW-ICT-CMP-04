# ecommerce-inventory 模块 03-通用规范与非功能设计检查报告

## 检查结论

### 一致

- Match：金额计算。已检查范围 `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/**/*.java`，未发现 `BigDecimal`、`double`、`float` 金额字段或金额计算逻辑；inventory 模块当前仅处理库存数量，不涉及金额，未违反 `03-通用规范与非功能设计.md §1. 金额计算`。
- Match：通用异常。库存不足、资源不存在等已使用 `BusinessException`、`ResourceNotFoundException`；未发现订单金额校验逻辑，也未发现 `IllegalArgumentException` 用于订单金额校验，符合 `03-通用规范与非功能设计.md §2. 通用异常` 中与本模块可对应的异常使用要求。
- Match：通知规范。已检查范围 `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/**/*.java`、`code/ecommerce-inventory/pom.xml`、`code/pom.xml`，未发现直接调用 `MockMailSender` 或 `MockSmsSender`，符合 `03-通用规范与非功能设计.md §7. 通知规范` 的禁止直接调用要求。
- Match：黑盒测试隔离。已检查范围 `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/**/*.java`、`code/ecommerce-inventory/pom.xml`、`code/pom.xml`，未发现业务 REST reset/bootstrap 接口或对 reset/bootstrap 的直接依赖，符合 `03-通用规范与非功能设计.md §5. 黑盒测试隔离`。

### 不一致

- Mismatch：库存人工调整未记录设计要求的审计日志。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-inventory\src\main\java\com\ecommerce\inventory\service\StockAdjustmentService.java:30-52`；`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-inventory\src\main\java\com\ecommerce\inventory\entity\StockAdjustment.java:12-25`。
  - 设计要求定位：`D:\Desktop\work\HW-ICT-CMP-04\design-docs-通用规范与非功能设计.md §6. 审计日志`。
  - 不一致具体描述：设计要求“库存人工调整”必须记录审计日志，且至少包含操作者、操作类型、业务 ID、操作前状态、操作后状态、操作时间和备注。现实现仅保存 `StockAdjustment` 的 `warehouseId`、`skuId`、`beforeQty`、`afterQty`、`reason`，并输出一条普通 `log.info`；未保存操作者、操作类型、业务 ID、操作时间等完整审计字段，也未发现独立审计日志记录实现。
  - 原因解析：`StockAdjustmentService.create` 只关注库存数量变更和调整记录持久化，未接入通用审计日志能力或审计实体；`StockAdjustment` 被设计为库存调整业务记录而非满足通用审计日志字段要求的审计记录。

- Mismatch：库存预占接口重复请求可能造成重复扣库存链路前置风险。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-inventory\src\main\java\com\ecommerce\inventory\service\InventoryReservationServiceImpl.java:38-78`；`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-inventory\src\main\java\com\ecommerce\inventoryepository\StockReservationRepository.java:14-18`；`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-inventory\src\main\java\com\ecommerce\inventory\entity\StockReservation.java:12-33`。
  - 设计要求定位：`D:\Desktop\work\HW-ICT-CMP-04\design-docs-通用规范与非功能设计.md §3. 幂等规范`。
  - 不一致具体描述：设计要求重复请求不得重复扣库存。`reserve(Long orderId, List<ReserveItem> items)` 未先按 `orderId` 或 `orderId + skuId + warehouseId` 查询已有预占，也未在 `stock_reservation` 表上声明业务唯一约束；同一订单重复调用 `reserve` 会再次增加 `reservedStock` 并新增 `StockReservation`，后续 `deductAfterPayment` 将处理这些重复的 `RESERVED` 记录，存在重复扣库存风险。
  - 原因解析：当前实现将每次 `reserve` 调用视为新请求处理，没有以订单号或外部幂等键返回第一次处理结果；仓储层虽提供 `findByOrderId...` 查询方法，但创建预占时未用于幂等判断，实体层也没有唯一约束兜底。

- Mismatch：管理端直接出库接口缺少幂等保护，重复请求会重复扣减现货库存。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-inventory\src\main\java\com\ecommerce\inventory\controller\AdminInventoryController.java:59-64`；`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-inventory\src\main\java\com\ecommerce\inventory\service\InventoryService.java:151-178`。
  - 设计要求定位：`D:\Desktop\work\HW-ICT-CMP-04\design-docs-通用规范与非功能设计.md §3. 幂等规范`。
  - 不一致具体描述：`POST /api/v1/admin/inventory/outbound` 每次调用都会在库存足够时执行 `stock.setOnHandStock(stock.getOnHandStock() - quantity)` 并新增 `OutboundOrder`；未按 `orderId` 或出库单号检查第一次处理结果。相同 `orderId` 的重复请求会重复扣减 `onHandStock`。
  - 原因解析：`outbound` 方法只做库存是否充足校验，没有使用 `OutboundOrderRepository.findByOrderId` 做幂等判断，也未定义请求幂等键或数据库唯一约束。

- Mismatch：未实现本地事件失败处理能力。
  - 代码定位：未找到对应实现；已搜索范围：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-inventory\src\main\java\com\ecommerce\inventory\**\*.java`，搜索关键字包括 `@EventListener`、`ApplicationListener`、`TransactionalEventListener`、`Listener`、`LocalEvent`、`event`。
  - 设计要求定位：`D:\Desktop\work\HW-ICT-CMP-04\design-docs-通用规范与非功能设计.md §8. 本地事件失败处理`。
  - 不一致具体描述：设计要求事件监听器失败时记录失败日志、保存失败记录到本地事件处理表、不回滚主业务事务（除非强一致监听器）、并可通过管理接口重放失败事件。inventory 模块未发现事件监听器、本地事件处理失败记录表/实体/仓储或重放管理接口。
  - 原因解析：当前 inventory 模块以同步 service 方法为主，未接入本地事件监听和失败记录机制，因此无法满足事件失败处理规范；若模块确实不承担事件监听职责，也应在实现或模块边界中明确无监听器，但当前代码中未找到相关声明。

- Mismatch：本地限流未在 inventory 暴露接口上发现统一实现或声明。
  - 代码定位：未找到对应实现；已搜索范围：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-inventory\src\main\java\com\ecommerce\inventory\**\*.java`，并检查 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-inventory\pom.xml`、`D:\Desktop\work\HW-ICT-CMP-04\code\pom.xml`，未发现 `RateLimit`、限流注解、过滤器或拦截器配置。
  - 设计要求定位：`D:\Desktop\work\HW-ICT-CMP-04\design-docs-通用规范与非功能设计.md §4. 本地限流`。
  - 不一致具体描述：设计列出登录、支付回调、商品搜索、创建订单的限流规则；inventory 模块不包含这些接口类别，但模块自身暴露 `POST /api/v1/admin/inventory/outbound`、`POST /api/v1/admin/inventory/adjustments`、`POST /api/v1/inventory/check` 等接口，代码中未发现限流机制或明确豁免说明，无法确认触发限流时返回 429 和错误码 `RATE_LIMITED`。
  - 原因解析：限流能力可能在网关或其他模块实现，但在本次限定检查范围内的 inventory 模块代码、模块 pom、根 pom 中未找到可验证实现；与设计中列明接口类别无直接对应的 inventory 接口也未提供本模块级别说明。

## 检查遗漏声明

- 配置文件：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-inventory\src\mainesources\*.yml` 未找到；`src/main/resources` 目录不存在。
- 金额计算：未找到金额字段或金额计算实现；搜索范围为 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-inventory\src\main\java\com\ecommerce\inventory\**\*.java`。
- 订单金额校验：未找到订单金额校验实现，未找到 `OrderValidationException` 使用；搜索范围为 inventory 模块 Java 源码。
- 通知发送：未找到 `LocalNotificationService`、`NotificationRequest`、`MockMailSender`、`MockSmsSender` 使用；搜索范围为 inventory 模块 Java 源码及 pom。
- reset/bootstrap：未找到业务 REST reset/bootstrap 接口或直接依赖；搜索范围为 inventory 模块 Java 源码及 pom。
- 事件监听器：未找到 `@EventListener`、`ApplicationListener`、`TransactionalEventListener` 或命名为 Listener 的实现；因此也未找到事件失败记录表、失败记录仓储或失败事件重放管理接口。
- 本地限流：未找到 `RateLimit`、限流注解、限流过滤器或限流拦截器实现；搜索范围为 inventory 模块 Java 源码及 pom。
- 审计日志：未找到独立审计日志实体、仓储、服务或通用审计日志调用；仅找到库存调整业务记录 `StockAdjustment` 和普通日志输出。
