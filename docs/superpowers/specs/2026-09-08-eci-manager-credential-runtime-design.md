# 云凭证：eci-manager 热切换 + 主后端统一目录 — 设计说明

> **状态：** 两步拆分已锁定（2026-09-08）。未要求前不写业务代码、不 commit。  
> **日期：** 2026-09-08

## 1. 最终目的

后端能实时管理多把阿里云 AccessKey：添加、删除、每种资源选择当前使用的钥；用不同的钥访问不同阿里云资源。

**一份 AK 列表**（在主后端），每种资源从列表里选当前钥。eci-manager **不维护第二份目录**，只暴露「把这把 AK 设成某资源的当前钥并热切换」。

## 2. 为什么拆成两步

| | 第一步（本期） | 第二步（后续） |
|--|----------------|----------------|
| 在哪 | 仅 `eci-manager/` `:8081` | 主仓 `backend/` + 管理页 |
| 做什么 | 每种资源有一把 **当前 AK**，可 HTTP 热切、落盘、测通 | **统一 AK 列表** + 绑定；变更时调用第一步接口推给 8081 |
| 权威 | 8081 只权威「当前正在用的那把」（每资源一行） | 列表与「选哪把」的权威在 8080 |
| 谁调用 | curl / 脚本 / 以后的 backend | Admin JWT → backend → `X-Api-Key` → 8081 |

若第一步就在 8081 做完整目录，第二步会有两套 id、两次增删，对不齐。第一步做成 **执行器**（apply current），第二步做成 **控制面**（catalog + select）。

```
第二步  管理页 → 主后端目录/绑定
                    │ 选中某把钥给 ECI
                    ▼
第一步  PUT 8081 当前 ECI AK → 热重建 AliyunEciProvider
         PUT 8081 当前 OSS AK → 热重建 eci-manager 的 S3（任务预签名）
         主后端自己的 OSS 客户端仍在 8080 本地热更新（已有）
```

## 3. 已锁定决策

| 项 | 结论 |
|----|------|
| AK 列表放哪 | **第二步才做**，在主后端 |
| 8081 存什么 | 每个 `resource` 仅存 **当前** AK/SK，可热覆盖 |
| 同一把钥打两种资源 | 第二步绑定两次；对 8081 就是两次 PUT（ECI 一次、OSS 一次），内容可以相同 |
| 账号类型 | 不问 |
| RAM 代码 | 不删不改；当前 ECI AK 为空才走旧链 |
| `ALIBABA_CLOUD_*` | 仅 `load-env.sh` 映射到 `ECI_ACCESS_KEY_*` 作种子 |
| 容器 | 不注入 AK |
| 第一步不改 `backend/` | 是 |
| STS / 加密 / 热改 VPC | 不做 |

资源枚举（两步共用，便于第二步直接调）：`ECI_OPENAPI`、`OSS`。以后加 ACR 只加枚举，8081 对未知 resource 返回 400。

HTTP `ECI_API_KEY` 不是阿里云钥。它只用于注入进程配置 `eci.api-key`，是 HTTP 鉴权的唯一权威；改钥必须改部署配置并重启。第二步 backend 调用 8081 时传部署配置中的 `X-Api-Key`。

## 4. 第一步：8081 当前钥执行器

### 4.1 存储

表 `eci_runtime_config`（或等价）按 resource 存当前钥：

| key | 含义 |
|-----|------|
| `current.ECI_OPENAPI.access-key-id` / `.secret` | 当前 ECI OpenAPI 钥 |
| `current.OSS.access-key-id` / `.secret` | 当前 OSS 钥（eci-manager 预签名） |

种子：上述 current 行 **不存在** 时从 `ECI_ACCESS_KEY_*`、`ECI_OSS_*` 写入。已存在则启动不覆盖（热切结果不能被 `.env` 打回）。

HTTP Api-Key 不写入 `eci_runtime_config`，也不从该表恢复。H2 中可能残留旧版本的 `eci.api-key`、`previous`、`previous-expires-at` 行，但升级后全部忽略。曾通过旧 PUT 轮换过的部署，升级前必须把当时仍有效的当前 HTTP Api-Key 回写到受保护的 env/yml，再停止旧版本并启动新版本；不要回写已撤销或仅处于旧重叠窗口的钥。

### 4.2 客户端

