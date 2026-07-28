# ECI 任务驱动容器编排服务 — 产品需求文档 (PRD)

| **文档版本** | **创建日期** | **状态** | **适用对象** |
| ------------ | ------------ | -------- | ------------ |
| **v2.0.2** | 2026-07-27 | 已评审 / 实现进行中 | 后端开发、容器运维、测试、评审人 |
| **产品定位** | 独立任务驱动的 ECI 容器编排服务 | 本服务在现有实例管理能力上增加任务提交、自动创建、终态检测、超时保护与自动删除 | |
| **前身文档** | `ECI容器管理后端Demo产品需求文档(PRD).md` v1.1 | 本文件已取代前身文档；Demo 阶段已完成的创建、查询、删除能力继续保留 | |

> **本次交付物是 v2.0 PRD（已评审批准，实现进行中）。** Demo 基座（D0–D3）已经完成。实现以 [`docs/superpowers/plans/2026-07-27-eci-task-orchestration.md`](docs/superpowers/plans/2026-07-27-eci-task-orchestration.md) 为准；不修改主仓 `backend/`。

### v1.1 → v2.0 变更概览

| 变化 | 说明 |
|------|------|
| 定位升级 | 本服务从「独立 Demo」升级为「任务驱动的独立编排服务」，工程目录仍为 `eci-manager/`。 |
| 新增任务层 | 调用方通过 `POST /tasks` 提交单个任务；服务为每个任务创建一个实例，并支持查询与取消。 |
| 自动生命周期 | 容器默认使用 `RestartPolicy=Never`；服务轮询到云侧 `Succeeded` 或 `Failed` 后自动删除实例。 |
| 超时保护 | 任务可指定 `maxRuntimeSec`；未指定时服务使用全局默认值，超时后强制删除实例并标记 `FAILED`/`TIMEOUT`。 |
| 启动命令 | `command` 与 `args` 为可选字段；未传入时服务使用镜像自身的 `ENTRYPOINT/CMD`。 |
| 手动入口 | 服务继续保留 `/instances` 手动管理接口，并与任务接口并存。 |
| **明确取消** | 一期不实现业务完成回调、镜像注册表、规格套餐、批量提交，也不实现常驻 Worker 领任务。 |

---

## 0. 本次评审建议决策（v2.0）

> 本节列出建议锁定的决策。若评审人无异议，团队将这些决策视为锁定，并据此编写实现计划；若有异议，请直接修改本表或回写到第 9 节。

| # | 议题 | 建议结论 | 影响摘要 |
|---|------|----------|----------|
| 1 | 产品定位 | 本服务定位为正式的独立容器编排服务。 | 现有 Demo 能力作为基座，工程位于主仓 `eci-manager/`，且不修改 `backend/`。 |
| 2 | 鉴权 | 接口使用 Api-Key，请求头为 `X-Api-Key`。 | 所有 `/api/v1/eci/**` 请求都必须通过校验；密钥只放在配置或环境变量中，不得进入 Git。 |
| 3 | 任务资源模型 | 一个任务对应一个 ECI 实例和一个主容器。 | 独立任务互不共享实例；一期不实现一实例多任务或多业务容器。 |
| 4 | 关闭语义 | 关闭实例等于删除云资源，而不是停止后保留。 | 服务调用云删除 API，并将本地状态更新为 `TERMINATED`，以控制费用并简化状态。 |
| 5 | 任务完成检测 | 服务通过轮询云状态判断任务是否结束。 | 容器组进入 `Succeeded` 或 `Failed` 后，服务自动删除实例；一期不实现回调协议。 |
| 6 | 重启策略 | 请求可配置 `Never`、`OnFailure`、`Always`，默认值为 `Never`。 | 一次性任务结束后默认不重启；所有重启策略都受最大运行时长约束。 |
| 7 | 镜像与规格 | 每个任务直接传入镜像地址和所需硬件规格。 | 一期不实现镜像注册表和规格套餐；历史任务保存实际使用值。 |
| 8 | 启动命令 | `command` 与 `args` 为可选字段。 | 未传入时，服务使用镜像自身的 `ENTRYPOINT/CMD`；传入时，服务覆盖镜像默认入口。 |
| 9 | 手动与自动 | 手动接口与任务接口并存。 | 服务保留 `/instances` 手动管理能力，并新增 `/tasks` 自动管理能力。 |
| 10 | 生产选型 | 生产环境最终使用 ECI、CCI，还是双 Provider，目前仍待定。 | 该议题留到后续集成评审，不阻塞本期实现。 |

