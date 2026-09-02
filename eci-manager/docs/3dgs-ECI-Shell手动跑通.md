# 3DGS 真云 ECI Shell 手动跑通指南

> **适用：** 在阿里云 GPU ECI 里**亲手敲命令**跑完 3DGS，**不经过**主仓 `backend/`、**不经过** eci-manager 自动 bootstrap。  
> **镜像：** `3dgs:1.0.0`（容器内 `/opt/3dgs`）  
> **交互方式：** 控制台 **Workbench 远程连接**（Shell）  
> **数据：** OSS 预签名 URL 下载 zip / 上传 ply（**不把 AK 写进容器**）

锁定参数与踩坑见 [`3dgs真云ECI实测记录.md`](./3dgs真云ECI实测记录.md)。自动 POST 任务见 [`3dgs实例测试.md`](./3dgs实例测试.md)。成功案例与「失败自动删容器丢日志」优化上下文见 [`3dgs成功案例与日志可观测性上下文.md`](./3dgs成功案例与日志可观测性上下文.md)。

---

## 0. 流程总览

```text
【本机 WSL】上传 zip → 生成 GET/PUT 预签名 URL（24h）
       ↓
【阿里云控制台】创建 GPU 容器组（shell 保活）→ Workbench 连进去
       ↓
【容器 Shell】GET zip → unzip → prepare → colmap → train → PUT ply
       ↓
【本机 WSL】从 OSS 下载 ply 验收 → 控制台删除容器组
```

**标准 3DGS 小场景三步**（与 Docker Compose 一致，由 `entrypoint.sh` 转发）：

| 步骤 | 命令 | 脚本 |
|------|------|------|
| 1 | `prepare <建筑名> <原图目录>` | `prepare_building.sh` |
| 2 | `colmap <建筑名>` | `run_colmap_sparse.sh` |
| 3 | `train <建筑名>` | `run_gsplat_train.sh` |

训完 PLY 在容器内：`/data/exports/<建筑名>.ply`。**必须在删实例前 PUT 到 OSS。**

---

## 1. 先写死的名字

| 项 | 值 |
|---|---|
| 建筑名 `BUILDING` | `新文科楼`（全文一致） |
| 本机 zip | `/home/YLiF4/XJI/新文科楼.zip` |
| OSS 输入 | `oss://eci-manager-bucket/3dgs/raw/新文科楼.zip` |
| OSS 输出 | `oss://eci-manager-bucket/3dgs/exports/新文科楼.ply` |
| 容器 `ROOT_DIR` | `/data` |
| 镜像（VPC） | `crpi-urdof1biefqsi8uj-vpc.cn-hangzhou.personal.cr.aliyuncs.com/ylif4/3dgs:1.0.0` |
| 镜像缓存 | `imc-bp15yiuttnxf8fh9n9fy`（Ready，80 GiB，K 区） |
| GPU 规格 | `ecs.gn7i-c16g1.4xlarge`，**GPU 数量 = 1** |
| CPU / 内存 | **16 vCPU / 60 GiB** |
| 地域 / 可用区 | `cn-hangzhou` / **cn-hangzhou-k** |
| 交换机 | `vsw-bp17apvyc28lj2dhwftw4` |
| 安全组 | `sg-bp19fa014hngv440ach4` |

zip 内结构须为 **`新文科楼/image*.JPG` 一层目录**，不要 `新文科楼/新文科楼/`。

---

## 2. 本机准备（WSL 终端）

### 2.1 上传原图 zip 到 OSS（若桶里还没有）

**在哪里：** 本机 WSL，目录 `~/XJI/XJICloud/eci-manager`。

