# ecommerce-user - 附录D 一致性检查

## 检查范围
- 模块：ecommerce-user
- 附录：附录D
- 输入资料：
  - `README.md`：比赛边界、冻结契约、错误响应、检查口径相关内容，重点纳入 `README.md:9`、`README.md:11`、`README.md:13`、`README.md:35`-`README.md:39`、`README.md:73`-`README.md:75`、`README.md:200`-`README.md:229`、`README.md:237`、`README.md:277`、`README.md:281`
  - `design-docs/附录D-本地事件契约.md` 全文，重点纳入事件通用字段 `design-docs/附录D-本地事件契约.md:3`-`design-docs/附录D-本地事件契约.md:11`，以及四类事件的发布方、监听方、载荷与失败语义 `design-docs/附录D-本地事件契约.md:13`-`design-docs/附录D-本地事件契约.md:74`
  - 当前模块 POM：`code/ecommerce-user/pom.xml`
  - 整个项目 POM：`code/pom.xml`
  - 当前模块源文件：`code/ecommerce-user/src/main/java` 下全部 Java 文件
  - 当前模块测试源文件：`code/ecommerce-user/src/test/java` 下全部 Java 文件
  - 当前模块配置文件：检查 `code/ecommerce-user/src/main/resources`，该目录不存在，未发现模块内 application 配置文件

## 检查结论
- 当前模块无与本附录直接相关的检查项。
- 共发现 0 处不一致。

附录D声明的事件为 `OrderPaidEvent`、`PaymentSucceededEvent`、`ShipmentDeliveredEvent`、`ReviewApprovedEvent`，其发布方/监听方分别限定在 order-service、payment-service、logistics-service、review-service、inventory-service、loyalty-service、common notification 等范围内，未声明 user-service 为任何列出事件的发布方或监听方（`design-docs/附录D-本地事件契约.md:13`-`design-docs/附录D-本地事件契约.md:74`）。

当前 `ecommerce-user` 模块内仅发现用户注册通知相关本地事件发布与监听：`UserRegisterService` 发布 `UserRegistrationNotificationEvent`（`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserRegisterService.java:79`-`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserRegisterService.java:80`），`UserRegistrationNotificationListener` 监听该事件（`code/ecommerce-user/src/main/java/com/ecommerce/user/event/UserRegistrationNotificationListener.java:33`-`code/ecommerce-user/src/main/java/com/ecommerce/user/event/UserRegistrationNotificationListener.java:35`）。该事件不属于附录D列出的四类事件，因此不构成附录D直接事件契约检查项。

## 不一致明细

当前模块无与本附录直接相关的检查项。
