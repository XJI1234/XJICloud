# 3DGS 成功案例上下文 & eci-manager 失败日志可观测性

> **日期：** 2026-09-02  
> **目的：** 固化「手动 Shell 标准档已跑通」的事实与参数，并定义下一步 **失败不因自动杀容器而丢日志** 的优化上下文。  
> **范围：** `eci-manager/`（不涉及主仓 `backend/` 对接）。  
> **相关：** [`3dgs真云ECI实测记录.md`](./3dgs真云ECI实测记录.md)（2026-09-01 POST 97% 失败）、[`3dgs-ECI-Shell手动跑通.md`](./3dgs-ECI-Shell手动跑通.md)、[`scripts/submit-3dgs-standard.sh`](../scripts/submit-3dgs-standard.sh)、[`scripts/3dgs-bootstrap.py`](../scripts/3dgs-bootstrap.py)

---

## 1. 本次成功案例（2026-09-02）

### 1.1 结论

| 项 | 结果 |
|---|---|
| 路径 | **手动 Shell**（控制台创建 + Workbench），**不经** `POST /api/v1/eci/tasks` |
| 质量档 | **标准档**：`MAX_STEPS=15000`，`DATA_FACTOR=1`，`PACKED=1`，`ANTIALIASED=1` |
| 产出 | `/data/exports/新文科楼.ply` → OSS → **SuperSplat 可打开** |
| 含义 | 镜像 `:1.0.0`、COLMAP、gsplat、内网 OSS、GPU 规格、镜像缓存 **均已验证可用** |

### 1.2 锁定参数（与 Shell 文档一致）

| 项 | 值 |
|---|---|
| 建筑名 | `新文科楼` |
| OSS 输入 | `oss://eci-manager-bucket/3dgs/raw/新文科楼.zip` |
| OSS 输出 | `oss://eci-manager-bucket/3dgs/exports/新文科楼.ply` |
| 镜像 | `…/ylif4/3dgs:1.0.0`（VPC） |
| 镜像缓存 | `imc-bp15yiuttnxf8fh9n9fy`（Ready，80 GiB，K 区） |
| 规格 | `ecs.gn7i-c16g1.4xlarge`，gpu=1，16 vCPU / 60 GiB |
| 地域 / 可用区 | `cn-hangzhou` / **cn-hangzhou-k** |
| 交换机 / 安全组 | `vsw-bp17apvyc28lj2dhwftw4` / `sg-bp19fa014hngv440ach4` |
| 容器保活（手动） | `entrypoint.sh` + `shell sleep infinity` |
| OSS | 本机上传公网；容器 GET/PUT **内网** endpoint 预签名 |

### 1.3 本机工具链（WSL）

| 项 | 说明 |
|---|---|
| 系统 Python | Ubuntu 24.04 PEP 668，不能直接 `pip install --user` |
| 方案 | Miniconda → `~/miniconda3`，环境 **`eci-oss`**（Python 3.12 + `oss2`） |
| 预签名脚本 | `scripts/oss-presign-3dgs.py` + `scripts/run-eci-oss.sh` |
| 坑 | 预签名 URL 含 `&`，Workbench 里 **必须单引号** 包整段，否则 403 / malformed |

### 1.4 手动路径踩坑（已解决，POST 仍需防）

| 现象 | 根因 | 处置 |
|---|---|---|
| GET/PUT 403 | Shell 把 URL 在 `&` 处截断 | `export PRESIGN_*='整段URL'` |
| train 100% 后 `cp: cannot stat '[eci-shm]...ply'` | §6.2 `sitecustomize` **stdout** 污染 `run_gsplat_train.sh` 路径捕获 | 手动 `cp` `results/.../point_cloud_*.ply` → `exports/`；文档已改 print→stderr；**bootstrap 已加 fallback** |
| 通档 densify 仍触发 | `EXTRA_TRAIN_ARGS` 可能未 export / 未透传 | 标准档可 `unset` 或显式 `refine-stop-iter` |

### 1.5 已落地的代码/脚本（为 POST 对齐）

