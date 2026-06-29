# 第5批评价服务修复结果

## 负责模块与 ID

- 评价服务
- `R-REVIEW-01`：敏感词检测采用完全相等匹配
- `R-REVIEW-02`：含敏感词评价被直接拒绝提交

## 修改的主要文件

- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-review\src\main\java\com\ecommerce\review\service\SensitiveWordFilter.java`
- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-review\src\main\java\com\ecommerce\review\service\ReviewService.java`
- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-review\src\test\java\com\ecommerce\review\service\SensitiveWordFilterTest.java`
- `D:\Desktop\work\HW-ICT-CMP-04\code\ecommerce-review\src\test\java\com\ecommerce\review\service\ReviewServiceTest.java`
- `D:\Desktop\work\HW-ICT-CMP-04\docs\04-15-check\checklist.md`

## 修复摘要

### R-REVIEW-01

- `SensitiveWordFilter.containsSensitiveWord` 从整段内容与敏感词 `equals` 改为 `content.contains(word)`。
- `SensitiveWordFilter.filter` 从完全相等时替换改为包含命中时用 `replace(word, "***")` 替换所有命中片段。
- 增加 `null`、空内容和空敏感词保护，避免无效数据触发异常。
- 调整单测覆盖：完整敏感词命中、嵌入敏感词命中、正常内容不命中、嵌入敏感词替换为 `***`。

### R-REVIEW-02

- `ReviewService.createReview` 保留评分、用户状态、购买/签收校验、订单匹配校验、重复评价校验等前置校验。
- 移除主评价命中敏感词时抛出 `SENSITIVE_CONTENT` 的中断逻辑。
- 创建评价前仍调用敏感词过滤器，保存过滤后的内容，并继续设置 `ReviewStatus.PENDING_REVIEW`。
- 补充单测覆盖含敏感词评价保存过滤内容并保持 `PENDING_REVIEW`。
- 公开商品评价查询继续通过 `findByProductIdAndStatus(productId, ReviewStatus.APPROVED, pageable)` 限定仅展示已审核通过评价，已有测试覆盖未审核评价不会通过公开列表返回。

## 已执行测试命令与结果

- `mvn -f "D:\Desktop\work\HW-ICT-CMP-04\code\pom.xml" -pl ecommerce-review test`
  - 结果：失败；PowerShell 工具只返回退出码。随后读取 surefire dump，定位为 Windows/JDK surefire boot classpath 路径检查问题：`Boot Manifest-JAR contains absolute paths ... Hint: <argLine>-Djdk.net.URLClassPath.disableClassPathURLCheck=true</argLine>`。
- `mvn -B -f /d/Desktop/work/HW-ICT-CMP-04/code/pom.xml -pl ecommerce-review -Djdk.net.URLClassPath.disableClassPathURLCheck=true test`
  - 初次结果：失败；业务代码已编译，失败原因是测试调整后空内容提前返回，原测试 stub 不再被调用，触发 Mockito `UnnecessaryStubbingException`。
  - 修正测试 stub 后重跑结果：成功。
  - 最终结果：`Tests run: 47, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。

## 未完成项、风险与后续协调

- 本批评价服务 `R-REVIEW-01`、`R-REVIEW-02` 已完成。
- 未修改 `design-docs`，未修改 README 冻结 REST API 契约。
- 当前敏感词匹配按设计实现为大小写敏感的简单包含匹配；大小写、全半角、规避写法等增强不在本次 `R-REVIEW-01` 范围内。
- 评价购买资格仍依赖订单服务 `OrderQueryService.verifyPurchase(userId, productId)` 返回已购买且订单匹配；本批未改动订单侧购买/签收判断。
- 与 loyalty 的后续协调：评价审核通过后的积分奖励仍由既有审核通过事件链路处理，本批未改动审核通过和积分发放逻辑。
