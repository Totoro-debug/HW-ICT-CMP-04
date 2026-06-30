# ecommerce-promotion - 附录B 一致性检查

## 检查范围
- 模块：ecommerce-promotion
- 附录：附录B
- 输入资料：
  - `README.md`：比赛边界、修改边界、冻结契约、检查口径相关内容（设计文档为验收基准、公开用例不覆盖全部验收范围、配置文件允许修改但不得破坏契约）。
  - `design-docs/附录B-配置参考.md` 全文。
  - `code/ecommerce-promotion/src/main/java` 下所有源文件。
  - `code/ecommerce-promotion/src/test/java` 下所有测试源文件。
  - `code/ecommerce-promotion` 模块配置文件检查：未发现 `src/main/resources` 或 `src/test/resources` 配置文件目录。
  - `code/ecommerce-promotion/pom.xml`。
  - `code/pom.xml`。
  - 为核对运行配置承载位置，另参考 `code/ecommerce-app/src/main/resources/application.yml` 与 `code/ecommerce-app/src/test/resources/application-test.yml`。

## 检查结论
- 共发现 1 处不一致。

## 不一致明细
### 1. `promotion.stack-order` 配置项缺失配置承载与绑定，促销叠加顺序被代码硬编码
- 设计要求定位：
  - `design-docs/附录B-配置参考.md:55` 定义 `promotion` 配置层级。
  - `design-docs/附录B-配置参考.md:56` 定义 `promotion.stack-order`。
  - `design-docs/附录B-配置参考.md:57`-`design-docs/附录B-配置参考.md:59` 要求顺序为 `FULL_REDUCTION`、`COUPON`、`MEMBER_DISCOUNT`。
  - `README.md:9`-`README.md:13` 要求设计文档为验收基准，代码实现需与设计文档对齐且不可反向修改设计。
  - `README.md:237` 与 `README.md:281` 说明公开用例不覆盖全部验收范围，不能按公开测试现状放宽设计要求。
- 代码定位：
  - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:74`-`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:80` 直接按固定代码顺序执行满减、优惠券、会员折扣、阶梯价折扣。
  - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:207` 注释也声明中心化叠加模型为硬编码顺序。
  - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:139`-`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:140` 仅通过 `RuntimeConfigRegistry` 读取 `member.discount-rate`，未读取或绑定 `promotion.stack-order`。
  - `code/ecommerce-promotion/pom.xml:11`-`code/ecommerce-promotion/pom.xml:35` 仅声明依赖，未提供配置属性处理或模块配置资源。
  - 跨模块配置承载处：`code/ecommerce-app/src/main/resources/application.yml:45`-`code/ecommerce-app/src/main/resources/application.yml:52` 与 `code/ecommerce-app/src/test/resources/application-test.yml:45`-`code/ecommerce-app/src/test/resources/application-test.yml:52` 只有 `loyalty` 配置段，未包含附录B要求的 `promotion.stack-order` 配置段。
- 不一致说明：附录B明确给出 `promotion.stack-order` 配置项及其层级和顺序，检查口径要求核对配置项名称、层级、默认值、配置绑定和测试相关配置行为。当前 `ecommerce-promotion` 模块没有配置属性类、配置资源或运行时读取逻辑来承载/绑定 `promotion.stack-order`；促销叠加顺序虽然在当前代码中实际执行为 `FULL_REDUCTION -> COUPON -> MEMBER_DISCOUNT`，但该顺序来自 `PromotionCalculationServiceImpl` 的硬编码流程，而不是附录B指定的 `promotion.stack-order` 配置项。
- 原因分析：设计要求是存在 `promotion.stack-order` 配置层级，并按 `FULL_REDUCTION`、`COUPON`、`MEMBER_DISCOUNT` 的顺序配置。当前实现是服务代码固定调用 `applyFullReduction`、`applyCoupon`、`applyMemberDiscount`，且应用配置文件未声明 `promotion.stack-order`，模块内也没有对应绑定。该问题属于配置项缺失/配置绑定缺失/结构不符：缺少附录B要求的配置项承载与绑定，导致配置参考中的配置层级无法通过模块实现体现。
