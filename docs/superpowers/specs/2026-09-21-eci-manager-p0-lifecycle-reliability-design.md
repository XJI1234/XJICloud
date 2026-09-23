# eci-manager P0 生命周期可靠性设计

> **状态：** 已批准（2026-09-21）  
> **日期：** 2026-09-21  
> **范围：** 仅 `eci-manager/`  
> **来源：** `eci-manager/AGENT_CONTEXT.md` §1.1 P0-1～P0-3

## 1. 目标

本设计把 ECI 创建和删除从“进程内异步线程 + sleep 轮询”改为“数据库持久化动作 + 短步骤协调器”，解决三项 P0 风险：

1. 删除失败或失败日志宽限阻塞 watcher，导致云资源清理无持续保证。
2. 云 Create 成功但 `cloudInstanceId` 落库前进程退出，可能留下本地无法关联的 ECI。
3. 任务先进入 `CANCELLED`、云实例后删除，终态语义与 PRD 不一致。

成功标准：

- 所有待执行云操作都能跨进程重启恢复。
- 同一本地实例的创建最多对应一个阿里云实例。
- 删除失败不会被放弃，重试期间状态和最后错误可追踪。
- `SUCCEEDED` 和 `CANCELLED` 都只在实例确认 `TERMINATED` 后出现。
- 保持失败处理硬顺序：抓日志 → 持久化失败与日志 → 宽限 → 删除。

## 2. 范围与不变量

### 2.1 本期包含

- P0-1：删除失败持久化重试、失败宽限非阻塞化。
- P0-2：创建 `ClientToken` 幂等、未知结果恢复、启动补偿。
- P0-3：新增 `CANCELLING`，清理完成后再进入 `CANCELLED`。
- Mock Provider、自动化测试、本地操作台及相关文档同步。

### 2.2 继续保持

- 本地实例状态是权威状态；HTTP 创建仍为异步受理。
- 一个任务只对应一个实例；关闭语义仍为 Delete ECI。
- 手动实例不因云侧任务终态自动删除；显式 DELETE 后仍须可靠重试。
- `SUCCEEDED` 仍要求云侧 `Succeeded` 且本地实例 `TERMINATED`。
- 失败任务可先进入 `FAILED`，但关联实例的删除动作必须在后台继续至成功。
- 不向 ECI 容器注入 AK/SK，不改当前凭据体系。

### 2.3 本期不做

- 训练队列、Worker 协议、自动扩缩、批量提交、业务回调。
- Redis/Kafka/RabbitMQ 等外部队列。
- 主后端或管理端改动。
- PostgreSQL/Flyway、凭据加密、API 分页等 P2 工作。
- 多副本部署承诺。动作领取仍使用租约和条件更新，避免同进程并发重复执行，并为以后多副本保留正确边界。

## 3. 方案选择

采用独立持久化动作表 `eci_lifecycle_action`，不把创建和删除重试字段继续堆入 `eci_instance`。

| 方案 | 结论 | 原因 |
|------|------|------|
| 持久化动作表 + 协调器 | **采用** | 创建、删除、宽限、重试、租约均有独立生命周期；崩溃恢复边界清楚 |
| 只在实例表追加字段 | 不采用 | 创建和删除阶段共用字段后组合状态过多，取消竞态仍难表达 |
| 外部消息队列 | 不采用 | 超出本期边界，增加部署依赖，当前单服务无需引入 |

## 4. 总体架构

```text
Controller
   │ 同一事务
   ├─ eci_instance（本地权威状态）
   ├─ eci_task（任务状态）
   └─ eci_lifecycle_action（待执行 CREATE / DELETE）
                         │
                         ▼
LifecycleActionDispatcher（定时扫描、条件领取、投递短任务）
                         │
                         ▼
LifecycleActionProcessor（每次只做一次 Create/Delete/Describe）
                         │
                         ▼
EciProvider ───────────► Mock / Aliyun ECI
```

职责划分：

