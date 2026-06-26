# 子代理提示词优化方案

请你启用 11 个子代理分别使用下面的专用配置，子代理之间绝对不允许使用不属于自己的配置，遵循通用配置和执行策略完成一致性检查任务：

---

## 通用配置

- **分批策略**: 每次启动 2 个子代理，防止并发量过高
- **输出目录**: `/docs/04-15-check/<module-name>-check.md`
- **设计文档目录**: `/design-docs/`
- **代码目录**: `/code/`

---

## 子代理 01: 电商用户服务 (04-用户服务设计.md)

### 代理标识
- **代理名称**: `agent-ecommerce-user`
- **设计文档**: `design-docs/04-用户服务设计.md`
- **目标模块**: `code/ecommerce-user`
- **引用模块**: 无直接引用

### 输入文件
1. `design-docs/04-用户服务设计.md` - 设计规范
2. `code/ecommerce-user/src/main/java/com/ecommerce/user/` - 所有源文件
3. `code/ecommerce-user/pom.xml` - 模块依赖配置
4. `code/pom.xml` - 父级 Maven 配置

### 检查要点
1. **领域模型**: User、UserProfile、UserAddress、EmailActivationToken、LoginSession 实体是否完整实现
2. **用户状态**: PENDING_ACTIVATION、ACTIVE、FROZEN、CLOSED 状态机是否正确
3. **注册流程**: 邮箱激活流程是否完整（校验唯一性 → PENDING_ACTIVATION → 发送激活邮件 → 激活变更状态）
4. **登录流程**: JWT 签发、登录会话记录是否正确
5. **地址格式化**: `format(String province, String city, String district, String detail)` 签名和输出格式
6. **UserQueryService**: getUserById、isActive、isFrozen、getDefaultAddress 方法签名
7. **REST API**: 9 个 API 端点（register、activate、login、me、addresses CRUD、freeze/unfreeze）是否完整实现
8. **冻结用户限制**: 冻结用户登录返回 403，错误码 USER_FROZEN

### 输出要求
输出到 `/docs/04-15-check/ecommerce-user-check.md`，包含：
- 按点列出所有不一致之处
- 每点包含代码定位和设计要求定位
- 不一致原因和详细解析

---

## 子代理 02: 电商商品服务 (05-商品服务设计.md)

### 代理标识
- **代理名称**: `agent-ecommerce-product`
- **设计文档**: `design-docs/05-商品服务设计.md`
- **目标模块**: `code/ecommerce-product`
- **引用模块**: `code/ecommerce-inventory`（通过 InventoryQueryService）

### 输入文件
1. `design-docs/05-商品服务设计.md` - 设计规范
2. `code/ecommerce-product/src/main/java/com/ecommerce/product/` - 所有源文件
3. `code/ecommerce-product/pom.xml` - 模块依赖配置
4. `code/pom.xml` - 父级 Maven 配置

### 检查要点
1. **领域模型**: ProductSpu、ProductSku、Category、Brand、AttributeTemplate、ProductTag 实体是否完整
2. **SKU 状态**: DRAFT、ON_SHELF、OFF_SHELF、DELETED 状态机
3. **商品详情**: 必须通过 `InventoryQueryService.getStockSummary(skuId)` 获取库存摘要，不得直接访问库存表
4. **ProductQueryService**: getSku、getSkuForSale、listSkuByIds、getProductSnapshot 方法签名
5. **商品搜索**: keyword、categoryId、brandId、minPrice/maxPrice、tags、onlyOnShelf 搜索条件
6. **REST API**: 8 个 API 端点是否完整实现
7. **跨模块约束**: 库存服务获取商品信息必须使用 ProductQueryService，不得在库存模块内重复定义 Product JPA Entity

### 输出要求
输出到 `/docs/04-15-check/ecommerce-product-check.md`，包含：
- 按点列出所有不一致之处
- 每点包含代码定位和设计要求定位
- 不一致原因和详细解析

---

## 子代理 03: 电商库存服务 (06-库存服务设计.md)

### 代理标识
- **代理名称**: `agent-ecommerce-inventory`
- **设计文档**: `design-docs/06-库存服务设计.md`
- **目标模块**: `code/ecommerce-inventory`
- **引用模块**: `code/ecommerce-order`（订单事件）、`code/ecommerce-product`（ProductQueryService）

### 输入文件
1. `design-docs/06-库存服务设计.md` - 设计规范
2. `code/ecommerce-inventory/src/main/java/com/ecommerce/inventory/` - 所有源文件
3. `code/ecommerce-inventory/pom.xml` - 模块依赖配置
4. `code/pom.xml` - 父级 Maven 配置

