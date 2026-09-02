# 3DGS 真云 ECI 实测记录与正确使用流程

> **日期：** 2026-09-01  
> **工程：** `eci-manager/`（`http://127.0.0.1:8081`）  
> **3DGS 源码本机：** `/home/YLiF4/XJI/3DGS`（容器内 `/opt/3dgs`）  
> **引导脚本：** [`scripts/3dgs-bootstrap.py`](../scripts/3dgs-bootstrap.py)  
> **复制粘贴提交步骤：** [`3dgs实例测试.md`](./3dgs实例测试.md)  
> **费用：** GPU ECI 按量计费；任务终态后服务会删实例，仍须在控制台确认无残留。

本文记录 **已跑通的正确用法**、**锁定参数**、**踩过的坑**，以及两笔完整训练均在约 97% 失败、未产出 PLY 的对策。不含 AccessKey / Api-Key。

**后续（2026-09-02）：** 同一套参数下 **手动 Shell 标准 15k 已跑通**（SuperSplat 可打开）。POST 失败丢根因的流程缺陷与日志优化方案见 [`3dgs成功案例与日志可观测性上下文.md`](./3dgs成功案例与日志可观测性上下文.md)。

---

## 0. 目标与边界

**目标：** 一次 `POST /api/v1/eci/tasks`，用 OSS 上的原图 zip 在 GPU ECI 里跑完 3DGS，把 PLY **在容器退出前** PUT 回 OSS。不挂 OSS 盘，不把 AK/SK 注入容器。

**不测：** 主仓 `backend/`、ossfs、indoor / large_scene / block_scene、smoke / 1000 步探路。不要使用已删除的 `run-3dgs-task.sh`。

**一次出图必须是一个容器：** 进程退出 → 云侧 Succeeded → 任务 `SUCCEEDED` → 自动删实例。`datasets/`、`results/` 在 ephemeral 盘上，不能拆成「这个容器 prepare、下一个 colmap」。

```text
POST /api/v1/eci/tasks
  → GPU ECI（storage.mode=keys 签发 GET/PUT）
  → GET zip → unzip → prepare → colmap sparse → gsplat train
  → PUT ply → exit 0 → 任务 SUCCEEDED → 自动删实例
  → 桶里留下 3dgs/exports/<建筑>.ply
```

容器删除后本地盘消失，**PLY 必须在退出前 PUT**。中间产物不进 OSS。

---

## 1. 正确使用流程

1. `.env`：真云 + **杭州 K 区**交换机 + OSS **内网** endpoint + `ECI_OSS_PRESIGN_EXPIRATION_MINUTES=1440` + `ECI_CREATE_TIMEOUT_SEC=1800`。改过必须重启 eci-manager。
2. zip 结构正确并已在桶里（见 §2.3）；本机上传走 **公网** OSS endpoint。
3. 镜像用 **`:1.0.0`**（不要 `:latest`）。先有 Ready 的 **≥80 GiB** 镜像缓存，POST 带 `imageSnapshotId`。
4. `gpu: 1` + `ecs.gn7i-c16g1.4xlarge` + `cpu: 16` / `memory: 60`。
5. `command` / `args` 用 §2.7 的写法，注入**当前** `3dgs-bootstrap.py`（含 PYTHONPATH DataLoader 补丁）。
6. 训完验收：任务 `SUCCEEDED`、`oss://eci-manager-bucket/3dgs/exports/新文科楼.ply` 可下载、实例 `TERMINATED`、控制台无残留 GPU。
7. 失败时立刻 `DescribeContainerLog`；不要等 watcher 删完再查。

本机启动：

```bash
cd ~/XJI/XJICloud/eci-manager
source ../.tools/env.sh          # JAVA_HOME = .tools/jdk-17.0.13+11
source scripts/load-env.sh
./scripts/start.sh               # 终端 A，默认 8081
```

提交 JSON 的生成与 curl 见 [`3dgs实例测试.md`](./3dgs实例测试.md) §4。提交时额外：

```bash
export IMAGE_SNAPSHOT_ID='imc-bp15yiuttnxf8fh9n9fy'
```

---

## 2. 已锁定参数（2026-09-01 实测）

### 2.1 地域与网络

