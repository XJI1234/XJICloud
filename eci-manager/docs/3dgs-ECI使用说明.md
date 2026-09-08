# 3DGS 真云 ECI 使用说明

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
| 工程 | `~/XJI/XJICloud/eci-manager` |
| 地域 | `cn-hangzhou`（ACR / ECI / OSS 一致） |
| 镜像 | `crpi-urdof1biefqsi8uj-vpc.cn-hangzhou.personal.cr.aliyuncs.com/ylif4/3dgs:1.0.0`（**固定 tag**，勿用 `:latest`） |
| 镜像缓存（默认） | `imc-bp15yiuttnxf8fh9n9fy` |
| 规格（默认） | `ecs.gn7i-c16g1.4xlarge`（A10 24GB），`cpu=16` `memory=60` **`gpu=1`** |
| OSS 输入 | `oss://eci-manager-bucket/3dgs/raw/<建筑名>.zip` |
| OSS 输出 | `oss://eci-manager-bucket/3dgs/exports/<建筑名>.ply` |

zip 结构：一层目录，例如 `新文科楼/image*.JPG`，不要嵌套成 `新文科楼/新文科楼/`。

### 1.2 `.env`（真云）

```bash
cd ~/XJI/XJICloud/eci-manager
# 若无 .env：cp .env.example .env 后填写
```

至少确认：

| 变量 | 要求 |
|------|------|
| `SPRING_PROFILES_ACTIVE` | `cloud,local` |
| `ECI_API_KEY` | 与请求头 `X-Api-Key` 一致 |
| `ALIBABA_CLOUD_ACCESS_KEY_ID` / `SECRET` | 调 ECI OpenAPI |
| `ECI_REGION_ID` / `ECI_VSWITCH_ID` / `ECI_SECURITY_GROUP_ID` | 创建容器网络 |
| `ECI_OSS_ENABLED` | `true` |
| `ECI_OSS_ENDPOINT` | **内网** `https://oss-cn-hangzhou-internal.aliyuncs.com`（容器无 NAT 时公网会超时） |
| `ECI_OSS_BUCKET` | `eci-manager-bucket` |
| `ECI_OSS_ACCESS_KEY` / `ECI_OSS_SECRET_KEY` | 仅用于本机签发 presign，**不会**注入容器 |
| `ECI_OSS_PRESIGN_EXPIRATION_MINUTES` | **1440**（默认 60 分钟不够训完；PUT 必须活到训练结束） |
| `ECI_CREATE_TIMEOUT_SEC` | 建议 **1800**（大镜像首次拉取常超过默认 300 秒） |

改过 `.env` 必须重启 eci-manager。

### 1.3 启动服务

**终端 A**（保持运行）：

```bash
cd ~/XJI/XJICloud/eci-manager
source ../.tools/env.sh    # 若需 JDK/Maven
./scripts/start.sh
```

期望日志含：`profiles are active: "cloud", "local"`、`ECI Aliyun client ready`、Tomcat `8081`；OSS 未出现意外 `skipped`。

**终端 B** 健康检查：

```bash
cd ~/XJI/XJICloud/eci-manager
source scripts/load-env.sh
curl -s -w "\nHTTP:%{http_code}\n" "$ECI_BASE/actuator/health"
```

期望：`HTTP:200`，体含 `"status":"UP"`。

---

## 2. 把原图放到 OSS

本机上传用 **公网** endpoint；eci-manager 给容器签发用 **内网**（见上表）。

```bash
cd ~/XJI/XJICloud/eci-manager
python3 -m pip install --user oss2   # 若尚未安装
python3 - <<'PY'
from pathlib import Path
import oss2

env = {}
for line in Path(".env").read_text(encoding="utf-8").splitlines():
    s = line.strip()
    if not s or s.startswith("#") or "=" not in s:
        continue
    k, v = s.split("=", 1)
    env[k] = v.strip().strip("'").strip('"')

building = "新文科楼"  # 换成目标建筑名
local = Path(f"/home/YLiF4/XJI/{building}.zip")  # 按本机路径改
key = f"3dgs/raw/{building}.zip"
auth = oss2.Auth(env["ECI_OSS_ACCESS_KEY"], env["ECI_OSS_SECRET_KEY"])
bucket = oss2.Bucket(auth, "https://oss-cn-hangzhou.aliyuncs.com", env["ECI_OSS_BUCKET"])
print(f"upload {local} -> oss://{env['ECI_OSS_BUCKET']}/{key}")
bucket.put_object_from_file(key, str(local))
info = bucket.head_object(key)
print(f"ok content-length={info.content_length}")
PY
```

期望：`content-length` 与本地 zip 一致。换建筑时，**本地 zip、OSS key、提交时的 `BUILDING` 三者同名**。

---

## 3. 一键提交

```bash
cd ~/XJI/XJICloud/eci-manager
./scripts/submit-3dgs-standard.sh
```

脚本会：组装与标准档一致的 `POST /api/v1/eci/tasks`（注入 `3dgs-bootstrap.py`）、打印 `TASK_ID`，并轮询到 `SUCCEEDED` / `FAILED` / `CANCELLED`。

### 3.1 默认参数

| 项 | 默认 |
|----|------|
| `BUILDING` | `新文科楼` |
| `MAX_STEPS` | `15000` |
| `DATA_FACTOR` | `1` |
| `PACKED` / `ANTIALIASED` | `1` / `1` |
| `MAX_LONG_EDGE` | `2048` |
| `MAX_RUNTIME_SEC` | `21600`（6 小时） |
| `IMAGE_SNAPSHOT_ID` | `imc-bp15yiuttnxf8fh9n9fy` |
| 轮询 | `POLL_SLEEP_SEC=15`，最多约 5 小时（`POLL_MAX=1200`） |

### 3.2 常用覆盖

```bash
# 换建筑（OSS 须已有 3dgs/raw/<名>.zip）
BUILDING=某楼 ./scripts/submit-3dgs-standard.sh

# 短测（约十余分钟量级，含 COLMAP + 首次 CUDA 编译）
MAX_STEPS=100 ./scripts/submit-3dgs-standard.sh

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

1. **`DataLoader patch failed: No module named 'torch'`**  
   系统 Python 无 torch 的噪声。后面若有 **`DataLoader num_workers=0 (ECI shm)`**，说明 venv 补丁已生效，可忽略前者。

2. **`ensuring CUDA extension is compiled...` 数分钟无新行**  
   首次编译 CUDA 扩展正常，实测约 **5–6 分钟**（例如 ~350s）后才会出现 `CUDA extension ready`。

3. **进度条一直显示 `0%| … 0/15000`**  
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
| `/dev/shm` | ECI 默认约 64MiB。bootstrap 强制 `DataLoader num_workers=0`，避免 step-0 shared memory 分配失败 |
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
