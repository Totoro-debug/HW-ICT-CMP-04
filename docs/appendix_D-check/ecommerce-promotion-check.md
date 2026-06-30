# ecommerce-promotion - 附录D 一致性检查

## 检查范围
- 模块：ecommerce-promotion
- 附录：附录D（本地事件契约）
- 输入资料：
  - `README.md`：比赛边界、冻结契约、错误响应、检查口径相关内容；重点纳入“设计文档是验收基准”“公开用例不覆盖全部验收范围”“后置动作失败不阻塞支付”等要求（`README.md:9`, `README.md:73`, `README.md:200`, `README.md:237`, `README.md:277`, `README.md:281`）。
  - `design-docs/附录D-本地事件契约.md` 全文：事件通用字段、`OrderPaidEvent`、`PaymentSucceededEvent`、`ShipmentDeliveredEvent`、`ReviewApprovedEvent` 的发布方、监听方、载荷字段与监听失败语义（`design-docs/附录D-本地事件契约.md:3`, `design-docs/附录D-本地事件契约.md:13`, `design-docs/附录D-本地事件契约.md:30`, `design-docs/附录D-本地事件契约.md:45`, `design-docs/附录D-本地事件契约.md:59`）。
  - 当前模块 POM：`code/ecommerce-promotion/pom.xml`。
  - 整个项目 POM：`code/pom.xml`。
  - 当前模块全部源文件：`code/ecommerce-promotion/src/main/java`、`code/ecommerce-promotion/src/test/java`。
  - 当前模块配置文件：检查 `code/ecommerce-promotion/src/main/resources`，该目录不存在，当前模块无独立资源配置文件。

## 检查结论
- 当前模块无与本附录直接相关的检查项。
- 未发现不一致。
- 发现数量：0。

## 不一致明细
当前模块无与本附录直接相关的检查项。

附录D声明的事件发布/监听关系为：`OrderPaidEvent` 发布方是 order-service、监听方是 logistics-service/loyalty-service/common notification（`design-docs/附录D-本地事件契约.md:13`-`design-docs/附录D-本地事件契约.md:28`）；`PaymentSucceededEvent` 发布方是 payment-service、监听方是 order-service/inventory-service/logistics-service/loyalty-service/common notification（`design-docs/附录D-本地事件契约.md:30`-`design-docs/附录D-本地事件契约.md:44`）；`ShipmentDeliveredEvent` 发布方是 logistics-service、监听方是 order-service/loyalty-service（`design-docs/附录D-本地事件契约.md:45`-`design-docs/附录D-本地事件契约.md:58`）；`ReviewApprovedEvent` 发布方是 review-service、监听方是 loyalty-service（`design-docs/附录D-本地事件契约.md:59`-`design-docs/附录D-本地事件契约.md:73`）。附录D未声明 promotion-service 为上述事件的发布方或监听方。

对 `code/ecommerce-promotion` 模块源文件、测试源文件、模块 POM 进行检查后，未发现 `OrderPaidEvent`、`PaymentSucceededEvent`、`ShipmentDeliveredEvent`、`ReviewApprovedEvent`、`ApplicationEventPublisher`、`publishEvent`、`@EventListener`、`@TransactionalEventListener` 或本地事件契约相关的直接发布/监听实现。因此不存在需要按附录D报告的事件名称、通用字段、载荷字段、发布方、监听方或“监听器失败不得回滚主流程”语义不一致项。