```bash
cd ~/XJI/XJICloud/eci-manager
python3 -m pip install --user oss2   # 仅本机缺时执行一次
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
# 本机上传必须用公网 endpoint
auth = oss2.Auth(env["ECI_OSS_ACCESS_KEY"], env["ECI_OSS_SECRET_KEY"])
bucket = oss2.Bucket(auth, "https://oss-cn-hangzhou.aliyuncs.com", env["ECI_OSS_BUCKET"])
print(f"local={local} size={local.stat().st_size}")
info = bucket.head_object(key)
print(f"remote exists size={info.content_length}")
if info.content_length != local.stat().st_size:
    print("size mismatch — upload manually or fix zip")
else:
    print("OK: zip ready in OSS")
PY
```

若远程不存在或大小不对，用控制台/ossutil 上传；或改脚本里 `bucket.put_object_from_file(...)` 上传。

### 2.2 生成本次 GET / PUT 预签名 URL

**在哪里：** 本机 WSL，仍在 `eci-manager` 目录。  
**给谁用：** 复制打印出来的两条 URL，稍后在 **容器 Workbench Shell** 里粘贴（容器走 **内网** endpoint 签名）。

```bash
cd ~/XJI/XJICloud/eci-manager
python3 - <<'PY'
from pathlib import Path
from datetime import timedelta
import oss2

env = {}
for line in Path(".env").read_text(encoding="utf-8").splitlines():
    s = line.strip()
    if not s or s.startswith("#") or "=" not in s:
        continue
    k, v = s.split("=", 1)
    env[k] = v.strip().strip("'").strip('"')

auth = oss2.Auth(env["ECI_OSS_ACCESS_KEY"], env["ECI_OSS_SECRET_KEY"])
# 容器内 GET/PUT 必须内网
bucket = oss2.Bucket(
    auth,
    "https://oss-cn-hangzhou-internal.aliyuncs.com",
    env["ECI_OSS_BUCKET"],
)
expire = 60 * 60 * 24  # 24 小时

get_url = bucket.sign_url("GET", "3dgs/raw/新文科楼.zip", expire)
put_url = bucket.sign_url(
    "PUT",
    "3dgs/exports/新文科楼.ply",
    expire,
    headers={"Content-Type": "application/octet-stream"},
)

print("=== 复制以下两行到记事本，进容器后用 ===")
print("PRESIGN_GET=" + get_url)
print("PRESIGN_PUT=" + put_url)
PY
```

把 `PRESIGN_GET=...` 和 `PRESIGN_PUT=...` 整行保存到本机记事本。**不要提交 Git。**

---

## 3. 阿里云控制台：创建 GPU 容器组

