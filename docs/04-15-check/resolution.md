# 04-15 一致性检查修复方案

## 1. 任务边界

- 设计基准：`design-docs/04-15`，具体为 `design-docs/04-用户服务设计.md` 至 `design-docs/15-本地通知组件设计.md`。
- API 边界：`README.md` 冻结 REST API 契约，禁止破坏 URL、HTTP Method、认证要求、请求/响应字段名与类型、成功状态码、错误响应结构和 `/api/v1/` 前缀。
- 输入报告：`docs/04-15-check/ecommerce-user-check.md`、`ecommerce-product-check.md`、`ecommerce-inventory-check.md`、`ecommerce-cart-check.md`、`ecommerce-order-check.md`、`ecommerce-payment-check.md`、`ecommerce-promotion-check.md`、`ecommerce-logistics-check.md`、`ecommerce-loyalty-check.md`、`ecommerce-review-check.md`、`ecommerce-common-check.md`。
- 处理范围：仅处理报告中明确列出的“不一致点”。
- 非处理范围：代码修改、设计文档修改、报告外 bug；无法确认项默认不纳入 checklist。

## 2. 覆盖汇总

| 模块 | 报告文件 | 不一致点数量 | 已给出方案数量 | 备注 |
|---|---|---:|---:|---|
| 用户服务 | `ecommerce-user-check.md` | 6 | 6 | 已纳入 |
| 商品服务 | `ecommerce-product-check.md` | 5 | 5 | 已纳入 |
| 库存服务 | `ecommerce-inventory-check.md` | 4 | 4 | 已纳入 |
| 购物车服务 | `ecommerce-cart-check.md` | 3 | 3 | 已纳入 |
| 订单服务 | `ecommerce-order-check.md` | 5 | 5 | 已纳入 |
| 支付/退款/发票/结算 | `ecommerce-payment-check.md` | 6 | 6 | 已纳入 |
| 促销服务 | `ecommerce-promotion-check.md` | 5 | 5 | 已纳入 |
| 物流服务 | `ecommerce-logistics-check.md` | 4 | 4 | 已纳入 |
| 积分与会员服务 | `ecommerce-loyalty-check.md` | 4 | 4 | 已纳入 |
| 评价服务 | `ecommerce-review-check.md` | 2 | 2 | 已纳入 |
| 通用模块/本地通知组件 | `ecommerce-common-check.md` | 5 | 5 | 已纳入 |
| **合计** |  | **49** | **49** | 已覆盖全部报告不一致点 |

## 3. 跨模块依赖与修复顺序建议

- 注册激活链路：`R-USER-02`、`R-USER-03`、`R-USER-04` 应作为一个闭环一起修复；先保存 `PENDING_ACTIVATION` 用户和激活令牌，再发送激活邮件，最后由激活接口置为 `ACTIVE`。
- 商品搜索链路：`R-PRODUCT-02` 至 `R-PRODUCT-05` 都涉及 `ProductSearchService`，建议统一改造成分页前组合数据库查询条件，避免在分页结果上做内存过滤。
- 商品 SKU 状态机：`R-PRODUCT-01` 会收紧上下架行为；冻结 REST API 端点不变，只修正状态转换语义。
- 支付后事件链：优先保证 `R-PAYMENT-01` 发布 `OrderPaidEvent`，再落实 `R-INVENTORY-02` 生成出库单和 `R-LOGISTICS-04` 只监听 `OrderPaidEvent` 创建发货单；该链路还影响积分发放、通知发送和“后置动作失败不阻塞支付”的验收。
- 库存预占链：`R-ORDER-01` 的预占前移依赖库存预占引用能力，并与 `R-INVENTORY-03`、`R-INVENTORY-04` 的多仓/单仓优先策略共同决定订单库存分配；`R-ORDER-04` 需保证超时取消释放相同预占。
- 金额计算链：`R-PROMOTION-01`、`R-PROMOTION-03`、`R-PROMOTION-05` 影响优惠金额；`R-CART-03` 透出购物车预估拆分；`R-ORDER-02`、`R-ORDER-03` 决定订单应付金额；支付校验、发票、结算和销售统计都应以修正后的订单金额为准。
- 退款/结算链：`R-PAYMENT-03` 的财务退款完成时间是 `R-PAYMENT-06` 统计退款数据的前置条件；`R-PAYMENT-05`、`R-PAYMENT-06` 应一起处理结算去重和作废重建风险。
- 本地通知链：`R-COMMON-01` 至 `R-COMMON-05` 应同批修复，先迁移业务通知请求字段和渠道，再删除 `subject/content`，最后调整失败隔离和成功日志时机；用户激活邮件 `R-USER-04` 也应遵循同一通知请求模型。

## 4. 详细修复方案

### 4.1 用户服务

#### R-USER-01 `UserProfile` 未完整实现“昵称”字段

- **来源报告**：`docs/04-15-check/ecommerce-user-check.md` 中第 1 个不一致点。
- **报告定位**：`code/ecommerce-user/src/main/java/com/ecommerce/user/entity/UserProfile.java:17-27`；设计依据为 `design-docs/04-用户服务设计.md:14`。
- **问题概述**：`UserProfile` 只有 `userId`、`avatar`、`birthday`、`gender`，缺少设计要求的昵称字段；虽然 `User` 中已有 `nickname`，但领域模型将昵称归入 `UserProfile`。
- **设计依据**：用户服务设计的领域模型表要求 `UserProfile` 包含昵称、头像、生日、性别。
- **修复目标**：`UserProfile` 自身完整承载用户资料字段，同时不破坏现有用户注册/查询响应。
- **建议修复方案**：
  1. 在 `UserProfile` 增加 `nickname` 字段，建议与 `User.nickname` 保持相同长度约束，并补齐 getter/setter。
  2. 保留 `User.nickname`，不要移除或改名，避免影响现有 `UserResponse`、认证和冻结相关逻辑。
  3. 如注册或资料更新流程会创建/维护 `UserProfile`，将 `RegisterRequest.nickname` 或资料更新昵称同步写入 `UserProfile.nickname`。
  4. 若项目关闭自动 DDL，需要为 `user_profiles.nickname` 配套数据库迁移；本任务不修改迁移文件，只提出修复落点。
- **API/兼容性影响**：不修改 README 冻结的 REST URL、方法、请求/响应字段；只新增内部持久化字段。若响应 DTO 已从 `User.nickname` 取值，可继续保持。
- **验证建议**：增加实体或服务测试断言 `UserProfile` 可读写 `nickname`；注册/资料更新后校验资料昵称；运行 `mvn -f code/pom.xml test`。
- **风险与依赖**：`User.nickname` 与 `UserProfile.nickname` 可能出现双写不一致，需在资料更新入口明确同步策略。

#### R-USER-02 注册后用户状态被直接置为 `ACTIVE`，未进入 `PENDING_ACTIVATION`

- **来源报告**：`docs/04-15-check/ecommerce-user-check.md` 中第 2 个不一致点。
- **报告定位**：`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserRegisterService.java:52-58`；设计依据为 `design-docs/04-用户服务设计.md:23-24`、`:30-40`。
- **问题概述**：注册流程保存用户前直接 `user.setStatus(UserStatus.ACTIVE)`，跳过待邮箱激活状态。
- **设计依据**：注册流程必须“保存用户，状态为 `PENDING_ACTIVATION` → 生成邮箱激活令牌 → 发送激活邮件 → 点击激活后变更为 `ACTIVE`”；未激活用户不得登录、不得创建订单。
- **修复目标**：新注册用户默认不可登录，必须通过激活接口后才进入 `ACTIVE`。
- **建议修复方案**：
  1. 将 `UserRegisterService.register` 中的初始状态从 `UserStatus.ACTIVE` 改为 `UserStatus.PENDING_ACTIVATION`。
  2. 保持 `UserAuthService.activate` 在 token 校验通过后将用户状态置为 `ACTIVE`。
  3. 保持或补齐 `UserAuthService.login` 对非 `ACTIVE` 状态的拦截；未激活应返回 README 中的 `USER_NOT_ACTIVE`。
  4. 与 `R-USER-03` 同步实施，避免用户没有激活 token 而永久无法激活。
- **API/兼容性影响**：`POST /api/v1/users/register` 仍为 201，字段名和类型不变；响应中的 `status` 会按设计从当前 `ACTIVE` 修正为 `PENDING_ACTIVATION`。
- **验证建议**：注册后断言状态为 `PENDING_ACTIVATION`；未激活登录返回 HTTP 403、`code=USER_NOT_ACTIVE`；激活后登录成功并返回 JWT。
- **风险与依赖**：依赖 `R-USER-03` 和 `R-USER-04` 完成激活闭环；会影响依赖“注册即登录”的旧测试，但该旧行为不符合设计。

#### R-USER-03 注册流程未生成并保存邮箱激活令牌

- **来源报告**：`docs/04-15-check/ecommerce-user-check.md` 中第 3 个不一致点。
- **报告定位**：`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserRegisterService.java:42-67`；设计依据为 `design-docs/04-用户服务设计.md:16`、`:36`。
- **问题概述**：注册阶段没有创建 `EmailActivationToken`，而激活接口依赖 token 查询，导致邮箱激活闭环缺失。
- **设计依据**：注册流程必须生成邮箱激活令牌；领域模型中包含 `EmailActivationToken`。
- **修复目标**：每次注册成功后都有一条未使用、未过期、可供 `/api/v1/users/activate` 消费的激活令牌。
- **建议修复方案**：
  1. 在 `UserRegisterService` 构造器注入 `EmailActivationTokenRepository`。
  2. 保存 `User` 后创建 `EmailActivationToken`：设置 `userId=saved.getId()`、随机不可预测 `token`、`expiresAt`（建议配置化，默认如 24 小时）、`used=false`。
  3. 调用 `EmailActivationTokenRepository.save(...)` 持久化令牌。
  4. 将 token 传给注册通知构建逻辑，供 `R-USER-04` 放入激活邮件变量。
  5. 保持 `UserAuthService.activate` 使用现有 `findByToken`、过期校验和 `used=true` 标记逻辑。
- **API/兼容性影响**：不改变 `/api/v1/users/register` 或 `/api/v1/users/activate` 的 URL、方法和字段；只补齐服务端状态。
- **验证建议**：注册后通过仓储断言存在 `used=false` 的 token；使用 token 激活后用户为 `ACTIVE` 且 token 置为已使用；重复激活返回冲突类错误。
- **风险与依赖**：token 有效期设计未给出精确值，建议配置化；通知失败不得回滚用户和 token 保存。

#### R-USER-04 注册通知不是设计要求的“激活邮件”

- **来源报告**：`docs/04-15-check/ecommerce-user-check.md` 中第 4 个不一致点。
- **报告定位**：`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserRegisterService.java:63-80`、`code/ecommerce-user/src/main/java/com/ecommerce/user/event/UserRegistrationNotificationListener.java:33-39`；设计依据为 `design-docs/04-用户服务设计.md:36-38`。
- **问题概述**：当前通知使用 `USER_REGISTERED` 语义，变量中没有激活 token 或激活链接，不能作为激活邮件。
- **设计依据**：注册必须通过 `LocalNotificationService` 发送激活邮件。
- **修复目标**：注册通知明确成为激活邮件，接收人可从通知变量获得激活 token/链接。
- **建议修复方案**：
  1. 将 `buildRegistrationNotification` 或对应事件构建逻辑调整为激活邮件语义，例如 `bizType=EMAIL_ACTIVATION`、`templateCode=EMAIL_ACTIVATION` 或项目统一命名。
  2. 扩展 `UserRegistrationNotificationEvent` 或 `NotificationRequest.variables`，加入 `activationToken`，可选加入 `activationLink`。
  3. 激活链接只作为通知变量生成，不改变 README 冻结的激活接口契约。
  4. 继续由 `UserRegistrationNotificationListener` 调用 `LocalNotificationService.send(...)`，保持本地通知组件统一入口。
- **API/兼容性影响**：不改变 REST API；内部通知模板编码和变量变化。若已有模板配置依赖 `USER_REGISTERED`，需同步改为激活模板。
- **验证建议**：注册后查询通知记录，断言 `templateCode` 为激活模板，变量包含可用 token 或链接；故障注入通知失败时注册仍成功且失败原因被记录。
- **风险与依赖**：强依赖 `R-USER-03`；激活链接域名/路径建议配置化，避免硬编码环境地址。

#### R-USER-05 地址格式化方法签名参数顺序与设计不一致

- **来源报告**：`docs/04-15-check/ecommerce-user-check.md` 中第 5 个不一致点。
- **报告定位**：`code/ecommerce-user/src/main/java/com/ecommerce/user/service/AddressFormatter.java:20-21`；设计依据为 `design-docs/04-用户服务设计.md:57-69`。
- **问题概述**：当前签名为 `format(String city, String province, String district, String detail)`，与设计要求的 `format(String province, String city, String district, String detail)` 相反。
- **设计依据**：地址格式化签名和参数顺序不得调整，输出为“省 + 市 + 区 + 详细地址”。
- **修复目标**：内部方法签名和所有调用点都按省、市、区、详细地址传参。
- **建议修复方案**：
  1. 修改 `AddressFormatter.format` 方法声明和 JavaDoc 为 `format(String province, String city, String district, String detail)`。
  2. 方法体保持 `province + city + district + detail`。
  3. 人工复核所有 `AddressFormatter.format(...)` 调用点，尤其 `AddressService`、`UserQueryServiceImpl` 或测试中的调用，保证传参顺序同步调整。
  4. 由于四个参数都是 `String`，不能依赖编译器发现顺序错误，必须用测试覆盖。
