# 第 2 批促销服务修复结果

## 负责模块与 R-... ID 列表
- 模块：促销服务
- 范围：R-PROMOTION-01、R-PROMOTION-02、R-PROMOTION-03、R-PROMOTION-04、R-PROMOTION-05

## 修改的主要文件
- `code/ecommerce-promotion/pom.xml`
- `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponService.java`
- `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/CouponValidator.java`
- `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/PromotionCalculationServiceImpl.java`
- `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/service/SeckillService.java`
- `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/entity/SeckillPurchaseRecord.java`
- `code/ecommerce-promotion/src/main/java/com/ecommerce/promotion/repository/SeckillPurchaseRecordRepository.java`
- `code/ecommerce-promotion/src/test/java/com/ecommerce/promotion/service/CouponServiceTest.java`
- `code/ecommerce-promotion/src/test/java/com/ecommerce/promotion/service/CouponValidatorTest.java`
- `code/ecommerce-promotion/src/test/java/com/ecommerce/promotion/service/PromotionCalculationServiceTest.java`
- `code/ecommerce-promotion/src/test/java/com/ecommerce/promotion/service/SeckillServiceTest.java`
- `code/ecommerce-product/src/main/java/com/ecommerce/product/query/ProductQueryService.java`
- `code/ecommerce-product/src/main/java/com/ecommerce/product/service/ProductQueryServiceImpl.java`
- `code/ecommerce-product/src/test/java/com/ecommerce/product/service/ProductQueryServiceImplTest.java`

## 每个 R-... 的修复摘要
- `R-PROMOTION-01`：`CouponService` 中折扣券公式改为“优惠 = 原价 - 原价×discountValue”，并保留 `maxDiscount` 封顶逻辑。
- `R-PROMOTION-02`：`CouponValidator` 按“存在性 → 有效期 → 门槛 → 商品适用性 → 用户限制 → 已使用”顺序校验；补充 `applicableProductIds` 与 `applicableCategoryIds` 判断。
- `R-PROMOTION-03`：`PromotionCalculationServiceImpl` 叠加顺序改为“满减 → 优惠券 → 会员折扣”，后一步基于前一步结果计算。
- `R-PROMOTION-04`：`SeckillService` 新增按 `userId + quantity` 的限购校验与购买数量记录；新增 `SeckillPurchaseRecord` / `SeckillPurchaseRecordRepository`。
- `R-PROMOTION-05`：普通满减基数会排除进行中的秒杀价行，但 `itemTotal` 仍保留全部商品金额，不增加冻结外请求字段。

## 已执行测试命令与结果
```bash
mvn -q -f code/pom.xml -pl ecommerce-promotion,ecommerce-product -am test
```

结果：通过。
- `com.ecommerce.product.service.SkuServiceTest`：15/15 通过
- `com.ecommerce.product.service.ProductSearchServiceTest`：8/8 通过
- `com.ecommerce.product.service.ProductSearchServiceDataJpaTest`：5/5 通过
- `com.ecommerce.product.service.ProductQueryServiceImplTest`：2/2 通过
- `com.ecommerce.promotion.service.CouponServiceTest$CalculateDiscount`：4/4 通过
- `com.ecommerce.promotion.service.CouponServiceTest$Claim`：3/3 通过
- `com.ecommerce.promotion.service.CouponValidatorTest`：6/6 通过
- `com.ecommerce.promotion.service.PromotionCalculationServiceTest`：5/5 通过
- `com.ecommerce.promotion.service.SeckillServiceTest`：7/7 通过

## 未完成项、风险或需要后续批次协调的事项
- 为支持 `R-PROMOTION-02`，本批新增了商品模块内部只读查询 `ProductQueryService#getCategoryIdsBySkuIds(...)`；这不是新的公共 REST API。
- 秒杀限购能力已在促销模块落地，但订单链路后续仍需接入：
  - `validateSeckill(Long skuId, Long userId, Integer quantity)`
  - `recordPurchase(Long activityId, Long userId, Long skuId, Integer quantity, Long orderId)`
- 并发下单场景下的最终限购收口仍需与后续 order/inventory 批次联调确认。
