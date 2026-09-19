# XJICloud

3D Gaussian Splatting（3DGS）建模云平台：上传图片数据集触发 GPU 训练、管理 PLY/SPZ 模型，浏览器内用 Spark 查看、SuperSplat（iframe）高级编辑。

> 本仓库为 **pnpm monorepo**：根目录仅做编排，业务代码在 `apps/`、`services/`、`packages/`。

**当前用户前端是 `apps/web2`**（Vue 3 + Vite，前端 DDD：Domain / Application / Infrastructure / Presentation）。`apps/web` 是旧版产品面，已弃用，仅作对照；不要在新功能里改它。UI 为主题：**浅色产品壳 + 暗色 3D 画布**，不是旧版 Twilight Amber 暗色壳。

---

## 仓库结构

```text
XJICloud/
├── apps/
│   ├── web2/                # 当前用户前端 @xjicloud/web2（:5176，DDD）
│   ├── web/                 # 已弃用：旧用户前端 @xjicloud/web（:5174）
│   └── admin/               # 管理面板 @xjicloud/admin（:5175，base /admin/）
├── packages/
│   ├── spark/               # Spark 2.0 渲染库 @xjicloud/spark
│   └── shared/              # 共享 TS 类型 @xjicloud/shared
├── services/
│   ├── backend/             # Spring Boot API（:8080）
│   └── gpu-worker/          # Python GPU Worker 容器
├── rust/                    # Spark WASM（Cargo workspace）
├── vendors/
│   └── supersplat/          # SuperSplat 预编译静态资源（构建时复制到 public）
├── deploy/                  # Compose / Nginx / systemd / K8s
├── docs/                    # Deploy.md、AGENT_CONTEXT.md、WEB2_FRONTEND.md
├── scripts/                 # 构建辅助脚本
├── package.json             # pnpm workspace 入口
└── pnpm-workspace.yaml
```

| 角色 | 目录 | 日常命令 |
|------|------|----------|
| 用户前端（当前） | `apps/web2`, `packages/spark`, `rust` | `pnpm dev` / `pnpm test:web2` |
| 用户前端（已弃用） | `apps/web` | `pnpm dev:web-legacy` |
| 管理前端 | `apps/admin` | `pnpm --filter @xjicloud/admin dev` |
| 后端 | `services/backend` | `cd services/backend && mvn spring-boot:run` |
| Worker | `services/gpu-worker` | `docker build -t xjicloud/gpu-worker services/gpu-worker` |
| SuperSplat | `vendors/supersplat`（预编译） | web2 `predev`/`prebuild` 自动复制 |

---

## 快速开始（本地开发）

### 环境要求

- **Node.js** ≥ 18、**pnpm** 9（`corepack enable`）
- **Java** 17+、**Maven** 3.9+
- **Docker**（Redis、MinIO、Worker）

### 1. 克隆与安装

```bash
git clone <repo-url> XJICloud
cd XJICloud
pnpm install
```

### 2. 基础设施

```bash
cd deploy
docker compose up redis minio minio-init -d
```

社区版 MinIO 需配置全局 CORS（Origin 含 `http://127.0.0.1:5176`），见 [docs/Deploy.md](docs/Deploy.md)。

### 3. 后端

```bash
cd services/backend
mvn spring-boot:run
```

### 4. 前端

```bash
# 仓库根目录
pnpm dev           # :5176，当前用户端（apps/web2）
pnpm test:web2     # web2 单测 + 分层门禁
pnpm dev:admin     # :5175
pnpm dev:web-legacy  # :5174，仅旧版对照
```

`pnpm dev:web` / `pnpm build:web` 与 `pnpm dev` 一样指向 **web2**。旧包请用 `dev:web-legacy` / `build:web-legacy`。

架构说明见 [apps/web2/README.md](apps/web2/README.md) 与 [docs/WEB2_FRONTEND.md](docs/WEB2_FRONTEND.md)。

### 5. GPU Worker（可选）

```bash
docker build -t xjicloud/gpu-worker services/gpu-worker/
docker run --rm \
  -e XJICLOUD_BACKEND_URL=http://host.docker.internal:8080 \
  -e WORKER_SECRET=change-me-worker-secret-in-production \
  xjicloud/gpu-worker
```

---

## 构建

```bash
pnpm build:wasm          # Rust WASM
pnpm build               # 当前用户端 web2（含复制 vendors/supersplat）
pnpm build:admin
pnpm build:all           # web2 + admin
pnpm build:web-legacy    # 仅旧版 apps/web

cd services/backend && mvn -DskipTests package
```

产物：

- 用户前端（当前） → `apps/web2/dist/`
- 用户前端（已弃用） → `apps/web/dist/`
- 管理面板 → `apps/admin/dist/`
- 后端 → `services/backend/target/*.jar`

---

## 功能概览

| 端 | 能力 |
|----|------|
| 用户端（web2） | 工程管理、图片数据集 OSS 直传训练、PLY/SPZ 上传、Spark 查看、SuperSplat iframe 编辑 |
| 管理端 | OSS 配置、Worker / 任务监控、仪表盘 |
| Worker | 注册 / 心跳 / 领任务；当前为 mock 训练（可替换 `mock_trainer.py`） |

默认管理员：`admin` / `admin123`（生产务必修改）。

---

## 生产部署

分机拓扑与安全组见 **[docs/Deploy.md](docs/Deploy.md)**。

| 脚本 / 目录 | 用途 |
|-------------|------|
| `deploy/deploy-backend.sh` | 后端一键构建 + systemd（路径：`services/backend`） |
| `deploy/nginx-frontend.conf.example` | 前端 Nginx（`/api/`、`/admin/`、`/supersplat/`） |
| `deploy/k8s/` | K8s 清单 |
| `deploy/docker-compose.yml` | 单机演示（backend + redis + minio + worker） |

```bash
cp deploy/config/application-prod.yml.example deploy/config/application-prod.yml
sudo ./deploy/deploy-backend.sh
```

---

## 文档

- [AGENTS.md](AGENTS.md) — Agent 入口（强制验证与 Code Review）
- [docs/TEAM_ENGINEERING.md](docs/TEAM_ENGINEERING.md) — 团队编程规则、强制功能验证、强制 Code Review
- [docs/Deploy.md](docs/Deploy.md) — 分机部署
- [docs/AGENT_CONTEXT.md](docs/AGENT_CONTEXT.md) — 全仓架构与 API 速查（给开发 / Agent）
- [docs/WEB2_FRONTEND.md](docs/WEB2_FRONTEND.md) — 当前用户前端 DDD 架构知识库
- [apps/web2/README.md](apps/web2/README.md) — web2 包说明
- [apps/web2/AGENTS.md](apps/web2/AGENTS.md) — 改 web2 时的 Agent 硬规则
- [vendors/README.md](vendors/README.md) — SuperSplat 预编译资源说明

---

## 许可与第三方

- **Spark 2.0**（`packages/spark`）：专有许可
- **SuperSplat**：PlayCanvas；仓库内仅保留预编译静态资源，见上游许可证