### 运行与构建约束（建议沿用 v1.1）

| 项 | 要求 |
|----|------|
| **运行环境** | 开发验证与演示都以 Linux 为准；文档中的路径、脚本、systemd 与 curl 示例均按 Linux 编写。 |
| **JDK** | 运行环境必须使用 JDK 17（`java -version` 显示 17.x），并与主仓 Spring Boot 3 保持一致。 |
| **构建** | 构建工具为 Maven 3.9+（`mvn -v`）；团队可使用项目自带 Maven 或系统 Maven，并通过 `mvn spring-boot:run` 或 `mvn -DskipTests package` 构建。 |
| **说明** | 开发者可以在 Windows 上编写代码，但验收与联调文档以 Linux 命令为准。 |

---

## 1. 背景与目标

### 1.1 背景

团队已经验证了用 Spring Boot 调用阿里云弹性容器实例（ECI）OpenAPI 来创建、查询和删除容器的能力。现在需要在此基础上提供可实际使用的任务驱动容器编排服务：调用方提交任务后，本服务按任务指定的镜像、启动命令与硬件规格创建容器；任务进程结束后，本服务自动识别结果并删除云资源。

当前 XJICloud 主仓已经具备训练任务队列，以及 GPU Worker 主动拉活协议（`/api/v1/worker/*`）。主仓部署文档中也提到了 CCI 相关方案，但自动伸缩尚未实现。本 PRD 只约束 `eci-manager` 这条独立服务线。

现有 `eci-manager` 已经完成独立 Demo 所需的创建、查询、删除、Mock 与 Api-Key 等基础能力。v2.0 将本服务升格为独立编排服务，但仍保持独立进程，并且不直接改动主后端、训练队列与鉴权体系。

### 1.2 核心目标

| 目标 | 说明 |
|------|------|
| **任务驱动创建** | 调用方提交一个独立任务后，后端据此创建一个 ECI 实例和一个主容器。 |
| **镜像与脚本可控** | 任务直接指定托管镜像地址；调用方可用 `command` 与 `args` 覆盖镜像默认入口。 |
| **硬件可控** | 任务直接指定 CPU、内存、实例规格和 GPU 数量；未传入的字段使用配置默认值。 |
| **任务完成自动删除** | 容器使用可配置的重启策略（默认 `Never`）；服务轮询到云侧终态后自动删除实例。 |
| **运行超时保护** | 每个任务可指定 `maxRuntimeSec`；未传入时服务使用全局默认值，超时后强制删除实例。 |
| **保留手动管理** | 原有实例创建、查询、删除 API 继续提供，供运维和调试使用。 |
| **可查询** | 调用方可以列出本地记录，并在需要时查询云上状态，以便联调。 |
| **可配置** | AccessKey、Region、VPC、默认规格、重启策略、轮询周期与超时等均通过配置管理，不写死在代码中。 |
| **可本地演练** | 服务提供 `mock` 模式；在没有真实云凭证时，调用方也能跑通 API 与状态机。 |
| **为后续集成留口** | API 形态与状态字段尽量与主仓约定对齐，便于将来迁入主仓或被其他服务通过 HTTP 调用。 |

### 1.3 非目标（v2.0 一期明确不做）

- 本服务不实现常驻 Worker 的注册、心跳与领任务协议；本期容器在启动时就确定执行内容。
- 本服务不按队列深度自动扩缩；调用方每提交一个任务，服务就创建一个对应实例。
- 本服务不实现批量任务提交；一期 `POST /tasks` 每次只接收一个任务。
- 本服务不实现镜像注册表和规格套餐；任务直接传入镜像地址与硬件规格。
- 本服务不实现业务完成回调；任务是否完成只通过轮询 ECI 容器组状态判断。
- 本服务不实现一实例承载多个独立任务，也不实现多业务容器编排。
- 本服务不实现用户前端、动态域名网关与 WebSocket 透传。
- 本服务默认不并入生产 ACK 或 CCI 拓扑；生产环境最终是否继续使用 ECI，仍待后续评审。