| 项 | 值 |
|---|---|
| 地域 | `cn-hangzhou` |
| VPC | `vpc-bp1cvwvjp5n4g4r47dqs0` |
| 交换机 | `vsw-bp17apvyc28lj2dhwftw4`（**cn-hangzhou-k**） |
| 安全组 | `sg-bp19fa014hngv440ach4` |
| 旧交换机（勿用） | `vsw-bp1tvba4b6b8l4b1ilhto`（杭州 **B** 区，GPU `OperationDenied.NoStock`） |

改 `ECI_VSWITCH_ID` 后必须重启 eci-manager。

### 2.2 eci-manager `.env`（非密钥）

| 变量 | 值 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `cloud,local` |
| `ECI_REGION_ID` | `cn-hangzhou` |
| `ECI_OSS_ENABLED` | `true` |
| `ECI_OSS_ENDPOINT` | **内网** `https://oss-cn-hangzhou-internal.aliyuncs.com` |
| `ECI_OSS_REGION` | `cn-hangzhou` |
| `ECI_OSS_BUCKET` | `eci-manager-bucket` |
| `ECI_OSS_PATH_STYLE_ACCESS` | `false` |
| `ECI_OSS_PRESIGN_EXPIRATION_MINUTES` | **1440**（默认 60 不够训完） |
| `ECI_CREATE_TIMEOUT_SEC` | **1800** |
| 本机 JDK | `/home/YLiF4/XJI/XJICloud/.tools/jdk-17.0.13+11` |
| HTTP | `http://127.0.0.1:8081`，头 `X-Api-Key` |

本机上传 zip：公网 `https://oss-cn-hangzhou.aliyuncs.com`。  
容器 GET/PUT：必须内网 `https://oss-cn-hangzhou-internal.aliyuncs.com`（无 NAT 时公网会超时）。

密钥：`ECI_API_KEY`、`ALIBABA_CLOUD_ACCESS_KEY_*`、`ECI_OSS_ACCESS_KEY` / `ECI_OSS_SECRET_KEY` 只存在 `.env`，**不要**进容器、不要进 Git。

### 2.3 OSS 对象

| 角色 | URI | 说明 |
|---|---|---|
| 输入 | `oss://eci-manager-bucket/3dgs/raw/新文科楼.zip` | 已上传；HEAD **2361154723** 字节（约 2.2 GiB） |
| 输出 | `oss://eci-manager-bucket/3dgs/exports/新文科楼.ply` | `PACKED=0` / `PACKED=1` 两笔均为 **404**，未产出 |
| 本地样例 | `/home/YLiF4/XJI/新文科楼.zip` | 81 张 `新文科楼/image*.JPG`，一层目录，不要 `新文科楼/新文科楼/` |

建筑名全文一致：`新文科楼`。

PUT 的 presign 必须签 `Content-Type: application/octet-stream`（keys 模式）。

### 2.4 镜像与缓存

| 项 | 值 |
|---|---|
| 镜像 | `crpi-urdof1biefqsi8uj-vpc.cn-hangzhou.personal.cr.aliyuncs.com/ylif4/3dgs:1.0.0` |
| **不要用** | `:latest`（ACR `not found`） |
| 压缩层约 | 10 077 531 063 bytes（~10 GiB）；解压远大于默认 30 GiB 系统盘 |
| 镜像缓存 ID | `imc-bp15yiuttnxf8fh9n9fy` |
| 状态 | **Ready**（2026-09-01 21:42 UTC+8） |
| 缓存盘 | 80 GiB，实际占用 **20.78 GiB / 80 GiB** |
| 快照 | `s-bp1113ro087w3tbdvkf8` |
| 缓存 Zone | `cn-hangzhou-k`（与 GPU 交换机一致） |
| 失败的自动缓存 | `imc-bp12f3wrj6za1do3fpsh`，34 GiB，`no space left on device`（解压 `nvidia/cufft/.../libcufft.so.11`） |

POST 必须带：

```json
"imageSnapshotId": "imc-bp15yiuttnxf8fh9n9fy"
```

命中时事件：`SuccessfulHitImageCache`，约 10 秒进入 Running。不带缓存会在 GPU 上再解压一次，又慢又容易撑盘。

手工建缓存（仅当 Ready 缓存丢失时）：`CreateImageCache`，`ImageCacheSize=80`，`Image.1` 为上面 `:1.0.0` 地址，`VSwitchId` / `SecurityGroupId` / `ZoneId=cn-hangzhou-k`。

### 2.5 GPU 规格（已成功拉起并训到 97%）

