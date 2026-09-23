# eci-manager P0 Lifecycle Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用数据库持久化生命周期动作替换进程内长轮询，使 ECI 创建幂等可恢复、删除持续重试，并让任务严格按 `CANCELLING -> CANCELLED` 收敛。

**Architecture:** 新建 `eci_lifecycle_action` 表保存 CREATE/DELETE 的阶段、租约、退避和最后错误；定时 dispatcher 只领取到期动作，有界 processor 每次最多执行一个云调用。实例仍是本地权威状态，任务终态由实例持久化证据收敛，HTTP 路径和统一响应体不变。

**Tech Stack:** Java 17、Spring Boot 3.3.5、Spring Data JPA、H2、Aliyun Java SDK ECI 1.3.3、JUnit 5、Mockito、MockMvc、Awaitility

**Spec:** `docs/superpowers/specs/2026-09-21-eci-manager-p0-lifecycle-reliability-design.md`

## Global Constraints

- 只修改 `eci-manager/` 及本计划/设计文档；不修改主后端、前端、Worker、Spark 或 Rust。
- HTTP 创建继续异步受理；一个任务仍只对应一个实例；关闭语义仍是 Delete ECI。
- 失败顺序固定为：`fetchContainerLog` -> 持久化 `FAILED` 与日志 -> 宽限截止时间 -> DELETE 动作。
- Processor 每次执行最多一次 Create、Describe 或 Delete，不写轮询循环，不调用 `Thread.sleep`。
- CREATE 的 `ClientToken` 固定为本地实例 UUID 字符串；未知结果始终复用同一 token。
- DELETE 不进入 `FAILED`，错误使用 2 秒起步、300 秒封顶的指数退避无限重试。
- `SUCCEEDED`、`CANCELLED` 只在关联实例确认 `TERMINATED` 后写入。
- 不新增动作 REST API，不暴露凭据，不把 AK/SK 注入容器。
- 自动化测试只用 Mock，不访问真云；真云验证须再次获得用户明确授权。
- 严格保留 Red -> Green 证据。每个任务由独立编码 agent 实现，确定性检查通过后由独立审查 agent 审查；修复重审最多 3 轮。
- 未获得明确授权时不执行 `git commit` 或 `git push`；每个任务只记录建议提交信息。

## File Structure

新增文件按职责拆分如下：

- `src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionEntity.java`：动作持久化字段与唯一约束。
- `src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionType.java`：`CREATE`、`DELETE`。
- `src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionPhase.java`：四个远端阶段。
- `src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionStatus.java`：六个调度状态。
- `src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionRepository.java`：到期扫描、条件领取、带租约 token 的条件更新。
- `src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionService.java`：动作幂等创建、租约状态变更、退避和实例终态持久化。
- `src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionDispatcher.java`：定时批量领取并投递短任务。
- `src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionProcessor.java`：每次推进一个 CREATE/DELETE 步骤。
- `src/main/java/com/xjicloud/eci/lifecycle/action/ClaimedLifecycleAction.java`：dispatcher 交给 processor 的不可变领取快照。
- `src/main/java/com/xjicloud/eci/provider/CloudOperationException.java`：云错误类别与安全短文案。
- `src/main/java/com/xjicloud/eci/provider/CloudOperationKind.java`：`REJECTED`、`UNKNOWN_OUTCOME`、`RETRYABLE`。
- `src/test/java/com/xjicloud/eci/lifecycle/action/LifecycleActionRepositoryTest.java`：动作映射、到期查询和条件更新。
- `src/test/java/com/xjicloud/eci/lifecycle/action/LifecycleActionServiceTest.java`：租约、幂等请求和退避状态变更。
- `src/test/java/com/xjicloud/eci/lifecycle/action/LifecycleActionDispatcherTest.java`：批量领取、并发领取和执行器拒绝。
- `src/test/java/com/xjicloud/eci/lifecycle/action/CreateLifecycleActionProcessorTest.java`：CREATE 单步状态机。
- `src/test/java/com/xjicloud/eci/lifecycle/action/DeleteLifecycleActionProcessorTest.java`：DELETE 单步状态机。
- `src/test/java/com/xjicloud/eci/lifecycle/LifecycleActionRecoveryIT.java`：幂等创建、删除重试、崩溃窗口和重启补偿集成测试。

迁移完成后删除 `src/main/java/com/xjicloud/eci/lifecycle/AsyncLifecycleWorker.java`；现有短步骤执行器 bean 保留。

---

### Task 1: 动作模型、配置与退避基础

**Files:**
- Create: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionType.java`
- Create: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionPhase.java`
- Create: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionStatus.java`
- Create: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionEntity.java`
- Create: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionRepository.java`
- Create: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionBackoff.java`
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/config/EciProperties.java`
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/config/ProviderConfig.java`
- Modify: `eci-manager/src/main/resources/application.yml`
- Modify: `eci-manager/src/test/resources/application.properties`
- Create: `eci-manager/src/test/java/com/xjicloud/eci/lifecycle/action/LifecycleActionRepositoryTest.java`
- Create: `eci-manager/src/test/java/com/xjicloud/eci/lifecycle/action/LifecycleActionBackoffTest.java`
- Modify: `eci-manager/src/test/java/com/xjicloud/eci/config/ProviderConfigValidationTest.java`