### 1.4 成功标准（验收口径）

验收环境为 Linux，并安装 JDK 17 与 Maven。真实模式还需要有效的阿里云凭证与可达网络。

1. 服务可以在 Linux 上通过 `mvn spring-boot:run` 或 `java -jar` 启动。
2. 调用方携带正确的 `X-Api-Key` 提交单个任务时，可以指定镜像、`command`、`args`、CPU、内存、实例规格、GPU、重启策略与最大运行时长。
3. 后端为该任务创建一台 ECI 实例；调用方可以查询任务与实例，并可以通过本地 ID 将二者关联。
4. 当 `restartPolicy=Never` 时，任务进程正常结束或失败退出后不会重启；后端轮询到 `Succeeded` 或 `Failed` 后自动删除实例。
5. 当任务运行时间超过 `maxRuntimeSec` 时，后端强制删除实例，并将任务标记为 `FAILED`，失败原因为 `TIMEOUT`。
6. 原有 `/instances` 手动创建、查询与删除能力继续可用。
7. 当请求缺少 Api-Key 或 Api-Key 错误时，服务拒绝访问（401/403），并且不创建云资源。
8. 当 `mock=true` 时，上述任务流程不调用云 API，也能够模拟创建、运行、终态和自动删除。

---

## 2. 产品形态与边界

### 2.1 形态

| 项 | 约定 |
|----|------|
| **形态** | 本服务是独立的 Spring Boot 3.3.x + Java 17 Maven 工程。 |
| **工程目录** | 工程位于主仓子目录 `eci-manager/`；实现计划可以微调目录名，但必须保留在主仓内。 |
| **与主后端关系** | 本服务使用独立进程和独立 `pom.xml`，并且不修改 `backend/` 业务代码。 |
| **端口** | 服务监听 `8081`，以避免与 XJICloud 主后端的 `8080` 冲突。 |
| **数据库** | 一期沿用 H2 文件库；任务记录与实例记录都必须持久化。 |
| **调用方** | 主要调用方是 XJICloud 后端或其他内部服务；开发联调可以使用 curl 或 Postman；一期不提供正式前端。 |
| **OS** | 文档、脚本与验收都以 Linux 为准。 |

### 2.2 与 XJICloud 主系统的关系

```
【v2.0 一期】
  调用方 ──HTTP + X-Api-Key──► eci-manager (:8081) ──OpenAPI──► 阿里云 ECI
             POST /tasks         │   │
                                 │   ├─ 任务记录
                                 │   └─ 实例记录
                                 └─ 轮询终态并自动删除

  运维人员 ──HTTP + X-Api-Key──► /instances（手动管理，继续保留）

【后续可选集成；生产选型待定】
  XJICloud Backend ──HTTP──► eci-manager
       或将代码迁入主仓 / 增加其他云容器 Provider
```

本服务负责独立任务记录和云实例生命周期。任务在容器启动时通过镜像默认入口或 `command` / `args` 确定执行内容。一期不实现容器注册、领任务或业务完成回调。

### 2.3 为何一期继续使用 ECI

| 点 | 说明 |
|----|------|
| 已有基础 | `eci-manager` 已经跑通 ECI 创建、查询、删除及 Mock 链路。 |
| 生产选型 | 生产环境最终是否改用 CCI 或其他方案，目前仍待定，因此不阻塞本期在 ECI 上先落地。 |
| 一期价值 | 团队可以在现有 ECI Provider 上先验证任务自动生命周期；后续如需切换云产品，可以扩展 Provider 抽象。 |

---

## 3. 功能需求

### 3.1 任务与实例模型

- 一个自动任务对应一个本地实例记录、一个 ECI 容器组和一个主容器。
- `task.instanceId` 关联本地实例 UUID；实例删除后，任务历史仍然保留。
- 手动创建的实例不要求关联任务。
- 任务需要保存实际使用的镜像地址、命令、参数和硬件规格，以避免未来默认配置变化影响历史追溯。

### 3.2 任务状态机