- **API/兼容性影响**：不改变 REST 请求/响应字段；仅修正内部工具方法语义。
- **验证建议**：单测 `format("广东省", "深圳市", "南山区", "科技园")` 应返回 `广东省深圳市南山区科技园`；创建地址并查询，断言 `province/city/district/detail` 不互换。
- **风险与依赖**：所有参数同类型，遗漏调用点会导致省市互换且编译通过。

#### R-USER-06 冻结用户登录错误码正确，但用户服务代码未明确保证 HTTP 403

- **来源报告**：`docs/04-15-check/ecommerce-user-check.md` 中第 6 个不一致点。
- **报告定位**：`code/ecommerce-user/src/main/java/com/ecommerce/user/service/UserAuthService.java:71-75`；设计依据为 `design-docs/04-用户服务设计.md:47-53`。
- **问题概述**：冻结登录抛出 `BusinessException("USER_FROZEN", ...)`，错误码正确，但若通用业务异常默认映射 400，则不能满足 403 要求。
- **设计依据**：冻结用户登录必须返回 HTTP 403，错误码 `USER_FROZEN`；README 错误码表也规定 `USER_FROZEN`、`USER_NOT_ACTIVE` 为 403。
- **修复目标**：冻结和未激活登录均通过统一错误响应返回 403，同时保留错误码。
- **建议修复方案**：
  1. 在 `UserAuthService.login` 对 `FROZEN` 和 `PENDING_ACTIVATION`/非 `ACTIVE` 状态抛出可映射为 403 且可保留业务 code 的异常，例如使用或扩展 `AuthorizationException("USER_FROZEN", ...)`、`AuthorizationException("USER_NOT_ACTIVE", ...)`。
  2. 若现有 `AuthorizationException` 不支持自定义业务 code，应扩展该异常及全局异常处理器对该异常类型的处理能力，而不是把所有 `BusinessException` 改成 403。
  3. 保持 `UserController.login` 不手写异常转换，继续使用全局异常处理。
  4. 不改变错误响应结构，只修正 HTTP 状态。
- **API/兼容性影响**：登录 URL、方法和响应结构不变；冻结/未激活登录状态码从可能的 400 修正为 403，符合 README 冻结契约。
- **验证建议**：冻结用户登录返回 HTTP 403 且 `code=USER_FROZEN`；未激活用户登录返回 HTTP 403 且 `code=USER_NOT_ACTIVE`；密码错误仍保持既有认证失败行为。
- **风险与依赖**：若修改全局异常映射，需回归其他模块业务异常，避免误把普通业务错误改为 403。

### 4.2 商品服务

#### R-PRODUCT-01 SKU 状态机未完整覆盖 DELETED 转换，且上下架转换约束过宽

- **来源报告**：`docs/04-15-check/ecommerce-product-check.md` 中第 1 个不一致点。
- **报告定位**：`code/ecommerce-product/src/main/java/com/ecommerce/product/entity/SkuStatus.java:8`、`code/ecommerce-product/src/main/java/com/ecommerce/product/service/SkuService.java:108`、`:125`；设计依据为 `design-docs/05-商品服务设计.md:18`。
- **问题概述**：`SkuStatus` 声明 `DELETED`，但服务层没有删除/转为 `DELETED` 的入口；`onShelf/offShelf` 仅禁止 `DELETED`，状态转换过宽。
- **设计依据**：设计列出 `DRAFT`、`ON_SHELF`、`OFF_SHELF`、`DELETED`，应作为 SKU 生命周期状态机管理。
- **修复目标**：让 `DELETED` 可达，并让上下架只按设计状态机允许的路径流转。
- **建议修复方案**：
  1. 在 `SkuService.onShelf(Long skuId)` 中仅允许 `DRAFT -> ON_SHELF`；其他状态返回状态冲突或校验错误。
  2. 在 `SkuService.offShelf(Long skuId)` 中仅允许 `ON_SHELF -> OFF_SHELF`。
  3. 新增服务层方法如 `deleteSku(Long skuId)`：允许 `DRAFT -> DELETED`、`OFF_SHELF -> DELETED`，禁止 `ON_SHELF -> DELETED` 和重复删除。
  4. 删除转换后保存 SKU、记录审计日志（如项目已有审计服务）并清理商品详情缓存。
  5. 冻结 API 未列 SKU 删除端点；如不确认允许新增 REST，先只落地服务层状态机，不修改 README 中既有商品 API。
- **API/兼容性影响**：既有上架/下架 URL、Method、字段不变；行为更严格。新增删除 REST 入口可能超出冻结基线，应谨慎，必要时仅内部服务层支持。
- **验证建议**：单测覆盖 `DRAFT -> ON_SHELF`、`ON_SHELF -> OFF_SHELF`、`OFF_SHELF/DRAFT -> DELETED` 成功，以及 `DRAFT -> OFF_SHELF`、`OFF_SHELF -> ON_SHELF`、`DELETED -> ON_SHELF` 失败；运行 `mvn -f code/pom.xml test`。
- **风险与依赖**：若旧业务依赖下架后重新上架，会受到影响；但报告和设计以状态机约束为准。新增 REST 入口需避免破坏冻结契约。

#### R-PRODUCT-02 商品搜索 keyword 未覆盖卖点模糊匹配

- **来源报告**：`docs/04-15-check/ecommerce-product-check.md` 中第 2 个不一致点。
- **报告定位**：`code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java:101`；设计依据为 `design-docs/05-商品服务设计.md:46`。
- **问题概述**：keyword 只匹配 `ProductSku.name`，未匹配 SPU 名称或卖点。
- **设计依据**：商品搜索 `keyword` 应支持商品名称、卖点模糊匹配。
- **修复目标**：按 SKU 名称、SPU 名称及现有卖点承载字段进行模糊搜索。
- **建议修复方案**：
  1. 复用当前源码中的 `ProductSpu.description` 作为“卖点/描述”落点；若 `ProductSpu.name` 也存在，一并纳入商品名称匹配。
  2. 在 `ProductSearchService.buildSpecification` 中将 keyword 条件扩展为 `ProductSku.name like ? OR ProductSku.spuId in (匹配 ProductSpu.name/ProductSpu.description 的子查询)`。
  3. 保持 `lower + like` 的大小写不敏感风格。
  4. 该过滤必须在 `skuRepository.findAll(spec, pageRequest)` 前完成，不能分页后内存过滤。
- **API/兼容性影响**：不改变 `/api/v1/products/search` 参数或响应；只扩大 keyword 命中范围。
- **验证建议**：构造 SKU 名称不含 keyword、SPU 描述含 keyword 的商品，搜索应命中；构造 SPU 名称含 keyword 的商品也应命中；`onlyOnShelf=true` 时仍只返回上架 SKU。
- **风险与依赖**：当前无独立卖点字段，使用 `description` 是符合代码现状的可落地映射；后续若新增卖点字段需再扩展查询。

#### R-PRODUCT-03 商品搜索 categoryId 未包含子类目，且在分页后过滤会导致结果不完整

- **来源报告**：`docs/04-15-check/ecommerce-product-check.md` 中第 3 个不一致点。
- **报告定位**：`code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java:65`、`:77`、`:121`；设计依据为 `design-docs/05-商品服务设计.md:47`。
- **问题概述**：类目过滤只比较 SPU 当前 `categoryId`，不包含子类目，并且在分页后过滤导致结果和总数不准确。
- **设计依据**：商品搜索 `categoryId` 过滤必须包含子类目。
- **修复目标**：按当前类目及全部后代类目在数据库分页前过滤 SKU。
- **建议修复方案**：
  1. 为 `ProductSearchService` 注入 `CategoryRepository`。
  2. 根据请求 `categoryId` 递归调用 `CategoryRepository.findByParentId(...)`，收集当前类目和全部后代类目 ID，使用 `visited` 防止环。
  3. 在 `buildSpecification` 中通过 SPU 子查询或先查 SPU ID，将 `ProductSku.spuId` 限制为 `ProductSpu.categoryId in collectedCategoryIds`。
  4. 删除分页后的 `matchesCategory` 内存过滤，确保 `totalElements` 和当前页内容均是过滤后的结果。
  5. 类目不存在时建议返回空页，而不是忽略过滤条件。
- **API/兼容性影响**：搜索参数和响应结构不变；修复后父类目可命中子类目商品，分页总数也会按过滤后计算。
- **验证建议**：构造父/子/孙类目，商品绑定子或孙类目，按父类目搜索应返回；构造多页数据验证第一页不会因分页后过滤而漏结果。
- **风险与依赖**：依赖类目树数据正确；与 `R-PRODUCT-04`、`R-PRODUCT-02` 共同改造查询条件时需避免重复子查询造成性能问题。

#### R-PRODUCT-04 商品搜索 brandId 在分页后过滤，不能完整实现品牌过滤条件

- **来源报告**：`docs/04-15-check/ecommerce-product-check.md` 中第 4 个不一致点。
- **报告定位**：`code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java:65`、`:78`、`:132`；设计依据为 `design-docs/05-商品服务设计.md:48`。
- **问题概述**：`brandId` 未进入数据库查询条件，而是在 SKU 分页后内存过滤，导致漏数和 `totalElements` 错误。
- **设计依据**：商品搜索支持品牌过滤。
- **修复目标**：品牌条件参与分页前数据库查询，与其他搜索条件取交集。
- **建议修复方案**：
  1. 将 `request.getBrandId()` 条件移入 `ProductSearchService.buildSpecification`。
  2. 通过 SPU 子查询实现：`ProductSku.spuId in (select ProductSpu.id where ProductSpu.brandId = :brandId)`。
  3. 删除分页后 `matchesBrand` 过滤。
  4. 与 category、keyword、price、status 等条件在同一个 `Specification` 中组合。
- **API/兼容性影响**：不改变搜索 API；结果总数和分页内容从“未真正按品牌分页”修正为设计期望行为。
- **验证建议**：构造多个品牌商品，指定 `brandId` 只返回对应品牌；构造其他品牌数据排在前页的场景，验证目标品牌不会被漏掉。
- **风险与依赖**：若先查 SPU ID 再用 `IN`，大量 SPU 可能影响 SQL 参数数量；推荐 Criteria 子查询。

#### R-PRODUCT-05 商品搜索 tags 条件只有 DTO 字段，没有实际过滤实现

- **来源报告**：`docs/04-15-check/ecommerce-product-check.md` 中第 5 个不一致点。
- **报告定位**：`code/ecommerce-product/src/main/java/com/ecommerce/product/dto/ProductSearchRequest.java:24`、`code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductSearchService.java:89`；设计依据为 `design-docs/05-商品服务设计.md:50`。
- **问题概述**：请求 DTO 有 `tags` 字段，但搜索逻辑完全未读取，也没有基于 `ProductTag` 的过滤。
- **设计依据**：商品搜索必须支持 tags 标签过滤。
- **修复目标**：传入 tags 时只返回绑定相应标签的商品，并且分页总数正确。
- **建议修复方案**：
  1. 复核当前 `ProductTag` 实体和仓储；如缺少商品-标签关联模型，新增内部实体如 `ProductSpuTag(spuId, tagId)` 及 `ProductSpuTagRepository`。
  2. 扩展 `ProductTagRepository`，支持按标签名称批量查询 tag ID。
  3. 在 `ProductSearchService` 处理非空 `request.getTags()`：先将名称解析为 tag ID；无匹配时返回空页；有匹配时通过关联表筛选 SPU，再约束 `ProductSku.spuId`。
  4. 固定标签过滤语义为“匹配任一标签”：请求 tags 中任一标签与 SPU 绑定标签相同即命中；该语义兼容性更好，并应在测试中固定。
  5. 标签过滤同样必须在分页前完成。
- **API/兼容性影响**：`tags` 字段已存在，不改变 API 字段名和类型；需要新增内部表/实体或使用已有关联数据。
- **验证建议**：构造标签和商品关联，搜索 `tags=HOT` 返回绑定商品；不存在标签返回空页；与品牌、类目、上架状态组合时分页总数正确。
- **风险与依赖**：当前源码若没有商品-标签关联表，这是主要实现依赖；新增表需配合 JPA 自动建表或迁移策略。

### 4.3 库存服务

#### R-INVENTORY-01 库存充足判断使用了 `>`，不符合 `availableStock >= requestQuantity`

