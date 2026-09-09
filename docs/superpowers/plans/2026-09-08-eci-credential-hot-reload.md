# eci-manager 当前钥热切换 Implementation Plan（第一步）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **How to resume in a new chat:** `@docs/superpowers/plans/2026-09-08-eci-credential-hot-reload.md` 并说明要从哪个未完成 Task 继续。  
> **设计源：** [`docs/superpowers/specs/2026-09-08-eci-manager-credential-runtime-design.md`](../specs/2026-09-08-eci-manager-credential-runtime-design.md)  
> **工程目录：** 仅 `eci-manager/`（禁止修改 `backend/`）  
> **第二步**（主后端统一 AK 列表 + 调用本计划接口）不在本文实现任务内，契约见设计 §5。

**Goal:** 让 eci-manager 每种阿里云资源持有一把可热切换的当前 AccessKey（落盘、GET/PUT、测通），供日后主后端目录在「选用」时调用。

**Architecture:** C1 使 ECI 能按指定 AK 静态建连。C2 按 resource 持久化 **当前** AK 并热重建客户端，接口固定为 `/api/v1/eci/runtime-credentials`（不要做目录 CRUD）。C3 HTTP Api-Key 双钥。C4 test 接口（草稿或当前）。C5 文档。

**Tech Stack:** Spring Boot 3.3.5、Java 17、Maven、H2、aliyun-java-sdk-core 4.7.3、aliyun-java-sdk-eci 1.3.3、AWS S3 SDK 2.29.45。

## Global Constraints

- 仅改 `eci-manager/`；8081；`/api/v1/eci`；`X-Api-Key`
- **禁止**改 RAM / DefaultCredentialsProvider 分支内部；只在其前加 STATIC_AK
- **禁止** AK/SK 进容器；Java 不读 `ALIBABA_CLOUD_*`
- **禁止**在 8081 实现多钥目录 / 绑定表 / 按 id 选钥（那是第二步主后端的事）
- GET 永不返回 SK
- 未要求则不 commit
- `mvn test`；真云 `./scripts/start.sh`
- 行为以设计文档为准

### 分期

| 阶段 | 内容 |
|------|------|
| **C1** | STATIC_AK + `load-env.sh` 映射 |
| **C2** | 当前钥 GET/PUT + 落盘 + 热重建 |
| **C3** | HTTP Api-Key 双钥 |
| **C4** | POST test |
| **C5** | 文档 |

第二步将调用：`GET/PUT /runtime-credentials`、`POST /runtime-credentials/test`。路径与 JSON 字段不要在第一步之后随便改名。

---

## File Structure

```
eci-manager/src/main/java/com/xjicloud/eci/
├── credential/
│   ├── AliyunCredentialMode.java
│   ├── AliyunCredentialResolver.java
│   ├── AliyunResource.java          # ECI_OPENAPI, OSS
│   ├── CredentialHint.java
│   ├── RuntimeCredentialEntity.java # key/value 或按 resource 一行
│   ├── RuntimeCredentialRepository.java
│   ├── RuntimeCredentialService.java
│   ├── RuntimeCredentialController.java
│   └── HttpApiKeyStore.java         # C3
├── provider/AliyunEciProvider.java  # C1 STATIC_AK；C2 volatile + reload
└── oss/OssStorageService.java       # applyRuntimeKeys
```

---

## 接口契约（第二步依赖，勿改名）

```http
GET  /api/v1/eci/runtime-credentials
PUT  /api/v1/eci/runtime-credentials
     { "resource": "ECI_OPENAPI"|"OSS", "accessKeyId": "...", "accessKeySecret": "..." }
POST /api/v1/eci/runtime-credentials/test
     { "resource": "...", "accessKeyId": "...", "accessKeySecret": "..." }
     // id/secret 可省略 = 测已保存的当前钥
```

```java
public enum AliyunResource { ECI_OPENAPI, OSS }

AliyunCredentialMode.resolve(ak, sk, roleName); // STATIC_AK | INSTANCE_PROFILE | DEFAULT_CHAIN
CredentialHint.mask(value);

EciProvider.reloadAccessKey(ak, sk);        // default no-op
OssStorageService.applyRuntimeKeys(ak, sk);
```

