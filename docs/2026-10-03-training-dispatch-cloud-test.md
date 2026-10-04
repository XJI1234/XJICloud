# 2026-10-03 主后端 → ECI Manager 真云训练联调记录

本文 §1–8 记录首次脚本真云联调；§9 补记同日用户手动操作浏览器的 E2E 结果。两次测试的任务不同，不能将首次脚本成功当作浏览器取消功能通过。

**浏览器补测结论：OSS 需要用户手动配置 CORS；处理后图片上传成功，云端训练启动，但取消训练未完成实例释放。根因为 ECI Manager 旧 H2 表结构不接受 `CANCELLING`，详见 §9。**

## 1. 首次脚本联调结论与范围

本次使用现有 `scripts/integration-training-dispatch-cloud.sh`，以普通用户身份提交“新文科楼”图片数据集，通过主后端派发到 ECI Manager，再启动真实云 GPU 容器执行 COLMAP 与 3DGS 训练。OSS 和 ECI 均为真实云服务，不是 Mock。

最终联调脚本退出码为 **0**，主后端任务为 **COMPLETED / 100%**，ECI 任务为 **SUCCEEDED**，实例在 ECI Manager API 中为 **TERMINATED**。产出 PLY 的 Range 下载、文件头和非零顶点数检查通过。

这证明了一次真实数据上传、任务派发、云端训练、产物回传与实例清理的成功链路；**不代表完整浏览器 E2E、模型视觉质量、生产部署或故障恢复已经验收通过**。

执行阶段没有改动源码或配置文件；需要修正的本地配置由用户手动处理。本报告仅记录过程，不再次提交收费云任务。准备说明见 [主后端云训练配置准备计划](superpowers/plans/2026-09-30-cloud-training-backend-setup.md)。

## 2. 云测思路

选择主后端入口，而不是直接调用 ECI Manager 的独立训练提交脚本，目的是覆盖普通用户到云训练的业务链路：

1. 普通用户登录主后端；账号由用户通过 web2 注册，不直接写数据库，也不绕过注册验证码。
2. Python 联调脚本创建项目，向主后端申请数据集上传凭证。
3. 主后端签发 OSS 预签名上传 URL；脚本逐张 PUT 图片，并上传 manifest。
4. 脚本调用主后端的数据集上传完成接口，触发队列与训练调度。
5. 主后端携带服务间 API key 向 ECI Manager 派发任务；联调观察器检查镜像、入口、资源规格与运行限制。
6. ECI Manager 为云容器提供同地域 OSS 内网预签名下载 URL；容器下载图片、执行 COLMAP、进行 3DGS 训练并上传 PLY。
7. ECI Manager 收敛任务终态并清理实例；主后端轮询到成功结果，更新业务任务状态并签发产物下载 URL。
8. 联调脚本轮询到三个成功条件同时满足后退出；另行检查 PLY 的 Range 下载与头部信息。

主要主后端 API：

```text
POST /api/v1/auth/login
POST /api/v1/projects
POST /api/v1/projects/{projectId}/datasets
POST /api/v1/projects/{projectId}/datasets/{jobId}/complete
GET  /api/v1/jobs/{jobId}
```

图片内容通过预签名 URL 直传 OSS，不是把约 2.2 GiB 图片请求体传入主后端。ECI 任务状态通过 ECI Manager API 观察。

## 3. 实际环境与配置来源

| 项目 | 本次使用方式 |
| --- | --- |
| 主后端 | WSL 本机，`127.0.0.1:8080`，Maven 前台启动，profiles 为 `dev,local` |
| 主后端基础配置 | 显式加载已有 `deploy/config/application-prod.yml`，复用 OSS、JWT、管理员初始配置 |
| 主后端本地覆盖 | Bash 加载 `deploy/config/backend.env.local`，不修改源文件 |
| 数据库 | 隔离 H2：`jdbc:h2:file:./data/cloud-training-db;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE` |
| 本地存储根 | 相对后端工作目录的 `./data/cloud-training-storage` |
| Redis | 本机 `127.0.0.1:6379`，队列 `xjicloud:cloud-training:jobs`，没有清空已有数据 |
| ECI Manager | 本机 `8081`，`cloud,local`，API 确认 `mock=false`，沿用已有持久化数据库 |
| OSS | 两端同 Bucket；主后端使用公网 endpoint，ECI Manager 为云容器签发内网 URL |
| 镜像 | 主后端读取 `XJICLOUD_ECI_MANAGER_TRAINING_IMAGE`；联调检查实际镜像与期望值一致 |
| 时间参数 | ECI 预签名有效期 1440 分钟，创建超时 1800 秒，云测观察超时 25200 秒 |

