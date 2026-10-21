# 训练状态复核修复与最小简化实施计划

> 执行方式：subagent-driven-development；用户已在状态机复核结论后明确授权“实现”。不自动提交，不启动服务或创建云任务。

**目标：** 修复复核发现的八项正确性问题，并集中现有生命周期判断，不增加状态枚举或另建同步系统。

**依据：** `2026-10-18-training-cloud-status.md` 的接口和不变量，以及本轮用户确认的复核结论。保留已有工作区改动、锁定测试和原契约；不得改 `apps/web2/public/heif/`、旧 web、spark、rust。

**设计：** 业务结果和资源清理为独立事实。活动任务持续同步；终态仍有取消/清理责任时继续同步。请求发送证据承担未知创建责任，租约字段仅协调执行者。保留 attempt/clientRequestId/claim fencing、事务提交后 SSE、成功证据和前端作用域/连接防护。

**用户补充优先级：** 不严重的漏测可以跳过，以跑通为第一任务。后续仅为会丢失成功结果、清理责任或阻塞升级的严重问题补必要回归；不再扩展低风险覆盖、复杂并发测试或额外重构，剩余缺口如实记录。

## T1：8081 可靠清理与裁决（复杂逻辑严格 TDD）

- [x] 测试 Agent 增加并观察 RED：未知云状态不能确认删除；成功后清理延迟不能触发运行超时。三个测试类编译成功后 4 条预期断言失败。CREATE 与 refresh 的并发测试最终 2/2 GREEN；旧测试装配未可靠复现锁交错，因此此项没有有效 RED，不能声称完成完整 RED/GREEN。基线及草稿：`/tmp/eci-manager-t1-tests-20261021`。
- [x] 实现 Agent 修复上述问题，保持云调用在事务外、事务内重新加锁；统一云观察写入及 watcher/启动恢复的成功证据判断，不做无关重构。
- [x] 保持 Always 原契约；成功证据判断排除 Always，不另改产品语义。
- [x] 完成针对性测试 35/35、`mvn -B test` 283/283、`mvn -B -Dtest='*Test,*IT' test` 最终重跑 333/333、`mvn -B package` 283/283。Surefire 报告：`eci-manager/target/surefire-reports/`。显式 IT 首次运行出现 1 条自动成功测试的中间态时序错误（等待 RUNNING 时已 SUCCEEDED），重跑通过，无删改或跳过断言。

## T2：8080 同步与旧证据迁移（复杂逻辑严格 TDD）

- [x] 测试 Agent 增加并观察 RED：RUNNING/STARTING + cleanupConfirmed 后仍读取任务终态；旧活跃 claim 经迁移、取消、恢复后不丢失未知创建责任。`TrainingDispatchLegacyEvidenceTest`：6 failures、0 errors，退出码 1；证据与基线位于 `/tmp/xjicloud-backend-t2-20261021`。
- [x] 实现 Agent 修复活动任务停轮询问题；保守迁移旧请求证据，兼顾本地 H2 和部署 SQL，迁移完成前不得清除唯一证据。
- [x] 集中实体上的清理责任派生策略，让取消、门禁、REST/SSE 共用；保留既有调度契约要求的 claim 时间兼容，不强行删除。现有 fencing 和终态粘性不变。
- [x] 完成针对性测试、`mvn -B test`、`mvn -B package`，同步迁移说明。最终目标 8/8、全量及 package 89/89；日志 `/tmp/xjicloud-backend-t2-20261021/verification-results.txt`。

## T3：web2 合并与异步作用域（复杂逻辑严格 TDD）

- [x] 测试 Agent 增加并观察 RED：同版本 REST 补全；旧轮询在途的终态 SSE 详情补全及临时失败恢复；完整 Instant 精度防旧事件；上传完成晚于 clear/工程切换不得复活旧任务。针对性 Vitest：7 failures、4 passed、0 装配错误，退出码 1；证据与基线位于 `/tmp/web2-training-status-review-T3-baseline-20261021`。
- [x] 实现 Agent 修复四条路径，统一版本合并和监听协调；详情补全与业务清理收敛分开，不另造状态库。
- [x] 云生命周期显示解释集中在现有 domain policy，经 composable 暴露；Vue 只映射文案。DTO 缺字段默认值留在 mapper，保留 REST 兜底、generation、connectionId。
- [x] 完成针对性测试 19/19、`pnpm test:web2` 104/104、`pnpm build:web2` 通过。无额外 lint 配置。未做浏览器 E2E；保留既有 Vite chunk 大小提示。

