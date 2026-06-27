# ecommerce-logistics 模块 03-通用规范与非功能设计检查报告

## 检查结论

### 一致

- Match：金额字段未发现使用 `double` / `float` 表示金额；运费相关 DTO、实体、服务均使用 `BigDecimal`，且 `Shipment.freightAmount`、`FreightTemplate.defaultFreight`、`FreightTemplate.freeShippingThreshold` 的 JPA 字段声明为 `precision = 12, scale = 2`。代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\entity\Shipment.java:63`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\entity\FreightTemplate.java:32`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\entity\FreightTemplate.java:36`。
- Match：通用异常中，资源不存在、鉴权失败、状态冲突等场景使用了公共异常类型 `ResourceNotFoundException`、`AuthorizationException`、`ConflictException`。代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\LogisticsCallbackService.java:61`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\LogisticsCallbackService.java:95`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\ShipmentService.java:311`。
- Match：本地限流规范列出的登录、支付回调、商品搜索、创建订单接口，在 `ecommerce-logistics` 模块主代码范围内未找到对应实现；未发现 logistics 模块对这些接口返回非 `RATE_LIMITED` 的违规实现。已搜索范围：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\**\*.java`。
- Match：通知规范方面，`ecommerce-logistics` 模块主代码中未发现直接调用 `MockMailSender` 或 `MockSmsSender`；也未发现通知发送实现。已搜索范围：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\**\*.java`。
- Match：支付成功后的物流监听器采用 `@Async` 且监听事务 `AFTER_COMMIT`，主支付事务提交后才执行物流发货创建逻辑，体现非强一致监听器形态。代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\event\OrderPaidShipmentListener.java:27`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\event\OrderPaidShipmentListener.java:28`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\event\OrderPaidShipmentListener.java:33`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\event\OrderPaidShipmentListener.java:34`。

### 不一致

- Mismatch：运费金额最终处理未显式按 `HALF_UP` 保留 2 位。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\FreightCalculator.java:43`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\FreightCalculator.java:67`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\FreightCalculator.java:77`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\FreightTemplateService.java:45`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\FreightTemplateService.java:73`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\ShipmentService.java:74`。
  - 设计要求定位：`D:\Desktop\work\HW-ICT-CMP-04\design-docs\03-通用规范与非功能设计.md §1 金额计算`。
  - 不一致具体描述：设计要求最终入库金额保留 2 位且舍入模式为 `RoundingMode.HALF_UP`。当前运费模板创建/更新、运费计算、运费写入 shipment 时均未调用 `setScale(2, RoundingMode.HALF_UP)` 或等价统一金额规整逻辑，只依赖 JPA `scale = 2` 字段声明。
  - 原因解析：JPA 字段 scale 只描述持久化列精度，不能证明业务层所有入库前金额均按 `HALF_UP` 规整；不同数据库或 JDBC 行为可能导致截断、默认舍入或保留非预期 scale。

- Mismatch：`PickListService.completePicking` 的状态冲突抛出 Java 标准 `IllegalStateException`，未使用通用冲突异常。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\PickListService.java:88`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\PickListService.java:89`。
  - 设计要求定位：`D:\Desktop\work\HW-ICT-CMP-04\design-docs\03-通用规范与非功能设计.md §2 通用异常`。
  - 不一致具体描述：设计将状态冲突、重复提交归类为 `ConflictException`，HTTP 语义为 409；当前拣货单状态不允许完成时抛出 `IllegalStateException`，不属于设计列出的通用异常体系。
  - 原因解析：该业务冲突未通过公共异常类型表达，可能导致全局异常处理无法稳定映射到 409 与统一错误结构。

- Mismatch：物流回调接口未按 `trackingNo` + `eventTime` + `status` 实现幂等键，重复请求也未返回第一次处理结果。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\controller\LogisticsController.java:56`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\LogisticsCallbackService.java:52`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\LogisticsCallbackService.java:61`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\LogisticsCallbackService.java:65`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\LogisticsCallbackService.java:66`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\repository\ShipmentTrackingRepository.java:13`。
  - 设计要求定位：`D:\Desktop\work\HW-ICT-CMP-04\design-docs\03-通用规范与非功能设计.md §3 幂等规范`。
  - 不一致具体描述：回调服务只按 `trackingNo` 查找 shipment，然后调用 `updateStatus`；`eventTime` 仅用于日志和签名载荷，没有作为幂等键参与查询或唯一约束。`ShipmentTrackingRepository` 也只有按 `shipmentId` 查询的方法，未提供 `trackingNo + eventTime + status` 的重复检测或首次处理结果缓存。
  - 原因解析：缺少幂等记录表/唯一索引/首次结果存储，重复 carrier callback 会再次执行状态更新、再次写入 tracking，并可能重复发布投递事件等副作用。