| 状态 | 含义 |
|------|------|
| `PENDING` | 服务已经受理任务，但尚未创建实例。 |
| `STARTING` | 服务正在创建实例，或正在等待实例进入 Running。 |
| `RUNNING` | 实例已经 Running，任务进程正在执行。 |
| `SUCCEEDED` | 云侧任务已经成功结束，并且自动清理流程已经完成。 |
| `FAILED` | 创建失败、任务进程失败、运行超时，或其他不可恢复错误已经发生。 |
| `CANCELLED` | 调用方主动取消任务，并且关联实例已经完成删除，或确认不存在。 |

```
PENDING → STARTING → RUNNING → SUCCEEDED | FAILED
STARTING → SUCCEEDED | FAILED（短任务可能在首次观察到 Running 前已经结束）
PENDING / STARTING / RUNNING → CANCELLED
任意非终态 → FAILED（创建失败、运行超时或云 API 异常）
```

### 3.3 实例状态机（本地权威）

| 状态 | 含义 |
|------|------|
| `CREATING` | 服务已经受理创建请求，正在调用云 API，或正在等待 Running。 |
| `RUNNING` | 云侧实例已经 Running；在 mock 模式下，服务可以直接进入该状态。 |
| `STOPPING` | 服务已经受理删除请求。 |
| `TERMINATED` | 云侧实例已经删除，这是终态。 |
| `FAILED` | 创建或删除失败；服务需要记录 `failureReason`。 |

```
CREATING → RUNNING | FAILED
RUNNING  → STOPPING → TERMINATED | FAILED
任意非终态 → FAILED（云 API 异常）
```

当任务驱动的实例在云侧进入 `Succeeded` 或 `Failed` 后，本地状态进入 `STOPPING`，并且服务执行删除。手动创建的实例仍然按现有 API 控制。

### 3.4 提交与执行独立任务

- 调用方通过 `POST /tasks` 每次提交一个任务。
- `image` 为必填字段，其值为调用方自编译并上传后的托管镜像完整地址。
- `command` 与 `args` 为可选字段：未传入时，服务沿用镜像自身的 `ENTRYPOINT/CMD`；传入时，服务覆盖对应入口。
- CPU、内存、实例规格与 GPU 数量由任务直接指定；未传入的字段可以使用配置默认值。
- `restartPolicy` 为可选字段，取值可以是 `Never`、`OnFailure` 或 `Always`，默认值为 `Never`。
- `maxRuntimeSec` 为可选字段；未传入时，服务使用全局默认值。所有重启策略都受该上限约束。
- 后端先落库任务，再创建关联实例，再轮询至 Running，再持续轮询云状态；在终态或超时后，后端删除实例并回写任务状态。

### 3.5 创建容器实例（手动）

- 默认镜像来自配置项 `default-image`；请求体可以覆盖 `image`。
- Region、镜像、CPU/Memory、VPC、交换机与安全组为必填项；调用方可以通过 yml 默认值或请求体覆盖这些字段。
- 环境变量、实例名称、实例规格、GPU、`command`、`args` 与 `restartPolicy` 为可选项。
- 服务生成本地 UUID，再调用 ECI 创建接口，再保存 `cloudInstanceId`，再轮询至 Running；若超时，则标记失败。
- 手动创建用于运维调试和手动生命周期管理。该路径不创建任务记录，也不按业务终态自动删除。

### 3.6 任务完成检测与自动删除

1. 当轮询发现云侧容器组为 `Succeeded` 时，服务删除实例；清理成功后，服务将任务置为 `SUCCEEDED`。
2. 当轮询发现云侧容器组为 `Failed` 时，服务记录云侧失败信息并删除实例，然后将任务置为 `FAILED`。
3. 当运行时间超过 `maxRuntimeSec` 时，服务强制删除实例，并将任务置为 `FAILED`，同时将 `failureReason` 记为 `TIMEOUT`。
4. 当 `restartPolicy=OnFailure` 时，失败进程可能被反复重启；任务最终仍由成功终态或超时结束。
5. 当 `restartPolicy=Always` 时，实例通常不会自然进入终态；任务只能由超时、任务取消或人工删除结束。
6. 当删除失败时，服务保留实例失败状态和原因，供运维重试清理；服务不得把云资源清理失败静默当成成功。

