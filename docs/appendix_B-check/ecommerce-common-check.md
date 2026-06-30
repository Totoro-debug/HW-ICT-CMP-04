# ecommerce-common - 附录B 一致性检查

## 检查范围
- 模块：ecommerce-common
- 附录：附录B
- 输入资料：
  - `D:/Desktop/work/HW-ICT-CMP-04/README.md` 中比赛边界、修改边界、冻结契约、检查口径相关内容（设计文档是验收基准、公开用例不覆盖全部验收范围、配置文件允许修改但不得破坏契约等）。
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录B-配置参考.md` 全文。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java` 下全部源文件。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/test/java` 下全部测试源文件。
  - 当前模块配置文件检查结果：`ecommerce-common` 未包含 `src/main/resources` 或 `src/test/resources` 配置文件。
  - 当前模块 POM：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/pom.xml`。
  - 整个项目 POM：`D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`。

## 检查结论
- 共发现 1 处不一致。

## 不一致明细

### 1. 运行时配置默认值注册不完整，导致附录B列明的部分配置项无法按默认值查询/使用
- 设计要求定位：
  - `design-docs/附录B-配置参考.md:69`-`80`：附录B“配置默认值”明确列出 `order.expire-minutes=60`、`order.max-items=30`、`payment.retry-times=5`、`payment.refund-fee-rate=0.02`、`invoice.tax-rate=0.06`、`cart.ttl-days=7`、`loyalty.max-redeem-points-per-order=10000`、`loyalty.max-redeem-ratio=0.5`。
  - `README.md:184`-`191`：冻结契约包含黑盒测试支撑管理接口 `PUT /api/v1/admin/system/configs/{key}` 和 `GET /api/v1/admin/system/configs/{key}`，用于运行时配置覆盖和查询配置。
  - `README.md:279`-`282`：设计文档是验收基准，不应根据当前代码行为或公开测试现状反向放宽设计要求。
- 代码定位：
  - `code/ecommerce-common/src/main/java/com/ecommerce/common/test/RuntimeConfigRegistry.java:8`-`13`：当前通用运行时配置默认值只注册了 `payment.retry-times`、`invoice.tax-rate`、`loyalty.activity-multiplier`、`member.discount-rate`。
  - 必要跨模块调用定位：`code/ecommerce-app/src/main/java/com/ecommerce/app/controller/SystemAdminController.java:35`-`41`：配置查询接口直接通过 `RuntimeConfigRegistry.getOrDefault(key)` 取值，取不到时返回资源不存在。
- 不一致说明：附录B默认值表中与运行时配置查询/覆盖直接相关的配置项，应当在通用运行时配置默认值支持中保持配置项名称、层级和默认值一致；当前 `RuntimeConfigRegistry` 仅覆盖其中 `payment.retry-times`、`invoice.tax-rate` 两项，缺少 `order.expire-minutes`、`order.max-items`、`payment.refund-fee-rate`、`cart.ttl-days`、`loyalty.max-redeem-points-per-order`、`loyalty.max-redeem-ratio` 等附录B已列明默认值的配置项。由于查询接口依赖该注册表，缺失项在未覆盖时无法按附录B默认值返回/使用。
- 原因分析：设计要求是附录B列明的配置项及默认值作为验收基准，并且 README 冻结了运行时配置查询接口；当前实现是在 `ecommerce-common` 的共享 `RuntimeConfigRegistry` 中维护默认值，但默认值集合不完整。该问题属于配置默认值支持的**缺失**，并同时体现为运行时配置查询行为与附录B默认配置要求不一致。
