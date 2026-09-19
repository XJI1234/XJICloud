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