**Interfaces:**
- Produces: `LifecycleActionRepository.findDueIds(Instant now, Collection<LifecycleActionType> enabledTypes, Collection<LifecycleActionStatus> ready, LifecycleActionStatus inProgress, Pageable pageable)`、`claim(...)`、`releaseExpiredLeases(...)`。
- Produces: `LifecycleActionBackoff.nextAttemptAt(Instant now, int attemptCount, int initialSec, int maxSec)`。
- Produces: `EciProperties.LifecycleAction`，默认值严格为 1000/20/60/2/300/5。

- [ ] **Step 1: 写动作实体映射与仓储条件更新的失败测试**

使用 `@DataJpaTest` 保存 CREATE 动作，断言 `(instance_id, action_type)` 重复写违反唯一约束；再固定 `now`，断言只有到期的 `READY/WAITING` 和租约过期的 `IN_PROGRESS` 会被 `findDueIds` 返回。条件领取测试必须用旧状态或旧 lease 再执行一次，断言更新行数为 0。

```java
assertThat(repository.claim(
        actionId, leaseToken, now, now.plusSeconds(60),
        Set.of(READY, WAITING), IN_PROGRESS)).isEqualTo(1);
assertThat(repository.claim(
        actionId, UUID.randomUUID(), now, now.plusSeconds(60),
        Set.of(READY, WAITING), IN_PROGRESS)).isZero();
```

- [ ] **Step 2: 运行新测试并保存 Red 证据**

```bash
cd eci-manager
source ../.tools/env.sh
mvn -B -Dtest=LifecycleActionRepositoryTest test
```

预期：编译失败，缺少 action 类型、实体和仓储。

- [ ] **Step 3: 实现动作枚举、实体和仓储**

实体使用 UUID 主键、`@Enumerated(EnumType.STRING)`、`@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"instance_id", "action_type"}))`；字段完整对应设计 §5.1。仓储用 JPQL 条件 UPDATE 领取，所有写方法加 `@Modifying(clearAutomatically = true, flushAutomatically = true)`，结果更新必须带 `id` 与 `leaseToken` 条件。

```java
public interface LifecycleActionRepository extends JpaRepository<LifecycleActionEntity, UUID> {
    @Query("""
        select a.id from LifecycleActionEntity a
        where a.actionType in :enabledTypes
          and ((a.status in :ready and a.nextAttemptAt <= :now)
            or (a.status = :inProgress and a.leaseUntil <= :now))
        order by a.nextAttemptAt asc, a.createdAt asc
        """)
    List<UUID> findDueIds(Instant now, Collection<LifecycleActionType> enabledTypes,
            Collection<LifecycleActionStatus> ready,
            LifecycleActionStatus inProgress, Pageable pageable);

    Optional<LifecycleActionEntity> findByInstanceIdAndActionType(
            UUID instanceId, LifecycleActionType actionType);
}
```

- [ ] **Step 4: 写并运行退避和配置校验 Red 测试**

覆盖 attempt 1/2/8/20 分别得到 2/4/256/300 秒，非法的 batch、lease、retry、alert 配置均让启动校验失败。

```bash
mvn -B -Dtest=LifecycleActionBackoffTest,ProviderConfigValidationTest test
```

- [ ] **Step 5: 实现配置与纯退避函数**

`EciProperties` 新增嵌套属性：

```java
public static class LifecycleAction {
    private long dispatchIntervalMs = 1000;
    private int batchSize = 20;
    private int leaseSec = 60;
    private int retryInitialSec = 2;
    private int retryMaxSec = 300;
    private int cleanupAlertAttempts = 5;
}
```

为六个字段实现标准 JavaBean getter/setter，并在 `EciProperties` 增加非空的 `lifecycleAction` 字段及 getter/setter，保证 Spring 配置绑定可用。

退避用饱和计算，避免大 attempt 位移溢出；`attemptCount <= 1` 返回初始间隔，达到上限直接返回最大间隔。

- [ ] **Step 6: 运行本任务测试和确定性质量门**

```bash
mvn -B -Dtest=LifecycleActionRepositoryTest,LifecycleActionBackoffTest,ProviderConfigValidationTest test
mvn -B -DskipTests package
git diff --check -- eci-manager
```

预期：全部通过；生产创建/删除路径尚未写 action，现有行为不变。

- [ ] **Step 7: 独立语义审查**

审查唯一约束、到期查询括号优先级、旧租约写入防护、指数溢出和所有正数配置校验。建议提交信息：`feat(eci-manager): add persistent lifecycle action model`。

---

### Task 2: 条件领取与租约服务

**Files:**
- Create: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/action/ClaimedLifecycleAction.java`
- Create: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionService.java`
- Create: `eci-manager/src/test/java/com/xjicloud/eci/lifecycle/action/LifecycleActionServiceTest.java`