- Mismatch：业务代码依赖测试故障注入入口，不符合黑盒测试隔离中业务代码不得依赖测试支撑接口的方向。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\ShipmentService.java:76`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\ShipmentService.java:77`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\ShipmentService.java:78`。
  - 设计要求定位：`D:\Desktop\work\HW-ICT-CMP-04\design-docs\03-通用规范与非功能设计.md §5 黑盒测试隔离`。
  - 不一致具体描述：`ShipmentService.createShipment` 中直接调用 `com.ecommerce.common.test.FaultInjectionRegistry.isActive(...)` 并根据测试开关注入失败；虽然未发现 reset/bootstrap REST 接口，但主业务代码显式依赖 `common.test` 故障注入能力。
  - 原因解析：黑盒隔离要求由测试 harness 提供，不应通过业务代码内置测试开关满足用例；该依赖会把测试支撑机制带入生产业务路径。

- Mismatch：未找到仓库验收审计日志实现，也未看到包含设计要求字段的审计日志记录。
  - 代码定位：未找到对应实现；已搜索范围为 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\**\*.java`，重点检查 `AdminLogisticsController`、`ShipmentService`、`PickListService`、实体与仓储层。现有仓库作业仅写 `ShipmentTracking`，例如 `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\service\ShipmentService.java:322`。
  - 设计要求定位：`D:\Desktop\work\HW-ICT-CMP-04\design-docs\03-通用规范与非功能设计.md §6 审计日志`。
  - 不一致具体描述：设计要求仓库验收必须记录审计日志，字段至少包含操作者、操作类型、业务 ID、操作前状态、操作后状态、操作时间、备注。当前 logistics 模块未发现 `Audit`/`audit` 相关服务、实体或调用；`ShipmentTracking` 只包含 shipmentId、status、location、description、eventTime、operator，缺少操作类型、业务 ID语义、操作前状态、操作后状态、备注等审计字段。
  - 原因解析：物流轨迹记录用于展示物流生命周期，不等同于审计日志；它没有记录操作前后状态和审计操作类型，无法满足仓库验收审计追踪要求。

- Mismatch：支付成功后的物流事件监听器失败时只记录日志，未保存失败记录到本地事件处理表。
  - 代码定位：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\event\OrderPaidShipmentListener.java:39`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\event\OrderPaidShipmentListener.java:40`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\event\OrderPaidShipmentListener.java:43`、`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\java\com\ecommerce\logistics\event\OrderPaidShipmentListener.java:44`。
  - 设计要求定位：`D:\Desktop\work\HW-ICT-CMP-04\design-docs\03-通用规范与非功能设计.md §8 本地事件失败处理`。
  - 不一致具体描述：监听器失败时会 `log.error`，且因 `@Async` + `AFTER_COMMIT` 不会回滚主业务事务；但未发现保存失败记录到本地事件处理表的实体、Repository 或调用，也未发现失败事件可重放管理接口。
  - 原因解析：当前实现只能在日志中看到失败，缺少持久化失败记录与重放入口，无法满足本地事件失败处理闭环。

## 检查遗漏声明

- 配置文件：`D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-logistics\src\main\resources\*.yml` 未找到；`src\main\resources` 目录不存在。
- 金额计算：未找到 logistics 模块中的优惠金额计算实现；未找到应付金额校验实现；未找到订单金额校验实现，因此也未找到 `OrderValidationException` 的使用场景。
- 通用异常：未找到 `BusinessException`、`ValidationException`、`RateLimitException` 在 logistics 主代码中的使用；未找到订单金额校验失败场景。
- 幂等规范：未找到物流回调幂等记录表、幂等键唯一约束、按 `trackingNo + eventTime + status` 查询的 Repository 方法、首次处理结果缓存或返回实现。
- 本地限流：未找到登录、支付回调、商品搜索、创建订单接口实现；未找到 logistics 模块内 `RATE_LIMITED` 返回实现。
- 黑盒测试隔离：未找到 reset/bootstrap REST 接口；但找到业务代码依赖 `com.ecommerce.common.test.FaultInjectionRegistry`。
- 审计日志：未找到仓库验收接口或服务命名实现；未找到 audit/audit log 相关实体、Repository、Service 或调用。
- 通知规范：未找到 `LocalNotificationService`、`NotificationRequest`、`MockMailSender`、`MockSmsSender` 在 logistics 主代码中的调用；即未找到通知发送实现。
- 本地事件失败处理：未找到本地事件处理表实体、Repository、失败记录保存逻辑、失败事件重放管理接口。