### 3.7 删除容器实例（关闭等于删除）

- 调用方可以按本地 `id` 删除对应 ECI；服务调用云 Delete 类 API，而不是 Stop 保留。
- 删除流程为：本地进入 `STOPPING`，删除成功后进入 `TERMINATED`；若失败，则进入 `FAILED` 并记录 `failureReason`。
- 删除必须幂等：若实例已经是 `TERMINATED`，再次 DELETE 仍应返回成功（或明确返回「已终止」），并且不得抛出 500。

### 3.8 查询

- 调用方可以查询任务列表（可按 `status` 过滤）、任务详情，并取消任务。
- 调用方可以查询实例列表（可按 `status` 过滤）、实例详情，并可选调用 `refresh` 回写云状态；在 mock 模式下，服务返回本地状态。
- 任务详情应包含 `instanceId`、实际使用规格、云状态、失败原因，以及创建、开始与结束时间。

### 3.9 环境变量与任务参数

- 服务支持透传任意 `env` Map。
- 任务业务内容主要由镜像默认入口或 `command` / `args` 决定。
- 一期不强制注入 `TASK_ID` 与 `CALLBACK_URL`，也不实现业务完成回调。

### 3.10 Mock 模式

| 配置 | 行为 |
|------|------|
| `eci.mock=true` | 服务不调用云 API；实例创建后进入 `RUNNING`；任务可以模拟进入 `Succeeded` 或 `Failed` 后自动删除；`cloudInstanceId` 形如 `mock-eci-xxx`。 |
| `eci.mock=false` | 服务调用真实 ECI OpenAPI。 |

建议：`dev` profile 使用 `mock=true`；`cloud` profile 使用 `mock=false`。

### 3.11 鉴权（Api-Key）

- 配置项为 `eci.api-key`，也可以由环境变量覆盖。
- 客户端请求头为 `X-Api-Key: <密钥>`。
- 当请求未携带密钥或密钥错误时，服务拒绝访问，并且不执行创建或删除。
- `/actuator/health` 可以保持公开，仅用于存活探测。

---

## 4. API 规范

### 4.1 约定

- API 前缀为 `/api/v1/eci`。
- 响应格式为 `{ "success": boolean, "message": string|null, "data": ... }`。
- 鉴权方式已经确定为 `X-Api-Key`（见第 3.11 节）。

### 4.2 提交任务

`POST /api/v1/eci/tasks`

```json
{
  "name": "3dgs-job-1",
  "image": "registry-vpc.cn-hangzhou.aliyuncs.com/your-ns/3dgs:v1",
  "command": ["python", "train.py"],
  "args": ["--mode", "3dgs", "--input", "/data/a"],
  "cpu": 8,
  "memory": 32,
  "instanceType": "ecs.gn7i-c32g1.8xlarge",
  "gpu": 1,
  "restartPolicy": "Never",
  "maxRuntimeSec": 14400,
  "env": {
    "EXTRA": "optional"
  }
}
```

字段规则：

| 字段 | 要求 |
|------|------|
| `image` | 必填；值为完整托管镜像地址。 |
| `command` / `args` | 可选；未传入时，服务使用镜像自身的 `ENTRYPOINT/CMD`；传入时，服务覆盖镜像默认入口。 |
| `cpu` / `memory` / `instanceType` / `gpu` | 可选；未传入的字段使用配置默认值。 |
| `imageSnapshotId` | 可选；镜像缓存 ID（如 `imc-xxx`），透传阿里云 `ImageSnapshotId` 以加速镜像拉取；须为 Ready 状态。 |
| `restartPolicy` | 可选；取值为 `Never`、`OnFailure` 或 `Always`，默认值为 `Never`。 |
| `maxRuntimeSec` | 可选；必须为正整数；未传入时，服务使用全局默认值。 |
| `env` / `name` | 可选。 |

提交成功后，服务立即返回本地 `taskId` 以及 `PENDING` 或 `STARTING` 状态；云资源采用异步创建。

