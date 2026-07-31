# Prompt：用 WASM 重写 Wayline 航线规划内核（全流程可执行版）

> 用法：把本文整份（或从「角色与目标」起）粘贴给后续 Agent / 自己执行。  
> 范围：**仅 Wayline 航线计算内核**，与 XJICloud Spring / Admin 无关。  
> 日期基准：2026-07-31（以仓库当时结构为准，执行前先核对路径是否仍存在）。

---

## 角色与目标

你是资深前端 + C++/WASM 工程师。任务是：**把 Wayline 的「航线规划后端」从依赖 Node 规划服务 / 纯 JS，推进为浏览器内 C++/WASM 内核**，使云平台嵌入场景下用户打开 `/route/` 即可规划，无需部署独立规划微服务，也无需访客本机编译。

成功标准：

1. 规划在浏览器完成；生产环境**不依赖** `server/`（8787）与 `VITE_PLANNING_API_BASE_URL`。
2. 已迁移模块：同一输入下 WASM 与现有 JS 结果对齐（航点序列、关键统计在约定误差内）。
3. WASM 不可用时**自动回退 JS**，不阻塞规划。
4. 预编译产物提交进仓库；`pnpm build` 打进静态资源；访客零安装。
5. 同步更新算法文档 `XJICloud/local/wayline-planning/ALGORITHM.md`（变更记录必填）。

非目标（不要做）：

- 不要改 XJICloud Admin / Spring Boot / GPU Worker。
- 不要把 Cesium 渲染、天地图、白模加载迁进 WASM。
- 不要未经用户明确确认 `git push` Wayline 远程，或直接覆盖生产 `/www/wwwroot/.../route/`。
- 不要要求最终用户执行 `wasm:build`。

---

## 背景与原理（必须理解）

### 为什么

- 旧路径：前端采点 → HTTP 调 Node `server/` 算航线 → 返回。上云要多部署服务、配地址，和「云平台只嵌静态 `/route/`」冲突。
- 新路径：纯几何规划在浏览器执行。JS 可维护；复杂核可用 C++，经 Emscripten 编成 WASM，仍随静态页分发。

### 分层

| 层 | 职责 | 是否迁 WASM |
|----|------|-------------|
| UI / Cesium | 选点、白模点选、高度采样、展示导出 | 否 |
| 编排 JS | `planMissionWithObstacleAnalysis` 流水线 | 可保留 JS |
| 纯几何算法 | orbit / building / IG / 多机 / 绕障几何 | **是** |
| 场景适配 | `sceneObstacleAdapter` 采高度 → 数组 | 否（WASM 只吃数组） |

流水线（权威说明见 ALGORITHM.md）：

```text
UI 模式 → 种子航线(orbit|building) → 可选 IG → 可选多机 → 可选白模绕障 → routePlan
```

### WASM 是什么（执行时口头可省略，实现时遵守）

C++ 源码 → `emcc` → `wayline_planner.js` + `.wasm` → 浏览器加载 → `ccall` / JSON 交换纯数据。

---

## 仓库与关键路径

| 用途 | 路径 |
|------|------|
| 开发仓库 | `/root/workspace/Wayline`（或同级 `../Wayline`） |
| 算法文档（权威） | `/root/workspace/XJICloud/local/wayline-planning/ALGORITHM.md` |
| 上云架构说明 | `/root/workspace/XJICloud/deploy/WAYLINE_FRONTEND_PLANNING.md` |
| C++ 源 | `Wayline/native/wayline_planner.cpp` |
| 构建脚本 | `Wayline/scripts/build-wasm-planner.mjs`（`pnpm run wasm:build`） |
| 胶水 / 加载 | `Wayline/src/wasm/waylinePlannerKernel.js` |
| 预编译产物 | `Wayline/src/wasm/generated/` |
| 编排入口 | `Wayline/src/algorithm/core/missionPlanner.js` |
| JS 种子 | `Wayline/src/utils/routePlanner.js` |
| IG | `Wayline/src/utils/informationGainReshootPlanner.js` |
| 多机 | `Wayline/src/utils/multiUavPlanner.js` |
| 绕障 | `Wayline/src/utils/routeCollisionPlanner.js` |
| 可选 Node 后端 | `Wayline/server/`（仅本地联调，非云依赖） |

