# ecommerce-review - 附录D 一致性检查

## 检查范围
- 模块：ecommerce-review
- 附录：附录D（本地事件契约）
- 输入资料：
  - `README.md` 中比赛边界、冻结契约、错误响应、检查口径相关内容（含 `README.md:9`、`README.md:21`、`README.md:35`、`README.md:73`、`README.md:200`、`README.md:237`、`README.md:277`、`README.md:281`）
  - `design-docs/附录D-本地事件契约.md` 全文
  - `code/ecommerce-review/src/main/java` 下全部源文件
  - `code/ecommerce-review/src/test/java` 下全部测试源文件
  - 当前模块配置文件：检查 `code/ecommerce-review/src/main/resources`、`code/ecommerce-review/src/test/resources`，未发现资源配置文件
  - 当前模块 POM：`code/ecommerce-review/pom.xml`
  - 整个项目 POM：`code/pom.xml`
  - 为核对事件基类/监听方关系，交叉引用了 `code/ecommerce-common` 的事件定义/发布器与 `code/ecommerce-loyalty` 的 `ReviewApprovedEvent` 监听器

## 检查结论
- 共发现 1 处不一致。

## 不一致明细

### 1. ReviewApprovedEvent 发布的事件对象缺少附录D要求的部分通用字段和载荷字段
- 设计要求定位：
  - `README.md:9`、`README.md:21`、`README.md:237`、`README.md:281`：设计文档是验收基准，公开用例不覆盖全部验收范围，不能按当前代码或公开测试反向放宽设计要求。
  - `design-docs/附录D-本地事件契约.md:3`-`design-docs/附录D-本地事件契约.md:11`：本地事件通用字段要求包含 `eventId`、`eventType`、`occurredAt`、`aggregateId`、`traceId`。
  - `design-docs/附录D-本地事件契约.md:59`-`design-docs/附录D-本地事件契约.md:72`：`ReviewApprovedEvent` 发布方为 `review-service`，监听方为 `loyalty-service`，载荷要求包含 `reviewId`、`userId`、`orderId`、`productId`。
- 代码定位：
  - `code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewModerationService.java:62`-`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewModerationService.java:63`：评价审核通过后发布 `new ReviewApprovedEvent(this, reviewId, review.getUserId())`，仅传入 `reviewId`、`userId`。
  - 交叉模块事件定义：`code/ecommerce-common/src/main/java/com/ecommerce/common/event/AbstractDomainEvent.java:14`-`code/ecommerce-common/src/main/java/com/ecommerce/common/event/AbstractDomainEvent.java:29` 仅提供 `eventId`、`occurredAt`；`code/ecommerce-common/src/main/java/com/ecommerce/common/event/ReviewApprovedEvent.java:9`-`code/ecommerce-common/src/main/java/com/ecommerce/common/event/ReviewApprovedEvent.java:24` 仅定义 `reviewId`、`userId`。
  - 交叉模块监听方：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEventListener.java:34`-`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEventListener.java:43` 监听 `ReviewApprovedEvent`，当前也只能读取 `reviewId`、`userId`。
- 不一致说明：附录D要求 `ReviewApprovedEvent` 作为本地事件具备完整通用字段，并携带 `reviewId`、`userId`、`orderId`、`productId`。当前 `ecommerce-review` 在审核通过时发布的事件只构造并传递 `reviewId`、`userId`；其事件基类只暴露 `eventId`、`occurredAt`，事件类未实现/暴露 `eventType`、`aggregateId`、`traceId`，也未包含载荷字段 `orderId`、`productId`。
- 原因分析：设计要求明确了本地事件的通用字段集合和 `ReviewApprovedEvent` 的载荷字段集合；当前实现的事件构造与事件类字段均不完整，导致发布方 `review-service` 发出的事件契约无法满足监听方 `loyalty-service` 按附录D可获得完整事件信息的要求。该问题属于字段缺失（通用字段缺失与载荷字段缺失），并按同一根因“`ReviewApprovedEvent` 事件契约实现不完整”合并报告。

## 已核对且未发现不一致的附录D相关点
- 发布/监听关系：`ecommerce-review` 在审核通过路径发布 `ReviewApprovedEvent`（`ReviewModerationService.java:62`-`ReviewModerationService.java:63`），`ecommerce-loyalty` 存在 `@EventListener` 监听该事件（`ReviewApprovedEventListener.java:34`-`ReviewApprovedEventListener.java:35`），与 `design-docs/附录D-本地事件契约.md:61`-`design-docs/附录D-本地事件契约.md:63` 的发布方/监听方关系一致。
- 监听器失败不回滚主流程：附录D在 `OrderPaidEvent` 下明确“监听器失败不得回滚支付状态”（`design-docs/附录D-本地事件契约.md:28`），README公开用例亦强调后置动作失败不阻塞支付（`README.md:277`）。本次仅检查 `ecommerce-review` 与 `ReviewApprovedEvent` 直接相关契约，未发现附录D对 `ReviewApprovedEvent` 另行写明同等回滚约束。