### 4.3 任务查询与取消

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/eci/tasks` | 返回任务列表；调用方可以用 `?status=` 过滤。 |
| GET | `/api/v1/eci/tasks/{id}` | 返回任务详情，内容包含关联实例、实际规格、时间与失败原因。 |
| POST | `/api/v1/eci/tasks/{id}/cancel` | 幂等取消任务；服务删除关联实例，并将任务置为 `CANCELLED`。 |

### 4.4 手动创建实例

`POST /api/v1/eci/instances`

```json
{
  "name": "demo-1",
  "image": "nginx:alpine",
  "imageSnapshotId": "imc-2zebxkiifuyzzlhl",
  "cpu": 1.0,
  "memory": 2.0,
  "restartPolicy": "Never",
  "command": ["nginx"],
  "args": ["-g", "daemon off;"],
  "env": {}
}
```

当请求未传入 `image` 时，服务使用 `eci.default-image`。当请求未传入 `command` 与 `args` 时，服务使用镜像默认入口。`imageSnapshotId` 为可选字段，用于指定已有镜像缓存以加速创建。

### 4.5 删除实例

`DELETE /api/v1/eci/instances/{id}`

该接口删除云资源，而不是停止后保留实例。

### 4.6 实例列表 / 详情 / 刷新

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/eci/instances` | 返回实例列表；调用方可以用 `?status=` 过滤。 |
| GET | `/api/v1/eci/instances/{id}` | 返回实例详情。 |
| POST | `/api/v1/eci/instances/{id}/refresh` | 刷新云状态。 |

任务驱动创建的实例应携带 `taskId`；手动创建的实例的 `taskId` 为空。

### 4.7 健康检查

`GET /actuator/health` 不需要 Api-Key。

---

## 5. 配置需求

配置项前缀为 `eci.`。

| 键 | 说明 |
|----|------|
| `mock` | 是否启用 Mock。 |
| `api-key` | 必填且非空；服务用该值与请求头比对。 |
| `access-key-id` / `access-key-secret` | 阿里云凭证；仅在 `mock=false` 时需要。 |
| `region-id` | 地域，例如 `cn-hangzhou`。 |
| `vpc-id` / `vswitch-id` / `security-group-id` | 网络配置。 |
| `default-image` | 手动创建实例时的默认镜像；当前建议为 `nginx:alpine`。自动任务仍要求显式传入 `image`。 |
| `default-cpu` / `default-memory` | 默认规格。 |
| `create-timeout-sec` | 等待 Running 的超时时间。 |
| `default-restart-policy` | 默认重启策略；建议为 `Never`。 |
| `default-max-runtime-sec` | 任务未指定时的最大运行时长。 |
| `task-poll-interval-sec` | 运行中任务轮询云状态的周期。 |

凭证与 `api-key` 不得提交到 Git。仓库应提供 `application-local.yml.example`。

服务不提供跨镜像通用的默认 `command` 与 `args`。当请求未显式传入这些字段时，应由镜像自身的 `ENTRYPOINT/CMD` 决定启动程序。若未来增加镜像注册表，可以为每个镜像保存专属默认命令。

---

## 6. 非功能需求

| 类别 | 要求 |
|------|------|
| **OS** | 运行与验收环境以 Linux 为准。 |
| **JDK** | 运行环境使用 JDK 17。 |
| **构建** | 构建工具为 Maven 3.9+；支持 `mvn spring-boot:run` 与 `mvn -DskipTests package`。 |
| **框架** | 使用 Spring Boot 3.3.x，并与主仓同代。 |
| **云 SDK** | 使用阿里云 ECI 相关官方 Java SDK；具体版本在实现计划中锁定。 |
| **日志** | 创建与删除日志记录本地 id、`cloudInstanceId`、耗时与错误码；禁止打印 Secret 与 Api-Key。 |
| **成本** | README 应醒目提示：真实模式用完后务必删除实例。 |
| **任务恢复** | 服务重启后扫描 `PENDING`、`STARTING`、`RUNNING` 任务，并按关联实例与云状态恢复轮询或标记失败。 |
| **幂等** | 任务取消与实例删除必须幂等；自动删除与人工删除并发时，不得产生 500 或错误回退。 |
| **可观测性** | 日志同时记录 `taskId`、本地 `instanceId` 与 `cloudInstanceId`，并记录终态、超时和清理结果。 |

### 6.1 Linux 验收命令示意（实现后写入 README）