| 产物 | 作用 |
|---|---|
| `scripts/submit-3dgs-standard.sh` | POST 一笔与手动标准档对齐的任务（`PACKED=1`、`ANTIALIASED=1`、镜像缓存默认） |
| `scripts/3dgs-bootstrap.py` | train 非 0 或 exports 缺失时，从 `results/.../point_cloud_*.ply` **复制最高 step 再 PUT** |
| Shell 文档 §6.2 | shm 补丁日志改 **stderr**，避免污染 `cp` |

> **注意：** 标准档 **POST 真云验收** 在写本文时可能尚未跑完；成功证据目前主要来自 **手动 Shell + SuperSplat**。POST 应用同一 env 再验一笔。

---

## 2. 对比：为何「后端 POST」曾经失败且丢信息

### 2.1 不是算力不行

同一镜像、同规格下，手动 Shell 已训完 15k 并出 ply。POST 失败属于 **编排 / 容错 / 可观测性** 问题。

### 2.2 2026-09-01 两笔 POST（摘自实测记录）

- 训到约 **97%（~14530–14550 / 15000）** → 云侧 **Failed**
- `failureReason` 近似 **`云侧状态: Failed`**（无 traceback）
- watcher **立刻删实例** → 无法 `DescribeContainerLog` → OSS ply **404**
- 假说：ephemeral 盘约 30 GiB 写满、后期 OOM、或无栈进程杀；**因删实例过快无法取证**

### 2.3 手动 vs POST 差异（与日志相关）

| | 手动 Shell | POST + bootstrap + watcher |
|--|-----------|---------------------------|
| 失败后 | 人还在容器里，可查 `nvidia-smi`、日志、手动 PUT | **秒删**，控制台日志页也没了 |
| 任务侧记录 | 无 | `failureReason` 很短，常无容器 stdout |
| 导出 | 可手 `cp` | 旧 bootstrap 仅 exit 0 才 PUT（已改进 fallback） |

### 2.4 当前代码路径（丢日志的直接原因）

`EciProvider` **只有** `create` / `describe` / `delete`，**没有**拉取容器日志的 API。

`TaskLifecycleWatcher` 在云侧 Failed 时大致：

1. `onInstanceFailed(taskId, reason)` — reason 多为 `view.statusDetail()` 或 `"云侧状态: Failed"`
2. **`ensureDelete(instance)`** — 异步删云实例

**没有**「删之前拉 Tail 日志并落库 / 写 OSS」的步骤。  
实例删除后，阿里云控制台「容器日志」与 `DescribeContainerLog` 都不可用 → **根因永久丢失**。

这与实测记录 §5.5 已写的对策一致，但 **尚未实现**。

---

## 3. 优化目标：失败日志管理（产品意图）

### 3.1 要解决的问题

> **任务失败（或超时）时，即使容器被自动删除，仍能拿到足够诊断信息，而不依赖人在几秒内打开控制台。**

### 3.2 成功标准（建议）

1. 任意任务进入 `FAILED` / `TIMEOUT` 后，通过 **`GET /api/v1/eci/tasks/{id}`**（或附属资源）能看到：
   - 原有短 `failureReason`
   - **容器日志尾部**（例如最后 N 行或最后 M KB）
2. 日志在 **调用云 Delete 之前** 已持久化（DB / 本地文件 / OSS 任一，见方案）
3. 成功任务可选：不存全量日志，或只存摘要，避免磁盘膨胀
4. Mock 模式可测「先抓日志再删」顺序，不依赖真云

### 3.3 非目标（本期可砍）

- 实时流式日志 UI / WebSocket
- 替代阿里云控制台完整日志检索
- 主仓 `backend/` 对接
- 自动解析 OOM vs ENOSPC（可二期用规则扫日志）

---

## 4. 方案草案（实现前需选型）

### 方案 A — 删前拉 Tail，写入 `failureReason` / 新字段（推荐 MVP）

| | |
|--|--|
| 做法 | `EciProvider` 增加 `fetchContainerLog(cloudId, tailLines)`（阿里云 `DescribeContainerLog`）；Watcher 在 `ensureDelete` **之前**调用；截断后写入任务表新列（如 `containerLogTail`）或拼进 `failureReason`（有长度上限风险） |
| 优点 | 改动面小；查询任务即可看；与现有 API 一致 |
| 缺点 | DB 行变大；tqdm `\r` 仍可能难读；需配置 tail 行数与截断 |
| 工作量 | 小～中 |