| 组件 | 职责 |
|------|------|
| `LifecycleActionService` | 在业务事务内幂等创建动作、请求删除、完成动作、计算下次执行时间 |
| `LifecycleActionDispatcher` | 分批查询到期动作，以条件更新领取租约，投递到现有有界执行器 |
| `LifecycleActionProcessor` | 根据动作类型和阶段执行一个有界步骤；不循环、不 `sleep` |
| `TaskLifecycleWatcher` | 识别任务云终态/超时、抓失败日志、持久化任务结果、请求删除动作 |
| `StartupCompensationRunner` | 恢复过期租约，为旧版本遗留记录补建动作，不直接执行长轮询 |
| `EciProvider` | 执行单次云调用；创建接收稳定 `ClientToken`；返回可判断的异常类别 |

`AsyncLifecycleWorker` 的长轮询职责由动作协调器替代。现有线程池可保留为短步骤执行器，但线程中不得进行轮询循环或宽限等待。

## 5. 持久化模型

### 5.1 `eci_lifecycle_action`

| 字段 | 类型/约束 | 含义 |
|------|-----------|------|
| `id` | UUID PK | 动作 ID |
| `instance_id` | UUID, NOT NULL | 本地实例 ID；代码层校验实例存在 |
| `action_type` | `CREATE` / `DELETE` | 云动作类型 |
| `phase` | 见 §5.2 | 当前远端步骤 |
| `status` | 见 §5.3 | 调度状态 |
| `client_token` | varchar(64), nullable | CREATE 使用本地实例 UUID 字符串；DELETE 为空 |
| `attempt_count` | int, NOT NULL | 发生云调用失败的累计次数 |
| `next_attempt_at` | Instant, NOT NULL | 下次允许领取的时间；失败宽限也由此表达 |
| `lease_token` | UUID, nullable | 本次领取者 token，防止旧执行结果覆盖新租约 |
| `lease_until` | Instant, nullable | 进程退出后的自动恢复边界 |
| `last_error` | varchar(1000), nullable | 最近一次云操作错误，不存凭据 |
| `created_at` / `updated_at` | Instant, NOT NULL | 审计时间 |
| `completed_at` | Instant, nullable | 动作终态时间 |

表级唯一约束：`(instance_id, action_type)`。同一实例最多各有一条 CREATE 和 DELETE，重复请求通过读取现有动作实现幂等。

本期保留已完成动作，不做历史清理策略。动作表不作为公开 API 返回。

### 5.2 动作阶段 `LifecycleActionPhase`

- `CREATE_REQUEST`：尚未可靠取得 `cloudInstanceId`，用相同 `ClientToken` 调 Create。
- `WAIT_RUNNING`：已有 `cloudInstanceId`，单次 Describe 等待 Running/Succeeded/Failed。
- `DELETE_REQUEST`：调用 Delete；NotFound 直接视为已删除。
- `WAIT_DELETED`：单次 Describe 等待 Deleted/NotFound。

### 5.3 调度状态 `LifecycleActionStatus`

- `READY`：可在 `nextAttemptAt` 到期后领取。
- `IN_PROGRESS`：已领取且租约未过期。
- `WAITING`：等待下次轮询或退避。
- `SUCCEEDED`：动作完成。
- `CANCELLED`：CREATE 在确认尚未发往云端时被取消。
- `FAILED`：仅用于能确定“云端未创建资源”的 CREATE 永久拒绝。

DELETE 不进入 `FAILED`。删除失败始终回到 `WAITING`，按有上限的退避持续重试并告警。

## 6. 领取、租约与重试

### 6.1 领取

Dispatcher 每秒最多读取 20 条满足以下条件的动作：

- `status` 为 `READY` / `WAITING`，且 `nextAttemptAt <= now`；或
- `status=IN_PROGRESS` 且 `leaseUntil <= now`（崩溃恢复）。

每条动作使用条件 UPDATE 写入新的 `leaseToken`、`leaseUntil=now+60s`、`status=IN_PROGRESS`。只有更新行数为 1 的领取者可以投递执行。执行结果提交时必须同时匹配动作 ID 和 `leaseToken`；过期执行者的结果不得覆盖新租约。