单独运行 `mvn spring-boot:run -Dspring-boot.run.profiles=dev,local` 不会自动读取 `deploy/config/` 中的 YAML。最初的 `OSS endpoint 无效: null` 是配置未加载到主后端，不能仅从上层 `UnsatisfiedDependencyException` 判断为 Controller 或 Service 代码缺陷。

本次已有本地环境文件通过 `SPRING_CONFIG_ADDITIONAL_LOCATION=file:../../deploy/config/application-prod.yml` 显式加载部署配置，再用环境变量覆盖数据库、Redis 等本地选项。相对路径依赖 `services/backend` 工作目录，不能随意换目录启动。

配置中的 API key、OSS AccessKey、用户密码、JWT 与完整预签名 URL 均不写入报告。若要复现，凭据继续保留在已有本地配置或进程环境中，不要提交到 Git。

## 4. 使用的命令

以下命令从仓库根目录执行；不同服务分别保留终端运行。这里记录主要执行步骤，不是要求现在重新提交云任务。

### 4.1 打包主后端

```bash
cd /home/YLiF4/XJI/XJICloud
source .tools/env.sh
(cd services/backend && mvn -B package)
```

`.tools/env.sh` 按需提供本机已有 JDK/Maven 工具链。此次打包为 `BUILD SUCCESS`，63 个测试通过，没有跳过测试。

### 4.2 启动 ECI Manager

```bash
bash eci-manager/scripts/start.sh cloud
```

沿用 `eci-manager/.env`。启动前确认是真云模式，并注意已有持久化任务可能被恢复处理；不要把“启动服务”当作无任何云资源影响的操作。

### 4.3 启动主后端

```bash
(
  source .tools/env.sh
  source eci-manager/scripts/load-env.sh
  set -a
  source deploy/config/backend.env.local
  set +a
  cd services/backend
  mvn spring-boot:run
)
```

加载顺序为 ECI 环境在前、主后端本地覆盖在后，使主后端使用正确的服务间 API key，并保持隔离 H2 与本机 Redis 设置。不要只启动 Maven 而跳过环境加载。

### 4.4 健康检查与前端注册

```bash
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health
curl --fail --silent --show-error http://127.0.0.1:8081/actuator/health

export PATH="/tmp/xjicloud-web2-toolchain/node/bin:$PATH"
pnpm --filter @xjicloud/web2 exec vite --host 127.0.0.1
```

Node/pnpm 起初不在 PATH；本次使用临时目录中的 Node 22.14.0 与 pnpm 9.15.0，未修改项目 manifest、lockfile 或 shell 配置。上述临时路径不是永久部署工具链，重启或清理 `/tmp` 后不保证仍存在。

使用 `exec vite` 而不是触发 `pnpm dev` 的 `predev`，避免生成或复制源码目录下的前端资产。前端登录页位于 `http://localhost:5176/login`，经审批使用 Windows PowerShell 的 `Start-Process 'http://localhost:5176/login'` 打开。用户手动完成普通用户注册。

登录页和 `LoginView.vue` 编译请求返回 HTTP 200，`/api/v1/auth/need-captcha` 代理成功；这些检查不等同于完整浏览器上传训练 E2E。

另执行了：

```bash
pnpm test:web2
```

结果为 23 个测试文件、60 个测试通过。

### 4.5 提交本次真云联调

本次实际通过 Python 设置进程环境：覆盖新注册普通用户的登录参数、清除可能残留的 `TRAINING_TEST_TOKEN`、设置 `PYTHONUNBUFFERED=1`，再以 `os.execvpe` 调用现有 Bash 脚本。没有将新密码写回配置文件。

