# Wayline 航线规划架构说明

XJICloud「航线规划」通过 iframe 加载 VM 上的静态前端（`/route/`）。

## 正确架构（前端内置）

- 航线计算在**浏览器内**完成：`planMissionWithObstacleAnalysis`（JavaScript + 仓库内**已提交的预编译 WASM**，`src/wasm/generated/`）。
- 编排策略：**WASM 优先，失败自动回退 JS**。覆盖模块：orbit / building / IG 补拍 / 多机扇区均分 / 白模径向绕障（高度采样仍在 JS/Cesium）。
- WASM 在**前端构建时**由 Vite 打包进 `/route/assets/`（产物含 `wayline_planner-*.wasm`），访问网站的用户**无需**安装 Node、也无需本机编译 WASM。
- 云嵌入：`/app/wayline` → iframe `/route/index.html`（或 `VITE_WAYLINE_ORIGIN` + `/route/`），**不**指向 `127.0.0.1:8787`。
- 用户只需打开云平台 →「航线规划」，即可使用。

## WASM 模块与对拍（开发者）

| 模块 | 导出 | 对拍命令（Wayline 根目录） |
|------|------|---------------------------|
| orbit | `plan_orbit_json` | `pnpm run wasm:compare:orbit:wasm` |
| building | `plan_building_json` | `pnpm run wasm:compare:building:wasm` |
| 绕障 | `plan_obstacle_json` | `pnpm run wasm:compare:obstacle` |
| IG | `plan_ig_json` | `pnpm run wasm:compare:ig` |
| 多机 | `plan_multi_uav_json` | `pnpm run wasm:compare:multi` |

更新 C++ 后：`pnpm run wasm:build`（需 emcc），再 `vite build` / `pnpm build`。

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
