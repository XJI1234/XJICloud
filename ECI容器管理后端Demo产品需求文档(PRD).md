# ECI 容器管理后端 Demo — 产品需求文档 (PRD)

| **文档版本** | **创建日期** | **状态** | **适用对象** |
| ------------ | ------------ | -------- | ------------ |
| **v1.1** | 2026-07-23 | 关键决策已锁定 / 待实现计划 | 后端开发、容器运维、测试 |
| **阶段定位** | **选项 C：独立 Demo / 原型** | 先验证能力，**本阶段不做**并入 XJICloud 主后端的实现 | |
| **关联文档** | [训练队列驱动的 CCI GPU Worker 自动启停 PRD](训练队列驱动的CCI-GPU-Worker自动启停产品需求文档(PRD).md) | 后续集成参考；**本 Demo 与之并行，不互相替代** | |

> **本阶段交付物：本 PRD（及评审修订）。未批准实现计划前：不写业务代码、不改主仓 `backend/`、不调云资源。**

---

## 0. 已确认决策（v1.1）

| # | 议题 | 结论 | 影响摘要 |
|---|------|------|----------|
| 1 | 仓库位置 | **主仓 Monorepo** 子目录（建议 `eci-manager/`） | 与 XJICloud 同仓；独立 Maven 工程，不改动现有 `backend/` |
| 2 | 鉴权 | **Api-Key**（请求头 `X-Api-Key`） | 所有 `/api/v1/eci/**` 须校验；密钥仅配置/环境变量，不进 Git |
| 3 | 联调镜像 | **小镜像**，默认 `nginx:alpine` | 只验证创建/Running/删除；不验证训练与 Worker 注册 |
| 4 | 关闭语义 | **删除**实例（非停止保留） | `DELETE` → 调云删除 API → 本地 `TERMINATED`；省费用、状态简单 |
| 5 | 生产选型 | **待定** | Demo 继续用 ECI；正式走 ECI / CCI / 双 Provider 留到集成评审（D4/I1） |

### 运行与构建约束（已确认）

| 项 | 要求 |
|----|------|
| **运行环境** | **Linux**（开发验证与演示均以 Linux 为准；路径、脚本、systemd/curl 示例按 Linux 编写） |
| **JDK** | **17**（`java -version` 须为 17.x；与主仓 Spring Boot 3 一致） |
| **构建** | **Maven 3.9+**（`mvn -v`；使用项目自带或系统 Maven，`mvn spring-boot:run` / `mvn -DskipTests package`） |
| **说明** | Windows 上可写代码，但验收与联调文档以 Linux 命令为准 |

---

## 1. 背景与目标

### 1.1 背景

团队需要掌握并验证：**用 Spring Boot 调用阿里云弹性容器实例（ECI）OpenAPI，对算力容器做实时生命周期控制**（创建、查询、删除等）。

当前 XJICloud 主仓已有：

- 训练任务队列 + GPU Worker 主动拉活协议（`/api/v1/worker/*`）
- 生产文档偏向 **CCI** 按需启停（尚无自动伸缩实现）

学长要求本阶段先做 **独立 Demo**：验证 ECI 控制链路，再考虑与主平台对接。避免一上来改动主后端、训练队列与鉴权体系。

### 1.2 核心目标

| 目标 | 说明 |
|------|------|
| **可创建** | 通过 REST API 创建一台（组）ECI 实例，拉取指定镜像并运行 |
| **可删除** | 通过 REST API **删除**指定 ECI 实例（非停止保留） |
| **可查询** | 列出本地记录与（可选）云上状态，便于联调 |
| **可配置** | AccessKey、Region、VPC、镜像、规格等均走配置，不写死在代码 |
| **可本地演练** | 提供 `mock` 模式：无真实云凭证时也能跑通 API 与状态机 |
| **为后续集成留口** | API 形态、状态字段与主仓约定尽量对齐，便于将来迁入或被 HTTP 调用 |