下面是安全等价的复现方式，密码交互输入，不公开本次真实凭据：

```bash
(
  source eci-manager/scripts/load-env.sh
  set -a
  source deploy/config/backend.env.local
  set +a

  unset TRAINING_TEST_TOKEN
  read -r -p '普通用户账号: ' TRAINING_TEST_USERNAME
  read -r -s -p '普通用户密码: ' TRAINING_TEST_PASSWORD
  printf '\n'
  export TRAINING_TEST_USERNAME TRAINING_TEST_PASSWORD
  export PYTHONUNBUFFERED=1
  bash scripts/integration-training-dispatch-cloud.sh
)
```

交互覆盖必须放在配置加载之后。本次图片目录为 `/home/YLiF4/XJI/新文科楼`，由 `TRAINING_TEST_DATASET_DIR` 指定；`TRAINING_TEST_BUILDING=新文科楼`，`TRAINING_TEST_TIMEOUT_SEC=25200`。

**该命令会上传数据、创建新项目和任务，并启动收费 GPU 云容器，不是只读检查。** 脚本超时、断网或 Ctrl+C 不会自动取消云任务；需要通过现有任务取消能力处理，并确认实例释放，不能以关闭终端作为停止计费的依据。

## 5. 实际结果

### 5.1 输入、资源与训练

| 检查项 | 结果 |
| --- | --- |
| 数据集 | “新文科楼”，81 张 JPG/JPEG |
| 图片总大小 | 2,365,337,600 字节，约 2.2 GiB |
| 云容器输入 | 81 个输入文件，下载预签名 URL 均为 OSS 内网地址 |
| COLMAP | 运行时观察到注册图片 81/81，QC 通过，稀疏点数 84,924 |
| 3DGS 训练 | 实际 15,000 步；不是独立提交脚本默认的 2,000 步 |
| 资源 | 16 CPU、60 GiB 内存、1 GPU，`ecs.gn7i-c16g1.4xlarge` |
| 容器限制 | 共享内存 `8Gi`，`restartPolicy=Never`，`maxRuntimeSec=21600` |
| 训练入口 | `/opt/3dgs/docker/entrypoint.sh`，主后端传入 `shell` 与 base64 bootstrap |
| 新增 ECI 任务数 | 1，与本次执行前快照比较；不将管理器历史任务计入本次结果 |

### 5.2 任务终态、产物与时间

| 检查项 | 结果 |
| --- | --- |
| 联调脚本 | 退出码 0 |
| 主后端任务 | `COMPLETED`，进度 100，stage 为 `completed` |
| ECI 任务 | `SUCCEEDED` |
| 实例 | ECI Manager API 返回 `TERMINATED`，记录的云状态为 `Succeeded` |
| PLY 下载检查 | GET `Range: bytes=0-4095` 返回 HTTP 206，头部为 `ply`，顶点数非零 |
| PLY 大小 | 772,538,510 字节，约 773 MB |
| PLY 顶点数 | 3,273,462 |
| ECI startedAt | `2026-10-03T14:15:55.200422Z`，北京时间 22:15:55 |
| ECI endedAt | `2026-10-03T14:53:24.705999Z`，北京时间 22:53:24 |
| ECI 任务生命周期 | 约 37 分 30 秒；不能当作纯训练耗时，也不含此前客户端上传时间 |

实例清理结论来自 ECI Manager API，**未独立登录阿里云控制台核验资源释放，也未核对账单**。没有下载完整 PLY、计算整文件校验和或进入 3D 查看器验收画质。

本次任务标识，便于后续查日志：

```text
projectId       ebe015bb-816d-487d-a194-0577d3eb8000
jobId           921db0f6-c214-4f0c-83fa-bd59ef2c45d0
eciTaskId       c0c4ac0e-6fa2-4925-86f1-65655fb72c31
localInstanceId e1833adf-d87d-4981-b747-bdcac26631b9
cloudInstanceId eci-bp18og25v2iw1ru9gm3m
```

## 6. 与真实用户和生产场景的差异