执行器继续使用有界线程池。队列拒绝时立即释放该动作租约并将 `nextAttemptAt` 推迟 1 秒，不丢动作。

### 6.2 退避

- 初始退避：2 秒。
- 第 N 次错误：`min(2 * 2^(N-1), 300)` 秒。
- 最大退避：300 秒。
- 删除尝试达到 5 次时记录 ERROR 级告警；以后每 10 次再告警一次，但继续重试。
- 正常云状态轮询沿用 `eci.poll-interval-ms`，不计入失败尝试次数。

上述默认值加入 `eci.lifecycle-action.*` 配置并在启动时校验为正数。测试使用注入 `Clock`，不真实等待。

### 6.3 云错误分类

新增内部 `CloudOperationException`，至少携带：

- `kind=REJECTED`：云 API 明确拒绝且能确认未创建资源，例如参数非法、鉴权拒绝、库存拒绝。CREATE 可终止为 `FAILED`。
- `kind=UNKNOWN_OUTCOME`：网络中断、超时、服务端错误等无法确认 Create 是否生效。必须使用同一 `ClientToken` 重试。
- `kind=RETRYABLE`：Describe/Delete 临时失败，按退避重试。

未知错误一律按更保守的 `UNKNOWN_OUTCOME` / `RETRYABLE` 处理，不能为了尽快结束而假定云资源不存在。

## 7. 创建流程与崩溃恢复

### 7.1 正常创建

1. 创建 HTTP 事务保存 `eci_instance(status=CREATING)`。
2. 同一事务创建 `CREATE/CREATE_REQUEST/READY` 动作，`clientToken=instanceId.toString()`。
3. HTTP 立即返回，不直接调用云 API。
4. Processor 领取动作，重新从实例行构建完整 `CreateSpec`。
5. `AliyunEciProvider` 将 token 写入 `CreateContainerGroupRequest.setClientToken(...)`，并给实例添加本地 instanceId Tag。
6. Create 返回后，在同一短事务写 `cloudInstanceId`，动作进入 `WAIT_RUNNING/WAITING`。
7. 后续步骤每次只 Describe 一次；Running 或 Succeeded 时动作 `SUCCEEDED`，实例进入 `RUNNING`，任务按现有规则推进。

实例行已有 name、image、cpu、memory、env、command、args、instanceType、gpu、imageSnapshotId、shmSize、restartPolicy，可完整重建请求。实现必须保证重试时这些字段不重新读取可变化的默认配置。

### 7.2 Create 未知结果

若云端可能已创建，但本地尚未写入 ID：

- 动作保留 `CREATE_REQUEST`，以同一 token 重试。
- 云端幂等返回同一实例 ID 后继续正常流程。
- 超过 `eci.create-timeout-sec` 时，任务和实例可以暴露 `FAILED`/创建超时，但动作不能丢弃；它继续以最大退避恢复结果。
- 超时后若最终取得 cloudId，因为本地任务已经失败或实例已有终止意图，立即创建/唤醒 DELETE 动作，不把实例重新标为可用 RUNNING。

这一区分保证“业务及时看到失败”和“后台继续防止孤儿资源”可以同时成立。

### 7.3 永久拒绝

仅 `REJECTED` 可将 CREATE 动作置 `FAILED`。此时 Provider 已确认云端未创建资源：

- 实例进入 `FAILED`，记录短错误。
- 关联活动任务进入 `FAILED`。
- 不创建 DELETE 动作。

## 8. 删除、失败宽限与持续清理

### 8.1 请求删除

所有删除入口统一调用 `LifecycleActionService.requestDelete(instanceId, notBefore)`：

- 手动 DELETE：`notBefore=now`。
- 任务成功：持久化 `cloudStatus=Succeeded` 后 `notBefore=now`。
- Failed/TIMEOUT：先抓日志并持久化任务 `FAILED`，再以 `now + failureLogGraceSec` 作为 `notBefore`。
- cancel：任务先进入 `CANCELLING`，再以 `notBefore=now` 请求删除。

