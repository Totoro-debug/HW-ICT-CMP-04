# ecommerce-promotion - 附录C 一致性检查

## 检查范围
- 模块：ecommerce-promotion
- 附录：附录C（数据模型）
- 输入资料：
  - `README.md`：比赛边界、修改边界、冻结契约、检查口径相关内容，重点纳入 `README.md:9`、`README.md:13`、`README.md:35`、`README.md:73`、`README.md:237`、`README.md:281`
  - `design-docs/附录C-数据模型.md` 全文，当前模块直接相关重点为第 6 节促销域 `coupons`：`design-docs/附录C-数据模型.md:161`-`design-docs/附录C-数据模型.md:174`
  - 当前模块 POM：`code/ecommerce-promotion/pom.xml`
  - 整个项目 POM：`code/pom.xml`
  - 当前模块源文件：`code/ecommerce-promotion/src/main/java`、`code/ecommerce-promotion/src/test/java` 下全部 Java 源文件
  - 当前模块配置文件：检查 `code/ecommerce-promotion/src/main/resources`，该目录不存在，未发现 `application.yml` / `application-test.yml` 等资源配置文件

## 检查结论
- 共发现 1 处不一致

## 不一致明细

### 1. 优惠券数据模型未按附录C定义实现为 `coupons` 表/实体，字段被拆分并改名
- 设计要求定位：
  - `design-docs/附录C-数据模型.md:161`-`design-docs/附录C-数据模型.md:174`：促销域要求存在 `coupons` 表，字段为 `id`、`coupon_code`、`type`、`discount_rate`、`amount`、`threshold_amount`、`valid_from`、`valid_to`；其中 `type` 为 `DISCOUNT/AMOUNT_OFF/THRESHOLD_OFF`，`discount_rate` 为 `DECIMAL(6,4)`，`amount` 与 `threshold_amount` 为 `DECIMAL(18,2)`。
  - `README.md:9`、`README.md:13`、`README.md:281`：设计文档是验收基准，代码必须按设计修正，不得反向修改设计文档。
  - `README.md:237`：公开用例不覆盖全部验收范围，不能据此放宽设计要求。
- 代码定位：
  - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/CouponTemplate.java:17`-`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/CouponTemplate.java:19`：实体表名为 `coupon_template`，不是附录C要求的 `coupons`。
  - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/CouponTemplate.java:24`-`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/CouponTemplate.java:32`：包含 `type`、`discount_value`、`threshold_amount`，其中 `discount_value` 与附录C `discount_rate` 命名不一致，且 `discount_value` 精度为 `precision = 10, scale = 4`，不是 `DECIMAL(6,4)`；`threshold_amount` 精度为 `precision = 12, scale = 2`，不是 `DECIMAL(18,2)`。
  - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/CouponTemplate.java:43`-`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/CouponTemplate.java:47`：有效期字段实现为 `start_time` / `end_time`，不是附录C要求的 `valid_from` / `valid_to`。
  - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/UserCoupon.java:15`-`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/UserCoupon.java:26`：另一个实体表名为 `user_coupon`，其中 `coupon_code` 位于 `user_coupon` 表；附录C要求 `coupon_code` 属于 `coupons` 表。
  - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/UserCoupon.java:22`-`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/UserCoupon.java:23`：实现通过 `coupon_template_id` 将用户券关联到券模板，附录C第 6 节未定义这种拆分关系。
  - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/CouponType.java:6`-`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/CouponType.java:20`：优惠券类型枚举值 `DISCOUNT`、`AMOUNT_OFF`、`THRESHOLD_OFF` 与附录C一致；该点无枚举值不一致。
- 不一致说明：附录C将促销域优惠券定义为单一 `coupons` 表，并明确了表名、字段名和 DECIMAL 类型精度；当前实现将优惠券数据拆成 `coupon_template` 与 `user_coupon` 两张表，未实现名为 `coupons` 的实体/表，并将附录C要求的字段分散或改名：`coupon_code` 在 `user_coupon`，`discount_rate` 实现为 `discount_value`，`valid_from` / `valid_to` 实现为 `start_time` / `end_time`，`amount` 未按附录C字段名出现在优惠券实体中，`threshold_amount` 与折扣字段精度也未按附录C的 `DECIMAL(18,2)` / `DECIMAL(6,4)` 实现。
- 原因分析：设计要求是以 `coupons` 作为促销优惠券数据模型的验收基准；当前代码采用“券模板 + 用户券”的内部建模方式，导致附录C已有数据模型在结构、命名和类型精度上均不一致。归类：结构不符、命名不符、类型不符。