# 训练队列驱动的 CCI GPU Worker 自动启停 — 产品需求文档 (PRD)

| **文档版本** | **创建日期** | **状态** | **适用对象** |
| ------------ | ------------ | -------- | ------------ |
| **v2.0** | 2026-07-23 | 草案 / 待评审 | 后端开发、容器运维、Admin 前端、测试 |
| **取代** | v1.0（ACK + ECI 沙箱型动态算力 PRD） | — | 原文档与本项目交互模型不符，已废弃 |

**相关文档：** [Deploy.md §5.6](Deploy.md)、[deploy/k8s/overlays/aliyun/CCI.md](deploy/k8s/overlays/aliyun/CCI.md)、[AGENT_CONTEXT.md](AGENT_CONTEXT.md)

---

## 1. 项目背景与目标

### 1.1 背景

XJICloud 是 3D Gaussian Splatting（3DGS）建模云平台。用户上传图片数据集后，训练任务进入 Redis 队列 `xjicloud:jobs`，由 **GPU Worker**（`gpu-worker/`）主动注册、心跳，并通过长轮询 `GET /api/v1/worker/jobs/next` 领取任务。

生产环境算力约定为 **阿里云 CCI（弹性容器实例）** 运行 `xjicloud-gpu-worker` 镜像（见 Deploy.md §5.6）。当前缺口是：

1. **启停靠人工**：队列积压时需运维手动创建 CCI；空闲时需手动停止，否则持续计费。
2. **任务易卡住**：无 ONLINE Worker 时，任务长期停留在 `QUEUED`，用户只能干等。
3. **文档已预留扩展点**：Deploy.md §5.6.3 / CCI.md 写明可按 `LLEN xjicloud:jobs` 或 Admin 队列深度调用 CCI OpenAPI，但**后端尚未实现**。

本需求只解决「**训练队列驱动的 Worker 容量自动伸缩**」，不引入用户直连沙箱、动态域名网关等能力。

### 1.2 核心目标

| 目标 | 说明 |
|------|------|
| **有活就起** | 存在 `QUEUED` 任务且 ONLINE Worker 不足时，自动创建 CCI 实例并注入正确环境变量，使 Worker 能连上后端完成注册。 |
| **没空就停** | 队列为空、无 `RUNNING` 任务、且空闲超过可配置阈值后，自动停止 CCI，避免 GPU 空转计费。 |
| **兼容现有协议** | 不改变 Worker ↔ Backend 的 register / heartbeat / jobs/next / progress / complete / fail 协议；不改变 `TrainingJob` 状态枚举。 |
| **可观测、可手动** | Admin 可查看伸缩状态、手动 scale、一键清理闲置实例；记录启停时间戳便于与阿里云账单对账。 |
| **防泄漏** | 创建失败可重试/告警；心跳超时 Worker 标 OFFLINE；卡住的 CCI 实例可被 Reaper 回收。 |

### 1.3 非目标（本阶段明确不做）

- 用户浏览器直连算力容器、二级域名 / 路径动态路由、WebSocket 透传网关。
- 面向用户的「申请任意镜像 / CPU 沙箱」API（`/compute/instances` 一类）。
- 常驻大规模预热池（默认 GPU 预热数为 **0**；可选最多预热 1 台，默认关闭）。
- 强制将训练 Pod 跑在 ACK Virtual Kubelet + ECI 上（见 §2.1 选型；MVP 以 **CCI OpenAPI** 为准）。
- 替换真实训练算法（仍为 `gpu-worker` 内 trainer；与本 PRD 解耦）。

---

## 2. 系统总体架构

### 2.1 技术选型（决策）

| 方案 | 说明 | MVP |
|------|------|-----|
| **A. CCI OpenAPI** | 后端（或调度组件）直接调用阿里云 CCI API 创建/停止容器组；Worker 与后端同 VPC，主动连 `XJICLOUD_BACKEND_URL` | **采用** |
| B. ACK + 虚拟节点 + ECI Pod | 通过 K8s API 创建/删除 Pod，由 Virtual Kubelet 落到 ECI | 可选后续；若采用须同步改写 Deploy / CCI.md |

理由：仓库 ACK overlay（`deploy/k8s/overlays/aliyun/`）** intentionally 不含 gpu-worker**，生产算力文档已锚定 CCI；方案 A 与现有部署一致、改动面更小。

