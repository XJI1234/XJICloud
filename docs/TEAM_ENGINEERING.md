# XJICloud — 团队工程规则与强制 Code Review

> **给所有维护者与所有 AI Agent 的硬约束。** 读过 [AGENT_CONTEXT.md](./AGENT_CONTEXT.md) 仍必须遵守本文。  
> 本文优先于「尽快交差」「看起来能跑」「模型自己觉得没问题」。  
> **最后更新：** 2026-09-02

本仓库是多人分板块维护的 monorepo。多数改动由 AI 生成。**没有经过验证和审查的生成代码，一律视为未完成。**

---

## 0. Agent 开场必做

开始改代码前：

1. 读本文 + [AGENT_CONTEXT.md](./AGENT_CONTEXT.md)。
2. 只打开任务点名的板块对应手册（见 §1）。不要凭训练数据臆造目录或 API。
3. 先定位现有实现再写。禁止平行再造一套「更干净」的模块。
4. 任务结束前必须完成 **§3 功能验证** 和 **§4 自我 Code Review**。缺一不可。

改用户前端再读：[apps/web2/AGENTS.md](../apps/web2/AGENTS.md)、[WEB2_FRONTEND.md](./WEB2_FRONTEND.md)。

---

## 1. 板块边界（禁止串台）

| 板块 | 目录 | 手册 | 默认命令 |
|------|------|------|----------|
| 当前用户前端 | `apps/web2/` | `apps/web2/AGENTS.md` | `pnpm test:web2` / `pnpm build:web2` / `pnpm dev` |
| 管理前端 | `apps/admin/` | 本文 §2.3 | `pnpm build:admin` / `pnpm dev:admin` |
| 后端 API | `services/backend/` | 本文 §2.4 | `mvn -B test` 或至少 `mvn -B -DskipTests package`（有测试时禁止无故 skip） |
| GPU Worker | `services/gpu-worker/` | AGENT_CONTEXT §9 | 改后说明如何验证（镜像构建 / 心跳 / 领任务） |
| 共享类型 | `packages/shared/` | — | 变更必须同步所有调用方 |
| Spark / WASM | `packages/spark/`、`rust/` | — | **默认禁止**，除非任务点名 |
| 旧用户前端 | `apps/web/` | — | **已弃用。禁止为新功能改它** |
| 部署 | `deploy/` | `docs/Deploy.md` | 不要把本地路径、密钥写进清单 |

规则：

- **只改任务需要的板块。** 不要「顺手」重构相邻包、统一命名、升级依赖、重排目录。
- 前端契约变了必须改对应后端（或反过来）。禁止只改一端还声称联调完成。
- 跨板块行为（JWT、OSS/本地盘、任务状态机、SSE）以 AGENT_CONTEXT 为准，禁止私自发明第二套语义。
- 不要把 PLY/SPZ 迁到 OSS。不要改三套 JWT 的 `type` 与 Filter 对应关系。

---

## 2. 编程规则

### 2.1 全仓

- **最小 diff：** 只改完成需求所需的行。禁止无关格式化、批量重命名、复制粘贴新抽象层。
- **跟着现有风格走：** 命名、错误处理、目录、测试位置与周围代码一致。不要引入第二套框架（新状态库、新 HTTP 客户端、新日志库），除非任务要求。
- **禁止臆造 API：** 路径、字段、状态枚举必须与现有 Controller / 前端 mapper 对齐。新接口先看 `com.xjicloud` 包是否已有同类能力。
- **禁止吞错：** 空 `catch`、只 `console.log`、把异常变成成功返回，都算缺陷。
- **禁止密钥入库：** 不把 JWT 密钥、Worker Secret、OSS 密钥写进源码或示例以外的可提交文件。沿用配置项与环境变量。
- **禁止假完成：** 用硬编码假数据、永远成功的 mock、注释掉的校验冒充实现。
- **架构变更必须改文档：** 新 BC、新 API、新存储、新任务状态，同步 AGENT_CONTEXT（及 web2 则 WEB2_FRONTEND / AGENTS.md）。

### 2.2 用户前端 `apps/web2`（DDD，不可打折）

硬规则以 `apps/web2/AGENTS.md` 为准，这里只强调 AI 最常踩的坑：

- Domain 禁止 `vue` / `fetch` / infrastructure。Presentation / composable 禁止 DTO、mapper、直接 HTTP。
- 组装仓库只允许 `src/app/create-container.ts`。页面用 composable + `inject(CONTAINER_KEY)`。
- 仓库与用例返回 `Result<T> = [error, data]`。UI 用 `formatDomainError`。
- 不要把旧版 `apps/web` 的 Pinia + `src/api` 或 Twilight Amber 暗色壳抄进来。
- 新能力：限界上下文 → 实体/port → mapper → 用例 → 页面。不要先堆 `.vue` 再补层。

### 2.3 管理前端 `apps/admin`

- 保持现有 Vue + 简单 store/api 结构，**不要**擅自升级成 web2 DDD。
- `base` 为 `/admin/`。只打 `/api/v1/admin/**`（及登录）。不要复用用户 JWT 调管理接口。
- 共用类型走 `@xjicloud/shared`，不要复制一份 `ApiResponse`。

### 2.4 后端 `services/backend`

