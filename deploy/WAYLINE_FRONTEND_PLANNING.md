# Wayline 航线规划架构说明

XJICloud「航线规划」通过 iframe 加载 VM 上的静态前端（`/route/`）。

## 正确架构（前端内置）

- 航线计算在**浏览器内**完成：`planMissionWithObstacleAnalysis`（JavaScript），可选加速使用仓库内**已提交的预编译 WASM**（`src/wasm/generated/`）。
- WASM 在**前端构建时**由 Vite 打包进 `/route/assets/`，访问网站的用户**无需**安装 Node、也无需本机编译 WASM。
- 用户只需打开云平台 →「航线规划」，即可使用。

## 底图（在线天地图 + 离线可选）

Wayline（仓库：`../Wayline`）侧栏支持三种地图模式：

1. **天地图（推荐上云）**：配置 `VITE_TIANDITU_MAP_KEY` 后默认使用在线底图，无需部署 `Data.gmdb`
2. **离线瓦片**：保留 GMDB/`sql.js` 方案，专网或无外网时可切回
3. **腾讯地图 GL**：独立容器，白模碰撞分析不可用

构建 Wayline 前请在其 `.env.production` 写入天地图 Key；产物同步到 `dist/route/`。

## 不要做的事

- 不要要求访客在本机运行 `start-wayline-server.bat` / 8787 端口服务。
- 不要在生产环境把 `VITE_PLANNING_API_BASE_URL` 指到 `127.0.0.1`。
- 不要让最终用户自行 `wasm:build`；开发者若需更新内核，在构建机上编译后把产物提交/打进前端包即可。

## 可选：开发用 Node 规划 API

Wayline 仓库里的 `server/` 仅供本地 API 联调或自动化测试，**不是**云平台访客依赖。开发时可用：

```bash
pnpm run server:dev   # 可选，127.0.0.1:8787
pnpm run dev          # 前端；规划默认走浏览器内核
```

## 部署前端到 VM

构建后同步 `dist/route/`（含 `index.html`、`assets/`、`cesium/` 等）到服务器 `/www/wwwroot/.../route/`。无需为每位用户部署规划后端。

## 本地算法实验（重要）

航线算法实验约定：**先本地改 → 本地测通 → 你确认后再 Git 远程**。

- 开发工作区：`../Wayline`（未确认前只改工作树，不 push）
- 备份镜像：`XJICloud/local/wayline-planning/`
- **算法原理文档（权威）**：[`local/wayline-planning/ALGORITHM.md`](../local/wayline-planning/ALGORITHM.md) —— 改算法时由 Cursor 规则强制同步更新
- **禁止**未经确认直接改生产 `/www/wwwroot/.../route/`（本机测试同步除外）
- **禁止**未经确认 `git push` Wayline 远程
