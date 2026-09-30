# 阿里云 CCI 按任务训练容器

用户上传完成后，主 backend 将任务写入 Redis；主 backend `TrainingDispatchScheduler` 消费队列并调用 ECI Manager 创建 CCI one-shot 容器。调度责任保留在主 backend。训练镜像已经上传，通过 `XJICLOUD_ECI_MANAGER_TRAINING_IMAGE` 配置；本仓库不构建或推送训练镜像。

## 镜像与服务配置

ACK backend ConfigMap 中的镜像字段是示例占位值，部署时替换为已有 3DGS 镜像：

```yaml
XJICLOUD_ECI_MANAGER_TRAINING_IMAGE: registry.example.com/your-project/3dgs:1.0.0
```

backend 还需配置可访问的 `XJICLOUD_ECI_MANAGER_BASE_URL`，以及和 ECI Manager `ECI_API_KEY` 相同的服务间 `XJICLOUD_ECI_MANAGER_API_KEY`。该 HTTP API key 不是阿里云 AK/SK；云钥继续由主 backend 云钥列表管理，并通过现有 runtime credentials 流程提供给 ECI Manager。不要把 AK/SK 注入训练容器。

## ECI 任务参数

backend 提交的 one-shot 任务使用已配置镜像的 `/opt/3dgs/docker/entrypoint.sh`，以 `shell` 运行 base64 bootstrap。资源为 16 CPU、60 GiB、1 GPU、`gn7i-c16g1.4xlarge`、`shm=8Gi`、最长运行 `21600` 秒。输入/输出使用 `storage.mode=keys`，ECI Manager 为任务签发 OSS presigned URL。

真云 ECI Manager 必须配置 `ECI_OSS_PRESIGN_EXPIRATION_MINUTES` 至少为 `1440`，以覆盖 21600 秒最长运行时间和余量；OSS endpoint 使用与 ECI 同地域的内网地址。容器只需出站 HTTPS 访问这些 URL，不需要注册 Worker、轮询 Redis 或访问 backend。

bootstrap 源文件为 `eci-manager/scripts/3dgs-bootstrap.py`，backend classpath snapshot 为 `services/backend/src/main/resources/3dgs-bootstrap.py`。源文件变更必须同步 snapshot，并由测试验证两者字节一致。

现有独立 ECI 标准档提交样例见 [`eci-manager/scripts/submit-3dgs-standard.sh`](../../../../eci-manager/scripts/submit-3dgs-standard.sh)。它是手工 ECI task 样例；用户训练仍走 backend 调度链路。

## K8s 部署关系

- ACK overlay 部署 backend、frontend、Redis 等应用资源；训练 CCI 由 ECI Manager 按 backend 任务请求创建。
- `deploy/k8s/gpu-worker/deployment.yaml` 仅作为历史文件保留，不在活动 Kustomize `resources` 中。
- `services/gpu-worker/` 仅保留旧 Worker 源码；Compose、K8s 和镜像构建/导入脚本不构建或启动它。