**Interfaces:**
- Consumes: Task 1 的 action 仓储与 `EciProperties.LifecycleAction`。
- Produces: `Optional<ClaimedLifecycleAction> claim(UUID actionId, Instant now)`。
- Produces: `void releaseAfterDispatchFailure(UUID actionId, UUID leaseToken, Instant nextAttemptAt, String error)`。

- [ ] **Step 1: 写并运行并发领取与租约释放 Red 测试**

测试两次领取同一 action 只有一次成功；旧 lease token 不能释放新租约；有效 token 释放后 action 回到 `WAITING`、租约清空、`nextAttemptAt` 使用调用方给定时间；`recoverExpiredLeases(now)` 只恢复已过期租约。

```java
Optional<ClaimedLifecycleAction> first = service.claim(id, now);
Optional<ClaimedLifecycleAction> second = service.claim(id, now);
assertThat(first).isPresent();
assertThat(second).isEmpty();
service.releaseAfterDispatchFailure(id, first.orElseThrow().leaseToken(), now.plusSeconds(1), "executor rejected");
assertThat(actionRepository.findById(id).orElseThrow().getStatus()).isEqualTo(WAITING);
assertThat(actionRepository.findById(id).orElseThrow().getLeaseToken()).isNull();
```

```bash
mvn -B -Dtest=LifecycleActionServiceTest test
```

- [ ] **Step 2: 实现短事务 claim 服务**

注入 `Clock`（在配置中提供 `Clock.systemUTC()` bean），为每次领取生成 UUID lease token。`claim` 先做条件 UPDATE，再按 id 回读并返回只含 `actionId/instanceId/actionType/phase/leaseToken` 的 record；回读失败视为未领取。

```java
public record ClaimedLifecycleAction(
        UUID actionId,
        UUID instanceId,
        LifecycleActionType actionType,
        LifecycleActionPhase phase,
        UUID leaseToken) {}
```

- [ ] **Step 3: 运行本任务测试、编译门和审查**

```bash
mvn -B -Dtest=LifecycleActionServiceTest test
mvn -B -DskipTests package
git diff --check -- eci-manager
```

审查条件领取原子性、旧 token 防护、过期租约恢复和所有租约写入是否短事务。建议提交信息：`feat(eci-manager): lease lifecycle actions safely`。

---

### Task 3: DELETE 动作、非阻塞失败宽限与无限重试

