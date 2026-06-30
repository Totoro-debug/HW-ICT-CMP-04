# ecommerce-user - 附录C 一致性检查

## 检查范围
- 模块：ecommerce-user
- 附录：附录C
- 输入资料：
  - `README.md` 中比赛边界、修改边界、冻结契约、检查口径相关内容（设计文档是验收基准、公开用例不覆盖全部验收范围等）
  - `design-docs/附录C-数据模型.md` 全文
  - `code/ecommerce-user/src/main/java` 下所有源文件
  - `code/ecommerce-user/src/test/java` 下所有测试源文件
  - `code/ecommerce-user/src/main/resources` 下配置文件（当前模块未发现该目录下配置文件）
  - `code/ecommerce-user/pom.xml`
  - `code/pom.xml`

## 检查结论
- 共发现 2 处不一致

## 不一致明细

### 1. users 表角色字段命名与结构不一致
- 设计要求定位：`design-docs/附录C-数据模型.md:15`
- 代码定位：`code/ecommerce-user/src/main/java/com/ecommerce/user/entity/User.java:33`、`code/ecommerce-user/src/main/java/com/ecommerce/user/entity/User.java:34`、`code/ecommerce-user/src/main/java/com/ecommerce/user/entity/User.java:35`
- 不一致说明：附录 C 要求 `users` 表包含 `roles` 字段，类型为 `VARCHAR`，说明为“角色列表”；当前 `User` 实体实现为单个 `UserRole role` 字段，且未指定 `@Column(name = "roles")`，按 JPA 默认命名映射为 `role` 列。
- 原因分析：设计要求是复数字段 `roles` 且表达角色列表；当前实现是单数 `role` 枚举字段并映射为单列 `role`。这会导致表/实体字段命名不符，并且列表语义未按设计体现，属于命名不符/结构不符。

### 2. user_addresses 表默认地址字段命名不一致
- 设计要求定位：`design-docs/附录C-数据模型.md:30`
- 代码定位：`code/ecommerce-user/src/main/java/com/ecommerce/user/entity/UserAddress.java:36`、`code/ecommerce-user/src/main/java/com/ecommerce/user/entity/UserAddress.java:37`
- 不一致说明：附录 C 要求 `user_addresses` 表默认地址字段为 `default_address`，类型为 `BOOLEAN`；当前 `UserAddress` 实体将布尔字段 `isDefault` 映射为 `@Column(name = "is_default")`。
- 原因分析：设计要求的列名是 `default_address`，当前实现的列名是 `is_default`，字段类型同为布尔但数据库列命名不一致，属于命名不符。