当前已知状态（执行前再核实）：

- `orbit`：已有 `plan_orbit_json` WASM 导出；编排层目前**优先 JS**，JS 失败才试 WASM（与 README「优先 WASM」描述可能不一致，迁移时一并理顺策略）。
- `building` / IG / 多机 / 绕障：仍以 JS 为主。

---

## 约束与安全

1. 工作约定：先本地改 → 本地测通 → **用户确认后再** Git 远程 / 同步生产静态目录。
2. 改算法必须同次更新 `ALGORITHM.md` 文末变更记录。
3. 保持 `base: '/route/'` 与云 iframe 嵌入兼容。
4. 导出函数变更时同步改 `build-wasm-planner.mjs` 的 `EXPORTED_FUNCTIONS`。
5. 使用简体中文回复用户；代码与提交说明可用英文短句。

---

## 全流程执行步骤（按顺序）

### Phase 0 — 对齐范围（与用户确认后再写代码）

向用户确认并记录：

1. 迁移范围：仅稳固 orbit / +building / +绕障 / 全量（含 IG、多机）？
2. 运行时策略：生产默认 WASM 优先还是 JS 优先？
3. 数值对齐：绝对一致还是允许误差（如坐标 1e-7、距离 1e-3 m）？
4. 是否保留 Node `server/` 仅作测试对比？

若用户未指定，默认假设：

- 顺序：orbit 稳固 → building → 绕障 → IG → 多机  
- 策略：WASM 可用则优先，失败回退 JS  
- 误差：经纬度 1e-7°，长度/高度 1 cm 级  
- `server/` 保留但文档标明非云依赖  

### Phase 1 — 摸底

1. 阅读 ALGORITHM.md、missionPlanner.js、routePlanner.js、waylinePlannerKernel.js、native/wayline_planner.cpp、build-wasm-planner.mjs。
2. 画出「模块 × 实现」表：JS 路径、WASM 是否已有、导出符号、输入输出 JSON 形状。
3. 确认本机是否有 `emcc`；若无，文档化安装步骤，但**不得**把「用户需装 Emscripten」写进产品路径。
4. 产出简短摸底结论给用户，再进入实现。

### Phase 2 — 基建与对拍框架

1. 固定构建：`pnpm run wasm:build` 可重复成功；产物写入 `src/wasm/generated/`。
2. 建立对比工具（脚本或单测均可）：
   - 固定 fixture（orbit / building 各至少 2 组）
   - 同输入跑 JS 与 WASM，断言结构字段与数值误差
3. 统一结果 schema（与现有 routePlan 对齐）：waypoints、stats、circles/bands 等现有字段不得无故改名。
4. 理顺 `buildBaseMissionPlanAsync` 的 runtime 选择与 `planningRuntime` 元数据（`javascript` | `wasm-cpp`）。

### Phase 3 — 迁移实现（按默认优先级）

对每个模块重复：**读 JS 行为 → C++ 实现 → 导出 → 胶水 → 编排接入 → 对拍 → 更新 ALGORITHM.md**。

#### 3.1 orbit（单点环绕）

- 对齐 `planSinglePointOrbitMission`。
- 已有 `plan_orbit_json`：补缺口、修偏差、补测试。
- 明确与 JS 优先级策略并改文档。

#### 3.2 building（立面 / 轮廓采样）

- 对齐 `planBuildingFootprintMission`。
- 输入：footprintPoints + form 相机/GSD/重叠等。
- 新增导出（示例名）：`plan_building_json`（最终以实现为准，需进 EXPORTED_FUNCTIONS）。
- `search` / `shape` 仍只产 footprint，**不单独做 WASM 模式**。

