# ecommerce-inventory - 附录B 一致性检查

## 检查范围
- 模块：ecommerce-inventory
- 附录：附录B
- 输入资料：
  - `D:/Desktop/work/HW-ICT-CMP-04/README.md`：比赛边界、修改边界、冻结契约、检查口径相关内容，重点纳入设计文档为验收基准（line 9、line 21、line 37、line 237、line 281）、配置文件允许修改但不得破坏契约（line 29-33、line 35-40）、库存模块冻结 API 契约（line 105-115）。
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录B-配置参考.md` 全文：application.yml 示例与配置项层级（line 3-67）、配置默认值表（line 69-81）。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java` 下全部 Java 源文件。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/test/java` 下全部 Java 测试源文件。
  - 当前模块配置文件：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/resources`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/test/resources` 均不存在，未发现 `application.yml` / `application-test.yml` 等模块内资源配置文件。
  - 当前模块 POM：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/pom.xml`。
  - 整个项目 POM：`D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`。

## 检查结论
- 未发现不一致。
- 当前模块未直接定义或绑定附录B中的业务配置项（如 `order.*`、`payment.*`、`invoice.*`、`cart.*`、`loyalty.*`、`test.reset-enabled`），也未发现模块内 `application.yml` / `application-test.yml` 对附录B配置项作出与设计冲突的名称、层级或默认值定义。
- 模块内存在直接使用 Caffeine 的库存汇总缓存实现（`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/cache/InventorySummaryCache.java:16-18`），但附录B仅给出 `spring.cache.type: caffeine` 的配置示例（`D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录B-配置参考.md:19-20`），未在当前模块范围内规定该库存缓存必须通过 Spring Cache 配置绑定实现，因此不作为附录B不一致项报告。

## 不一致明细
未发现与当前附录相关的实现不一致项。