- **来源报告**：`docs/04-15-check/ecommerce-inventory-check.md` 中第 1 个不一致点。
- **报告定位**：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryService.java:68`、`:71`；设计依据为 `design-docs/06-库存服务设计.md:27`。
- **问题概述**：`InventoryService.checkAvailability(Long skuId, int quantity)` 使用 `totalAvailable > quantity`，导致可用库存刚好等于请求数量时被误判为不足。
- **设计依据**：库存设计规定 `availableStock = onHandStock - reservedStock`，且 `availableStock >= requestQuantity` 时库存充足。
- **修复目标**：库存可用性校验按“大于等于”判断，并影响 REST 库存检查和跨模块库存校验。
- **建议修复方案**：
  1. 将 `InventoryService.checkAvailability` 中 `totalAvailable > quantity` 改为 `totalAvailable >= quantity`。
  2. 保持 `checkAndReport(Long skuId, int quantity)`、`InventoryQueryService.checkAvailability` 和 `/api/v1/inventory/check` 的签名与响应结构不变。
  3. 增加边界测试，覆盖可用库存等于请求数量的场景。
- **API/兼容性影响**：不修改 README 冻结的库存 API；只修正边界行为，原先错误返回 `available=false` 的等量场景会变为 `true`。
- **验证建议**：构造可用库存 10、请求数量 10，调用 `POST /api/v1/inventory/check` 断言 `available=true`；运行 `mvn -f code/pom.xml test` 和必要的黑盒库存/购物车/订单链路测试。
- **风险与依赖**：风险低；可能影响依赖旧错误行为的测试，但设计和库存公式要求以新行为为准。

#### R-INVENTORY-02 支付后扣减流程未生成 `OutboundOrder`

- **来源报告**：`docs/04-15-check/ecommerce-inventory-check.md` 中第 2 个不一致点。
- **报告定位**：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java:125`、`:135`、`:136`、`:139`；设计依据为 `design-docs/06-库存服务设计.md:40-48`。
- **问题概述**：`deductAfterPayment(Long orderId)` 只扣减 `onHandStock`、`reservedStock` 并将预占记录置为 `DEDUCTED`，没有创建或保存 `OutboundOrder`。
- **设计依据**：支付成功后扣减库存流程必须“查找预占记录 → 减少 `onHandStock` → 减少 `reservedStock` → 生成 `OutboundOrder`”。
- **修复目标**：每条支付后扣减的预占记录都生成对应出库单，且事件重试时不重复生成。
- **建议修复方案**：
  1. 在 `InventoryReservationServiceImpl` 注入 `OutboundOrderRepository`。
  2. 在 `deductAfterPayment(Long orderId)` 遍历 `RESERVED` 预占记录时，在同一事务内完成库存扣减、预占状态更新和 `OutboundOrder` 创建。
  3. 出库单字段建议与 `InventoryService.outbound` 的手工出库逻辑保持一致：`warehouseId` 取预占仓、`skuId` 取预占 SKU、`quantity` 取预占数量、`orderId` 取当前订单、`status` 使用现有出库单约定状态（如 `COMPLETED`）。
  4. 保存前根据 `{orderId, skuId, warehouseId}` 查询或利用唯一约束做幂等保护，重复事件到达时跳过已存在的出库单，而不是抛出唯一约束异常影响事件处理。
  5. 保留现有 `StockReservation.status=DEDUCTED` 逻辑和 `InventoryOrderPaidEventListener` 监听入口。
- **API/兼容性影响**：不新增、不删除、不修改 REST API；支付后会新增内部 `OutboundOrder` 数据记录，符合设计要求。
- **验证建议**：支付成功后断言库存扣减、预占状态 `DEDUCTED`、存在 `OutboundOrder` 且字段与预占记录一致；重复发布 `OrderPaidEvent` 或重复调用扣减逻辑时不重复生成出库单。
- **风险与依赖**：依赖支付模块按设计发布 `OrderPaidEvent`（关联 `R-PAYMENT-01`）；必须处理幂等，否则事件重试可能导致唯一约束异常或重复出库单。

#### R-INVENTORY-03 多仓分配未实现省份匹配、距离、仓库优先级排序

- **来源报告**：`docs/04-15-check/ecommerce-inventory-check.md` 中第 3 个不一致点。
- **报告定位**：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java:52-64`、`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/repository/InventoryStockRepository.java:15`；设计依据为 `design-docs/06-库存服务设计.md:57-65`。
- **问题概述**：预占库存按 `InventoryStockRepository.findBySkuId(...)` 返回顺序遍历，没有使用用户省份、仓库服务区域、距离或仓库优先级。
- **设计依据**：库存分配优先级为用户默认地址所在省份匹配仓库服务区域、可用库存充足、距离优先、仓库优先级。
- **修复目标**：预占前构造稳定的候选仓排序，不再依赖数据库自然返回顺序。
- **建议修复方案**：
  1. 在 `InventoryReservationServiceImpl` 引入 `WarehouseRepository`，根据 `InventoryStock.warehouseId` 加载 `Warehouse` 信息。
  2. 构造候选对象，包含 `InventoryStock`、`Warehouse`、可用库存、服务区域/省份匹配结果和优先级。
  3. 省份匹配：由订单模块在预占调用上下文传递用户默认地址省份，或为内部 `ReserveItem` 增加可选 `province/addressProvince` 字段；旧调用方不传时降级为仅按库存和优先级排序。匹配时优先检查 `Warehouse.serviceRegions`，为空时可降级使用 `Warehouse.province`。
  4. 可用库存充足：候选仓按 `availableStock` 是否可满足当前 SKU 数量进行排序或优先筛选。
  5. 距离优先：当前源码未提供仓库/地址经纬度或距离服务，不能编造字段；短期以省份/服务区域命中作为距离近似，并在代码中预留独立距离计算扩展点。
  6. 仓库优先级：使用 `Warehouse.priority` 作为最终排序字段，空值按最低优先级处理。
  7. 将 `reserve` 中原始列表遍历替换为排序后的候选列表遍历。
- **API/兼容性影响**：不改变 REST API；`InventoryReservationService.reserve(Long orderId, List<ReserveItem> items)` 可保持签名不变。如为 `ReserveItem` 增加可选字段，是内部 DTO 扩展，需保证旧调用方仍可编译和运行。
- **验证建议**：构造多个仓库：服务区域命中但优先级低、不命中但优先级高，断言优先选择服务区域命中仓；构造同区域仓库时按 `priority` 稳定排序；无省份输入时仍能稳定预占。
- **风险与依赖**：依赖订单/用户地址链路提供省份；真实距离排序受当前数据模型限制，只能降级实现或新增坐标/距离服务作为后续实现依赖。

#### R-INVENTORY-04 同一订单同一 SKU 未显式优先选择可满足数量的单仓

- **来源报告**：`docs/04-15-check/ecommerce-inventory-check.md` 中第 4 个不一致点。
- **报告定位**：`code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/service/InventoryReservationServiceImpl.java:52-80`；设计依据为 `design-docs/06-库存服务设计.md:66`。
- **问题概述**：当前从第一个可用仓开始按 `Math.min(remaining, available)` 拆分预占，即使后续存在一个仓库可完整满足，也可能先拆仓。
- **设计依据**：单个 SKU 可拆分到多个仓库，但同一订单的同一 SKU 优先单仓发出。
- **修复目标**：存在可满足整项数量的单仓时只创建一条预占记录；没有单仓满足时才拆仓。
- **建议修复方案**：
  1. 与 `R-INVENTORY-03` 共用排序后的候选仓列表。
  2. 每个 `ReserveItem` 处理时，先查找排序后第一个 `availableStock >= item.quantity` 的仓库。
  3. 如存在单仓候选，一次性创建一条 `StockReservation`，增加该仓 `reservedStock`，并跳过拆仓逻辑。
  4. 如不存在单仓候选，再按排序结果逐仓拆分预占。
  5. 保持库存不足时抛出 `INVENTORY_NOT_ENOUGH` 的现有行为，保持订单级幂等判断 `matchesExistingReservation`。
- **API/兼容性影响**：不修改 REST API 或库存预占服务签名；只改变预占仓库分配结果，使其符合设计。
- **验证建议**：仓库 A 可用 3、仓库 B 可用 10、请求数量 10 时，只预占 B；仓库 A 可用 3、B 可用 7、请求 10 时允许拆仓；多个仓库都可满足时选择 `R-INVENTORY-03` 排序最优仓；重复预占不重复增加 `reservedStock`。
- **风险与依赖**：强依赖 `R-INVENTORY-03` 的候选仓排序；需回归释放预占、支付后扣减和重复事件场景。

### 4.4 购物车服务

#### R-CART-01 同一 SKU 重复加入时未按设计要求累加数量

- **来源报告**：`docs/04-15-check/ecommerce-cart-check.md` 中第 1 个不一致点。
- **报告定位**：`code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:78`、`:82`；设计依据为 `design-docs/07-购物车服务设计.md:25`。
- **问题概述**：`CartService.addItem` 在已有购物车项分支中直接 `item.setQuantity(request.getQuantity())`，覆盖旧数量而不是累加。
- **设计依据**：同一个 SKU 重复加入购物车时，数量累加。
- **修复目标**：重复加入同 SKU 后购物车数量为原数量加本次数量，并以累加后数量做范围和库存校验。
- **建议修复方案**：
  1. 在 `existingItem.isPresent()` 分支计算 `newQuantity = existingQuantity + request.getQuantity()`。
  2. 对 `newQuantity` 调用 `CartValidationService.validateQuantity(newQuantity)`，确保仍在 1 到 999。
  3. 对 `newQuantity` 调用 `CartValidationService.validateStock(skuId, newQuantity)`，确保最终数量不超过库存。
  4. 将 `item.setQuantity(request.getQuantity())` 改为 `item.setQuantity(newQuantity)`；新增 SKU 分支保持现有最大种类等校验。
- **API/兼容性影响**：不改变 `POST /api/v1/cart/items` 的 URL、方法、请求或响应字段；重复添加行为从覆盖修正为累加，符合设计和 README 公开用例 `PUB-006`。
- **验证建议**：已有数量 2 再添加 3，断言数量 5；已有 998 再添加 2 返回 `VALIDATION_FAILED`；已有 2、库存 4、再添加 3 返回 `INVENTORY_NOT_ENOUGH`。
- **风险与依赖**：依赖 `CartValidationService` 对累加后数量执行统一校验；注意重复添加时库存校验必须使用总量而不是本次数量。

#### R-CART-02 购物车库存展示未体现通过 `InventoryQueryService` 查询的库存信息

- **来源报告**：`docs/04-15-check/ecommerce-cart-check.md` 中第 2 个不一致点。
- **报告定位**：`code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:248`、`code/ecommerce-cart/src/main/java/com/ecommerce/cart/dto/CartItemResponse.java:10`、`code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartValidationService.java:62`；设计依据为 `design-docs/07-购物车服务设计.md:22`。
- **问题概述**：库存只在校验中查询，购物车查询响应没有库存展示字段，也未在构建响应时查询库存摘要。
- **设计依据**：购物车服务负责库存摘要展示，库存展示通过 `InventoryQueryService` 查询。
- **修复目标**：查询购物车时，每个购物车项能展示来自库存服务的可用库存或库存摘要。
- **建议修复方案**：
  1. 在 `CartItemResponse` 增加库存展示字段，建议最小化为 `availableStock`、`reservedStock`，类型与库存摘要 DTO 保持一致；保留既有字段和构造器。
  2. 在 `CartService` 直接注入 `InventoryQueryService`，或新增只读库存查询协作方法；建议由 `CartService` 直接查询，避免把展示职责混入 `CartValidationService`。
  3. 修改 `buildCartResponse` 或 `toCartItemResponse`，对每个 `CartItemData.skuId` 调用 `InventoryQueryService.getStockSummary(skuId)` 并填充库存字段。
  4. 库存摘要为空或查询失败时，建议记录日志并返回 `null`/0，避免查询购物车因展示库存失败而整体不可用；但库存校验仍保持原有严格逻辑。
- **API/兼容性影响**：不改变 URL、方法和既有字段；会为 `GET /api/v1/cart` item 增加响应字段。README 冻结契约要求不破坏字段名和类型，因此实现时必须保留既有字段，不得改名或删除。
- **验证建议**：mock `InventoryQueryService.getStockSummary` 返回可用库存和预占库存，查询购物车断言 item 库存字段正确；通过入库后添加购物车并 REST 查询，断言展示库存与库存摘要一致。
- **风险与依赖**：依赖库存模块 Bean；购物车最多 100 个 SKU，逐项查询可能带来性能压力，后续可按批量接口优化。

#### R-CART-03 价格预估返回字段不完整，缺少优惠券可用列表和会员折扣等设计字段

- **来源报告**：`docs/04-15-check/ecommerce-cart-check.md` 中第 3 个不一致点。
- **报告定位**：`code/ecommerce-cart/src/main/java/com/ecommerce/cart/dto/CartEstimateResponse.java:10`、`:15`，`code/ecommerce-cart/src/main/java/com/ecommerce/cart/service/CartService.java:212`、`:218`；设计依据为 `design-docs/07-购物车服务设计.md:29`、`:31`、`:36`。
- **问题概述**：`CartEstimateResponse` 只有总优惠 `discountAmount` 等字段，缺少满减优惠、优惠券可用列表和会员折扣拆分。
- **设计依据**：购物车价格预估返回商品原价合计、满减优惠、优惠券可用列表、会员折扣、预计运费、预计应付金额。
- **修复目标**：在保留当前兼容字段的同时，补齐设计要求的优惠拆分和可用券列表。
- **建议修复方案**：
  1. 扩展 `CartEstimateResponse`，新增 `fullReductionDiscount`、`memberDiscount`、`applicableCoupons` 等字段；保留 `discountAmount` 作为总优惠兼容字段。
  2. 新增购物车侧可用券 DTO，字段可映射促销模块已有响应，如 `couponId`、`couponCode`、`name`、`discountAmount`，避免直接暴露促销内部类型。
  3. 将 `CartService.calculateDiscountAmount` 调整为返回完整 `PromotionCalculateResponse` 或在 `estimate` 中直接接收完整促销结果。
  4. 从促销结果中填充 `fullReductionDiscount`、`memberDiscount`、`applicableCoupons`，继续用 `totalDiscount` 填充既有 `discountAmount` 并参与 `payableAmount` 计算。
  5. `emptyEstimateResponse` 同步初始化新增金额字段为 `BigDecimal.ZERO`、列表为空列表；所有金额做空值归零和分位舍入。
- **API/兼容性影响**：`POST /api/v1/cart/estimate` 的 URL、方法、请求字段和既有响应字段保持不变；新增字段用于满足设计。不得删除 README 公开用例依赖的 `itemTotal`、`shippingFee`、`discountAmount`、`payableAmount`。
- **验证建议**：mock `PromotionCalculationService.calculate` 返回满减 10、会员折扣 5、总优惠 15、可用券列表，断言购物车预估响应完整填充且 `discountAmount=15`；促销字段为空时响应为 0/空列表。
- **风险与依赖**：依赖促销模块拆分字段正确填充；新增响应字段需在不破坏 README 冻结契约的前提下补齐。

### 4.5 订单服务

#### R-ORDER-01 下单流程中库存预占顺序不符合设计流程

- **来源报告**：`docs/04-15-check/ecommerce-order-check.md` 中第 1 个不一致点。
- **报告定位**：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java:193`、`:299`；设计依据为 `design-docs/08-订单服务设计.md:32-33`。
- **问题概述**：设计要求先校验并预占库存，再执行风控；当前 `OrderService.createOrder` 先做风控，并在订单和明细保存后才调用 `inventoryReservationService.reserve(...)`。
- **设计依据**：下单流程第 4 步为“校验库存可用并预占库存”，第 5 步为“执行风控校验，高风险订单拒绝”。
- **修复目标**：库存可用校验与预占发生在风控前；风控或后续步骤失败时库存不泄漏。
- **建议修复方案**：
  1. 在 `OrderService.createOrder` 中完成用户、商品、购物车/直接购买项校验和 `ReserveItem` 组装后，将库存预占逻辑前移到风控前。
  2. 扩展库存模块内部预占契约，新增不破坏 REST API 的“业务预占引用”能力，例如 `reserveByReference(String reservationRef, List<ReserveItem> items)`，`reservationRef` 使用订单号或创建请求中的稳定业务键；订单实体持久化成功后再通过内部方法将预占记录绑定真实 `orderId`。
  3. 风控拒绝或后续计算失败时，使用同一 `reservationRef` 调用库存释放逻辑释放预占；订单成功持久化并绑定后，再按 `orderId` 维持现有释放、支付扣减等后续流程。
  4. `OrderCreatedEvent` 仍应在订单和明细完整创建成功、预占记录已绑定真实订单后发布，不得因预占前移而提前发布。
  5. 对异常路径补充补偿逻辑，特别是风控拒绝、优惠计算失败、积分抵扣失败等发生在预占之后的路径。