### 检查要点
1. **领域模型**: Warehouse、InventoryStock、StockReservation、InboundOrder、OutboundOrder、StockAdjustment、StockWarningRule 实体
2. **库存公式**: `availableStock = onHandStock - reservedStock`
3. **下单库存处理**: 预占库存流程（创建 StockReservation、增加 reservedStock、不减少 onHandStock）
4. **支付后扣减**: 接收 OrderPaidEvent → 减少 onHandStock → 减少 reservedStock → 生成 OutboundOrder
5. **取消/超时释放**: 减少 reservedStock、关闭 StockReservation
6. **多仓分配**: 优先级（省份匹配→可用库存→距离→仓库优先级）、同一订单同一 SKU 优先单仓
7. **InventoryQueryService**: getStockSummary、checkAvailability、listAvailableWarehouses 方法签名
8. **InventoryReservationService**: reserve、release、deductAfterPayment 方法签名
9. **REST API**: 7 个 API 端点是否完整实现
10. **跨模块约束**: 获取商品名称/SKU编码/规格必须通过 ProductQueryService，不得在库存模块内重复定义 Product JPA Entity

### 输出要求
输出到 `/docs/04-15-check/ecommerce-inventory-check.md`，包含：
- 按点列出所有不一致之处
- 每点包含代码定位和设计要求定位
- 不一致原因和详细解析

---

## 子代理 04: 电商购物车服务 (07-购物车服务设计.md)

### 代理标识
- **代理名称**: `agent-ecommerce-cart`
- **设计文档**: `design-docs/07-购物车服务设计.md`
- **目标模块**: `code/ecommerce-cart`
- **引用模块**: 无直接引用

### 输入文件
1. `design-docs/07-购物车服务设计.md` - 设计规范
2. `code/ecommerce-cart/src/main/java/com/ecommerce/cart/` - 所有源文件
3. `code/ecommerce-cart/pom.xml` - 模块依赖配置
4. `code/pom.xml` - 父级 Maven 配置

### 检查要点
1. **存储要求**: 购物车必须存储在本地 Caffeine Cache，Key 格式为 `cart:{userId}`，TTL 为 7 天
2. **购物车规则**: 最大商品种类 100、单项数量 1-999、SKU 状态必须为 ON_SHELF
3. **重复加入**: 同一 SKU 重复加入购物车时数量累加
4. **库存展示**: 通过 InventoryQueryService 查询
5. **价格预估**: 通过 PromotionCalculationService 计算，返回商品原价合计、满减优惠、优惠券可用列表、会员折扣、预计运费、预计应付金额
6. **REST API**: 6 个 API 端点（items POST、cart GET、items PUT、items DELETE skuId、cart DELETE、estimate POST）是否完整实现

### 输出要求
输出到 `/docs/04-15-check/ecommerce-cart-check.md`，包含：
- 按点列出所有不一致之处
- 每点包含代码定位和设计要求定位
- 不一致原因和详细解析

---

## 子代理 05: 电商订单服务 (08-订单服务设计.md)

### 代理标识
- **代理名称**: `agent-ecommerce-order`
- **设计文档**: `design-docs/08-订单服务设计.md`
- **目标模块**: `code/ecommerce-order`
- **引用模块**: 库存预占、支付、物流、积分协作

### 输入文件
1. `design-docs/08-订单服务设计.md` - 设计规范
2. `code/ecommerce-order/src/main/java/com/ecommerce/order/` - 所有源文件
3. `code/ecommerce-order/pom.xml` - 模块依赖配置
4. `code/pom.xml` - 父级 Maven 配置

### 检查要点
1. **订单状态**: CREATED、PAYING、PAID、PICKING、SHIPPED、DELIVERED、COMPLETED、CANCEL_REVIEWING、CANCELLED、REFUNDING、REFUNDED、CLOSED 状态机
2. **下单完整流程**: 10 步流程是否完整实现
3. **订单计价公式**: `商品总金额 = Σ(商品单价 × 数量)`、`优惠抵扣金额 = 满减优惠 + 优惠券优惠 + 会员折扣优惠`、`订单总价 = 商品总金额 + 运费 + 包装费 - 优惠抵扣金额 - 积分抵扣金额`
4. **金额精度**: 所有金额使用 `RoundingMode.HALF_UP` 四舍五入到分，订单总价不得小于 0.01
5. **订单超时**: 60 分钟未支付自动取消并释放预占库存
6. **取消规则**: CREATED 用户可直接取消、PAID 进入 CANCEL_REVIEWING 审核、SHIPPED 不可取消
7. **批量导入**: 逐条校验、合法订单逐条创建、非法订单记录失败原因跳过、不得整批回滚
8. **OrderQueryService**: getOrder、getPayableOrder、verifyPurchase、getOrderAmount 方法签名
9. **REST API**: 8 个 API 端点是否完整实现
10. **跨模块约束**: 支付服务必须通过 OrderQueryService 查询订单信息，不得直接访问订单表

