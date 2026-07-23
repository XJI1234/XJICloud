# eci-manager

独立 Spring Boot Demo：通过 REST API 管理阿里云 **ECI** 容器实例生命周期（创建 / 查询 / **删除**）。

> **详细文档（结构 / 接口 / 用法）：** [docs/设计与使用说明.md](docs/设计与使用说明.md)  
> **Agent 上下文（架构 / 决策 / 联调记忆）：** [AGENT.md](AGENT.md)

> **费用警告：** `eci.mock=false`（cloud profile）会创建真实云资源并产生账单。演示结束后务必调用 `DELETE` 删除实例。

对应 PRD：`ECI容器管理后端Demo产品需求文档(PRD).md` v1.1。本工程不修改主仓 `backend/`。

## 环境要求（Linux）

- JDK **17**
- Maven **3.9+**

```bash
java -version   # 须为 17.x
mvn -v          # 3.9+
```

### 若出现 `JAVA_HOME is not defined` / apt 找不到 `maven`

本仓已提供便携工具链（目录 `.tools/`，已 gitignore）。在项目根或本目录执行：

```bash
source ../.tools/env.sh    # 在 eci-manager/ 下
# 或：source /path/to/XJICloud/.tools/env.sh
```

会设置 `JAVA_HOME` 并把 JDK/Maven 加入 `PATH`。之后即可正常 `mvn`。

（可选）写入 shell 配置永久生效：把上面的 `source .../env.sh` 加到 `~/.bashrc`。

系统 apt 若报 `Unable to locate package maven`，可先跑 `sudo apt update`；WSL/精简镜像上更推荐直接用上面的 `.tools`。

## 快速开始（Mock，推荐本地联调）

```bash
cd eci-manager
source ../.tools/env.sh                    # 本机未装 JDK/Maven 时需要
export ECI_API_KEY=dev-eci-api-key-change-me   # 可选；默认见 application.yml
mvn spring-boot:run
# 或
mvn -DskipTests package && java -jar target/eci-manager-1.0.0.jar
```

默认：`server.port=8081`，`eci.mock=true`，H2 文件库 `./data/eci-manager`。

### 验收 curl

```bash
# 健康检查（无需 Api-Key）
curl -s http://127.0.0.1:8081/actuator/health

# 缺少 Api-Key → 401
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://127.0.0.1:8081/api/v1/eci/instances \
  -H "Content-Type: application/json" -d '{}'

# 创建（异步：立刻返回 CREATING，稍后变为 RUNNING）
curl -s -X POST http://127.0.0.1:8081/api/v1/eci/instances \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $ECI_API_KEY" \
  -d '{
    "name": "demo-1",
    "env": {
      "XJICLOUD_BACKEND_URL": "http://10.0.1.20:8080",
      "WORKER_SECRET": "change-me",
      "WORKER_NAME": "demo-1"
    }
  }'

# 列表 / 详情（将 {id} 换成返回的 UUID）
curl -s "http://127.0.0.1:8081/api/v1/eci/instances" -H "X-Api-Key: $ECI_API_KEY"
curl -s "http://127.0.0.1:8081/api/v1/eci/instances/{id}" -H "X-Api-Key: $ECI_API_KEY"
curl -s -X POST "http://127.0.0.1:8081/api/v1/eci/instances/{id}/refresh" -H "X-Api-Key: $ECI_API_KEY"

# 删除（关闭 = 删除云资源；mock 下本地变为 TERMINATED）
curl -s -X DELETE "http://127.0.0.1:8081/api/v1/eci/instances/{id}" -H "X-Api-Key: $ECI_API_KEY"
```

未传 `image` 时默认 `nginx:alpine`。

## 真实 ECI 模式

详细分场景配置（Mock / 本机环境变量 / ECS 角色）见 **[docs/设计与使用说明.md](docs/设计与使用说明.md) §4.2、§6**。

摘要：

1. `cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml`  
   填写 `api-key`、region、vswitch、security-group；**不要**指望 yml 里的 AccessKey 建客户端。  
2. 云身份：  
   - **有 ECS：** 绑 RAM 角色，可选 `eci.ecs-ram-role-name`  
   - **本机 WSL：** `export ALIBABA_CLOUD_ACCESS_KEY_ID` / `ALIBABA_CLOUD_ACCESS_KEY_SECRET`（STS 加 `SECURITY_TOKEN`）  
3. 启动：

```bash
export SPRING_PROFILES_ACTIVE=cloud,local
export ECI_API_KEY='<与 yml 中 api-key 一致>'
mvn spring-boot:run
```

常用环境变量：

| 变量 | 说明 |
|------|------|
| `ECI_API_KEY` | 请求头 `X-Api-Key` |
| `ECI_ECS_RAM_ROLE_NAME` | 可选，实例角色名 |
| `ECI_REGION_ID` | 如 `cn-hangzhou` |
| `ECI_VSWITCH_ID` / `ECI_SECURITY_GROUP_ID` | 网络（必填） |
| `ALIBABA_CLOUD_ACCESS_KEY_ID` / `SECRET` | 本机云凭证（非 yml） |
| `ALIBABA_CLOUD_SECURITY_TOKEN` | STS 时需要 |

创建可带 GPU：`instanceType`（如 `ecs.gn7i-c32g1.8xlarge`）+ `gpu: 1`。测完务必 **DELETE**（GPU 费用高；库存不足会 `NoStock`）。

默认镜像建议：`registry-vpc.cn-hangzhou.aliyuncs.com/eci_open/nginx:1.14.2`

## API 摘要


| 方法     | 路径                                   | 说明                          |
| ------ | ------------------------------------ | --------------------------- |
| POST   | `/api/v1/eci/instances`              | 异步创建，返回 `CREATING`          |
| DELETE | `/api/v1/eci/instances/{id}`         | 删除实例（幂等：已 `TERMINATED` 仍成功） |
| GET    | `/api/v1/eci/instances?status=`      | 列表                          |
| GET    | `/api/v1/eci/instances/{id}`         | 详情                          |
| POST   | `/api/v1/eci/instances/{id}/refresh` | 拉取云状态并回写                    |
| GET    | `/actuator/health`                   | 健康检查（无 Api-Key）             |


响应形态：`{ "success", "message", "data" }`。

## 与 XJICloud 的关系

本服务只负责云实例生命周期。后续可由主后端 HTTP 调用本服务，或迁入代码并替换为 CCI Provider（生产选型待定）。容器内业务通过 `env` 透传 `XJICLOUD_BACKEND_URL` / `WORKER_SECRET` / `WORKER_NAME` 等变量预留对接。

## 构建

```bash
mvn -DskipTests package
```

