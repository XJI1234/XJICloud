# web2 Agent 手册

改 `@xjicloud/web2` 前先读 [docs/WEB2_FRONTEND.md](../../docs/WEB2_FRONTEND.md)。本文件是硬约束，不是摘要。

通用前端 DDD 术语见仓库技能 `.agents/skills/frontend-ddd/SKILL.md`。落地以 **本包实际分层** 为准。

## 任务边界

- 用户前端 DDD 产品面：只动 `apps/web2/`，除非用户点名改 `apps/web` 或后端。
- 不要把 PLY/SPZ 迁到 OSS。
- 不要把默认 `pnpm dev` 改成 web2，除非用户要求。
- 不要改 `packages/spark`、`rust/`，除非任务要求。

## 分层（必须）

| 层 | 放什么 | 禁止 |
|----|--------|------|
| `features/*/domain` | 实体、不变量、port 接口、纯函数领域服务 | `vue`、`fetch(`、`infrastructure` |
| `features/*/application` | 用例函数，依赖 port | 页面组件、DTO 类型 |
| `features/*/infrastructure` | HTTP、mapper、SSE、XHR、postMessage | 被 presentation 直接 import |
| `features/*/presentation` | Vue + composable | `fetch`、OpenAPI/DTO、`response.data.map` |
| `src/presentation` | 壳、按钮、样式、`formatDomainError` | 业务规则 |

新能力：先定限界上下文与用例名，再写实体 / port，再写 mapper，最后写页面。

## 组合根

- 组装仓库只允许 `src/app/create-container.ts`。
- 页面通过 `inject(CONTAINER_KEY)` 的 **composable** 拿端口，不要在 `.vue` 里 `inject` 后直接调 HTTP。
- 路由守卫用 `getContainer()`（`app/runtime.ts`），不要 new 第二份容器。

## 返回值

仓库与用例返回 `Result<T> = [error, data]`（`ok` / `err`）。UI 用 `formatDomainError(t, error)`。不要把异常直接扔进模板。

## 导入

- Vue SFC 里用 `@/features/{bc}/...` 与 `@/shared/...`。相对路径 `../../domain` 会落到错误目录。
- Phosphor：`PhHouse`、`PhCube`，不要无前缀导出。
- composable 禁止 import `mappers/` 或 `*Dto`。

## 产品约定（web2）

- 浅色壳 + 暗 3D 画布。按钮只有 Primary / Ghost / Destructive。
- 查看器 `/app/layer`：`NativeSplatViewport` + SparkControls；涂抹/标注/导出在 SuperSplat。
- 高级编辑 `/app/supersplat`：无云端模型也要能进；本地 PLY/SPZ 合法即可打开。
- i18n 在 `src/app/i18n/locales/`；用户可见文案不要用 `—`。

## 验证

```bash
pnpm test:web2
pnpm build:web2
```

UI 改完后：有浏览器就走主路径；后端挂了要写明未做 E2E，不要假装测过登录后的云端流。