### 输出要求
输出到 `/docs/04-15-check/ecommerce-order-check.md`，包含：
- 按点列出所有不一致之处
- 每点包含代码定位和设计要求定位
- 不一致原因和详细解析

---

## 子代理 06: 电商支付服务 (09-支付服务设计.md)

### 代理标识
- **代理名称**: `agent-ecommerce-payment`
- **设计文档**: `design-docs/09-支付服务设计.md` 和 `design-docs/14-发票与结算设计.md`
- **目标模块**: `code/ecommerce-payment`
- **引用模块**: `code/ecommerce-order`（OrderQueryService）

### 输入文件
1. `design-docs/09-支付服务设计.md` - 支付规范
2. `design-docs/14-发票与结算设计.md` - 发票结算规范
3. `code/ecommerce-payment/src/main/java/com/ecommerce/payment/` - 所有源文件
4. `code/ecommerce-payment/pom.xml` - 模块依赖配置
5. `code/pom.xml` - 父级 Maven 配置

### 检查要点
1. **支付规则**: 全额支付、支付金额必须等于订单应付金额
2. **支付校验**: 订单存在、状态可支付、金额一致、支付方式受支持、未重复成功
3. **支付成功流程**: 校验签名 → 更新支付单 SUCCESS → 更新订单 PAID → 触发库存扣减 → 发布 PaymentSucceededEvent 和 OrderPaidEvent
4. **后置动作**: 物流创建、积分发放、通知发送通过本地事件异步触发，后置失败不得导致支付确认失败
5. **退款流程**: 用户申请 → 商家审核退货 → 仓库验收 → 财务退款 → 发布 RefundCompletedEvent
6. **退款金额**: `退款金额 = 实付金额 × (1 - 手续费率)`，默认 2%，即 `refund = paidAmount × 0.98`
7. **发票规则**: 税率从配置 `invoice.tax-rate` 读取默认 6%、单张不超过剩余可开票金额、可开多张、累计不超过实付金额
8. **发票税额**: `税额 = 发票金额 × 税率`，按 `RoundingMode.HALF_UP` 保留两位小数
9. **结算批次**: 按自然日生成，包含支付成功未结算订单、退款和发票数据，生成后不可修改
10. **REST API**: 10 个支付/退款/发票/结算 API 端点是否完整实现
11. **跨模块约束**: 必须通过 OrderQueryService 查询订单信息，不得直接查询或更新订单数据库表

### 输出要求
输出到 `/docs/04-15-check/ecommerce-payment-check.md`，包含：
- 按点列出所有不一致之处
- 每点包含代码定位和设计要求定位
- 不一致原因和详细解析

---

## 子代理 07: 电商促销服务 (10-促销服务设计.md)

### 代理标识
- **代理名称**: `agent-ecommerce-promotion`
- **设计文档**: `design-docs/10-促销服务设计.md`
- **目标模块**: `code/ecommerce-promotion`
- **引用模块**: 无直接引用

### 输入文件
1. `design-docs/10-促销服务设计.md` - 设计规范
2. `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/` - 所有源文件
3. `code/ecommerce-promotion/pom.xml` - 模块依赖配置
4. `code/pom.xml` - 父级 Maven 配置

### 检查要点
1. **优惠券类型**: DISCOUNT（折扣券）、AMOUNT_OFF（立减券）、THRESHOLD_OFF（满减券）
2. **折扣券计算**: `折后价 = 原价 × discountValue`、`优惠金额 = 原价 × (1 - discountValue)`，8 折券 discountValue=0.8
3. **优惠券校验顺序**: 存在性 → 有效期 → 使用门槛 → 商品适用性 → 用户限制 → 已使用
4. **优惠叠加顺序**: 满减活动 → 优惠券折扣 → 会员专属折扣，后一步基于前一步结果
5. **满减规则**: 按订单金额阶梯匹配，取最优减免金额，直接抵扣
6. **会员折扣规则**: `折后价 = 上一步结果 × member.discount-rate`、`优惠金额 = 上一步结果 × (1 - member.discount-rate)`，默认 discount-rate=0.95
7. **秒杀规则**: 活动进行中、SKU 参与、用户未超限购、秒杀库存充足、秒杀价格不参与普通满减
8. **REST API**: 6 个 API 端点（coupons CRUD、calculate、full-reductions、seckill）是否完整实现