### 方案 B — 删前拉日志，PUT 到 OSS

| | |
|--|--|
| 做法 | 日志落到 `oss://…/eci-logs/{taskId}/container.log`；任务响应带 `logObjectKey` |
| 优点 | 可存更长；不撑爆 DB |
| 缺点 | 依赖 OSS；失败时 OSS 本身也可能有问题；要鉴权与生命周期 |
| 工作量 | 中 |

### 方案 C — 延迟删除（保留取证窗口）

| | |
|--|--|
| 做法 | Failed 后延迟 N 分钟再删（配置项）；期间人可 Workbench / 控制台看日志 |
| 优点 | 实现简单；适合调试周 |
| 缺点 | **GPU 继续计费**；不适合生产默认；仍不自动落库 |
| 工作量 | 小 |

### 推荐组合

**MVP：A（必做）+ 可选短延迟 C（如 60–120s，仅 `eci.failure-log-grace-sec`）**。  
长训任务再考虑 B 存全量。

### 实现时注意点（来自本次踩坑）

1. **顺序硬约束：** `fetchLog` → 落库 → 再 `delete`；落库失败也应尽力写本地服务日志，再删或延迟删。  
2. **Never 策略**下 Failed 立即删是现状；抓日志必须插在这条路径上（含 TIMEOUT）。  
3. tqdm `\r`：抓 log 时尽量要 **较大 Tail**（如 500–2000 行）或按字节；文档说明控制台同样难看。  
4. bootstrap 侧可补强：关键阶段 `log()` 已有；训练结束/异常时 **显式 print traceback 到 stderr** 并 `flush`，提高 Tail 命中率。  
5. `PYTHONUNBUFFERED=1` 建议打进标准 POST env（submit 脚本可加）。

---

## 5. 建议改动落点（供写 plan）

| 层 | 文件 / 点 | 改动方向 |
|----|-----------|----------|
| Provider | `EciProvider` + `AliyunEciProvider` + `MockEciProvider` | `fetchContainerLog` |
| Watcher | `TaskLifecycleWatcher` Failed / TIMEOUT 分支 | 删前抓日志 |
| 任务模型 | `EciTaskEntity` / `TaskResponse` | 新字段或扩展 `failureReason` 策略 |
| 配置 | `EciProperties` | `failure-log-tail-lines`、`failure-log-grace-sec` |
| 测试 | Watcher 单测 | 断言 **先 log 后 delete** 顺序 |
| 文档 | API文档、AGENT、本文件 | 验收清单 |

---

## 6. 验收清单（日志优化做完后）

```text
[ ] 真云或 Mock：任务 Failed 时，delete 调用前必有 fetchContainerLog
[ ] GET task 可见日志尾部（或 OSS key）
[ ] 实例已删后，仍能从任务记录读到该尾部
[ ] TIMEOUT 路径同样抓日志
[ ] 成功路径行为明确（不存 / 短摘要 / 可选）
[ ] 单测锁定「先抓后删」顺序
[ ] 用 submit-3dgs-standard.sh 或故意失败镜像做一次真云冒烟
```

---

## 7. 会话索引（便于回溯）

| 主题 | 要点 |
|------|------|
| 本机 conda | 无系统 conda → 装 Miniconda + `eci-oss` + oss2 |
| presign | 必须本机（或 eci-manager）签内网 URL；Shell 里单引号 |
| 通档 / 标准档 | 通档验证链路；标准 15k + SuperSplat 验收画质/格式 |
| POST vs Shell | Shell 已证明算法；POST 缺失败可观测性与（曾）导出脆弱 |
| 监控 | POST Running 期间可用 Workbench 旁观；主日志在控制台「容器日志」；删后全无 |

---

## 8. 下一步（待你确认）

实现日志管理前请选定：

1. **MVP 是否采用方案 A**（删前 Tail → 任务字段）？  
2. 是否叠加 **短延迟删除**（方案 C，默认多少秒）？  
3. `failureReason` 内嵌截断 vs **独立字段** `containerLogTail`？

确认后可再写 `docs/superpowers/plans/…-eci-failure-log-retention.md` 并改代码。