- **API/兼容性影响**：不修改 `POST /api/v1/orders/create` 的 URL、Method、请求/响应字段和 201 成功状态；如调整库存预占接口，仅属于模块内部 Java 契约变更，不影响 README 冻结 REST API。
- **验证建议**：库存不足订单应在风控前失败且不生成有效订单；高风险但库存充足订单应释放预占；成功订单保持 `CREATED` 且库存已预占；回归 README `PUB-008`、`PUB-103`、`PUB-106`。
- **风险与依赖**：依赖库存模块预占接口可支持预占前移；异常补偿不完整会造成库存悬挂，与 `R-INVENTORY-03`、`R-INVENTORY-04` 的预占策略有关联。

#### R-ORDER-02 订单总价计算公式遗漏运费

- **来源报告**：`docs/04-15-check/ecommerce-order-check.md` 中第 2 个不一致点。
- **报告定位**：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderTotalCalculator.java:78`；设计依据为 `design-docs/08-订单服务设计.md:47`。
- **问题概述**：`OrderTotalCalculator.calculate(...)` 接收 `shippingFee`，但应付金额只从 `itemTotal + packagingFee` 开始计算，没有加运费。
- **设计依据**：订单总价 = 商品总金额 + 运费 + 包装费 - 优惠抵扣金额 - 积分抵扣金额。
- **修复目标**：非包邮订单的 `payableAmount` 正确包含运费。
- **建议修复方案**：
  1. 在 `OrderTotalCalculator.calculate(...)` 中按 `itemTotal + shippingFee + packagingFee - discountAmount - pointsDeductionAmount` 计算。
  2. 保持现有空值兜底逻辑，`shippingFee` 为 null 时按 0 处理。
  3. 与 `R-ORDER-03` 合并实现时，完整公式计算后再做 0.01 下限和最终分位舍入。
  4. 回归 `OrderService` 中写入订单的 `shippingFee`、`packagingFee`、`discountAmount`、`pointsDeductionAmount` 和 `payableAmount` 一致性。
- **API/兼容性影响**：不修改 API 字段；`payableAmount` 数值会修正为包含运费，影响支付金额校验、发票、销售统计等下游链路，但符合设计和 README `PUB-104`。
- **验证建议**：构造商品金额低于 199 且运费 8.00 的订单，断言 `payableAmount=itemTotal+shippingFee+packagingFee-discountAmount-pointsDeductionAmount`；回归支付单创建金额一致。
- **风险与依赖**：历史已创建订单金额不会自动修正；支付模块必须以修正后的订单应付金额校验。

#### R-ORDER-03 金额最终未统一使用 HALF_UP 四舍五入到分

- **来源报告**：`docs/04-15-check/ecommerce-order-check.md` 中第 3 个不一致点。
- **报告定位**：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderTotalCalculator.java:78`、`code/ecommerce-common/src/main/java/com/ecommerce/common/money/MonetaryUtil.java:34`；设计依据为 `design-docs/08-订单服务设计.md:52`。
- **问题概述**：公共工具已有 `MonetaryUtil.roundToCent(...)` 使用 `RoundingMode.HALF_UP`，但订单总价计算返回前未调用。
- **设计依据**：所有金额最终使用 `RoundingMode.HALF_UP` 四舍五入到分。
- **修复目标**：订单最终应付金额和写入/返回的金额边界稳定为两位小数。
- **建议修复方案**：
  1. 在 `OrderTotalCalculator.calculate(...)` 完成完整公式和 0.01 下限处理后，返回前调用 `MonetaryUtil.roundToCent(payableAmount)`。
  2. 避免在中间步骤过早四舍五入，减少累计误差；仅在最终输出边界落分。
  3. 复核 `OrderService` 写入订单字段时是否存在其他多位小数金额，至少确保 `payableAmount` 最终落分。
- **API/兼容性影响**：不修改字段名和类型；金额值会稳定为两位小数，符合 REST 黑盒比较和支付金额校验预期。
- **验证建议**：构造三位小数折扣/积分抵扣场景，断言 `payableAmount` 按 HALF_UP 两位；构造计算结果小于 0.01 的场景，仍返回 0.01。
- **风险与依赖**：如果下游以未落分金额比较，需要同步改为两位金额比较；应与 `R-ORDER-02` 同步测试。

#### R-ORDER-04 订单超时自动取消未释放预占库存

- **来源报告**：`docs/04-15-check/ecommerce-order-check.md` 中第 4 个不一致点。
- **报告定位**：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderTimeoutService.java:69`；设计依据为 `design-docs/08-订单服务设计.md:56`。
- **问题概述**：超时取消只修改订单状态并发布取消事件，没有调用库存释放；用户主动取消 CREATED 订单已有释放库存逻辑可参考。
- **设计依据**：60 分钟未完成支付，系统自动取消订单并释放预占库存。
- **修复目标**：超时取消后订单状态为 `CANCELLED`，对应预占库存被释放。
- **建议修复方案**：
  1. 在 `OrderTimeoutService` 注入 `InventoryReservationService`。
  2. 在 `cancelExpiredOrder(...)` 将订单置为 `CANCELLED` 后调用 `inventoryReservationService.release(order.getId())`。
  3. 建议与订单状态保存处于同一事务；如果库存释放失败，应记录并触发重试或抛出异常，避免静默库存占用。
  4. 保留 `OrderCancelledEvent` 发布逻辑，但不要只依赖异步事件释放库存。
  5. 可抽取 CREATED 订单取消公共逻辑，复用 `OrderCancelService.cancelCreatedOrder(...)` 的释放模式。
- **API/兼容性影响**：不影响订单 REST API；测试支撑接口 `POST /api/v1/admin/orders/timeout-cancel` 的结果会增加库存恢复这一正确副作用。
- **验证建议**：创建订单后推进测试时钟超过 60 分钟，触发超时取消，断言订单 `CANCELLED`、预占记录 `RELEASED`、库存可用量恢复；重复触发不重复释放。
- **风险与依赖**：依赖库存释放接口幂等；如其他事件监听也释放库存，需要避免双重释放。

#### R-ORDER-05 批量导入可因单条失败提前停止，未保证非法订单跳过后继续处理剩余订单

- **来源报告**：`docs/04-15-check/ecommerce-order-check.md` 中第 5 个不一致点。
- **报告定位**：`code/ecommerce-order/src/main/java/com/ecommerce/order/service/BatchOrderService.java:41`、`code/ecommerce-order/src/main/java/com/ecommerce/order/dto/BatchCreateOrderRequest.java:18`；设计依据为 `design-docs/08-订单服务设计.md:72`、`:74`。
- **问题概述**：批量订单有 `continueOnError` 开关，`false` 时单条失败会 `break`，后续订单不再处理。
- **设计依据**：批量导入应逐条校验，非法订单记录失败原因并跳过；任何一条失败不得导致整批订单回滚。
- **修复目标**：无论请求中 `continueOnError` 如何，服务端都按设计处理全部条目并返回每条结果。
- **建议修复方案**：
  1. 移除或忽略 `BatchOrderService.createBatch(...)` 中 `if (!request.isContinueOnError()) break;` 的提前终止逻辑。
  2. 保持每条订单通过 `TransactionTemplate` 独立处理，单条失败只回滚该条。
  3. `BatchCreateOrderRequest.continueOnError` 如已暴露为请求字段，不删除、不改名，避免破坏 API 兼容；服务端可保留字段但不再用它中止批处理。
  4. 确保最终 `results` 包含所有请求条目的成功或失败明细，`totalCount` 与请求条数一致。
- **API/兼容性影响**：`POST /api/v1/orders/batch` 字段不变；对 `continueOnError=false` 的行为从遇错停止修正为遇错继续，符合设计。
- **验证建议**：批量三条：合法、非法、合法，断言返回三条结果，成功 2、失败 1，第三条仍被创建；第一条不被第二条失败回滚。
- **风险与依赖**：若旧客户端依赖停止语义，会看到行为变化；与 `R-ORDER-01` 组合时需确保单条失败后的库存预占已释放。

### 4.6 支付/退款/发票/结算

#### R-PAYMENT-01 支付成功流程未发布 `OrderPaidEvent`，导致库存扣减事件链不完整

- **来源报告**：`docs/04-15-check/ecommerce-payment-check.md` 中第 1 个不一致点。
- **报告定位**：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentCallbackService.java:112`、`:119`、`:120`，`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/PaymentService.java:96`、`:97`、`:100`，以及库存监听器 `InventoryOrderPaidEventListener.java:39`、`:42`；设计依据为 `design-docs/09-支付服务设计.md:23-32`。
- **问题概述**：支付回调成功后只发布 `PaymentSucceededEvent`，没有发布库存监听器所需的 `OrderPaidEvent`。
- **设计依据**：支付成功流程要求更新支付单、更新订单支付状态、触发库存扣减，并发布 `PaymentSucceededEvent` 和 `OrderPaidEvent`；后置动作失败不得导致支付确认失败。
- **修复目标**：支付确认成功后同时发布两类本地事件，库存服务能收到 `OrderPaidEvent` 执行扣减。
- **建议修复方案**：
  1. 在 `PaymentService.confirmPayment(PaymentRecord payment)` 保留现有 `PaymentSucceededEvent` 发布逻辑。
  2. 构造并发布已有 `com.ecommerce.common.event.OrderPaidEvent`，字段使用 `payment.orderId`、`payment.paymentNo`、`payment.paidAmount`，`userId` 可通过 `OrderQueryService.getOrder(orderId)` 获取。
  3. 维持回调幂等与 `SUCCESS` 状态短路，避免重复回调重复发布或重复扣减。
  4. 确保库存、物流、积分、通知等事件监听器失败时只记录失败，不向支付主流程传播异常。
