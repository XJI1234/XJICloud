# 阿里云 CCI GPU Worker 部署指南

生产环境算力使用 **CCI 弹性容器实例**（替代 K8s 内 mock gpu-worker），按需启停以节省成本。

## 1. 推送镜像到 ACR

```bash
cd /path/to/XJICloud

# 登录 ACR（替换为你的 registry 地址）
docker login registry.cn-hangzhou.aliyuncs.com

docker build -t xjicloud/gpu-worker gpu-worker/
docker tag xjicloud/gpu-worker:latest \
  registry.cn-hangzhou.aliyuncs.com/YOUR_NAMESPACE/xjicloud-gpu-worker:latest
docker push registry.cn-hangzhou.aliyuncs.com/YOUR_NAMESPACE/xjicloud-gpu-worker:latest
```

或使用脚本：

```bash
export ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
export ACR_NAMESPACE=YOUR_NAMESPACE
export IMAGE_TAG=latest
./deploy/k8s/scripts/push-gpu-worker-acr.sh
```

## 2. 创建 CCI 实例（控制台）

| 配置项 | 建议值 |
|--------|--------|
| 镜像 | `registry.cn-hangzhou.aliyuncs.com/YOUR_NAMESPACE/xjicloud-gpu-worker:latest` |
| 规格 | GPU 计算型（按训练需求选择） |
| 网络 | 与 ACK / 后端 **同一 VPC**，分配私网 IP |
| 重启策略 | 按需；任务结束后停止实例 |

### 环境变量（必填）

| 变量 | 示例 | 说明 |
|------|------|------|
| `XJICLOUD_BACKEND_URL` | `http://<backend内网地址>:8080` | 后端 Service 或 SLB 内网地址 |
| `WORKER_SECRET` | 与 `xjicloud.worker.shared-secret` 一致 | Worker 注册密钥 |
| `WORKER_NAME` | `cci-worker-01` | 管理面板显示名称 |

> Worker 通过后端下发的 presigned URL 访问 OSS，**一般无需**在 CCI 中配置 OSS AccessKey。

## 3. 安全组

| 方向 | 规则 |
|------|------|
| CCI → 后端 | 出站 TCP 8080 到后端安全组 |
| 后端 ← CCI | 入站 TCP 8080 来自 CCI 安全组 |
| CCI → OSS | 出站 HTTPS 443 到 `*.aliyuncs.com` |

## 4. 验证

1. 管理面板 `https://cloud.example.com/admin/` → Workers → 状态 **ONLINE**
2. 创建图片训练任务 → 状态 QUEUED → RUNNING → COMPLETED
3. `curl http://<backend>/actuator/health` 从 CCI 同 VPC 可达

## 5. 按需启停

| 场景 | 操作 |
|------|------|
| 队列积压 | 启动 1~N 个 CCI 实例 |
| 队列清空 | 停止 CCI 实例 |
| 自动扩缩 | 监听 Redis `LLEN xjicloud:jobs` 或 Admin API，调用 CCI OpenAPI |

详见 [Deploy.md §5.6](../../Deploy.md)。

## 6. 本地 K3s 与 CCI 的关系

- **本地验证**：使用 `deploy/k8s/gpu-worker/` Deployment（mock，无 GPU）
- **生产**：删除或缩容 K8s gpu-worker，改用 CCI 实例连接同一后端

ACK overlay（`overlays/aliyun/`）**不包含** gpu-worker，算力完全由 CCI 承担。