| 方面 | 本次云测 | 真实场景需要额外验证 |
| --- | --- | --- |
| 客户端 | Python 脚本调用主后端、同步逐张上传 OSS | web2 浏览器选文件、CORS、上传进度/重试、会话过期与交互错误态 |
| 上传实现 | 递归扫描目录，先把全部图片读入进程内存 | 浏览器的上传性能和内存模型；本次不是性能压测 |
| 状态观察 | 脚本轮询主后端与 ECI Manager API | UI 状态同步、SSE、页面刷新与重新登录后的状态恢复 |
| 服务部署 | WSL 本机 H2、本机 Redis、前台 Maven、loopback | 生产 PostgreSQL、持久化部署、守护进程、反向代理/TLS、多机网络 |
| 云端执行 | 真实 OSS、真实 ECI/GPU，与本地管理服务联动 | 生产网络权限、镜像访问、配额和费用治理；不是 Mock 测试 |
| 数据覆盖 | 一个建筑、81 张 JPG/JPEG | PNG/WebP、多种数据规模和拍摄质量；本次不能宣称其他格式真云通过 |
| 负载与故障 | 单账号、单任务、成功路径 | 并发、幂等/重复提交、取消、配额不足、重启恢复、密钥热切与失败清理 |
| 模型产物 | 训练产出 PLY 上传 OSS，主后端签发下载 URL | 不等同于自动导入本地 ModelAsset/模型库，也不证明查看器、编辑与导出流程可用 |
| 验收强度 | PLY Range、头部、大小、顶点数检查 | 全文件完整性、模型渲染与视觉质量；单次成功不能作为上线验收结论 |

### 图片目录与 ZIP 的区别

- **本次主后端联调入口使用本地图片目录。** 脚本逐张上传图片及 manifest，不上传 ZIP，也不负责解压 ZIP。
- 独立 `eci-manager/scripts/submit-3dgs-standard.sh` 可以消费 OSS 中的 `3dgs/raw/<建筑名>.zip`；该路径绕过主后端创建项目、上传凭证、队列派发等业务环节，因此不能替代本次联调。
- 云端 bootstrap 支持 ZIP 或独立图片输入，不代表主后端的联调脚本接受 ZIP。要复现本次路径，先准备解压后的图片目录，再让 `TRAINING_TEST_DATASET_DIR` 指向该目录。

云容器使用预签名 URL 获取数据和回传产物，不需要携带长期 OSS AK/SK。本次训练结果存于 OSS 是现有训练派发链路的行为，不是对平台本地 PLY/SPZ 模型库存储策略的迁移。

## 7. 前置问题与处理边界

以下问题在成功执行前处理过，不应混写成训练算法失败：

- OSS endpoint 为 null：通过已有配置加载方式解决，不改 `OssStorageService` 或依赖注入代码。
- Linux 数据路径中的反斜杠、部署 YAML 误粘文本：由用户手动修正，Agent 没有改配置文件。
- Redis 与 Docker 的 WSL 集成：由用户手动处理；部署 Redis 的建议命令为 `docker compose -f deploy/docker-compose.yml up -d redis`，不将建议命令记为 Agent 实际执行记录。
- 隔离 H2 没有普通用户：由用户在 web2 注册，联调通过进程环境使用该账号，不冒用管理员或直接写库。
- pnpm 不可用：使用临时 Node/pnpm 工具链和已有可用项目依赖恢复启动，不改源码、manifest 或 lockfile。

执行前后比对输入文件 SHA-256，最终记录的 `changedSourceOrConfig` 为空。仓库本来已有其他修改与未跟踪文件，此结论不表示整个工作区是干净的，也不把他人的改动算成本次云测修改。

## 8. 本机证据与复查注意

关键脱敏记录：

| 文件 | 用途 |
| --- | --- |
| `/tmp/xjicloud-cloud-final-result.json` | 最终业务/ECI/实例状态、时间、PLY 检查、新增任务数与文件变更结果 |
| `/tmp/xjicloud-cloud-spec-check.json` | 资源与入口检查、输入文件数 |
| `/tmp/xjicloud-cloud-final-before.json` | 最终提交前的 ECI 任务快照，区分历史任务与本次新增任务 |
| `/tmp/xjicloud-cloud-final-input-hashes.json` | 执行前源文件/配置摘要，用于确认没有内容改动 |
| `/tmp/xjicloud-cloud-integration-20261003.log` | 联调状态变化记录 |
| `/tmp/xjicloud-cloud-container-tail-safe.log` | 保留的容器日志尾部，不是完整训练日志 |
| `/tmp/xjicloud-backend-package-current-20261003.log` | 本次后端打包及 63 个测试结果 |
| `/tmp/xjicloud-web2-tests.log` | web2 的 23 文件、60 测试结果 |