### 输出要求
输出到 `/docs/04-15-check/ecommerce-promotion-check.md`，包含：
- 按点列出所有不一致之处
- 每点包含代码定位和设计要求定位
- 不一致原因和详细解析

---

## 子代理 08: 电商物流服务 (11-物流服务设计.md)

### 代理标识
- **代理名称**: `agent-ecommerce-logistics`
- **设计文档**: `design-docs/11-物流服务设计.md`
- **目标模块**: `code/ecommerce-logistics`
- **引用模块**: `code/ecommerce-order`（监听 OrderPaidEvent）

### 输入文件
1. `design-docs/11-物流服务设计.md` - 设计规范
2. `code/ecommerce-logistics/src/main/java/com/ecommerce/logistics/` - 所有源文件
3. `code/ecommerce-logistics/pom.xml` - 模块依赖配置
4. `code/pom.xml` - 父级 Maven 配置

### 检查要点
1. **发货流程**: 支付确认 → 生成拣货单 → 仓库人员拣货 → 打印物流面单 → 扫码出库 → 物流揽收 → 更新物流状态
2. **流程约束**: 不得跳过拣货单和面单步骤直接出库
3. **物流状态**: CREATED、PICKING、LABEL_PRINTED、OUTBOUND、COLLECTED、IN_TRANSIT、DELIVERED、EXCEPTION
4. **状态更新**: 物流状态变更后必须通过 `OrderLogisticsStatusUpdater` 更新对应订单的物流状态
5. **运费规则**: 默认运费 8 元，订单商品金额满 199 元免运费，运费模板可按省份/重量/商品件数配置
6. **事件驱动**: 订单支付成功后通过监听 `OrderPaidEvent` 创建发货单，不应由订单服务同步调用
7. **REST API**: 6 个 API 端点是否完整实现

### 输出要求
输出到 `/docs/04-15-check/ecommerce-logistics-check.md`，包含：
- 按点列出所有不一致之处
- 每点包含代码定位和设计要求定位
- 不一致原因和详细解析

---

## 子代理 09: 电商积分与会员服务 (12-积分与会员服务设计.md)

### 代理标识
- **代理名称**: `agent-ecommerce-loyalty`
- **设计文档**: `design-docs/12-积分与会员服务设计.md`
- **目标模块**: `code/ecommerce-loyalty`
- **引用模块**: `code/ecommerce-order`（订单查询）

### 输入文件
1. `design-docs/12-积分与会员服务设计.md` - 设计规范
2. `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/` - 所有源文件
3. `code/ecommerce-loyalty/pom.xml` - 模块依赖配置
4. `code/pom.xml` - 父级 Maven 配置

### 检查要点
1. **积分赚取**: `订单积分 = 订单实付金额 × 会员等级倍率 × 活动系数`，活动系数默认 1.0
2. **积分抵扣规则**: 最多 10,000 积分（等值 100 元）、抵扣不超过订单实付金额 50%、兑换比例 100 积分=1 元
3. **抵扣计算公式**: `按比例可用积分 = 订单金额 × 100 × 0.5`、`实际可用积分 = min(用户可用积分, 10000, 按比例可用积分)`、`抵扣金额 = 实际可用积分 / 100`
4. **积分过期**: 有效期 12 个自然月，每月 1 号凌晨批量扫描过期积分，抵扣时不得使用已过期积分
5. **会员等级**: NORMAL(1.0)、SILVER(1.1 年消费满 1000)、GOLD(1.2 年消费满 5000)、PLATINUM(1.5 年消费满 20000)
6. **跨模块约束**: 不得直接访问订单表或订单模块 Repository，必须通过 OrderQueryService 或订单销售统计接口获取累计消费金额
7. **REST API**: 5 个 API 端点（points、estimate-redeem、history、member-level、expire）是否完整实现

### 输出要求
输出到 `/docs/04-15-check/ecommerce-loyalty-check.md`，包含：
- 按点列出所有不一致之处
- 每点包含代码定位和设计要求定位
- 不一致原因和详细解析

---

## 子代理 10: 电商评价服务 (13-评价服务设计.md)

### 代理标识
- **代理名称**: `agent-ecommerce-review`
- **设计文档**: `design-docs/13-评价服务设计.md`
- **目标模块**: `code/ecommerce-review`
- **引用模块**: `code/ecommerce-order`（verifyPurchase）

