# @xjicloud/web2

XJICloud **用户端 DDD 重写**：Vue 3 + Vite，端口 **5176**。与 `apps/web`（:5174，Pinia + 直连 API）能力对齐，但业务规则在 Domain / Application，页面只消费已经安全的结果。

默认 `pnpm dev` 仍指向 `@xjicloud/web`。web2 是并行产品面，不是临时脚手架。

| 文档 | 给谁 |
|------|------|
| 本 README | 人：怎么跑、目录、路由、设计约定 |
| [docs/WEB2_FRONTEND.md](../../docs/WEB2_FRONTEND.md) | 人 + AI：完整 DDD 架构、限界上下文、数据流 |
| [AGENTS.md](./AGENTS.md) | AI：改 web2 时必须遵守的规则与检查清单 |

仓库总览见根目录 [README.md](../../README.md)。Agent 全仓上下文见 [docs/AGENT_CONTEXT.md](../../docs/AGENT_CONTEXT.md)。

---

## 为什么有 web2

`apps/web` 把 fetch、DTO、校验、UI 状态混在 view / store 里。web2 按前端 Clean Architecture 拆开：

- **Domain** 稳定：实体、不变量、仓库接口。零 Vue、零 `fetch`。
- **Application** 编排用例：登录、提交数据集、打开编辑器。
- **Infrastructure** 适配后端与浏览器：HTTP、mapper（防腐层）、SSE、XHR 直传 OSS、iframe postMessage。
- **Presentation** 薄：composable 注入容器，页面不写 API 路径、不 `map` DTO。

同一套 `/api/v1` 与本地盘 PLY/SPZ 策略，**不要**擅自把模型迁到 OSS。

---

## 快速开始

```bash
# 仓库根
pnpm install
pnpm dev:web2          # http://localhost:5176  /api → :8080
pnpm test:web2
pnpm build:web2
```

后端需 `services/backend` 在 **8080**。验证码、登录、项目、模型都走代理。没有后端时页面能开，业务请求会 `ECONNREFUSED`。

`predev` / `prebuild` 会把 `vendors/supersplat` 复制到 `apps/web` 与 `apps/web2` 的 `public/supersplat`。

---

## 路由

| 路径 | 限界上下文 | 页面 |
|------|------------|------|
| `/login` | Identity | 登录 / 注册 |
| `/app/home` | Project | 主页：最近工程、新建/打开 |
| `/app/projects` | Project + ModelAsset | 工程列表、上传模型 |
| `/app/upload` | Dataset-training | 图片数据集归档直传 + 训练任务 |
| `/app/layer` | Viewer + ModelAsset | 原生 Spark 查看（轨道/平移/缩放，无涂抹编辑） |
| `/app/supersplat` | Editor | SuperSplat iframe；无云端模型也可进，可打开本地 PLY/SPZ |
| `/app/help` | Presentation | 帮助 |

未登录访问 `/app/**` 会按 `resolveAuthNavigation` 跳到 `/login`。

---

## 源码地图

```text
apps/web2/src/
├── main.ts                      # 组合根：建容器、provide、挂路由
├── app/
│   ├── create-container.ts      # 唯一组装 HTTP / 仓库 / 适配器的地方
│   ├── container.types.ts       # Web2Container（只引用 port）
│   ├── runtime.ts               # getContainer（路由守卫用）
│   ├── router.ts
│   └── i18n/
├── shared/                      # Result、DomainError、HTTP 客户端、架构测试
├── features/{bc}/
│   ├── domain/                  # 实体、领域服务、repository 接口
│   ├── application/             # 用例
│   ├── infrastructure/          # mapper、HTTP 仓库、适配器
│   └── presentation/            # composable + Vue 页面
└── presentation/                # 跨 BC 壳：布局、AppButton、样式、错误文案
```

限界上下文：`identity` · `project` · `dataset-training` · `model-asset` · `viewer` · `editor`。

跨 BC 只通过 Application / 组合根，禁止 `features/a` 直接 import `features/b/infrastructure`。

---

## 设计（产品壳，不是落地页）

- 纸白壳：`--surface #f5f6f8`，`--elevated #ffffff`，单一强调色 `--accent #3d6b8a`
- 控件圆角 10px，面板 16px；主按钮 / 幽灵 / 危险；`:active` `scale(0.97)`
- 字体 `system-ui` + 系统中文；图标 `@phosphor-icons/vue`，组件名必须是 `PhHouse` 这种前缀
- 查看器画布可以是暗的；Chrome 保持浅色
- UI 文案不用 em dash（`—`）
- 动效：`motion`，默认临界阻尼（`bounce: 0`）

---

## 测试与 CI

- `src/shared/architecture.spec.ts`：domain 不得 import vue / fetch / infrastructure；composable 不得 import mapper / Dto
- `src/shared/parity.spec.ts`：六个用户能力各有一个用例模块
- 领域与用例单测与实现同目录 `*.spec.ts`
- GitHub Actions 跑 `pnpm --filter @xjicloud/web2 test` 与 `build`

Vitest 使用独立的 `vitest.config.ts`（不要并进 `vite.config.ts`，插件类型会冲突）。

---

## 和 apps/web 的关系

| | `apps/web` | `apps/web2` |
|--|------------|-------------|
| 包名 | `@xjicloud/web` | `@xjicloud/web2` |
| 端口 | 5174 | 5176 |
| 状态 | Pinia + `api/` | 容器 + 用例 + composable |
| 查看器 | 厚 Spark 编辑视口 | `NativeSplatViewport`（查看） |
| 高级编辑 | 无云端模型则拦截 | 空白 SuperSplat + 本地打开 |

改 web2 **不要**顺手改 `apps/web`，除非任务明确要求两边一起改。