#### 3.3 绕障

- 对齐 `buildObstacleAwareMission`。
- WASM 只接收：基础航线 + 障碍高度采样数组（或等价纯数据），**不**在 WASM 内调 Cesium。
- 场景采样留在 `sceneObstacleAdapter`。

#### 3.4 IG 补拍

- 对齐 `appendInformationGainReshoot`。
- 文档中保持「代理实现 ≠ 论文逐行 FisherRF」定位，除非实现已升级。

#### 3.5 多机

- 对齐 `planMultiUavFromBasePlan` / `assembleMultiUavPlan`。
- 可在种子+绕障稳定后再迁。

### Phase 4 — 集成与上云验证

1. `pnpm build`（Wayline），确认 WASM 进产物；抽查 dist 含 `.wasm`。
2. 本地 `pnpm dev`：orbit / building 各跑通；断网或故意损坏 wasm 时 JS 回退仍可用。
3. 云嵌入：XJICloud `/app/wayline` → iframe `/route/`；确认未指向 `127.0.0.1:8787`。
4. 仅在用户确认后：同步静态到部署目录或提交/推送 Git。

### Phase 5 — 文档与收尾

1. 更新 ALGORITHM.md（原理若有变 + 变更记录）。
2. 更新 Wayline README / `deploy/WAYLINE_FRONTEND_PLANNING.md`：WASM 构建仅开发者、云上浏览器内规划。
3. 标注 `server/`：可选联调，非生产依赖。
4. 向用户提交：完成模块列表、对拍结果摘要、剩余风险、建议下一步。

---

## 接口约定（实现时遵守）

### 推荐交换格式

- C++ 导出：`*_json` 返回堆上 UTF-8 JSON 字符串指针；配套 `release_planner_string`。
- JS 胶水：`ccall` → `UTF8ToString` → `JSON.parse` → 映射为现有 plan 对象。
- 复杂输入（多边形、航点数组）优先 **一整段 JSON 字符串入参**，避免海量标量参数。

### 禁止

- WASM 内访问 DOM / Cesium / 网络。
- 改变 KML 导出对外字段语义（除非同步改导出方并说明）。
- 为通过编译而删减安全回退。

---

## 验收清单（全部勾选才算完成）

- [x] 目标模块均有 WASM 路径 + JS 回退
- [x] fixture 对拍通过
- [x] 生产构建含预编译 wasm，访客无需 emcc
- [x] 云 iframe 场景不依赖 Node 规划端口
- [x] ALGORITHM.md 已更新
- [x] 未擅自 push / 未擅自覆盖生产 route（除非用户当次明确要求）

---

## 建议的 Agent 工作方式

1. 先 Phase 0/1 输出摸底与计划，等用户一句「继续」再大规模改代码。
2. 小步提交：每完成一个模块（orbit / building / …）可本地 commit（仅当用户要求提交时）。
3. 遇到与文档冲突：以**代码真实行为**为准，并改文档消除冲突。
4. 通信：直接、简洁；用简体中文；不要提 Spring Admin。

---

## 一键启动语（可复制）

```text
请严格按 XJICloud/local/wayline-planning/WASM_REWRITE_PROMPT.md 执行「Wayline 航线规划 WASM 重写」。
先做 Phase 0 确认与 Phase 1 摸底，输出模块对照表与计划，等我确认后再写代码。
工作目录以 /root/workspace/Wayline 为主；算法文档同步到
/root/workspace/XJICloud/local/wayline-planning/ALGORITHM.md。
未经我确认不要 git push，不要改生产 /www/wwwroot/.../route/。
```

---

## 变更记录（本文）

| 日期 | 摘要 |
|------|------|
| 2026-07-31 | 初版：全流程 Prompt（原因/原理/路径/分阶段/验收/启动语） |