### 1.3 非目标（本 Demo 明确不做）

- 不实现训练任务队列、Worker 注册/心跳/领任务协议（仍属 XJICloud 主后端）。
- 不实现按队列深度自动扩缩（属另一份 CCI/Scaler PRD）。
- 不实现用户前端、动态域名网关、WebSocket 透传。
- 不要求 GPU 训练端到端；默认小镜像 `nginx:alpine`。
- 不把本 Demo 默认合并进生产 ACK/CCI 拓扑；**生产是否用 ECI 待定**。

### 1.4 成功标准（验收口径）

**环境：** Linux + JDK 17 + Maven；具备有效阿里云凭证与网络（真实模式）。

1. `mvn spring-boot:run`（或 `java -jar`）在 Linux 上可启动服务。
2. 带正确 `X-Api-Key` 的 `POST` 创建 → 云控制台可见 ECI，进入 Running（或等价成功态）；默认镜像为 `nginx:alpine`。
3. `GET` 可查到该实例本地记录与状态。
4. 带正确 `X-Api-Key` 的 `DELETE` → **云上实例被删除**，本地记录为 `TERMINATED`。
5. 错误或缺失 Api-Key → 拒绝（401/403），不创建云资源。
6. `mock=true` 时上述流程不调用云 API 也能返回成功与状态流转。

---

## 2. 产品形态与边界

### 2.1 形态

| 项 | 约定 |
|----|------|
| **形态** | 独立 Spring Boot 3.3.x + **Java 17** Maven 工程 |
| **工程目录** | 主仓子目录 **`eci-manager/`**（最终目录名实现计划可微调，须在主仓内） |
| **与主后端关系** | **独立进程 / 独立 `pom.xml`**；不修改 `backend/` 业务代码 |
| **端口** | `8081`（避免与 XJICloud `8080` 冲突） |
| **数据库** | Demo 可用 H2 文件库或内存；不强制 PostgreSQL |
| **调用方** | 开发者用 curl / Postman；暂无正式前端 |
| **OS** | 文档、脚本、验收均按 **Linux** |

### 2.2 与 XJICloud 主系统的关系

```
【本阶段】
  开发者 ──HTTP + X-Api-Key──► eci-manager (:8081) ──OpenAPI──► 阿里云 ECI
                                （主仓子目录，独立进程）

【后续可选集成，不在本 PRD 实现范围；生产选型待定】
  XJICloud Backend ──HTTP──► eci-manager
       或代码迁入 / 换 CCI Client
       │
       ▼
  gpu-worker 启动后主动 register 到 XJICloud :8080
```

本 Demo **只负责云实例生命周期**；容器内业务进程如何连 XJICloud，仅通过环境变量模板预留（见 §3.4），本阶段不联调训练流水线。

### 2.3 为何本 Demo 用 ECI（而非 CCI）

| 点 | 说明 |
|----|------|
| 学长指定 | 本阶段技术验证对象为 **ECI** |
| 与主仓文档差异 | 主仓 Deploy/CCI.md 写的是生产 **CCI**；**生产最终选型待定** |
| Demo 价值 | 跑通「Spring Boot ↔ 阿里云弹性容器 OpenAPI」；后续可换 CCI 或抽象 `ContainerProvider` |

---

## 3. 功能需求

### 3.1 实例状态机（Demo 本地权威）

| 状态 | 含义 |
|------|------|
| `CREATING` | 已受理创建请求，正在调云 API / 等待 Running |
| `RUNNING` | 云侧已 Running（或 mock 直接进入） |
| `STOPPING` | 已受理**删除**请求 |
| `TERMINATED` | 云侧已删除，终态 |
| `FAILED` | 创建或删除失败，需记录 `failureReason` |

```
CREATING → RUNNING | FAILED
RUNNING  → STOPPING → TERMINATED | FAILED
任意非终态 → FAILED（云 API 异常）
```

