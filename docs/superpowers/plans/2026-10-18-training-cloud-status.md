# 训练云端状态、取消及删除修复计划

用户授权：调研并按工作区约定修复 8080/8081 状态对接和 web2 显示；不要求云端复现。本文是本轮实现与测试契约，范围仅 eci-manager、backend、web2 及相关文档。不得修改未跟踪的 apps/web2/public/heif/，不得自动提交。

## 调研与选择

- backend TrainingDispatchService 对任何 create 响应直接设 RUNNING；绑定任务仅扫描 RUNNING/CANCELLED，正常轮询丢弃非终态。
- backend 取消先写 CANCELLED，隐藏的 eciCancellationPending 阻挡删除；web2 看到 CANCELLED 就停 SSE、提供删除。
- 8081 create 实际返回 STARTING；FAILED 的 cancel 返回 409；任务响应缺少实例/清理证据。
- 8081 无 cloudId 的取消判定未检查 createCallStartedAt，首次调用后崩溃恢复可能误判从未创建。

选择由 8081 提供统一快照，8080 投影、web2 展示。避免 8080 分别查询 task/instance 后复制清理判断。保留现有 JobStatus 和 ECI 枚举。删除等待真实收敛；不增加墓碑/软删除系统。不根据未知实际库存错误码猜测创建明确失败，不将空 cloudId 视为无资源。

## 接口和不变量

1. 8081 TaskResponse 新增 instanceStatus、cloudStatus、statusReason、cleanupConfirmed（boolean）；保留已有 failureReason。statusReason 来自已确认的 provider 状态说明/安全状态错误，不输出原始 SDK 异常、密钥、env、URL。实例持久化最新非终态说明，重启后可读。
2. cleanupConfirmed 仅在有可靠的生命周期证据证明资源已消失或根本未创建时为 true；缺失实例/未知 CREATE/旧数据隔离/查询失败均不可推定 true。云 Delete 受理仍需 Describe 确认。对 createCallStartedAt 非空的动作，取消必须使用同 token 恢复结果。
3. 8081 FAILED 任务 cancel 幂等登记清理，保留 FAILED、failureReason、日志、endedAt；SUCCEEDED 保持现有取消冲突语义。保持既有锁序与失败日志先取后删不变量。
4. 8080 客户端完整读取新增字段，缺失 cleanupConfirmed 按 false 保守处理。TrainingJob 持久化最新 computeStatus、cloudStatus、computeStatusReason 与 eciCleanupConfirmed（或等价最小字段实现）；REST/SSE 同步公开 computeStatus、cloudStatus、computeStatusReason、cancellationPending、cleanupPending、updatedAt。cleanupPending 表示该 attempt 可能存在资源而尚未被确认清理；不能仅凭任务终态或空 taskId 清除。
5. 8081 QUEUED/STARTING 或实例等待/创建阶段保持业务 QUEUED，用 stage/message 区分排队与启动；只有确认 RUNNING 才置业务 RUNNING。SUCCEEDED/FAILED/CANCELLED 直接投影，不能先发假 RUNNING。未知状态不宣告运行/完成。绑定 QUEUED 继续轮询；FAILED/CANCELLED 清理未完成继续轮询。
6. 用户取消本地接受后可以保留业务 CANCELLED 以兼容，但 cancellationPending/cleanupPending 为 true 时 API/UI 明确为取消中而非已完成。网络错误不能撤销已接受清理意向或冒充收敛；重复取消不重开已完成清理。不吞掉错误。Redis 移除失败不得丢失已持久化取消意向；SSE 必须在事务提交后发送，取消及新 attempt 优先于迟到响应。
7. cancel/retry/delete 保持用户归属校验。未确认清理不删除唯一任务绑定、不重试新 attempt。明确拒绝创建、无资源取消可最终收敛后删除；未知创建要保留清理责任。收敛必须发布 SSE。
8. web2 domain/application 处理取消中、可删除/重试与监听策略；infrastructure 处理新 DTO/SSE 和断流兜底。取消中或其他终态尚未清理时继续同步，清理完成才停监听/开放操作。REST/SSE 的 updatedAt 防旧状态覆盖；clear/切换工程后迟到请求不能复活记录。新增可见文案用中英文 i18n，presentation 不解析原始云生命周期规则。
9. 不做真实云端复现，不改库存资源配置、不迁移 PLY/SPZ、不改旧 web/spark/rust。工程删除绕过任务清理是调研发现的既有相邻风险，本次不扩大重构。

## 顺序与职责

### T1：8081 生命周期快照和安全取消（严格 TDD）

测试 Agent 先修改旧 FAILED cancel 契约测试并锁定新需求，增加 Pending 原因、清理证据、失败取消幂等/证据保留、Create 调用崩溃租约恢复取消等测试，运行并记录预期 RED。实现 Agent 仅改生产实现与对应 API/上下文文档，运行针对性测试、全量 Test 与显式 IT、package；不可改测试。

### T2：8080 生命周期投影与取消删除（严格 TDD）

测试 Agent 先修订旧 STARTING→RUNNING 测试以符合新授权需求并锁定，增加 DTO 解析、绑定 QUEUED 轮询、运行与失败原因、取消幂等、FAILED 清理、删除/重试门禁、迟到响应、事务提交/队列异常测试并观察 RED。实现 Agent 仅改生产与文档，保留 clientRequestId/attempt/claim fencing 与退避恢复。

### T3：web2 状态展示和可靠同步（复杂逻辑严格 TDD；文案/文档非严格）

测试 Agent 覆盖 mapper、domain policy、watch hub 取消继续监听/收敛停止、旧事件、断流/漏事件兜底及 clear 后迟到响应。实现 Agent 保持 DDD 最小改动，更新面板、双语文案、前端手册，不能修改测试。复用现有状态同步机制，避免重造状态库。

T1/T2 测试可独立编写；各任务测试→实现串行，T3 按冻结接口编写。所有阶段汇总、质量门通过后独立审查（eci-manager 与 backend/web2 各一范围）；审查者只读，给文件行号及建议。同一任务修复重审最多三轮。实现 Agent 必须完成自我 diff review。无自动 commit。

## 验证与验收

- 8081：mvn -B test；mvn -B -Dtest='*Test,*IT' test；mvn -B package（按 .tools/env.sh 设置工具链）。
- backend：mvn -B test；mvn -B package。
- web2：pnpm test:web2；pnpm build:web2。按实际 package 配置执行 lint（若有）。
- 必须保存严格任务 RED/GREEN 命令、退出码和关键断言。旧契约测试的需求变更由测试 Agent 在实现前完成，禁止实现 Agent 为求绿改测试。
- 用本地确定性替身覆盖：提交 STARTING→云 Pending/原因→Running；明确创建失败→取消→清理确认→删除；未知 CREATE→取消→恢复同 token→清理确认；取消请求失败/重复、重启恢复、旧轮询竞争。
- 有可用浏览器则走用户点击路径；否则明确报告“未做浏览器 E2E”，不得声称验证了真实云端训练。数据库新增字段需提供部署迁移说明与兼容默认值。
- 最终汇报必须有“## 验证”和“## Code Review”，明确未覆盖真云库存错误码、浏览器或生产迁移等限制。
