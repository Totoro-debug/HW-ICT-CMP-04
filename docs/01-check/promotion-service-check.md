# M8 promotion-service 一致性审查报告

审查范围：`code/ecommerce-promotion/`、`code/pom.xml`，以及题目允许读取的 `README.md` / `design-docs/01-项目概述.md` 指定行范围。

发现与本模块相关的文档不一致 5 条。

## 1. USER 认证接口未使用认证用户身份，而是固定为 userId=1

1. 实现位置：
   - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/PromotionController.java:53-59`
   - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/PromotionController.java:66-69`
   - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/PromotionController.java:100-108`
   - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/PromotionController.java:111-118`
2. 设计依据：
   - `README.md` API 基线促销部分 `156-165` 行：`/api/v1/promotions/coupons/claim`、`/api/v1/promotions/coupons/my`、`/api/v1/promotions/calculate` 均要求 `USER` 认证。
   - `README.md` 修改边界 `35-38` 行：不得修改 Request Header / API 版本前缀等冻结契约。
3. 不一致内容：
   - 用户侧促销接口通过 `extractUserId()` 获取用户，但该方法直接 `return 1L`，并未从已认证的 USER 身份中读取当前用户。
4. 原因分析与影响：
   - 这使领取优惠券、查看“我的优惠券”、促销计算等 USER 认证接口无法按真实登录用户隔离数据；所有请求都会落到固定用户 1，导致用户 A 可能读取或影响用户 1 的促销数据，违背 USER 认证接口的语义。

## 2. 优惠券有效性校验缺失，过期优惠券未按 COUPON_EXPIRED 处理

1. 实现位置：
   - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationService.java:119-137`
   - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponValidator.java:32-38`
   - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponService.java:39-58`
2. 设计依据：
   - `design-docs/01-项目概述.md` 关键业务原则 `49-52` 行：商品下单前必须校验促销有效性。
   - `README.md` 错误码 `214-227` 行：`COUPON_EXPIRED` 的 HTTP 状态为 400，说明为“优惠券已过期”。
   - `design-docs/01-项目概述.md` M8 职责 `32-43` 行：promotion-service 负责优惠券。
3. 不一致内容：
   - 促销计算中调用 `couponValidator.validate(userCoupon)`，但 `CouponValidator` 只检查 `userCoupon` 非空和模板存在，没有检查优惠券模板的 `startTime` / `endTime`、模板状态、用户券状态是否过期，也没有在过期时抛出 `COUPON_EXPIRED`。
   - 领取优惠券时 `CouponService.claim` 只检查模板状态、领取限制和总量，也未检查模板有效期。
4. 原因分析与影响：
   - 下单前促销计算可能接受已过期或尚未生效的优惠券；过期场景也不会返回冻结错误码 `COUPON_EXPIRED`。这会导致订单优惠金额计算错误，并破坏黑盒用例对错误码和促销有效性的预期。

## 3. 满减活动计算未校验活动有效时间

1. 实现位置：
   - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/FullReductionService.java:56-59`
   - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/FullReductionService.java:65-84`
   - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationService.java:54-58`
2. 设计依据：
   - `design-docs/01-项目概述.md` 关键业务原则 `49-52` 行：商品下单前必须校验促销有效性。
   - `design-docs/01-项目概述.md` M8 职责 `32-43` 行：promotion-service 负责满减。
3. 不一致内容：
   - `FullReductionService.listActive()` 仅按 `status = ACTIVE` 查询；`calculateBestReduction()` 只比较订单金额与门槛金额，没有校验 `startTime` / `endTime` 是否覆盖当前时间。
4. 原因分析与影响：
   - 已设置为 ACTIVE 但尚未开始或已经结束的满减活动仍可能参与下单前促销计算，造成订单应付金额偏低或偏高，违背“下单前必须校验促销有效性”的要求。

## 4. M8 职责中的“阶梯价”未体现在促销计算实现中

1. 实现位置：
   - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationService.java:46-81`
   - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/dto/PromotionCalculateRequest.java:14-19`
   - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/dto/PromotionCalculateResponse.java:11-17`
2. 设计依据：
   - `design-docs/01-项目概述.md` M8 职责 `32-43` 行：promotion-service 职责包含“阶梯价”。
3. 不一致内容：
   - 促销计算只包含会员折扣、满减和优惠券折扣；请求 / 响应 DTO 也没有阶梯价相关字段或折扣明细，模块内未体现阶梯价职责。
4. 原因分析与影响：
   - 当订单商品数量或金额满足阶梯价条件时，promotion-service 无法计算或返回阶梯价优惠，导致 M8 职责覆盖不完整，相关下单金额会与设计预期不一致。

## 5. M8 职责中的“叠加规则”未作为规则进行校验或表达

1. 实现位置：
   - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationService.java:50-66`
   - `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationService.java:111-138`
2. 设计依据：
   - `design-docs/01-项目概述.md` M8 职责 `32-43` 行：promotion-service 职责包含“叠加规则”。
3. 不一致内容：
   - 促销计算固定按“会员折扣 → 满减 → 优惠券”顺序全部叠加；优惠券循环中也直接累加多个优惠券折扣，没有叠加规则模型、互斥/可叠加校验或规则输出。
4. 原因分析与影响：
   - 设计将“叠加规则”列为 promotion-service 职责，但实现无法表达或校验不同促销之间、多个优惠券之间的叠加约束。若存在不可叠加或优先级规则，当前实现会产生错误优惠金额。
