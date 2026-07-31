# ECI OSS 集成 — 进度与续跑上下文

> **日期：** 2026-07-31  
> **主计划：** [`docs/superpowers/plans/2026-07-30-eci-oss-integration.md`](./plans/2026-07-30-eci-oss-integration.md)  
> **PRD：** 仓库根目录 `ECI任务OSS对象存储集成产品需求文档(PRD).md` v1.0  
> **工程：** 仅 `eci-manager/`（禁止改 `backend/`）

## 当前状态

| 阶段 | 状态 | 说明 |
|------|------|------|
| O0 计划 | 已创建 | 总计划已落盘 |
| O1 配置与依赖 | **完成** | S3 SDK 2.29.45、`OssProperties`、`eci.oss`、`.env.example` |
| O2 OssStorageService | **完成** | 精简移植；本地 presign 单测；无 MinIO |
| O3 storage 解析 | **完成** | DTO + `TaskStorageResolver`；mode 忽略大小写；callerEnv 覆盖 |
| O4 任务 API 接入 | **完成** | `EciTaskService.create` 在 save 前解析 storage |
| O5 联调镜像 | **完成** | Task 5：`test-images/oss-smoke`；基座 `ylif4/python_mirror:3.12`；本地 build+run 通过 |
| O5.2 文档同步 | **完成** | Task 6：API §3.1 storage；设计说明 §4.4；编排 PRD 链接已存在 |
| O5.3 云端验收 | **未开始** | Task 7 |

**测试基线：** `cd eci-manager && mvn test` → **99** 项 PASS（2026-07-31，O4 后；Task 5–6 无 Java 改动）。  
**JDK：** 仓库内 `.tools/jdk-17.0.13+11`（`JAVA_HOME` 指向该路径）。

**下一跳：** `@docs/superpowers/plans/2026-07-30-eci-oss-integration.md` 并从 **Task 7（云端验收）** 继续。  
推送联调镜像（若尚未推）：公网  
`crpi-urdof1biefqsi8uj.cn-hangzhou.personal.cr.aliyuncs.com/ylif4/eci-oss-test:latest`；  
ECI 拉取用 VPC  
`crpi-urdof1biefqsi8uj-vpc.cn-hangzhou.personal.cr.aliyuncs.com/ylif4/eci-oss-test:latest`。

## 已落地核心路径

```
eci-manager/
├── test-images/oss-smoke/         # Dockerfile + run.py + requirements.txt
├── docs/API文档.md                # §3.1 storage / env / 400
├── docs/设计与使用说明.md         # §4.4 eci.oss + 联调 checklist
└── src/main/java/com/xjicloud/eci/
    ├── config/OssProperties.java
    ├── oss/OssStorageService.java
    └── task/
        ├── EciTaskService.java
        ├── dto/CreateTaskRequest.java
        └── storage/               # TaskStorageRequest / Resolver / ...
```

关键 API 测试：

- `EciTaskStorageApiTest` — OSS off：无 storage / `storage:null` → 200；有 storage → 400
- `EciTaskStorageIncompleteApiTest` — enabled 但 bucket 空 → 400，count 不变
- `EciTaskStorageEnabledApiTest` — 完整配置 + `mode=test` → envJson 含精确 `eci-test/{id}/output.txt`

## 关键契约（实现已对齐）

1. **无 `storage`：** 与 v2.0.3 完全一致。  
2. **有 `storage`：** 须 `enabled && isConfigured()`，否则 `BusinessException` 400，**不落库、不建实例**。  
3. **mode：** `test` / `keys`（忽略大小写）；`dataset` → 400「尚未支持」。  
4. **env 合并：** 先写 OSS 变量，再 `putAll(callerEnv)`（调用方同名优先）。  
5. **禁止** AK/SK 进容器 env；只注入 presigned URL 与元信息。  
6. **PUT contentType：** `test` → `text/plain`；`keys` → `application/octet-stream`。  
7. **输出 GET：** 一期不做（`ResolvedTaskStorage` 仅 upload PUT）。

## 代码注释

2026-07-31 已为 O1–O4 核心类补充中文契约注释（为何 / 边界 / 异常语义），逻辑未改。

## 二期（本计划不实施）

- `storage.mode=dataset`
- 任务响应 `storageResolved`
- backend 调用 eci-manager
- 抽取 `xjicloud-oss-common`