**在哪里：** 浏览器 → [弹性容器实例控制台](https://eci.console.aliyun.com/) → 左上角地域选 **华东1（杭州）**。

### 3.1 创建入口

1. 左侧 **容器组** → **创建容器组**。
2. **付费方式 / 地域 / 可用区：** 按量；杭州；可用区选 **K 区**（与 GPU 交换机、镜像缓存一致）。

### 3.2 容器配置

| 控制台字段 | 填写 |
|---|---|
| 容器名称 | 自定，如 `3dgs-manual-新文科楼` |
| 镜像 | 选 ACR 个人版 **VPC 地址**：`.../ylif4/3dgs:1.0.0`（**勿用 `:latest`**） |
| 镜像缓存 | 选 **`imc-bp15yiuttnxf8fh9n9fy`**（状态 Ready） |
| 实例规格 | **GPU 型** → `ecs.gn7i-c16g1.4xlarge` |
| GPU 数量 | **1** |
| vCPU | **16** |
| 内存 | **60 GiB** |
| 容器启动命令 | `/opt/3dgs/docker/entrypoint.sh` |
| 容器启动参数（Args） | `shell sleep infinity` |
| 重启策略 | **Never**（或 Always；手动跑完会自己删） |

**说明：** `shell sleep infinity` 会先跑镜像自检，再 **`sleep infinity` 保活**，方便 Workbench 连进去；**不要**让容器跑完就退出。

### 3.3 网络

| 字段 | 值 |
|---|---|
| VPC | `vpc-bp1cvwvjp5n4g4r47dqs0` |
| 交换机 | `vsw-bp17apvyc28lj2dhwftw4`（K 区） |
| 安全组 | `sg-bp19fa014hngv440ach4` |

**不要** 挂载 OSS 盘 / ossfs。数据走预签名 HTTPS。

### 3.4 环境变量（容器 env）

在控制台 **环境变量** 区域新增：

| Key | Value |
|---|---|
| `ROOT_DIR` | `/data` |
| `BUILDING` | `新文科楼` |
| `EXPECTED_DRIVER_MAJOR` | `550` |
| `TORCH_FILE_SYSTEM_SHARING` | `1` |

训练阶段还要用的变量（也可进 Shell 后再 `export`，见 §6.4）：

| Key | 先跑通建议 | 标准质量 |
|---|---|---|
| `MAX_STEPS` | `2000` | `15000` |
| `DATA_FACTOR` | `4` | `1` |
| `PACKED` | `1` | `1` |
| `ANTIALIASED` | `0` | `1` |
| `EXTRA_TRAIN_ARGS` | `--strategy.refine-start-iter 100000`（关 densify） | 留空或 `--strategy.refine-stop-iter 8000` |

### 3.5 创建并等待 Running

1. 单击 **创建**。
2. 回到 **容器组列表**，等状态 **Running**（命中镜像缓存约 **10–30 秒**；日志里应有 `SuccessfulHitImageCache`）。
3. 记下 **容器组 ID**（形如 `eci-bpxxxxxxxx`）。

无 GPU 库存时，同 K 区试 `ecs.gn7i-c8g1.2xlarge`。**不要用杭州 B 区旧交换机**（曾整区 NoStock）。

---

## 4. 连接 Workbench Shell

**在哪里：** 同一容器组 → **实例详情** → **Workbench 远程连接**（或「终端」类页签）。

1. 选择 **运行中** 的容器。
2. 连接方式：**Shell**，命令 **`/bin/bash`**（默认即可）。
3. 单击 **登录**。

期望提示符类似：

```text
/ #
```

或已在某个工作目录。会话默认最长约 **6 小时**无操作会断开，可重新连接。

**另：** 也可用 API `ExecContainerCommand` 拿 `HttpUrl` 开浏览器终端（30 秒内有效）；日常手动调试 **Workbench 最省事**。

---

## 5. 容器内：自检（Workbench 里粘贴）

**在哪里：** 上一步打开的 **Workbench 终端**。

```bash
export ROOT_DIR=/data
export BUILDING=新文科楼
export EXPECTED_DRIVER_MAJOR=550

nvidia-smi
df -h / /data /dev/shm
/opt/3dgs/docker/entrypoint.sh selfcheck
```

期望：

- `nvidia-smi` 能看到 **A10**，驱动大版本 **550**
- `torch ... cuda_available=True`
- `colmap:` 行含 **CUDA**（不是 `without CUDA`）
- `/dev/shm` 约 **64M**（正常，后面训练前打补丁）

---

## 6. 容器内：标准 3DGS 全流程

以下 **全部在 Workbench Shell** 执行。把 §2.2 记下的 URL 贴到对应变量。

### 6.1 下载并解压原图

```bash
export ROOT_DIR=/data
export BUILDING=新文科楼
mkdir -p "${ROOT_DIR}"
cd "${ROOT_DIR}"

# 把本机 §2.2 生成的整行粘贴进来（含 https://...）
export PRESIGN_GET='https://eci-manager-bucket.oss-cn-hangzhou-internal.aliyuncs.com/3dgs%2Fraw%2F%E6%96%B0%E6%96%87%E7%A7%91%E6%A5%BC.zip?OSSAccessKeyId=LTAI5t5p6WNH86ngXe4KNvZC&Expires=1788400414&Signature=pb%2BooWSFt9IqFrhziXfIyc4Kt8U%3D'
export PRESIGN_PUT='https://eci-manager-bucket.oss-cn-hangzhou-internal.aliyuncs.com/3dgs%2Fexports%2F%E6%96%B0%E6%96%87%E7%A7%91%E6%A5%BC.ply?OSSAccessKeyId=LTAI5t5p6WNH86ngXe4KNvZC&Expires=1788400414&Signature=vdAyRX0Bh21Uha1WT3ePNoMXbUU%3D'


curl -fL -o "${BUILDING}.zip" "${PRESIGN_GET}"
ls -lh "${BUILDING}.zip"
unzip -o "${BUILDING}.zip" -d "${ROOT_DIR}"
ls "${ROOT_DIR}/${BUILDING}" | head
```

期望：能看到 `image*.JPG`，且路径是 **`/data/新文科楼/`**，不是双层目录。

### 6.2 ECI `/dev/shm` 补丁（训练前必做）

ECI 默认 `/dev/shm` 只有 64 MiB，不补丁会在训练 step 0 报 `unable to allocate shared memory`。

```bash
export ROOT_DIR=/data
export TORCH_FILE_SYSTEM_SHARING=1

HOOK="${ROOT_DIR}/.eci-site"
mkdir -p "${HOOK}"
cat > "${HOOK}/sitecustomize.py" <<'EOF'
import sys
try:
    import torch.multiprocessing as mp
    mp.set_sharing_strategy("file_system")
except Exception:
    pass
try:
    import torch.utils.data as _tud
    _orig = _tud.DataLoader.__init__
    def _init(self, *args, **kwargs):
        kwargs["num_workers"] = 0
        kwargs["persistent_workers"] = False
        return _orig(self, *args, **kwargs)
    _tud.DataLoader.__init__ = _init
    print("[eci-shm] DataLoader num_workers=0", file=sys.stderr, flush=True)
except Exception as exc:
    print(f"[eci-shm] patch failed: {exc}", file=sys.stderr, flush=True)
EOF

export PYTHONPATH="${HOOK}${PYTHONPATH:+:${PYTHONPATH}}"
echo "PYTHONPATH=${PYTHONPATH}"
```

后续 **`prepare` / `colmap` / `train` 三条命令都在同一 Shell 会话里跑**，不要新开 Workbench 会话（否则 `PYTHONPATH` 会丢）。

### 6.3 第一步：prepare

```bash
export ROOT_DIR=/data
export BUILDING=新文科楼
export MAX_LONG_EDGE=2048
export DATA_FACTOR=4          # 与训练一致；标准质量改为 1

/opt/3dgs/docker/entrypoint.sh prepare "${BUILDING}" "${ROOT_DIR}/${BUILDING}"
```

期望日志：`[prepare] dataset=/data/datasets/新文科楼`，无 `ERROR: source image directory not found`。

### 6.4 第二步：colmap（稀疏重建）

```bash
export ROOT_DIR=/data
export BUILDING=新文科楼

/opt/3dgs/docker/entrypoint.sh colmap "${BUILDING}"
```

期望末尾类似：

```text
[colmap] registered=81/81 ratio=1.000 (min=0.9)
[colmap] QC passed
[colmap] done: /data/datasets/新文科楼/sparse/0
```

若注册率 &lt; 0.9，脚本会非 0 退出，**不要硬训**。

### 6.5 第三步：train（gsplat）

**先跑通档（推荐第一次手动试）：**

```bash
export ROOT_DIR=/data
export BUILDING=新文科楼
export MAX_STEPS=2000
export DATA_FACTOR=4
export PACKED=1
export ANTIALIASED=0
export EXTRA_TRAIN_ARGS='--strategy.refine-start-iter 100000'
export PYTHONPATH="/data/.eci-site${PYTHONPATH:+:${PYTHONPATH}}"

/opt/3dgs/docker/entrypoint.sh train "${BUILDING}"
```

**标准质量档（显存够再开）：**

```bash
export MAX_STEPS=15000
export DATA_FACTOR=1
export PACKED=1
export ANTIALIASED=1
unset EXTRA_TRAIN_ARGS
# 可选：export EXTRA_TRAIN_ARGS='--strategy.refine-stop-iter 8000'

/opt/3dgs/docker/entrypoint.sh train "${BUILDING}"
```

期望：

- 开头 `[gsplat] ensuring CUDA extension is compiled...`（首次约数分钟）
- 训练日志有 step / loss
- 结束 `[gsplat] exported PLY: /data/exports/新文科楼.ply`

确认文件存在：

```bash
ls -lh "/data/exports/${BUILDING}.ply"
```

若 train 日志已到 100% 但末尾报 `cp: cannot stat '[eci-shm] DataLoader...point_cloud_*.ply'`，说明 **训练已成功、仅自动导出失败**（§6.2 的 `sitecustomize` 向 stdout 打印，污染了 `run_gsplat_train.sh` 的路径捕获）。手动复制即可：

```bash
export BUILDING=新文科楼
ls -lh /data/results/${BUILDING}/ply/point_cloud_*.ply
cp /data/results/${BUILDING}/ply/point_cloud_1999.ply /data/exports/${BUILDING}.ply
ls -lh /data/exports/${BUILDING}.ply
```

（`1999` 与 `MAX_STEPS=2000` 对应；若 step 不同，用 `ls` 里最新的 `point_cloud_*.ply`。）

---

## 7. 容器内：PUT ply 到 OSS

**在哪里：** 仍在 **Workbench Shell**（删实例前）。

```bash
export BUILDING=新文科楼

# 粘贴 §2.2 的 PUT 预签名 URL
export PRESIGN_PUT='https://eci-manager-bucket.oss-cn-hangzhou-internal.aliyuncs.com/3dgs%2Fexports%2F%E6%96%B0%E6%96%87%E7%A7%91%E6%A5%BC.ply?OSSAccessKeyId=LTAI5t5p6WNH86ngXe4KNvZC&Expires=1788400414&Signature=vdAyRX0Bh21Uha1WT3ePNoMXbUU%3D'

curl -f -X PUT \
  -H 'Content-Type: application/octet-stream' \
  --data-binary "@/data/exports/${BUILDING}.ply" \
  "${PRESIGN_PUT}"

echo "PUT done, exit=$?"
```

期望：`PUT done, exit=0`。若 **403**，检查 PUT URL 是否过期、是否带 `Content-Type: application/octet-stream` 签名。

---

## 8. 本机验收（WSL）

**在哪里：** 本机 WSL。

```bash
cd ~/XJI/XJICloud/eci-manager
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

用 [SuperSplat](https://playcanvas.com/supersplat/editor) 打开预览。

---

## 9. 收尾：删实例

**在哪里：** 阿里云 ECI 控制台 → 该容器组 → **删除**。

手动 Shell 跑完后实例不会自动删，**务必删除**，避免 GPU 继续计费。

---

## 10. 质量档位速查

| 目标 | MAX_STEPS | DATA_FACTOR | PACKED | ANTIALIASED | densify |
|------|-----------|-------------|--------|-------------|---------|
| **先跑通 PUT** | 2000 | 4 | 1 | 0 | 关（`refine-start-iter 100000`） |
| **中等** | 5000 | 2 | 1 | 0 | 早停（`refine-stop-iter 2500`） |
| **标准** | 15000 | 1 | 1 | 1 | 默认 |

实测：15k + 默认 densify 时 GS 可涨到 **300 万+**，A10 24GB 常在 **97% 附近 Failed**；磁盘/内存监控正常时更像 **显存** 问题。手动 Shell 的好处是：**失败时人还在容器里**，可立刻 `nvidia-smi`、`ls -lh /data/results/`、看完整日志，不必等实例被删。

---

## 11. 排障

| 现象 | 在哪里查 | 处理 |
|------|----------|------|
| 容器起不来 / 拉镜像失败 | 控制台事件 | 确认 `:1.0.0` + 镜像缓存 Ready；勿用 `:latest` |
| `OperationDenied.NoStock` | 创建页 | 换 K 区交换机；试 `gn7i-c8g1.2xlarge` |
| Workbench 连不上 | 实例状态 | 须 **Running**；Args 须 `shell sleep infinity` |
| GET zip 超时 | 容器内 curl | presign 须 **内网** endpoint 签发 |
| step 0 `shared memory` | 训练日志 | 重做 §6.2 `PYTHONPATH` 补丁 |
| COLMAP 注册率低 | colmap 日志 | 查图片重叠/模糊；勿降低 `MIN_REG_RATIO` 硬训 |
| 训练中途 Failed | Workbench + `nvidia-smi` | 降 `MAX_STEPS` / 提 `DATA_FACTOR` / 关 densify / `PACKED=1` |
| PUT 403 | curl 输出 | 重签 PUT URL；头必须是 `application/octet-stream` |
| train 完 `cp: cannot stat '[eci-shm]...point_cloud_*.ply'` | train 末尾 | **训练已成功**；`sitecustomize` 的 `print` 污染了 shell 捕获路径 → 手动 `cp` 见 §6.5 下 |
| 控制台内存 3%、磁盘平但仍崩 | 监控 | 容器 MEMORY 不含 GPU 显存；用 `nvidia-smi` 看 VRAM |

---

## 12. 与自动任务（eci-manager POST）的差异

| 项 | Shell 手动 | POST + bootstrap |
|---|---|---|
| 创建 | 控制台 / OpenAPI | `POST /api/v1/eci/tasks` |
| 下载 / 上传 | 自己 curl 预签名 URL | 注入 `XJI_*` env |
| 流程 | 自己敲 prepare/colmap/train | `3dgs-bootstrap.py` 串行 |
| shm 补丁 | §6.2 手写 | bootstrap 自动 |
| 失败取证 | **可留在 Shell 里查** | 实例常被 watcher 立刻删掉 |
| 适合 | 调试、调参、排 OOM | 批量、自动化 |

**一键 POST（与本文 §6 标准质量档对齐）：** [`scripts/submit-3dgs-standard.sh`](../scripts/submit-3dgs-standard.sh)

```bash
cd ~/XJI/XJICloud/eci-manager
source ../.tools/env.sh
source scripts/load-env.sh
./scripts/start.sh          # 终端 A
./scripts/submit-3dgs-standard.sh   # 终端 B
```

---

## 13. 验收清单

```text
[ ] OSS 有 3dgs/raw/新文科楼.zip
[ ] 本机已生成 PRESIGN_GET / PRESIGN_PUT（24h 内）
[ ] 控制台创建 GPU 容器 Running，命中镜像缓存
[ ] Workbench 内 selfcheck 通过（CUDA + COLMAP）
[ ] prepare → colmap(81/81) → train 完成
[ ] /data/exports/新文科楼.ply 存在且已 PUT
[ ] 本机可从 OSS 下载 ply；SuperSplat 能打开
[ ] 控制台已删除容器组
[ ] 未把 AK / 预签名 URL 提交 Git
```

---

## 14. 可选：不用控制台，OpenAPI 只创建「Shell 保活」容器

若熟悉 API，可在 **本机 WSL** 用 OpenAPI `CreateContainerGroup` 代替 §3，核心字段与上文一致：

- `Container.1.Image` = VPC 镜像 `:1.0.0`
- `Container.1.Command` = `["/opt/3dgs/docker/entrypoint.sh"]`
- `Container.1.Arg` = `["shell", "sleep", "infinity"]`
- `InstanceType` = `ecs.gn7i-c16g1.4xlarge`
- `GpuCount` = 1
- `ImageSnapshotId` = `imc-bp15yiuttnxf8fh9n9fy`
- `VSwitchId` / `SecurityGroupId` 见 §1

创建成功后仍走 §4 Workbench → §6–§7。**训练命令与控制台路径完全相同。**