本地 / 预生产仍可用：Docker 常驻 Worker 或 K3s `deploy/k8s/gpu-worker/`（mock），**不走自动 CCI 伸缩**（配置开关关闭即可）。

### 2.2 架构图

```
  用户 Vue 前端                         Admin 面板
       │                                    │
       │ /api/v1/...                        │ /api/v1/admin/...
       ▼                                    ▼
┌─────────────────────────────────────────────────────────┐
│              Spring Boot 后端 (8080)                      │
│  TrainingJob + RedisQueue (xjicloud:jobs)                 │
│  Worker API (register / heartbeat / jobs/next / ...)      │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ 新增 ComputeScaler（调度器）                         │ │
│  │  · 周期评估：queueDepth、ONLINE workers、RUNNING jobs │ │
│  │  · 决策：scale-up / scale-down / no-op               │ │
│  │  · 调用：Aliyun CciClient                            │ │
│  │  · 持久化：cci_instances（或等价表）启停记录          │ │
│  └──────────────────────────┬──────────────────────────┘ │
└─────────────────────────────┼────────────────────────────┘
                              │ CCI OpenAPI
                              ▼
                    阿里云 CCI（GPU 实例）
                    镜像: xjicloud-gpu-worker
                    env: XJICLOUD_BACKEND_URL, WORKER_SECRET, WORKER_NAME
                              │
                              │ register / heartbeat / poll jobs
                              ▼
                         同一后端 :8080
                              │
                    OSS presigned 读写数据集/产出
```

**流量原则（不变）：**

- 用户浏览器 **不** 访问 Worker / CCI。
- 大数据走 OSS presigned URL；Worker 一般无需长期 OSS AccessKey。
- Nginx 仅反代 `/api/` 到后端；无需为 CCI 配置动态路由。

### 2.3 组件职责

| 组件 | 职责 |
|------|------|
| **ComputeScaler** | 定时评估伸缩条件；幂等创建/停止 CCI；分布式锁防多副本重复操作 |
| **CciClient** | 封装阿里云 CCI OpenAPI（创建容器组、查询、删除/停止） |
| **现有 Worker 子系统** | 注册、心跳、领任务；Scaler **不替代** 心跳，只负责「机器在不在」 |
| **Admin API / 面板** | 展示状态、手动伸缩、一键清理、配置水位 |

---

## 3. 详细功能需求

### 3.1 两层状态模型（必须分开）

#### 3.1.1 TrainingJob（已有，禁止改语义）

`PENDING | UPLOADING | QUEUED | RUNNING | COMPLETED | FAILED | CANCELLED`

Scaler **只读**队列与 Job 状态，不发明新的 Job 状态。

#### 3.1.2 CCI / Worker 实例状态（新增）

| 状态 | 含义 | 典型下一状态 |
|------|------|--------------|
| **PROVISIONING** | 已调用 CCI 创建，等待实例 Running 且 Worker 完成 register | ONLINE / FAILED |
| **ONLINE** | `worker_nodes` 中对应节点心跳正常（复用现有心跳超时逻辑） | DRAINING / OFFLINE |
| **DRAINING** | 已发起停止：不再期望领新任务；等待当前 RUNNING 结束或超时强制停 | TERMINATED |
| **OFFLINE** | 心跳超时或实例异常（已有 Worker 标记逻辑可对齐） | PROVISIONING（补启）/ TERMINATED |
| **TERMINATED** | CCI 已停止/删除，记录保留用于对账 | 终态（记录层） |
| **FAILED** | 创建失败（额度、镜像、网络等） | 可重试 → PROVISIONING |

可选 **WARM**（预热空闲实例）：**默认关闭**；若开启，最多 1 台，且须在成本评审通过后启用。

### 3.2 伸缩策略 (ComputeScaler)

#### 3.2.1 配置项（建议前缀 `xjicloud.compute`）

