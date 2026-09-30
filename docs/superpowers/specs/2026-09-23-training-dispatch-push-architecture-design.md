# 训练任务派发：Push + 可靠 Outbox 架构设计

> **状态：** 待审阅（已按 Redis 保留策略微调）  
> **日期：** 2026-09-23  
> **范围：** 目标架构（概念与边界）；不含实现细节、接口字段清单、排期  
> **背景：** 弃用常驻 GPU Worker 拉队列；主后端向 eci-manager 投递训练任务

---

## 1. 问题与目标

### 1.1 现状（旧）

- 主后端在数据集上传完成后：关系数据库写入 `TrainingJob=QUEUED`，并将 `jobId` **RPUSH** 到 Redis 列表（如 `xjicloud:jobs`）。
- 常驻 `gpu-worker` 长轮询 `GET /api/v1/worker/jobs/next`，从 Redis **出队**后训练并回写进度/完成。
- **入队 ≠ 派发到云**；无 Worker 时任务一直停在排队。该模型适合常驻算力节点，不适合按需 ECI。
- Redis 在主后端上还有**与训练无关**的用途：验证码、登录失败限流（删 Worker / 改派发时不得误拆整机 Redis）。

### 1.2 目标

- 用户体感：**交作业后由平台向云侧派发**（push）。
- 系统内部：**业务落库 + Redis 入队 → 主后端异步出队派发 → 失败重试 → 与 eci `taskId` 幂等绑定**。
- **换消费者，不强制换队列存储**：删除常驻 Worker；由**主后端调度器**消费 Redis 待派发列表并 `POST` eci-manager。
- eci-manager 继续只做「eci 任务 → 拉起并收敛一个容器」；主后端做「业务作业 → eci 任务」。
- 第一版只具备**最基本派发能力**（见 §7）。

### 1.3 非目标（第一版）

- 主后端并发帽、优先级、多租户公平调度
- eci → 主后端 webhook
- 容器回调主后端 progress/complete（复活 Worker 协议）
- 细粒度 percent / stage 进度条
- 自动扩缩、批量提交
- `storage.mode=dataset` 等存储增强（能用现有等价能力把输入输出交给容器即可）
- **将训练队列从 Redis 迁到关系数据库**（非必须；见 §2.1）
- **去掉主后端对 Redis 的依赖**（训练队列可留；鉴权侧仍需要 Redis）

---

## 2. 职责边界

| 组件 | 负责 | 不负责 |
|------|------|--------|
| **主后端** | 训练作业业务真相（关系数据库）；Redis 待派发出箱的入队/出队/取消移除；向 eci 投递与重试；`trainingJobId ↔ eciTaskId` 绑定（落在业务库）；用户/Admin API；粗状态 SSE；取消编排；轮询 eci 终态并回写业务 | 阿里云 OpenAPI；ECI 实例 Create/Delete 状态机 |
| **eci-manager** | 受理任务；异步拉起容器；OSS storage 注入；云侧生命周期与终态；任务级幂等（见 §5） | 业务训练队列；用户进度语义；Worker 注册/心跳/拉活 |
| **训练容器** | 按注入的输入/输出约定执行算法并写产出 | 注册、心跳、拉队列、回调主后端完成协议 |
| **Redis** | **第一版：训练待派发列表（outbox 挂榜）**；以及既有验证码/登录限流 | 业务终态真相；eci 生命周期 |
| **关系数据库（DB）** | `TrainingJob` 等业务状态；`eciTaskId` 等绑定与可观测字段 | 不必在第一版承载「待派发挂榜」本身 |

用语：**DB** = 主后端关系数据库（dev 多为 H2，prod 为 PostgreSQL），不是 Redis。

两层幂等**不重复建设同一件事**：

- eci：保证「一个已受理的 eci 任务 → 最终对应一个容器」。
- 主后端：保证「一个活跃业务作业 → 最终对应一个 eci 任务（或不重复盲建）」。

主后端派发层做**完整可靠投递**（非「尽量瘦」的同步 fire-and-forget），但不复制 eci 的云生命周期引擎。

### 2.1 Redis 与日后迁 DB

