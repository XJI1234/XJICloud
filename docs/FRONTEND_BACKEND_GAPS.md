# 前后端对接缺口清单

> 对照 `apps/web`、`apps/admin` 与 `services/backend` REST 控制器逐项核对。  
> 后端前缀：`/api/v1`。前端「即将推出」统一走 `apps/web/src/utils/comingSoon.ts`（`window.alert`）。  
> **最后更新：** 合并 `main` 后（含验证码、工程 PATCH/DELETE、训练 cancel/delete）。

---

## 1. 总览

| 类别 | 数量 | 说明 |
|------|------|------|
| 用户侧后端已实现接口 | **22** | auth / projects / models / jobs |
| `apps/web` 用户 API 封装 | **22/22** | 全部在 `apps/web/src/api/` 且已接入 UI |
| `apps/admin` 管理 API 封装 | **11/11** | 全部在 `apps/admin/src/api/adminClient.ts` |
| Worker 接口 | **6** | 由 `services/gpu-worker` 调用，不进浏览器 |
| 前端 stub（coming soon） | **7 类** | 前端有入口，后端无对应 API |
| 训练实现状态 | mock | `services/gpu-worker/mock_trainer.py`，非真实训练 |

---

## 2. 后端有、前端未写接口？

**结论：无遗漏。**

| 后端族群 | 消费端 | 前端 API 层 | 状态 |
|----------|--------|-------------|------|
| 用户 JWT（22 个） | `apps/web` | `apps/web/src/api/*.ts` | 全部封装 + 页面/Store 已调用 |
| Admin（11 个） | `apps/admin` | `apps/admin/src/api/adminClient.ts` | 全部封装 + 管理页已调用 |
| Worker（6 个） | `gpu-worker` | `worker_agent.py` | 故意不进浏览器前端 |

以下能力在合并 `main` 后**已从 stub 变为真实对接**（旧版文档曾误报为缺口）：

- 图形验证码：`GET /auth/captcha` · `GET /auth/need-captcha` → `LoginView`
- 更新 / 删除工程：`PATCH` · `DELETE /projects/{id}` → `ProjectListView`
- 取消 / 删除训练任务：`POST /jobs/{id}/cancel` · `DELETE /jobs/{id}` → `TrainingJobPanel`
- 帮助页：顶栏跳转 `/app/help` → `HelpView`（静态页，无需后端）

---

## 3. 前端有按键，后端无接口（stub 清单）

下列入口在 UI 可见可点，但**不会发起真实业务 API**（或整条产品链路未建）。

### 3.1 明确 `showComingSoon` 的入口

| # | 页面/位置 | 按键文案（中） | 代码位置 | featureKey | 后端现状 |
|---|-----------|----------------|----------|------------|----------|
| 1 | 顶栏导航 | 搜索索引 | `CloudLayout.vue` | `nav.searchIndex` | 无路由、无 Controller |
| 2 | 顶栏导航 | 双屏显示 | 同上 | `nav.dualScreen` | 无 |
| 3 | 顶栏导航 | 用户空间 | 同上 | `nav.userSpace` | 无用户资料/空间 API |
| 4 | 左侧工具栏 | 航线规划 | `CloudLayout.vue` | `tools.routePlanning` | 无 |
| 5 | 首页功能卡 CTA | 开始规划 | `HomeView.vue` | `tools.routePlanning` | 同 #4 |
| 6 | 顶栏 | 团队 | `CloudLayout.vue` | `header.team` | 无团队/成员 API |
| 7 | 工程页 | 上传云端模型 | `ProjectListView.vue` | `projects.cloudModelUploadFeature` | 无「云端选型/拉取」API（本地上传已有） |
| 8 | 高级编辑空态 | 云端模型上传 | `SuperSplatEditorView.vue` | `supersplat.cloudModelUploadFeature` | 同 #7 |
| 9 | 登录页 | 获取验证码（短信） | `LoginView.vue` | `login.smsCodeFeature` | 无短信 API（图形验证码已对接） |

### 3.2 纯前端行为（无需后端）

| 前端行为 | 位置 | 说明 |
|----------|------|------|
| 退出登录 | `CloudLayout` 用户菜单 | 清本地 token；无 `POST /auth/logout`（JWT 无状态可接受） |
| 语言切换 | 顶栏 | 纯前端 i18n |
| 打开/切换当前工程 | Home / Projects | store + localStorage；无服务端「最近打开」持久化 |
| 帮助 | 顶栏 | 跳转 `HelpView` 静态页 |

### 3.3 导航无页面（`route: null`）

`apps/web/src/router/index.ts` 已定义：`/login` · `/app/home` · `/app/projects` · `/app/upload` · `/app/layer` · `/app/supersplat` · `/app/help`

以下入口 **无独立 Vue 页面**：

- 搜索索引、双屏显示、用户空间
- 航线规划
- 团队

---

## 4. 已对接（用户端 22/22）