| 项 | 值 |
|---|---|
| `instanceType` | `ecs.gn7i-c16g1.4xlarge` |
| GPU | A10 24 GB × **1**（`gpu: 1` 必填） |
| `cpu` / `memory` | **16vCPU / 60G** |
| 驱动 | 主机 `550.163.01`（Tesla 550，ECI 默认） |
| `EXPECTED_DRIVER_MAJOR` | `550` |
| 容器自检 | nvcc CUDA **12.8**；torch `2.11.0+cu128` `cuda_available=True`；gsplat **1.5.3**；COLMAP **4.2.0.dev0**；pycolmap **4.1.0** |
| `restartPolicy` | `Never` |
| `maxRuntimeSec` | `21600`（6 小时） |

无库存再试 `ecs.gn7i-c8g1.2xlarge`。杭州 B 区 GPU 整区停售。

### 2.6 训练 / 数据超参（完整训练；两笔均崩在 ~97%）

| 项 | 值 |
|---|---|
| `BUILDING` | `新文科楼` |
| `ROOT_DIR` | `/data` |
| `MAX_STEPS` | **15000** |
| `DATA_FACTOR` | **1** |
| `PACKED` | 第一笔 **0**；重测笔 **1**（仍崩，见 §3 / §4） |
| `MAX_LONG_EDGE` | 2048（`prepare_building.sh` 默认） |
| COLMAP | 稀疏；81/81 SIFT；GPU matcher；增量重建（脚本 `MIN_REG_RATIO` 默认 ≥ 0.90） |
| 初始化高斯 | 约 **84946**–**84966**（COLMAP Points） |
| 训练速度 | 前期 ~12–13.6 it/s，中后期 ~7.5–8 it/s |
| ~48% | step ~7195，loss ~0.04–0.07，SH degree=3 |
| ~55% | step ~8200，loss ~0.05，SH degree=3 |
| ~97% | step **~14530–14550 / 15000**，loss ~0.04–0.07，SH degree=3；随后 Failed |
| `run_gsplat_train.sh` | `--eval-steps 7500 15000`；`--save-steps 7500 15000`；`--save-ply`；`--ply-steps 15000` |
| 7500 中段 | 两笔都**越过** 7500（可见 8200+），中段 eval/save **不是**致死点 |

容器路径：

| 路径 | 含义 |
|---|---|
| `/opt/3dgs` | 镜像代码、scripts、`.venv` |
| `/data` | `ROOT_DIR`：原图、`datasets/`、`results/`、`exports/`（ephemeral） |

### 2.7 容器启动（正确写法）

```text
command: ["/opt/3dgs/docker/entrypoint.sh"]
args:    ["shell", "python3 -c 'import base64; exec(base64.b64decode(\"<bootstrap.py 的 b64>\").decode())'"]
```

**禁止** `import base64,exec`。`exec` 是内置函数，会：

```text
ModuleNotFoundError: No module named 'exec'
```

entrypoint 的 `shell`：先 `selfcheck`，再 `bash -lc` 跑这段 Python。

eci-manager 还会注入 `XJI_*`（不要手写 AK）：

| Key | 含义 |
|---|---|
| `XJI_INPUT_FILES_JSON` | 含 zip 的 GET presign |
| `XJI_OUTPUT_UPLOAD_URL` | ply 的 PUT presign |
| `XJI_OUTPUT_OSS_KEY` | `3dgs/exports/新文科楼.ply` |
| `XJI_TASK_ID` | 任务 UUID |
| `XJI_OSS_BUCKET` / `XJI_OSS_ENDPOINT` / `XJI_OSS_REGION` | 桶与内网 endpoint |

任务 env 还应包含：`TORCH_FILE_SYSTEM_SHARING=1`、`SHM_SIZE=8G`（remount 会失败，见下）。

### 2.8 `/dev/shm`（必须按这个处理）

| 事实 | 说明 |
|---|---|
| ECI 默认 shm | **64 MiB** |
| 3DGS compose | `shm_size: 8gb` |
| 容器内 remount | `mount: /dev/shm: permission denied`，`df` 仍为 64 M |
| 发行版 sitecustomize | `/usr/lib/python3.12/sitecustomize.py` **挡住**写进 site-packages 的同名文件 |
| 未打补丁 | `simple_trainer.py` `num_workers=4` → step 0：`unable to allocate shared memory` |
| **现行 workaround** | bootstrap 写 `/data/.eci-site/sitecustomize.py` + `eci_shm_patch.pth`；`run()` 把该目录插到 `PYTHONPATH` 最前；强制 `DataLoader(num_workers=0, persistent_workers=False)` 与 `file_system` sharing |

