# ecommerce-user - 附录B 一致性检查

## 检查范围
- 模块：ecommerce-user
- 附录：附录B
- 输入资料：
  - `README.md`：比赛边界、修改边界、冻结契约、检查口径相关内容，重点纳入 `README.md:9`、`README.md:26`、`README.md:35`、`README.md:73`、`README.md:237`、`README.md:281`。
  - `design-docs/附录B-配置参考.md` 全文，重点纳入 `design-docs/附录B-配置参考.md:3`、`design-docs/附录B-配置参考.md:22`、`design-docs/附录B-配置参考.md:69`。
  - 当前模块 POM：`code/ecommerce-user/pom.xml`。
  - 整个项目 POM：`code/pom.xml`。
  - 当前模块源文件：`code/ecommerce-user/src/main/java/**/*.java`、`code/ecommerce-user/src/test/java/**/*.java`。
  - 当前模块配置文件：检查 `code/ecommerce-user/src/main/resources`、`code/ecommerce-user/src/test/resources`，当前模块未发现独立 application 配置文件；同时核对 JWT 配置在模块实现中的绑定点 `code/ecommerce-user/src/main/java/com/ecommerce/user/service/JwtTokenProvider.java:26-29`。

## 检查结论
- 未发现不一致。
- 附录B 与 `ecommerce-user` 直接相关的配置项主要是 `security.jwt.issuer`、`security.jwt.secret`、`security.jwt.expire-minutes`（见 `design-docs/附录B-配置参考.md:22-26`）。当前模块的 JWT 配置绑定名称与层级一致：`code/ecommerce-user/src/main/java/com/ecommerce/user/service/JwtTokenProvider.java:26-29` 使用 `@Value("${security.jwt.secret}")`、`@Value("${security.jwt.issuer}")`、`@Value("${security.jwt.expire-minutes}")`。
- 附录B 的“配置默认值”表 `design-docs/附录B-配置参考.md:69-81` 未列出 `security.jwt.*` 默认值要求；因此当前模块未发现与 JWT 默认值相关的直接冲突。
- 当前模块 POM 依赖 JWT 相关库，且父 POM 管理 Spring Boot/JJWT 版本：`code/ecommerce-user/pom.xml:29-45`、`code/pom.xml:28-45`，未发现与附录B配置项名称、层级、绑定或默认值直接冲突的内容。

## 不一致明细
未发现与当前附录相关的实现不一致项。
