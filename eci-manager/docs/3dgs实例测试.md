# 3DGS 真云 ECI 完整功能测试（presigned URL，不挂盘）

> **适用工程：** `eci-manager/`（默认 `http://127.0.0.1:8081`）  
> **3DGS 项目：** 本机 `/home/YLiF4/XJI/3DGS`（镜像内代码在 `/opt/3dgs`）  
> **操作系统：** Linux / WSL2；Shell 以 **bash** 为例  
> **费用：** 真云 GPU ECI 按量计费。任务终态后服务会删实例，仍请到控制台确认无残留。

本文按「复制粘贴即可执行」编写。主路径：**一次出一张 3D 图 = 提交一个任务 = 开一个 GPU 容器**。直接 `POST /api/v1/eci/tasks`，容器内串完下载 → prepare → colmap → train → PUT PLY 后退出。

锁定参数、最后一笔约 97% 失败的原因与对策见 [`3dgs真云ECI实测记录.md`](./3dgs真云ECI实测记录.md)。

---

## 0. 先读：测什么、不测什么

### 0.1 和原来云服务器是同一套 OSS，只换算力

原来 gpu-worker 跑在**常驻 GPU 云服务器**上：backend 签发限时下载/上传链接，进程 GET 到本地盘，训完 PUT 回 OSS，**没有把 OSS 挂成硬盘**。

ECI 只换算力形态（一次性容器）。Presigned URL 仍是 HTTPS。eci-manager 用 `storage.mode=keys` 签发 GET/PUT，注入容器 env。3dgs 镜像不认识 `XJI_*`，须把 [`scripts/3dgs-bootstrap.py`](../scripts/3dgs-bootstrap.py) 经 `args` 注入。

容器结束、实例删除后本地盘消失，所以 **PLY 必须在进程退出前 PUT 到 OSS**。

### 0.2 一次出图为什么是一个容器

ECI 任务是一次性的：进程退出 → 云侧 Succeeded → 任务 `SUCCEEDED` → **自动删实例**。`datasets/`、`results/` 都在容器本地盘上，不能拆成「这个容器 prepare、下一个容器 colmap」——下一个容器是空的。

```text
POST /api/v1/eci/tasks（一个）
  → 一个 GPU ECI
  → GET zip → unzip → prepare → colmap → train → PUT ply
  → exit 0 → 实例删除
  → OSS 上留下 3dgs/exports/<建筑名>.ply
```

### 0.3 本文覆盖 / 不覆盖

| 做 | 不做 |
|----|------|
| `POST /api/v1/eci/tasks` + `storage.mode=keys` | OSS FlexVolume / ossfs 挂盘 |
| 小场景：`prepare_building.sh` → `run_colmap_sparse.sh` → `run_gsplat_train.sh` | indoor / large_scene / block_scene |
| 稀疏 COLMAP + gsplat | COLMAP dense / MVS |
| 原图 zip 进、PLY 出 | 把 `datasets/`、`results/` 长期放 OSS |
| GPU 规格 + `gpu: 1` | 往容器 env 注入 AccessKey |
| 一次完整 15k 步出图 | smoke 自检、先跑 1000 步探路、封装提交脚本 |
| 本机 curl 调 eci-manager | 主仓 `backend/` 训练队列 |

---

## 1. 名字与路径（先写死）

建筑名：`新文科楼`（换成别的则全文一致）。

OSS 进出（中间产物不进桶）：

```text
oss://eci-manager-bucket/3dgs/raw/新文科楼.zip
oss://eci-manager-bucket/3dgs/exports/新文科楼.ply
```

样例压缩包：`/home/YLiF4/XJI/新文科楼.zip`（约 2.2GiB，81 张 `新文科楼/image*.JPG`，一层目录，不要 `新文科楼/新文科楼/`）。

容器内：

| 路径 | 含义 |
|------|------|
| `/opt/3dgs` | 镜像代码、scripts、venv |
| `/data` | `ROOT_DIR`：解压后的原图、`datasets/`、`results/`、`exports/`（本地 ephemeral） |

镜像（无 tag 时用 `:latest`）：

```bash
export GS_IMAGE='crpi-urdof1biefqsi8uj-vpc.cn-hangzhou.personal.cr.aliyuncs.com/ylif4/3dgs:1.0.0'
```

规格（杭州 GPU，显存优先 ≥16GB / 24GB 更稳）：