| 配置键 | 默认建议 | 说明 |
|--------|----------|------|
| `enabled` | `false` | 总开关；本地/预生产默认关 |
| `provider` | `cci` | MVP 仅实现 `cci` |
| `max-workers` | `1` | 同时存在的 CCI Worker 上限（可配置升高） |
| `min-workers` | `0` | 空闲时保留数；默认 scale-to-zero |
| `idle-timeout-sec` | `300` | 队列空且无 RUNNING 后，空闲多久再停 |
| `poll-interval-sec` | `30` | Scaler 评估周期 |
| `provision-timeout-sec` | `600` | PROVISIONING 超时则标 FAILED 并可告警 |
| `image` | ACR 上的 `xjicloud-gpu-worker` | 与 CCI.md 一致 |
| `backend-url` | 后端内网地址 | 写入 CCI 环境变量 `XJICLOUD_BACKEND_URL` |
| `worker-secret` | 与 `xjicloud.worker.shared-secret` 同源 | 写入 `WORKER_SECRET` |
| `vpc` / `vswitch` / `security-group` / `region` 等 | — | 创建 CCI 所需网络与规格参数 |
| `gpu-spec` | 按业务选定 | GPU 规格；文档中写清示例值 |

#### 3.2.2 Scale-up 条件（同时满足才创建）

1. `xjicloud.compute.enabled == true`
2. `queueDepth > 0`（Redis `LLEN xjicloud:jobs`，与 Admin dashboard 同源）**或** 存在状态为 `QUEUED` 的 Job
3. `onlineWorkerCount + provisioningCount < 所需容量`  
   - MVP：**所需容量 = min(max-workers, 1)** 当 queueDepth ≥ 1（先做「有队至少 1 台」）  
   - 后续：可按 queueDepth 线性扩到 `max-workers`
4. 未持有过期的创建锁；同一逻辑名/标签下无重复创建中的实例（**幂等**）

创建成功后：

- 写入实例记录（云上 instanceId、本地状态 PROVISIONING、`created_at`）
- 等待 Worker `POST /api/v1/worker/register` 成功 → 关联 `worker_nodes.id`，状态改为可视为 ONLINE

#### 3.2.3 Scale-down 条件（同时满足才停止）

1. `queueDepth == 0`
2. 无 `RUNNING` 的 TrainingJob（或仅允许 DRAINING 等待当前任务完成）
3. `onlineWorkerCount > min-workers`
4. 空闲时长 ≥ `idle-timeout-sec`（自「上次队列非空或上次有 RUNNING」起算）

停止流程：

1. 状态 → DRAINING  
2. 调用 CCI 停止/删除 API  
3. 成功后状态 → TERMINATED，写 `destroyed_at`  
4. 对应 `worker_nodes` 标记 offline（可复用 Admin `POST .../workers/{id}/offline` 逻辑）

**注意：** 若仍有 RUNNING 任务，MVP 默认 **不杀** 该 Worker；仅对「确认空闲」的实例缩容。可选增强：取消任务后再强杀（需产品确认，默认不做）。

#### 3.2.4 Reaper / 防泄漏

| 场景 | 行为 |
|------|------|
| PROVISIONING 超时 | 标 FAILED；尝试删除可能残留的云资源；告警/日志 |
| Worker 心跳超时 | 现有逻辑标 OFFLINE；Scaler 下一轮可按队列决定是否补启 |
| TERMINATED 但云上仍 Running | 对账扫描：以云 API 列表为准补删 |
| 控制面多副本 | Redis 分布式锁（如 `xjicloud:compute:scaler-lock`），避免双创建 |

### 3.3 与现有 Worker 心跳的关系

- **唯一心跳权威**：继续由 Worker 调用 `POST /api/v1/worker/heartbeat`（及 poll 时附带刷新）。
- **禁止**要求用户前端每 10 秒对「算力实例」续约。
- Scaler 判断「活着」优先看：`worker_nodes` 心跳未超时 +（可选）CCI 实例 Running。

### 3.4 安全与网络

1. CCI 与后端 **同一 VPC**；安全组：CCI → 后端 TCP 8080；CCI → OSS HTTPS 443。
2. **不向公网暴露** Worker 业务端口；无动态接入网关。
3. 镜像固定为受控的 `xjicloud-gpu-worker`；不接受客户端任意 `image` 参数（防供应链与逃逸面）。
4. `WORKER_SECRET` / AccessKey 仅存服务端配置或 K8s Secret / 环境变量，**不进前端、不进仓库明文**。
5. Admin 操作需 Admin JWT；普通用户 JWT 不可调用伸缩接口。

