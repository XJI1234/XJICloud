# 训练任务派发联调方案

> 范围：验证用户上传 → Redis → 主 backend `TrainingDispatchScheduler` → eci-manager → ECI 的派发链路。调度仍由主 backend 负责，不迁入 eci-manager。

## 前置条件

- 主后端、Redis、eci-manager 和两端配置的同一个 OSS bucket 均可用；主后端的 `XJICLOUD_ECI_MANAGER_BASE_URL` 可从后端进程访问 eci-manager。
- 主后端 `XJICLOUD_ECI_MANAGER_API_KEY` 与 eci-manager `ECI_API_KEY` 相同。这是两服务调用 `/api/v1/eci/**` 时使用的 HTTP `X-Api-Key` 鉴权值，**不是**阿里云 AccessKey。阿里云 `ECI_OPENAPI` / `OSS` AK/SK 仍由主后端云钥列表管理，选用后经现有 `/runtime-credentials` 接口推送给 eci-manager；训练派发不读取或注入这些 AK/SK。`XJICLOUD_ECI_MANAGER_TRAINING_IMAGE` 指向已经上传的 3DGS 镜像，不在本仓库创建、构建或推送训练镜像。
- backend 用 `/opt/3dgs/docker/entrypoint.sh` + `shell` 执行 base64 bootstrap；资源为 16 CPU / 60 GiB / GPU 1、`gn7i-c16g1.4xlarge`、`shm=8Gi`、`maxRuntime=21600` 秒。bootstrap 源文件 `eci-manager/scripts/3dgs-bootstrap.py` 与 backend classpath snapshot `services/backend/src/main/resources/3dgs-bootstrap.py` 必须字节一致；修改源文件时同步 snapshot 和测试。
- eci-manager 已启用 `eci.oss`，并为 `storage.mode=keys` 配置可用的 OSS 当前钥。真云 `ECI_OSS_PRESIGN_EXPIRATION_MINUTES` 必须至少为 `1440` 分钟，以覆盖最长 21600 秒任务及余量；使用同地域 OSS 内网 endpoint。训练容器须能访问签出的输入 GET 和输出 PUT URL。
- 提供已有测试账号的 Bearer token（`TRAINING_TEST_TOKEN`），或测试账号用户名、密码（`TRAINING_TEST_USERNAME` / `TRAINING_TEST_PASSWORD`）。`TRAINING_TEST_DATASET_DIR` 指向名为“新文科楼”的 JPG/JPEG 图片目录；当前仅有 JPG/JPEG 真云路径验证证据。脚本不创建账号、不打印 token、密码或预签名 URL。
- 生产 PostgreSQL 环境先执行并核验 `services/backend/db/migration/V20260923_01__training_dispatch_fields.sql` 与 `V20260923_02__training_dispatch_backoff.sql`。开发 H2 由现有 JPA `ddl-auto=update` 建列。

## Mock 联调

1. 启动 Redis、可供本机访问的 OSS 和主后端，再启动 `./eci-manager/scripts/start.sh mock`。eci-manager `eci.oss.enabled=true`，bucket 与主后端一致。确认 `/actuator/health`、eci `/meta` 的 `mock=true`、`ossEnabled=true`、`ossConfigured=true`。
2. 运行 `TRAINING_TEST_DATASET_DIR=/path/to/新文科楼 ./scripts/integration-training-dispatch-mock.sh`。可用 `TRAINING_TEST_BACKEND_BASE`、`TRAINING_TEST_ECI_BASE` 覆盖默认 `http://127.0.0.1:8080/8081`。
3. 脚本按用户路径完成登录或使用 token、创建临时项目、申请整组图片的上传 URL、PUT 图片和 manifest、调用 complete，并观察 backend `QUEUED → RUNNING → COMPLETED`、ECI 任务终态和实例 `TERMINATED`。脚本只读取状态，不执行调度或取消。
4. Mock Provider 不执行真实训练容器，也不上传真实模型；脚本只验证控制面状态和 backend 提交给 ECI 的镜像、规格、入口命令与环境变量。联调创建的项目和输入对象保留，输出的 jobId/projectId 供人工清理；不要在仍有运行任务时直接删除项目。

