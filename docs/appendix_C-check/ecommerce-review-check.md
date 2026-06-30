# ecommerce-review - 附录C 一致性检查

## 检查范围
- 模块：ecommerce-review
- 附录：附录C
- 输入资料：
  - `README.md` 中比赛边界、修改边界、冻结契约、检查口径相关内容（如 `README.md:9`、`README.md:35`、`README.md:73`、`README.md:237`、`README.md:281`）
  - `design-docs/附录C-数据模型.md` 全文
  - `code/ecommerce-review/src/main/java` 下所有源文件
  - `code/ecommerce-review/src/test/java` 下所有测试源文件
  - 当前模块配置文件：未发现 `code/ecommerce-review/src/main/resources` 下配置文件
  - 当前模块 POM：`code/ecommerce-review/pom.xml`
  - 整个项目 POM：`code/pom.xml`

## 检查结论
- 共发现 1 处不一致

## 不一致明细
### 1. `reviews.content` 字段类型与附录C不一致
- 设计要求定位：`design-docs/附录C-数据模型.md:199`、`design-docs/附录C-数据模型.md:201`、`design-docs/附录C-数据模型.md:208`
- 代码定位：`code/ecommerce-review/src/main/java/com/ecommerce/review/entity/Review.java:35`、`code/ecommerce-review/src/main/java/com/ecommerce/review/entity/Review.java:36`
- 不一致说明：附录C 第 7 节 `reviews` 表要求 `content` 字段类型为 `VARCHAR`；当前 `Review` 实体将 `content` 字段声明为 `String`，但通过 `@Column(columnDefinition = "TEXT")` 显式指定数据库列类型为 `TEXT`。
- 原因分析：设计要求 `reviews.content` 为 `VARCHAR`，当前实现显式使用 `TEXT` 列定义，落库字段类型与设计不一致，属于类型不符。