## 分工及质量门

- 各模块测试与实现串行；三个模块可并行。测试 Agent 只改测试；实现 Agent 不改测试；各自保存 RED/GREEN 命令、退出码和关键断言。
- 所有任务保留本次增量基线供独立 diff 审查；不回滚已有改动，不改锁定断言求绿。
- 主 Agent 负责计划、调度和验收，不写实现代码。先通过实际配置的 lint、编译、测试，再由独立只读 Agent 统一审查；同任务修复重审至多三轮。
- 高优先级缺陷必须修复；最终报告包含 `## 验证` 与 `## Code Review`，明确未进行真云调用、生产迁移和浏览器 E2E 的范围。

## 审查记录

- T2/T3 第 1 轮：web2 修复静态成立；backend 发现 High：部署 SQL 回填没有接入本地 H2 启动升级。要求增加真实启动路径与二次启动幂等回归，确保不错误回填新版未发送请求的 claim。修复后重跑质量门并进入第 2 轮审查。
- 初次实现质量门：backend 目标 6/6、全量及 package 87/87 通过；web2 目标 19/19、全量 104/104、build 通过。无额外 lint 配置；未做浏览器 E2E。此记录不替代审查修复后的验证。
- T2/T3 第 2 轮：通过，issues 为空；确认 H2 真实启动升级、事务性一次回填标记、两服务入口顺序、布尔列默认值，以及二次启动不误回填新版 claim。
- T1 第 1 轮：要求修复 refresh 已将实例置 TERMINATED 后的成功超时误判、历史 Succeeded/false 被新观察覆盖时的成功证据丢失；同范围将 DELETE helper 预加载改成标量查询以消除一级缓存陈旧。前两项新增回归已 RED→GREEN；按用户优先级不再扩展 DELETE 复杂并发覆盖。修复后 focused 35/35 通过，等待完整质量门及第 2 轮审查。
- T1 第 2 轮：通过，issues 为空；确认有效成功证据豁免运行超时、历史成功覆盖前固化、Always 排除及 DELETE 标量预读。未做真实云验证，未做浏览器 E2E，未执行生产 PostgreSQL 迁移；没有自动 commit。

## 最终测试筛选与用户授权提交

用户随后授权筛选未提交测试、保留必要覆盖，并将测试与实现分两次提交。本轮不重新要求严格 RED；没有修改生产实现。

- ECI 候选测试由 13 文件/48 方法/2260 行收敛为 9 文件/41 方法/2034 行。合并重复脱敏样本，将 null 状态和启动恢复的独立重复测试并入保留的服务集成用例。
- backend 删除两条手工 SQL 迁移测试及辅助代码，减少 88 行；真实 Spring/H2 启动升级、第二次启动幂等和取消/恢复门禁继续覆盖同一风险。
- web2 删除重复 policy 用例、参数化 pending 门禁并共用 mapper fixture，净减少 22 行；版本合并、终态详情恢复及上传作用域回归保持。
- 当前质量门：`mvn -B -Dtest='*Test,*IT' package`（ECI）326/326；`mvn -B package`（backend）87/87；`pnpm test:web2` 104/104；`pnpm build:web2` 成功。ECI 后台 scheduler/mock 隔离噪声和 Vite 大包提示均如实保留，未绕过断言。
- 测试筛选独立审查通过，无 High/Critical；核实了删除/合并用例到保留覆盖的映射。未做浏览器 E2E、真云调用及生产迁移。
- 首次提交仅测试，第二次提交实现及配套文档。测试契约提交依赖后续实现新增类型，完整验证针对两者组合，不宣称中间提交独立可构建。
- 其他分支误留的三个 HEIF 文件单独保存到命名 stash，可恢复，不纳入本次提交。原有 `SecurityConfig.java` JSON 401 响应改动与本任务无关，原样保留为未提交内容。
