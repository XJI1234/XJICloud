# 真云训练主后端配置与部署计划

> **For agentic workers:** 使用现有配置和启动命令执行以下步骤；不修改业务代码、训练脚本或 ECI 生命周期实现。

**Goal:** 为 `scripts/integration-training-dispatch-cloud.sh` 准备可复现的本地主后端启动配置，复用已有 OSS、ECI Manager 配置，并明确用户待填项。

**Architecture:** 主后端显式加载 `deploy/config/application-prod.yml`，再通过受保护的本地环境文件覆盖开发数据库、监听地址和 ECI Manager 参数。ECI Manager 继续使用原有 `.env`、`scripts/load-env.sh` 和 `scripts/start.sh`。使用独立 H2 数据库，避免旧业务任务在开启真云派发后意外产生费用。

**Tech Stack:** Spring Boot 3.3.5、Java 17、Maven、H2、Bash、Python 3。

**Spec:** 本次用户要求：为既有真云联调脚本处理主后端配置与部署，尽量不改代码，凭据和镜像由用户填写。

## 执行检查表

- [x] 检查现有配置、脚本、端口与密钥加载链路；不输出凭据。
- [x] 准备 `deploy/config/backend.env.local`，沿用根 `.gitignore` 的 `*.local` 规则。
- [x] 使用 Maven 构建并运行测试；检查环境文件语法与实际配置解析。
- [x] 启动主后端验证健康检查，再停止验证进程，不创建 ECI 任务。
- [x] 自我审查配置、用户操作步骤与费用/清理风险。
- [ ] 用户填写镜像、数据集与测试账号，确认云配置后启动两个服务并执行真云联调。

## 1. 当前采用的部署方式

这是本机真云联调部署，不是生产 systemd 安装：

- 当前环境没有 systemd 作为 PID 1，也没有监听 5432 的 PostgreSQL。
- 主后端监听 `127.0.0.1:8080`，通过 Maven 前台启动，保留完整日志和 Ctrl+C 停止能力。
- 主后端数据库为 `services/backend/data/cloud-training-db`；模型目录为 `services/backend/data/cloud-training-storage`。
- Redis 使用本机已运行的 `127.0.0.1:6379`，队列名覆盖为 `xjicloud:cloud-training:jobs`；不使用部署 YAML 中的云 Redis 占位地址，不清空已有 Redis 数据。
- 主后端 OSS、JWT、管理员初始账号沿用 `deploy/config/application-prod.yml`。
- ECI Manager 沿用 `eci-manager/.env` 和原有持久化数据库；本次不启动、不提交或取消已有云任务。
- `backend.env.local` 是需要 Bash 加载的本地补充文件，不是 systemd `EnvironmentFile`，不要直接复制成生产 `backend.env`。

显式加载部署 YAML，才不会再出现 `OSS endpoint 无效: null`。仅指定 `dev,local` 不会读取部署目录。环境变量的数据库与 Redis 覆盖确保加载部署 YAML 后仍使用隔离的 H2 和本机 Redis，而不是生产 PostgreSQL 或云 Redis 占位地址。

## 2. 填写配置

### ECI Manager HTTP API key

编辑 `eci-manager/.env`：

```dotenv
ECI_API_KEY='填写一个强随机秘密值'
```

已有值可以继续使用；不要把密钥发到聊天或提交到 Git。若需要生成新值，可在本机运行 `openssl rand -hex 32`，将结果填入该文件。

主后端会在加载本地环境文件时从 `ECI_API_KEY` 派生 `XJICLOUD_ECI_MANAGER_API_KEY`，不需要重复填写。修改后必须重启 ECI Manager 与主后端。

`ECI_API_KEY` 是两个服务之间的 HTTP 鉴权秘密，不是阿里云 AK/SK。保留或填写 `.env` 中的 ECI OpenAPI 凭据与网络配置：`ECI_ACCESS_KEY_ID/SECRET`（或原有 `ALIBABA_CLOUD_ACCESS_KEY_ID/SECRET`）、`ECI_REGION_ID`、`ECI_VPC_ID`、`ECI_VSWITCH_ID`、`ECI_SECURITY_GROUP_ID`。

### 主后端 OSS

编辑已有的 `deploy/config/application-prod.yml`，保留其他配置，只确认或调整 `xjicloud.oss`：

```yaml
xjicloud:
  oss:
    endpoint: https://oss-cn-hangzhou.aliyuncs.com
    region: cn-hangzhou
    bucket: "填写实际 Bucket 名称"
    access-key: "填写有该 Bucket 权限的 AccessKey ID"
    secret-key: "填写对应 AccessKey Secret"
    path-style-access: false
    presign-expiration-minutes: 1440
```

示例地域必须替换为实际地域。本机上传图片使用公网 endpoint；ECI Manager 为云容器生成预签名 URL，使用同地域内网 endpoint。检查 `eci-manager/.env`：

```dotenv
ECI_OSS_ENABLED=true
ECI_OSS_ENDPOINT=https://oss-cn-hangzhou-internal.aliyuncs.com
ECI_OSS_REGION=cn-hangzhou
ECI_OSS_BUCKET='与主后端同一个 Bucket'
ECI_OSS_ACCESS_KEY='有该 Bucket 权限的 AccessKey ID'
ECI_OSS_SECRET_KEY='对应 AccessKey Secret'
ECI_OSS_PATH_STYLE_ACCESS=false
ECI_OSS_PRESIGN_EXPIRATION_MINUTES=1440
ECI_CREATE_TIMEOUT_SEC=1800
```

现有配置检查时两端 Bucket、地域已一致，主后端使用公网、ECI Manager 使用内网，预签名时间已为 1440 分钟；不需要为了本次联调重新生成这些值。

