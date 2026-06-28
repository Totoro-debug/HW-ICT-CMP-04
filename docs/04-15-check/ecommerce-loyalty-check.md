# 电商积分与会员服务一致性检查报告

## 检查范围

- 设计文档：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/12-积分与会员服务设计.md`
- 代码目录：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/`
- 模块配置：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-loyalty/pom.xml`
- 父级配置：`D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`
- 引用模块：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order`

## 不一致点

### 1. 订单积分赚取公式多乘了 100 积分/元换算系数

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:35-39`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:223-235`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/12-积分与会员服务设计.md:11-15`
- 不一致原因：设计要求订单积分为“订单实付金额 × 会员等级倍率 × 活动系数”，活动系数默认 1.0；代码在 `calcOrderPoints` 中额外乘以 `POINTS_PER_YUAN = 100`。
- 详细解析：代码第 230-233 行计算逻辑为 `amount * 100 * levelMultiplier * requestActivityMultiplier * configuredActivityMultiplier`。按照设计文档第 12 行公式，订单实付金额不应再乘以 100。该实现会使积分发放结果扩大 100 倍，例如 NORMAL 会员、活动系数 1.0、实付 100 元，设计公式结果为 100 积分，代码结果为 10,000 积分。

### 2. GOLD 会员等级积分倍率实现为 1.1，而设计要求为 1.2

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/entity/MemberLevel.java:11-14`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/MemberBenefitService.java:16-22`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/12-积分与会员服务设计.md:57-62`
- 不一致原因：设计文档规定 GOLD 等级积分倍率为 1.2；代码中 `MemberLevel.GOLD` 的 multiplier 为 1.1，`MemberBenefitService` 返回 GOLD 会员权益时积分倍率也为 1.1。
- 详细解析：`LoyaltyPointService.calcOrderPoints` 第 226 行通过 `memberBenefitService.getPointsMultiplier(account.getMemberLevel())` 获取等级倍率，因此实际用于积分赚取的是 `MemberBenefitService` 中第 19 行的 GOLD 倍率 1.1，而不是设计要求的 1.2。同时 `MemberLevel` 枚举第 13 行也将 GOLD 声明为 1.1，导致领域枚举定义和服务权益配置均与设计不一致。

### 3. 缺少“每月 1 号凌晨批量扫描过期积分”的定时调度实现

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/PointsExpireService.java:38-87`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/controller/AdminLoyaltyController.java:31-38`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/12-积分与会员服务设计.md:49-53`
- 不一致原因：设计要求系统每月 1 号凌晨批量扫描过期积分；代码只提供 `PointsExpireService.expire()` 处理逻辑和后台手动触发 REST 接口，未发现 `@Scheduled` 或等价调度入口。
- 详细解析：`PointsExpireService.expire()` 可以扫描 `expiresAt <= now` 的 EARN 流水并扣减过期积分，但该方法本身没有定时注解；`AdminLoyaltyController` 第 34-38 行仅暴露 `POST /api/v1/admin/loyalty/points/expire` 手动触发。对指定源码目录扫描未发现 `@Scheduled`、cron 表达式或其他每月 1 号凌晨自动调用 `expire()` 的实现，因此无法满足设计中的周期性批量扫描要求。

### 4. 积分抵扣未在抵扣时按流水过期时间排除已过期积分

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:66-79`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java:122-143`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/repository/PointsTransactionRepository.java:27-36`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/12-积分与会员服务设计.md:49-53`
- 不一致原因：设计要求“积分抵扣时不得使用已过期积分”；代码估算和实际抵扣均只读取账户级 `availablePoints`，没有查询未过期 EARN 流水，也没有在抵扣时触发过期扣减或过滤 `expiresAt <= now` 的积分。
- 详细解析：`estimateRedeemPoints` 第 68-78 行使用 `account.getAvailablePoints()` 参与 `min(available, 10000, ratioCapped)`；`doRedeemPoints` 第 127 行复用该估算结果，第 134-137 行直接扣减账户可用积分。`PointsTransactionRepository` 仅提供按过期时间查询过期 EARN 的方法，但该方法只被 `PointsExpireService` 用于批处理；抵扣路径未按 `expiresAt` 判断积分是否已过期。若某用户存在已到期但尚未被批处理扣减的积分，当前抵扣逻辑仍可能将其计入 `availablePoints` 并用于抵扣。

## 未发现不一致的检查点

1. 积分抵扣规则中的单笔最多 10,000 积分、等值 100 元、抵扣不超过订单实付金额 50%、100 积分 = 1 元：`LoyaltyPointService.java:35-42`、`LoyaltyPointService.java:66-79`、`LoyaltyController.java:82-90` 与设计文档 `12-积分与会员服务设计.md:19-30` 一致。
2. 抵扣计算公式：`LoyaltyPointService.java:71-78` 实现了 `订单金额 × 100 × 0.5`、`min(用户可用积分, 10000, 按比例可用积分)`；`LoyaltyController.java:84-85` 实现了 `抵扣金额 = 实际可用积分 / 100`。
3. 会员等级门槛：`MemberLevelService.java:23-25`、`MemberLevelService.java:55-64` 实现了 SILVER 年消费满 1000、GOLD 年消费满 5000、PLATINUM 年消费满 20000。
4. 跨模块约束：`MemberLevelService.java:6`、`MemberLevelService.java:28-33`、`MemberLevelService.java:79-81` 通过 `OrderQueryService.getAnnualConsumption(userId)` 获取累计消费金额；未发现直接访问订单表或订单模块 Repository。
5. REST API 端点完整性：`LoyaltyController.java:61-78`、`LoyaltyController.java:97-126`、`AdminLoyaltyController.java:34-35` 覆盖设计文档 `12-积分与会员服务设计.md:68-74` 中 5 个端点：`GET /api/v1/loyalty/points`、`POST /api/v1/loyalty/points/estimate-redeem`、`GET /api/v1/loyalty/points/history`、`GET /api/v1/loyalty/member-level`、`POST /api/v1/admin/loyalty/points/expire`。
6. 积分有效期字段设置：`LoyaltyPointService.java:44`、`LoyaltyPointService.java:276` 为赚取流水设置 12 个月后的 `expiresAt`，与设计文档 `12-积分与会员服务设计.md:51` 的 12 个自然月要求未发现直接冲突。

## 无法确认项

- 未发现无法确认项。

## 汇总

- 不一致点数量：4
- 无法确认项数量：0