这些是本机临时证据，未作为附件提交；`/tmp` 清理后可能不存在。COLMAP 指标来自运行时观察，日志尾部不保证保留对应完整输出。日志和 API 原始响应可能含凭据或带签名 URL，对外分享前必须再次脱敏。

本次文档整理不重跑云测、不改服务配置。浏览器上传、启动训练与取消的补测记录见 §9；模型查看、实例释放的云控制台核查、并发和故障场景仍需另行验收。

## 9. 浏览器 E2E 补测：上传与启动通过，取消失败

### 9.1 测试方式与结论

2026-10-03，用户在 web2 浏览器页面点击“开始上传并训练”，随后操作取消任务。浏览器操作与上传成功由用户实测反馈；Agent 通过 OSS 只读预检、服务日志、任务 API 和数据库只读查询核对失败原因与服务端状态。这不是 Agent 执行的自动化浏览器 E2E。

| 环节 | 结果 | 证据与边界 |
| --- | --- | --- |
| OSS 浏览器直传 | 最初失败，需要手动配置 Bucket CORS | 浏览器预检报错；只读 OPTIONS 返回 HTTP 403，明确提示未启用 CORS |
| 图片上传 | 用户手动处理 CORS 后成功 | 用户实测反馈；后续已创建对应 ECI 训练任务 |
| 开启云端训练 | 通过 | ECI Manager 查询到任务 `RUNNING`、实例云状态 `Running` |
| 取消按钮调用主后端 | 通过 | 对应主后端任务已为 `CANCELLED`，但云端取消待完成标记仍为 true |
| 取消训练并释放实例 | **失败** | ECI Manager 写取消中间态报数据库错误，任务与实例仍在运行 |
| 连续训练进度、完整训练产物及模型查看 | 未验收 | 启动成功不能代表训练完成、持续进度更新或模型质量通过 |

### 9.2 OSS 需要手动配置 CORS

浏览器从 `http://localhost:5176` 发起图片直传，请求 OSS 对象的预签名上传 URL。首次失败为预检响应缺少 `Access-Control-Allow-Origin`，页面统一显示“上传失败，请重试”。没有把完整预签名 URL、AccessKey 标识或签名参数保存到本文。

排障时，对相同对象路径执行不带签名的只读 OPTIONS 请求，指定 `Origin: http://localhost:5176`、请求方法 `PUT`、请求头 `content-type`，实际响应为：

```text
HTTP 403 Forbidden
Code: AccessForbidden
Message: CORSResponse: CORS is not enabled for this bucket.
```

因此不能认为阿里云会自动允许本地网页跨域上传。用户需在 OSS 控制台目标 Bucket 的跨域设置中手动允许实际网页来源；本次为 `http://localhost:5176`，上传方法为 `PUT`，并允许 `Content-Type` 请求头。预签名鉴权不替代浏览器跨域许可。

用户手动处理后反馈“已经成功上传”，并开始云端训练。此处记录已验证的行为，不将先前建议的全部 CORS 字段写成已经读取并核验的云端配置。

### 9.3 取消失败的任务与状态快照

以下为排障时查询到的关联任务；最后一次管理器 API 状态核对时间为 **2026-10-03 23:52:02（北京时间）**。这是当时的证据快照，不代表后来是否已手动释放实例。

```text
backendJobId    8293f1c7-6553-43a4-af57-7b79ea92883a
eciTaskId       4963ba1e-53dc-4273-a423-529e1968c962
localInstanceId 3285b246-f66e-497a-9c2e-df92baee23c6
cloudInstanceId eci-bp1hhmkoa54g0zo5uk7h
```