### 3.5 Admin 功能

在现有 Admin（Workers / Jobs / Dashboard）上扩展，而非另起运维站：

| 能力 | 说明 |
|------|------|
| 伸缩总览 | 开关状态、queueDepth、ONLINE/PROVISIONING 数量、`max-workers`、最近一次伸缩决策 |
| 实例列表 | 本地记录的 CCI 实例：云 ID、状态、关联 workerId、创建/销毁时间、失败原因 |
| 手动 scale | 「启动 1 台」「停止指定实例」——仍走同一 CciClient，写审计日志 |
| 一键清理闲置 | 停止所有「无 RUNNING 任务且心跳已死/已标记空闲」的 CCI |
| 配置 | 允许热更新部分项（如 `max-workers`、`idle-timeout-sec`）；敏感项可仅 yml |

---

## 4. API 接口规范

统一响应格式（与现有一致）：

```json
{ "success": true, "message": "...", "data": { } }
```

鉴权：Admin JWT（`Authorization: Bearer <admin_token>`）。  
路径前缀：`/api/v1/admin/compute`（挂在现有 admin 安全链下）。

### 4.1 查询伸缩状态

- **GET** `/api/v1/admin/compute/status`

**data 示例：**

```json
{
  "enabled": true,
  "provider": "cci",
  "queueDepth": 3,
  "runningJobs": 1,
  "onlineWorkers": 1,
  "provisioningWorkers": 0,
  "maxWorkers": 2,
  "minWorkers": 0,
  "idleTimeoutSec": 300,
  "lastDecision": "SCALE_UP",
  "lastDecisionAt": "2026-07-23T10:00:00Z",
  "instances": [
    {
      "id": "uuid",
      "cloudInstanceId": "cci-xxxxx",
      "status": "ONLINE",
      "workerId": "uuid-or-null",
      "workerName": "cci-worker-01",
      "createdAt": "2026-07-23T09:55:00Z",
      "destroyedAt": null,
      "failureReason": null
    }
  ]
}
```

### 4.2 手动伸缩

- **POST** `/api/v1/admin/compute/scale`

```json
{
  "action": "UP",
  "count": 1
}
```

或 `"action": "DOWN", "instanceId": "<本地记录 uuid>"`。

- 校验：`UP` 后总数不超过 `max-workers`；`DOWN` 时若实例上有 RUNNING Job，MVP 返回失败并提示（除非强制标志且产品允许）。

### 4.3 一键清理闲置

- **POST** `/api/v1/admin/compute/reap-idle`

停止所有满足 scale-down 安全条件的实例；返回实际停止数量。

### 4.4 不提供的用户侧 API

以下 **不在** 本 PRD 范围：

- `POST /api/v1/compute/instances`（用户申请沙箱）
- 用户侧实例 heartbeat / 动态 endpoint

普通用户继续只使用现有：

- 数据集上传 / `jobs` / SSE `GET /api/v1/jobs/{id}/events`

---

## 5. 非功能需求 (NFR)

### 5.1 性能与时效（现实指标）

| 指标 | 目标 |
|------|------|
| Scaler 决策周期 | 默认 30s 可配 |
| 从「队列非空且无 Worker」到「Worker ONLINE」 | P95 视 GPU 规格与镜像缓存而定；文档验收建议 **≤ 5～10 分钟**（非 500ms） |
| Scale-down 在 idle-timeout 满足后 | 下一评估周期内发起停止 |
| 幂等创建 | 并发两次 scale-up 最多创建 `max-workers` 台，不双开 |

> 不承诺「预热分配 &lt; 500ms」：本系统是 GPU 批处理 Worker，用户感知的是任务进度 SSE，不是沙箱打开速度。

### 5.2 可用性

- 后端多副本时：Scaler 用 Redis 锁，同一时刻仅一个执行者。
- Redis 短暂不可用：暂停自动伸缩并打错误日志；不误删正在跑训练的实例。
- CCI API 失败：指数退避重试（有上限）；写入 `failureReason` 供 Admin 查看。

### 5.3 成本与审计

- 每条实例记录必须有 `created_at` / `destroyed_at`（或等价），可计算运行秒数。
- 日志包含：决策原因（queueDepth、onlineWorkers）、云 instanceId、结果成功/失败。

