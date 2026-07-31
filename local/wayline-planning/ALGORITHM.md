# 航线规划算法原理

> **维护约定**：凡修改航线规划相关逻辑，须同步更新本文（见文末「变更记录」）。  
> **源码位置**：`../Wayline`（开发）· 备份镜像 `./`（本目录）  
> **编排入口**：`Wayline/src/algorithm/core/missionPlanner.js` → `planMissionWithObstacleAnalysis`

---

## 1. 总流水线

```
UI 模式（orbit / building / search / shape）
        ↓
① 种子航线
   · orbit  → planSinglePointOrbitMission
   · building / search / shape → planBuildingFootprintMission
        ↓
② 信息增益补拍（可选）→ appendInformationGainReshoot
        ↓
③ 多机扇区均分（可选）→ planMultiUavFromBasePlan
        ↓
④ 白模径向绕障（有白模时）→ buildObstacleAwareMission
        ↓
routePlan（航点 / 圈层 / 统计 / KML）
```

| 阶段 | 文件 | 入口 |
|------|------|------|
| 编排 | `src/algorithm/core/missionPlanner.js` | `planMissionWithObstacleAnalysis` |
| 环绕种子 | `src/utils/routePlanner.js` / **WASM** `native/wayline_planner.cpp` | `planSinglePointOrbitMission`；编排优先 `planOrbitMissionWithWasm`，失败回退 JS |
| 立面种子 | `src/utils/routePlanner.js` / **WASM** `native/wayline_building.inl` | `planBuildingFootprintMission`；编排优先 `planBuildingMissionWithWasm`，失败回退 JS |
| IG 补拍 | `src/utils/informationGainReshootPlanner.js` / **WASM** `native/wayline_ig.inl` | `appendInformationGainReshoot`；编排优先 `planIgReshootWithWasm`，失败回退 JS |
| 多机 | `src/utils/multiUavPlanner.js` / **WASM** `native/wayline_multi_uav.inl` | `planMultiUavFromBasePlan`；编排优先 `planMultiUavWithWasm`，失败回退 JS；绕障后合并仍用 JS `assembleMultiUavPlan` |
| 绕障 | `src/utils/routeCollisionPlanner.js` / **WASM** `native/wayline_obstacle.inl` | `buildObstacleAwareMission`；编排优先 `planObstacleMissionWithWasm`（高度采样仍在 JS），失败回退 JS |

`search` / `shape` 只负责得到 footprint，**不单独成算法**，最终走 building。

---

## 2. 公共相机与几何模型

### 2.1 大地测量

- **Haversine**：两点球面距离  
- **Bearing**：大圆方位角（北 0°，东 90°）  
- **projectDestination**：沿方位角前进指定距离  

航点 `heading = bearing(相机 → 目标)`，俯角来自参数 `pitchDeg`。

### 2.2 视场与站位距离

传感器默认 36×24 mm，焦距 \(f\)：

\[
\mathrm{HFOV}=2\arctan\frac{W_s}{2f},\quad
\mathrm{VFOV}=2\arctan\frac{H_s}{2f}
\]

覆盖宽高（米）：\(W_c=w_{px}\cdot\mathrm{GSD}/1000\)，\(H_c\) 同理。

站位距离：

\[
D=\max\left(
\frac{W_c}{2\tan(\mathrm{HFOV}/2)},\;
\frac{H_c}{2\tan(\mathrm{VFOV}/2)}
\right)
\]

### 2.3 垂直分层 `buildBandCenters`

目标高度 \([h_{low},h_{high}]\)，垂直覆盖 \(H_v\)，重叠率 \(\rho_v\)：

\[
\Delta h = H_v(1-\rho_v)
\]

带中心从 \(h_{low}+H_v/2\) 步进到 \(h_{high}-H_v/2\)，再 `compressBandCenters` 压到有限层（建筑侧最多约 6 层）。

飞行高度：

\[
h_{fly}=\max\bigl(h_{band}-R\tan\theta,\;10\,\mathrm{m}\bigr)
\]

---

## 3. 算法 A：单点多层圆环（Orbit）

**函数**：`planSinglePointOrbitMission`  
**输入**：中心点、边缘点、焦距 / GSD / 俯角 / 重叠等  
**输出**：多层同心圆航点，`kind: 'orbit'`

### 原理

1. \(R_{target}=\mathrm{Haversine}(中心,边缘)\)，\(R_{orbit}=R_{target}+D\)  
2. 垂直多层（§2.3）  
3. 单圈张数（面向 SfM / 3DGS）取较大值：  
   - 视场：\(\lceil 360/(\mathrm{HFOV}(1-\rho_h))\rceil\)  
   - 弧长：\(\lceil 2\pi R/(W_c(1-\rho_h))\rceil\)  
