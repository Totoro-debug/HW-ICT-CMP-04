# M11 review-service 一致性审查报告

模块：M11 review-service  
模块目录：`code/ecommerce-review/`  
包名：`com.ecommerce.review`

## 审查结论

发现与本模块相关的文档不一致 3 条。

## 不一致项

### 1. 创建评价未校验用户已购买且订单已签收

1. 实现位置：`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:60-101`
2. 设计依据：`design-docs/01-项目概述.md` 关键业务原则，`49-60` 行，尤其 `60` 行：`评价必须校验用户已购买且订单已签收。`
3. 不一致内容：`createReview` 仅校验评分范围、同一订单明细是否重复评价、敏感词，然后直接使用请求中的 `productId`、`orderId`、`orderItemId` 创建评价；未看到对“当前用户是否已购买该商品”以及“对应订单是否已签收”的校验。
4. 原因分析与影响：实现把请求中的订单和商品信息作为可信输入保存，未按设计验证购买和签收事实。未购买商品、未签收订单或伪造订单/订单明细的用户可能成功提交评价，破坏评价业务准入规则。

### 2. 未按文档使用评价准入错误码 `REVIEW_PURCHASE_REQUIRED`

1. 实现位置：`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:60-101`；同模块内未发现 `REVIEW_PURCHASE_REQUIRED` 使用。
2. 设计依据：`README.md` 错误码，`200-230` 行，尤其 `228` 行：`REVIEW_PURCHASE_REQUIRED | 403 | 未购买商品不可评价`；通用错误码见 `README.md:202-212`。
3. 不一致内容：创建评价路径没有在购买/签收校验失败时返回文档指定的 `REVIEW_PURCHASE_REQUIRED`。当前实现只在评分、重复评价、敏感词等条件下抛出其他业务异常，缺少评价准入失败对应的冻结错误码。
4. 原因分析与影响：黑盒调用未购买或未满足评价准入条件的创建评价 API 时，无法得到文档要求的 `403 REVIEW_PURCHASE_REQUIRED`；要么错误地创建评价，要么返回非契约错误码，导致客户端和测试 harness 无法按冻结错误码处理。

### 3. 分页响应 DTO 增加了统一分页结构之外的字段

1. 实现位置：`code/ecommerce-review/src/main/java/com/ecommerce/review/dto/ReviewListResponse.java:9-35`；`code/ecommerce-review/src/main/java/com/ecommerce/review/service/ReviewService.java:168-172`
2. 设计依据：`design-docs/01-项目概述.md` 统一数据格式/统一响应格式，`71-83` 行：分页使用并返回 `page`、`size`、`total`、`items`；`README.md` API 基线，`73-75` 行要求不得修改 Request/Response 字段名和类型、成功状态码、错误响应结构。
3. 不一致内容：`ReviewListResponse` 继承统一分页响应之外，额外定义并可能返回 `averageRating`、`totalReviews` 字段；商品评价列表接口在服务层设置了这两个额外字段。
4. 原因分析与影响：评价分页接口响应结构超出文档明确给出的统一分页结构，可能破坏冻结 API 响应字段约定；严格按 `page`、`size`、`total`、`items` 解析或断言响应字段的客户端/测试可能出现不匹配。

## 已核对但未发现不一致的范围

- README 评价 API 冻结项中列出的 URL、HTTP Method、认证要求、成功状态，与控制器映射一致：`POST /api/v1/reviews`、`POST /api/v1/reviews/{reviewId}/append`、`GET /api/v1/reviews/product/{productId}`、`GET /api/v1/reviews/my`、`POST /api/v1/admin/reviews/{reviewId}/approve`、`POST /api/v1/admin/reviews/{reviewId}/reject`。
- M11 职责中的评价、追评、图片评价、审核、敏感词、评价奖励均存在对应实现入口或实体/服务支撑；上述不一致项仅列出与文档明确要求冲突的部分。