---

### Task C1: STATIC_AK 与 env 映射

**Files:** Resolver/Mode/Hint + 单测；`AliyunEciProvider` 构造器最前插入 STATIC_AK；`load-env.sh` / `start.sh` / `.env.example`

- [x] Resolver 单测：有 AK 优先于 role；无 AK 有 role → INSTANCE_PROFILE；都无 → DEFAULT_CHAIN；Hint 长度 `< 12` 为 `****`，`>= 12` 为前4+****+后4
- [x] 实现后 `mvn -q -Dtest=AliyunCredentialResolverTest,CredentialHintTest test`
- [x] 构造器：`DefaultProfile.getProfile(regionId, ak, sk)` 成功则 return；其后 RAM/默认链 **逐字保留**。C1 的 client 可 `final`
- [x] `ALIBABA_CLOUD_*` 在 `ECI_ACCESS_KEY_*` 为空时 export 过去
- [x] `mvn test` PASS

真云可选：旧 `.env` + start.sh 出现 `credentialMode=STATIC_AK`，无 IMDS 超时。

---

### Task C2: 当前钥 GET/PUT、落盘、热重建

**Files:** Entity/Repo/Service/Controller；`client` 改 `volatile` + `reloadAccessKey`；OSS `applyRuntimeKeys`（无 runtime 时现有单测仍可 `new OssStorageService(props)`）；`ProviderConfig` 构造时读 **已 seed 的当前 ECI 钥**，不要只信 properties（否则重启打回 `.env`）

种子：current 行不存在才从 properties 写入。

- [x] `RuntimeCredentialApiTest`（独立 `jdbc:h2:mem:eci-runtime-cred`）  
  1. 无 Api-Key → GET 401  
  2. OSS env 种子后 GET `resources.OSS.configured=true`，body 无 SK  
  3. PUT ECI 成对 → 200，hint 变；再 GET 一致  
  4. PUT 只传 id 不传 secret → 400  
  5. 未知 resource → 400  
  6. Mock 下 PUT ECI 不抛（不建云客户端）
- [x] 实现至 PASS
- [x] `mvn test`

手工：PUT 后重启，GET hint 仍是新钥。

---

### Task C3: HTTP Api-Key 双钥

独立于云当前钥。Filter 改读 store。`PUT /credentials/http-api-key`，`POST /credentials/revoke-previous-api-key`。`eci.api-key-overlap-sec` 默认 3600。`MessageDigest.isEqual`，禁止 `String.equals`。

- [ ] 新钥立即可用、重叠期内旧钥可用、revoke 后旧钥 401、相同钥 no-op、overlap=0 立即切断
- [ ] `mvn test`（含 `EciTaskApiTest.rejectsMissingApiKey`）

---

### Task C4: test

`POST /runtime-credentials/test`。带 AK/SK 则用临时客户端，不改当前。省略则测已保存当前。Mock + ECI → 400。OSS 未配 endpoint → 400。CI 不连真云。禁止测通打 IMDS。

- [ ] 测试 + `mvn test`
- [ ] 可选真云：带真 AK 测 ECI_OPENAPI success

---

### Task C5: 文档

AGENT.md、API文档、设计与使用说明、测试说明、README、3dgs 说明。写明：8081 只有当前钥；列表在主后端第二步；`.env` 是种子；`ALIBABA_CLOUD_*` 仅脚本映射。删掉「yml AK 不建客户端」。

不改 `backend/`、`docs/Deploy.md`。

---

## 实现注意

- `ObjectProvider` 解循环依赖
- RAM 分支原样
- 新 IT 独立内存库名
- 不要做 `/cloud-keys` 目录 API

## 自检

| 设计 | Task |
|------|------|
| STATIC_AK、映射 | C1 |
| 每资源当前钥、GET/PUT、热切、种子 | C2 |
| HTTP 双钥 | C3 |
| test 不改当前 | C4 |
| 无 8081 目录 | 全局 |
| 第二步可调同一 URL | 契约节 |