4. 等方位角投影航点；环尾 `closeLoop`；层间 `connector`

**本质**：开环多层等高圆环（非真螺旋）。

---

## 4. 算法 B：建筑轮廓外扩立面（Building）

**函数**：`planBuildingFootprintMission`

### 步骤

1. 轮廓 → 局部米制 → 去重 / 简化转角 / 短边  
2. 凸包 + **最小面积 OBB** → 覆盖包络  
3. `createOffsetPolygon` 法向外扩距离 \(D\)  
4. `generateOffsetFacadeSamples`：沿边步进 \(\Delta s=W_c(1-\rho_h)\)（默认水平重叠约 70%；每面最多 24 段）  
5. 垂直多层；航点 `kind: 'building-orbit'`，朝向立面目标点  

**本质**：轮廓外扩 + 立面条带扫描 + 多层。

---

## 5. 算法 C：信息增益补拍（IG Reshoot）

**函数**：`appendInformationGainReshoot` / WASM `plan_ig_json`  
**UI**：采样参数区 ·「信息增益补拍」开关 +「速度 ↔ 质量」β 滑条  
**航点**：`kind: 'ig-reshoot'`（地图绿色 `IG*`）

编排优先 WASM，失败回退 JS。仍为几何代理，**不是** FisherRF 源码移植。

### 5.1 定位（必读）

**不是** FisherRF / POp-GS 源码移植，而是其 **NBV + 信息增益思想的几何代理**：

- 产品约束：起飞前一次性规划、浏览器内实时、无在线 3DGS 训练  
- 代理目标：未覆盖单元 × 视角新颖度（视差） / 旅行代价  

### 5.2 文献与思路来源

