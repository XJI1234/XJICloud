# 前后端对接缺口清单

> 生成说明：对照 `apps/web` 前端按键/路由与 `services/backend` REST 控制器逐项核对。  
> 后端前缀：`/api/v1`。前端「即将推出」统一走 `apps/web/src/utils/comingSoon.ts`（`window.alert`）。

---

## 1. 总览

| 类别 | 数量 | 说明 |
|------|------|------|
| 前端有按键，明确 stub（coming soon） | **12 处调用** | 无对应业务页面或无用户侧 API |
| 用户侧后端已实现接口 | **16** | auth / projects / models / jobs |
| 管理端 / Worker 接口 | **15** | `apps/web` 用户端未对接 |
| 训练实现状态 | mock | `services/gpu-worker/mock_trainer.py`，非真实训练 |

---

## 2. 前端有按键，后端无接口 / 未实现（重点）

下列入口在 UI 可见可点，但**不会发起真实业务 API**（或整条产品链路未建）。

### 2.1 明确 `showComingSoon` 的按键

| # | 页面/位置 | 按键文案（中） | 代码位置 | featureKey | 后端现状 | 缺口说明 |
|---|-----------|----------------|----------|------------|----------|----------|
| 1 | 顶栏导航 | 搜索索引 | `CloudLayout.vue` `navItems` | `nav.searchIndex` | 无路由、无 Controller | 需产品定义 + 全栈 |
| 2 | 顶栏导航 | 双屏显示 | 同上 | `nav.dualScreen` | 无 | 状态栏文案也标明后续启用 |
| 3 | 顶栏导航 | 用户空间 | 同上 | `nav.userSpace` | 无用户资料/空间 API | 仅有 register/login |
| 4 | 左侧工具栏 | 航线规划 | `CloudLayout.vue` `toolItems` | `tools.routePlanning` | 无 | `route: null`，无页面 |
| 5 | 首页功能卡 CTA | 开始规划 | `HomeView.vue` | `tools.routePlanning` | 无 | 与 #4 同一能力 |
| 6 | 顶栏 | 团队 | `CloudLayout.vue` | `header.team` | 无团队/成员 API | — |
| 7 | 顶栏 | 帮助 | `CloudLayout.vue` | `header.help` | 无帮助/文档 API | 可为静态页，不必走后端 |
| 8 | 工程页 | 上传云端模型 | `ProjectListView.vue` | `projects.cloudModelUploadFeature` | **无「云端选型/拉取」接口** | 本地上传已有（见 §3）；云端指从对象存储/库中选模型，未做 |
| 9 | 工程页列表行 | 删除工程 | `ProjectListView.vue` | `projects.deleteProjectFeature` | **无** `DELETE /projects/{id}` | `ProjectController` 仅 GET 列表 + POST 创建 |
| 10 | 高级编辑空态 | 云端模型上传 | `SuperSplatEditorView.vue` | `supersplat.cloudModelUploadFeature` | 同 #8 | — |
| 11 | 训练任务面板 | 删除上传记录 | `TrainingJobPanel.vue` | `training.deleteRecordFeature` | **无用户侧** `DELETE /jobs/{id}` | Admin 有 `POST /admin/jobs/{id}/cancel`，用户端未暴露删除/取消 |
| 12 | 登录页 | 获取验证码 | `LoginView.vue` | `login.smsCodeFeature` | **无** 短信/验证码 Auth API | 手机号与验证码仅为 UI 占位；提交仍走用户名密码 |

### 2.2 有 UI 行为，但非完整后端能力

| 前端行为 | 位置 | 实际实现 | 缺口 |
|----------|------|----------|------|
| 退出登录 | `CloudLayout` 用户菜单 | 仅清本地 token / session | **无** `POST /auth/logout`（可选；JWT 无状态时可接受） |
| 语言切换 | 顶栏 | 纯前端 i18n | 无需后端 |
| 打开/切换当前工程 | Home / Projects | 前端 store + localStorage | 无需后端「打开」接口；无服务端「最近打开」持久化 |
| 取消训练任务 | 用户端无按钮 | — | Job 状态含取消语义；**仅 Admin** `POST /api/v1/admin/jobs/{jobId}/cancel` |
| 重试训练任务 | 用户端无按钮 | — | 仅 Admin `POST /api/v1/admin/jobs/{jobId}/retry` |
| 修改工程名称/描述 | 无独立表单 | Viewer 内「工程信息」写入 **viewer-config** | **无** `PATCH /projects/{id}` |

### 2.3 导航有入口、路由表无页面

`apps/web/src/router/index.ts` 仅有：

`/login` · `/app/home` · `/app/projects` · `/app/upload` · `/app/layer` · `/app/supersplat`

以下导航/工具 **无 Vue 页面**：

