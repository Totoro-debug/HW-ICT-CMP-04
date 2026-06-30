# ecommerce-loyalty - 附录C 一致性检查

## 检查范围
- 模块：ecommerce-loyalty
- 附录：附录C
- 输入资料：
  - `README.md` 中比赛边界、修改边界、冻结契约、检查口径相关内容（设计文档为验收基准、公开用例不覆盖全部验收范围等）
  - `design-docs/附录C-数据模型.md` 全文
  - `code/ecommerce-loyalty/src/main/java` 下全部源文件
  - `code/ecommerce-loyalty/src/test/java` 下全部测试源文件
  - `code/ecommerce-loyalty/src/main/resources`、`code/ecommerce-loyalty/src/test/resources`：本模块未发现资源配置文件
  - `code/ecommerce-loyalty/pom.xml`
  - `code/pom.xml`

## 检查结论
- 共发现 1 处不一致

## 不一致明细
### 1. 积分数据模型未实现为设计要求的 `loyalty_points` 表/字段结构
- 设计要求定位：
  - `README.md:9`（设计文档为验收基准）
  - `README.md:12-13`（需对比设计文档与代码实现并按设计修复，不可反向修改设计文档）
  - `README.md:237`（公开用例不覆盖全部验收范围）
  - `README.md:281`（设计文档是验收基准，不能因当前代码行为或公开测试现状反向调整设计）
  - `design-docs/附录C-数据模型.md:188-197`（要求 `loyalty_points` 表包含 `id BIGINT`、`user_id BIGINT`、`points INT`、`available_points INT`、`expire_date DATE`、`source_type VARCHAR`）
- 代码定位：
  - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/entity/LoyaltyAccount.java:19-46`（当前实体映射为 `@Table(name = "loyalty_account")`，字段为 `user_id`、`total_points`、`available_points`、`frozen_points`、`redeemed_points`、`expired_points`、`member_level`、`annual_consumption`）
  - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/entity/PointsTransaction.java:20-49`（当前积分流水实体映射为 `@Table(name = "points_transaction")`，字段为 `user_id`、`type`、`amount`、`balance`、`biz_type`、`biz_id`、`description`、`expires_at`）
  - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/repository/LoyaltyAccountRepository.java:13-21`（仓储绑定 `LoyaltyAccount` 而非设计表 `loyalty_points`）
  - `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/repository/PointsTransactionRepository.java:18-39`（仓储绑定 `PointsTransaction` 流水表而非设计表 `loyalty_points`）
- 不一致说明：附录C要求当前模块直接相关的数据模型为 `loyalty_points`，并明确字段命名与类型；当前实现没有 `loyalty_points` 实体/表映射，而是拆分为 `loyalty_account` 与 `points_transaction` 两套表模型。设计字段 `points` 未实现为同名字段（实现中账户表为 `total_points`，流水表为 `amount`）；设计字段 `expire_date DATE` 未实现为同名 DATE 字段（实现中流水表为 `expires_at`，Java 类型为 `LocalDateTime`，对应时间戳语义）；设计字段 `source_type` 未实现为同名字段（实现中流水表使用 `biz_type`/`type` 表达来源或变更类型）。因此表名、字段命名、字段集合及过期日期类型均与附录C不一致。
- 原因分析：设计要求是单表 `loyalty_points` 数据模型，字段为 `user_id`、`points`、`available_points`、`expire_date`、`source_type` 等；当前实现采用账户余额表 `loyalty_account` 加流水表 `points_transaction` 的结构，并使用 `total_points`/`amount`、`expires_at LocalDateTime`、`biz_type`/`type` 等不同字段表达相近概念。这属于结构不符、命名不符、类型不符。