**Files:**
- Create: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionDispatcher.java`
- Create: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionProcessor.java`
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionService.java`
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/config/AsyncConfig.java`
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/instance/EciInstanceService.java`
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/instance/EciInstanceRepository.java`
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/TaskLifecycleWatcher.java`
- Modify: `eci-manager/src/test/java/com/xjicloud/eci/lifecycle/TaskLifecycleWatcherUnitTest.java`
- Create: `eci-manager/src/test/java/com/xjicloud/eci/lifecycle/action/LifecycleActionDispatcherTest.java`
- Create: `eci-manager/src/test/java/com/xjicloud/eci/lifecycle/action/DeleteLifecycleActionProcessorTest.java`
- Create: `eci-manager/src/test/java/com/xjicloud/eci/lifecycle/action/DeleteLifecycleActionIT.java`

**Interfaces:**
- Produces: `void requestDelete(UUID instanceId, Instant notBefore)`，事务内幂等 upsert。
- Produces: DELETE phase `DELETE_REQUEST -> WAIT_DELETED -> SUCCEEDED`。
- Produces: `void completeInstanceTermination(UUID instanceId, UUID actionId, UUID leaseToken)`，同事务写 action 和实例。
- Produces: `void LifecycleActionProcessor.process(UUID actionId, UUID leaseToken)`；本阶段 dispatcher 只扫描 `actionType=DELETE`。

- [ ] **Step 1: 写失败宽限非阻塞 Red 测试**

将 20 个任务设为云 Failed，调用一次 `watcher.pollOnce()`；断言 20 个任务均已落 `FAILED`、均有 DELETE action，action 的 `nextAttemptAt` 等于固定 Clock 的 `now + grace`，并用超时断言整轮没有等待 grace。校验调用顺序为 fetch -> task save -> action insert；原 `Thread.sleep` 相关测试改为断言 action 截止时间，不弱化日志顺序断言。

```bash
mvn -B -Dtest=TaskLifecycleWatcherUnitTest test
```

- [ ] **Step 2: 实现 `requestDelete` 幂等事务**

首次请求创建 `DELETE/DELETE_REQUEST/READY`，`nextAttemptAt=notBefore`，并把非终态实例置 `STOPPING`。重复请求使用 `min(existing.nextAttemptAt, notBefore)`；`IN_PROGRESS` 不抢占租约，只记录更早时间供本次步骤结束后采用；`SUCCEEDED` 保持终态。

```java
@Transactional
public void requestDelete(UUID instanceId, Instant notBefore) {
    EciInstanceEntity instance = instanceRepository.findForUpdateById(instanceId)
            .orElseThrow(() -> new IllegalStateException("instance not found: " + instanceId));
    LifecycleActionEntity action = repository
            .findByInstanceIdAndActionType(instanceId, LifecycleActionType.DELETE)
            .orElseGet(() -> newDeleteAction(instanceId, notBefore));
    if (action.getStatus() != LifecycleActionStatus.SUCCEEDED
            && action.getNextAttemptAt().isAfter(notBefore)) {
        action.setNextAttemptAt(notBefore);
    }
    if (instance.getStatus() != InstanceStatus.TERMINATED) {
        instance.setStatus(InstanceStatus.STOPPING);
        instance.setUpdatedAt(clock.instant());
    }
    instanceRepository.save(instance);
    repository.save(action);
}
```

同一任务内实现并单测 `newDeleteAction(instanceId, notBefore)`：生成 UUID，设置 `DELETE/DELETE_REQUEST/READY`、`attemptCount=0`、`nextAttemptAt=notBefore`、`createdAt=updatedAt=clock.instant()`，其余租约/错误/完成字段为空。

在 `EciInstanceRepository` 增加 `@Lock(PESSIMISTIC_WRITE) Optional<EciInstanceEntity> findForUpdateById(UUID id)`；`requestDelete` 先锁实例行，再读写 action，使同一实例的并发请求串行化。唯一约束保留为数据库最后防线，不在已经发生约束异常、可能标记 rollback-only 的同一事务中继续读写。

- [ ] **Step 3: 写 DELETE processor Red 测试**

覆盖：无 cloudId 且无不确定 CREATE 直接终止；Delete 成功转 `WAIT_DELETED`；NotFound 完成；Describe Deleted 完成；连续两次异常后第三次成功；长期失败保持 `WAITING`；旧 lease token 无法提交结果；第 5 次和其后每 10 次打印 ERROR；`WAIT_DELETED` 超过 `deleteTimeoutSec` 只告警并继续轮询，不把 action 或实例标 FAILED。

```java
processor.process(actionId, leaseToken);
assertThat(reload(actionId).getStatus()).isEqualTo(WAITING);
assertThat(reload(actionId).getAttemptCount()).isEqualTo(1);
assertThat(reload(instanceId).getStatus()).isEqualTo(STOPPING);
```

- [ ] **Step 4: 写 dispatcher 拒绝恢复 Red 测试并实现有界投递**

断言一轮最多投递 batch-size 条 DELETE；执行器抛 `RejectedExecutionException` 时用当前 lease token 调 `releaseAfterDispatchFailure(..., now+1s, ...)`；某条领取/投递异常不阻断其余动作。

```java
@Scheduled(fixedDelayString = "${eci.lifecycle-action.dispatch-interval-ms:1000}")
public void scheduledDispatch() {
    dispatchOnce();
}
```

`dispatchOnce()` 以 `enabledTypes=Set.of(DELETE)` 查询到期 ID，逐条 claim，再用 `eciLifecycleExecutor.execute(() -> processor.process(actionId, leaseToken))` 投递。`AsyncConfig` 暴露 `ThreadPoolTaskExecutor` 或 `TaskExecutor`，拒绝时只释放对应 token。

- [ ] **Step 5: 实现 DELETE 单步状态机**

`DELETE_REQUEST` 每次只调用一次 `provider.delete(cloudId)`，成功后写 `WAIT_DELETED/WAITING`；`WAIT_DELETED` 每次只调用一次 `provider.describe(cloudId)`。错误增加 `attemptCount`、截断 `lastError` 到 1000 字符并按退避写回 `WAITING`；正常轮询用 `pollIntervalMs`，不增加失败次数。`WAIT_DELETED` 且 `now > action.createdAt + deleteTimeoutSec` 时记录超时 ERROR，但仍保持 WAITING；用固定 message marker 防止每轮重复打印，下一次真实云错误可覆盖 `lastError`。

无 cloudId 时按 CREATE 依赖判断：只有 CREATE 不存在，或 `CREATE_REQUEST` 从未尝试且未处于 `IN_PROGRESS`，或 CREATE 已明确 `FAILED/CANCELLED`，才可直接终止；未知结果或在途创建使 DELETE 回到 `WAITING`。

- [ ] **Step 6: 切换所有删除入口并移除 watcher sleep**

`EciInstanceService.delete`、`deleteForTaskCancel`、`recordCloudSucceededAndDeleteForTask` 改为调用 `requestDelete`，不再调 `AsyncLifecycleWorker.scheduleDelete`。`TaskLifecycleWatcher.failPersistThenDelete` 在日志和 FAILED 落库后直接以 `clock.instant().plusSeconds(failureLogGraceSec)` 请求删除，删除 `applyFailureGrace`。

- [ ] **Step 7: 运行 P0-1 聚焦测试和质量门**

```bash
mvn -B -Dtest=LifecycleActionDispatcherTest,DeleteLifecycleActionProcessorTest,DeleteLifecycleActionIT,TaskLifecycleWatcherUnitTest,TaskLifecycleWatcherTest test
mvn -B -DskipTests package
git diff --check -- eci-manager
```

审查失败日志顺序、20 任务非阻塞、DELETE 永不 `FAILED`、手动实例只在显式 DELETE 后清理，以及重复删除只提前不延后。建议提交信息：`fix(eci-manager): persist delete retries without blocking watcher`。

---

### Task 4: Provider ClientToken、Tag、幂等 Mock 与错误分类

**Files:**
- Create: `eci-manager/src/main/java/com/xjicloud/eci/provider/CloudOperationKind.java`
- Create: `eci-manager/src/main/java/com/xjicloud/eci/provider/CloudOperationException.java`
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/provider/EciProvider.java`
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/provider/AliyunEciProvider.java`
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/provider/MockEciProvider.java`
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/AsyncLifecycleWorker.java`
- Create: `eci-manager/src/test/java/com/xjicloud/eci/provider/AliyunEciProviderIdempotencyTest.java`
- Create: `eci-manager/src/test/java/com/xjicloud/eci/provider/MockEciProviderIdempotencyTest.java`

**Interfaces:**
- Changes: `String EciProvider.create(CreateSpec spec, String clientToken)`。
- Produces: `CloudOperationException.kind()` 与无凭据的安全 message。
- Keeps: `describe`/`delete` NotFound 仍按现有幂等语义返回。

- [ ] **Step 1: 写稳定 token 与 Mock 幂等 Red 测试**

同一 token 两次 create 必须返回相同 cloudId、Mock 云资源计数为 1；不同 token 返回不同 ID。Aliyun request 测试捕获 `CreateContainerGroupRequest`，断言 `clientToken` 与 Tag key/value。

```java
String first = provider.create(spec, localId.toString());
String second = provider.create(spec, localId.toString());
assertThat(second).isEqualTo(first);
assertThat(provider.getCloudInstanceCount()).isEqualTo(1);
```

- [ ] **Step 2: 运行 Provider Red 测试**

```bash
mvn -B -Dtest=AliyunEciProviderIdempotencyTest,MockEciProviderIdempotencyTest test
```

- [ ] **Step 3: 实现 Provider 新契约**

阿里云请求必须执行：

```java
request.setClientToken(clientToken);
CreateContainerGroupRequest.Tag tag = new CreateContainerGroupRequest.Tag();
tag.setKey("xjicloud-local-instance-id");
tag.setValue(clientToken);
request.setTags(List.of(tag));
```

Mock 使用 `ConcurrentHashMap<String, String> cloudIdByClientToken` 和原子映射创建，故障注入支持“云记录已创建但本次调用抛 `UNKNOWN_OUTCOME`”，下一次同 token 返回原 ID。

- [ ] **Step 4: 实现保守错误分类测试与代码**

测试明确参数/鉴权/配额拒绝映射为 `REJECTED`；网络、超时、服务端和未识别错误映射为 `UNKNOWN_OUTCOME`；Describe/Delete 非 NotFound 异常映射为 `RETRYABLE`。REJECTED 只能使用代码 allowlist，未命中一律保守处理。

```java
public final class CloudOperationException extends RuntimeException {
    private final CloudOperationKind kind;
    public CloudOperationKind kind() { return kind; }
}
```

异常 message 只包含 operation、云错误码和安全说明，不包含 AK/SK、请求对象或环境变量。

- [ ] **Step 5: 临时适配旧创建调用并运行回归**

在尚未删除的 `AsyncLifecycleWorker.scheduleCreate(localId, spec)` 中调用 `provider.create(spec, localId.toString())`，保证 Task 4 单独完成时仍可编译运行。

```bash
mvn -B -Dtest='*Provider*Test' test
mvn -B -DskipTests package
git diff --check -- eci-manager
```

审查 token 是否始终来自本地 UUID、Tag 是否不含敏感数据、错误分类是否默认保守、Mock 是否真实验证单资源幂等。建议提交信息：`feat(eci-manager): make cloud creation idempotent`。

---

### Task 5: CREATE 动作、超时后收敛与移除长轮询 Worker

**Files:**
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionService.java`
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionProcessor.java`
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/instance/EciInstanceService.java`
- Delete: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/AsyncLifecycleWorker.java`
- Modify: `eci-manager/src/test/java/com/xjicloud/eci/instance/EciInstanceServiceCompensationTest.java`
- Modify: `eci-manager/src/test/java/com/xjicloud/eci/instance/EciInstanceServiceRefreshTest.java`
- Create: `eci-manager/src/test/java/com/xjicloud/eci/lifecycle/action/CreateLifecycleActionProcessorTest.java`
- Create: `eci-manager/src/test/java/com/xjicloud/eci/lifecycle/LifecycleActionRecoveryIT.java`

**Interfaces:**
- Produces: `void createCreateAction(UUID instanceId)`，必须与实例 insert 同一事务。
- Produces: CREATE phase `CREATE_REQUEST -> WAIT_RUNNING -> SUCCEEDED|FAILED`。
- Produces: 从 `EciInstanceEntity` 持久化字段重建 `CreateSpec`，不读取可变默认配置。

- [ ] **Step 1: 写创建原子落库 Red 测试**

手动实例和任务实例提交后都断言同事务存在唯一 CREATE action，token 等于 instance UUID，phase/status 为 `CREATE_REQUEST/READY`；强制 action insert 抛异常时实例/任务事务整体回滚。

- [ ] **Step 2: 写 CREATE processor Red 测试**

覆盖：正常 create 得到 cloudId；WAIT_RUNNING 的 Running/Succeeded；云 Failed；明确 REJECTED；UNKNOWN_OUTCOME 同 token 重试；旧 token 结果拒绝；取消在 create 前把 action 置 CANCELLED；create 在途取消等待收敛；创建超时暴露 FAILED 但 action 继续；超时后取得 cloudId 立即请求 DELETE。

```java
provider.failAfterCreateOnce(clientToken, CloudOperationKind.UNKNOWN_OUTCOME);
processor.process(actionId, firstLease);
processor.process(actionId, secondLease);
assertThat(provider.getCreateTokens()).containsOnly(clientToken);
assertThat(provider.getCloudInstanceCount()).isEqualTo(1);
```

- [ ] **Step 3: 实现 CREATE 单步状态机**

`CREATE_REQUEST` 从实例 JSON/spec 字段重建 `CreateSpec` 并调用一次 `create(spec, clientToken)`；成功时同一短事务写 `cloudInstanceId/cloudStatus=Pending` 和 `WAIT_RUNNING/WAITING`。`WAIT_RUNNING` 一次 Describe 后：Running/Succeeded 正常意图写实例 RUNNING、action SUCCEEDED 并通知任务；Failed 写实例/任务 FAILED、action SUCCEEDED 后请求带 grace 的 DELETE；Pending 写下次正常轮询。

REJECTED 将 action 标 `FAILED`，实例与活动任务标 FAILED；UNKNOWN_OUTCOME 增加 attempt 并持续重试。超过 `createTimeoutSec` 时只将业务实例/任务标 FAILED 一次，CREATE action 仍 `WAITING`；后来拿到 cloudId 时不得恢复 RUNNING，必须创建/唤醒 DELETE。

- [ ] **Step 4: 切换实例创建事务**

`EciInstanceService.create` 和 `createForTask` 加 `@Transactional`，`createInternal` 在保存实例后直接 `lifecycleActionService.createCreateAction(id)`；移除 `scheduleCreateAfterCommit`、`TransactionSynchronizationManager` 和 worker 依赖。保留当前补偿代码所需的 `TransactionTemplate`，直到 Task 7 删除旧补偿实现；保留 DTO、默认值合并和完整规格落库行为。

- [ ] **Step 5: 删除长轮询 Worker 并修复构造测试**

先把 `EciInstanceService.compensateCreating/compensateStopping` 的 worker 调用改为 `ensureCreateAction` / `requestDelete`，并更新 `EciInstanceServiceCompensationTest` 断言 action service；这样当前启动 Runner 在 Task 7 完整改造前也只会恢复持久化动作。随后确认以下搜索无结果后删除文件：

```bash
rg -n "scheduleCreate|scheduleDelete|resumeCreatePoll|resumeDelete|AsyncLifecycleWorker|Thread\.sleep" eci-manager/src/main eci-manager/src/test
```

同时把 dispatcher 的 enabledTypes 从 `Set.of(DELETE)` 扩成 `Set.of(CREATE, DELETE)`。预期最终只允许测试辅助代码出现 sleep；主代码零命中。

- [ ] **Step 6: 运行 P0-2 聚焦测试和质量门**

```bash
mvn -B -Dtest=CreateLifecycleActionProcessorTest,LifecycleActionRecoveryIT,EciTaskServiceTransactionalTest,EciInstanceServiceRefreshTest test
mvn -B -DskipTests package
git diff --check -- eci-manager
```

审查事务原子性、完整规格重建、未知结果不终止、超时后资源不复活、一个 token 只对应一个云资源。建议提交信息：`fix(eci-manager): recover idempotent create actions after crashes`。

---

### Task 6: `CANCELLING -> CANCELLED` 状态与终态竞态

**Files:**
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/task/TaskStatus.java`
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/task/EciTaskRepository.java`
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/task/EciTaskService.java`
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/action/LifecycleActionProcessor.java`
- Modify: `eci-manager/src/test/java/com/xjicloud/eci/task/EciTaskApiTest.java`
- Modify: `eci-manager/src/test/java/com/xjicloud/eci/task/EciTaskServiceTerminalRaceTest.java`
- Modify: `eci-manager/src/test/java/com/xjicloud/eci/lifecycle/TaskLifecycleWatcherTest.java`
- Create: `eci-manager/src/test/java/com/xjicloud/eci/task/EciTaskCancellationIT.java`

**Interfaces:**
- Produces: `markCancellingIfActive(...)`，仅 `PENDING/STARTING/RUNNING -> CANCELLING`。
- Produces: `markCancelledIfCancelling(...)`，仅 `CANCELLING -> CANCELLED` 并设置 `endedAt`。
- Produces: `void onInstanceTerminated(UUID taskId, String cloudStatus)`，根据任务状态和成功证据收敛。

- [ ] **Step 1: 写 API 和状态竞态 Red 测试**

首次 cancel 返回 CANCELLING，重复 cancel 仍返回 CANCELLING；DELETE 完成后才返回 CANCELLED。覆盖 cancel 先提交时后续 Succeeded/Failed 不覆盖，Succeeded/Failed 先提交时 cancel 返回 409，以及无 cloudId 但 CREATE 未发出时可直接收敛 CANCELLED。

```bash
mvn -B -Dtest=EciTaskApiTest,EciTaskServiceTerminalRaceTest,EciTaskCancellationIT test
```

- [ ] **Step 2: 实现仓储条件更新**

将原 `markCancelledIfActive` 拆成两个明确查询。`markFailedIfActive`、`markSucceededIfActive` 的 active 集合继续只含 PENDING/STARTING/RUNNING，禁止覆盖 CANCELLING。

```java
int markCancellingIfActive(UUID id, TaskStatus cancelling, Instant now,
        Collection<TaskStatus> active);