### 输入文件
1. `design-docs/13-评价服务设计.md` - 设计规范
2. `code/ecommerce-review/src/main/java/com/ecommerce/review/` - 所有源文件
3. `code/ecommerce-review/pom.xml` - 模块依赖配置
4. `code/pom.xml` - 父级 Maven 配置

### 检查要点
1. **评价前提**: 用户已登录、已购买该商品（通过订单服务验证）、订单状态为 DELIVERED 或 COMPLETED、同一订单明细只能发表一次主评价
2. **验证接口**: `GET /api/v1/orders/verify-purchase?userId=&productId=`
3. **审核流程**: 用户提交 → 敏感词过滤 → PENDING_REVIEW → 管理员审核 → 审核通过展示 → 发布 ReviewApprovedEvent → 积分服务发放评价奖励
4. **敏感词过滤**: 采用包含匹配，只要评价内容包含任一敏感词即不得直接进入 APPROVED，应进入 PENDING_REVIEW 或 REJECTED
5. **评价状态**: PENDING_REVIEW、APPROVED、REJECTED、HIDDEN
6. **跨模块约束**: 必须调用订单服务验证购买记录，不得允许未购买用户评价
7. **REST API**: 6 个 API 端点（reviews POST、append POST、product/{productId} GET、my GET、approve POST、reject POST）是否完整实现

### 输出要求
输出到 `/docs/04-15-check/ecommerce-review-check.md`，包含：
- 按点列出所有不一致之处
- 每点包含代码定位和设计要求定位
- 不一致原因和详细解析

---

## 子代理 11: 电商通用模块 (15-本地通知组件设计.md)

### 代理标识
- **代理名称**: `agent-ecommerce-common`
- **设计文档**: `design-docs/15-本地通知组件设计.md`
- **目标模块**: `code/ecommerce-common`
- **引用模块**: 所有业务模块使用 LocalNotificationService

### 输入文件
1. `design-docs/15-本地通知组件设计.md` - 设计规范
2. `code/ecommerce-common/src/main/java/com/ecommerce/common/` - 所有源文件
3. `code/ecommerce-common/pom.xml` - 模块依赖配置
4. `code/pom.xml` - 父级 Maven 配置

### 检查要点
1. **模块位置**: 本地通知组件位于 common 模块
2. **通知类型**: EMAIL（注册激活、发票通知）、SMS（支付成功、发货提醒）、IN_APP（订单状态、退款状态）
3. **NotificationRequest 字段**: bizType、bizId、receiver、channel、templateCode、variables、idempotencyKey
4. **发送规则**: idempotencyKey 去重 → 渲染模板 → 调用 MockMailSender/MockSmsSender → 记录发送日志 → 失败记录原因但不影响主流程
5. **统一处理**: 所有业务模块必须通过 LocalNotificationService 发送通知，不得直接调用邮件/短信服务

### 输出要求
输出到 `/docs/04-15-check/ecommerce-common-check.md`，包含：
- 按点列出所有不一致之处
- 每点包含代码定位和设计要求定位
- 不一致原因和详细解析

---

## 执行策略

### 分批启动顺序
```
第一批（2个）: agent-ecommerce-user, agent-ecommerce-product
第二批（2个）: agent-ecommerce-inventory, agent-ecommerce-cart
第三批（2个）: agent-ecommerce-order, agent-ecommerce-payment
第四批（2个）: agent-ecommerce-promotion, agent-ecommerce-logistics
第五批（2个）: agent-ecommerce-loyalty, agent-ecommerce-review
第六批（1个）: agent-ecommerce-common
```

### 子代理通用指令
1. 严格遵循对应的设计文档，仔细检查所有设计要求
2. 只检查设计文档中的要求，其他 bug 或未实现功能不检查
3. 不允许修改代码，只能检查并输出结果
4. 每个子代理完成任务后立即将结果输出到结果文件
5. 结果文件格式：每个不一致点包含代码定位、设计要求定位、原因解析

### 输出文件命名规范
```
/docs/04-15-check/ecommerce-user-check.md
/docs/04-15-check/ecommerce-product-check.md
/docs/04-15-check/ecommerce-inventory-check.md
/docs/04-15-check/ecommerce-cart-check.md
/docs/04-15-check/ecommerce-order-check.md
/docs/04-15-check/ecommerce-payment-check.md
/docs/04-15-check/ecommerce-promotion-check.md
/docs/04-15-check/ecommerce-logistics-check.md
/docs/04-15-check/ecommerce-loyalty-check.md
/docs/04-15-check/ecommerce-review-check.md
/docs/04-15-check/ecommerce-common-check.md
```