日志应出现：`[3dgs-bootstrap] DataLoader num_workers=0 (ECI shm)`。没有这行不要开始烧 GPU。

`AliyunEciProvider` **尚未**设置 `EphemeralStorage` 或 EmptyDir(Memory) 挂 `/dev/shm`。

---

## 3. 任务时间线

### 3.1 第一笔（`PACKED=0`）

| 时间 (UTC+8) | 事件 |
|---|---|
| 更早 | B 区 NoStock；`:latest` NotFound；无缓存 / 34 GiB 自动缓存解压 ENOSPC |
| 21:31–21:42 | 手工 80 GiB 镜像缓存 → Ready `imc-bp15yiuttnxf8fh9n9fy` |
| 若干笔 | `import base64,exec` 秒退；shm 在 step 0 崩；site-packages sitecustomize 被发行版挡住 |
| **22:13:15** | 提交任务 `12638dd8-8094-40ea-a5d3-ecc5335cf6fc` |
| | 实例 `dc162694-c34b-4d6f-bb7e-01d850fc5cfb` |
| | 云 `eci-bp105vewz6y7d4h71vgu` |
| 22:13:43 | 容器 Running；`SuccessfulHitImageCache`；内网 IP `192.168.1.49` |
| ~22:14 | GET zip → prepare → COLMAP |
| ~22:18 | `run_gsplat_train.sh`；CUDA extension；DataLoader 补丁生效 |
| ~22:42 | ~8260 / 15000（55%） |
| ~22:56 | ~14500+ / 15000（**97%**），随后云侧 Failed |
| 22:56:54 | 任务 `FAILED`，`failureReason=云侧状态: Failed`；实例 Deleted |
| 之后 | OSS ply **404**；无 Running 残留；Describe 已空，**最终 traceback 未留下** |

### 3.2 重测笔（`PACKED=1`，2026-09-01 23:06–23:47）

| 时间 (UTC+8) | 事件 |
|---|---|
| **23:06:03** | 提交 `b7ab85fd-6279-457e-804a-7f0d86d236d6`；`PACKED=1`；同镜像/缓存/规格 |
| | 实例 `c8a0c839-0876-4be6-9729-64f1e299b2f8`；云 `eci-bp147320zoipj7yaawmd` |
| 23:06:41 | Running；`SuccessfulHitImageCache`；IP `192.168.1.50`；zone `cn-hangzhou-k` |
| ~23:07 | GET zip → prepare；DataLoader `num_workers=0` 补丁生效 |
| ~23:11 | COLMAP **81/81** ratio=1.000；Points≈84966；启动 `run_gsplat_train.sh` |
| ~23:12–23:17 | 编译 CUDA extension |
| ~23:18 | 开训；~543 / 15000（4%），~13.6 it/s |
| ~23:29 | ~7195 / 15000（48%），~8.1 it/s，SH=3 |
| ~23:40 | ~8209 / 15000（55%）；**已越过 7500 eval/save** |
| ~23:43 | ~13651 / 15000（91%），~7.6 it/s |
| ~23:45–23:46 | ~14544–**14553** / 15000（**97%**），~7.6 it/s，训练仍健康 |
| **23:46:56** | 任务 `FAILED`（`云侧状态: Failed`）；实例立刻 Deleted |
| 之后 | OSS ply **404**；Describe 已空；**仍无最终 traceback**（与第一笔同缺陷） |

---

## 4. 失败：事实与可能原因（两笔对照）

**事实（可复现）：**

- 两笔都健康训到约 **97%（step ~14530–14550）**，SH=3，~7–8 it/s，然后非 0 退出 → 云侧 Failed → watcher 立刻删实例 → **没有 PUT** → 桶里无 ply。
- **`PACKED=1` 不能消除该失败**（崩点与 `PACKED=0` 几乎相同），因此「单纯训练步显存不够」优先级下降。
- 两笔都**越过 7500** 中段 eval/save，致死点**不是** 7500 收尾。
- 崩在 **15000 最终 eval/save/ply 之前**（最后可见步仍是普通 train step）。
- bootstrap **只在 train exit 0 后** PUT；`--ply-steps` **只有 15000** → 97% 死掉 = 整晚作废。

**较可能（仍无最终日志，按修订优先级）：**