int markCancelledIfCancelling(UUID id, TaskStatus cancelled, Instant now,
        TaskStatus cancelling);
```

- [ ] **Step 3: 实现 cancel 与删除完成收敛**

`cancel` 在同一事务中先条件写 CANCELLING、清空旧 `endedAt`，再 `requestDelete(instanceId, now)`；CANCELLING/CANCELLED 重复调用直接返回当前状态；SUCCEEDED/FAILED 返回 409。DELETE processor 将实例和 action 写终态后调用 `onInstanceTerminated`：CANCELLING -> CANCELLED；活动任务且 `cloudStatus=Succeeded` -> SUCCEEDED；FAILED 保持不变。

- [ ] **Step 4: 移除内存成功门闩**

删除 `TaskLifecycleWatcher.pendingSucceeded`。TERMINATED 分支只根据实例行 `cloudStatus=Succeeded` 调 `markSucceeded`，否则按现有中断原因失败；CANCELLING 不在 watcher 扫描集合中。

- [ ] **Step 5: 运行 P0-3 测试与质量门**

```bash
mvn -B -Dtest=EciTaskApiTest,EciTaskServiceTerminalRaceTest,EciTaskCancellationIT,TaskLifecycleWatcherTest,TaskLifecycleWatcherUnitTest test
mvn -B -DskipTests package
git diff --check -- eci-manager
```

审查 `CANCELLED` 是否始终有 TERMINATED 证据、cancel 是否幂等、终态条件更新是否互斥、`endedAt` 时点是否正确。建议提交信息：`fix(eci-manager): finalize cancellation after resource deletion`。

---

### Task 7: 启动补偿、旧数据迁移与租约恢复

**Files:**
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/lifecycle/StartupCompensationRunner.java`
- Modify: `eci-manager/src/main/java/com/xjicloud/eci/instance/EciInstanceService.java`
- Delete: `eci-manager/src/main/java/com/xjicloud/eci/instance/CompensationOutcome.java`
- Modify: `eci-manager/src/test/java/com/xjicloud/eci/instance/EciInstanceServiceCompensationTest.java`
- Modify: `eci-manager/src/test/java/com/xjicloud/eci/lifecycle/StartupCompensationRunnerTest.java`
- Create: `eci-manager/src/test/java/com/xjicloud/eci/lifecycle/action/StartupLifecycleActionCompensationIT.java`