| 变量 | 建议 |
|------|------|
| `INSTANCE_TYPE` | 首选 `ecs.gn7i-c16g1.4xlarge`（A10 24GB）；无库存再试 `ecs.gn7i-c8g1.2xlarge` |
| `gpu` | **必须 `1`**（指定 GPU 规格但不声明 gpu 会创建失败） |
| `cpu` / `memory` | 与规格匹配，如 16 / 60 |
| 质量默认 | `MAX_LONG_EDGE=2048`，`DATA_FACTOR=1`，`MAX_STEPS=15000` |
| `maxRuntimeSec` | `21600`（6 小时） |

地域：ACR / ECI / OSS 都在 `cn-hangzhou`。

本机 WSL 上传 zip 必须走 **公网** `https://oss-cn-hangzhou.aliyuncs.com`。eci-manager 给容器签发 presign 必须走 **内网** `https://oss-cn-hangzhou-internal.aliyuncs.com`（无 NAT 时公网会超时）。

---

## 2. 环境准备（真云）

### 2.1 `.env` 必填项

```bash
cd ~/XJI/XJICloud/eci-manager
```

`.env` 至少：

| 变量 | 要求 |
|------|------|
| `SPRING_PROFILES_ACTIVE` | `cloud,local` |
| `ECI_API_KEY` | 与 curl 头一致 |
| `ALIBABA_CLOUD_ACCESS_KEY_ID` / `SECRET` | 调 ECI OpenAPI |
| `ECI_REGION_ID` / `ECI_VSWITCH_ID` / `ECI_SECURITY_GROUP_ID` | 创建容器网络 |
| `ECI_OSS_ENABLED` | `true` |
| `ECI_OSS_ENDPOINT` | **内网** `https://oss-cn-hangzhou-internal.aliyuncs.com` |
| `ECI_OSS_BUCKET` | `eci-manager-bucket` |
| `ECI_OSS_ACCESS_KEY` / `ECI_OSS_SECRET_KEY` | 签发 presign 用，**不会**进容器 |
| `ECI_OSS_PRESIGN_EXPIRATION_MINUTES` | **1440**（默认 60 只够冒烟；PUT 必须活到训练结束） |
| `ECI_CREATE_TIMEOUT_SEC` | **1800**（大镜像首次拉取常超过默认 300 秒） |

改过 `.env` 必须重启服务。

### 2.2 终端 A：启动 eci-manager

```bash
cd ~/XJI/XJICloud/eci-manager
source ../.tools/env.sh
chmod +x scripts/*.sh
./scripts/start.sh
```

期望日志：`profiles are active: "cloud", "local"`、`ECI Aliyun client ready`、OSS 未出现 `skipped`、Tomcat `8081`。

### 2.3 终端 B：公共变量

**不要停终端 A。** 新开终端：

```bash
cd ~/XJI/XJICloud/eci-manager
source ../.tools/env.sh
source scripts/load-env.sh
export GS_IMAGE='crpi-urdof1biefqsi8uj-vpc.cn-hangzhou.personal.cr.aliyuncs.com/ylif4/3dgs:1.0.0'
export BUILDING='新文科楼'
export INSTANCE_TYPE='ecs.gn7i-c16g1.4xlarge'
```

健康检查：

```bash
curl -s -w "\nHTTP_CODE:%{http_code}\n" "$BASE/actuator/health"
```

期望：`HTTP_CODE:200`，体含 `"status":"UP"`。

---

## 3. 把原图放到 OSS

本机无 `ossutil` 时用 `oss2`。**endpoint 用公网**，不要用 internal。

```bash
cd ~/XJI/XJICloud/eci-manager
python3 -m pip install --user oss2
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

local = Path("/home/YLiF4/XJI/新文科楼.zip")
key = "3dgs/raw/新文科楼.zip"
auth = oss2.Auth(env["ECI_OSS_ACCESS_KEY"], env["ECI_OSS_SECRET_KEY"])
bucket = oss2.Bucket(auth, "https://oss-cn-hangzhou.aliyuncs.com", env["ECI_OSS_BUCKET"])
print(f"upload {local} ({local.stat().st_size} bytes) -> oss://{env['ECI_OSS_BUCKET']}/{key}")
bucket.put_object_from_file(key, str(local))
info = bucket.head_object(key)
print(f"ok content-length={info.content_length}")
PY
```

期望：`content-length` 与本地 zip 一致（约 2.2GiB）。

---

## 4. 主路径：一个任务出一个 PLY

