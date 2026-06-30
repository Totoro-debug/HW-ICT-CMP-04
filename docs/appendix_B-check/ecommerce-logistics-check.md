# ecommerce-logistics - 附录B 一致性检查

## 检查范围
- 模块：ecommerce-logistics
- 附录：附录B
- 输入资料：
  - `D:/Desktop/work/HW-ICT-CMP-04/README.md` 中比赛边界、修改边界、冻结契约、检查口径相关内容，特别是设计文档为验收基准、公开用例不覆盖全部验收范围、配置文件允许修改但不得破坏契约等要求。
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录B-配置参考.md` 全文。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java` 下所有 Java 源文件。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/test/java` 下所有 Java 测试源文件。
  - 当前模块资源配置：`src/main/resources`、`src/test/resources` 目录不存在；模块内未发现 `application.yml`、`application-test.yml`、`application.properties`。
  - 当前模块 POM：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/pom.xml`。
  - 整个项目 POM：`D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`。

## 检查结论
- 共发现 1 处不一致。

## 不一致明细
### 1. logistics 配置项未按附录B命名空间绑定，默认承运商实现值与配置示例不一致
- 设计要求定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录B-配置参考.md:61`-`63`：`logistics.default-carrier` 示例值为 `LOCAL_EXPRESS`，`logistics.free-shipping-threshold` 示例值为 `199.00`。
  - `D:/Desktop/work/HW-ICT-CMP-04/README.md:9`-`15`、`D:/Desktop/work/HW-ICT-CMP-04/README.md:281`：设计文档是验收基准，代码必须按设计修正，不应按当前代码行为或公开测试现状反向放宽设计要求。
- 代码定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/config/LogisticsConfig.java:10`-`15`：物流模块配置类仅做组件扫描，未声明/启用与 `logistics.default-carrier`、`logistics.free-shipping-threshold` 对应的配置属性绑定。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/controller/AdminLogisticsController.java:55`-`59`：打印面单时固定传入承运商字符串 `"DEFAULT"`。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/FreightCalculator.java:33`-`35`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/FreightCalculator.java:102`-`115`：包邮阈值以常量 `DEFAULT_FREE_SHIPPING_THRESHOLD = 199.00` 写死并在默认运费计算/模板兜底中使用，未从 `logistics.free-shipping-threshold` 绑定读取。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/FreightTemplateService.java:27`-`51`：创建运费模板缺省包邮阈值同样使用写死常量 `199.00`，未从 `logistics.free-shipping-threshold` 绑定读取。
- 不一致说明：附录B在 `logistics` 层级下给出了 `default-carrier: LOCAL_EXPRESS` 与 `free-shipping-threshold: 199.00` 两个当前模块直接相关配置项。当前模块没有对应的配置属性类或 `@Value` 绑定；默认承运商使用硬编码 `DEFAULT`，与附录B示例值 `LOCAL_EXPRESS` 不一致；包邮阈值虽然硬编码数值为 `199.00`，但没有绑定到附录B指定的 `logistics.free-shipping-threshold` 配置项，配置项名称/层级无法生效。
- 原因分析：设计要求当前模块通过 `logistics.default-carrier`、`logistics.free-shipping-threshold` 这一层级和命名表达物流配置；当前实现把承运商与包邮阈值散落为硬编码常量/字符串，没有形成附录B要求的配置绑定。该问题属于配置绑定缺失、配置项层级/命名未生效，以及默认承运商行为值不符。