| 查询对象 | 状态 |
| --- | --- |
| 主后端 `TRAINING_JOBS` | `STATUS=CANCELLED`，`ECI_CANCELLATION_PENDING=TRUE`，绑定上述 ECI taskId |
| ECI Manager `ECI_TASK` 及任务 API | `RUNNING` |
| ECI Manager 实例 API | 本地 `RUNNING`，记录的云状态 `Running` |

用户观察到取消后云实例未删除，与上述服务端状态一致。本次排障未独立登录云控制台核查资源或账单。

### 9.4 根因：旧数据库枚举未迁移，取消在删除前失败

前端并非只有一个本地“取消”按钮：它实际调用 `POST /api/v1/jobs/{jobId}/cancel`。主后端先记录业务取消状态与待收敛标记，再由后台调度器调用 ECI Manager 的取消接口。

```text
浏览器取消按钮
→ 主后端记录 CANCELLED + eciCancellationPending=true
→ 调度器调用 ECI Manager 取消接口
→ ECI Manager 尝试将 ECI_TASK.STATUS 写为 CANCELLING
→ H2 旧枚举拒绝该值，事务失败
→ 尚未执行实例删除步骤，云容器继续运行
```

当前代码要求 ECI 任务经历 `CANCELLING → CANCELLED`，其中 `CANCELLED` 应表示实例已确认清理。然而只读查询实际持久化数据库发现，`ECI_TASK.STATUS` 仍是只包含六种旧值的 H2 ENUM：

```text
CANCELLED, FAILED, PENDING, RUNNING, STARTING, SUCCEEDED
```

**该列没有 `CANCELLING`。** ECI Manager 日志持续报错：

```text
Value not permitted for column
('CANCELLED', 'FAILED', 'PENDING', 'RUNNING', 'STARTING', 'SUCCEEDED'):
"CANCELLING"
update eci_task ... set status=? ... [22030-224]
```

这是沿用旧持久化数据库后的表结构升级缺口，不是 API key 配错或前端没有发取消请求。已有 `ddl-auto: update` 在该环境没有把此列升级为包含新状态的枚举。主后端仍会重试取消，但重复请求无法绕过相同数据库错误。

相关实现位置（仓库根目录相对路径）：

- `apps/web2/src/features/dataset-training/infrastructure/repositories/http-training-job.repository.ts:54`：发送用户取消请求。
- `services/backend/src/main/java/com/xjicloud/job/TrainingJobService.java:243`：先标记业务取消，保留云端取消待完成标记。
- `services/backend/src/main/java/com/xjicloud/job/TrainingDispatchService.java:197`：后台重试云端取消并检查终态。
- `eci-manager/src/main/java/com/xjicloud/eci/task/EciTaskService.java:197`：先写 `CANCELLING`；后续才调用实例删除。

另一个暴露出的产品语义缺口是：页面显示“已取消”并不等于云资源已释放，当前业务状态没有直观区分“取消已受理”和“云端清理完成”。因此不能用按钮调用成功或主后端 `CANCELLED` 作为取消验收的唯一标准。

### 9.5 修复状态、风险与后续验收

截至本次补记，**取消问题仅完成定位，没有完成修复或复测**。排障没有修改源码、配置、实际数据库结构或业务数据，也没有由 Agent 执行云实例删除。

已提出的处理方向：先核对并释放上述已请求取消的计费实例；停止 ECI Manager 并备份现有数据库后，手动补齐 `ECI_TASK.STATUS` 的枚举结构迁移，保留历史任务与运行时配置，不删除整个数据库重建。扩充枚举的 SQL 已在独立内存 H2 中验证旧状态数据保留、新状态可写入，但这不等于实际数据库已迁移。

修复后需要重新验收：浏览器发起取消，主后端待收敛标记最终清除，ECI 任务达到取消终态，实例为 `TERMINATED`，并在云控制台独立确认资源释放。在此之前存在**页面已显示取消、云容器仍继续运行与计费的风险**。

本次补测排障日志为 `/tmp/xjicloud-eci-manager-cloud-20261003.log`。数据库枚举与主后端待收敛标记来自只读 SQL 查询，最后状态来自管理器 GET API；未将原始含敏感字段的 API 响应写入本文。本次只更新测试记录，不重新上传、提交或取消任何云任务。