**推荐：** 与手动 Shell 标准 15k 对齐的一键脚本 [`scripts/submit-3dgs-standard.sh`](../scripts/submit-3dgs-standard.sh)（`PACKED=1`、`ANTIALIASED=1`、镜像缓存默认 `imc-bp15yiuttnxf8fh9n9fy`）。

```bash
cd ~/XJI/XJICloud/eci-manager
source scripts/load-env.sh
./scripts/submit-3dgs-standard.sh
```

以下 JSON 手写方式与脚本等价；终端 B 已 `source scripts/load-env.sh` 之后。用 python 只生成 JSON（把 [`scripts/3dgs-bootstrap.py`](../scripts/3dgs-bootstrap.py) base64 进 `args`），再 `curl POST`。

```bash
cd ~/XJI/XJICloud/eci-manager
source scripts/load-env.sh
export ECI_MANAGER_ROOT="$(pwd)"
export GS_IMAGE="${GS_IMAGE:-crpi-urdof1biefqsi8uj-vpc.cn-hangzhou.personal.cr.aliyuncs.com/ylif4/3dgs:1.0.0}"
export BUILDING="${BUILDING:-新文科楼}"
export INSTANCE_TYPE="${INSTANCE_TYPE:-ecs.gn7i-c16g1.4xlarge}"
export MAX_STEPS="${MAX_STEPS:-15000}"
export MAX_RUNTIME_SEC="${MAX_RUNTIME_SEC:-21600}"
# 可选：Ready 的镜像缓存，避免每次在 GPU 上解压 ~10GiB 镜像
# export IMAGE_SNAPSHOT_ID=imc-xxxxxxxx

BODY=$(python3 - <<'PY'
import base64, json, os
from pathlib import Path
b64 = base64.b64encode((Path(os.environ["ECI_MANAGER_ROOT"]) / "scripts" / "3dgs-bootstrap.py").read_bytes()).decode("ascii")
body = {
  "name": "3dgs-run-" + os.environ["BUILDING"],
  "image": os.environ["GS_IMAGE"],
  "cpu": 16, "memory": 60,
  "instanceType": os.environ["INSTANCE_TYPE"],
  "gpu": 1,
  "restartPolicy": "Never",
  "maxRuntimeSec": int(os.environ.get("MAX_RUNTIME_SEC", "21600")),
  "command": ["/opt/3dgs/docker/entrypoint.sh"],
  "args": ["shell", "python3 -c 'import base64; exec(base64.b64decode(\"%s\").decode())'" % b64],
  "env": {
    "ROOT_DIR": "/data",
    "BUILDING": os.environ["BUILDING"],
    "MAX_STEPS": os.environ.get("MAX_STEPS", "15000"),
    "DATA_FACTOR": "1",
    "PACKED": os.environ.get("PACKED", "1"),
    "ANTIALIASED": os.environ.get("ANTIALIASED", "1"),
    "MAX_LONG_EDGE": "2048",
    "EXPECTED_DRIVER_MAJOR": "550",
    "TORCH_FILE_SYSTEM_SHARING": "1",
  },
  "storage": {
    "mode": "keys",
    "outputKey": "3dgs/exports/%s.ply" % os.environ["BUILDING"],
    "inputKeys": ["3dgs/raw/%s.zip" % os.environ["BUILDING"]],
  },
}
if os.environ.get("IMAGE_SNAPSHOT_ID"):
    body["imageSnapshotId"] = os.environ["IMAGE_SNAPSHOT_ID"]
print(json.dumps(body, ensure_ascii=False))
PY
)

curl -s -X POST "$BASE/api/v1/eci/tasks" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $ECI_API_KEY" \
  -d "$BODY" | tee /tmp/eci_3dgs_task.json

export TASK_ID=$(jq -r '.data.id' /tmp/eci_3dgs_task.json)
export INST_ID=$(jq -r '.data.instanceId' /tmp/eci_3dgs_task.json)
echo "TASK_ID=$TASK_ID INST_ID=$INST_ID"
```

期望：

- `"success": true`
- 任务立刻为 `STARTING`，`instanceId` 非空，`gpu=1`，`instanceType` 为上面规格
- 实例 `cloudInstanceId` **不是** `mock-eci-` 前缀

轮询：

```bash
for i in $(seq 1 200); do
  curl -s "$BASE/api/v1/eci/tasks/$TASK_ID" -H "X-Api-Key: $ECI_API_KEY" | tee /tmp/eci_3dgs_get.json >/dev/null
  TS=$(jq -r '.data.status' /tmp/eci_3dgs_get.json)
  echo "try=$i status=$TS"
  case "$TS" in SUCCEEDED|FAILED|CANCELLED) break;; esac
  sleep 15
done
jq '{status:.data.status, failureReason:.data.failureReason}' /tmp/eci_3dgs_get.json
```

