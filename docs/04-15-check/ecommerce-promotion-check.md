# 电商促销服务一致性检查报告

- 检查对象：`design-docs/10-促销服务设计.md`
- 代码范围：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/`
- 输出文件：`docs/04-15-check/ecommerce-promotion-check.md`
- 不一致点数量：5
- 无法确认项：0

## 不一致点

### 1. 折扣券优惠金额计算公式与设计不一致

- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponService.java:81`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponService.java:83`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponService.java:84`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponService.java:91`
- 设计要求定位：`design-docs/10-促销服务设计.md:19`
- 设计要求定位：`design-docs/10-促销服务设计.md:21`
- 设计要求定位：`design-docs/10-促销服务设计.md:26`

**不一致原因：** 设计规定折扣券 `discountValue` 表示折扣率，折后价应为 `原价 × discountValue`，优惠金额应为 `原价 × (1 - discountValue)`；但代码先计算 `rate = 1 - discountValue`，再将 `price × rate` 当作折后价，最终返回 `price - afterDiscount`，等价于返回 `price × discountValue` 作为优惠金额。

**详细解析：** 以设计示例 8 折券 `discountValue=0.8`、原价 100 为例，设计要求折后价为 80、优惠金额为 20。当前代码中 `rate=0.2`，`afterDiscount=100×0.2=20`，返回优惠金额 `100-20=80`。这会把应付折后价误算为优惠金额，导致折扣券优惠金额过大。

### 2. 优惠券校验顺序和校验项不完整

- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:144`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:149`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:153`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponValidator.java:32`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponValidator.java:40`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponValidator.java:44`
- 设计要求定位：`design-docs/10-促销服务设计.md:30`
- 设计要求定位：`design-docs/10-促销服务设计.md:32`

**不一致原因：** 设计要求校验顺序固定为：存在性 → 有效期 → 使用门槛 → 商品适用性 → 用户限制 → 已使用。当前计算流程先按 `couponId` 查 `UserCoupon`，随后立即校验 `userId` 归属，再调用 `CouponValidator.validate`。`CouponValidator` 中又先检查用户券状态是否 `AVAILABLE`，再检查模板状态和时间有效期；使用门槛与商品适用性不在该校验器中按顺序校验。

**详细解析：** 当前实现把“用户限制”放在有效期之前，并把“已使用/状态不可用”放在有效期、使用门槛、商品适用性之前；同时没有看到基于当前商品的适用性校验逻辑。虽然 `CouponTemplate` 存在 `applicableCategoryIds`、`applicableProductIds` 字段，但计算请求只传 SKU、价格、数量，当前校验流程未按设计顺序验证商品适用性。

### 3. 优惠叠加顺序与设计固定顺序不一致

- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:71`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:73`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:74`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:77`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:79`
- 设计要求定位：`design-docs/10-促销服务设计.md:41`
- 设计要求定位：`design-docs/10-促销服务设计.md:45`
- 设计要求定位：`design-docs/10-促销服务设计.md:49`

**不一致原因：** 设计规定固定叠加顺序为“满减活动 → 优惠券折扣 → 会员专属折扣”，且后一步基于前一步结果计算。当前代码创建 `StackingContext` 后，先执行会员折扣，再执行满减，最后执行优惠券折扣。

**详细解析：** 当前实现会使会员折扣基于商品原始总额计算，而设计要求会员折扣应基于“满减后、优惠券后”的结果计算。对于设计示例“商品金额 300，满减 30，8 折券，会员 95 折”，设计流程应先变为 270，再优惠券后为 216，再会员后为 205.20。当前流程会先对 300 计算会员优惠，再进行满减和优惠券，计算基础和各优惠金额归属都与设计不一致。

### 4. 秒杀用户限购校验缺失

- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/SeckillActivity.java:33`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/SeckillService.java:57`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/SeckillService.java:58`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/SeckillService.java:73`
- 设计要求定位：`design-docs/10-促销服务设计.md:73`
- 设计要求定位：`design-docs/10-促销服务设计.md:75`
- 设计要求定位：`design-docs/10-促销服务设计.md:79`

**不一致原因：** 设计要求秒杀订单必须校验“用户未超过限购数量”。实体中有 `perUserLimit` 字段，但 `validateSeckill` 方法签名仅接收 `skuId`，没有接收 `userId` 或购买数量，也没有查询用户已购数量，因此无法执行用户限购校验。

**详细解析：** 当前秒杀校验覆盖了活动状态/时间窗口、SKU 参与活动和库存充足，但缺少面向用户维度的限购判断。即使创建秒杀活动时配置了 `perUserLimit`，当前校验方法也无法判断某个用户是否超过限购数量。

### 5. 秒杀价格不参与普通满减的规则未在促销计算中体现

- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/dto/PromotionCalculateRequest.java:24`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/dto/PromotionCalculateRequest.java:26`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/dto/PromotionCalculateRequest.java:29`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/dto/PromotionCalculateRequest.java:32`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:69`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:74`
- 设计要求定位：`design-docs/10-促销服务设计.md:73`
- 设计要求定位：`design-docs/10-促销服务设计.md:81`

**不一致原因：** 设计要求“秒杀价格不参与普通满减”。当前促销计算请求的单项仅包含 `skuId`、`price`、`quantity`，没有标识该项是否为秒杀商品/秒杀价格；计算逻辑直接用全部商品金额计算 `itemTotal`，随后将当前金额传入 `fullReductionService.calculateBestReduction`。

**详细解析：** 因为计算模型没有秒杀标记或排除字段，满减计算无法区分普通商品金额和秒杀价格金额。只要秒杀商品以普通 `CalculateItem` 进入促销计算，其金额就会被纳入 `itemTotal` 并参与满减阶梯匹配，违反“秒杀价格不参与普通满减”的设计规则。

## 未发现不一致的检查点

### 1. 优惠券类型

- 设计要求定位：`design-docs/10-促销服务设计.md:11`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/CouponType.java:6`

未发现不一致。`CouponType` 包含 `DISCOUNT`、`AMOUNT_OFF`、`THRESHOLD_OFF` 三种类型。

### 5. 满减规则

- 设计要求定位：`design-docs/10-促销服务设计.md:51`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/FullReductionService.java:69`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/FullReductionService.java:82`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/FullReductionService.java:83`

未发现不一致。`calculateBestReduction` 按订单金额与活动门槛比较，并选取最大 `reductionAmount` 作为最优减免金额。

### 6. 会员折扣规则

- 设计要求定位：`design-docs/10-促销服务设计.md:55`
- 设计要求定位：`design-docs/10-促销服务设计.md:62`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:112`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:117`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:119`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java:120`

未发现不一致。会员折扣读取运行期配置 `member.discount-rate`，默认值为 `0.95`，并按 `amount - amount × rate` 计算优惠金额。叠加顺序问题已在不一致点 3 单独记录。

### 8. REST API 完整性

- 设计要求定位：`design-docs/10-促销服务设计.md:83`
- 设计要求定位：`design-docs/10-促销服务设计.md:85`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/AdminPromotionController.java:25`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/AdminPromotionController.java:45`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/AdminPromotionController.java:55`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/AdminPromotionController.java:66`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/PromotionController.java:35`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/PromotionController.java:58`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/PromotionController.java:71`
- 代码定位：`code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/controller/PromotionController.java:105`

未发现不一致。设计列出的 6 个端点均有对应控制器映射：创建优惠券、领取优惠券、我的优惠券、计算优惠、创建满减活动、创建秒杀活动。
