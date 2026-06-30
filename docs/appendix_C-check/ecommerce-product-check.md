# ecommerce-product - 附录C 一致性检查

## 检查范围
- 模块：ecommerce-product
- 附录：附录C
- 输入资料：
  - `README.md` 中比赛边界、修改边界、冻结契约、检查口径相关内容（设计文档为验收基准、公开用例不覆盖全部验收范围等）
  - `design-docs/附录C-数据模型.md` 全文
  - `code/ecommerce-product/src/main/java` 下所有源文件
  - `code/ecommerce-product/src/test/java` 下所有测试源文件
  - 当前模块配置文件：未发现 `code/ecommerce-product/src/main/resources` 或 `src/test/resources` 下配置文件
  - 当前模块 POM：`code/ecommerce-product/pom.xml`
  - 整个项目 POM：`code/pom.xml`

## 检查结论
- 共发现 2 处不一致

## 不一致明细

### 1. product_sku.price 精度与设计 DECIMAL(18,2) 不一致
- 设计要求定位：`design-docs/附录C-数据模型.md:52`
- 代码定位：`code/ecommerce-product/src/main/java/com/ecommerce/product/entity/ProductSku.java:28`
- 不一致说明：设计要求 `product_sku.price` 字段类型为 `DECIMAL(18,2)`；当前实现中 `price` 字段使用 `@Column(precision = 12, scale = 2)`，实际模型精度为 `DECIMAL(12,2)`。
- 原因分析：附录 C 商品域明确规定 `product_sku.price` 为售价字段，类型应为 `DECIMAL(18,2)`。当前实体字段虽然使用 `BigDecimal` 且 scale 为 2，但 precision 配置为 12，不满足设计中的 18 位总精度要求，属于类型不符。

### 2. product_sku.specs_json 字段命名和类型与设计不一致
- 设计要求定位：`design-docs/附录C-数据模型.md:54`
- 代码定位：`code/ecommerce-product/src/main/java/com/ecommerce/product/entity/ProductSku.java:34-35`
- 不一致说明：设计要求 `product_sku` 表包含字段 `specs_json`，类型为 `CLOB`；当前实现中实体字段为 `private String specs`，注解为 `@Column(columnDefinition = "TEXT")`，未指定列名 `specs_json`，按 JPA 默认命名会映射为 `specs`，且列定义为 `TEXT` 而非 `CLOB`。
- 原因分析：附录 C 明确要求字段名为 `specs_json`，用于保存规格 JSON，字段类型为 `CLOB`。当前实现保存规格 JSON 的字段命名为 `specs`，数据库列名与设计不一致，同时列类型定义也与设计不一致，属于命名不符和类型不符。