- **API/兼容性影响**：不修改支付 REST API；只增加内部事件发布。对已监听 `PaymentSucceededEvent` 的逻辑保持兼容。
- **验证建议**：单元测试断言 `confirmPayment` 同时发布两类事件；集成测试支付回调后库存扣减被触发；重复回调不重复扣减。
- **风险与依赖**：依赖库存扣减幂等（关联 `R-INVENTORY-02`）；若物流等模块同时监听 `PaymentSucceededEvent` 和 `OrderPaidEvent`，需避免双重后置动作。

#### R-PAYMENT-02 退款金额额外扣除固定 1 元，违反默认公式 `refund = paidAmount × 0.98`

- **来源报告**：`docs/04-15-check/ecommerce-payment-check.md` 中第 2 个不一致点。
- **报告定位**：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundCalculator.java:35`、`:38`、`:39`，`code/ecommerce-payment/src/main/java/com/ecommerce/payment/config/PaymentConfig.java:20`；设计依据为 `design-docs/09-支付服务设计.md:51-65`。
- **问题概述**：退款按费率计算后又扣除固定 1 元，违反不得额外扣固定费用。
- **设计依据**：退款金额 = 实付金额 × (1 - 手续费率)，默认手续费率 2%，即 `paidAmount × 0.98`。
- **修复目标**：退款金额只按手续费率扣减，并按金额工具保留两位。
- **建议修复方案**：
  1. 移除 `RefundCalculator.calculate(...)` 中对 `BigDecimal.ONE` 的固定扣减。
  2. 使用 `PaymentConfig.refundFeeRate` 计算 `paidAmount × (1 - feeRate)`。
  3. 使用 `MonetaryUtil.roundToCent` 或现有金额工具保留两位。
  4. 保留现有退款金额合法性校验，确保大于 0 且不超过实付金额。
- **API/兼容性影响**：不修改退款 API；响应中的 `refundAmount` 数值从错误公式修正为设计公式。
- **验证建议**：默认费率下 `paidAmount=100.00` 返回 `98.00`；配置其他费率时按配置计算；退款申请接口返回设计金额。
- **风险与依赖**：旧测试若硬编码 `-1` 后的金额需按设计修正；注意小额退款四舍五入和下限校验。

#### R-PAYMENT-03 退款流程缺少独立“财务退款”步骤，仓库验收后由仓库接口直接完成退款

- **来源报告**：`docs/04-15-check/ecommerce-payment-check.md` 中第 3 个不一致点。
- **报告定位**：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundService.java:141`、`:147`、`:149`，`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/RefundStageService.java:66`、`:84`、`:93`；设计依据为 `design-docs/09-支付服务设计.md:37-49`。
- **问题概述**：仓库验收接口内部立即调用 `completeRefund` 并发布完成事件，将仓库验收和财务退款合并为一步。
- **设计依据**：退款流程必须经过“仓库验收 → 财务退款 → 发布 RefundCompletedEvent”，商家审核后不得直接退款且必须等待仓库验收。
- **修复目标**：仓库验收和财务退款成为两个独立阶段；完成事件只在财务退款成功后发布。
- **建议修复方案**：
  1. 保持 README 已冻结的退款 REST API 不变，不新增外部财务退款端点，财务退款作为 payment-service 内部阶段实现。
  2. 修改 `RefundService.warehouseAccept(...)`：仓库接口只调用 `RefundStageService.acceptWarehouse(...)`，将状态推进到 `WAREHOUSE_ACCEPTED`，不直接完成退款。
  3. 在 `RefundStageService` 拆分方法：`acceptWarehouse` 只负责仓库验收；新增 `executeFinanceRefund` 方法且只允许从 `WAREHOUSE_ACCEPTED` 推进到 `COMPLETED`。
  4. 采用事务后本地事件触发财务退款执行：仓库验收成功后发布内部财务退款请求事件，监听器调用 `executeFinanceRefund`；财务退款成功后设置 `completedAt`、必要时更新支付单为 `REFUNDED`、发布 `RefundCompletedEvent`。
  5. 不新增外部 REST 端点；若需要表达执行中状态，使用既有 `WAREHOUSE_ACCEPTED` 作为等待财务退款的中间状态，避免引入额外响应状态。
- **API/兼容性影响**：不改 URL、Method、字段；`warehouse-accept` 响应状态可能从立即 `COMPLETED` 变为 `WAREHOUSE_ACCEPTED` 或短暂中间态，这是按设计修正。需通过异步/调度确保最终可完成退款。
- **验证建议**：仓库验收后未发布 `RefundCompletedEvent`，状态为 `WAREHOUSE_ACCEPTED`；财务退款执行后状态为 `COMPLETED`、支付单 `REFUNDED`、事件已发布；财务失败有失败记录/重试机制。
- **风险与依赖**：异步财务退款需要重试或失败记录，避免退款长期停留中间状态；关联 `R-PAYMENT-06`，结算退款统计依赖 `completedAt`。

#### R-PAYMENT-04 发票税率默认值为 13%，不是设计要求的 6%

- **来源报告**：`docs/04-15-check/ecommerce-payment-check.md` 中第 4 个不一致点。
- **报告定位**：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/InvoiceService.java:36`、`:83`、`:84`、`:85`；设计依据为 `design-docs/09-支付服务设计.md:67-76`、`design-docs/14-发票与结算设计.md:25-35`。
- **问题概述**：`InvoiceService` 默认税率常量为 `0.13`，而设计要求未配置时默认 6%。
- **设计依据**：发票税率从 `invoice.tax-rate` 读取，默认 6%；税额 = 发票金额 × 税率，并按 HALF_UP 保留两位。
- **修复目标**：未配置税率时按 0.06 计算，运行时配置仍可覆盖。
- **建议修复方案**：
  1. 将 `InvoiceService` 默认税率常量从 `new BigDecimal("0.13")` 改为 `new BigDecimal("0.06")`。
  2. 保留 `RuntimeConfigRegistry.getBigDecimal("invoice.tax-rate", TAX_RATE)` 的读取方式。
  3. 保持现有税额计算和两位舍入逻辑。
- **API/兼容性影响**：不修改发票 REST API；默认 `taxRate`、`taxAmount` 数值从 13% 修正为 6%。
- **验证建议**：未配置时发票金额 100.00 税额为 6.00；配置 `invoice.tax-rate=0.13` 时税额为 13.00；部分开票累计金额仍不超过实付金额。
- **风险与依赖**：测试需清理运行时配置，避免默认税率被覆盖；旧测试若按 13% 断言需按设计修正。

#### R-PAYMENT-05 结算批次未限定“支付成功且未结算订单”，且未记录/排除已结算订单

- **来源报告**：`docs/04-15-check/ecommerce-payment-check.md` 中第 5 个不一致点。
- **报告定位**：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/SettlementBatchService.java:70`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/repository/PaymentRecordRepository.java:23`、`:25`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/entity/PaymentRecord.java:38`、`:51`、`code/ecommerce-payment/src/main/java/com/ecommerce/payment/controller/AdminSettlementController.java:35`；设计依据为 `design-docs/14-发票与结算设计.md:37-41`。
- **问题概述**：结算只按支付时间查询，没有限定 `PaymentStatus.SUCCESS`，也没有结算标记来排除已结算支付单。
- **设计依据**：结算批次按自然日生成，包含支付成功且未结算的订单、退款和发票数据；生成后不可修改，只能作废后重建。
- **修复目标**：结算批次只纳入支付成功且尚未结算的支付记录，并在批次生成后标记。
- **建议修复方案**：
  1. 将查询从 `findByPaidAtBetween` 改为限定 `PaymentStatus.SUCCESS` 的查询。
  2. 为 `PaymentRecord` 增加内部结算标记字段，如 `settledAt`、`settlementBatchNo` 或 `settlementBatchId`。
  3. 在 `PaymentRecordRepository` 增加按 `status=SUCCESS`、`paidAt` 自然日范围、`settledAt is null` 查询的方法。
  4. `SettlementBatchService.generateBatch(...)` 只选取未结算成功支付记录；保存批次和明细成功后回写支付记录结算标记。
  5. 修正 `AdminSettlementController` 与设计相反的注释，避免误导维护者。
- **API/兼容性影响**：不修改 `POST /api/v1/admin/settlements/batches`；新增字段为内部持久化字段，不应出现在冻结响应契约之外。
- **验证建议**：同日存在 `SUCCESS`、`PENDING`、`FAILED` 支付单时只纳入成功未结算；同日重复生成不重复纳入；结算明细与批次汇总金额一致。
- **风险与依赖**：涉及数据库迁移和历史数据补标；未来作废重建批次时需回滚或清理结算标记。

#### R-PAYMENT-06 结算批次未包含退款数据，总退款金额固定传入 0

- **来源报告**：`docs/04-15-check/ecommerce-payment-check.md` 中第 6 个不一致点。
- **报告定位**：`code/ecommerce-payment/src/main/java/com/ecommerce/payment/service/SettlementBatchService.java:38`、`:44`、`:105`、`:106`、`:141`、`:142`；设计依据为 `design-docs/14-发票与结算设计.md:37-41`。
- **问题概述**：结算服务未注入退款仓储，生成批次时 `totalRefundAmount` 固定为 0。
- **设计依据**：结算批次应包含支付成功且未结算的订单、退款和发票数据。
- **修复目标**：按自然日统计已完成退款并写入结算批次退款合计。
- **建议修复方案**：
  1. 在 `SettlementBatchService` 注入 `RefundRecordRepository`。
  2. 在 `RefundRecordRepository` 增加按 `RefundStatus.COMPLETED` 和 `completedAt` 自然日范围查询的方法。
  3. 将 `totalRefundAmount` 计算为查询到的退款记录 `refundAmount` 合计，而不是固定 `BigDecimal.ZERO`。
  4. 如需避免重复结算退款，为 `RefundRecord` 增加内部结算标记字段，查询时排除已结算退款，并在批次保存后回写。
  5. 如结算明细需要订单级退款，可在内部明细或聚合逻辑中按订单汇总退款金额，不改变冻结 API。
- **API/兼容性影响**：不修改结算 API；`SettlementBatchResponse.totalRefundAmount` 从恒为 0 修正为实际退款合计。
- **验证建议**：同一结算日存在已完成退款时，批次 `totalRefundAmount` 等于退款金额合计；未完成退款、非当天退款不计入；无退款时仍为 0.00。
- **风险与依赖**：依赖 `R-PAYMENT-03` 正确设置退款 `completedAt`；退款跨日结算口径应按完成时间自然日固定；若引入退款结算标记，作废重建时需处理回滚。

### 4.7 促销服务

#### R-PROMOTION-01 折扣券优惠金额计算公式与设计不一致

- **来源报告**：`docs/04-15-check/ecommerce-promotion-check.md` 中第 1 个不一致点。
- **报告定位**：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponService.java:81`、`:83`、`:84`、`:91`；设计依据为 `design-docs/10-促销服务设计.md:19`、`:21`、`:26`。
- **问题概述**：当前把 `1 - discountValue` 当作折扣率计算折后价，导致 8 折券 100 元优惠被算成 80 元，而设计要求优惠 20 元。
- **设计依据**：折后价 = 原价 × `discountValue`，优惠金额 = 原价 × (1 - `discountValue`)。
- **修复目标**：折扣券按设计返回真实优惠金额，并保留封顶逻辑。
- **建议修复方案**：
  1. 在 `CouponService.calculateDiscount` 的 `DISCOUNT` 分支中，将 `coupon.getDiscountValue()` 直接作为折扣率。
  2. 计算 `afterDiscount = price × discountValue`，再计算 `rawDiscount = price - afterDiscount`。
  3. `maxDiscount` 封顶应作用于 `rawDiscount`，而不是错误的折后价。
  4. 对 `discountValue == null` 或超出合理范围的输入增加参数校验或归零保护，避免空指针。
- **API/兼容性影响**：不修改促销 REST API；`discountAmount` 数值从错误公式修正为设计公式，影响订单/购物车预估链路但符合 README `PUB-101`。
- **验证建议**：8 折券、原价 100.00，断言优惠金额 20.00；执行 `PUB-101`；运行 `mvn -f code/pom.xml test`。
- **风险与依赖**：与 `R-PROMOTION-03` 叠加顺序共同影响最终优惠；旧错误断言需要按设计修正。

#### R-PROMOTION-02 优惠券校验顺序和校验项不完整

- **来源报告**：`docs/04-15-check/ecommerce-promotion-check.md` 中第 2 个不一致点。
- **报告定位**：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:144`、`:149`、`:153`，`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponValidator.java:32`、`:40`、`:44`；设计依据为 `design-docs/10-促销服务设计.md:30`、`:32`。
- **问题概述**：当前先校验用户归属和券状态，后校验有效期；使用门槛和商品适用性没有按设计顺序完整校验。
- **设计依据**：优惠券校验顺序固定为存在性 → 有效期 → 使用门槛 → 商品适用性 → 用户限制 → 已使用。
- **修复目标**：所有优惠券校验集中在校验器内按固定顺序执行，促销计算结果稳定可解释。
- **建议修复方案**：
  1. 重构 `CouponValidator.validate` 入参，使其接收 `UserCoupon`、请求 `userId`、当前金额/门槛金额和当前请求商品 SKU 列表。
  2. 将 `PromotionCalculationServiceImpl.calculateCouponDiscount` 中提前执行的用户归属校验移入校验器，避免顺序被打乱。
  3. 校验器按设计顺序检查：券/模板存在、模板状态和有效期、`thresholdAmount`、`applicableProductIds/applicableCategoryIds`、用户归属、`UserCoupon.status` 是否已使用。
  4. 商品适用性中，`applicableProductIds` 可直接按请求 SKU 判断；`applicableCategoryIds` 如需 SKU 类目信息，应通过商品模块只读查询端口补齐，不新增 REST API。
  5. 在计算最优券场景，不适用券可跳过；用户显式指定非法券时应保持现有错误响应结构。
