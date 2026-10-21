# eci-manager

通过 HTTP API 或内置操作台提交任务，在阿里云 ECI 中运行容器，并在任务结束后自动删除实例。也支持手动创建、查询和删除实例。

服务基于 Java 17 / Spring Boot，默认端口为 `8081`。可独立运行，无需启动主项目的前后端、Redis 或外部数据库；任务、实例和当前云凭据默认保存在本地 H2 数据库中。

- [项目结构](#项目结构)
- [启动服务](#启动服务)
- [配置密钥与真云环境](#配置密钥与真云环境)
- [修改容器参数](#修改容器参数)
- [使用示例](#使用示例)
- [相关文档](#相关文档)

## 项目结构

```text
eci-manager/
├── src/main/java/com/xjicloud/eci/
│   ├── task/、instance/       # 任务与实例 API
│   ├── provider/             # 阿里云 ECI / Mock 实现
│   ├── lifecycle/            # 状态轮询、自动清理与重启恢复
│   ├── credential/、oss/     # 当前云凭据、OSS 预签名
│   └── config/、auth/        # 服务配置与 HTTP 鉴权
├── src/main/resources/
│   ├── application*.yml     # 默认配置与运行模式
│   └── static/              # 内置操作台，无需前端构建
├── src/test/                # 自动化测试
├── scripts/                 # 启动、普通任务、OSS 与 3DGS 示例
├── test-images/             # 测试镜像源码
├── docs/                    # API、配置与使用说明
├── .env.example             # 环境变量模板
└── pom.xml
```

运行后生成的 `data/` 保存 H2 数据。重启和重新部署时应保留此目录，服务才能继续恢复任务与清理动作。

## 启动服务

需要自行安装 JDK 17、Maven 3.9+。先按下面的命令从仓库根目录进入 `eci-manager/`，后续命令均在此目录执行。已有本地 `.tools/env.sh` 时可加载其中的工具链；该文件不随仓库交付。

### Mock 模式

Mock 模式不调用 ECI，也不实际拉取镜像、执行容器程序。无需配置阿里云 AccessKey。

```bash
cd eci-manager
if [ -f ../.tools/env.sh ]; then source ../.tools/env.sh; fi

export ECI_API_KEY=dev-eci-api-key-change-me
SPRING_PROFILES_ACTIVE=dev ECI_MOCK=true ECI_OSS_ENABLED=false \
  mvn spring-boot:run
```

内置操作台位于 <http://127.0.0.1:8081/>，使用上面设置的 HTTP Api-Key，顶栏显示当前模式。操作台仍有已知问题，当前阶段请按下文的 HTTP API 示例操作。

健康检查无需 Api-Key：

```bash
curl -fsS http://127.0.0.1:8081/actuator/health
```

服务默认尝试打开浏览器。无桌面环境可在启动前设置 `export ECI_CONSOLE_OPEN_BROWSER=false`。修改端口用 `SERVER_PORT`，例如 `SERVER_PORT=18081 mvn spring-boot:run`。

### 构建后运行

```bash
mvn test
mvn package
java -jar target/eci-manager-1.0.0.jar
```

未指定 profile 时默认为 `dev`（Mock）。Java 和 Maven 不会自动加载 `.env`；使用该文件时，先执行 `source scripts/load-env.sh`，再运行 Maven 或 JAR。

部署到服务器时，可将构建好的 JAR、`scripts/load-env.sh` 和自行填写的 `.env` 放到固定目录（例如 `/opt/eci-manager`），从该目录运行：

```bash
cd /opt/eci-manager
source scripts/load-env.sh
ECI_CONSOLE_OPEN_BROWSER=false java -jar eci-manager-1.0.0.jar
```

默认 H2 路径相对于启动目录。请固定运行目录（使用 systemd 时设置 `WorkingDirectory`），让运行用户拥有 `data/` 写权限；更换运行目录会使用另一份数据库。当前云 Secret 以明文形式持久化在数据库中，数据库文件、`.env` 及备份都应限制访问权限，不能随 JAR 打包分发。

真云部署要求 eci-manager 主机可以解析并通过 HTTPS 访问阿里云 ECI OpenAPI，ECI 容器可以拉取镜像。与主项目集成时，还需让 8080 能访问 8081；使用 OSS `storage` 的任务需让容器访问对应地域的 OSS 内网 endpoint。

## 配置密钥与真云环境

### 两类密钥

| 配置 | 用途 | 修改方式 |
|------|------|----------|
| `ECI_API_KEY` | 客户端访问本服务，放在请求头 `X-Api-Key` 中 | 修改环境变量或 YAML 后重启服务 |
| ECI / OSS AccessKey | 服务访问阿里云 ECI 或签发 OSS URL | 在主项目 Admin 管理，由主后端 `8080` 调用 `8081` API 推送并热更新 |

`/api/v1/eci/` 下的所有接口都需要 `X-Api-Key`，包括 `/meta`。操作台静态页面和健康检查无需此请求头。

### 通过 8080 管理和热切换云密钥

与主项目一起使用时，ECI 和 OSS 的 AccessKey 统一在 Admin 中新增、测试和选用，调用链路为：

```text
Admin（管理员 JWT）
  → 主后端 8080：选用某把密钥，指定 ECI_OPENAPI 或 OSS
  → eci-manager 8081：携带 X-Api-Key，推送该资源的 AK / SK
  → 保存当前凭据并立即更新对应客户端，无需重启
```

先在 **8080 的部署环境**中配置与 `eci-manager` 的连接：

```dotenv
XJICLOUD_ECI_MANAGER_BASE_URL=http://127.0.0.1:8081
XJICLOUD_ECI_MANAGER_API_KEY=your-eci-manager-api-key
```

`XJICLOUD_ECI_MANAGER_API_KEY` 必须与 8081 的 `ECI_API_KEY` 一致。上面的回环地址适用于两个服务共享主机网络的部署；若分别运行在容器中，即使在同一容器网络，也应使用服务名地址（如 `http://eci-manager:8081`），跨主机则填写可达的主机地址。

管理员 JWT 用于访问 8080；8080 访问 8081 时使用上面的 HTTP Api-Key，两者用途不同。

在 Admin 中新增密钥并绑定资源，然后对每个资源分别测试、选用。新增或编辑目录项只保存信息，执行“选用”才会推送到 `eci-manager`。

| 操作 | 8080 接口 | 8080 调用的 8081 接口 |
|------|-----------|----------------------|
| 测试已保存密钥 | `POST /api/v1/admin/cloud-access-keys/{id}/test` | `POST /api/v1/eci/runtime-credentials/test` |
| 选用密钥并热切换 | `POST /api/v1/admin/cloud-access-keys/{id}/select` | `PUT /api/v1/eci/runtime-credentials` |

两个 8080 请求的 body 均指定 `{"resource":"ECI_OPENAPI"}` 或 `{"resource":"OSS"}`；8080 从目录中读取 AK / SK 后传给 8081。ECI 与 OSS 分别选用，切换其中一个不会改变另一个；同一把密钥也可以绑定并分别用于两个资源。测试不会保存或切换当前凭据，Mock 模式不支持 ECI 真云凭据测试。

选用后，8081 持久化当前凭据并热更新 ECI 或 OSS 客户端，8080 收到成功响应后记录目录的选中状态。可通过操作台或 `GET /api/v1/eci/runtime-credentials` 核对 8081 当前凭据的脱敏信息。若提示“已推送但保存当前选用失败”，重试选用。完整新增、轮换和删除流程见[云访问密钥管理说明](docs/云访问密钥管理说明.md)。

### 使用 `.env` 启动真云服务

```bash
cp .env.example .env
# 编辑 .env，替换下面列出的配置与模板中的占位值
./scripts/start.sh
```

`.env` 负责服务鉴权、网络和首次启动配置。日常 ECI / OSS 换钥按上面的 8080 链路操作；AccessKeyId 与 Secret 必须成对填写，不要将实际密钥提交到 Git。

| 变量 | 配置说明 |
|------|----------|
| `SPRING_PROFILES_ACTIVE` | 真云用 `cloud,local`；Mock 用 `dev` |
| `ECI_API_KEY` | 自行设置的服务访问密钥，真云部署时替换开发默认值 |
| `ECI_ACCESS_KEY_ID` / `ECI_ACCESS_KEY_SECRET` | 首次空库启动的 ECI 凭据种子；后续当前密钥由 8080 推送 |
| `ECI_REGION_ID` | ECI 所在地域，如 `cn-hangzhou` |
| `ECI_VSWITCH_ID` / `ECI_SECURITY_GROUP_ID` | 真云必填，使用目标地域、VPC 内的交换机与安全组 |
| `ECI_VPC_ID` | 可选，填写对应 VPC |
| `ECI_CREATE_TIMEOUT_SEC` | 创建超时，默认 `300` 秒；大镜像可调为 `1800` |

模板默认注释了所有云凭据种子。使用静态 AK 时，取消 `ECI_ACCESS_KEY_*` 的注释并填写真实凭据；使用 ECS RAM 角色时，保持 ECI 和 `ALIBABA_CLOUD_*` 两组 AK 种子为空，且数据库中不能已有当前 ECI AK。兼容变量只会由 `load-env.sh` 在 ECI 凭据为空且未设 STS token 时映射为初始化值；直接运行 Java 不会读取这两个兼容变量。RAM 角色等配置见[设计与使用说明](docs/设计与使用说明.md)。

首次空库启动真云模式时，先配置 ECI 初始凭据或可用的 RAM 角色等云身份，之后由 8080 推送选用的密钥。静态 AK 在启动时不会验证有效性或云权限；服务启动成功后，仍需通过 8080 的测试接口检查凭据。独立部署时可直接调用 8081 的 `POST /api/v1/eci/runtime-credentials/test`，详见 [API 文档](docs/API文档.md)。

`.env` 中的 ECI / OSS 凭据仅在数据库对应记录不存在时用于初始化，不能覆盖 8080 已推送的当前凭据；若曾保存过模板占位值，通过 8080 选用有效密钥来替换。

HTTP Api-Key 仍由进程配置决定，修改后需要重启，并同步更新 8080 的连接配置。

`start.sh` 要求 `.env` 存在。它也接受 `mock` / `cloud` 参数，但随后加载的 `.env` 会覆盖同名环境变量；切换模式时请同步修改 `.env` 中的 `SPRING_PROFILES_ACTIVE`。若另设了 `ECI_MOCK`，它会覆盖 profile 中的模式配置，真云应设为 `false` 或移除。切换模式需要重启，并在操作台确认顶栏显示 `CLOUD`。

真云模式会创建计费资源。任务结束后会自动清理，手动实例需要自行删除；使用后请确认 `cleanupConfirmed=true` 或在 ECI 控制台确认无残留。

### 按需启用 OSS

普通容器任务可跳过此项。任务需要下载输入或上传结果时，在 `.env` 中填写：

```dotenv
ECI_OSS_ENABLED=true
ECI_OSS_ENDPOINT=https://oss-cn-hangzhou-internal.aliyuncs.com
ECI_OSS_REGION=cn-hangzhou
ECI_OSS_BUCKET=your-bucket
ECI_OSS_PRESIGN_EXPIRATION_MINUTES=1440
```

OSS 的 AK / SK 通过 8080 选用 `OSS` 资源后推送。`.env.example` 中已注释的 `ECI_OSS_ACCESS_KEY` / `ECI_OSS_SECRET_KEY` 仅用于可选的首次初始化，走 8080 管理时保持注释。热切换只更新凭据，OSS 的启用状态、endpoint、地域和 bucket 仍由本服务配置决定，修改后需要重启。

真云 ECI 任务使用对应地域的 OSS 内网 endpoint。预签名 URL 必须覆盖镜像启动、训练和上传的总耗时；3DGS 长任务建议使用 `1440` 分钟，默认值仅为 `60` 分钟。

服务通过预签名 URL 让容器读写对象，不向容器注入 AccessKey。镜像中的程序仍需自行下载和上传文件；任务请求的 `storage` 字段及注入的环境变量见 [API 文档](docs/API文档.md)。

## 修改容器参数

### 单个任务

在操作台表单或 `POST /api/v1/eci/tasks` 的 JSON 请求体中设置参数。请求值优先于服务默认值，修改只影响新提交的任务。

| 字段 | 含义与默认值 |
|------|--------------|
| `image` | 必填，容器镜像地址；真云需使用 VPC 可达的镜像仓库 |
| `command` / `args` | 字符串数组，覆盖容器启动命令与参数 |
| `env` | 容器环境变量，JSON 对象 |
| `cpu` / `memory` | CPU 核数 / 内存 GiB；默认 `1` / `2` |
| `instanceType` / `gpu` | 可选，云实例规格 / GPU 数量，须匹配地域库存与资源规格 |
| `imageSnapshotId` | 可选，已有镜像缓存 ID；需在目标地域可用 |
| `shmSize` | 如 `256Mi`、`8Gi`；未配置时保留 ECI 默认 `64Mi` |
| `restartPolicy` | 默认 `Never`；也支持 `OnFailure`、`Always` |
| `maxRuntimeSec` | 正整数，任务最长运行秒数；默认 `14400` |
| `clientRequestId` | 可选，提交幂等键；相同值返回已有任务，新任务应换新值 |
| `storage` | 可选，OSS 输入输出配置；要求服务已启用并配置 OSS |

`shmSize` 使用内存型卷，计入容器内存，必须大于 `64Mi` 且严格小于 `memory`。只对需要的任务放大，避免全局设置 `8Gi` 后让小内存任务无法提交。

一次性任务使用 `Never`。`OnFailure` 可能因失败反复重启；`Always` 通常需要超时或主动取消才会结束，会延长计费时间。

### 服务默认值

可将 `src/main/resources/application-local.yml.example` 复制为同目录下的 `application-local.yml`，按需修改，例如：

```yaml
eci:
  default-cpu: 2.0
  default-memory: 4.0
  default-restart-policy: Never
  default-max-runtime-sec: 3600
```

启用 `local` profile 后重启生效，真云使用 `cloud,local`。复制的模板还包含 `mock: false` 和示例 Api-Key，使用前要替换为自己的配置；用于 Mock 时，把 `mock` 改为 `true`，以 `dev,local` 启动。

任务接口始终要求显式提供 `image`；`default-image` 只在手动创建实例等场景作为缺省值。

## 使用示例

### 提交、查询和取消任务

以下示例用于已启动的 Mock 服务，需 `curl` 和 Python 3。另开终端，设置与服务一致的 Api-Key：

```bash
export ECI_BASE=http://127.0.0.1:8081
export ECI_API_KEY=dev-eci-api-key-change-me

TASK_JSON=$(curl -fsS "$ECI_BASE/api/v1/eci/tasks" \
  -H "X-Api-Key: $ECI_API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "hello-python",
    "image": "python:3.12-alpine",
    "command": ["python3", "-c"],
    "args": ["print(\"hello from ECI\")"],
    "cpu": 1,
    "memory": 2,
    "restartPolicy": "Never",
    "maxRuntimeSec": 60
  }')
printf '%s\n' "$TASK_JSON"
TASK_ID=$(printf '%s' "$TASK_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["id"])')

# 查询任务；API 响应为 { success, message, data }
curl -fsS "$ECI_BASE/api/v1/eci/tasks/$TASK_ID" \
  -H "X-Api-Key: $ECI_API_KEY"

# 查看日志尾部
curl -fsS "$ECI_BASE/api/v1/eci/tasks/$TASK_ID/logs?tailLines=200" \
  -H "X-Api-Key: $ECI_API_KEY"
```

提交返回 `STARTING` 表示已受理。Mock 默认在创建约 3 秒后模拟云端成功，还需等待轮询和清理完成，任务才变为 `SUCCEEDED`。可重复查询任务详情，查看 `instanceStatus`、`cloudStatus` 与 `cleanupConfirmed`。

需要提前结束尚未完成的任务时：

```bash
curl -fsS -X POST "$ECI_BASE/api/v1/eci/tasks/$TASK_ID/cancel" \
  -H "X-Api-Key: $ECI_API_KEY"
```

取消先进入 `CANCELLING`，确认实例已删除后才进入 `CANCELLED`。`SUCCEEDED` 已确认清理；`FAILED` 可能仍在抓取日志、等待清理或重试删除，应继续检查 `cleanupConfirmed`。成功任务不保存容器日志尾部；失败或超时任务会在删除前尝试保存 `containerLogTail`。

所有示例中的 ID 都是本服务返回的本地 UUID。手动创建的实例不会随任务轮询自动删除，使用后须调用 `DELETE /api/v1/eci/instances/{id}`，详见 [API 文档](docs/API文档.md)。

### 真云任务脚本

这些脚本从 `.env` 加载配置，运行前需启动真云服务并准备可用镜像。

| 脚本 | 用途与前提 |
|------|------------|
| [run-hello-python.sh](scripts/run-hello-python.sh) | 普通 Python 任务；需要 `jq`，通过 `CLOUD_IMAGE` 指定可达的 Python 镜像 |
| [debug-oss-smoke.sh](scripts/debug-oss-smoke.sh) | 验证容器向 OSS 上传结果；需要 Python 3 和已配置的 OSS |
| [submit-3dgs-standard.sh](scripts/submit-3dgs-standard.sh) | 提交 3DGS 训练；需要 Python 3、兼容的 3DGS 镜像、OSS 输入和可用镜像缓存 |

3DGS 脚本使用 `16` 核 CPU、`60` GiB 内存、`1` 张 GPU、`8Gi` 共享内存，最长运行 `21600` 秒。先上传 `3dgs/raw/<建筑名>.zip`，再显式指定自己的镜像、缓存和训练步数：

```bash
GS_IMAGE='你的可达的3DGS镜像地址' \
IMAGE_SNAPSHOT_ID='imc-你的同地域镜像缓存ID' \
BUILDING='某楼' MAX_STEPS=15000 \
  ./scripts/submit-3dgs-standard.sh
```

结果上传到 `3dgs/exports/某楼.ply`。脚本当前默认 `MAX_STEPS=2000`，并带有特定环境的镜像与缓存默认值；请显式覆盖。完整输入格式、镜像要求和排错方法见 [3DGS ECI 使用说明](docs/3dgs-ECI使用说明.md)，参数默认值以当前脚本为准。

脚本默认最多轮询约 5 小时，停止轮询不会取消任务。若仍未结束，请继续查询任务状态或主动取消，并确认资源已清理。

## 相关文档

- [API 文档](docs/API文档.md)：接口、字段、状态码、OSS `storage` 与云凭据 API。
- [设计与使用说明](docs/设计与使用说明.md)：完整配置、网络、OSS，以及旧版本升级与异常实例的人工核对。
- [云访问密钥管理说明](docs/云访问密钥管理说明.md)：主项目 Admin 的密钥新增、测试、选用与轮换。
- [3DGS ECI 使用说明](docs/3dgs-ECI使用说明.md)：训练数据准备、标准任务与故障排查。
- [测试说明](docs/测试说明.md)：Mock 验证与真云联调步骤。
