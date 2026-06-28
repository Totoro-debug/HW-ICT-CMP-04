# 电商评价服务一致性检查报告

## 检查范围

- 设计文档：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/13-评价服务设计.md`
- 评价服务源码：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/`
- 评价服务 Maven 配置：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/pom.xml`
- 父级 Maven 配置：`D:/Desktop/work/HW-ICT-CMP-04/code/pom.xml`
- 引用模块：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order` 中的 verifyPurchase

## 不一致点

### 1. 敏感词检测采用完全相等匹配，不符合“包含匹配”要求

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/SensitiveWordFilter.java:31`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/SensitiveWordFilter.java:35`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/SensitiveWordFilter.java:50`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/SensitiveWordFilter.java:55`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/13-评价服务设计.md:36`

**不一致原因：**

设计要求敏感词过滤“采用包含匹配”，只要评价内容包含任一敏感词即命中，且明确“不得只做完全相等匹配”。但 `SensitiveWordFilter.containsSensitiveWord` 使用 `sw.getWord().equals(content)` 判断，`filter` 也使用 `sw.getWord().equals(result)` 判断，只有当整段评价内容与敏感词完全相等时才会命中。

**详细解析：**

例如敏感词为 `badword`，评价内容为 `这个商品包含 badword 内容` 时，按设计应判定命中敏感词；当前实现中 `sw.getWord().equals(content)` 为 `false`，不会触发敏感词检测。过滤方法同样只在整段内容完全等于敏感词时替换，无法处理“内容包含敏感词”的场景。因此该实现违反了设计文档对包含匹配的明确要求。

### 2. 含敏感词的评价被直接拒绝提交，未进入 PENDING_REVIEW 或 REJECTED 状态

- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:82`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:83`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:84`
- 代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:99`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/13-评价服务设计.md:24`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/13-评价服务设计.md:27`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/13-评价服务设计.md:28`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/13-评价服务设计.md:29`
- 设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/13-评价服务设计.md:36`

**不一致原因：**

设计审核流程要求“用户提交评价 → 敏感词过滤 → 状态为 PENDING_REVIEW → 管理员审核”，并规定只要内容包含敏感词，即不得直接进入 `APPROVED`，应进入 `PENDING_REVIEW` 或 `REJECTED`。当前 `ReviewService.createReview` 在检测到敏感词时直接抛出 `BusinessException("SENSITIVE_CONTENT")`，不会保存评价记录，也不会将评价置为 `PENDING_REVIEW` 或 `REJECTED`。

**详细解析：**

当前代码只有在敏感词检测未抛异常后才继续创建 `Review` 并设置 `review.setStatus(ReviewStatus.PENDING_REVIEW)`。因此一旦敏感词检测命中，评价不会进入审核流程，也不会形成带状态的评价记录。该行为与设计中的“提交后过滤并进入待审核/拒绝状态”不一致。

## 未发现不一致的检查点

1. **评价前提：用户已登录**  
   `ReviewController.createReview` 使用 `@PreAuthorize("hasRole('USER')")`，并从 `SecurityContextHolder` 获取当前用户 ID。代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/controller/ReviewController.java:46`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/controller/ReviewController.java:47`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/controller/ReviewController.java:50`。

2. **评价前提：必须通过订单服务验证已购买该商品，不得允许未购买用户评价**  
   `ReviewService` 注入 `OrderQueryService`，并在创建评价前调用 `orderQueryService.verifyPurchase(userId, request.getProductId())`；未购买或订单不匹配时抛出业务异常。代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:43`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:119`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:120`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:121`。

3. **评价前提：订单状态为 DELIVERED 或 COMPLETED**  
   订单模块 `OrderQueryServiceImpl.verifyPurchase` 只接受 `DELIVERED` 或 `COMPLETED` 状态的订单，其他状态会跳过。代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderQueryServiceImpl.java:80`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderQueryServiceImpl.java:85`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderQueryServiceImpl.java:86`。

4. **评价前提：同一订单明细只能发表一次主评价**  
   `ReviewService.createReview` 使用 `findByUserIdAndOrderItemId` 检查重复评价，存在记录时抛出 `ConflictException`。代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:77`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:78`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/repository/ReviewRepository.java:32`。

5. **验证接口：GET /api/v1/orders/verify-purchase?userId=&productId=**  
   订单控制器实现了 `GET /api/v1/orders/verify-purchase`，请求对象包含 `userId` 和 `productId`。代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java:123`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java:125`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java:126`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/dto/VerifyPurchaseRequest.java:10`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-order/src/main/java/com/ecommerce/order/dto/VerifyPurchaseRequest.java:13`。

6. **审核流程：管理员审核通过后展示，并发布 ReviewApprovedEvent**  
   管理员审核通过接口将 `PENDING_REVIEW` 状态更新为 `APPROVED`，商品评价列表只查询 `APPROVED`，审核通过后发布 `ReviewApprovedEvent`。代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewModerationService.java:47`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewModerationService.java:52`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewModerationService.java:63`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:185`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:186`。

7. **评价状态：PENDING_REVIEW、APPROVED、REJECTED、HIDDEN**  
   `ReviewStatus` 枚举完整包含设计要求的 4 个状态。代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/entity/ReviewStatus.java:8`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/entity/ReviewStatus.java:9`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/entity/ReviewStatus.java:10`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/entity/ReviewStatus.java:11`。

8. **REST API：6 个端点完整实现**  
   已实现 `POST /api/v1/reviews`、`POST /api/v1/reviews/{reviewId}/append`、`GET /api/v1/reviews/product/{productId}`、`GET /api/v1/reviews/my`、`POST /api/v1/admin/reviews/{reviewId}/approve`、`POST /api/v1/admin/reviews/{reviewId}/reject`。代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/controller/ReviewController.java:46`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/controller/ReviewController.java:65`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/controller/ReviewController.java:85`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/controller/ReviewController.java:103`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/controller/AdminReviewController.java:43`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/controller/AdminReviewController.java:62`。

## 无法确认项

1. **积分服务是否实际发放评价奖励**  
   设计要求审核通过后发布 `ReviewApprovedEvent` 并由积分服务发放评价奖励，设计要求定位：`D:/Desktop/work/HW-ICT-CMP-04/design-docs/13-评价服务设计.md:32`、`D:/Desktop/work/HW-ICT-CMP-04/design-docs/13-评价服务设计.md:33`。本次专用输入仅允许检查评价服务、父/模块 Maven 配置和订单模块 verifyPurchase；未包含积分/会员模块源码，因此只能确认评价服务在审核通过后发布事件，无法确认积分服务是否订阅并实际发放奖励。评价服务中的 `ReviewApprovedEventListener` 也明确标注为未注册到 Spring、积分由 `ecommerce-loyalty` 处理，代码定位：`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewApprovedEventListener.java:11`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewApprovedEventListener.java:13`、`D:/Desktop/work/HW-ICT-CMP-04/code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewApprovedEventListener.java:15`。

## 汇总

- 不一致点数量：2
- 无法确认项数量：1
- 报告输出路径：`D:/Desktop/work/HW-ICT-CMP-04/docs/04-15-check/ecommerce-review-check.md`