- **删 Worker 时不必改掉对 Redis 训练队列的依赖**；必做的是换消费者（主后端调度器）。
- 现网训练队列对接面很浅（`RedisQueueService` 四操作 + 少数调用点），耦合低；若日后把挂榜迁到 DB 表，属于可选项、预期改动面小。
- 第一版不把「迁出 Redis」列为成功标准。

---

## 3. 主路径

```
用户数据集上传完成
  → 关系数据库：TrainingJob = QUEUED（及派发相关字段初值）
  → Redis：入队 jobId（待派发挂榜）
  → 主后端调度器出队 / 领取（意图 IN_FLIGHT）
  → POST eci-manager /tasks（带 clientRequestId）
  → 成功：DB 绑定 eciTaskId，意图 BOUND，业务 → RUNNING
  → 投递失败/可重试错误：重新入队或等价可重试放置，退避后再投；不丢单、不假装已跑
  → 主后端轮询已绑定且未终态的 eci 任务
  → 按映射收敛业务 COMPLETED / FAILED / CANCELLED
```

**取消：**

- 业务标为取消意向/终态策略与现网一致的前提下：未绑定则从 Redis 移除（或标记不再投递）并停意图；已绑定则调用 eci `POST .../tasks/{id}/cancel`，并以 eci 终态或本地策略收敛业务 `CANCELLED`。

**重新训练：**

- 视为新的可跑意图或新作业；幂等键不得与仍可能存活的旧 eci 任务冲突（见 §5）；再次 Redis 入队由主后端调度器消费。

---

## 4. 状态模型

拆成三套状态，避免单一枚举同时表示业务、投递与云侧。

### 4.1 业务任务 `TrainingJob`（用户可见）

| 状态 | 含义 |
|------|------|
| `UPLOADING` | 数据集上传中 |
| `QUEUED` | 已受理；等待或正在向 eci 投递 |
| `RUNNING` | 已绑定 `eciTaskId`，云上执行中（含起实例过程） |
| `COMPLETED` / `FAILED` / `CANCELLED` | 终态 |

第一版用户侧只需上述粗状态（可经现有 SSE 推送状态变化）。

### 4.2 派发意图 `Dispatch`（主后端内部）

语义上必须可表达下列状态（第一版存储形态：**Redis 挂榜表达「待领取」**；`IN_FLIGHT` / `BOUND` / `eciTaskId` 等以业务库字段或等价结构为准，实现阶段选定）：

| 状态 | 含义 | 第一版典型落点 |
|------|------|----------------|
| `PENDING` | 待投递 | Redis 列表中有该 `jobId` |
| `IN_FLIGHT` | 正在调用 eci（抑制并发双发） | 调度器领取后写 DB |
| `BOUND` | 已绑定 `eciTaskId` | DB |
| `SUCCEEDED` / `FAILED` | 该意图结束（是否允许再开新意图由重试/重训策略决定） | DB |
| `CANCELLED` | 业务取消，不再投递 | DB + 从 Redis 移除 |

约束：

- 同一 job 的**活跃**派发意图串行。
- 进入 `BOUND` 之后，重试逻辑只做**状态同步**，不再对同一活跃意图盲目 `POST /tasks` 新建。

### 4.3 eci 任务状态（只读对齐）

沿用 eci-manager 既有枚举（如 `STARTING` / `RUNNING` / `SUCCEEDED` / `FAILED` / `CANCELLED` 等）。

映射：

| eci | 业务 |
|-----|------|
| 非终态且已绑定 | `RUNNING` |
| `SUCCEEDED` | `COMPLETED` |
| `FAILED`（含超时等失败语义） | `FAILED` |
| `CANCELLED` | `CANCELLED` |

对齐直觉：

- 「排队中」→ 业务 `QUEUED`（Dispatch 可能为 `PENDING` / `IN_FLIGHT`）。
- 「训练中」→ 业务 `RUNNING`，且**必须**已有 `eciTaskId`。

---

## 5. 幂等：作业 → eci 任务

采用业界常见的 **Client Request Id / Idempotency-Key** 模式。