### 3.2 创建容器实例

- **默认镜像**：`nginx:alpine`（小镜像；请求体可覆盖 `image`，但不作为本阶段验收必测项）。
- **必填（yml 默认 + 请求可覆盖）**：Region、镜像、CPU/Memory、VPC / 交换机 / 安全组。
- **可选**：环境变量、实例名称等。
- **行为**：生成本地 UUID → 调 ECI 创建 → 存 `cloudInstanceId` → 轮询至 Running / 超时失败。

### 3.3 删除容器实例（关闭 = 删除）

- **能力**：按本地 `id` 删除对应 ECI（调用云 **Delete** 类 API，非 Stop 保留）。
- **行为**：`STOPPING` → 删除成功 → `TERMINATED`；失败 → `FAILED` + `failureReason`。
- **幂等**：已是 `TERMINATED` 再次 DELETE → 成功（或明确「已终止」），不抛 500。

### 3.4 查询

- 列表（可按 `status` 过滤）、详情、可选 `refresh`（Describe 回写；mock 下返回本地状态）。

### 3.5 环境变量模板（预留，本阶段可不联调）

| 变量名 | 用途（面向未来集成） |
|--------|----------------------|
| `XJICLOUD_BACKEND_URL` | Worker 注册地址 |
| `WORKER_SECRET` | 与主后端对齐 |
| `WORKER_NAME` | 显示名 |

支持透传任意 `env` Map，便于以后换成 `gpu-worker` 镜像。

### 3.6 Mock 模式

| 配置 | 行为 |
|------|------|
| `eci.mock=true` | 不调云；创建→`RUNNING`；删除→`TERMINATED`；`cloudInstanceId=mock-eci-xxx` |
| `eci.mock=false` | 真实 ECI OpenAPI |

建议：`dev` profile → `mock=true`；`cloud` profile → `mock=false`。

### 3.7 鉴权（Api-Key）

- 配置项：`eci.api-key`（或环境变量覆盖）。
- 客户端请求头：`X-Api-Key: <密钥>`。
- 未带 / 错误：拒绝访问，不执行创建/删除。
- `/actuator/health` 可保持公开（仅存活探测）。

---

## 4. API 规范

### 4.1 约定

- 前缀：`/api/v1/eci`
- 响应：`{ "success": boolean, "message": string|null, "data": ... }`
- 鉴权：**已定** — `X-Api-Key`（§3.7）

### 4.2 创建实例

`POST /api/v1/eci/instances`

```json
{
  "name": "demo-1",
  "image": "nginx:alpine",
  "cpu": 1.0,
  "memory": 2.0,
  "env": {
    "XJICLOUD_BACKEND_URL": "http://10.0.1.20:8080",
    "WORKER_SECRET": "change-me",
    "WORKER_NAME": "demo-1"
  }
}
```

未传 `image` 时使用配置默认值 **`nginx:alpine`**。

### 4.3 删除实例

`DELETE /api/v1/eci/instances/{id}`

语义：**删除**云资源，不是停止保留。

