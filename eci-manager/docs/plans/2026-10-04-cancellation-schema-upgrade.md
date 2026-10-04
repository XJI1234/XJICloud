# 旧 H2 数据库取消状态迁移修复计划

**Goal:** 修复云测记录 §9 的旧库缺少 `CANCELLING` 导致取消失败的问题。

**Spec:** `docs/2026-10-03-training-dispatch-cloud-test.md` §9 与 `eci-manager/AGENTS.md`。

**Architecture:** 沿用 Spring Boot/JPA/H2 启动流程，增加最小的、幂等的旧表状态列升级，在任务恢复和生命周期处理之前完成。保持现有取消 API 与状态机，禁止删除数据库重建。

**Tech Stack:** Spring Boot 3.3.5、Java 17、Hibernate、H2 2.2.224、JUnit 5、Maven。

## 约束和验收

- 仅修改 `eci-manager/`；保留任务历史、实例记录、运行时凭据和非目标字段。
- 旧 H2 ENUM 的六个值增加 `CANCELLING`，旧状态与行内容不变，非空约束保留；新库正常启动，重复启动可安全重复升级。
- 升级必须在任何取消或启动恢复写入新状态之前完成；失败不得吞错后继续提供服务。
- 沿用 `CANCELLING → CANCELLED`，只有确认实例 `TERMINATED`/不存在后才允许取消终态。
- 无新云任务、无生产数据修改、无自动 commit/push。
- 测试与实现由分离 Agent 顺序执行；测试锁定后实现 Agent 不得改测试；独立审查只读，最多三轮修复重审。

## Task 1：旧库升级与取消回归（按用户要求简化流程）

测试 Agent 负责新回归测试及必要 fixture；实现 Agent 负责最小启动迁移、配置和相关使用文档，不改既有业务取消逻辑，除非回归证据要求。

- [x] 测试 Agent 用真实 H2 六值旧 ENUM 建表、插入历史数据，再启动实际应用；验证旧行保留和新值可写，不以 mock 数据库代替。
- [x] 覆盖升级后重复启动/执行、全新库，以及实际任务取消与 DELETE 生命周期收敛；验证 `CANCELLED` 前实例清理完成。
- [x] 实际运行新测试，编译通过，旧库取消出现 SQLState 22030 / `CANCELLING` 不被旧枚举允许；fresh DB 通过。
- [x] 实现 Agent 根据上述验收补最小升级；使用 Spring Boot SQL 初始化，无新框架或依赖。
- [x] 定向回归转绿后运行 `source ../.tools/env.sh`、`mvn -B package`（包含完整 `test` 阶段），检查实际 lint 配置和 diff 空白。
- [x] 更新 `eci-manager/AGENT_CONTEXT.md` 和 `eci-manager/docs/测试说明.md`：说明自动升级、升级前停止服务并备份旧库、真实云取消复验边界。
- [x] 汇总 diff 和 RED/GREEN 证据交独立审查 Agent；修复 High/Critical，完成主 Agent 自我审查。

## 进度与审查记录

- 基线：`2319d01`（用户已提交联调改动），工作区干净；当前分支 `merge_eci`。在当前共享工作区顺序写入，避免不必要的分支切换。
- 单任务测试、实现、审查依赖顺序明确，无并行写入。
- 基线验证：`mvn -B test`，276 tests，0 failures/errors/skipped；日志 `/tmp/eci-cancel-baseline.log`。
- 迁移可行性：独立 H2 2.2.224 内存库验证扩充 ENUM 保留旧行、支持新状态、允许重复执行，表不存在时 `ALTER TABLE IF EXISTS` 安全；未操作实际数据库。
- 实际配置无独立 lint 插件，采用编译、测试和 `git diff --check` 质量门。默认 Surefire 不包含 `*IT`，新回归须使用 `*Test`，已有取消 IT 额外定向执行。
- 用户要求按简单修复处理，不强求复现；已有失败证据足够，立即进入最小迁移实现，不再追加流程或测试范围。
- 最小实现已完成：`schema.sql` 扩充 ENUM，`spring.sql.init.mode=always`；日志确认在 EntityManagerFactory 前执行。定向 6 项中 5 项通过，剩余旧库恢复用例的 fixture 漏填 `eci_instance.task_id`，与迁移无关。
- 用户已明确授权“补齐并继续”：由测试 Agent 只补齐 fixture 关联，保留断言；再完成构建与独立审查。
- fixture 修正后新回归 3/3 通过；已有 `EciTaskCancellationIT` 定向 3/3 通过。完整 `mvn -B package`：279 tests，0 failures/errors/skipped，BUILD SUCCESS；日志 `/tmp/eci-cancellation-package.log`。`git diff --check` 通过，打包 JAR 包含迁移 SQL 与配置。
- 主 Agent 自我审查：仅本任务 SQL/配置/回归/文档；未改取消业务逻辑、未访问实际云资源或数据库、无新凭据、无删库重建。独立审查已派发，等待结论。
- 独立审查第 1 次：无 High/Critical；Medium 为新增回归测试中的首次自动调度可能与手工 DELETE 竞争。使用测试专用调度器阻止自动执行，保留断言与生产逻辑，作为验证设施修正而非契约变更；测试 Agent 负责。修复—重审轮次 1/3。
- 修复后定向 6 项与 `mvn -B package` 279 项全部通过，0 failures/errors/skipped。独立重审通过，原 Medium 已解决，无新问题。
- 完成：仅代码和隔离 H2/Mock 验证；实际数据库、浏览器取消、主后端待收敛标记与云实例释放未复验。未自动提交或重启实际服务。

## Task 2：按用户要求精简回归测试

用户已批准将新增测试精简为一条：真实旧六值 ENUM → 实际应用启动 → 新状态可写、历史数据保留。保留简单非空约束检查（无需重复异常测试）。删除本文件的新库取消、DELETE 收敛、重复启动和相关实例/action fixture；既有取消生命周期测试保持不变。生产 SQL/配置不变；此任务不要求严格 TDD，不追加复现。

- [x] 测试 Agent 精简 `LegacyCancellationSchemaUpgradeTest.java`，保留真实 H2 和自动初始化路径、测试调度隔离；删除无关 helper/import/fixture。
- [x] 同步 `docs/测试说明.md` 中本测试的覆盖描述，避免仍宣称精简后已删除的用例。
- [x] 运行定向精简回归 + 既有 `EciTaskCancellationIT`，再 `mvn -B test`（包括编译与全部默认测试）；检查 diff。
- [x] 独立审查精简 diff，检查核心回归保障未丢失，无 High/Critical；主 Agent 自我审查并汇报前后行数。

精简结果：282 行/3 例 → 112 行/1 例。定向 4 项通过，完整 277 项通过，0 failures/errors/skipped；`git diff --check` 通过。日志在 `/tmp/eci-test-simplification/`。主 Agent 自我审查确认真实旧 ENUM、启动配置、新状态写入和历史数据断言均保留；生产代码未修改。独立审查通过，无遗留问题。
