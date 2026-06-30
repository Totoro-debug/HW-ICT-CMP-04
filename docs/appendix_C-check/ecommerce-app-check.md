# ecommerce-app - 附录C 一致性检查

## 检查范围
- 模块：ecommerce-app
- 附录：附录C（数据模型）
- 输入资料：
  - `README.md`：比赛边界、修改边界、冻结契约、检查口径相关内容（设计文档为验收基准；公开用例不覆盖全部验收范围；不得反向修改设计文档；冻结 REST API 契约）
  - `design-docs/附录C-数据模型.md` 全文
  - `code/pom.xml`
  - `code/ecommerce-app/pom.xml`
  - `code/ecommerce-app/src/main/java` 下全部源文件
  - `code/ecommerce-app/src/test/java` 下全部测试源文件
  - `code/ecommerce-app/src/main/resources/application.yml`
  - `code/ecommerce-app/src/test/resources/application-test.yml`

## 检查结论
- 当前模块无与本附录直接相关的检查项；未发现不一致。

## 不一致明细
当前模块无与本附录直接相关的检查项。

说明：附录C列出了业务域数据表/字段/类型/枚举要求，包括 `users`、`user_addresses`、`product_spu`、`product_sku`、`warehouses`、`inventory_stock`、`stock_reservations`、`orders`、`order_items`、`payments`、`refunds`、`invoices`、`coupons`、`shipments`、`loyalty_points`、`reviews`（`design-docs/附录C-数据模型.md:3` 至 `design-docs/附录C-数据模型.md:210`）。`ecommerce-app` 当前直接源文件仅包含应用启动、组件扫描/JPA 扫描配置、安全/CORS/桥接配置和测试支撑管理控制器，未直接声明上述业务表对应的实体、字段类型、状态枚举或表关系；业务实体模型由依赖的业务子模块提供，按本次检查口径不在当前模块重复报告。

相关代码定位：
- `code/ecommerce-app/src/main/java/com/ecommerce/app/ShopHubApplication.java:15` 至 `code/ecommerce-app/src/main/java/com/ecommerce/app/ShopHubApplication.java:18`：应用启动与扫描配置。
- `code/ecommerce-app/src/main/java/com/ecommerce/app/InventoryQueryBridgeConfig.java:13` 至 `code/ecommerce-app/src/main/java/com/ecommerce/app/InventoryQueryBridgeConfig.java:24`：库存查询桥接 DTO 转换，无实体/表结构声明。
- `code/ecommerce-app/src/main/java/com/ecommerce/app/SecurityConfig.java:49` 至 `code/ecommerce-app/src/main/java/com/ecommerce/app/SecurityConfig.java:75`：安全过滤链配置，无实体/表结构声明。
- `code/ecommerce-app/src/main/java/com/ecommerce/app/controller/EventFailureAdminController.java:15` 至 `code/ecommerce-app/src/main/java/com/ecommerce/app/controller/EventFailureAdminController.java:58`、`code/ecommerce-app/src/main/java/com/ecommerce/app/controller/SystemAdminController.java:17` 至 `code/ecommerce-app/src/main/java/com/ecommerce/app/controller/SystemAdminController.java:87` 等：测试/管理支撑接口控制器，无附录C业务数据模型直接实现。