该方法幂等 upsert DELETE 动作，并把实例置为 `STOPPING`。重复请求只允许把 `nextAttemptAt` 提前，不能延后已经到期的清理。

### 8.2 CREATE 依赖

DELETE 动作执行前检查 CREATE：

- CREATE 尚未领取且无 cloudId：将 CREATE 置 `CANCELLED`，实例可直接 `TERMINATED`。
- CREATE 为 `IN_PROGRESS` 或未知结果：DELETE 保持 `WAITING`，等待同 token 创建结果收敛。
- CREATE 已明确 `FAILED` 且无 cloudId：实例直接 `TERMINATED`。
- 已有 cloudId：执行正常 Delete。

因此取消请求不会在“云 Create 可能仍在飞行”时过早宣称资源已清理。

### 8.3 Delete 执行

1. `DELETE_REQUEST` 调一次 Delete。
2. Delete 返回成功后进入 `WAIT_DELETED`；NotFound 直接完成。
3. `WAIT_DELETED` 每个动作步骤只 Describe 一次。
4. Deleted/NotFound 后，动作 `SUCCEEDED`、实例 `TERMINATED`。
5. 任意临时失败记录 `lastError` 并回到 `WAITING`；永不放弃。

Delete 成功返回后、动作状态落库前崩溃时，重启会再次调用幂等 Delete，不会造成额外资源。

## 9. 任务状态与竞态

### 9.1 新状态机

```text
PENDING → STARTING → RUNNING → SUCCEEDED | FAILED
PENDING | STARTING | RUNNING → CANCELLING → CANCELLED
```

- `CANCELLING` 是非终态控制状态，不允许被 Succeeded/Failed 回写覆盖。
- cancel 接口保持 HTTP 200 和统一响应体，首次调用立即返回 `CANCELLING`。
- 对 `CANCELLING` 重复 cancel 返回当前状态，不重复创建动作。
- 对 `CANCELLED` 重复 cancel 返回 `CANCELLED`。
- 对已提交的 `SUCCEEDED` / `FAILED` cancel 仍返回 409。
- 若取消条件更新先提交，取消胜出；后续云 Succeeded/Failed 只用于取证/清理，不改变任务为成功或失败。
- 若 `SUCCEEDED` / `FAILED` 已先提交，后续 cancel 返回 409。

### 9.2 删除完成后的任务收敛

实例进入 `TERMINATED` 后按持久化证据处理：

- 任务为 `CANCELLING` → 条件更新为 `CANCELLED`，设置 `endedAt`。
- 活动任务且实例 `cloudStatus=Succeeded` → 条件更新为 `SUCCEEDED`。
- 任务已 `FAILED` → 保持 `FAILED`，只完成清理。
- 手动实例无 taskId → 不操作任务。

现有 watcher 的内存 `pendingSucceeded` 集合不再作为成功证据；成功证据只读实例行的 `cloudStatus=Succeeded`。

## 10. 启动补偿与旧数据

启动时先恢复动作，再补齐旧实例：

1. 所有租约过期的 `IN_PROGRESS` 动作恢复为可领取。
2. `CREATING` 且无 cloudId、无 CREATE 动作：按实例持久化规格补建 CREATE，token 使用实例 UUID。
3. `CREATING` 且有 cloudId：补建 `WAIT_RUNNING` CREATE 动作。
4. `STOPPING` 且未 `TERMINATED`：补建 DELETE 动作。
5. taskId 非空的 `FAILED` 实例若仍有 cloudId：补建 DELETE 动作。
6. 手动 `FAILED` 实例不因任务规则自动删除；只有存在 DELETE 动作时才继续清理。
7. 旧 `CANCELLED` 任务若实例未 `TERMINATED`：改为 `CANCELLING`、清空旧 `endedAt`、补建 DELETE；清理完成后重新写 `CANCELLED`。

补偿必须逐条短事务执行。一条坏数据只记录告警，不中止其余记录。