- 搜索索引、双屏显示、用户空间  
- 航线规划  
- 团队、帮助（无独立页）

---

## 3. 已对接（前端按键 ↔ 后端接口）

便于对照「哪些是通的」。

| 前端能力 | 主要入口 | 前端 API | 后端接口 |
|----------|----------|----------|----------|
| 注册 / 登录 | `LoginView` | `auth.register` / `login` | `POST /auth/register` · `POST /auth/login` |
| 工程列表 | Home / Projects | `projects.listProjects` | `GET /projects` |
| 新建工程 | Home / Projects | `projects.createProject` | `POST /projects` |
| 上传本地模型 | Projects / Upload / Layer | `models.uploadModel` | `POST /projects/{id}/models/upload` |
| 模型列表 | SuperSplat / Layer | `models.listModels` | `GET /projects/{id}/models` |
| 下载 / Token | SuperSplat / Layer | `createDownloadToken` · `downloadModelBytes` | `POST .../download-token` · `GET .../download` |
| Viewer 配置 | Layer | `get/saveViewerConfig` | `GET/PUT /models/{id}/viewer-config` |
| 导出写回 | SuperSplat / Layer | `uploadExport` | `POST /models/{id}/export` |
| 数据集上传+排队 | Upload → Dataset | `createDataset` · OSS PUT · `completeDataset` | `POST .../datasets` · `.../complete` |
| 训练任务列表/进度 | `TrainingJobPanel` | `listProjectJobs` · SSE · `getJob` | `GET .../jobs` · `GET /jobs/{id}/events` · `GET /jobs/{id}` |

---

## 4. 后端已有、用户端前端未对接

| 后端接口 | 用途 | 用户端前端 |
|----------|------|------------|
| `POST /api/v1/admin/**` 全套 | 管理台：OSS、Worker、任务重试/取消、统计 | `apps/web` **未使用**（若有独立 admin 前端需另查） |
| `POST /api/v1/worker/**` | GPU Worker 拉活 | 仅 `gpu-worker` 客户端 |
| `POST /api/v1/admin/auth/login` | 管理员登录 | 用户端未用 |

---

## 5. 建议优先补齐的对接项（按产品影响）

| 优先级 | 能力 | 建议后端 | 建议前端 |
|--------|------|----------|----------|
| P0 | 删除工程 | `DELETE /api/v1/projects/{projectId}`（级联策略需定） | 去掉 coming soon，接 API + 确认框 |
| P0 | 删除/取消训练记录 | 用户侧 `DELETE` 或 `POST .../cancel` | `TrainingJobPanel` 接真实接口 |
| P1 | 上传云端模型 | 明确语义：列 OSS/模型库 → 选定绑定到工程；或复用已有模型 ID | 替换 coming soon；与本地上传区分 |
| P1 | 航线规划 | 新模块 + 路由（或明确不做则隐藏入口） | 工具栏 / 首页 CTA |
| P2 | 用户空间 / 团队 | 用户资料、成员邀请等 | 顶栏入口 |
| P2 | 搜索索引 / 双屏 | GIS / 检索产品方案 | 顶栏入口 |
| P3 | 帮助 | 静态文档链接即可 | 可去掉 coming soon，外链或内嵌页 |
| P3 | Logout | 可选黑名单/刷新令牌 | 可选 |

---

## 6. 后端用户侧接口速查（已实现）

```
POST   /api/v1/auth/register
POST   /api/v1/auth/login
GET    /api/v1/projects
POST   /api/v1/projects
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
```

**用户侧明确缺失（相对前端按键）：**

```
DELETE /api/v1/projects/{projectId}              # 删除工程
DELETE|POST cancel  /api/v1/jobs/{jobId}       # 用户删/取消训练记录
*    云端模型选择/导入                             # 产品未定
*    航线规划 / 搜索 / 双屏 / 用户空间 / 团队     # 产品未定
```

---

## 7. 相关源码索引

| 角色 | 路径 |
|------|------|
| Coming soon 工具 | `apps/web/src/utils/comingSoon.ts` |
| 导航 / 工具栏 stub | `apps/web/src/layouts/CloudLayout.vue` |
| 工程双上传 / 删除 | `apps/web/src/views/ProjectListView.vue` |
| 首页航线 CTA | `apps/web/src/views/HomeView.vue` |
| SuperSplat 云端上传 | `apps/web/src/views/SuperSplatEditorView.vue` |
| 训练删除记录 | `apps/web/src/components/TrainingJobPanel.vue` |
| 前端 API 封装 | `apps/web/src/api/*.ts` |
| 后端 Controllers | `services/backend/src/main/java/com/xjicloud/**/**Controller.java` |
| 产品上下文 | `docs/AGENT_CONTEXT.md` |

---

*文档随代码演进；若新增 coming soon 或接口，请同步更新本节。*
