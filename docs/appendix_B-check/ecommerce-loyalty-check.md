# ecommerce-loyalty - 附录B 一致性检查

## 检查范围
- 模块：ecommerce-loyalty
- 附录：附录B
- 输入资料：
  - `README.md` 中比赛边界、修改边界、冻结契约、检查口径相关内容：`README.md:9`、`README.md:26`、`README.md:73`、`README.md:237`、`README.md:281`
  - `design-docs/附录B-配置参考.md` 全文
  - 当前模块 `code/ecommerce-loyalty/src/main/java` 下所有源文件
  - 当前模块 `code/ecommerce-loyalty/src/test/java` 下所有测试源文件
  - 当前模块配置文件：检查 `code/ecommerce-loyalty` 后未发现 `src/main/resources` 或 `src/test/resources` 下配置文件
  - 当前模块 POM：`code/ecommerce-loyalty/pom.xml`
  - 整个项目 POM：`code/pom.xml`

## 检查结论
- 共发现 1 处不一致

## 不一致明细
### 1. `loyalty.*` 配置项未建立配置绑定，当前实现以硬编码常量/非附录B键名替代
- 设计要求定位：`design-docs/附录B-配置参考.md:47`、`design-docs/附录B-配置参考.md:48`、`design-docs/附录B-配置参考.md:49`、`design-docs/附录B-配置参考.md:50`、`design-docs/附录B-配置参考.md:51`、`design-docs/附录B-配置参考.md:52`、`design-docs/附录B-配置参考.md:53`、`design-docs/附录B-配置参考.md:79`、`design-docs/附录B-配置参考.md:80`
- 代码定位：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:35`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:36`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:39`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:42`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:44`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:222`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:223`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:279`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEventListener.java:22`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/event/ReviewApprovedEventListener.java:23`
- 不一致说明：附录B在 `loyalty` 层级给出 `points-per-yuan`、`redeem-rate`、`max-redeem-points-per-order`、`max-redeem-ratio`、`expire-months`、`review-reward-points`，并在默认值表中明确 `loyalty.max-redeem-points-per-order=10000` 与 `loyalty.max-redeem-ratio=0.5`。当前模块没有配置属性类或配置文件绑定这些 `loyalty.*` 键；对应行为由 `LoyaltyPointService` 和 `ReviewApprovedEventListener` 中的硬编码常量实现，且运行时读取的是附录B未定义的 `loyalty.activity-multiplier`，不是附录B列出的配置项。
- 原因分析：设计要求是配置项应位于 `loyalty` 层级，并通过指定键名承载示例值/默认值；当前实现中 `redeem-rate`、`max-redeem-points-per-order`、`max-redeem-ratio`、`expire-months`、`review-reward-points` 等值虽然部分数值与附录B一致，但没有绑定到附录B配置键，外部配置这些键不会影响模块行为；`points-per-yuan` 也未作为配置项参与积分计算。该问题属于配置绑定缺失，并伴随配置项命名/结构不符。