期望终态 `SUCCEEDED`，`failureReason` 为空；关联实例最终 `TERMINATED`。

失败时取消（把 UUID 换成上面打印的值）：

```bash
curl -s -X POST "$BASE/api/v1/eci/tasks/$TASK_ID/cancel" \
  -H "X-Api-Key: $ECI_API_KEY"
```

容器内引导逻辑（不必手敲，上面 JSON 会注入）：读 `XJI_INPUT_FILES_JSON` → GET → unzip → `prepare_building.sh` → `run_colmap_sparse.sh`（注册率默认 ≥ 0.90）→ `run_gsplat_train.sh` → 对 `XJI_OUTPUT_UPLOAD_URL` 做 PUT，头为 `application/octet-stream`。镜像无 `requests`，用标准库 `urllib`。

---

## 5. 质检与收尾

```bash
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

key = "3dgs/exports/新文科楼.ply"
dest = Path("/tmp/新文科楼.ply")
auth = oss2.Auth(env["ECI_OSS_ACCESS_KEY"], env["ECI_OSS_SECRET_KEY"])
bucket = oss2.Bucket(auth, "https://oss-cn-hangzhou.aliyuncs.com", env["ECI_OSS_BUCKET"])
bucket.get_object_to_file(key, str(dest))
print(f"downloaded {dest} size={dest.stat().st_size}")
PY
ls -lh /tmp/新文科楼.ply
```

用 [SuperSplat](https://playcanvas.com/supersplat/editor) 打开。记录：GPU 规格、镜像 digest、耗时、费用。

扫未删实例：

```bash
curl -s "$BASE/api/v1/eci/instances" -H "X-Api-Key: $ECI_API_KEY" \
  | jq -r '.data[] | select(.status!="TERMINATED") | [.id,.status,.cloudInstanceId,.taskId] | @tsv'
```

终端 A：`Ctrl+C` 停服务。

---

## 6. 排障

| 现象 | 优先查 |
|------|------|
| `ModuleNotFoundError: No module named 'exec'` | `python3 -c` 里不要写 `import base64,exec`；`exec` 是内置函数 |
| 拉不到镜像 / `no space left on device` | 3dgs 镜像解压远大于默认 30GiB；先建 ≥80GiB 镜像缓存，POST 时带 `imageSnapshotId` |
| `OperationDenied.NoStock` | 换 `INSTANCE_TYPE` 为 `ecs.gn7i-c8g1.2xlarge` |
| `cuda_available=False` | 非 GPU 规格 / 未传 `gpu` / 驱动大版本 |
| prepare 找不到图 | zip 内是否为 `新文科楼/*.JPG`；`BUILDING` 是否一致 |
| COLMAP 注册率低 | 脚本 `MIN_REG_RATIO=0.90` 会非 0 退出；查重叠/模糊，不要盲目训 |
| 训练 OOM | 给 `env` 加 `PACKED=1` 或 `DATA_FACTOR=2` |
| `unable to allocate shared memory` | ECI `/dev/shm` 默认 64MiB；发行版 sitecustomize 会挡住 site-packages。bootstrap 用 `PYTHONPATH` + `.pth` 把 DataLoader `num_workers` 置 0 |
| PUT 403 / 超时 | presign 过期（须 1440 分钟）；OSS 须内网 endpoint；PUT 头须 `application/octet-stream` |
| 任务 SUCCEEDED 但桶里没有 ply | 引导没执行到 PUT，或 `outputKey` 看错；看容器日志 |
| 实例没了 PLY 也没了 | 没在退出前 PUT |

---

## 7. 验收记录

```text
[ ] .env：真云 + OSS 内网 endpoint + presign=1440；ECI_CREATE_TIMEOUT_SEC=1800
[ ] OSS 可见 3dgs/raw/新文科楼.zip（约 2.2GiB）
[ ] POST /api/v1/eci/tasks 一个出图任务 → SUCCEEDED
[ ] oss://eci-manager-bucket/3dgs/exports/新文科楼.ply 可下载；SuperSplat 能打开
[ ] 实例 TERMINATED；控制台无残留
[ ] 未把密钥提交 Git
```

记录：规格 ______  digest ______  耗时 ______  费用 ______
