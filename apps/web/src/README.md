# apps/web/src — 领域驱动目录

用户前端按 **6 个限界上下文** 组织，共享内核与应用壳层独立。

## 目录结构

```
src/
├── app/              # 应用壳：启动、路由、布局、全局样式/i18n
├── shared/           # 共享内核：HTTP client、session、通用 UI、composables
├── domains/          # 限界上下文
│   ├── identity/     # 认证：api、stores、LoginView
│   ├── project/      # 工程：api、stores、HomeView、ProjectListView
│   ├── model/        # 模型资产：api（upload/list/export）
│   ├── training/     # 训练：api、stores、UploadView 及数据集/任务组件
│   ├── viewer/       # Spark 查看器：types、SparkViewport、LayerViewerView
│   ├── editor/       # SuperSplat 编辑：bridge、SuperSplatEditorView
│   └── support/      # HelpView 等辅助页
└── assets/           # 静态资源（SVG 等）
```

## 依赖规则

```
pages → stores / components / api（本域）
stores → api（本域）
api → shared/infrastructure/http
app → domains/*/pages、shared
```

- **禁止**：`domains/A` 直接 import `domains/B` 的 `pages/` 或 `components/`
- **允许**：组合页（如 `training/pages/UploadView`）同时调用 `training` 与 `model` 的 api
- **允许**：`app/layouts/CloudLayout` 使用 `identity` store + `shared`

## 路径别名

| 别名 | 指向 |
|------|------|
| `@/*` | `src/*` |
| `@/app/*` | `src/app/*` |
| `@/shared/*` | `src/shared/*` |
| `@/domains/*` | `src/domains/*` |

## 新增功能放哪

| 功能 | 位置 |
|------|------|
| 新登录/注册能力 | `domains/identity/` |
| 工程 CRUD、首页 | `domains/project/` |
| 模型上传/列表/下载 | `domains/model/api/` |
| 数据集上传、训练任务 | `domains/training/` |
| 3D 查看、标注、viewer-config | `domains/viewer/` |
| SuperSplat 集成 | `domains/editor/` |
| 帮助、关于等 | `domains/support/` |
| 全局布局、路由、i18n | `app/` |
| HTTP、通用组件、工具 | `shared/` |

入口：`app/main.ts`（见 `index.html`）。