1. **临时盘写满（ENOSPC）** — 默认 ephemeral 约 **30 GiB**。zip≈2.2 GiB + 解压图 + COLMAP + `results/`（7500 ckpt 已写）随 densify 变大；后期某次写盘失败同样 Failed。与 `PACKED` 无关，更吻合「两笔同崩点」。
2. **后期 densify / 某步瞬时显存尖峰** — packed 仍不够；但不如磁盘假说干净（崩点不是 eval）。
3. **进程被宿主机 OOM-kill / 其它非 Python 退出** — 日志里可能根本没有 traceback。
4. **无法取证** — Failed 后立即 Delete。**这是流程缺陷**；两笔都因此丢了根因。

云日志里 tqdm 用 `\r` 原地刷新，Tail 往往只剩进度条，**看不到**崩溃栈。

---

## 5. 对策

### 5.1 只改 POST env（下一笔优先）

保持：`:1.0.0`、`imc-bp15yiuttnxf8fh9n9fy`、`ecs.gn7i-c16g1.4xlarge`、`gpu: 1`、cpu 16 / memory 60、现有 bootstrap、storage keys。建议 **`PACKED=1` 继续保留**（无害）。

| 改动 | 作用 | 状态 |
|---|---|---|
| `PACKED=1` | 降显存 | **已测；不能单独救命** |
| **`MAX_STEPS=7000` 或 `8000`** | 先验证 PUT 通路；在已知崩点前结束并走 ply | **下一笔优先** |
| `DATA_FACTOR=2` | 降分辨率/显存/磁盘占用 | 若短步仍挂再试 |

### 5.2 失败也留 ply（改 bootstrap / 训练参数）

- `--ply-steps` 加上 **7500**（脚本已有 mid save）。
- bootstrap：train 非 0 时若 `results/` 已有 `point_cloud_*.ply`，仍 PUT 最高 step。

### 5.3 改 `AliyunEciProvider`（与 Docker 对齐）

当前 `CreateContainerGroup` 未设下列字段：

| API | 建议 |
|---|---|
| `EphemeralStorage` | 额外 **50–80 GiB** |
| EmptyDir `Medium=Memory` 挂 `/dev/shm` | **8 GiB**，可去掉 `num_workers=0` 补丁 |

改完须重启 eci-manager。

### 5.4 更大显存

`gn7i-c16g1.4xlarge` 已是这块 A10 24 GB。更多显存需换卡（如 gn8ia L20），并确认 K 区库存与驱动 550。

### 5.5 失败可观测性

- Failed 时 **先** `DescribeContainerLog` 再 Delete（或延迟 1–2 分钟）。
- 最后 200 行写入任务 `failureReason` 或本地文件。
- `PYTHONUNBUFFERED=1`；tqdm `\r` 对云日志不友好。

### 5.6 不要做的

- `:latest`
- 不带 `imageSnapshotId` 在 GPU 上解压 10 GiB 镜像
- 杭州 B 区旧交换机
- `import base64,exec`
- 指望容器内 remount `/dev/shm`
- 把 AK 写进容器 env
- 主仓 `backend/` 在本次范围内对接 eci-manager

---

## 6. 验收清单

```text
[x] .env：真云 + OSS 内网 + presign=1440 + ECI_CREATE_TIMEOUT_SEC=1800
[x] OSS 可见 3dgs/raw/新文科楼.zip（2361154723 字节）
[x] 镜像 :1.0.0 + 缓存 imc-bp15yiuttnxf8fh9n9fy Ready
[x] GPU K 区 gn7i-c16g1.4xlarge 可创建；CUDA / COLMAP / 训练可跑到 97%
[x] DataLoader num_workers=0 补丁可开训（不再 step 0 shm 失败）
[x] PACKED=1 重测：同样训到 ~97% 后 Failed（不能单独解决）
[ ] POST 一笔出图 → SUCCEEDED
[ ] oss://eci-manager-bucket/3dgs/exports/新文科楼.ply 可下载
[x] 失败实例已 TERMINATED；无 Running 残留
[ ] 未把密钥提交 Git
```

记录（`PACKED=0`）：任务 `12638dd8-8094-40ea-a5d3-ecc5335cf6fc`；云 `eci-bp105vewz6y7d4h71vgu`；22:13–22:56；FAILED；ply 无。

记录（`PACKED=1` 重测）：任务 `b7ab85fd-6279-457e-804a-7f0d86d236d6`；云 `eci-bp147320zoipj7yaawmd`；23:06–23:47；最后可见 **14553/15000**；FAILED；ply 404。
