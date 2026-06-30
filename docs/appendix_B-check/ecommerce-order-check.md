# ecommerce-order - 附录B 一致性检查

## 检查范围
- 模块：ecommerce-order
- 附录：附录B
- 输入资料：
  - `README.md` 中比赛边界、修改边界、冻结契约、检查口径相关内容（重点纳入 `README.md:9`、`README.md:13`、`README.md:28`、`README.md:35`、`README.md:73`、`README.md:279`）
  - `design-docs/附录B-配置参考.md` 全文
  - `code/ecommerce-order/src/main/java` 下全部源文件
  - `code/ecommerce-order/src/test/java` 下全部测试源文件
  - 当前模块配置文件：检查 `code/ecommerce-order/src/main/resources`、`code/ecommerce-order/src/test/resources`，目录不存在，模块内无独立 `application.yml` / `application-test.yml`
  - 当前模块 POM：`code/ecommerce-order/pom.xml`
  - 整个项目 POM：`code/pom.xml`

## 检查结论
- 共发现 1 处不一致。

## 不一致明细

### 1. `order` 配置项未在订单模块进行配置绑定，部分值硬编码或未按配置约束生效
- 设计要求定位：
  - `design-docs/附录B-配置参考.md:28` - `design-docs/附录B-配置参考.md:32`：示例配置层级为 `order.expire-minutes`、`order.max-items`、`order.packaging-fee`、`order.free-shipping-threshold`，示例值分别为 60、30、2.00、199.00。
  - `design-docs/附录B-配置参考.md:69` - `design-docs/附录B-配置参考.md:74`：配置默认值明确要求 `order.expire-minutes` 默认值 60、`order.max-items` 默认值 30。
  - `README.md:9` - `README.md:13`、`README.md:281`：设计文档是验收基准，代码必须按设计修正，不能根据当前代码行为或公开测试现状反向放宽设计要求。
- 代码定位：
  - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:215` - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:217`：订单创建时包装费通过 `totalCalculator.calculatePackagingFee(orderItems.size())` 计算，而不是绑定 `order.packaging-fee`。
  - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:261`：订单过期时间硬编码为 `SystemClockService.now().plusMinutes(60)`，未绑定 `order.expire-minutes`。
  - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPreconditionChecker.java:31` - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderPreconditionChecker.java:47`：订单前置校验只检查用户状态和 `itemCount <= 0`，未按 `order.max-items` 的默认值 30/配置值约束单笔订单最大商品种类。
  - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderTotalCalculator.java:22` - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderTotalCalculator.java:24`：运费、包邮阈值、包装费使用类内常量，`FREE_SHIPPING_THRESHOLD` 硬编码为 199.00，`PACKAGING_FEE_PER_ITEM` 硬编码为 1.00。
  - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderTotalCalculator.java:57` - `code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderTotalCalculator.java:65`：包装费按商品种类数量乘以 1.00 计算，未使用 `order.packaging-fee: 2.00` 示例配置项。
- 不一致说明：附录B给出了订单模块直接相关的 `order` 配置层级和值，且明确了 `order.expire-minutes` 与 `order.max-items` 的默认值；当前 `ecommerce-order` 模块没有配置属性类或配置绑定使用这些键，订单过期时间、包邮阈值、包装费等通过代码常量/字面量实现，`order.max-items` 未按配置约束生效，`order.packaging-fee` 示例值 2.00 也未被模块计算逻辑使用。
- 原因分析：设计要求是以 `order.*` 配置项作为配置来源，并保证默认值/示例值对应的配置行为可生效；当前实现是硬编码 60 分钟、硬编码 199.00 包邮阈值、按 1.00 × 商品种类数计算包装费，且只校验订单商品种类数大于 0、不校验最大 30。该问题属于配置绑定缺失，并伴随默认值/示例值未生效及约束不符。
