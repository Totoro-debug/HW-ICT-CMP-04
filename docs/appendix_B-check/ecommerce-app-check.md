# ecommerce-app - 附录B 一致性检查

## 检查范围
- 模块：ecommerce-app
- 附录：附录B（配置参考）
- 输入资料：
  - `README.md`：比赛边界、修改边界、冻结契约、检查口径相关内容（设计文档为验收基准、公开用例不覆盖全部验收范围、配置文件允许修改但不得破坏契约等）
  - `design-docs/附录B-配置参考.md` 全文
  - `code/pom.xml`
  - `code/ecommerce-app/pom.xml`
  - `code/ecommerce-app/src/main/java` 下全部源文件
  - `code/ecommerce-app/src/test/java` 下全部测试源文件
  - `code/ecommerce-app/src/main/resources/application.yml`
  - `code/ecommerce-app/src/test/resources/application-test.yml`

## 检查结论
- 共发现 5 处不一致

## 不一致明细

### 1. `invoice.max-title-length` 配置项缺失
- 设计要求定位：`design-docs/附录B-配置参考.md:39`-`41`
- 代码定位：`code/ecommerce-app/src/main/resources/application.yml:38`-`40`
- 不一致说明：附录B在 `invoice` 层级下要求包含 `tax-rate: 0.06` 和 `max-title-length: 100`；当前启动模块 `application.yml` 的 `invoice` 层级仅配置了 `tax-rate: 0.06`，缺少 `max-title-length`。
- 原因分析：设计要求 `invoice.max-title-length` 作为配置项出现在附录B示例中；当前实现未在 `ecommerce-app` 的主配置中提供该配置。属于缺失。

### 2. `promotion.stack-order` 配置项缺失
- 设计要求定位：`design-docs/附录B-配置参考.md:55`-`59`
- 代码定位：`code/ecommerce-app/src/main/resources/application.yml:45`-`52`
- 不一致说明：附录B要求存在 `promotion.stack-order`，示例值依次为 `FULL_REDUCTION`、`COUPON`、`MEMBER_DISCOUNT`；当前 `application.yml` 在 `loyalty` 配置后结束，没有 `promotion` 层级及 `stack-order` 配置。
- 原因分析：设计要求启动配置提供促销叠加顺序；当前实现未声明该层级和配置项。属于缺失/结构不符。

### 3. `logistics.default-carrier` 与 `logistics.free-shipping-threshold` 配置项缺失
- 设计要求定位：`design-docs/附录B-配置参考.md:61`-`63`
- 代码定位：`code/ecommerce-app/src/main/resources/application.yml:35`-`36`
- 不一致说明：附录B要求 `logistics` 层级包含 `default-carrier: LOCAL_EXPRESS` 和 `free-shipping-threshold: 199.00`；当前 `application.yml` 的 `logistics` 层级只配置了 `callback-secret`，未提供附录B要求的两个配置项。
- 原因分析：设计要求物流默认承运商和包邮阈值作为配置项存在；当前实现的 `logistics` 层级被用于回调密钥配置，缺少附录B规定的配置项。属于缺失。

### 4. `test.reset-enabled` 配置项缺失
- 设计要求定位：`design-docs/附录B-配置参考.md:65`-`66`
- 代码定位：`code/ecommerce-app/src/main/resources/application.yml:45`-`52`；`code/ecommerce-app/src/test/resources/application-test.yml:45`-`51`
- 不一致说明：附录B要求存在 `test.reset-enabled: false`；当前主配置 `application.yml` 及测试 profile 配置 `application-test.yml` 均未声明 `test` 层级和 `reset-enabled` 配置。
- 原因分析：设计要求测试 reset 开关默认为关闭；当前模块未在主配置或测试配置中提供该开关，导致运行期配置项缺失。属于缺失。

### 5. 测试 profile 中 `security.jwt` 示例值与附录B不一致
- 设计要求定位：`design-docs/附录B-配置参考.md:22`-`26`
- 代码定位：`code/ecommerce-app/src/test/resources/application-test.yml:17`-`21`
- 不一致说明：附录B示例中 `security.jwt` 为 `issuer: shophub`、`secret: local-development-secret-change-me`、`expire-minutes: 120`；当前测试 profile 将其覆盖为 `issuer: test-issuer`、`secret: this-is-a-very-long-secret-key-for-testing-purposes-only`、`expire-minutes: 60`。
- 原因分析：README明确设计文档是验收基准，不能因公开测试现状反向放宽设计要求（`README.md:9`-`13`、`README.md:237`、`README.md:281`）。附录B给出了 `security.jwt` 层级与示例值；当前测试 profile 对同名配置进行了不同值覆盖，且 `expire-minutes` 与附录B示例值直接不一致。属于行为不符/默认示例值不符。