**Interfaces:**
- Produces: `recoverExpiredLeases(Instant now)`。
- Produces: `ensureLegacyActionsForInstance(UUID instanceId)`，每条独立事务。
- Produces: `migrateLegacyCancelledTask(UUID taskId)`，未清理实例改为 CANCELLING 并补 DELETE。

- [ ] **Step 1: 写旧数据补偿 Red 测试**

覆盖设计 §10 全部七条：过期 IN_PROGRESS；无 action 的 CREATING 无/有 cloudId；STOPPING；任务关联 FAILED 且有 cloudId；手动 FAILED 不自动删；旧 CANCELLED + 非 TERMINATED 回退 CANCELLING 并清空 endedAt。另造一条坏外键数据，断言不阻断后续正常记录。

- [ ] **Step 2: 实现逐条短事务补偿**

Runner 顺序固定：恢复过期租约 -> 扫实例补 action -> 迁移旧取消任务 -> 原任务证据收敛。扫描方法只返回 UUID；每个 UUID 调代理 service 的 `@Transactional(REQUIRES_NEW)` 方法，单条异常只记不含凭据的 warn。

```java
for (UUID id : compensationService.findCandidateInstanceIds()) {
    try {
        compensationService.ensureLegacyActionsForInstance(id);
    } catch (RuntimeException e) {
        log.warn("startup lifecycle compensation failed instanceId={}", id, e);
    }
}
```