- 按现有业务包扩展：`auth` / `project` / `model` / `job` / `worker` / `admin` / `oss` / `queue` / `sse`。不要新建平行顶层包「更微服务」。
- HTTP 统一 `{ success, message, data }`。业务失败走现有 `BusinessException` + `GlobalExceptionHandler`。
- 新接口必须挂对 Filter：用户 / 管理员 / Worker。公开接口必须显式放行，不要把鉴权当默认。
- Worker 注册继续要求 `X-Worker-Secret`。不要为了「方便本地」拆掉。
- 文件与 Range 下载、presigned URL 过期、任务状态机（`PENDING | UPLOADING | QUEUED | RUNNING | COMPLETED | FAILED | CANCELLED`）改动必须考虑并发与重复 complete。
- 持久化用 JPA 实体与现有表。不要为了一次需求引入第二套 ORM 或手写另一套数据源。

### 2.5 GPU Worker

- 协议字段与后端 `worker` API 对齐。改进度/完成回调必须两边一起改。
- 默认仍是 mock 训练。替换 `mock_trainer.py` 须任务明确要求，并说明对 SSE 阶段文案的影响。

---

## 3. 强制功能验证（Definition of Done）

**没有验证记录，禁止宣称任务完成。** 「代码能编译」「我看了 diff」不等于验证。

### 3.1 按改动选择命令（必须实际跑）

| 改了什么 | 最低验证 |
|----------|----------|
| `apps/web2` | `pnpm test:web2`；涉及构建/资源则 `pnpm build:web2` |
| `apps/admin` | `pnpm build:admin` |
| `services/backend` | `cd services/backend && mvn -B test`；若该模块几乎无测试，至少 `mvn -B package`，并在回复里写明测试缺口 |
| `packages/shared` | 构建所有引用方（至少 web2 + admin） |
| API 契约（前后端） | 两端都构建；列出未跑的联调路径 |
| UI 行为 / 样式 / 路由 / 客户端状态 | 见 §3.2 |

跑失败就修，禁止删测试、跳过断言、`--no-verify` 蒙混。

### 3.2 UI 必须走用户路径（有浏览器就做）

改了 Web 应用的可见行为、布局、路由、客户端状态或展示数据时：

- 按真实用户操作把主路径走完（点击、输入、提交、跳转），不要只截一张静态图。
- 同一状态若被多个页面读取，那些页面都要看一眼，避免只在一个屏好使。
- 空态、错误态、未登录、后端挂掉，至少说明测了哪些、哪些没法测。
- 没有浏览器时：用单测 / curl / 构建作为替代，并在回复中**明确写「未做浏览器 E2E」**。禁止假装测过登录后的云端流。

### 3.3 回复里必须出现的验证块

```text
## 验证
- 执行：<命令与结果>
- 手动：<路径 / 操作 / 结果> 或「未做：原因」
- 未覆盖：<风险>
```

缺这一块视为交付不合格。

---

## 4. 强制 Code Review（人可以不做，AI 必须做）

维护者可能不会开 PR Review。**因此每次改动结束前，实施改动的 Agent 必须先做一次自我审查**，再写给用户的总结。

审查对象：本次 diff，不是整个历史仓库。优先：**缺陷、行为回归、安全、缺失测试**。风格问题排最后。

### 4.1 审查顺序（按严重度）

1. **正确性：** 状态机、鉴权角色、空值、并发 complete、错误是否被吞、前后端字段是否对得上。
2. **安全：** 越权（用户打到 admin/worker）、路径穿越（本地盘文件）、密钥与 presigned、CORS、SSE 未鉴权。
3. **回归：** 分层被打破（web2 DDD）、误改 `apps/web`、误改 spark/rust、双存储策略被破坏。
4. **测试：** 新分支/新用例有没有对应测试；架构门禁（`architecture.spec.ts`）会不会挂。
5. **可维护性：** 重复逻辑、错误的包依赖、过宽的 try/catch。不要为「优雅」而扩大 diff。

### 4.2 发现问题时

- **Critical / High**（逻辑错、安全、数据丢失、鉴权洞）：必须先修再结束。不得只评论。
- **Medium**（错误处理缺口、明显可测未测、分层擦边）：能修则修；否则在总结里列出文件与原因。
- **Low**（纯风格）：除非破坏本板块已有约定，否则不要借机全文件格式化。

### 4.3 回复里必须出现的审查块

```text
## Code Review
- 范围：<文件/板块>
- 结论：通过 | 已修复 N 项 | 仍有风险（列出）
- High/Critical：<无 或 条目>
- 测试缺口：<无 或 条目>
```

未输出该块，视为未做 Review。

### 4.4 禁止的 Review 表演

- 只说「LGTM」「代码质量良好」而没有对着 diff 看行为。
- 用新功能清单代替缺陷清单。
- 审查自己刚生成的代码时假装是旁观者吹捧。
- 为了通过审查而削弱测试或关掉校验。

---

## 5. 快速清单（提交前）

- [ ] 只动了任务板块；没有改弃用的 `apps/web`（除非点名）
- [ ] 没有改 `packages/spark` / `rust/`（除非点名）
- [ ] 没有把模型存储策略、JWT 三种 `type`、任务状态枚举改 silently
- [ ] web2：分层、组合根、Result、主题仍成立
- [ ] 新用户可见文案走 i18n（web2），不用 `—`
- [ ] 已跑 §3 对应命令；UI 按 §3.2 验证或诚实声明未测
- [ ] 已做 §4 自我 Review；High/Critical 已修
- [ ] 架构/API 变更已更新记忆文档

---

*规则与仓库行为冲突时：以代码与测试为准，并开任务修正本文。不要用本文当借口扩大重构。*