- **API/兼容性影响**：不修改促销 API 字段；只改变无效券处理与优惠选择行为。
- **验证建议**：分别构造过期、未达门槛、不适用 SKU/类目、非本人、已使用优惠券，验证校验顺序和返回结果；确保合法券仍参与计算。
- **风险与依赖**：类目适用性依赖商品模块 SKU/类目查询能力；需统一“跳过无效券”与“显式非法券报错”的行为边界。

#### R-PROMOTION-03 优惠叠加顺序与设计固定顺序不一致

- **来源报告**：`docs/04-15-check/ecommerce-promotion-check.md` 中第 3 个不一致点。
- **报告定位**：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:71`、`:73`、`:74`、`:77`、`:79`；设计依据为 `design-docs/10-促销服务设计.md:41`、`:45`、`:49`。
- **问题概述**：当前先会员折扣，再满减，最后优惠券；设计要求满减 → 优惠券 → 会员折扣，且后一步基于前一步结果。
- **设计依据**：优惠叠加顺序固定为“满减活动 → 优惠券折扣 → 会员专属折扣”。
- **修复目标**：促销响应中的各项优惠金额和 `finalAmount` 按设计顺序计算。
- **建议修复方案**：
  1. 调整 `PromotionCalculationServiceImpl.calculate` 中 `StackingContext` 应用顺序为：先 `applyFullReduction(...)`，再 `applyCoupon(...)`，最后 `applyMemberDiscount(...)`。
  2. `calculateCouponDiscount` 使用满减后的 `context.currentAmount()` 作为基数。
  3. `calculateMemberDiscount` 使用优惠券后的 `context.currentAmount()` 作为基数。
  4. 更新 `StackingContext` 注释和测试，避免继续表达旧顺序。
  5. 阶梯价当前未作为本报告不一致点展开，保持 no-op 或不影响三步顺序。
- **API/兼容性影响**：不改 API；`fullReductionDiscount`、`couponDiscount`、`memberDiscount`、`totalDiscount`、`finalAmount` 数值会按设计变化。
- **验证建议**：商品金额 300、满减 30、8 折券、会员 95 折，断言最终 205.20；验证会员折扣基于优惠券后金额。
- **风险与依赖**：与 `R-PROMOTION-01` 折扣公式和 `R-PROMOTION-05` 满减基数强相关，建议同批实现和测试。

#### R-PROMOTION-04 秒杀用户限购校验缺失

- **来源报告**：`docs/04-15-check/ecommerce-promotion-check.md` 中第 4 个不一致点。
- **报告定位**：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/SeckillActivity.java:33`，`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/SeckillService.java:57`、`:58`、`:73`；设计依据为 `design-docs/10-促销服务设计.md:73`、`:75`、`:79`。
- **问题概述**：`SeckillActivity` 有 `perUserLimit`，但 `validateSeckill(Long skuId)` 不接收 `userId` 或数量，也不查询用户已购数量。
- **设计依据**：秒杀订单必须校验用户未超过限购数量。
- **修复目标**：秒杀校验按用户维度统计已购数量，阻止超过 `perUserLimit`。
- **建议修复方案**：
  1. 新增秒杀购买记录实体/仓储，记录 `activityId`、`userId`、`skuId`、`quantity`、订单号/业务流水号和创建时间。
  2. 增加按 `activityId + userId` 汇总已购数量的 Repository 方法。
  3. 扩展内部校验方法为 `validateSeckill(Long skuId, Long userId, Integer quantity)`，真实下单链路必须调用带用户维度的方法。
  4. 校验顺序保持活动进行中、SKU 参与、用户限购、库存充足。
  5. 扩展 `recordPurchase`，记录用户购买数量并更新活动销量；保留旧方法作为非下单场景兼容适配。
- **API/兼容性影响**：不修改促销 REST API；订单模块内部调用秒杀校验时需传入用户和数量。
- **验证建议**：`perUserLimit=1` 时同一用户第二次购买被拒绝，不同用户独立；购买数量大于限购直接拒绝；并发下单不突破限购和秒杀库存。
- **风险与依赖**：依赖订单链路传递用户和数量；并发场景需要锁、唯一约束或事务隔离；订单取消后是否释放限购额度需后续明确。

#### R-PROMOTION-05 秒杀价格不参与普通满减的规则未在促销计算中体现

- **来源报告**：`docs/04-15-check/ecommerce-promotion-check.md` 中第 5 个不一致点。
- **报告定位**：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/dto/PromotionCalculateRequest.java:24`、`:26`、`:29`、`:32`，`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:69`、`:74`；设计依据为 `design-docs/10-促销服务设计.md:73`、`:81`。
- **问题概述**：促销计算项没有秒杀标识，满减直接基于全部商品金额计算，秒杀价商品会错误参与普通满减。
- **设计依据**：秒杀价格不参与普通满减。
- **修复目标**：不改变冻结请求字段的前提下，在内部计算满减基数时排除秒杀价行。
- **建议修复方案**：
  1. 在促销计算内部新增 `computeFullReductionEligibleTotal(items)`，单独计算普通满减基数。
  2. 通过 `SeckillRepository.findBySkuIdAndStatus(skuId, "ACTIVE")` 并结合活动时间窗口判断 SKU 是否处于秒杀活动。
  3. 若请求项 SKU 命中进行中的秒杀活动且请求价格等于 `seckillPrice`，该行金额不计入普通满减基数；否则计入。
  4. `itemTotal` 仍保留全部商品金额，满减服务只使用排除后的基数。
  5. 与 `R-PROMOTION-03` 合并时，满减仍作为第一步，只是基数排除秒杀价。
- **API/兼容性影响**：不新增公共请求字段，不修改 `/api/v1/promotions/calculate` 契约；仅改变满减计算结果。
- **验证建议**：普通商品 100 + 秒杀价商品 100，在满 200 减 30 活动下不触发满减；普通商品 200 + 秒杀价商品 100 时只按普通商品触发满减；活动未开始/结束或价格不等于秒杀价时不排除。
- **风险与依赖**：依赖秒杀活动查询和价格匹配；同 SKU 多活动时需固定匹配优先级；与 `R-PROMOTION-03` 有顺序依赖。

### 4.8 物流服务

#### R-LOGISTICS-01 DELIVERED 状态变更后未通过 `OrderLogisticsStatusUpdater` 更新订单物流状态

- **来源报告**：`docs/04-15-check/ecommerce-logistics-check.md` 中第 1 个不一致点。
- **报告定位**：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/ShipmentService.java:293`、`:299`；设计依据为 `design-docs/11-物流服务设计.md:36`。
- **问题概述**：`updateStatus(...)` 在 `DELIVERED` 分支只发布 `ShipmentDeliveredEvent`，订单物流状态同步在 `else` 分支，导致签收状态不同步。
- **设计依据**：物流状态变更后必须通过 `OrderLogisticsStatusUpdater` 更新对应订单的物流状态。
- **修复目标**：所有物流状态变更，包括 `DELIVERED`，均同步订单侧物流状态。
- **建议修复方案**：
  1. 将 `orderLogisticsStatusUpdater.updateLogisticsStatus(shipment.getOrderId(), newStatus.name())` 从 `else` 分支抽出。
  2. 建议顺序为保存发货单状态 → 记录轨迹 → 同步订单物流状态 → 若 `newStatus == DELIVERED` 再发布签收事件。
  3. 保留当前同步失败的降级/记录策略，避免订单侧同步失败阻塞物流回调。
- **API/兼容性影响**：不修改物流 REST API；订单侧状态可正确变为 `DELIVERED`。
- **验证建议**：单测 `updateStatus(..., DELIVERED, ...)` 断言调用 `OrderLogisticsStatusUpdater.updateLogisticsStatus(orderId, "DELIVERED")`；回归 `/api/v1/logistics/callback` 签收链路。
- **风险与依赖**：订单模块状态机需允许流转到 `DELIVERED`；事件发布和订单同步顺序需固定，避免监听器观察到旧状态。

#### R-LOGISTICS-02 运费模板缺少“商品件数”配置能力

- **来源报告**：`docs/04-15-check/ecommerce-logistics-check.md` 中第 2 个不一致点。
- **报告定位**：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/entity/FreightTemplate.java:39`、`:46`，`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/dto/FreightTemplateRequest.java:22`、`:25`；设计依据为 `design-docs/11-物流服务设计.md:42`。
- **问题概述**：运费模板实体和请求 DTO 只有省份规则、重量规则，没有商品件数规则。
- **设计依据**：运费模板可按省份、重量和商品件数配置。
- **修复目标**：管理员创建/更新运费模板时可配置商品件数规则，并能持久化。
- **建议修复方案**：
  1. 在 `FreightTemplate` 新增可选 `itemCountRules` 字段，建议用 `TEXT` 存储 JSON 规则。
  2. 在 `FreightTemplateRequest` 增加同名可选字段及 getter/setter。
  3. 在 `FreightTemplateService.createTemplate(...)` 和更新逻辑中保存/更新该字段。
  4. 约定稳定 JSON 结构，例如 `[{"maxItemCount":3,"freight":8.00},{"maxItemCount":10,"freight":15.00}]`。
  5. 保留 `provinceRules`、`weightRules` 不变。
- **API/兼容性影响**：不改 URL、Method、认证、成功状态；新增可选请求/响应字段用于补齐设计。必须保留既有字段名和类型，避免破坏 README 契约。
- **验证建议**：创建/更新模板时提交 `itemCountRules`，断言持久化和读取正确；不传该字段的旧请求仍返回 201。
- **风险与依赖**：需要数据库列或自动建表支持；规则 JSON 必须与 `R-LOGISTICS-03` 解析逻辑保持一致。

#### R-LOGISTICS-03 运费计算未按省份、重量、商品件数应用模板规则

- **来源报告**：`docs/04-15-check/ecommerce-logistics-check.md` 中第 3 个不一致点。
- **报告定位**：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/service/FreightCalculator.java:44`、`:68`、`:78`；设计依据为 `design-docs/11-物流服务设计.md:40`、`:42`。
- **问题概述**：当前计算方法只接收 `itemTotal` 和 `templateId`，模板计算只使用免邮门槛和默认运费，没有省份、重量、件数输入或规则解析。
- **设计依据**：默认运费 8 元、满 199 免运费；运费模板可按省份、重量、商品件数配置，最终以订单创建时计算结果为准。
- **修复目标**：运费计算能基于订单上下文匹配模板规则，并在订单创建时固化结果。
- **建议修复方案**：
  1. 保留现有 `calculateFreight(BigDecimal itemTotal)` 和 `calculateFreight(BigDecimal itemTotal, Long templateId)`，新增上下文式入口，如 `calculateFreight(FreightCalculationContext context)` 或带 `province/weightKg/itemCount` 参数的重载。
  2. 上下文包含 `itemTotal`、`templateId`、`province`、`weightKg`、`itemCount`。
  3. `calculateWithTemplate` 先判断免邮门槛，满足则返回 0；否则解析并匹配 `provinceRules`、`weightRules`、`itemCountRules`。
  4. 固定冲突优先级，建议省份规则 → 重量规则 → 件数规则 → 默认运费，或在代码和测试中明确其他稳定策略。
  5. JSON 解析失败时记录警告并回退默认运费，避免订单创建不可用。
  6. 订单创建侧使用上下文式计算并将 `shippingFee` 固化到订单；后续物流发货单读取订单创建时结果。
- **API/兼容性影响**：不修改 REST URL/Method；新增 Java 方法保持旧调用方兼容。配置模板后运费行为从默认规则扩展为模板规则。
- **验证建议**：单测覆盖默认运费、199 免邮、省份规则、重量规则、件数规则、模板不存在、非法 JSON 回退；订单创建集成测试验证 `shippingFee` 固化。
- **风险与依赖**：订单侧需提供省份、重量、件数；若商品重量缺失需从商品/SKU 或订单项快照补齐；运费变化会影响订单应付、支付校验。

#### R-LOGISTICS-04 除 `OrderPaidEvent` 外还监听 `PaymentSucceededEvent` 创建发货单

