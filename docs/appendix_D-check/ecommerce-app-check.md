# ecommerce-app - 附录D 一致性检查

## 检查范围
- 模块：ecommerce-app
- 附录：附录D
- 输入资料：
  - `README.md` 中比赛边界、冻结契约、错误响应、检查口径相关内容（包括设计文档为验收基准、公开用例不覆盖全部验收范围、后置动作失败不阻塞支付、事件失败查询等要求）。
  - `design-docs/附录D-本地事件契约.md` 全文。
  - `code/ecommerce-app/src/main/java` 下全部源文件。
  - `code/ecommerce-app/src/test/java` 下全部测试源文件。
  - `code/ecommerce-app/src/main/resources/application.yml`、`code/ecommerce-app/src/test/resources/application-test.yml`。
  - `code/ecommerce-app/pom.xml`。
  - `code/pom.xml`。

## 检查结论
- 未发现不一致。

## 不一致明细
未发现与当前附录相关的实现不一致项。

核对说明：附录D定义本地事件通用字段及 `OrderPaidEvent`、`PaymentSucceededEvent`、`ShipmentDeliveredEvent`、`ReviewApprovedEvent` 的发布方、监听方、载荷字段和“监听器失败不得回滚支付状态”等语义（`design-docs/附录D-本地事件契约.md:3`、`design-docs/附录D-本地事件契约.md:13`、`design-docs/附录D-本地事件契约.md:28`、`design-docs/附录D-本地事件契约.md:30`、`design-docs/附录D-本地事件契约.md:45`、`design-docs/附录D-本地事件契约.md:59`）。`ecommerce-app` 作为启动/聚合模块未直接声明上述事件名称、通用字段或载荷字段；其直接相关实现主要是全局启动配置、事件失败查询管理接口和故障注入管理接口：`ShopHubApplication` 启用组件扫描/异步/调度（`code/ecommerce-app/src/main/java/com/ecommerce/app/ShopHubApplication.java:12`-`16`），`EventFailureAdminController` 提供 `/api/v1/admin/events/failures` 查询并返回失败记录集合（`code/ecommerce-app/src/main/java/com/ecommerce/app/controller/EventFailureAdminController.java:30`-`40`），`FaultInjectionAdminController` 提供故障注入启用和清除接口（`code/ecommerce-app/src/main/java/com/ecommerce/app/controller/FaultInjectionAdminController.java:18`-`33`）。这些实现与 README 中黑盒测试支撑管理接口的事件失败查询/故障注入要求（`README.md:184`-`195`）以及后置动作失败不阻塞支付、不得反向放宽设计要求的检查口径（`README.md:277`-`281`）未发现冲突。