- `ECI_OPENAPI` 当前 AK 非空 → `STATIC_AK`（C1 的 `DefaultProfile.getProfile(region, ak, sk)`）。空 → 现有 RAM/默认链，内部不改。
- `OSS` 当前 AK 非空 → 用它建 S3；endpoint/bucket 仍来自 `eci.oss.*`。空 → 与现在一样看 properties。
- PUT 成功：落盘 + 只重建该 resource 的客户端。进行中的调用用旧 client 跑完。
- 重启按库里的当前钥建客户端。
- Mock：PUT 仍落盘，不建阿里云客户端。

### 4.3 HTTP（均需 `X-Api-Key`）

这就是第二步要调的接口，第一步就必须稳定。

**`GET /api/v1/eci/runtime-credentials`**

```json
{
  "mock": true,
  "resources": {
    "ECI_OPENAPI": {
      "configured": true,
      "accessKeyHint": "LTAI****abcd",
      "mode": "MOCK"
    },
    "OSS": {
      "configured": true,
      "accessKeyHint": "seed****1234",
      "mode": "STATIC_AK"
    }
  }
}
```

永不返回 SK。

**`PUT /api/v1/eci/runtime-credentials`**

```json
{
  "resource": "ECI_OPENAPI",
  "accessKeyId": "LTAI...",
  "accessKeySecret": "..."
}
```

`resource` 必填。Id 与 Secret 必须成对非空。成功：覆盖该资源当前钥并热重建，返回与 GET 相同结构（可只含该 resource）。

清空当前钥（回到 env/旧链）本期 **不做**（避免误切 IMDS）。要换钥就 PUT 新的一对。

**`POST /api/v1/eci/runtime-credentials/test`**

```json
{
  "resource": "ECI_OPENAPI",
  "accessKeyId": "...",
  "accessKeySecret": "..."
}
```

用请求里的钥测，**不**改当前、不落盘。供第二步「先测再选」。  
`ECI_OPENAPI`：DescribeContainerGroups max 1；mock → 400。  
`OSS`：对当前配置的 bucket ListObjects；endpoint/bucket 未配 → 400。  
也可不传 id/secret，表示测 **已保存的当前钥**。

**HTTP Api-Key：** 所有受保护接口使用 `X-Api-Key`，并与进程配置 `eci.api-key` 精确匹配；不存在运行时轮换或吊销接口。配置变更后重启服务，与云当前钥的 GET/PUT/test 和热切语义无关。

### 4.4 第一步分期（仅 eci-manager）

| 阶段 | 交付 |
|------|------|
| **C1** | STATIC_AK + env 映射 |
| **C2** | 当前钥落盘 + GET/PUT `runtime-credentials` + 热重建 |
| **C3** | HTTP Api-Key 配置权威（无运行时轮换） |
| **C4** | `POST .../test`（可测草稿或当前） |
| **C5** | 文档 |

第一步 **不做**：目录 CRUD、绑定表、按 id 选钥、改 `backend/`。

## 5. 第二步：主后端统一列表（规划，本期不实现）

- 表：AK 目录（name + AK + SK）+ 资源绑定（`ECI_OPENAPI` / `OSS` / 以后更多 → credential_id）。
- 管理页：一份列表；每个资源下拉选当前钥（可多资源选同一条）。
- 绑定 `OSS` 且该 OSS 是 **主后端浏览器/Worker 那条**：只热更新 8080 的 `OssStorageService`（已有能力），**不必**调 8081。
- 绑定 `ECI_OPENAPI`（以及若 eci-manager 任务预签名要用另一把/同一把 OSS）：`PUT 8081 /runtime-credentials` 带明文 AK/SK（8080→8081 内网，不进浏览器）。
- 8081 调用失败：绑定事务失败或回滚，避免「库里选了 A、8081 仍是 B」。
- 删除目录项：若仍被绑定 → 409，与先前产品规则相同。
- backend 配置：`eci-manager.base-url` + 用于调 8081 的 Api-Key（与 8081 的 `eci.api-key` / `ECI_API_KEY` 配置一致）。

eci-manager 内网 OSS 与主后端公网 OSS 可能同一把 AK、不同 endpoint：第二步列表里一条钥，绑定两次（OSS 给 8080，ECI 或再 PUT OSS 给 8081）。8081 的 OSS endpoint 仍用它自己的 yml，不随选钥改。

## 6. 约束

- 选钥不改 region / vswitch / sg / OSS endpoint。跨账号 403 是预期。
- 热切 ECI 不取消已有任务；之后的 create/describe/delete 用新钥。切账号前应清旧账号实例。
- 日志只打 resource、hint、mode。

## 7. 明确不做（第一步）

- `backend/`、管理前端、部署向导。
- 8081 上的多钥目录。
- 改 RAM 分支内部。
- STS、KMS、热改网络、热切 mock。