| 模块 | 前端能力 | 主要入口 | 前端 API | 后端接口 |
|------|----------|----------|----------|----------|
| Auth | 图形验证码 | `LoginView` | `getCaptcha` · `needCaptcha` | `GET /auth/captcha` · `GET /auth/need-captcha` |
| Auth | 注册 / 登录 | `LoginView` | `register` · `login` | `POST /auth/register` · `POST /auth/login` |
| Project | 列表 / 新建 | Home / Projects | `listProjects` · `createProject` | `GET/POST /projects` |
| Project | 编辑 / 删除 | `ProjectListView` | `updateProject` · `deleteProject` | `PATCH/DELETE /projects/{id}` |
| Model | 上传 / 列表 | Projects / Upload / Layer | `uploadModel` · `listModels` | `POST/GET .../models` |
| Model | 下载 / Token | SuperSplat / Layer | `createDownloadToken` · `downloadModelBytes` | `POST .../download-token` · `GET .../download` |
| Model | Viewer 配置 / 导出 | Layer / SuperSplat | `get/saveViewerConfig` · `uploadExport` | `GET/PUT .../viewer-config` · `POST .../export` |
| Job | 数据集上传+排队 | Upload | `createDataset` · OSS PUT · `completeDataset` | `POST .../datasets` · `.../complete` |
| Job | 列表 / 进度 / 详情 | `TrainingJobPanel` | `listProjectJobs` · SSE · `getJob` | `GET .../jobs` · `GET /jobs/{id}/events` · `GET /jobs/{id}` |
| Job | 取消 / 删除 | `TrainingJobPanel` | `cancelJob` · `deleteJob` | `POST /jobs/{id}/cancel` · `DELETE /jobs/{id}` |

---

## 5. 管理端与 Worker（非 apps/web 职责）

| 后端接口 | 用途 | 消费端 |
|----------|------|--------|
| `POST /api/v1/admin/auth/login` | 管理员登录 | `apps/admin` LoginView |
| `GET /admin/dashboard` · `GET /admin/stats` | 仪表盘 | `apps/admin` DashboardView |
| `GET/PUT /admin/oss` · `POST /admin/oss/test` | OSS 配置 | `apps/admin` OssSettingsView |
| `GET /admin/workers` · `POST .../offline` | Worker 管理 | `apps/admin` WorkersView |
| `GET /admin/jobs` · `POST .../retry` · `POST .../cancel` | 全局任务管理 | `apps/admin` JobsView |
| `POST /api/v1/worker/**`（6 个） | Worker 注册、心跳、领任务、上报 | `services/gpu-worker/worker_agent.py` |

用户端 **不应** 直接调用 Admin / Worker 接口（权限域不同）。

---

## 6. 建议优先补齐（产品向）

| 优先级 | 能力 | 说明 |
|--------|------|------|
| P1 | 上传云端模型 | 需定义「从 OSS/模型库选型导入」语义；本地上传 `POST .../models/upload` 已有 |
| P1 | 航线规划 | 新模块 + 路由，或隐藏工具栏/首页入口 |
| P2 | 用户空间 / 团队 | 用户资料、成员邀请等全新 API |
| P2 | 搜索索引 / 双屏 | GIS / 检索产品方案 |
| P3 | 短信验证码登录 | 与现有图形验证码并存；需短信网关 + Auth API |
| P3 | Logout / 最近打开持久化 | 可选增强 |

**已完成（合并 main 后）：** 删除工程、编辑工程、取消/删除训练记录、图形验证码、帮助静态页。

---

## 7. 后端用户侧接口速查（22 个，均已对接）

```
GET    /api/v1/auth/captcha
GET    /api/v1/auth/need-captcha
POST   /api/v1/auth/register
POST   /api/v1/auth/login
GET    /api/v1/projects
POST   /api/v1/projects
PATCH  /api/v1/projects/{projectId}
DELETE /api/v1/projects/{projectId}
GET    /api/v1/projects/{projectId}/models
POST   /api/v1/projects/{projectId}/models/upload
POST   /api/v1/models/{modelId}/download-token
GET    /api/v1/models/{modelId}/download
GET    /api/v1/models/{modelId}/viewer-config
PUT    /api/v1/models/{modelId}/viewer-config
POST   /api/v1/models/{modelId}/export
POST   /api/v1/projects/{projectId}/datasets
POST   /api/v1/projects/{projectId}/datasets/{jobId}/complete
GET    /api/v1/projects/{projectId}/jobs
GET    /api/v1/jobs/{jobId}
GET    /api/v1/jobs/{jobId}/events          (SSE)
POST   /api/v1/jobs/{jobId}/cancel
DELETE /api/v1/jobs/{jobId}
```

**相对前端 stub 仍缺失的后端能力：**

```
*    云端模型选择/导入
*    航线规划 / 搜索 / 双屏 / 用户空间 / 团队
*    短信验证码 Auth
```

---

## 8. 相关源码索引

| 角色 | 路径 |
|------|------|
| Coming soon 工具 | `apps/web/src/utils/comingSoon.ts` |
| 用户端 API | `apps/web/src/api/*.ts` |
| 管理端 API | `apps/admin/src/api/adminClient.ts` |
| 导航 / stub 入口 | `apps/web/src/layouts/CloudLayout.vue` |
| 登录（验证码 + 短信占位） | `apps/web/src/views/LoginView.vue` |
| 工程 CRUD | `apps/web/src/views/ProjectListView.vue` |
| 训练 cancel/delete | `apps/web/src/components/TrainingJobPanel.vue` |
| 帮助静态页 | `apps/web/src/views/HelpView.vue` |
| 后端 Controllers | `services/backend/src/main/java/com/xjicloud/**/**Controller.java` |
| GPU Worker 客户端 | `services/gpu-worker/worker_agent.py` |
| 产品上下文 | `docs/AGENT_CONTEXT.md` |

---

*文档随代码演进；若新增 coming soon 或接口，请同步更新本节。*