- **来源报告**：`docs/04-15-check/ecommerce-logistics-check.md` 中第 4 个不一致点。
- **报告定位**：`code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/event/OrderPaidShipmentListener.java:37`、`:43`；设计依据为 `design-docs/11-物流服务设计.md:7`。
- **问题概述**：物流监听器同时监听 `OrderPaidEvent` 和 `PaymentSucceededEvent`，扩大了发货单创建触发源。
- **设计依据**：订单支付成功后物流服务通过监听 `OrderPaidEvent` 创建发货单，不应由订单服务同步调用，也不应额外依赖支付成功事件作为创建入口。
- **修复目标**：发货单创建唯一事件入口为 `OrderPaidEvent`，重复事件仍保持幂等。
- **建议修复方案**：
  1. 删除或禁用 `OrderPaidShipmentListener.onPaymentSucceeded(PaymentSucceededEvent event)`。
  2. 移除不再使用的 `PaymentSucceededEvent` import。
  3. 保留 `onOrderPaid(OrderPaidEvent event)` 调用 `LogisticsCommandService.createShipmentForPaidOrder(orderId)`。
  4. 保留 `LogisticsCommandServiceImpl.createShipmentForPaidOrder(...)` 按 `orderId` 查询已有发货单的幂等保护。
  5. 失败事件记录中的来源事件类型后续应只出现 `OrderPaidEvent`，便于重放和排查。
- **API/兼容性影响**：不修改 REST API；事件消费行为变为设计指定的单一入口。需要先确保支付/订单链路会可靠发布 `OrderPaidEvent`（关联 `R-PAYMENT-01`）。
- **验证建议**：发布 `OrderPaidEvent` 时创建发货单；发布 `PaymentSucceededEvent` 不再创建发货单；支付回调成功后通过 `OrderPaidEvent` 创建发货单并通过 `/api/v1/logistics/order/{orderId}` 查询。
- **风险与依赖**：强依赖 `R-PAYMENT-01` 或订单侧 `OrderPaidEvent` 发布可靠性；不能为了兜底改成订单服务同步调用物流服务。

### 4.9 积分与会员服务

#### R-LOYALTY-01 订单积分赚取公式多乘了 100 积分/元换算系数

- **来源报告**：`docs/04-15-check/ecommerce-loyalty-check.md` 中第 1 个不一致点。
- **报告定位**：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:35-39`、`:223-235`；设计依据为 `design-docs/12-积分与会员服务设计.md:11-15`。
- **问题概述**：订单积分计算把订单实付金额额外乘以 `POINTS_PER_YUAN = 100`，导致支付发放积分扩大 100 倍。
- **设计依据**：订单积分 = 订单实付金额 × 会员等级倍率 × 活动系数；`100 积分 = 1 元` 只属于积分抵扣换算规则。
- **修复目标**：支付成功发放的订单积分按设计公式计算。
- **建议修复方案**：
  1. 保留 `POINTS_PER_YUAN = 100` 给抵扣逻辑使用，不再用于订单赚取。
  2. 修改 `LoyaltyPointService.calcOrderPoints(...)`，将当前 `amount × POINTS_PER_YUAN × levelMultiplier × activityMultiplier` 改为 `amount × levelMultiplier × activityMultiplier`。
  3. 保留请求活动系数为空时取 1.0、运行时配置 `loyalty.activity-multiplier` 参与计算的现有逻辑。
  4. 更新方法注释和单元测试，避免继续表达“每元 100 积分”的赚取口径。
- **API/兼容性影响**：不改变积分 REST API；支付后 EARN 流水和账户积分数值会从旧错误结果修正为设计值。
- **验证建议**：NORMAL、实付 100.00、活动系数 1.0 时返回 100 而非 10000；支付事件链路最终写入 EARN 流水符合公式。
- **风险与依赖**：历史错误积分不会自动回滚；与 `R-LOYALTY-02` 共同影响 GOLD 等级最终赚取积分。

#### R-LOYALTY-02 GOLD 会员等级积分倍率实现为 1.1，而设计要求为 1.2

- **来源报告**：`docs/04-15-check/ecommerce-loyalty-check.md` 中第 2 个不一致点。
- **报告定位**：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/entity/MemberLevel.java:11-14`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/MemberBenefitService.java:16-22`；设计依据为 `design-docs/12-积分与会员服务设计.md:57-62`。
- **问题概述**：`MemberLevel.GOLD` 和 `MemberBenefitService` 中 GOLD 权益均为 1.1，设计要求 1.2。
- **设计依据**：GOLD 年消费满 5000，积分倍率为 1.2。
- **修复目标**：所有 GOLD 等级倍率展示和积分计算统一为 1.2。
- **建议修复方案**：
  1. 将 `MemberLevel.GOLD` 的 multiplier 从 `1.1` 改为 `1.2`。
  2. 将 `MemberBenefitService` GOLD 分支的 `pointsMultiplier` 从 `1.1` 改为 `1.2`。
  3. 将 GOLD 权益码从类似 `POINTS_MULTIPLIER_1_1` 调整为能表达 1.2 的值，如 `POINTS_MULTIPLIER_1_2`。
  4. 保持 NORMAL=1.0、SILVER=1.1、PLATINUM=1.5 不变。
- **API/兼容性影响**：不改变 REST 字段；`GET /api/v1/loyalty/member-level` 的 GOLD `multiplier` 值会按设计变为 1.2。
- **验证建议**：单测 `MemberBenefitService.getPointsMultiplier(GOLD)=1.2`；GOLD 用户会员接口返回 1.2；结合 `R-LOYALTY-01`，实付 100 的 GOLD 订单积分为 120。
- **风险与依赖**：旧权益码或测试断言需更新；依赖 `R-LOYALTY-01` 修复后完整符合赚取公式。

#### R-LOYALTY-03 缺少“每月 1 号凌晨批量扫描过期积分”的定时调度实现

- **来源报告**：`docs/04-15-check/ecommerce-loyalty-check.md` 中第 3 个不一致点。
- **报告定位**：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/PointsExpireService.java:38-87`、`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/AdminLoyaltyController.java:31-38`；设计依据为 `design-docs/12-积分与会员服务设计.md:49-53`。
- **问题概述**：代码只有手动触发接口和过期处理服务，未发现每月 1 号凌晨自动调度入口；即使应用已启用调度能力，也没有实际 `@Scheduled` 调用。
- **设计依据**：积分有效期 12 个自然月，每月 1 号凌晨系统批量扫描过期积分并记录日志。
- **修复目标**：保留手动接口，同时新增自动月度过期扫描。
- **建议修复方案**：
  1. 在 loyalty 模块新增 `PointsExpireScheduler` 组件并注入 `PointsExpireService`。
  2. 添加 `@Scheduled(cron = "0 0 0 1 * *")` 方法调用 `pointsExpireService.expire()`。
  3. 保留 `POST /api/v1/admin/loyalty/points/expire` 手动补偿入口不变。
  4. 复用 `PointsExpireService` 现有 EXPIRE 流水幂等逻辑，必要时补充并发/重复执行测试。
- **API/兼容性影响**：不修改 REST API；新增后台定时执行路径。
- **验证建议**：mock `PointsExpireService` 验证 scheduler 调用；集成准备过期 EARN 流水，执行过期后断言账户积分和 EXPIRE 流水；启动上下文确认 scheduler bean 可加载。
- **风险与依赖**：多实例部署可能重复执行，需依赖幂等流水和事务隔离；测试环境时钟推进可能触发调度，需隔离。

#### R-LOYALTY-04 积分抵扣未在抵扣时按流水过期时间排除已过期积分

- **来源报告**：`docs/04-15-check/ecommerce-loyalty-check.md` 中第 4 个不一致点。
- **报告定位**：`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:66-79`、`:122-143`，`code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/repository/PointsTransactionRepository.java:27-36`；设计依据为 `design-docs/12-积分与会员服务设计.md:49-53`。
- **问题概述**：估算和实际抵扣都只读取账户级 `availablePoints`，没有在抵扣时排除已过期但尚未批处理扣减的积分。
- **设计依据**：积分抵扣时不得使用已过期积分。
- **修复目标**：积分估算和实际抵扣都只能使用未过期积分，订单模块通过 loyalty 估算时也不包含过期积分。
- **建议修复方案**：
  1. 在 `PointsExpireService` 增加用户级过期处理能力，如 `expireForUser(Long userId)`，复用全局过期扣减和 EXPIRE 流水幂等逻辑。
  2. 在 `PointsTransactionRepository` 增加按 `userId`、`type=EARN`、`expiresAt <= now` 查询当前用户过期流水的方法。
  3. 在 `LoyaltyPointService.estimateRedeemPoints(...)` 计算 `available` 前先触发当前用户过期处理。
  4. 在 `LoyaltyPointService.doRedeemPoints(...)` 扣减前再次触发或复用同一处理，避免估算和扣减之间跨过过期窗口。
  5. 保持抵扣上限公式不变：`min(未过期可用积分, 10000, 订单金额 × 100 × 0.5)`。
- **API/兼容性影响**：不修改 REST API；`estimate-redeem` 返回可用积分可能变小；抵扣前可能自动生成 EXPIRE 流水。
- **验证建议**：用户只有已过期积分时估算为 0；用户有已过期 1000 和未过期 500 时最多使用 500；订单创建积分抵扣不包含过期积分；重复估算/抵扣/手动过期不重复扣减。
- **风险与依赖**：依赖过期处理幂等和并发安全；当前流水未记录 REDEEM 消耗哪笔 EARN，若要精确批次消耗需新增内部分摊模型，但不改变冻结 API。

### 4.10 评价服务

#### R-REVIEW-01 敏感词检测采用完全相等匹配，不符合“包含匹配”要求

- **来源报告**：`docs/04-15-check/ecommerce-review-check.md` 中第 1 个不一致点。
- **报告定位**：`code/ecommerce-review/src/main/java/com/ecommerce/review/service/SensitiveWordFilter.java:31`、`:35`、`:50`、`:55`；设计依据为 `design-docs/13-评价服务设计.md:36`。
- **问题概述**：`containsSensitiveWord` 和 `filter` 使用 `equals`，只有整段内容等于敏感词才命中。
- **设计依据**：敏感词过滤采用包含匹配，只要评价内容包含任一敏感词即命中，不得只做完全相等匹配。
- **修复目标**：敏感词嵌入在普通评价文本中也能命中并被过滤。
- **建议修复方案**：
  1. 在 `SensitiveWordFilter.containsSensitiveWord(String content)` 中排除 null/空内容/空敏感词后，使用 `content.contains(sw.getWord())` 判断。
  2. 在 `SensitiveWordFilter.filter(String content)` 中，只要 `result.contains(sw.getWord())`，就执行 `result = result.replace(sw.getWord(), "***")`。
  3. 保持 `SensitiveWordRepository.findAll()` 数据来源不变，不新增 API、DTO 或数据库结构。
  4. 补充空值保护，避免内容或敏感词为空导致异常。
- **API/兼容性影响**：不修改评价 REST API；行为从完全相等命中扩展为包含命中。
- **验证建议**：敏感词 `badword` 下，`badword` 和 `这个商品包含 badword 内容` 都命中，正常评价不命中；过滤后敏感词替换为 `***`。
- **风险与依赖**：依赖敏感词数据质量；复杂大小写/全半角规避未在设计中要求，本次不扩展。

#### R-REVIEW-02 含敏感词的评价被直接拒绝提交，未进入 PENDING_REVIEW 或 REJECTED 状态

- **来源报告**：`docs/04-15-check/ecommerce-review-check.md` 中第 2 个不一致点。
- **报告定位**：`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:82`、`:83`、`:84`、`:99`；设计依据为 `design-docs/13-评价服务设计.md:24`、`:27-29`、`:36`。
- **问题概述**：`ReviewService.createReview` 命中敏感词时抛出 `BusinessException("SENSITIVE_CONTENT")`，不会保存评价记录或进入审核状态。
- **设计依据**：审核流程为用户提交评价 → 敏感词过滤 → 状态为 `PENDING_REVIEW` → 管理员审核；含敏感词不得直接 `APPROVED`，应进入 `PENDING_REVIEW` 或 `REJECTED`。
- **修复目标**：含敏感词评价也形成评价记录并进入审核流程，默认选择改动最小的 `PENDING_REVIEW`。
- **建议修复方案**：
  1. 在 `ReviewService.createReview` 中移除“命中敏感词即抛 `SENSITIVE_CONTENT`”的中断逻辑。
  2. 保留购买校验、订单状态校验、重复评价、评分范围等前置校验。
  3. 创建评价前调用修复后的 `SensitiveWordFilter.filter(request.getContent())` 得到保存内容。
  4. 保存 `Review` 并设置 `ReviewStatus.PENDING_REVIEW`，复用现有管理员审核通过/拒绝流程。
  5. 可记录日志说明命中敏感词但已进入待审核，不新增响应字段。
