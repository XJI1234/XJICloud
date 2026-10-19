# XJICloud Agent 入口

本仓库是多人分板块维护的 **pnpm + Spring Boot monorepo**。多数维护者用 AI 改代码。**生成代码在未验证、未审查前不得视为完成。**

开始任何任务前先读：

1. [docs/TEAM_ENGINEERING.md](docs/TEAM_ENGINEERING.md) — 编程规则、强制功能验证、强制 Code Review
2. [docs/AGENT_CONTEXT.md](docs/AGENT_CONTEXT.md) — 架构、API、存储、JWT、训练流水线

然后只读任务板块的手册，不要全仓瞎改：

| 任务落在 | 再读 |
|----------|------|
| 用户产品面 | [apps/web2/AGENTS.md](apps/web2/AGENTS.md)、[docs/WEB2_FRONTEND.md](docs/WEB2_FRONTEND.md) |
| 管理端 | `apps/admin/`（保持现有简单结构，不要抄 web2 DDD） |
| 后端 | AGENT_CONTEXT §5–6、`services/backend/` |
| Worker | AGENT_CONTEXT §9、`services/gpu-worker/` |

当前用户前端是 **`apps/web2`**。`apps/web` 已弃用。不要改 `packages/spark`、`rust/`，不要把 PLY/SPZ 迁到 OSS。

## 完成任务的硬门槛

1. **最小 diff**，只改点名的板块；禁止顺手重构。
2. **跑验证**（见 TEAM_ENGINEERING §3）：web2 用 `pnpm test:web2`；admin 用 `pnpm build:admin`；后端用 Maven。UI 必须走用户路径或明确写未做 E2E。
3. **自我 Code Review**（见 TEAM_ENGINEERING §4）：对照 diff 查缺陷 / 回归 / 安全 / 缺测试。High/Critical 必须先修。回复必须带 `## 验证` 与 `## Code Review` 两块。

## 测试档案（用户长期要求，2026-10-19 起）

所有自动化测试、构建、只读诊断和真机验证必须保存可追溯记录，不能只在聊天中报告结果。web2 的 test/build 默认归档；其他命令从原工作目录使用 `node D:/Desktop/XJICloud/scripts/test-records/run.mjs --label <测试名> -- <可执行文件> <参数...>`。Windows bat/cmd 通过明确的 PowerShell 脚本调用，不拼接用户输入为 shell 代码。

真机网关自动建立会话，人工观察通过 `view.mjs mark` 补充，截图/录像用 `attach`。目录为 `D:\XJI-Test-Records`（可用 `XJI_TEST_RECORDS_ROOT` 覆盖），不删除、不轮换旧档案。结论必须区分 SDK 回执、飞机实际动作、未知与推断；未采集项目不能声称正常。详见 `docs/TEST_RECORDS.md` 和 `scripts/test-records/CONTRACT.md`。
