# XJI Cloud

建模解决方案云平台 — 基于 [Spark 2.0](https://github.com/sparkjs-dev/spark) 的 3D Gaussian Splatting（3DGS）模型查看与编辑。支持 **Linux 云部署**（前后端分离）与 **Electron 桌面版**。

**仓库地址：** [github.com/XJI1234/XJICloud](https://github.com/XJI1234/XJICloud)

![Vue 3](https://img.shields.io/badge/Vue-3.5-42b883)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F)
![Java](https://img.shields.io/badge/Java-17-ED8B00)
![Three.js](https://img.shields.io/badge/Three.js-r180-black)

## 架构概览

```
浏览器 / Electron
       │
       ▼
  Vue 3 前端 (Vite)          ──►  SparkViewport + WASM
       │  /api/v1
       ▼
  Spring Boot 后端 (JWT)
       ├── H2 / PostgreSQL（元数据）
       └── 本地磁盘 /data/xjicloud（模型与 viewer.json）
```

| 模块 | 路径 | 说明 |
|------|------|------|
| 前端壳层 | `src/` | 主页、登录、工程项目、数据上传、路由与云 API |
| Spark 查看器 | `src/modules/viewer/` | 模型查看（`/app/layer`），Spark + WASM |
| SuperSplat 编辑器 | `modules/supersplat/` | 高级编辑（iframe 子应用，`/app/supersplat`） |
| 后端 | `backend/` | REST API、JWT、模型上传/下载、配置持久化 |
| 部署样例 | `deploy/` | Nginx、生产 `application` 模板 |
| 桌面端 | `electron/` | 可选，本地文件对话框 |
| 渲染核心 | `src/lib/spark/`、`rust/` | Spark 2.0 + WASM（查看器共享） |

后端为 **跨平台 Java**，开发可在 Windows/macOS 进行；**生产与测试推荐 Linux**（路径、Nginx、systemd 均按 Linux 约定）。

## 功能特性

### 云平台（Web）

- 用户注册 / 登录（JWT）
- **工程项目**：创建项目、上传 PLY/SPZ、列表选模
- **模型查看**（`/app/layer`）：Spark 查看、标注、编辑、导出 SPZ
- **高级编辑**（`/app/supersplat`）：嵌入 [SuperSplat](https://github.com/playcanvas/supersplat)，裁剪/变换等原生编辑，保存 PLY 到云端
- 查看器配置（`.viewer.json` v2 结构）存服务端，刷新可恢复
- 导出在浏览器生成后上传服务器（查看器 SPZ / 高级编辑 PLY）

### 模型能力（Spark 渲染）

- PLY / SPZ 加载，WebGL 高性能渲染
- 气泡标注、立方体标记、颜色标记 / 擦除 / 橡皮擦
- 撤销重做、默认视角、屏幕平面旋转
- 项目信息字段（经纬度、建筑名称等，可自定义）

### 桌面版（Electron）

- 本地目录打开模型，不依赖云后端
- 构建：`npm run electron:build` → `release/`

## 环境要求

| 用途 | 依赖 |
|------|------|
| 前端（Vite 壳层） | Node.js ≥ 18，npm ≥ 9 |
| SuperSplat 子工程 | Node.js ≥ **20.19**（见 `modules/supersplat/package.json`） |
| 后端 | Java 17+，Maven 3.9+ |
| 生产数据库（可选） | PostgreSQL 14+（`prod` profile） |
| 重编 WASM（可选） | Rust ≥ 1.82，`wasm32-unknown-unknown` |

## 快速开始（本地开发）

### 1. 安装前端依赖

```bash
npm install
# 或 npm run deps:install  # 使用 npmmirror 加速
```

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认 **dev** profile：H2 数据库 `./data/xjicloud-db`，模型目录 `./data/xjicloud`，端口 **8080**。

### 3. 构建 SuperSplat 子应用（高级编辑）

```bash
npm run build:supersplat
```

产物复制到 `public/supersplat/`，Vite dev 与 `npm run build` 会一并提供静态资源。

### 4. 启动前端

```bash
npm run dev
```

浏览器访问 **http://127.0.0.1:5174/login**（Vite 已将 `/api` 代理到 `8080`）。

### 5. 使用流程

1. 注册并登录 → 进入 **主页**（空屏）  
2. **新建项目** 或 **打开项目**（对话框选工程）；也可点击 **最近项目**  
3. **工程项目** 页查看/切换当前工程，上传 `.ply` / `.spz`  
4. 左侧栏 **数据上传** 可继续上传模型  
5. **模型查看**：Spark 标注 / 编辑 / 导出 SPZ  
6. **高级编辑**：SuperSplat 原生编辑，**保存到云端** 上传 PLY（覆盖云端模型文件）；保存后可在模型查看中重新加载  

### 双编辑器说明

| 入口 | 引擎 | 云端保存格式 |
|------|------|--------------|
| 模型查看 | Spark | SPZ |
| 高级编辑 | SuperSplat | PLY |

同一模型可先高级编辑保存 PLY，再在模型查看中打开（格式互通）。

## Linux 服务器部署

以下命令在 **Ubuntu / Debian 等 Linux** 上验证；JAR 与路径均按 Linux 约定。

### 1. 构建

```bash
# 前端（含 SuperSplat 子应用）
npm ci
npm run build:all
# 产物：dist/（含 dist/supersplat/）

# 后端
cd backend
mvn -DskipTests package
# 产物：backend/target/xjicloud-backend-1.0.0.jar
```

### 2. 数据目录与数据库

```bash
sudo mkdir -p /data/xjicloud
sudo chown "$USER:$USER" /data/xjicloud
```

**方式 A — 快速试跑（H2，无需 PostgreSQL）**

```bash
cd backend
java -jar target/xjicloud-backend-1.0.0.jar \
  --spring.profiles.active=dev \
  --xjicloud.storage.root=/data/xjicloud \
  --xjicloud.cors.allowed-origins=http://你的服务器IP,https://你的域名
```

**方式 B — 生产（PostgreSQL）**

```bash
# 创建库与用户后，使用 deploy/application-prod.yml.example 作为参考
java -jar target/xjicloud-backend-1.0.0.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/xjicloud \
  --spring.datasource.username=xjicloud \
  --spring.datasource.password=你的密码 \
  --xjicloud.jwt.secret=至少32位的随机密钥 \
  --xjicloud.storage.root=/data/xjicloud \
  --xjicloud.cors.allowed-origins=https://你的域名
```

### 3. Nginx

复制并按环境修改 [`deploy/nginx.conf.example`](deploy/nginx.conf.example)：

- `/` → `dist/` 静态文件，`try_files` 回退 `index.html`（SPA）
- `/supersplat/` → `dist/supersplat/`（SuperSplat 子应用静态资源）
- `/api/` → `http://127.0.0.1:8080`
- `client_max_body_size` 建议 ≥ 2G（大模型上传）

### 4. 健康检查

```bash
curl http://127.0.0.1:8080/actuator/health
```

### 验收清单

- [ ] 可注册 / 登录，进入主页（新建 / 打开 / 最近项目）  
- [ ] 工程项目页可切换当前工程并上传 PLY/SPZ；左侧栏「模型查看」加载模型  
- [ ] 标注、编辑、导出 SPZ，保存配置后刷新可恢复  
- [ ] 左侧栏「高级编辑」可加载 SuperSplat，保存 PLY 到云端后模型查看可重载  
- [ ] 左上角 logo、右上角用户菜单（退出登录）；左栏：航线规划 / 数据上传 / 模型查看 / 高级编辑  

## REST API 摘要

前缀：`/api/v1`（除登录外需 `Authorization: Bearer <token>`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/register`、`/auth/login` | 注册 / 登录 |
| GET/POST | `/projects` | 项目列表 / 创建 |
| GET | `/projects/{id}/models` | 模型列表 |
| POST | `/projects/{id}/models/upload` | 上传模型（multipart） |
| POST | `/models/{id}/download-token` | 签发 15 分钟短期下载 URL（供 SuperSplat iframe） |
| GET | `/models/{id}/download` | 下载模型（`Bearer` 或 `?access_token=`，支持 `Range`） |
| GET/PUT | `/models/{id}/viewer-config` | 读取 / 保存查看器 JSON |
| POST | `/models/{id}/export` | 上传导出文件（SPZ 或 PLY） |

## 配置项

| 配置键 | 默认值（dev） | 说明 |
|--------|----------------|------|
| `xjicloud.storage.root` | `./data/xjicloud` | 模型与 `viewer.json` 根目录；Linux 生产建议 `/data/xjicloud` |
| `xjicloud.jwt.secret` | 见 `application.yml` | **生产必须修改** |
| `xjicloud.cors.allowed-origins` | `http://127.0.0.1:5174,...` | 前端访问源，逗号分隔 |
| `server.port` | `8080` | 后端端口 |

前端可选环境变量：`VITE_API_BASE_URL`（留空则使用同源 `/api`，由 Nginx 或 Vite 代理）。

## 查看器配置格式

云端与本地均使用 **version 2** JSON（原 `{模型名}.viewer.json`）：

```json
{
  "version": 2,
  "defaultView": {
    "position": [0, 0, 5],
    "quaternion": [0, 0, 0, 1]
  },
  "pointAnnotations": [],
  "cubeMarkers": [],
  "projectInfo": {
    "projectName": "示例项目",
    "fields": [
      { "key": "coordinates", "label": "经纬度", "value": "" },
      { "key": "buildingName", "label": "建筑名称", "value": "" },
      { "key": "floorCount", "label": "楼层数", "value": "" },
      { "key": "height", "label": "高度", "value": "" }
    ]
  }
}
```

## 快捷键（图层查看页）

| 按键 | 功能 |
|------|------|
| `O` | 打开 / 选择模型 |
| `1` / `2` / `3` | 颜色标记 / 擦除 / 橡皮擦 |
| `Esc` | 查看模式 |
| `Ctrl+Z` / `Cmd+Z` | 撤回 |
| `Ctrl+Y` / `Cmd+Shift+Z` | 重做 |
| `Delete` | 删除选中标注 |

## 项目结构

```
XJICloud/
├── backend/                 # Spring Boot 3
│   └── src/main/java/com/xjicloud/
├── src/
│   ├── views/               # Home、Login、Projects、Upload、LayerViewer
│   ├── layouts/CloudLayout.vue
│   ├── components/SparkViewport.vue
│   ├── api/                 # REST 客户端
│   └── services/viewerStorage.ts
├── deploy/                  # nginx、application-prod 示例
├── electron/
└── rust/                    # WASM（spark-rs、spark-worker-rs）
```

## 致谢

- [Spark](https://github.com/sparkjs-dev/spark)（World Labs Technologies）
- [Three.js](https://threejs.org/)

## License

应用层代码可自由使用。`src/lib/spark/` 与 `rust/` 下 Spark 相关代码遵循 Spark 原始许可（Proprietary），详见 [sparkjs-dev/spark](https://github.com/sparkjs-dev/spark)。