## 11. API 与操作台变化

API 路径不变：

- `POST /api/v1/eci/tasks/{id}/cancel` 首次通常返回 `data.status=CANCELLING`。
- `GET /tasks`、`GET /tasks/{id}` 的枚举新增 `CANCELLING`。
- 实例 API 继续使用现有 `CREATING/RUNNING/STOPPING/TERMINATED/FAILED`。

本期不把动作表或重试字段暴露成新 REST API。运维信息先通过结构化日志提供；P2 再增加指标和巡检 API。

静态操作台需要：

- 把 `CANCELLING` 当作非终态，继续轮询。
- 显示“正在清理”，不显示为失败或完成。
- cancel 按钮在 `CANCELLING` 时禁用或保持幂等。

## 12. 配置

新增配置段及默认值：

```yaml
eci:
  lifecycle-action:
    dispatch-interval-ms: 1000
    batch-size: 20
    lease-sec: 60
    retry-initial-sec: 2
    retry-max-sec: 300
    cleanup-alert-attempts: 5
```

保留现有 `create-timeout-sec`、`delete-timeout-sec`、`poll-interval-ms`、`task-poll-interval-sec` 和 `failure-log-grace-sec`。其中：

- create/delete timeout 用于业务状态和告警，不再依赖线程循环 deadline。
- `failure-log-grace-sec` 转换成 DELETE 动作的 `nextAttemptAt`，不再调用 `Thread.sleep`。
- 所有新增数值配置启动时校验为正数；grace 继续允许 0。

## 13. 测试与验收

### 13.1 TDD 分层

1. 动作实体/仓储：唯一约束、到期查询、租约条件领取、旧租约结果拒绝。
2. 重试计算：固定 Clock 下验证退避和最大间隔。
3. CREATE Processor：正常、Rejected、Unknown Outcome、相同 ClientToken 重试、超时后取得 cloudId 自动清理。
4. DELETE Processor：NotFound、删除成功后轮询、失败两次后成功、长期失败不进入终态 FAILED。
5. watcher：日志持久化先于 DELETE 动作创建；无 `sleep`；20 个失败任务一轮均能登记清理。
6. cancel：PENDING/STARTING/RUNNING → CANCELLING → CANCELLED；重复 cancel；与 Succeeded/Failed 竞态。
7. 启动补偿：过期租约、旧 CREATING/STOPPING/FAILED/CANCELLED 数据。
8. Mock HTTP IT：提交、运行、成功、失败、超时、取消、手动实例全路径。
9. 静态操作台测试：识别 `CANCELLING` 并继续轮询。

### 13.2 崩溃窗口模拟

Mock Provider 按 `ClientToken` 保存幂等结果。测试先让 Provider 创建实例，再模拟“返回前/落库前异常”，使动作租约过期；下一次处理必须：

- 使用相同 token；
- 得到同一 cloudId；
- Mock 中只存在一个云实例；
- 最终进入 RUNNING，或在取消/超时意图下进入 DELETE 并清理。

### 13.3 质量门

每个阶段必须保留 Red → Green 证据并依次执行：

```bash
cd eci-manager
source ../.tools/env.sh
mvn -B test
```

真云验证只在用户明确授权并准备凭据后执行。创建测试资源后必须 cancel/delete，并在阿里云控制台确认无残留。

## 14. 实施分期

实施计划应拆成独立审查的阶段：

1. **P0-A：动作模型与领取基础**，尚不切换生产路径。
2. **P0-B：DELETE 动作、非阻塞宽限与永久重试**，完成 P0-1。
3. **P0-C：CREATE 动作、ClientToken 与崩溃恢复**，完成 P0-2，并移除长轮询创建路径。
4. **P0-D：CANCELLING 契约与竞态**，完成 P0-3。
5. **P0-E：启动补偿、操作台、文档和全量回归**。

每阶段独立提交候选 diff、先跑确定性检查，再由独立审查 agent 做语义审查。未经用户要求不执行 commit 或 push。
