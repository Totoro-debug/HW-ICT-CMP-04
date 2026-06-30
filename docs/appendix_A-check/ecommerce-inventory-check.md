# ecommerce-inventory - 附录A 一致性检查

## 检查范围
- 模块：ecommerce-inventory
- 附录：附录A
- 输入资料：
  - `D:/Desktop/work/HW-ICT-CMP-04/README.md`：比赛边界、冻结 REST API 契约、错误响应、检查口径相关内容，重点纳入第 3 章、第 6.3 节、第 7 章、第 8 章、第 9 章。
  - `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录A-API接口参考.md`：全文，重点纳入第 1 节、第 4 节。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java` 下全部源文件。
  - `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/test/java` 下全部测试源文件。
  - 当前模块配置文件：已检查 `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src`，未发现 `application.yml`、`application-test.yml`、`.properties`、`.xml` 等模块源配置文件。
  - 当前模块 POM：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/pom.xml`。
  - 整个项目 POM：`D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`。
  - 为核对错误响应结构，读取了跨模块公共实现：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/dto/ApiError.java`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/exception/BusinessException.java`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/exception/ResourceNotFoundException.java`。

## 检查结论
- 未发现不一致。

## 不一致明细
未发现与当前附录相关的实现不一致项。

核对要点摘要：
- `/api/v1/` 前缀：设计要求见 `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录A-API接口参考.md:5`、README 冻结契约见 `D:/Desktop/work/HW-ICT-CMP-04/README.md:75`；当前库存控制器使用 `@RequestMapping("/api/v1/inventory")`（`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/controller/InventoryController.java:17`），管理端控制器使用 `@RequestMapping("/api/v1/admin")`（`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/controller/AdminInventoryController.java:29`）。
- 库存模块 API 路径、HTTP Method、成功状态码：README 第 6.3 节要求见 `D:/Desktop/work/HW-ICT-CMP-04/README.md:105`-`D:/Desktop/work/HW-ICT-CMP-04/README.md:115`，附录 A 第 4 节要求见 `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录A-API接口参考.md:147`-`D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录A-API接口参考.md:157`；当前实现分别位于 `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/controller/AdminInventoryController.java:48`-`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/controller/AdminInventoryController.java:78` 和 `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/controller/InventoryController.java:26`-`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/controller/InventoryController.java:34`，路径、方法与 201/200 成功状态码匹配。
- 管理接口认证：附录 A 通用约定要求管理接口需要 `ADMIN` 角色，见 `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录A-API接口参考.md:13`；库存管理端控制器使用 `@PreAuthorize("hasRole('ADMIN')")`，见 `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/controller/AdminInventoryController.java:30`。
- 库存校验 Request/Response 字段：附录 A 要求 `skuId`、`quantity` 请求字段和 `skuId`、`available`、`availableStock` 响应字段，见 `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录A-API接口参考.md:159`-`D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录A-API接口参考.md:176`；当前 DTO 字段分别位于 `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/dto/InventoryCheckRequest.java:8`-`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/dto/InventoryCheckRequest.java:12`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/dto/InventoryCheckResponse.java:5`-`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/dto/InventoryCheckResponse.java:7`，名称与 Java/Jackson 输出类型匹配。
- 错误响应结构：附录 A 要求 `code`、`message`、`traceId`、`details`，见 `D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录A-API接口参考.md:15`-`D:/Desktop/work/HW-ICT-CMP-04/design-docs/附录A-API接口参考.md:24`；README 错误码要求见 `D:/Desktop/work/HW-ICT-CMP-04/README.md:200`-`D:/Desktop/work/HW-ICT-CMP-04/README.md:229`；公共错误 DTO 与全局异常处理位于 `D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/dto/ApiError.java:11`-`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/dto/ApiError.java:14`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:25`-`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-common/src/main/java/com/ecommerce/common/exception/GlobalExceptionHandler.java:101`，与结构要求匹配。
