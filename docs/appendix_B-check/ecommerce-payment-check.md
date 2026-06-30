# ecommerce-payment - 附录B 一致性检查

## 检查范围
- 模块：ecommerce-payment
- 附录：附录B
- 输入资料：
  - `D:/Desktop/work/HW-ICT-CMP-04/README.md`：比赛边界、修改边界、冻结契约、检查口径相关内容。
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录B-配置参考.md`：全文。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java`：当前模块全部主源码。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/test/java`：当前模块全部测试源码。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment`：当前模块配置文件检查；未发现 `application.yml`、`application-test.yml`、`.properties` 等配置资源文件。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/pom.xml`：当前模块 POM。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`：整个项目 POM。

## 检查结论
- 共发现 2 处不一致。

## 不一致明细

### 1. `payment` 配置绑定缺少 `retry-times` 和 `callback-timeout-seconds`
- 设计要求定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录B-配置参考.md:34` - `payment` 配置层级。
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录B-配置参考.md:35` - `payment.retry-times: 5` 示例配置。
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录B-配置参考.md:37` - `payment.callback-timeout-seconds: 5` 示例配置。
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录B-配置参考.md:75` - `payment.retry-times` 默认值为 `5`。
- 代码定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/config/PaymentConfig.java:12` - 当前配置类绑定前缀为 `payment`。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/config/PaymentConfig.java:20` - 当前仅定义了 `refundFeeRate` 默认值。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/config/PaymentConfig.java:26` - 当前还定义了附录B未列出的 `callbackSignature`，但未定义 `retryTimes` 或 `callbackTimeoutSeconds`。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentCallbackService.java:48` - 回调处理入口未读取 `payment.callback-timeout-seconds`。
- 不一致说明：附录B列出的当前模块相关 `payment.retry-times`、`payment.callback-timeout-seconds` 未在当前模块配置类中建模绑定；其中 `payment.retry-times` 也缺少设计要求的默认值 `5`。
- 原因分析：设计要求在 `payment` 层级提供 `retry-times`、`refund-fee-rate`、`callback-timeout-seconds`，且 `retry-times` 默认值为 `5`；当前实现的 `PaymentConfig` 仅绑定 `refundFeeRate` 和 `callbackSignature`，没有 `retryTimes`、`callbackTimeoutSeconds` 字段、getter/setter 或默认值。该问题属于缺失/配置绑定不符。

### 2. `invoice.max-title-length` 配置未绑定且未用于发票抬头长度约束
- 设计要求定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录B-配置参考.md:39` - `invoice` 配置层级。
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录B-配置参考.md:41` - `invoice.max-title-length: 100` 示例配置。
- 代码定位：
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/dto/InvoiceRequest.java:12` - `invoiceTitle` 仅为普通 `String` 字段。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/dto/InvoiceRequest.java:33` - `invoiceTitle` getter。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/dto/InvoiceRequest.java:34` - `invoiceTitle` setter，无长度配置或校验。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/InvoiceService.java:83` - 当前仅读取 `invoice.tax-rate` 配置。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/InvoiceService.java:99` - 直接保存 `invoiceTitle`，未读取或应用 `invoice.max-title-length`。
- 不一致说明：附录B列出的 `invoice.max-title-length` 未在当前模块配置绑定或业务校验中体现；当前只实现了 `invoice.tax-rate` 默认值与读取。
- 原因分析：设计示例要求 `invoice.max-title-length` 位于 `invoice` 层级且值为 `100`；当前实现没有发票配置类，也没有在 `InvoiceRequest` 或 `InvoiceService` 中读取 `invoice.max-title-length` 或对 `invoiceTitle` 执行配置化长度约束。该问题属于缺失/配置绑定不符/约束不符。
