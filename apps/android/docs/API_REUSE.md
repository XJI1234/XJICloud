# 后端 API 复用与移动端协议限制

2026-10-19。以下按当前 Controller、DTO 与 Service 核对，不把文档示意当成新 API。

源码依据位于 `services/backend/src/main/java/com/xjicloud/{auth,project,job,model}`；Web 对照位于 `apps/web2/src/features/*/infrastructure`。不改现有后端。

## 1. 通信与鉴权

- Base URL 使用构建配置 `API_BASE_URL`，不硬编码线上/开发机地址；统一响应 `{success,message,data}`。下载/OSS PUT/SSE 不是 JSON wrapper。
- 用户请求 `Authorization: Bearer <accessToken>`。用户 token 为 type=user；禁止调用 admin/worker API。当前无 refresh/logout/me API；登出是本地清会话，用户信息来自登录响应。
- login/register 响应含 `accessToken, tokenType, expiresInMs, userId, username, displayName`。按 JWT exp 校验，HTTP 401 为最终依据；不以 expiresInMs 每次重启重新计时。
- 本轮实际核对：无效 Bearer 访问 `/projects` 返回空体 HTTP 403。当前 SecurityConfig 未配置专门的认证失败入口，不能把所有 403 都视为会话失效，否则会误清有效会话。客户端保留 401/本地 exp 处理，403 显示权限错误；服务端撤销或不再认可、但本地尚未到期的凭据可能需要主动退出重登。该契约差异留给后端专项，本阶段不改后端。
- API、OSS 各自独立 HTTP 客户端；OSS 直传只带签名和指定 Content-Type，不能带用户 Bearer。签名 URL、密码、token、文件 URI 不入日志。
- Release 仅 HTTPS；debug 的 HTTP 白名单仅为 `127.0.0.1`、`localhost`、`::1` 及模拟器宿主别名 `10.0.2.2`。真机本机联调使用 `http://127.0.0.1:8080` 配合 `adb reverse tcp:8080 tcp:8080`；局域网服务需使用 HTTPS。不能开启 release 全局 cleartext。后端生成的 OSS URL 也必须对设备可达，API 可达并不等于 OSS 可达。

## 2. 接口清单

下表路径省略 `/api/v1`。

| BC | 方法与路径 | 输入/输出与客户端用途 |
|---|---|---|
| Identity | GET `/auth/captcha` | `captchaKey,captchaImage`；图片内容按真实响应 fixture 解码，不假设存在 expires 字段 |
| Identity | GET `/auth/need-captcha?username=...` | `needCaptcha`；query 需编码 |
| Identity | POST `/auth/login` | `{username,password,captchaKey?,captchaCode?}` |
| Identity | POST `/auth/register` | `{username,password,displayName?,captchaKey,captchaCode}` |
| Project | GET/POST `/projects` | 列表；创建 `{name,description}` |
| Project | PATCH/DELETE `/projects/{projectId}` | 改名/描述；删除 |
| Dataset | POST `/projects/{projectId}/datasets` | `{name,files:[{archivedName,originalName,contentType,sizeBytes}]}` → `jobId,manifestUploadUrl,uploads[]` |
| Dataset | PUT `<uploads[].uploadUrl>` | 对应 MIME 原始图片；OSS，不是后端 multipart |
| Dataset | PUT `<manifestUploadUrl>` | `application/json`，manifest v1；uploads 中也含 manifest，去重只传一次 |
| Dataset | POST `/projects/{projectId}/datasets/{jobId}/complete` | 仅全部 PUT 成功后调用；返回 JobResponse |
| Training | GET `/projects/{projectId}/jobs`、GET `/jobs/{jobId}` | 项目任务列表 / 当前快照 |
| Training | GET `/jobs/{jobId}/events` | 带 Bearer 的 `text/event-stream`，event=progress，data JSON |
| Training | POST `/jobs/{jobId}/cancel`、DELETE `/jobs/{jobId}` | 取消 / 终态删除；没有用户 retry 接口 |
| Model | GET `/projects/{projectId}/models` | ModelResponse 数组 |
| Model | POST `/projects/{projectId}/models/upload-sessions` | `{fileName,sizeBytes}` → `sessionId,chunkSizeBytes,receivedBytes,sizeBytes` |
| Model | GET `/models/upload-sessions/{sessionId}` | 查询已收字节，用服务端位置恢复，不靠本地进度猜测 |
| Model | PUT `/models/upload-sessions/{sessionId}/chunks` | `Content-Range: bytes start-end/total`，`application/octet-stream`；响应 `receivedBytes` |
| Model | POST `/models/upload-sessions/{sessionId}/complete` | 收齐后入库；已有幂等处理，返回 ModelResponse |
| Model | DELETE `/models/upload-sessions/{sessionId}` | 取消未完成会话 |
| Model | GET `/models/{modelId}/download` | Bearer 鉴权字节流，支持 Range；先下载私有文件再给 SDK |
| Model | DELETE `/models/{modelId}` | 删除云端资产；成功后失效该账号缓存 |
| Viewer | GET `/models/{modelId}/viewer-config` | 可读取配置元数据；不套用坐标来源不明的默认相机，不写回 |

现有 multipart 模型上传、download-token、export、versions/restore 保留在服务端但首版不依赖；普通查看走 Bearer 下载，无需借 SuperSplat token URL 绕过仓储。

## 3. DTO 与状态