- [ ] **Step 3: 删除旧 worker 补偿语义**

无 cloudId 的 CREATING 不再标“进程中断，创建未提交到云”，而是补 `CREATE_REQUEST` 并沿用 UUID token；有 cloudId 补 `WAIT_RUNNING`。STOPPING/任务 FAILED 补 DELETE。手动 FAILED 只有已有 DELETE action 才继续清理。

- [ ] **Step 4: 运行补偿测试和质量门**

```bash
mvn -B -Dtest=StartupLifecycleActionCompensationIT,StartupCompensationRunnerTest,EciInstanceServiceCompensationTest test
mvn -B -DskipTests package
git diff --check -- eci-manager
```

审查旧 CANCELLED 迁移不会错误删除已 TERMINATED 记录、手动 FAILED 不被任务规则清理、坏数据隔离和租约恢复。建议提交信息：`fix(eci-manager): rebuild lifecycle actions during startup`。

---

### Task 8: 操作台、文档与全量回归

**Files:**
- Modify: `eci-manager/src/main/resources/static/console/console.js`
- Modify: `eci-manager/src/test/java/com/xjicloud/eci/config/ConsoleStaticResourceTest.java`
- Modify: `eci-manager/docs/API文档.md`
- Modify: `eci-manager/docs/设计与使用说明.md`
- Modify: `eci-manager/docs/测试说明.md`
- Modify: `eci-manager/README.md`
- Modify: `eci-manager/AGENT_CONTEXT.md`

