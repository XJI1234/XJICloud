# eci-manager API 文档

> **服务：** `eci-manager`（默认 `http://127.0.0.1:8081`）  
> **API 前缀：** `/api/v1/eci`  
> **对应 PRD：** [ECI任务驱动容器编排服务产品需求文档(PRD).md](../../ECI任务驱动容器编排服务产品需求文档(PRD).md) v2.0.3  
> **更新日期：** 2026-07-30  

本文面向三类读者：

| 读者 | 建议阅读 |
|------|----------|
| 非技术 / 产品 | [§1 业务导读](#1-业务导读非技术) |
| 对接开发 | [§2 通用约定](#2-通用约定) → [§3 任务 API](#3-任务-api主路径) → [§4 实例 API](#4-实例-api运维调试) |
| 测试 / 联调 | [§6 联调速查](#6-联调速查测试) + [测试说明.md](./测试说明.md) |

### 状态图例

| 标记 | 含义 |
|------|------|
| **已实现** | 当前版本可调用、可验收 |

**费用提醒：** 真云模式（`eci.mock=false`）会产生阿里云账单。任务在云侧 Succeeded/Failed 或超时后会**自动删除**实例；仍请到控制台确认无残留。手动实例须人工 `DELETE`。

---

## 1. 业务导读（非技术）

### 1.1 这个服务做什么

调用方提交一个**任务**后，服务会在阿里云上创建一个**弹性容器实例（ECI）**来跑指定镜像（以及可选的启动命令）。容器跑完 → 服务识别结果 → 自动删掉云资源并结束任务。

### 1.2 任务 vs 手动实例

| | 任务（推荐业务路径） | 手动实例（运维调试） |
|--|---------------------|----------------------|
| 入口 | 「提交任务」 | 「直接创建容器」 |
| 关系 | 一个任务对应一个容器 | 没有关联任务 |
| 谁负责收尾 | 服务自动删（终态 / 超时 / 取消） | 始终需人手动删除 |
| 镜像 | **必须**指定 | 可不传，用默认镜像 |

### 1.3 当前能验收什么

**已实现（可以测）：**

1. 提交任务 → 自动创建关联容器  
2. 查询任务 / 实例状态（创建中 → 运行中 → 成功/失败）  
3. 取消任务 → 删除关联容器  
4. 云侧 Succeeded/Failed 或 `maxRuntimeSec` 超时后自动删并回写任务  
5. Mock 下时间触发终态（`mock-auto-complete-sec`）全流程不调云  
6. 手动创建 / 删除 / 刷新实例  

### 1.4 推荐业务心智模型

```text
提交任务 → 创建中 → 运行中 →（脚本结束）→ 成功/失败 → 云资源已清理
                ↘ 任意时刻可取消 → 已取消 → 云资源已清理
                ↘ 超时 → 失败(TIMEOUT) → 云资源已清理
```

---

## 2. 通用约定

### 2.1 Base URL 与鉴权

| 项 | 值 |
|----|-----|
| Base | `http://127.0.0.1:8081`（部署时替换主机与端口） |
| 前缀 | `/api/v1/eci` |
| 鉴权头 | `X-Api-Key: <密钥>` |
| 密钥来源 | 配置 `eci.api-key` 或环境变量 `ECI_API_KEY`（默认开发值见配置） |
| 无需鉴权 | `GET /actuator/health` |

缺 Key、错 Key → **HTTP 401**，响应体为统一失败壳；**不会**创建云资源。

### 2.2 统一响应壳

成功：

```json
{
  "success": true,
  "message": null,
  "data": { }
}
```

失败：

```json
{
  "success": false,
  "message": "错误说明",
  "data": null
}
```

列表接口的 `data` 为数组；单资源为对象。

### 2.3 常见 HTTP 状态码

| 码 | 场景 |
|----|------|
| 200 | 业务成功（含幂等取消/删除成功） |
| 400 | 参数非法（如任务缺 `image`、非法 `restartPolicy`、`maxRuntimeSec≤0`） |
| 401 | 缺少或错误的 `X-Api-Key` |
| 404 | 任务/实例不存在 |
| 409 | 冲突（如创建中禁止删实例；任务已终态无法取消） |
| 500 | 未预期服务器错误；真云创建时镜像缓存不存在或未就绪（`ImageSnapshot.NotFound` / `ImageSnapshot.IncorrectStatus`）会经此路径回写 `failureReason` |

### 2.4 任务状态（`TaskStatus`）

| 状态 | 含义 | 交付 |
|------|------|------|
| `PENDING` | 已受理，尚未进入创建 | **已实现**（通常极短） |
| `STARTING` | 正在创建实例或等待 Running | **已实现** |
| `RUNNING` | 实例已 Running，任务执行中 | **已实现** |
| `SUCCEEDED` | 云侧成功结束且自动清理完成 | **已实现** |
| `FAILED` | 创建失败、进程失败、超时等 | **已实现**（含 `failureReason=TIMEOUT`） |
| `CANCELLED` | 调用方取消 | **已实现** |

流转：

```text
PENDING → STARTING → RUNNING → SUCCEEDED | FAILED
STARTING → SUCCEEDED | FAILED
PENDING / STARTING / RUNNING → CANCELLED
非终态 → FAILED（创建失败、超时、云异常）
```

### 2.5 实例状态（`InstanceStatus`）

| 状态 | 含义 |
|------|------|
| `CREATING` | 已受理创建，调云或等待 Running |
| `RUNNING` | 云侧已 Running（或 Mock 成功） |
| `STOPPING` | 已受理删除 |
| `TERMINATED` | 云侧已删除，终态 |
| `FAILED` | 创建或删除失败 |

```text
CREATING → RUNNING | FAILED
RUNNING → STOPPING → TERMINATED | FAILED
```

### 2.6 默认配置（未传字段时）

| 配置键 | 默认值（`application.yml`） | 说明 |
|--------|---------------------------|------|
| `eci.default-image` | `nginx:alpine` | **仅手动实例**缺 `image` 时使用；任务仍必填 `image`。cloud profile 常改为 VPC 镜像 |
| `eci.default-cpu` | `1.0` | |
| `eci.default-memory` | `2.0` | |
| `eci.default-restart-policy` | `Never` | |
| `eci.default-max-runtime-sec` | `14400`（4 小时） | 未传 `maxRuntimeSec` 时写入任务；超时强制删 |
| `eci.task-poll-interval-sec` | `10` | 运行中任务轮询云终态周期 |
| `eci.mock-auto-complete-sec` | `3` | Mock：创建后 N 秒自动迁终态；`<=0` 关闭 |
| `eci.mock-auto-complete-status` | `Succeeded` | Mock 自动终态：`Succeeded` / `Failed` |
| `eci.create-timeout-sec` | `300` | 创建等待上限 |
| `eci.delete-timeout-sec` | `300` | 删除等待上限 |

`restartPolicy` 合法值：`Never` | `OnFailure` | `Always`。

---

## 3. 任务 API（主路径）

前缀：`/api/v1/eci/tasks`  
均需 `X-Api-Key`。

### 3.1 提交任务 — **已实现**

`POST /api/v1/eci/tasks`

受理后落库实际规格，创建关联实例，立即返回（异步创建云资源）。成功时任务多为 `STARTING`，并带非空 `instanceId`。

#### 请求体

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `image` | string | **是** | 完整镜像地址 |
| `name` | string | 否 | 未传则 `task-` + UUID 前 8 位 |
| `command` | string[] | 否 | 覆盖镜像 ENTRYPOINT |
| `args` | string[] | 否 | 覆盖镜像 CMD |
| `cpu` | number | 否 | 默认 `eci.default-cpu` |
| `memory` | number | 否 | 默认 `eci.default-memory` |
| `instanceType` | string | 否 | ECS 规格，如 GPU：`ecs.gn7i-c32g1.8xlarge` |
| `gpu` | integer | 否 | 容器 GPU 个数；GPU 规格时通常传 `1` |
| `imageSnapshotId` | string | 否 | 镜像缓存 ID（如 `imc-xxx`）；透传阿里云 `ImageSnapshotId`，加速镜像拉取 |
| `restartPolicy` | string | 否 | `Never` / `OnFailure` / `Always`；默认 `Never` |
| `maxRuntimeSec` | integer | 否 | 正整数；默认 `14400`。超时强制删 + `FAILED`/`TIMEOUT` |
| `env` | object | 否 | 透传到容器的环境变量 |

#### 请求示例

```json
{
  "name": "cloud-task-1",
  "image": "registry-vpc.cn-hangzhou.aliyuncs.com/eci_open/nginx:1.14.2",
  "imageSnapshotId": "imc-2zebxkiifuyzzlhl",
  "restartPolicy": "Never",
  "maxRuntimeSec": 600,
  "cpu": 1.0,
  "memory": 2.0,
  "command": ["nginx"],
  "args": ["-g", "daemon off;"],
  "env": {
    "DEMO": "1"
  }
}
```

#### 响应 `data`（`TaskResponse`）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID | 任务 ID |
| `name` | string | 名称 |
| `status` | enum | 见 §2.4 |
| `instanceId` | UUID | 关联实例；创建成功后非空 |
| `image` | string | 实际镜像 |
| `command` / `args` | array | 启动命令 |
| `cpu` / `memory` | number | 实际规格 |
| `instanceType` | string \| null | |
| `gpu` | integer \| null | |
| `imageSnapshotId` | string \| null | 镜像缓存 ID；未使用时为 `null` |
| `restartPolicy` | string | |
| `maxRuntimeSec` | integer | |
| `env` | object | |
| `failureReason` | string \| null | 失败原因 |
| `startedAt` / `endedAt` | instant \| null | |
| `createdAt` / `updatedAt` | instant | |

#### 错误

| 条件 | HTTP | message 示例 |
|------|------|----------------|
| 缺 `image` | 400 | `image 为必填项` |
| 非法 `restartPolicy` | 400 | `restartPolicy 须为 Never、OnFailure 或 Always` |
| `maxRuntimeSec≤0` | 400 | `maxRuntimeSec 须为正整数` |
| 无/错 Key | 401 | （鉴权失败文案） |

---

### 3.2 任务列表 — **已实现**

`GET /api/v1/eci/tasks`  
`GET /api/v1/eci/tasks?status=RUNNING`

| 参数 | 说明 |
|------|------|
| `status` | 可选；按 `TaskStatus` 过滤 |

`data`：`TaskResponse[]`，按创建时间倒序。

---

### 3.3 任务详情 — **已实现**

`GET /api/v1/eci/tasks/{id}`

| 条件 | HTTP |
|------|------|
| 不存在 | 404 |

---

### 3.4 取消任务 — **已实现**

`POST /api/v1/eci/tasks/{id}/cancel`

- 非终态 → 标为 `CANCELLED`，并删除关联实例（若有）  
- 已是 `CANCELLED` → **幂等**成功，仍返回当前任务  
- 已是 `SUCCEEDED` / `FAILED` → **409**，不可取消  

---

### 3.5 后台任务行为（无新 HTTP）

以下**不新增路径**，由 `TaskLifecycleWatcher` 按 `eci.task-poll-interval-sec` 驱动：

| 行为 | 说明 | 状态 |
|------|------|------|
| 云侧 `Succeeded` → 删实例 → 任务 `SUCCEEDED` | 仅实例已 `TERMINATED` 后落成功 | **已实现** |
| 云侧 `Failed` → 删实例 → 任务 `FAILED` | `Never`；`OnFailure`/`Always` 见策略 | **已实现** |
| 超过 `maxRuntimeSec` 强制删 | `failureReason=TIMEOUT` | **已实现** |
| Mock 时间触发终态 | `mock-auto-complete-sec` / `status` | **已实现** |

调用方对接：轮询 `GET /tasks/{id}` 的 `status` 直至终态。

---

## 4. 实例 API（运维 / 调试）

前缀：`/api/v1/eci/instances`  
均需 `X-Api-Key`。

手动创建的实例 `taskId` 为 `null`；由任务创建的实例 `taskId` 等于任务 ID。

### 4.1 创建实例 — **已实现**

`POST /api/v1/eci/instances`

异步创建，立即返回，`status` 多为 `CREATING`。勿期望 POST 直接得到 `RUNNING`。

#### 请求体（均可选）

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 未传则 `demo-` + UUID 前 8 位 |
| `image` | string | 未传用 `eci.default-image` |
| `cpu` / `memory` | number | 默认见 §2.6 |
| `env` | object | 透传环境变量 |
| `instanceType` | string | 可选 ECS 规格 |
| `gpu` | integer | 可选 GPU 个数 |
| `imageSnapshotId` | string | 可选镜像缓存 ID（如 `imc-xxx`） |
| `restartPolicy` | string | 默认 `Never` |
| `command` / `args` | string[] | 覆盖 ENTRYPOINT / CMD |

> 创建时可传 `restartPolicy` / `command` / `args` / `instanceType` / `gpu` / `imageSnapshotId`，会落库并调云；**当前 `InstanceResponse` 响应体不含这些字段**（以任务详情或后续版本为准）。

#### 请求示例（真云推荐 VPC 镜像）

```json
{
  "name": "cloud-manual-1",
  "image": "registry-vpc.cn-hangzhou.aliyuncs.com/eci_open/nginx:1.14.2",
  "cpu": 1.0,
  "memory": 2.0,
  "restartPolicy": "Never"
}
```

GPU 示例（贵、看库存；测完必须删）：

```json
{
  "name": "gn7i-a10-demo",
  "image": "registry-vpc.cn-hangzhou.aliyuncs.com/eci_open/nginx:1.14.2",
  "cpu": 32,
  "memory": 188,
  "instanceType": "ecs.gn7i-c32g1.8xlarge",
  "gpu": 1,
  "restartPolicy": "Never"
}
```

#### 响应 `data`（`InstanceResponse`）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID | 本地实例 ID |
| `taskId` | UUID \| null | 手动创建为空；任务创建则有值 |
| `name` | string | |
| `image` | string | |
| `cpu` / `memory` | number | |
| `status` | enum | 见 §2.5 |
| `cloudInstanceId` | string \| null | 云侧 ID；Mock 形如 `mock-eci-...` |
| `cloudStatus` | string \| null | 最近一次云侧状态字符串 |
| `env` | object | |
| `failureReason` | string \| null | |
| `createdAt` / `updatedAt` | instant | |

---

### 4.2 实例列表 — **已实现**

`GET /api/v1/eci/instances`  
`GET /api/v1/eci/instances?status=RUNNING`

`data`：`InstanceResponse[]`。

---

### 4.3 实例详情 — **已实现**

`GET /api/v1/eci/instances/{id}`

不存在 → **404**。

---

### 4.4 刷新状态 — **已实现**

`POST /api/v1/eci/instances/{id}/refresh`

主动 Describe 云侧并回写本地。云侧已不存在且本地为 `CREATING` / `RUNNING` / `STOPPING` / `FAILED` 时，可落到 `TERMINATED`。

---

### 4.5 删除实例 — **已实现**

`DELETE /api/v1/eci/instances/{id}`

| 条件 | 行为 |
|------|------|
| `CREATING` | **409**，提示稍后再删 |
| `TERMINATED` | 幂等成功 |
| 其它可删状态 | 进入 `STOPPING`，异步删云资源 |

业务任务收尾优先用 **任务 cancel**，而不是只删实例（只删实例时，任务在 T1 可能仍显示 `RUNNING`）。

---

## 5. 健康检查 — **已实现**

`GET /actuator/health`

- **无需** `X-Api-Key`  
- 期望：HTTP 200，体含 `"status":"UP"`（或同类健康状态）

---

## 6. 联调速查（测试）

变量约定：

```bash
export BASE=http://127.0.0.1:8081
export ECI_API_KEY=dev-eci-api-key-change-me   # 须与服务端一致
# 真云推荐：
export CLOUD_IMAGE='registry-vpc.cn-hangzhou.aliyuncs.com/eci_open/nginx:1.14.2'
```

### 6.1 健康检查

```bash
curl -s "$BASE/actuator/health"
```

### 6.2 提交任务（主路径）

```bash
curl -s -X POST "$BASE/api/v1/eci/tasks" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $ECI_API_KEY" \
  -d "{\"name\":\"demo-task\",\"image\":\"$CLOUD_IMAGE\",\"restartPolicy\":\"Never\",\"maxRuntimeSec\":600}"
```

记下返回的 `data.id`、`data.instanceId`，轮询：

```bash
curl -s "$BASE/api/v1/eci/tasks/<TASK_ID>" -H "X-Api-Key: $ECI_API_KEY"
curl -s "$BASE/api/v1/eci/instances/<INSTANCE_ID>" -H "X-Api-Key: $ECI_API_KEY"
```

期望：任务 → `RUNNING`；实例 `taskId` 等于任务 ID；真云时 `cloudInstanceId` 非 `mock-eci-` 前缀。

### 6.3 取消任务（当前推荐收尾）

```bash
curl -s -X POST "$BASE/api/v1/eci/tasks/<TASK_ID>/cancel" \
  -H "X-Api-Key: $ECI_API_KEY"
```

期望：`status=CANCELLED`；关联实例最终 `TERMINATED`。控制台确认 ECI 已删除。

### 6.4 手动实例（调试）

```bash
curl -s -X POST "$BASE/api/v1/eci/instances" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $ECI_API_KEY" \
  -d "{\"name\":\"manual-1\",\"image\":\"$CLOUD_IMAGE\",\"restartPolicy\":\"Never\"}"

curl -s -X DELETE "$BASE/api/v1/eci/instances/<INSTANCE_ID>" \
  -H "X-Api-Key: $ECI_API_KEY"
```

逐步命令、轮询与真云准备见 **[测试说明.md](./测试说明.md)**（Mock §3–6；真云 §8）。

---

## 7. 能力对照表（验收用）

| PRD / 能力 | 接口或行为 | 状态 |
|------------|------------|------|
| Api-Key 鉴权 | 请求头 `X-Api-Key` | **已实现** |
| 健康检查 | `GET /actuator/health` | **已实现** |
| 手动实例 CRUD / refresh | `/api/v1/eci/instances` | **已实现** |
| `restartPolicy` / `command` / `args` 透传创建 | 创建请求体 | **已实现** |
| `imageSnapshotId` 镜像缓存加速 | 创建请求体；透传 `CreateContainerGroup.ImageSnapshotId` | **已实现** |
| 任务提交（`image` 必填） | `POST /tasks` | **已实现** |
| 任务列表 / 详情 | `GET /tasks`、`GET /tasks/{id}` | **已实现** |
| 任务取消（幂等） | `POST /tasks/{id}/cancel` | **已实现** |
| 一任务一实例绑定 | `taskId` ↔ `instanceId` | **已实现** |
| 创建失败 → 任务 `FAILED` | 回写 | **已实现** |
| 实例 Running → 任务 `RUNNING` | 回写 | **已实现** |
| 云侧 Succeeded/Failed 后自动删 + 任务终态 | 后台轮询 | **已实现** |
| `maxRuntimeSec` 超时强制删 | 后台 | **已实现** |
| Mock 时间触发终态自动删 | `mock-auto-complete-sec` | **已实现** |

---

## 8. 相关文档

| 文档 | 用途 |
|------|------|
| [设计与使用说明.md](./设计与使用说明.md) | 架构、配置、启动方式 |
| [测试说明.md](./测试说明.md) | 可复制粘贴的逐步测试 |
| [../README.md](../README.md) | 工程入口与进度摘要 |
| [PRD](../../ECI任务驱动容器编排服务产品需求文档(PRD).md) | 产品需求全文 |
| [实现计划](../../docs/superpowers/plans/2026-07-27-eci-task-orchestration.md) | 分阶段任务 |