### 5.4 技术约束

- 实现语言：Java 17 + Spring Boot（现有 `backend/`），用 `@Scheduled` 或等价调度，**不**引入独立 Go「协程控制面」作为 MVP。
- 可选后续：将 Scaler 拆成独立服务；非本阶段必做。

---

## 6. 运维、监控与验收

### 6.1 监控（MVP → 增强）

**MVP：** Admin 面板即可（优先）。

**增强（可选）：** Prometheus 指标，例如：

- `xjicloud_compute_queue_depth`
- `xjicloud_compute_workers_online`
- `xjicloud_compute_cci_provisioning`
- `xjicloud_compute_scale_up_total` / `scale_down_total`
- `xjicloud_compute_provision_failures_total`

### 6.2 验收清单

- [ ] `enabled=false` 时行为与现网一致（不自动调 CCI）。
- [ ] 无 ONLINE Worker 时创建训练任务 → 任务入队 → 自动创建 CCI → Worker 在 Admin 显示 ONLINE → 任务 RUNNING → COMPLETED，可下载 `model.ply`。
- [ ] 队列清空且无 RUNNING、空闲超过 `idle-timeout-sec` → CCI 被停止 → 不再持续计费（以云控制台/账单或实例状态为准）。
- [ ] 手动「启动 1 台 / 停止 / 一键清理」可用且写审计信息。
- [ ] 重复触发 scale-up 不超 `max-workers`、不产生无名重复实例。
- [ ] PROVISIONING 超时能标 FAILED 并尝试清理残留。
- [ ] CCI 与后端同 VPC；Worker 仅用 `XJICLOUD_BACKEND_URL` + `WORKER_SECRET` 即可完成一轮训练（OSS 走 presigned）。
- [ ] 本地开发关闭开关后，仍可用 Docker / K3s mock Worker 跑通现有流水线。

### 6.3 与现有部署文档的关系

| 环境 | Worker 来源 | 本功能 |
|------|-------------|--------|
| 本地 Compose / 本机 Docker | 常驻 `gpu-worker` | `enabled=false` |
| 本地 K3s | `deploy/k8s/gpu-worker` | `enabled=false` |
| 预生产分机 | 服务器 D 常驻 Docker | 可选开启 Scaler 或仍手动 |
| 生产 ACK + CCI | CCI 按需 | **`enabled=true`**，本 PRD 主战场 |

---

## 7. 实现分期建议

| 阶段 | 范围 | 交付 |
|------|------|------|
| **P0** | 配置开关 + CciClient 创建/停止 + 定时 Scale 到 0/1 + 实例表 + Admin status/手动 scale | 能自动跑通「有队起、空了停」 |
| **P1** | Reaper、provision 超时、审计对账字段、一键清理、失败原因展示 | 防泄漏与可运维 |
| **P2** | `max-workers > 1` 的线性扩缩、可选预热 1 台、Prometheus | 更高并发与可观测 |
| **P3（可选）** | ACK Virtual Node + ECI 作为第二 provider | 仅当运维明确要求与 CCI 对齐 K8s 工作流时 |

---

## 8. 修订说明（相对 v1.0）

| v1.0（已废弃） | v2.0（本文） |
|----------------|--------------|
| 通用沙箱 / 推理 / 渲染节点 | 仅 3DGS **训练 GPU Worker** |
| 用户申请实例 + 动态域名网关 | 队列驱动伸缩；用户不连 Worker |
| ECI + Virtual Kubelet 为主叙事 | MVP = **CCI OpenAPI**；ACK 仅作后端可选部署 |
| 前端 10s 实例心跳 + 30s 空闲杀 | 复用 **Worker heartbeat**；空闲超时默认 300s |
| 预热池 3～10 | 默认 **min-workers=0**，无大预热池 |
| `/api/v1/compute/instances` + `{code:200}` | `/api/v1/admin/compute/*` + `{success,message,data}` |
| 分配 &lt; 500ms | 以「Worker ONLINE / 任务推进」为 SLA，分钟级可接受 |

---

*本文档为 XJICloud 仓库内产品需求；实现时以最小 diff 扩展 `backend/` 与 `admin/`，并同步更新 AGENT_CONTEXT.md / Deploy.md 中「自动扩缩不内置」的表述。*