```bash
java -version          # 须为 17
mvn -v                 # 3.9+
cd eci-manager
mvn -DskipTests package
java -jar target/*.jar   # 或 mvn spring-boot:run

curl -s http://127.0.0.1:8081/actuator/health
curl -s -X POST http://127.0.0.1:8081/api/v1/eci/instances \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $ECI_API_KEY" \
  -d '{}'

curl -s -X POST http://127.0.0.1:8081/api/v1/eci/tasks \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $ECI_API_KEY" \
  -d '{
    "image": "registry-vpc.cn-hangzhou.aliyuncs.com/your-ns/3dgs:v1",
    "command": ["python", "train.py"],
    "args": ["--mode", "3dgs"],
    "restartPolicy": "Never",
    "maxRuntimeSec": 14400
  }'
```

---

## 7. 文档与交付（实现阶段）

实现阶段需要交付以下文档与说明：

1. `eci-manager/README.md`：说明 Linux 下 JDK/Maven 准备、mock/cloud 启动、Api-Key、任务与实例 curl、控制台核对，以及自动清理与及时删除以防账单的要求。
2. `application-local.yml.example`
3. 3DGS 类一次性任务示例：包含托管镜像地址、脚本参数、GPU 规格、`Never` 与超时说明。
4. 重启策略差异说明，以及 `Always` / `OnFailure` 的成本风险说明。
5. 本工程文档与本 PRD 版本的对应关系。
6. 「后续如何被 XJICloud 调用」短节；生产选型仍待定。

---

## 8. 分期建议

| 阶段 | 内容 | 状态 |
|------|------|------|
| **D0–D3** | Demo 基座：Mock、Api-Key、真实 ECI 创建/查询/删除、启动补偿 | ✅ 已完成 |
| **T0** | PRD v2.0 评审定稿 | ✅ 已完成（2026-07-27） |
| **T1** | 任务持久化、提交/查询/取消；任务关联实例；`restartPolicy` | 部分完成：领域模型 + Provider 透传（含 command/args）✅；**`/tasks` API 待实现** |
| **T2** | 运行中轮询、Succeeded/Failed 自动删除、`maxRuntimeSec` | 待实现 |
| **T3** | Mock 自动生命周期、3DGS 示例、Linux/cloud 验收与 README | 部分：Mock `simulateStatus` 钩子 ✅；其余待实现 |
| **T4（可选）** | 镜像注册表、批量提交、规格套餐 | 后续需求 |
| **I1** | 与 XJICloud 联调或迁入；评审生产环境是否继续使用 ECI、改用 CCI，或支持双 Provider | 另立需求 |

---

## 9. 仍待确认 / 请评审人拍板

| # | 议题 | 状态 | 说明 |
|---|------|------|------|
| 1 | 生产最终使用 ECI、CCI，还是双 Provider | **待定** | 该议题不阻塞 T1–T3；它影响 I1 与主仓 Deploy 文档是否需要改写。 |
| 2 | 第 0 节建议决策是否整体通过 | **已通过（2026-07-27）** | 决策已锁定；实现按计划推进。 |

> 请评审人重点查看：第 0 节建议决策表、第 1.3 节非目标、第 3.6 节自动删除与超时、第 4.2 节任务 API。其余章节是展开说明。

---

## 10. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-23 | 初稿，多项待确认。 |
| v1.1 | 2026-07-23 | 锁定主仓、Api-Key、小镜像、删除语义；运行环境为 Linux + JDK17 + Maven；生产选型仍待定。 |
| v2.0 | 2026-07-26 | 升格为任务驱动容器编排服务；新增一任务一实例、可配置重启策略、云终态轮询、自动删除与运行超时；保留手动实例管理；文件名去掉 Demo。 |
| v2.0.1 | 2026-07-26 | 增加 v1.1→v2.0 变更概览；第 0 节与第 9 节改为待评审拍板口径；同步 README/AGENT 引用。 |
| v2.0.2 | 2026-07-27 | 评审批准；同步分期进度：T0 完成，T1 领域模型与 Provider 透传部分完成，下一刀 `/tasks` API。 |

---

*下一步：按实现计划继续 Task 3（任务提交/查询/取消 API）。详见 `docs/superpowers/plans/2026-07-27-eci-task-orchestration.md`。*
