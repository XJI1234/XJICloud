# XJICloud Kubernetes 部署

本地 3 VM K3s 验证与阿里云 ACK 上云清单。

## 节点规划

| VM | IP | role 标签 | 工作负载 |
|----|-----|-----------|----------|
| 本机 | 192.168.203.133 | edge | K3s Server、Ingress、frontend |
| VM2 | 192.168.203.134 | backend | PostgreSQL、Redis、backend |
| VM3 | 192.168.203.135 | storage | MinIO、gpu-worker |

## 快速开始（本地 K3s）

### 1. 三台 VM 前置准备

```bash
sudo swapoff -a
sudo setenforce 0  # 或配置 SELinux 允许容器
# 可选: sudo systemctl disable --now firewalld
```

### 2. 安装 K3s

**若 GitHub 下载失败**（国内网络），可先手动下载二进制再安装：

```bash
curl -fL -o /tmp/k3s "https://github.com/k3s-io/k3s/releases/download/v1.30.5%2Bk3s1/k3s"
chmod +x /tmp/k3s && sudo install -m 755 /tmp/k3s /usr/local/bin/k3s
curl -sfL https://get.k3s.io | INSTALL_K3S_SKIP_DOWNLOAD=true sh -s - server \
  --docker --tls-san=192.168.203.133 --write-kubeconfig-mode=644
```

> 推荐使用 `--docker`，复用 Docker 已拉取的镜像，避免 containerd 单独拉取失败。

**133（本机）：**

```bash
chmod +x deploy/k8s/scripts/*.sh
sudo ./deploy/k8s/scripts/install-k3s-server.sh
```

**134、135：**

```bash
export K3S_TOKEN=<来自133的node-token>
sudo ./deploy/k8s/scripts/install-k3s-agent.sh
```

**133 打标签：**

```bash
sudo ./deploy/k8s/scripts/label-nodes.sh
```

### 3. 构建并导入镜像

```bash
sudo ./deploy/k8s/scripts/build-and-import-images.sh
```

### 4. 部署

```bash
cp deploy/k8s/secrets.example.yaml deploy/k8s/secrets.yaml
# 编辑 secrets.yaml 修改密码（生产必改）
sudo ./deploy/k8s/scripts/deploy-local.sh
```

### 5. 验证

```bash
sudo ./deploy/k8s/scripts/validate-local.sh
```

访问：

- 用户前端：http://192.168.203.133/
- 管理面板：http://192.168.203.133/admin/（admin / admin123）
- MinIO API：http://192.168.203.135:30900

## 目录结构

```
deploy/k8s/
├── kustomization.yaml       # 本地 K3s base
├── namespace.yaml
├── secrets.example.yaml
├── configmap-backend.yaml
├── redis/
├── postgres/
├── minio/                   # NodePort 30900（浏览器直传）
├── backend/
├── gpu-worker/
├── frontend/                # Dockerfile + nginx.conf
├── ingress.yaml
├── scripts/                 # 安装、构建、部署、验证脚本
└── overlays/aliyun/         # ACK + RDS + OSS（无 MinIO/Postgres/gpu-worker）
```

## 阿里云 ACK

```bash
cp deploy/k8s/overlays/aliyun/secrets.example.yaml deploy/k8s/overlays/aliyun/secrets.yaml
# 编辑 configmap-aliyun.yaml、kustomization.yaml 中的 ACR 地址与域名
kubectl apply -k deploy/k8s/overlays/aliyun/
```

GPU 算力见 [overlays/aliyun/CCI.md](overlays/aliyun/CCI.md)。

## 关键配置

| 项 | 本地值 |
|----|--------|
| OSS endpoint | `http://192.168.203.135:30900` |
| MinIO CORS | `http://192.168.203.133` |
| backend CORS | `http://192.168.203.133` |

浏览器直传 MinIO 时，防火墙须放通 **用户 PC 网段** → 135:30900，见 [Deploy.md §5.1.5](../Deploy.md)。
