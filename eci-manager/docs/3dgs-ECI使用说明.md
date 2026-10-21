# 3DGS 真云 ECI 使用说明

命令中的 `/path/to/XJICloud` 请替换为实际仓库路径；运行前需安装 JDK 17 和 Maven 3.9+。

> **适用：** `eci-manager/`（默认 `http://127.0.0.1:8081`）  
> **主路径：** [`scripts/submit-3dgs-standard.sh`](../scripts/submit-3dgs-standard.sh)  
> **容器引导：** [`scripts/3dgs-bootstrap.py`](../scripts/3dgs-bootstrap.py)  
> **费用：** GPU ECI 按量计费；任务终态后服务会自动删实例，仍请到[弹性容器实例控制台](https://eci.console.aliyun.com/)确认无残留。

一次出一张图 = **提交一个任务** = **开一个 GPU 容器**：下载 zip → prepare → COLMAP → gsplat 训练 → PUT ply → 退出。中间产物在容器 ephemeral 盘上，**进程退出前必须把 PLY 写回 OSS**。

**已验证：** 标准档 `MAX_STEPS=15000`、建筑「新文科楼」、镜像 `:1.0.0` + 镜像缓存，任务可 `SUCCEEDED`，产物在 `3dgs/exports/新文科楼.ply`。

---

## 1. 前置条件

### 1.1 目录与资源

| 项 | 值 |
|----|-----|
| 工程 | `/path/to/XJICloud/eci-manager` |
| 地域 | `cn-hangzhou`（ACR / ECI / OSS 一致） |
| 镜像 | 用 `GS_IMAGE` 指定可由目标 VPC 拉取的 3DGS 镜像，固定版本 tag；镜像须提供 `/opt/3dgs/docker/entrypoint.sh shell` 入口 |
| 镜像缓存 | 用 `IMAGE_SNAPSHOT_ID` 指定目标账号、同地域可用的缓存 ID；脚本中的联调默认值不能直接复用于其他环境 |
| 规格（默认） | `ecs.gn7i-c16g1.4xlarge`（A10 24GB），`cpu=16` `memory=60` **`gpu=1`**，`shmSize=8Gi` |
| OSS 输入 | `oss://<ECI_OSS_BUCKET>/3dgs/raw/<建筑名>.zip` |
| OSS 输出 | `oss://<ECI_OSS_BUCKET>/3dgs/exports/<建筑名>.ply` |

zip 结构：一层目录，例如 `新文科楼/image*.JPG`，不要嵌套成 `新文科楼/新文科楼/`。

### 1.2 `.env`（真云）

```bash
cd /path/to/XJICloud/eci-manager
# 若无 .env：cp .env.example .env 后填写
```

至少确认：

| 变量 | 要求 |
|------|------|
| `SPRING_PROFILES_ACTIVE` | `cloud,local` |
| `ECI_API_KEY` | 与请求头 `X-Api-Key` 一致 |
| `ECI_ACCESS_KEY_ID` / `ECI_ACCESS_KEY_SECRET` | 可选的首次 ECI 凭据种子；后续通过 8080 选用并推送当前钥，或使用 RAM 角色 |
| `ALIBABA_CLOUD_ACCESS_KEY_ID` / `SECRET` | 仅 `load-env.sh` 映射到上一行；Java 不读 |
| `ECI_REGION_ID` / `ECI_VSWITCH_ID` / `ECI_SECURITY_GROUP_ID` | 创建容器网络 |
| `ECI_OSS_ENABLED` | `true` |
| `ECI_OSS_ENDPOINT` | **内网** `https://oss-cn-hangzhou-internal.aliyuncs.com`（容器无 NAT 时公网会超时） |
| `ECI_OSS_BUCKET` | 自己的目标 bucket |
| `ECI_OSS_ACCESS_KEY` / `ECI_OSS_SECRET_KEY` | 可选的首次 OSS 凭据种子；通常由 8080 选用 `OSS` 后推送，凭据只留在服务端，不会注入容器 |
| `ECI_OSS_PRESIGN_EXPIRATION_MINUTES` | **1440**（默认 60 分钟不够训完；PUT 必须活到训练结束） |
| `ECI_CREATE_TIMEOUT_SEC` | 建议 **1800**（大镜像首次拉取常超过默认 300 秒） |

改过 `.env` 必须重启 eci-manager。种子只在库中 current 行不存在时写入；若已 `PUT /runtime-credentials` 热切过，重启后仍用库里的当前钥。可 `GET /api/v1/eci/runtime-credentials` 看 hint（无 SK）。8081 没有多钥目录。

8080 的连接配置、Admin 中的测试与选用步骤见 [README 密钥配置](../README.md#配置密钥与真云环境)。

### 1.3 启动服务

**终端 A**（保持运行）：

```bash
cd /path/to/XJICloud/eci-manager
if [ -f ../.tools/env.sh ]; then source ../.tools/env.sh; fi    # 若需 JDK/Maven
./scripts/start.sh
```

期望日志含：`profiles are active: "cloud", "local"`、`ECI Aliyun client ready`、Tomcat `8081`；OSS 未出现意外 `skipped`。

**终端 B** 健康检查：

```bash
cd /path/to/XJICloud/eci-manager
source scripts/load-env.sh
curl -s -w "\nHTTP:%{http_code}\n" "$ECI_BASE/actuator/health"
```

期望：`HTTP:200`，体含 `"status":"UP"`。

---

## 2. 把原图放到 OSS

使用有目标 bucket 写入权限的账号登录 OSS 控制台，将准备好的 ZIP 上传到 `ECI_OSS_BUCKET` 指定的 bucket，目标对象 key 为 `3dgs/raw/<建筑名>.zip`。本地 ZIP、对象 key 中的建筑名和提交时的 `BUILDING` 应一致。

也可用已配置独立上传凭据的 OSS 客户端上传。8080 推送给 eci-manager 的当前钥只供服务端使用，本机上传工具无法从该流程取得 Secret；上传工具需要自己的登录身份或上传凭据。

上传后核对对象 key 和文件大小，再提交训练。容器通过服务签发的预签名 URL 下载 ZIP，无需在容器中配置上传账号的密钥。

---

## 3. 一键提交

```bash
cd /path/to/XJICloud/eci-manager
export GS_IMAGE='你的可达的3DGS镜像地址'
export IMAGE_SNAPSHOT_ID='imc-你的同地域镜像缓存ID'
export MAX_STEPS=15000
./scripts/submit-3dgs-standard.sh
```

脚本会组装 `POST /api/v1/eci/tasks`（注入 `3dgs-bootstrap.py`）、打印 `TASK_ID` 并轮询状态。默认最多轮询约 5 小时；达到轮询上限或退出脚本不会取消云端任务，脚本退出码也不代表训练成功。应检查任务终态和 `cleanupConfirmed`，必要时继续查询或主动取消。

### 3.1 默认参数

| 项 | 默认 |
|----|------|
| `BUILDING` | `新文科楼` |
| `MAX_STEPS` | `2000`；上面的标准档示例显式覆盖为 `15000` |
| `DATA_FACTOR` | `1` |
| `PACKED` / `ANTIALIASED` | `1` / `1` |
| `MAX_LONG_EDGE` | `2048` |
| `MAX_RUNTIME_SEC` | `21600`（6 小时） |
| `SHM_SIZE` | `8Gi`（POST `shmSize`；Memory EmptyDir 挂 `/dev/shm`） |
| `IMAGE_SNAPSHOT_ID` | 脚本带有特定环境的联调默认值，交付使用时须显式覆盖 |
| 轮询 | `POLL_SLEEP_SEC=15`，最多约 5 小时（`POLL_MAX=1200`） |

### 3.2 常用覆盖

```bash
# 换建筑（OSS 须已有 3dgs/raw/<名>.zip）
BUILDING=某楼 ./scripts/submit-3dgs-standard.sh

# 短测（约十余分钟量级，含 COLMAP + 首次 CUDA 编译）
MAX_STEPS=100 ./scripts/submit-3dgs-standard.sh

# 覆盖 shm（须小于任务 memory=60）
SHM_SIZE=16Gi ./scripts/submit-3dgs-standard.sh

# 后台跑
POLL_SLEEP_SEC=20 nohup ./scripts/submit-3dgs-standard.sh > /tmp/eci_3dgs_run.log 2>&1 &
tail -f /tmp/eci_3dgs_run.log
```

可选限 densify（显存/磁盘更紧时）：

```bash
export EXTRA_TRAIN_ARGS='--strategy.refine-stop-iter 8000'
./scripts/submit-3dgs-standard.sh
```

### 3.3 取消

```bash
source scripts/load-env.sh
curl -s -X POST "$ECI_BASE/api/v1/eci/tasks/<TASK_ID>/cancel" \
  -H "X-Api-Key: $ECI_API_KEY"
```

---

## 4. 进度、成功与失败

### 4.1 任务状态

```bash
source scripts/load-env.sh
curl -s "$ECI_BASE/api/v1/eci/tasks/<TASK_ID>" -H "X-Api-Key: $ECI_API_KEY"
```

| 状态 | 含义 |
|------|------|
| `STARTING` / `RUNNING` | 创建中或容器在跑 |
| `SUCCEEDED` | 容器 exit 0，实例将/已删除 |
| `FAILED` / `CANCELLED` | 失败或已取消；失败时看下方取证字段 |

关联实例：`POST .../instances/<INST_ID>/refresh` 可看云侧 `Running` / `Succeeded` / `Failed`。

### 4.2 日志为什么「看起来卡住」

云控制台 / `DescribeContainerLog` 里常见假象：

1. **`ensuring CUDA extension is compiled...` 数分钟无新行**  
   首次编译 CUDA 扩展正常，实测约 **5–6 分钟**（例如 ~350s）后才会出现 `CUDA extension ready`。

2. **进度条一直显示 `0%| … 0/15000`**  
   tqdm 用 `\r` 原地刷新，阿里云日志 Tail 常常只留下一行开头。任务仍是 `RUNNING` 时，**不等于**卡在第 0 步。可看换行里程碑（COLMAP QC、`Model initialized`、中段 **7500** eval/save、最终 ply/PUT）。

eci-manager **成功路径不落库** `containerLogTail`（防膨胀）；**没有**实时「训练进度 %」API。进度需看阿里云容器日志，或自行用 OpenAPI `DescribeContainerLog` 解析 tqdm 中的 `step/total`。

### 4.3 成功验收

1. 任务 `status=SUCCEEDED`  
2. OSS 存在 `3dgs/exports/<建筑名>.ply` 且大小合理（标准 15k 常为数十 MiB 量级，视场景而定）  
3. 实例 `TERMINATED`；控制台无残留 GPU 容器组  

### 4.4 失败取证

云侧 Failed / 任务 TIMEOUT 时，eci-manager 顺序为：

**拉容器日志 → 写入任务 `containerLogTail` →（可选宽限 `eci.failure-log-grace-sec`，默认 90s）→ 删实例**

```bash
curl -s "$ECI_BASE/api/v1/eci/tasks/<TASK_ID>" -H "X-Api-Key: $ECI_API_KEY" \
  | python3 -c "import json,sys;d=json.load(sys.stdin)['data'];print(d['status']);print(d.get('failureReason'));print((d.get('containerLogTail') or '')[-3000:])"
```

- `failureReason`：短文案（含 exitCode/events 摘要时更有用）  
- `containerLogTail`：删前日志尾部（成功时为 `null`）

---

## 5. 注意事项（已踩坑）

| 点 | 说明 |
|----|------|
| 一任务一容器 | 不能拆成「这个容器 prepare、下一个 colmap」；下一容器本地盘是空的 |
| Presign 时长 | `ECI_OSS_PRESIGN_EXPIRATION_MINUTES=1440`；训到一半链接过期会导致 PUT 失败 |
| OSS endpoint | 容器侧必须用 **内网**；本机上传用 **公网** |
| `/dev/shm` | ECI 默认约 64MiB。创建任务传 `shmSize`（标准档 `8Gi`）挂 Memory EmptyDir 到 `/dev/shm`。Memory 计入实例内存，须小于 `memory`。启动日志 `df -h /dev/shm` 应约为 8G，且不应再出现 `DataLoader num_workers=0 (ECI shm)` |
| 临时盘 | 默认 ephemeral 约 30GiB。大场景 + 长训 densify 后 `results/` 很大，后期可能 ENOSPC；必要时提高规格临时盘或缩短步数 / 限 densify |
| 显存 | A10 24GB + `PACKED=1` 为当前默认；极长训 densify 仍可能顶满显存导致 Failed |
| 镜像 | 固定 `:1.0.0` + Ready 的镜像缓存，避免每次在 GPU 上解压大镜像 |
| `gpu: 1` | 指定 GPU 规格时必须声明，否则创建失败 |
| 计费 | 终态后确认控制台无残留；不要依赖「以为删了」 |

API 字段说明见 [`API文档.md`](./API文档.md)（`storage`、`containerLogTail`）。服务配置与 OSS 见 [`设计与使用说明.md`](./设计与使用说明.md)。

---

## 6. 流程速查

```text
准备 .env → start.sh
    → 公网上传 3dgs/raw/<建筑>.zip
    → ./scripts/submit-3dgs-standard.sh
    → 轮询至 SUCCEEDED
    → 下载 3dgs/exports/<建筑>.ply
    → 控制台确认无残留 ECI
```
