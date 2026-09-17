# eci-manager

独立 Spring Boot 服务：任务驱动的阿里云 **ECI** 容器编排（提交任务 → 创建实例 → 轮询终态 → 自动删除），并保留手动 `/instances` 运维能力。

> **API 文档：** [docs/API文档.md](docs/API文档.md)  
> **设计与使用：** [docs/设计与使用说明.md](docs/设计与使用说明.md)  
> **逐步测试：** [docs/测试说明.md](docs/测试说明.md)  
> **3DGS 真云出图：** [docs/3dgs-ECI使用说明.md](docs/3dgs-ECI使用说明.md)  
> **Agent 上下文：** [AGENT.md](AGENT.md)  
> **实现计划：** [docs/superpowers/plans/2026-07-27-eci-task-orchestration.md](../docs/superpowers/plans/2026-07-27-eci-task-orchestration.md)  
> **产品需求：** [`ECI任务驱动容器编排服务产品需求文档(PRD).md`](../ECI任务驱动容器编排服务产品需求文档(PRD).md) **v2.0.3**

> **费用警告：** `eci.mock=false`（cloud profile）会创建真实云资源并产生账单。任务路径在终态后会自动删；仍请到控制台确认无残留。手动实例测完务必 `DELETE`。

## 环境要求（Linux）

- JDK **17**
- Maven **3.9+**

```bash
java -version   # 须为 17.x
mvn -v          # 3.9+
```

### 若出现 `JAVA_HOME is not defined`

```bash
source ../.tools/env.sh    # 在 eci-manager/ 下
```

## 配置（`.env`，推荐）

真云联调时，HTTP Api-Key、VPC、**当前钥种子**建议统一写在 **`eci-manager/.env`**（种子只在库行缺失时写入）。

```bash
cd eci-manager
cp .env.example .env
# 编辑 .env 后：
chmod +x scripts/*.sh
./scripts/start.sh
```

| 变量 | 说明 |
|------|------|
| `SPRING_PROFILES_ACTIVE` | 真云：`cloud,local`；Mock：`dev` |
| `ECI_API_KEY` | HTTP 请求头 `X-Api-Key`（可再经 `PUT /credentials/http-api-key` 轮换） |
| `ECI_ACCESS_KEY_ID` / `SECRET` | ECI 当前钥**种子**；成对则 `STATIC_AK`。已落盘的热切结果启动时不会被 `.env` 打回 |
| `ALIBABA_CLOUD_ACCESS_KEY_ID` / `SECRET` | Java **不读**；仅 `load-env.sh` 在 `ECI_ACCESS_KEY_*` 为空且无 STS token 时映射为种子 |
| `ECI_REGION_ID` / `ECI_VPC_ID` / `ECI_VSWITCH_ID` / `ECI_SECURITY_GROUP_ID` | 创建容器网络 |

**脚本：**

- `scripts/load-env.sh` — 加载 `.env`；`ALIBABA_CLOUD_*` 仅在此时映射到 `ECI_ACCESS_KEY_*`。无 `.env` 时用 Mock 默认 Api-Key。
- `scripts/start.sh [mock|cloud]` — 要求已有 `.env`，打印将打开的 `http://127.0.0.1:8081/`，然后 `mvn spring-boot:run`

curl 联调（另开终端）：

```bash
cd eci-manager
source scripts/load-env.sh
curl -s -H "X-Api-Key: $ECI_API_KEY" http://127.0.0.1:8081/actuator/health
```

 `application-local.yml` 仍被保留（见 [设计与使用说明](docs/设计与使用说明.md) §4.2）。省事。

## 快速开始（Mock，推荐）

```bash
cd eci-manager
source ../.tools/env.sh
./scripts/start.sh mock
# 浏览器打开 http://127.0.0.1:8081/ ，Api-Key 见 README / .env 的 ECI_API_KEY
# 无桌面：ECI_CONSOLE_OPEN_BROWSER=false
```

也可用 `mvn spring-boot:run`（默认 Mock）。`server.port=8081`，创建约 `mock-auto-complete-sec`（默认 3）秒后 Mock 云状态变为 `Succeeded`，watcher 自动删实例并将任务置 `SUCCEEDED`。

顶栏徽章为 `MOCK` 或 `CLOUD`（后者带计费警告）。切换须重启：`./scripts/start.sh mock` 或 `./scripts/start.sh cloud`。真云提交前操作台会二次确认。

无 `.env` 时也可：

```bash
export ECI_API_KEY=dev-eci-api-key-change-me
mvn spring-boot:run
```

### 任务全流程 curl（Mock）

```bash
export ECI_API_KEY=dev-eci-api-key-change-me

curl -s http://127.0.0.1:8081/actuator/health

# 缺 Api-Key → 401
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://127.0.0.1:8081/api/v1/eci/tasks \
  -H "Content-Type: application/json" -d '{"image":"nginx:alpine"}'

# 提交任务
TASK=$(curl -s -X POST http://127.0.0.1:8081/api/v1/eci/tasks \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $ECI_API_KEY" \
  -d '{
    "name": "demo-task",
    "image": "nginx:alpine",
    "restartPolicy": "Never",
    "maxRuntimeSec": 60,
    "cpu": 1.0,
    "memory": 2.0
  }')
echo "$TASK"
TASK_ID=$(echo "$TASK" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1)
INSTANCE_ID=$(echo "$TASK" | sed -n 's/.*"instanceId":"\([^"]*\)".*/\1/p' | head -1)

# 查询任务 / 关联实例
curl -s "http://127.0.0.1:8081/api/v1/eci/tasks/$TASK_ID" -H "X-Api-Key: $ECI_API_KEY"
curl -s "http://127.0.0.1:8081/api/v1/eci/instances/$INSTANCE_ID" -H "X-Api-Key: $ECI_API_KEY"

# 等待若干秒后任务应变 SUCCEEDED，实例 TERMINATED（Mock 自动终态）
sleep 8
curl -s "http://127.0.0.1:8081/api/v1/eci/tasks/$TASK_ID" -H "X-Api-Key: $ECI_API_KEY"
```