- **API/兼容性影响**：不修改 `POST /api/v1/reviews` 契约；含敏感词评价从错误响应变为 201 创建且 `status=PENDING_REVIEW`，符合 README 评价基础链路和设计审核流程。
- **验证建议**：已购买用户提交含 `badword` 的评价，接口成功且状态 `PENDING_REVIEW`；我的评价可见，商品公开评价列表不可见；管理员审核通过后公开展示，拒绝后状态 `REJECTED`。
- **风险与依赖**：依赖 `R-REVIEW-01` 的包含匹配；如果产品选择直接 `REJECTED` 也符合设计，但当前选择 `PENDING_REVIEW` 改动更小且复用审核流程。

### 4.11 通用模块/本地通知组件

#### R-COMMON-01 `NotificationRequest` 定义了设计未列出的额外字段

- **来源报告**：`docs/04-15-check/ecommerce-common-check.md` 中第 1 个不一致点。
- **报告定位**：`code/ecommerce-common/src/main/java/com/ecommerce/common/notification/NotificationRequest.java:12`、`:20`；设计依据为 `design-docs/15-本地通知组件设计.md:15`。
- **问题概述**：`NotificationRequest` 除设计字段外还包含 `subject`、`content` 及对应 getter/setter/builder，业务可绕过模板模型直接传正文。
- **设计依据**：本地通知组件设计列出的字段仅为 `bizType`、`bizId`、`receiver`、`channel`、`templateCode`、`variables`、`idempotencyKey`。
- **修复目标**：通知请求 DTO 与设计字段清单一致，所有通知通过模板编码和变量渲染。
- **建议修复方案**：
  1. 先完成 `R-COMMON-03`，将业务模块 `.subject(...)`、`.content(...)` 调用迁移到 `templateCode` + `variables`。
  2. 从 `NotificationRequest` 删除 `subject`、`content` 字段、getter/setter、builder 字段和 builder 方法。
  3. 保留设计要求字段及其访问器和 builder。
  4. 编译全工程，确认无 `.subject(...)`、`.content(...)` 遗留引用。
- **API/兼容性影响**：不影响 README 冻结 REST API；会影响 Java 内部编译兼容性，因此必须先迁移调用方。不建议保留 deprecated 方法，因为报告问题正是请求模型被扩大。
- **验证建议**：全工程编译和测试；搜索确认无 subject/content 构建调用；common 单测断言通知发送依赖 `templateCode`/`variables`。
- **风险与依赖**：强依赖 `R-COMMON-03`；若其他模块使用额外字段，删除后会暴露编译错误，需要同样迁移。

#### R-COMMON-02 支付成功、发货提醒、订单状态通知渠道与设计场景不一致

- **来源报告**：`docs/04-15-check/ecommerce-common-check.md` 中第 2 个不一致点。
- **报告定位**：`code/ecommerce-order/src/main/java/com/ecommerce/order/integration/OrderNotificationService.java:64`、`:83`、`:160`；设计依据为 `design-docs/15-本地通知组件设计.md:9`。
- **问题概述**：支付成功、发货提醒和订单状态更新都使用 `EMAIL`，与设计的 `SMS`/`IN_APP` 场景映射不一致。
- **设计依据**：`EMAIL` 用于注册激活、发票通知；`SMS` 用于支付成功、发货提醒；`IN_APP` 用于订单状态、退款状态。
- **修复目标**：订单通知场景使用正确通知渠道。
- **建议修复方案**：
  1. 将 `notifyPaymentSuccess(...)` 渠道从 `EMAIL` 改为 `SMS`。
  2. 将 `notifyOrderShipped(...)` 渠道从 `EMAIL` 改为 `SMS`。
  3. 将 `notifyStatusUpdate(...)` 渠道从 `EMAIL` 改为 `IN_APP`。
  4. 与 `R-COMMON-03` 同步补齐 `bizType`、`bizId`、`templateCode`、`variables`、`idempotencyKey`。
  5. 如当前方法参数仍名为 `userEmail`，短期可继续作为 receiver 标识以保持签名稳定；后续接入真实手机号时再扩展调用方。
- **API/兼容性影响**：不影响 REST API；通知记录中的 `channel` 会按设计变化。若测试断言旧 `EMAIL`，需按设计调整。
- **验证建议**：单测捕获 `NotificationRequest.channel`：支付成功和发货为 `SMS`，订单状态为 `IN_APP`；验证 `SMS` 调用 `MockSmsSender`、`IN_APP` 不走邮件发送。
- **风险与依赖**：SMS 接收人来源当前可能仍是邮箱语义，需后续业务补齐手机号；与 `R-COMMON-03` 同步实施可减少重复修改。

#### R-COMMON-03 业务模块构建的多处 `NotificationRequest` 未填写设计要求字段

- **来源报告**：`docs/04-15-check/ecommerce-common-check.md` 中第 3 个不一致点。
- **报告定位**：`code/ecommerce-order/src/main/java/com/ecommerce/order/integration/OrderNotificationService.java:48`、`:67`、`:86`、`:105`、`:124`、`:143`、`:163`、`:183`；设计依据为 `design-docs/15-本地通知组件设计.md:15`。
- **问题概述**：订单通知多处只设置 `channel`、`recipient`、`subject`、`content`，未设置 `bizType`、`bizId`、`templateCode`、`variables`、`idempotencyKey`，导致去重和模板渲染规则无法生效。
- **设计依据**：通知请求必须包含业务类型、业务 ID、接收人、渠道、模板编码、模板变量、幂等键；发送规则基于幂等键去重并渲染模板。
- **修复目标**：订单模块所有通知请求都使用完整设计字段构建，不再传正文。
- **建议修复方案**：
  1. 在 `OrderNotificationService` 抽取统一私有构建方法，集中设置 7 个设计字段。
  2. `bizType` 建议统一为 `ORDER` 或按场景细分；`bizId` 使用订单主键或订单号并保持全模块一致。
  3. 为各场景定义稳定 `templateCode`，如 `ORDER_CREATED`、`ORDER_PAYMENT_SUCCESS`、`ORDER_SHIPPED`、`ORDER_DELIVERED`、`ORDER_CANCELLED`、`ORDER_PAYMENT_EXPIRING`、`ORDER_STATUS_UPDATED`、`ORDER_BATCH_UPDATED`。
  4. 将原 `content` 中拼接的数据拆入 `variables`，如 `orderNo`、`payableAmount`、`paidAmount`、`paymentNo`、`trackingNumber`、`reason`、`minutesRemaining`、`status`、`expiresAt`。
  5. `idempotencyKey` 按业务场景构造稳定唯一值，例如 `ORDER:{orderNo}:{templateCode}`；状态通知可包含状态值避免不同状态被错误去重。
  6. 移除 `.subject(...)`、`.content(...)` 调用，保留 `try/catch` 保护主流程。
- **API/兼容性影响**：不影响 REST API；通知记录中的业务标识和模板字段从空值变为有效值，重复通知会按幂等键去重。
- **验证建议**：对 8 个订单通知构建点分别断言 7 个字段完整；同一订单同一模板重复调用被去重，不同场景或状态不冲突。
- **风险与依赖**：依赖 `R-COMMON-01`、`R-COMMON-02`；`idempotencyKey` 规则过粗会误去重，过细会无法去重，需测试覆盖。

#### R-COMMON-04 发送失败后会向调用方抛出异常，可能影响主流程

- **来源报告**：`docs/04-15-check/ecommerce-common-check.md` 中第 4 个不一致点。
- **报告定位**：`code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java:115`、`:121`；设计依据为 `design-docs/15-本地通知组件设计.md:29`。
- **问题概述**：`LocalNotificationServiceImpl.send(...)` 捕获发送异常并记录失败后继续抛出 `NotificationSendException`，可能影响业务调用方。
- **设计依据**：失败时记录失败原因，不影响主业务流程。
- **修复目标**：通知发送失败被记录但不向业务主流程传播异常。
- **建议修复方案**：
  1. 修改 `LocalNotificationServiceImpl.send(...)` 的 `catch` 分支：保留日志和 `failureRecordService.recordFailure(request, e)`，删除 `throw new NotificationSendException(...)`，记录失败后直接返回。
  2. 对 `failureRecordService.recordFailure(...)` 本身增加保护，失败记录写入异常也只记录日志，不再向外抛出。
  3. 保留 `NotificationSendException` 类以降低删除风险，但不再由该发送实现抛出。
  4. 调用方现有 try/catch 可保留，后续可简化但不是本报告必要修复。
- **API/兼容性影响**：不影响 REST API；Java 行为变为 `LocalNotificationService.send(...)` 失败不抛异常。依赖异常判断发送失败的内部代码应改查失败记录。
- **验证建议**：故障注入 `notification-send-failure` 后，调用发送不抛异常，失败记录存在，主业务方法继续完成。
- **风险与依赖**：与 `R-COMMON-05` 强相关；如果失败记录服务本身异常未隔离，仍会影响主流程。

#### R-COMMON-05 发送日志记录时机早于模板渲染和发送调用

- **来源报告**：`docs/04-15-check/ecommerce-common-check.md` 中第 5 个不一致点。
- **报告定位**：`code/ecommerce-common/src/main/java/com/ecommerce/common/notification/LocalNotificationServiceImpl.java:66`、`:85`、`:87`；设计依据为 `design-docs/15-本地通知组件设计.md:31`。
- **问题概述**：`sentRecords` 在模板渲染和渠道发送前写入，失败请求也可能出现在成功发送观测记录中。
- **设计依据**：发送顺序为幂等去重 → 渲染模板 → 调用 MockMailSender/MockSmsSender → 记录发送日志 → 失败记录原因但不影响主流程。
- **修复目标**：只有实际发送成功后才写成功发送记录；失败只写失败记录。
- **建议修复方案**：
  1. 将 `sentRecords.add(new NotificationRecord(...))` 从模板渲染前移到渠道发送成功之后。
  2. 推荐顺序为参数检查 → 幂等去重 → 故障注入检查 → `renderTemplate(...)` → 调用渠道发送 → 写 `sentRecords` → `NotificationRecordService.record(...)`。
  3. 未知或空 `channel` 应进入失败记录分支，不得写成功记录。
  4. 与 `R-COMMON-04` 联动：失败分支只记录失败原因并返回，不抛异常、不写成功记录。
- **API/兼容性影响**：不影响 REST API；通知观测语义变化为 `sentRecords`/`NotificationRecordService` 只表示成功发送，失败需查失败记录。
- **验证建议**：成功发送后成功记录存在；故障注入后不抛异常、无成功记录、有失败记录；重复 `idempotencyKey` 不重复记录。
- **风险与依赖**：强依赖 `R-COMMON-04`；若旧测试把 `sentRecords` 当作尝试记录，需要按设计改为成功记录。

## 5. 审校结果

- **读取确认**：已读取 `README.md` 的比赛要求、修改边界、冻结 REST API 契约和错误码；已读取 `design-docs/04-用户服务设计.md` 至 `design-docs/15-本地通知组件设计.md`；已读取 `docs/04-15-check` 下 11 份一致性检查报告。
- **覆盖数量**：报告不一致点合计 49 个，本文件给出 49 个 `R-...` 修复项，`checklist.md` 总览表 49 项、按模块 checklist 49 项，ID 集合一致且无重复。
- **未纳入的无法确认项**：
  - `ecommerce-user-check.md` 的冻结登录 HTTP 状态“无法确认项”与第 6 个不一致点同源，已按 `R-USER-06` 处理，未作为额外条目重复纳入。
  - `ecommerce-payment-check.md` 中“结算批次生成后不可修改”无法确认项未纳入 checklist；当前报告未将其列为明确不一致点。
  - `ecommerce-review-check.md` 中“积分服务是否实际发放评价奖励”无法确认项未纳入 checklist；当前报告未将其列为明确不一致点。
  - `ecommerce-common-check.md` 中“发票通知场景”和“SMS 场景是否由其他模块覆盖”无法确认项未单独纳入；其中订单模块 SMS 渠道错误已按明确不一致点 `R-COMMON-02` 处理。
- **设计文档边界**：所有方案均以 `design-docs/04-15` 为验收基准，未提出修改设计文档的方案。
- **REST API 契约**：各修复项均包含 API/兼容性影响说明；涉及 REST 的方案均要求保留 README 冻结的 URL、HTTP Method、认证要求、既有字段名与类型、成功状态码、错误响应结构和 `/api/v1/` 前缀。个别为满足设计新增内部字段或响应补充字段的方案均明确要求不得删除、改名或改变既有字段。
- **报告外问题控制**：未为报告外 bug、潜在缺陷或未实现功能新增 `R-...` 修复项；源码复核中出现的额外信息仅作为相关修复项的风险、依赖或验证说明。
- **跨模块依赖**：重点依赖链包括注册激活闭环、商品搜索统一查询、订单库存预占与释放、支付成功事件到库存扣减和物流发货、促销/购物车/订单/支付金额链路、退款完成到结算退款统计、本地通知请求字段和失败隔离链路。
- **后续建议**：后续实施时建议按跨模块依赖顺序分批落地，并在每批后运行 `mvn -f code/pom.xml test`；涉及黑盒链路时再执行 `mvn -f code/pom.xml install -DskipTests` 和 `mvn -f test-cases/pom.xml test`。