| 来源 | 出处 | 吸收点 |
|------|------|--------|
| 经典 NBV / EIG | Active Perception | \(\arg\max_v I(M;z_v\mid v)\) |
| P-Optimality | Optimal Experimental Design | 用信息量衡量「拍这张的价值」 |
| **FisherRF** | Jiang et al., **ECCV 2024 Oral**, [arXiv:2311.17874](https://arxiv.org/abs/2311.17874) | 3DGS/辐射场上 Fisher≈EIG 选视角 |
| **POp-GS** | Wilson et al., **CVPR 2025** | T/D-Optimality，改进信息量化 |
| **GS-Planner** | Jin et al., **IROS 2024**, [arXiv:2405.10142](https://arxiv.org/abs/2405.10142) | 完备性+质量引导；无人机闭环参考 |
| RT-GuIDE / ActiveSplat 等 | 2024–2025 | 信息/旅行代价、减重复走线 |

### 5.3 实现步骤

1. **覆盖模型**  
   - Orbit：方位 × 高度带网格  
   - Building：边 × 段 × 高度带  
   种子航点投影累加 `coverage`；空洞与边界 prior 更高。

2. **候选生成**  
   欠覆盖单元上生成相机位姿；β≥0.55 时加 **次外圈半径**（视差，利 3DGS）。

3. **β 速度–质量**（`resolveIgReshootBudget`）  

   | 量 | β↑ 时 |
   |----|-------|
   | 补拍上限 | 种子张数 ~6% → ~42% |
   | 最小角间隔 | ~28° → ~10° |
   | 旅行代价指数 | 高 → 低 |
   | 次外圈视差 | 关 → 开 |

4. **贪心效用**

\[
U=\frac{\mathrm{IG}}{1+(d/40)^{\alpha}}
\]

选 \(U\) 最大且角间隔足够、不与种子重合的点；增益过低则停。补拍圈接在种子航线之后。

### 5.4 与真 FisherRF 对照

| | FisherRF / POp-GS | 本项目 IG |
|--|-------------------|-----------|
| 地图 | 在线 3DGS | 几何覆盖网格 |
| 信息量 | Fisher / T·D-opt | 未覆盖 + 视差 bonus |
| 时机 | 边飞边训边选 | 起飞前开环 |
| 环境 | 研究 / GPU | 浏览器 |

后续若接入真 3DGS，可只替换 `candidateInformationGain`，保留 β 预算与贪心框架。

---

## 6. 算法 D：多机扇区均分

**函数**：`planMultiUavFromBasePlan` / WASM `plan_multi_uav_json` · `splitIndexRanges`

每环拍照点按索引均分给 N 机；**同一机跨所有高度层持有同一扇区**，降低交叉与 makespan。非多智能体 NBV。编排优先 WASM；绕障后按机合并仍用 JS `assembleMultiUavPlan`。

---

## 7. 算法 E：白模径向绕障

**函数**：`buildObstacleAwareMission` / WASM `plan_obstacle_json`

1. 环上相邻航点折线按约 18 m 采样白模高度（Cesium 采样在 JS；WASM 只吃 `heightSamples` 数组）  
2. 与飞行高度比净空（默认约 12 m）  
3. 目标 footprint 可忽略  
4. 碰撞则中点径向外扩试探：20…220 m → `detour`；失败 → `risky`  

局部几何绕行，非 A* / RRT。腾讯底图或关白模时跳过。编排层优先 WASM，失败回退 JS。

---

## 8. 设计取向小结

| 维度 | 本项目 |
|------|--------|
| 主目标 | 环绕 / 立面成像（含 3DGS 素材） |
| 范式 | 开环几何 + 几何 IG 补拍 |
| 重叠 | 水平/垂直重叠驱动密度 |
| 安全 | 白模采样 + 径向 detour |
| 多机 | 同扇区跨层切分 |

**未实现**：正射网格、割草机航线、真螺旋、倾斜五航线、在线 FisherRF。

---

## 9. 相关路径

| 路径 | 说明 |
|------|------|
| `../Wayline/src/utils/routePlanner.js` | Orbit / Building |
| `../Wayline/src/utils/informationGainReshootPlanner.js` | IG 补拍 |
| `../Wayline/src/algorithm/core/missionPlanner.js` | 总编排 |
| `deploy/WAYLINE_FRONTEND_PLANNING.md` | 部署与本地约定 |
| `local/wayline-planning/README.md` | 本地开发工作流 |

---

## 10. 变更记录

> 代理或开发者修改航线算法后，**在此追加一条**（日期 · 摘要 · 涉及文件）。

| 日期 | 变更 | 涉及 |
|------|------|------|
| 2026-07-31 | Phase 4/5：生产构建确认 `dist/assets/wayline_planner-*.wasm`；云 iframe 仍为 `/route/`、不依赖 8787；更新部署说明与 README | `deploy/WAYLINE_FRONTEND_PLANNING.md`, `Wayline/README.md`, 本文件 |
| 2026-07-31 | WASM 多机扇区均分：`plan_multi_uav_json`；编排优先 WASM 回退 JS；`assembleMultiUavPlan` 仍 JS | `native/wayline_multi_uav.inl`, `wayline_planner.cpp`, `waylinePlannerKernel.js`, `missionPlanner.js`, `scripts/compare-multi-uav-js-wasm.mjs`, `src/wasm/generated/` |
| 2026-07-31 | WASM IG 补拍：`plan_ig_json`；几何代理覆盖/贪心选点迁入 C++；编排优先 WASM 回退 JS；对拍 js↔wasm↔native | `native/wayline_ig.inl`, `wayline_planner.cpp`, `waylinePlannerKernel.js`, `missionPlanner.js`, `scripts/compare-ig-js-wasm.mjs`, `src/wasm/generated/` |
| 2026-07-31 | WASM 白模径向绕障：`plan_obstacle_json`；高度采样留 JS，几何判决进 C++；编排优先 WASM 回退 JS；对拍 js↔wasm↔native | `native/wayline_obstacle.inl`, `wayline_planner.cpp`, `waylinePlannerKernel.js`, `missionPlanner.js`, `routeCollisionPlanner.js`, `scripts/compare-obstacle-js-wasm.mjs`, `src/wasm/generated/` |
| 2026-07-31 | WASM building 立面采样：多边形外扩/立面采样迁入 C++，`plan_building_json`；编排优先 WASM 回退 JS；对拍脚本 | `native/wayline_building.inl`, `wayline_planner.cpp`, `waylinePlannerKernel.js`, `missionPlanner.js`, `scripts/compare-building-*.mjs`, `src/wasm/generated/` |
| 2026-07-31 | WASM orbit 对齐 JS：水平重叠/自动张数/层数上限 6；编排改为优先 WASM 失败回退 JS；新增 JS↔C++/WASM 对拍脚本 | `native/wayline_planner.cpp`, `waylinePlannerKernel.js`, `missionPlanner.js`, `scripts/compare-orbit-*.mjs`, `src/wasm/generated/` |
| 2026-07-28 | 初版：文档化 Orbit / Building / IG / 多机 / 绕障；IG 标明 FisherRF 等思想来源与几何代理实现 | 本文件 |
| 2026-07-28 | 水平重叠默认提高、立面分段加密、环绕自动张数；新增 IG 补拍与 β；UI 置于采样参数区 | `routePlanner.js`, `informationGainReshootPlanner.js`, `missionPlanner.js`, `plannerPanel.vue`, `mapContainer.vue` |