取消（非终态）：

```bash
curl -s -X POST "http://127.0.0.1:8081/api/v1/eci/tasks/$TASK_ID/cancel" \
  -H "X-Api-Key: $ECI_API_KEY"
```

### 手动实例 curl

```bash
curl -s -X POST http://127.0.0.1:8081/api/v1/eci/instances \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $ECI_API_KEY" \
  -d '{"name":"manual-1"}'

curl -s -X DELETE "http://127.0.0.1:8081/api/v1/eci/instances/{id}" \
  -H "X-Api-Key: $ECI_API_KEY"
```

手动实例**不会**因 Mock 云终态自动删本地记录；需人工 `DELETE`。

## 重启策略与成本

| `restartPolicy` | 行为 | 成本风险 |
|-----------------|------|----------|
| `Never`（默认） | 进程结束 → Succeeded/Failed → **自动删** | 最低；适合一次性任务 |
| `OnFailure` | 失败可能重启；成功终态或超时结束 | 失败重试会拉长计费 |
| `Always` | 通常仅超时 / cancel / 人工删结束 | **最高**；慎用 GPU |

## 3DGS 类一次性任务示例

```bash
curl -s -X POST http://127.0.0.1:8081/api/v1/eci/tasks \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $ECI_API_KEY" \
  -d '{
    "name": "3dgs-job",
    "image": "registry-vpc.cn-hangzhou.aliyuncs.com/your-ns/3dgs:latest",
    "command": ["python", "train.py"],
    "args": ["--source_path", "/data/scene", "--iterations", "30000"],
    "cpu": 8,
    "memory": 32,
    "instanceType": "ecs.gn7i-c32g1.8xlarge",
    "gpu": 1,
    "restartPolicy": "Never",
    "maxRuntimeSec": 14400,
    "env": {
      "CUDA_VISIBLE_DEVICES": "0"
    }
  }'
```

真云下请用 VPC 可达镜像；测完确认任务已 `SUCCEEDED`/`FAILED`/`CANCELLED`，控制台无残留容器组。

## 真实 ECI 模式

1. `cp .env.example .env`，填写 Api-Key、网络、种子 `ECI_ACCESS_KEY_*`（或 `ALIBABA_CLOUD_*` 仅供脚本映射）  
2. `./scripts/start.sh cloud`（或无参数沿用 `.env` 的 profile）

详解见 [docs/设计与使用说明.md](docs/设计与使用说明.md) §4.2。

## API 摘要

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 本地操作台（无 Api-Key） |
| GET | `/api/v1/eci/meta` | 模式与默认规格（无密钥） |
| POST | `/api/v1/eci/tasks` | 提交任务（`image` 必填） |
| GET | `/api/v1/eci/tasks` / `/{id}` | 列表 / 详情 |
| GET | `/api/v1/eci/tasks/{id}/logs` | 日志尾部（运行中拉云；终态走落库） |
| POST | `/api/v1/eci/tasks/{id}/cancel` | 取消（幂等） |
| POST | `/api/v1/eci/instances` | 手动创建（可选默认 image） |
| DELETE | `/api/v1/eci/instances/{id}` | 删除（幂等） |
| GET | `/api/v1/eci/instances` / `/{id}` | 列表 / 详情 |
| POST | `/api/v1/eci/instances/{id}/refresh` | 拉云状态 |
| GET | `/api/v1/eci/runtime-credentials` | 当前云钥 hint（无 SK） |
| PUT | `/api/v1/eci/runtime-credentials` | 覆盖该 resource 当前钥并热切 |
| POST | `/api/v1/eci/runtime-credentials/test` | 测草稿或已保存；不落盘 |
| PUT | `/api/v1/eci/credentials/http-api-key` | 轮换 HTTP Api-Key |
| POST | `/api/v1/eci/credentials/revoke-previous-api-key` | 吊销上一把 HTTP 钥 |
| GET | `/actuator/health` | 健康检查（无 Api-Key） |

响应：`{ "success", "message", "data" }`。当前钥与 HTTP 钥完整字段见 [docs/API文档.md](docs/API文档.md) §5。8081 **没有**多钥目录（列表在主后端第二步）。

## 一期验收清单（mock）

- [x] Linux 下 `mvn spring-boot:run` / `java -jar` 可启动  
- [x] 正确 Api-Key 可提交任务（镜像、command、args、规格、GPU、重启、超时）  
- [x] 可查询任务与实例，本地 ID 可关联  
- [x] `restartPolicy=Never` 时终态后自动删除实例  
- [x] 超过 `maxRuntimeSec` → 强制删 + 任务 `FAILED`/`TIMEOUT`  
- [x] `/instances` 手动能力仍可用  
- [x] 错误/缺失 Api-Key → 401 且不建云资源  
- [x] `mock=true` 全流程不调云可演练（`mvn test` + 文档 curl）
- [x] 本地操作台 `GET /` 无需 Api-Key；`/meta` 与 `/tasks/{id}/logs` 需 Key

与 PRD §1.4 对照；逐步命令见 [docs/测试说明.md](docs/测试说明.md) §11。

## 构建

```bash
mvn -DskipTests package
# 或全量测试
mvn test
```
