# ecommerce-order - 附录C 一致性检查

## 检查范围
- 模块：ecommerce-order
- 附录：附录C
- 输入资料：
  - `README.md`：比赛边界、修改边界、冻结契约、检查口径相关内容（设计文档为验收基准：`README.md:9`、`README.md:21`、`README.md:37`、`README.md:237`、`README.md:281`）
  - `design-docs/附录C-数据模型.md` 全文
  - `code/ecommerce-order/src/main/java` 下全部源文件（84 个 Java 文件）
  - `code/ecommerce-order/src/test/java` 下全部源文件（18 个 Java 文件）
  - `code/ecommerce-order` 配置文件：未发现 `src/main/resources` 或 `src/test/resources` 下配置文件；已检查配置类 `code/ecommerce-order/src/main/java/com/ecommerce/order/config/OrderModuleConfig.java`
  - 当前模块 POM：`code/ecommerce-order/pom.xml`
  - 整个项目 POM：`code/pom.xml`

## 检查结论
- 共发现 2 处不一致

## 不一致明细

### 1. `orders` 金额字段精度与附录C要求不一致
- 设计要求定位：`design-docs/附录C-数据模型.md:101`、`design-docs/附录C-数据模型.md:102`、`design-docs/附录C-数据模型.md:103`、`design-docs/附录C-数据模型.md:104`、`design-docs/附录C-数据模型.md:105`、`design-docs/附录C-数据模型.md:106`、`design-docs/附录C-数据模型.md:107`
- 代码定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/Order.java:47`、`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/Order.java:51`、`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/Order.java:55`、`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/Order.java:59`、`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/Order.java:63`、`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/Order.java:67`、`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/Order.java:71`
- 不一致说明：附录C要求 `orders` 表的 `item_total`、`shipping_fee`、`packaging_fee`、`discount_amount`、`points_deduction_amount`、`payable_amount`、`paid_amount` 均为 `DECIMAL(18,2)`；当前 `Order` 实体对应字段均使用 `@Column(..., precision = 12, scale = 2)`。
- 原因分析：设计要求的金额字段精度为 18、标度为 2；当前实现标度为 2 但精度为 12，不满足 `DECIMAL(18,2)`。该问题属于类型不符。

### 2. `order_items` 商品快照与金额字段命名/类型与附录C要求不一致
- 设计要求定位：`design-docs/附录C-数据模型.md:117`、`design-docs/附录C-数据模型.md:118`、`design-docs/附录C-数据模型.md:119`、`design-docs/附录C-数据模型.md:121`
- 代码定位：`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/OrderItem.java:29`、`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/OrderItem.java:30`、`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/OrderItem.java:33`、`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/OrderItem.java:34`、`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/OrderItem.java:37`、`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/OrderItem.java:38`、`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/OrderItem.java:45`、`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/OrderItem.java:46`、`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/OrderItem.java:49`、`code/ecommerce-order/src/main/java/com/ecommerce/order/entity/OrderItem.java:50`
- 不一致说明：附录C要求 `order_items` 表包含 `product_name`（商品快照名称）、`sku_specs`（CLOB，SKU规格快照）、`unit_price`（DECIMAL(18,2)，单价）、`item_amount`（DECIMAL(18,2)，明细金额）。当前 `OrderItem` 实体实现为 `sku_name`、额外的 `sku_code`、`price`（precision=12, scale=2）、`subtotal`（precision=12, scale=2）、`product_snapshot`（TEXT），未按设计字段名实现 `product_name`、`sku_specs`、`unit_price`、`item_amount`。
- 原因分析：设计要求以订单明细表字段名表达商品名称快照、SKU规格快照、单价和明细金额；当前实现使用不同字段名和不同快照字段结构，并且金额字段精度为 12 而非 18，`sku_specs` 的 CLOB 字段也未按名实现。该问题属于命名不符、缺失、类型不符和结构不符。