## 真云联调

1. 将已经上传的 3DGS 镜像地址配置到主 backend `XJICLOUD_ECI_MANAGER_TRAINING_IMAGE`，确认 ECI 可拉取。启动 `./eci-manager/scripts/start.sh cloud`，确认 `/meta` 为 `mock=false` 且 OSS 已启用、配置完整。运行中的 ECI Manager 必须使用 `ECI_OSS_PRESIGN_EXPIRATION_MINUTES>=1440`；`/meta` 不返回该有效值，运行联调脚本前需核对进程实际加载的 `.env`/启动环境。本机 cloud 脚本会校验从 `ECI_ENV_FILE`（默认 `eci-manager/.env`）加载的值至少为 1440，但无法证明远端或已运行进程使用同一配置。现有独立标准档样例见 `eci-manager/scripts/submit-3dgs-standard.sh`；应用训练仍由主 backend scheduler 按用户任务派发。
2. 运行 `TRAINING_TEST_DATASET_DIR=/path/to/新文科楼 ./scripts/integration-training-dispatch-cloud.sh`。入口会在提交前打印将使用的地址、镜像期望值和数据集路径，不打印凭据；执行该命令即同意创建计费 ECI 实例。
3. 脚本验收仅为 backend 接受任务并返回 `QUEUED`。提交后由 backend scheduler 派发；训练完成、ECI 终态、实例清理和 PLY 下载使用 backend/eci-manager 的独立观察接口核对。记录脚本输出的 projectId/jobId。
4. 脚本失败时不主动取消业务任务或 ECI 任务；backend 负责后续重试和取消编排。真云提交会创建计费资源，操作人员仍需在 backend/eci-manager 日志和阿里云控制台确认实例最终清理。测试输入/输出 OSS 对象及项目记录由运维按 ID 清理。

## 故障与边界用例

| 用例 | 操作 | 预期 |
|------|------|------|
| eci-manager 不可达 | 完成上传前暂时停止 eci-manager，再恢复 | 业务保持 `QUEUED`，30 秒退避后同一请求键受理一次；无重复 ECI 任务 |
| 取消 | 在 `QUEUED` 或 `RUNNING` 调用户取消 API | 业务 `CANCELLED`；已受理 ECI 任务最终 `CANCELLED`，实例 `TERMINATED`；禁止取消未收敛时重训/删除 |
| 输出失败 | 真云使上传 URL 不可用或用失败镜像，仅在隔离测试环境操作 | 业务 `FAILED`，eci 记录失败原因/日志尾部，并在宽限后删除实例 |
| 重训 | 失败/取消且清理收敛后，调用管理员重试接口 | 新 attempt 使用新 `clientRequestId`；旧 attempt 不再覆盖新业务状态 |

故障注入用例不由基础冒烟脚本自动执行。真云失败取证需等待 `failure-log-grace-sec`，清理完成前不得声称无残留。首次联调只跑一笔小任务，不开启批量并发。

## 脚本验收与维护

- `scripts/integration_training_dispatch.py` 负责整组图片的结构化提交和关键状态观察；两个 shell 入口固定 Mock/真云模式并加载 eci-manager 的现有 `.env`。调度和取消仍由 backend 负责，脚本不主动创建或取消 ECI 任务。
- 本轮运行 Mock 控制面联调，并记录实际经过的业务与 ECI 状态；Mock Provider 不执行训练镜像或生成 OSS 产出。本轮仅对真云脚本做静态检查，未运行真云联调或创建计费资源；真云步骤交由用户后续运行。若本机缺少 Redis、OSS 或可启动的服务，记录具体阻断与已完成的替代验证。
- 静态检查：`bash -n` 两个 shell 入口，`python3 -m py_compile` Python 驱动。真云步骤未经运行，交付时明确注明。