| 约定 | 说明 |
|------|------|
| 键 | 主后端传递 `clientRequestId`；默认稳定值 = `trainingJobId` |
| eci | 同一 `clientRequestId` 且前一次已受理 → **返回已有任务**，不新建 |
| 主后端 | 成功后持久化 `eciTaskId`；已有绑定则只查询同步；超时歧义下用**同一键**再次 `POST`，由 eci 去重 |
| 重训 | 新 attempt 或新 job，更换键，避免撞上旧任务 |

分层：

- 主后端 `clientRequestId`：作业 → eci 任务。
- eci 内部实例 Create 的 ClientToken：eci 任务 → 容器。  
二者**不得混用为同一语义**。

主后端只持久化并认 `eciTaskId`，不解析云实例 ID。

---

## 6. 终态回写

| 方式 | 第一版 |
|------|--------|
| 主后端轮询 eci `GET /tasks/{id}` | **采用** |
| eci → 主后端 webhook | 不做 |
| 容器回调主后端 progress/complete | 不做 |

规则：

- 仅轮询**已绑定且业务未终态**的任务。
- 未绑定任务只走派发重试，不轮询 eci。
- 产出展示仍按平台既有 OSS key / 下载约定；具体存储 mode 选型属实现阶段，架构上要求「容器能读到输入、写到约定产出」。

---

## 7. 第一版范围小结

**要有：** §3 主路径（含保留 Redis 入队）；主后端调度器出队并 push eci；§4 状态；§5 幂等契约；§6 轮询收敛；取消编排；不再依赖常驻 Worker 拉队列。

**不做：** §1.3 所列项（含「训练队列迁出 Redis」「去掉 Redis 依赖」）；主后端并发帽。

---

## 8. 与旧架构的关系

| 旧 | 新 |
|----|----|
| Redis 出队 → Worker 训练 | Redis 出队 → **主后端调度器** → push eci → 容器训练 |
| Worker JWT / register / heartbeat / jobs/next | 废弃为执行路径（清理节奏实现阶段定） |
| Worker 回写 progress/complete | 改为 eci 终态轮询映射 |
| Redis 队列深度作「待跑」观测 | **可继续**用 `LLEN` / 现有 `queueDepth`；业务真相仍看 DB 状态与 `eciTaskId` |
| 训练队列在 Redis | **第一版保留**；迁 DB 非必须、可后置 |

---

## 9. 风险与原则（架构级）

1. **超时歧义**：无 `clientRequestId` 幂等则禁止盲目重试建任务；本设计将 eci 支持该键列为契约前提。
2. **双真相**：业务状态以关系数据库为准；eci 状态为执行侧真相，由轮询投影；Redis 只表达「尚未被调度器成功绑定前的待派发」。
3. **取消竞态**：取消与派发并发时，以「取消优先、已绑定必调 eci cancel」为原则；细规则实现阶段补全。
4. **eci 不可达**：任务保持可重试（重新入队或等价），业务保持 `QUEUED`，不得标为已完成或静默丢弃。
5. **无消费者**：删 Worker 后若未上主后端调度器，Redis 会堆死单——换消费者是硬条件，换存储不是。

---

## 10. 已确认决策记录

| 决策 | 结论 |
|------|------|
| 对外叙事 | Push（平台派发） |
| 对内机制 | 业务落库 + Redis 入队 → 异步派发 → 重试 → eciTaskId 幂等绑定 |
| 主后端派发层 | 完整可靠层，非最瘦同步方案 |
| 与 eci 异步/幂等关系 | 分层必要；主后端不复制容器生命周期 |
| 业务/派发/eci 状态 | 三套分离 + 映射表 |
| 幂等 | `clientRequestId`，默认 = `trainingJobId` |
| 终态 | 主后端轮询；不做 webhook / 容器回写 |
| 进度 | 第一版仅粗状态 |
| 并发帽 | 第一版不做 |
| Redis 训练队列 | **第一版保留**；删 Worker 不强制去 Redis 依赖 |
| 队列迁 DB | 非必须；对接面浅，可后置 |
| 范围 | 最基本派发能力 |

---

## 11. 后续（本文之外）

本文通过审阅后，若进入落地：再单独写实现计划（主后端调度器消费 Redis、eci `clientRequestId` 契约、训练镜像入口从拉队列改为读注入约定、Worker API 下线步骤等）。**未授权前不实施。**