`ProjectResponse` 按真实 record 映射；当前工程/最近工程不是后端属性。`ModelResponse` 为 `id,projectId,fileName,format,sizeBytes,version,createdAt,updatedAt`。`JobResponse` 为 `id,projectId,name,status,progress,stage,message,downloadUrl,errorMessage,createdAt,updatedAt`。

状态严格为 `PENDING | UPLOADING | QUEUED | RUNNING | COMPLETED | FAILED | CANCELLED`；陌生值映射 unknown，不当完成。暂停/等待网络属于本地 UploadSession 状态，不向后端提交虚构的 PAUSED。仅终态可删除任务。

SSE parser 需要增量 UTF-8、半帧缓存、CRLF/LF、多行 data、注释/心跳处理；当前后端无心跳，parser 兼容不代表后端已发送。实际 progress payload 仅 `jobId,status,progress,stage,message`，不含 `downloadUrl/errorMessage`。后端 subscribe 不推初始状态，也无 Last-Event-ID 重放承诺；不能依赖等待首事件才查询。前台立即 GET 并订阅，健康流仍每 15 秒 GET 校正，失败降级 5 秒；用会话代际/观察序号丢弃迟到快照。COMPLETED/FAILED 事件后 GET 完整响应成功再停止，失败保留可重试的快照查询。CANCELLED 停 SSE 但前台有页面订阅者时继续 15 秒 GET，允许 Worker 回写后恢复观察；离页/后台停掉，重新进入必查状态。

## 4. 图片逻辑归档

不是 ZIP/TAR。系统选择器 → 过滤 JPG/JPEG/PNG/WebP → 按相对目录/文件名稳定排序（同名以选择序号作确定性次序）→ `0001.jpg` 等四位名 → manifest v1 → 每图 PUT + manifest PUT → complete。

Manifest：`{version:1,imageCount:N,files:[{archivedName,originalName,contentType,sizeBytes}]}`。后端归档名正则是 `\\d{4}\\.(jpg|jpeg|png|webp)`，因此客户端明确限制 **1–9999 张**，超出提示拆分；不能仅用 padLeft 产生 10000.jpg 再让服务端拒绝。MIME 与扩展一致，不支持 HEIC 不自动转码，空文件拒绝。

选择器没有目录相对路径时按名称 + 原始选择序号排序，页面展示最终顺序。任务保存同一 manifest 与来源身份，恢复不重新排序重命名。非 seek 来源要受控缓存；大模型/大量图片用流，不能 `readAsBytes` 全量聚合后跨通道。

## 5. 暂停/恢复：现有 API 能做到的边界

### 图片数据集

1. 上传前保存 userId/projectId/jobId、manifest、每图成功状态与来源授权。签名 URL 仅保留本进程内存，不写普通偏好或日志。
2. 切后台取消当前 PUT，保留“已确认成功”的图片；当前未确认项返回前台从文件头重传同一对象。未带分片协议，不能声称单张图片按字节续传。
3. 回前台先 GET job。QUEUED/RUNNING/COMPLETED 进入任务跟踪，不重做 complete；UPLOADING 且 URL 和文件仍有效则继续未确认项，最后才 complete。
4. OSS 403/过期、来源授权丢失或进程重启后 URL 不在内存：说明原因，用户确认取消旧 UPLOADING job 后创建新任务重传。没有刷新 URL/列 OSS 对象的用户 API，不能承诺断电恢复；保留能重新选取的 manifest 草稿。
5. `completeDataset` 仅接受 UPLOADING，**不是幂等**。超时先查询状态；若仍 UPLOADING，受控重试前确保本地所有 PUT 成功。若重试被状态拒绝，再查快照确认实际结果，不盲目重试或生成另一个 job。
6. 创建 dataset 的 POST 也无 idempotency key。创建响应丢失时刷新工程 jobs，呈现候选任务供用户核对，不靠名称推断身份并自动重复创建。

### 模型分片

模型会话本地保存 sessionId、源文件指纹/授权、目标账号和工程。回前台 GET 会话，以 receivedBytes 为权威，从对应范围顺序传；遵守服务端 chunkSizeBytes。原文件身份/大小发生变化则拒绝续传；POST 创建超时不盲目重试。若会话 404/过期，提示新建而非无限重试。complete 幂等不代表任意 API 都可自动重试。

## 6. 训练产物与当前后端限制

- 训练产物在 OSS，工程 PLY/SPZ 在本地盘。COMPLETED 不会自动生成 ModelAsset；先提供下载/本地打开。若用户要加入工程模型，可显式选择产物文件走已有模型上传流程，不改存储策略。
- `TrainingJobService.toResponse` 优先复用持久化的 outputDownloadUrl，仅当其为 null 时生成签名；因此重查 job **不保证拿到新的未过期 URL**。403 可重查一次，但不能承诺自动修复；若仍失效提示服务端限制。
- RUNNING 取消后 Worker 仍可能回写 COMPLETED。客户端呈现实际后续状态，不能用乐观更新永久锁死 CANCELLED。
- completeDataset 当前没有逐对象完整性核验；客户端必须完成所有图和 manifest 后再提交。不能把客户端校验称作服务端安全保证。
- 补签、幂等 create/complete、产物 URL 刷新、Worker 取消确认属于后续后端专项候选；本计划不擅自修改后端。如果这些能力成为上线硬需求，应单独立项后端契约与验证。