### 4.4 列表 / 详情 / 刷新

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/eci/instances` | 列表，`?status=` 可选 |
| GET | `/api/v1/eci/instances/{id}` | 详情 |
| POST | `/api/v1/eci/instances/{id}/refresh` | 刷新云状态 |

### 4.5 健康检查

`GET /actuator/health` — 无需 Api-Key。

---

## 5. 配置需求

前缀：`eci.`

| 键 | 说明 |
|----|------|
| `mock` | 是否 Mock |
| `api-key` | **必填（非空）**；与请求头比对 |
| `access-key-id` / `access-key-secret` | 阿里云凭证（`mock=false`） |
| `region-id` | 如 `cn-hangzhou` |
| `vpc-id` / `vswitch-id` / `security-group-id` | 网络 |
| `default-image` | 默认 **`nginx:alpine`** |
| `default-cpu` / `default-memory` | 默认规格 |
| `create-timeout-sec` | 等待 Running 超时 |

凭证与 `api-key` **不得提交 Git**；提供 `application-local.yml.example`。

---

## 6. 非功能需求

| 类别 | 要求 |
|------|------|
| **OS** | 以 **Linux** 为运行与验收环境 |
| **JDK** | **17** |
| **构建** | **Maven 3.9+**；`mvn spring-boot:run`、`mvn -DskipTests package` |
| **框架** | Spring Boot 3.3.x（与主仓同代） |
| **云 SDK** | 阿里云 ECI 相关官方 Java SDK（版本在实现计划锁定） |
| **日志** | 创建/删除记本地 id、cloudInstanceId、耗时、错误码；禁止打印 Secret / Api-Key |
| **成本** | README 醒目提示：真实模式用完务必 DELETE |

### 6.1 Linux 验收命令示意（实现后写入 README）

```bash
java -version          # 须为 17
mvn -v                 # 3.9+
cd eci-manager
mvn -DskipTests package
java -jar target/*.jar   # 或 mvn spring-boot:run

curl -s http://127.0.0.1:8081/actuator/health
curl -s -X POST http://127.0.0.1:8081/api/v1/eci/instances \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $ECI_API_KEY" \
  -d '{}'
```

---

## 7. 文档与交付（实现阶段才做）

1. `eci-manager/README.md`：Linux 下 JDK/Maven 准备、mock/cloud 启动、Api-Key、curl、控制台核对、**及时删除防账单**。
2. `application-local.yml.example`
3. 与本 PRD 版本对应关系
4. 「后续如何被 XJICloud 调用」短节（选型仍待定）

**当前阶段不交付代码与 README 实现。**

---

## 8. 分期建议

| 阶段 | 内容 | 状态 |
|------|------|------|
| **D0** | PRD 评审；关键决策锁定 | ✅ v1.1（生产选型除外） |
| **D1** | `eci-manager/` 骨架 + Mock API + Api-Key | 待实现计划批准 |
| **D2** | 真实 ECI：创建 / 查询 / **删除** | 待实现 |
| **D3** | 超时、失败原因、env 模板、Linux README | 待实现 |
| **D4（可选）** | `ContainerProvider` 抽象，为 CCI 预留 | 依赖「生产选型」 |
| **I1** | 与 XJICloud 联调或迁入 | 另立需求；选型待定 |

---

## 9. 仍待确认（仅一项）

| # | 议题 | 状态 | 说明 |
|---|------|------|------|
| 5 | **生产最终用 ECI 还是 CCI（或双 Provider）** | **待定** | 不阻塞 Demo 实现；影响 D4/I1 与主仓 Deploy 文档是否改写 |

其余 §0 表格内决策已锁定，可作为实现计划输入。

---

## 10. 与「CCI 自动启停 PRD」的差异对照

| 维度 | 本 Demo PRD（ECI） | CCI 自动启停 PRD |
|------|-------------------|------------------|
| 目的 | 验证 Spring Boot 实时控制 ECI | 按训练队列自动起停算力 |
| 触发 | 人工/API 显式创建、**删除** | 队列深度 + Scaler |
| 部署 | 主仓 `eci-manager/` 独立进程 | 主后端内嵌或强集成 |
| 产品 | **ECI**（Demo） | **CCI**（主仓文档；生产待定） |
| 鉴权 | `X-Api-Key` | Admin JWT 等 |
| 镜像 | 默认 `nginx:alpine` | `xjicloud-gpu-worker` |

---

## 11. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-23 | 初稿，多项待确认 |
| v1.1 | 2026-07-23 | 锁定：主仓、Api-Key、小镜像、删除；运行环境 Linux + JDK17 + Maven；生产选型仍待定 |

---

*下一步：基于 v1.1 编写实现计划（仍须人工批准后再写代码）。*
