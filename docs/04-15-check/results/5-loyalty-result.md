# 第5批：积分与会员服务修复结果

## 负责模块与 R-ID

- 模块：积分与会员服务
- 范围：`R-LOYALTY-01`、`R-LOYALTY-02`、`R-LOYALTY-03`、`R-LOYALTY-04`

## 修改的主要文件

- `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/LoyaltyPointService.java`
- `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/entity/MemberLevel.java`
- `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/MemberBenefitService.java`
- `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/PointsExpireService.java`
- `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/service/PointsExpireScheduler.java`
- `code/ecommerce-loyalty/src/main/java/com/ecommerce/loyalty/repository/PointsTransactionRepository.java`
- `code/ecommerce-loyalty/src/test/java/com/ecommerce/loyalty/service/LoyaltyPointServiceTest.java`
- `code/ecommerce-loyalty/src/test/java/com/ecommerce/loyalty/service/PointsExpireServiceTest.java`
- `code/ecommerce-loyalty/src/test/java/com/ecommerce/loyalty/service/PointsExpireSchedulerTest.java`
- `code/ecommerce-loyalty/src/test/java/com/ecommerce/loyalty/entity/MemberLevelTest.java`
- `code/ecommerce-loyalty/src/test/java/com/ecommerce/loyalty/event/OrderPaidEventListenerTest.java`
- `docs/04-15-check/checklist.md`

## 修复摘要

### R-LOYALTY-01

已将订单积分赚取公式从 `paidAmount × POINTS_PER_YUAN × levelMultiplier × activityMultiplier` 修正为 `paidAmount × levelMultiplier × activityMultiplier`。`POINTS_PER_YUAN = 100` 保留给积分抵扣换算使用。

### R-LOYALTY-02

已将 `MemberLevel.GOLD` 倍率从 `1.1` 修正为 `1.2`，并同步 `MemberBenefitService` 的 GOLD 权益倍率与权益码为 `POINTS_MULTIPLIER_1_2`。

### R-LOYALTY-03

已新增 `PointsExpireScheduler`，使用 `@Scheduled(cron = "0 0 0 1 * *")` 在每月 1 号凌晨调用 `PointsExpireService.expire()`。手动 REST 过期入口保持不变。

### R-LOYALTY-04

已在 `estimateRedeemPoints` 与实际 `doRedeemPoints` 扣减前调用用户级 `PointsExpireService.expireForUser(userId)`，并新增仓储查询按用户加载已过期 EARN 流水。抵扣计算继续使用 `min(未过期可用积分, 10000, 订单金额 × 100 × 0.5)`。

## 已执行测试命令与结果

```bash
mvn -f /d/Desktop/work/HW-ICT-CMP-04/code/pom.xml -pl ecommerce-loyalty test
```

结果：通过。`Tests run: 39, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。

## 未完成项、风险或后续协调

- 未发现第5批积分与会员服务范围内未完成项。
- 多实例部署时月度过期调度可能并发触发；当前依赖 `EXPIRE` 流水按原 EARN 流水 ID 幂等跳过重复处理。
- 订单/支付链路需持续保证支付成功可靠发布 `OrderPaidEvent`，积分发放由该事件链路触发；本批未修改 order/payment 模块。
