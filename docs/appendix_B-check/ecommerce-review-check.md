# ecommerce-review - 附录B 一致性检查

## 检查范围
- 模块：ecommerce-review
- 附录：附录B
- 输入资料：
  - `README.md`：比赛边界、修改边界、冻结契约、检查口径相关内容（设计文档为验收基准、公开用例不覆盖全部验收范围、配置文件可修改但不得破坏契约等）。
  - `design-docs/附录B-配置参考.md` 全文。
  - `code/ecommerce-review/src/main/java` 下全部源文件。
  - `code/ecommerce-review/src/test/java` 下全部测试源文件。
  - `code/ecommerce-review` 模块配置文件：检查未发现独立 `application.yml` / `application-test.yml` / `application.properties` 等资源配置文件。
  - `code/ecommerce-review/pom.xml`。
  - `code/pom.xml`。

## 检查结论
- 共发现 1 处不一致。

## 不一致明细
### 1. 评价奖励积分在 review 模块中硬编码，未绑定附录B定义的 `loyalty.review-reward-points`
- 设计要求定位：`design-docs/附录B-配置参考.md:47`-`design-docs/附录B-配置参考.md:53` 定义 `loyalty.review-reward-points: 20` 的配置层级和默认示例值；`README.md:9`、`README.md:281` 要求设计文档作为验收基准，不能按当前代码行为反向放宽设计要求；`README.md:28`-`README.md:33` 说明配置文件允许修改但不得破坏契约。
- 代码定位：`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewApprovedEventListener.java:19`-`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewApprovedEventListener.java:20` 将评价奖励积分定义为 `private static final int REVIEW_REWARD_POINTS = 20`；`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewApprovedEventListener.java:75`-`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewApprovedEventListener.java:81` 在评价奖励积分流程中使用该常量输出/授予积分。
- 不一致说明：附录B已经给出评价奖励积分配置项的名称和层级为 `loyalty.review-reward-points`，默认示例值为 `20`；当前 `ecommerce-review` 模块中与评价奖励积分相关的实现没有通过 `@Value`、`@ConfigurationProperties` 或等效配置绑定读取该配置项，而是直接硬编码常量 `20`。
- 原因分析：设计要求的可配置项应以 `loyalty.review-reward-points` 这一配置键作为来源，保持名称、层级和默认值可由配置文件/测试配置覆盖。当前实现虽然数值与示例默认值同为 `20`，但奖励积分来源不是配置项，运行时或测试配置对 `loyalty.review-reward-points` 的调整不会影响该模块内这段奖励逻辑。该问题属于**配置绑定缺失**。
