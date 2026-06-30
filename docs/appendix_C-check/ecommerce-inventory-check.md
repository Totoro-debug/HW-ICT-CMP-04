# ecommerce-inventory - 附录C 一致性检查

## 检查范围
- 模块：ecommerce-inventory
- 附录：附录C
- 输入资料：
  - `README.md` 中比赛边界、修改边界、冻结契约、检查口径相关内容（设计文档为验收基准、公开用例不覆盖全部验收范围等）
  - `design-docs/附录C-数据模型.md` 全文
  - `code/ecommerce-inventory/src/main/java` 下所有源文件
  - `code/ecommerce-inventory/src/test/java` 下所有源文件
  - `code/ecommerce-inventory/src/main/resources`、`src/test/resources`（如存在）
  - `code/ecommerce-inventory/pom.xml`
  - `code/pom.xml`

## 检查结论
- 共发现 3 处不一致

## 不一致明细

### 1. 仓库表名与字段模型不一致：设计为 `warehouses` 且包含 `code`，实现为 `warehouse` 且缺少 `code`
- 设计要求定位：`design-docs/附录C-数据模型.md:58`、`design-docs/附录C-数据模型.md:63`
- 代码定位：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/entity/Warehouse.java:8`、`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/entity/Warehouse.java:9`、`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/entity/Warehouse.java:12`
- 不一致说明：附录C库存域要求仓库数据模型为 `warehouses` 表，并包含 `code` 字段（VARCHAR，仓库编码）。当前 `Warehouse` 实体映射到 `warehouse` 表，实体字段从 `name` 开始，未实现 `code` 字段。
- 原因分析：设计要求明确规定仓库表名为复数 `warehouses`，且字段集合包含 `code`；当前实现使用单数表名 `warehouse`，并缺失仓库编码字段。属于命名不符、缺失。

### 2. 库存表预警阈值字段命名不一致：设计为 `warning_threshold`，实现为 `safety_stock`
- 设计要求定位：`design-docs/附录C-数据模型.md:68`、`design-docs/附录C-数据模型.md:77`
- 代码定位：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/entity/InventoryStock.java:10`、`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/entity/InventoryStock.java:27`
- 不一致说明：附录C要求 `inventory_stock` 表包含 `warning_threshold` 字段（INT，预警阈值）。当前 `InventoryStock` 实体映射表名为 `inventory_stock`，但对应阈值字段映射为 `safety_stock`，未按设计命名为 `warning_threshold`。
- 原因分析：设计要求的数据模型字段名是 `warning_threshold`；当前实现以 `safety_stock` 表达类似含义，但字段命名不符合设计文档。属于命名不符。

### 3. 库存预占表表名不一致：设计为 `stock_reservations`，实现为 `stock_reservation`
- 设计要求定位：`design-docs/附录C-数据模型.md:79`
- 代码定位：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/entity/StockReservation.java:13`、`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/entity/StockReservation.java:14`
- 不一致说明：附录C库存域要求库存预占数据模型表名为 `stock_reservations`。当前 `StockReservation` 实体映射到 `stock_reservation` 表。
- 原因分析：设计要求表名为复数 `stock_reservations`；当前实现使用单数 `stock_reservation`。字段 `order_id`、`sku_id`、`warehouse_id`、`quantity`、`status` 及状态枚举 RESERVED/RELEASED/DEDUCTED 与设计要求一致，但表名不一致。属于命名不符。