两端的凭据还可能被各自数据库中的运行时当前钥覆盖；修改文件并重启不保证替换已有持久化凭据。需要换钥时优先使用现有主后端管理端“云钥列表”的 `ECI_OPENAPI` / `OSS` 选用能力，将对应当前钥推送给 ECI Manager。主后端“OSS 对象存储”页面可以测试其服务端连接。

### 3DGS 镜像、图片与测试账号

编辑 `deploy/config/backend.env.local` 中这些待填项：

```dotenv
XJICLOUD_ECI_MANAGER_TRAINING_IMAGE='填写已经上传且 ECI 可拉取的完整 3DGS 镜像地址:tag'
TRAINING_TEST_DATASET_DIR='/填写/实际/JPG图片目录'
TRAINING_TEST_USERNAME='填写主后端普通用户账号'
TRAINING_TEST_PASSWORD='填写该普通用户密码'
```

镜像必须是现有 3DGS 训练镜像，包含 `/opt/3dgs/docker/entrypoint.sh`，不能填写普通 nginx 镜像或本机 Docker 镜像名。主后端提交的镜像由 `XJICLOUD_ECI_MANAGER_TRAINING_IMAGE` 决定，不是 `ECI_DEFAULT_IMAGE`；联调脚本的镜像期望值已从同一变量派生。

使用 JPG/JPEG 多视角照片目录；目前不把 PNG/WebP 当作已经验证的真云路径。脚本会创建项目、上传数据集，但不会创建用户。

隔离数据库初次启动没有普通用户。先在 web2 注册用户，再填账号密码；管理员账号不能作为普通用户传入。也可以在运行联调的终端使用 `read -r -s -p 'User JWT: ' TRAINING_TEST_TOKEN; echo; export TRAINING_TEST_TOKEN` 输入普通用户 JWT，脚本会优先使用 token。不绕过注册验证码。

`TRAINING_TEST_TIMEOUT_SEC=25200` 已准备，避免脚本默认 1800 秒在训练仍运行时过早退出；观察超时不会自动取消云任务。

## 3. 构建与启动

以下命令均从仓库根目录执行，每个服务使用独立终端。

### 构建主后端

```bash
(cd services/backend && mvn -B package)
```

### 终端 A：启动 ECI Manager

确认 `.env` 使用 `SPRING_PROFILES_ACTIVE=cloud,local` 且没有 `ECI_MOCK=true`。其已有数据库可能包含待恢复任务，启动前检查已有任务与控制台资源；启动会恢复生命周期处理。

```bash
bash eci-manager/scripts/start.sh cloud
```

### 终端 B：启动主后端

必须先加载 ECI 环境、再加载主后端本地覆盖，顺序不能倒置。

```bash
(
  source eci-manager/scripts/load-env.sh
  set -a
  source deploy/config/backend.env.local
  set +a
  cd services/backend
  mvn spring-boot:run
)
```

保留此终端运行；Ctrl+C 停止服务。日志应包含 `dev`、`local`、H2 数据源、OSS S3 client 初始化以及后端 Started 信息。

### 终端 C：健康检查与普通用户注册

```bash
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health
curl --fail --silent --show-error http://127.0.0.1:8081/actuator/health
pnpm dev
```

打开 web2（通常为 `http://127.0.0.1:5176`）完成验证码注册。若需要管理端检查 OSS 或选用云钥，另开终端 `pnpm dev:admin`；管理员初始账号来自部署 YAML 的 `xjicloud.admin`，不是联调脚本的普通用户。

## 4. 执行真云联调

确认上述三个 `FILL_*` 类别（镜像、目录、账号/密码）均已替换，两个服务已重启并健康；检查 ECI 当前钥、网络与 Bucket 配置后再运行：

```bash
(
  source eci-manager/scripts/load-env.sh
  set -a
  source deploy/config/backend.env.local
  set +a
  bash scripts/integration-training-dispatch-cloud.sh
)
```

执行此命令会创建计费 GPU ECI 容器。本次配置准备与启动验证不执行该命令。

成功标准为主后端任务 `COMPLETED`、ECI 任务 `SUCCEEDED`、对应实例 `TERMINATED`。仅主后端健康、数据上传成功或任务进入 `QUEUED` 都不代表真云训练成功。

脚本超时、断网或 Ctrl+C 不会自动取消任务。记录输出中的 `jobId`、`eciTaskId`，在 ECI Manager 操作台检查任务；需要取消时使用现有任务取消功能（对应 `POST /api/v1/eci/tasks/{id}/cancel`），并等待实例确认为 `TERMINATED`，同时核对云控制台无残留资源。不要只关闭终端就认为计费已停止。

## 5. 安全与验证边界

- 本次 `mvn -B package` 构建成功，63 项后端测试通过；联调驱动的 2 项 Python 单测通过。
- 已按本文命令启动主后端，实际确认 `dev,local`、隔离 H2、OSS client 初始化与 `/actuator/health` 返回 `{"status":"UP"}`。验证进程随后已停止，ECI Manager 未启动，真云联调未执行。
- `.env`、部署 YAML、本地环境文件保持 `0600`；不要打印环境变量全集、使用 `set -x` 或上传数据库/启动配置。
- 本地环境文件由 `*.local` 忽略，运行时 H2 和模型文件由 `services/backend/data/` 忽略。
- 不改变 PLY/SPZ 本地存储策略、不启动旧 Redis Worker、不修改业务代码和联调脚本。
- 启动主后端与健康检查不验证 OSS 实际读写、ECI 创建、镜像拉取或训练结果；这些在用户确认并执行真云联调后才能证明。
- 修改本地文件中的镜像、密钥或 OSS 初始配置后，按需重启对应服务，不能只重新执行联调脚本。