**Interfaces:**
- Consumes: Tasks 1-7 的最终状态机和配置。
- Produces: 操作台将 CANCELLING 视为非终态、显示“正在清理”、禁用重复 cancel，但继续轮询。
- Produces: 文档中的状态、配置、失败宽限和重启恢复语义与代码一致。

- [ ] **Step 1: 写操作台 Red 测试**

在 `ConsoleStaticResourceTest` 读取脚本并断言 terminal 集合不含 CANCELLING、状态文案含“正在清理”，且选中 CANCELLING 任务时 cancel 按钮禁用但 `pollLoop` 继续调用详情接口。

- [ ] **Step 2: 实现最小操作台状态适配**

保留 `TERMINAL = { SUCCEEDED, FAILED, CANCELLED }`；新增状态 label 映射，CANCELLING 显示“正在清理”；按钮禁用条件改为 terminal 或 CANCELLING，轮询停止条件仍只看 terminal。

- [ ] **Step 3: 更新文档**

记录：

- cancel 首次响应通常为 CANCELLING，CANCELLED 保证实例 TERMINATED/NotFound。
- `eci.lifecycle-action.*` 六个配置及默认值。
- `failure-log-grace-sec` 现在是持久化 not-before，不阻塞 watcher。
- CREATE 使用 UUID ClientToken 和 `xjicloud-local-instance-id` Tag。
- `delete-timeout-sec` 只控制从 DELETE 动作登记起的超时告警，清理不会停止；`create-timeout-sec` 只控制业务失败暴露，未知 CREATE 仍后台恢复。
- Mock 崩溃、删除重试、取消收敛的本地验证步骤；真云验证仍标为需授权。

- [ ] **Step 4: 运行完整自动化测试**

```bash
cd eci-manager
source ../.tools/env.sh
mvn -B test
```

预期：全部测试通过；日志中没有未捕获 scheduler 异常、`NullPointerException` 或真实云请求。记录总测试数、失败数和耗时。

- [ ] **Step 5: 运行静态检查和范围审计**

```bash
rg -n "Thread\.sleep|AsyncLifecycleWorker|pendingSucceeded|markCancelledIfActive" eci-manager/src/main
rg -n "CANCELLING|lifecycle-action|ClientToken|xjicloud-local-instance-id" eci-manager/src/main eci-manager/docs eci-manager/README.md eci-manager/AGENT_CONTEXT.md
git diff --check -- eci-manager docs/superpowers
git status --short
git diff -- eci-manager docs/superpowers/specs/2026-09-21-eci-manager-p0-lifecycle-reliability-design.md docs/superpowers/plans/2026-09-21-eci-manager-p0-lifecycle-reliability.md
```

第一条预期主代码零命中；第二条应覆盖枚举、实现、配置与文档；diff 不得包含主后端、web2、admin、Worker、Spark 或 Rust 的新增改动。

- [ ] **Step 6: 独立最终 Code Review**

按严重度检查：孤儿云资源窗口、旧租约覆盖、CREATE/DELETE 竞态、取消终态真实性、失败日志顺序、Secret 泄露、测试是否事后补写。High/Critical 必须在最多 3 轮内修复，否则停止并向用户报告。建议提交信息：`docs(eci-manager): document persistent lifecycle recovery`。

## Final Acceptance

- [ ] 20 个失败任务同一 watcher 轮次均登记 DELETE，且无 grace sleep。
- [ ] Delete 前两次失败、第三次成功后实例最终 TERMINATED，action SUCCEEDED。
- [ ] Create 成功后模拟落库前崩溃，恢复使用相同 token，Mock 云资源总数为 1。
- [ ] 未知 CREATE 超时可暴露 FAILED，但最终取得 cloudId 后会自动删除。
- [ ] cancel 返回 CANCELLING，只有实例 TERMINATED/NotFound 后返回 CANCELLED。
- [ ] Succeeded/Failed/cancel 三方竞态均由条件更新测试覆盖。
- [ ] 启动补偿覆盖过期租约和全部旧数据分支，一条坏数据不阻断其余记录。
- [ ] `mvn -B test` 全绿；完成独立语义审查；没有执行真云操作、commit 或 push。
