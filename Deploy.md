# XJICloud 云平台部署指南

本文档说明如何在 **Linux（Ubuntu / CentOS / Alibaba Cloud Linux）** 上部署 XJICloud。

**推荐生产拓扑：** 前端、后端、对象存储、算力容器分机部署；生产环境对象存储使用 **阿里云 OSS**，算力使用 **阿里云 CCI 弹性容器实例**（按需启动）。预生产/自建环境可用 **MinIO + 自建 GPU 服务器** 替代。

---

## 1. 架构概览

### 1.1 分机部署（推荐）

```mermaid
flowchart TB
  subgraph serverA [ServerA_Frontend]
    Browser[Browser]
    Nginx[Nginx]
    Static[dist_and_admin]
  end

  subgraph serverB [ServerB_Backend]
    API[SpringBoot_8080]
    PG[(PostgreSQL)]
    Redis[(Redis)]
  end

  subgraph serverC [ServerC_Storage]
    MinIO[MinIO_9000]
  end

  subgraph serverD [ServerD_Compute]
    Worker[GPU_Worker]
  end

  subgraph prodCloud [Production_Cloud]
    AliOSS[Aliyun_OSS]
    CCI[Aliyun_CCI]
  end

  Browser --> Nginx
  Nginx --> Static
  Nginx -->|"/api proxy"| API
  Browser -->|"presigned PUT/GET"| MinIO
  Browser -.->|"生产"| AliOSS
  API --> PG
  API --> Redis
  API -->|"S3 SDK"| MinIO
  API -.->|"生产"| AliOSS
  Worker -->|"register/jobs"| API
  Worker -->|"presigned URLs"| MinIO
  Worker -.->|"生产"| AliOSS
  CCI -.-> Worker
```




| 服务器        | 角色          | 组件                               | 生产替代              |
| ---------- | ----------- | -------------------------------- | ----------------- |
| **A — 前端** | 静态站点 + 反向代理 | Nginx、`dist/`、`admin/dist/`      | 可叠加 CDN           |
| **B — 后端** | 业务 API      | Spring Boot JAR、PostgreSQL、Redis | RDS、云 Redis（可选）   |
| **C — 存储** | 对象存储        | **MinIO**（S3 兼容）                 | **阿里云 OSS**       |
| **D — 算力** | 训练 Worker   | **Docker GPU 容器**                | **阿里云 CCI**（按需启动） |


> **说明：** PLY/SPZ 模型文件仍存后端服务器 B 本地磁盘（`xjicloud.storage.root`）；图片数据集与训练产出走 OSS（C 或阿里云 OSS）。

### 1.2 流量与连接关系


| 来源          | 目标                 | 端口/协议            | 用途                         |
| ----------- | ------------------ | ---------------- | -------------------------- |
| 用户浏览器       | 服务器 A              | 443 HTTPS        | 访问前端页面                     |
| 用户浏览器       | 服务器 A `/api/`      | 443 → 代理到 B:8080 | REST、SSE 进度                |
| 用户浏览器       | 服务器 C / 阿里云 OSS    | 9000 或 HTTPS     | **浏览器直传**图片（presigned URL） |
| 服务器 B       | 服务器 C / 阿里云 OSS    | S3 API           | 签发 presigned URL、管理配置      |
| 服务器 B       | PostgreSQL / Redis | 5432 / 6379      | 元数据与任务队列                   |
| 服务器 D / CCI | 服务器 B              | 8080 HTTP(S)     | Worker 注册、心跳、领任务           |
| 服务器 D / CCI | OSS                | HTTPS            | 通过 presigned URL 下载图片、上传模型 |


### 1.3 预生产 vs 生产对照


| 项目           | 预生产（自建分机）                                         | 生产（阿里云）                                                    |
| ------------ | ------------------------------------------------- | ---------------------------------------------------------- |
| 对象存储         | 服务器 C：MinIO                                       | 阿里云 OSS（不再部署 MinIO）                                        |
| OSS endpoint | `http://10.0.1.30:9000`，`path-style-access: true` | `https://oss-cn-*.aliyuncs.com`，`path-style-access: false` |
| 算力           | 服务器 D：常驻 Docker Worker                            | **CCI 弹性容器实例**，有任务时启动                                      |
| 数据库          | 服务器 B 本机 PostgreSQL                               | 推荐 **RDS PostgreSQL**                                      |
| 缓存           | 服务器 B 本机 Redis                                    | 可选 **云 Redis**                                             |
| 前端           | 服务器 A + 域名 + HTTPS                                | 同左，建议 WAF/CDN                                              |


### 1.4 运维能力说明

运维能力（OSS/DB 配置、Worker 监控）统一在 **Spring Boot 后端（8080）** 与 **Vue 管理面板**，无需单独部署额外运维服务。

| 能力 | 实现方式 |
|------|----------|
| OSS / 数据库初始配置 | `sudo ./deploy/deploy-backend.sh configure` 写入 `/etc/xjicloud/application-prod.yml` |
| OSS 运行时热更新 | 管理后台 **OSS 对象存储**（写入 `system_config` 表） |
| GPU 算力 / Worker | 手动启动 Docker Worker 或阿里云 CCI，向 `/api/v1/worker/*` 注册；队列深度见管理面板 |
| SSH 终端 / 节点 Agent | 运维直连 SSH；Worker 心跳在管理面板「算力容器」查看 |
| K8s / Docker 部署 | [`deploy/k8s/scripts/`](k8s/scripts/) 与 `docker-compose`（K8s 用 secrets + configmap，不用 shell 向导） |
| ECI 自动扩缩 | 不内置；可按队列深度用阿里云 API / 运维脚本手动启停 CCI（见 [§5.6.3](#563-按需启停)） |

---

## 2. 环境要求


| 组件                       | 版本                                                | 部署位置                          |
| ------------------------ | ------------------------------------------------- | ----------------------------- |
| 操作系统                     | Ubuntu 22.04+ / CentOS 7+ / Alibaba Cloud Linux 3 | 各服务器                          |
| Java                     | 17+                                               | 服务器 B                         |
| Maven                    | 3.9+                                              | 构建机（可非生产机）                    |
| Node.js                  | ≥ 18（SuperSplat ≥ 20.19）                          | 构建机                           |
| Nginx                    | 1.18+                                             | 服务器 A                         |
| PostgreSQL               | 14+                                               | 服务器 B 或 RDS                   |
| Redis                    | 7+                                                | 服务器 B 或云 Redis                |
| Docker                   | 24+                                               | 服务器 D（Worker）；单机 Compose 演示可选 |
| MinIO                    | 最新稳定版二进制                                          | 服务器 C（Linux 原生安装）             |
| NVIDIA Container Toolkit | 最新                                                | 服务器 D（GPU 时）                  |


---

## 3. 网络与安全组规划

以下为 **最小放通** 建议（优先使用**内网/VPC 互通**，公网仅开放必要入口）。

### 3.1 服务器 A（前端）


| 方向  | 端口   | 来源          | 说明               |
| --- | ---- | ----------- | ---------------- |
| 入站  | 443  | 0.0.0.0/0   | HTTPS 用户访问       |
| 入站  | 80   | 0.0.0.0/0   | 可选，跳转 HTTPS      |
| 出站  | 8080 | 服务器 B 内网 IP | Nginx 反代 `/api/` |


### 3.2 服务器 B（后端）


| 方向  | 端口         | 来源              | 说明                       |
| --- | ---------- | --------------- | ------------------------ |
| 入站  | 8080       | 服务器 A 内网 IP     | API（勿对公网裸奔，或经 SLB + 白名单） |
| 入站  | 8080       | 服务器 D / CCI 网段  | Worker 注册与心跳             |
| 入站  | 5432       | 本机或 RDS 安全组     | PostgreSQL               |
| 入站  | 6379       | 本机              | Redis                    |
| 出站  | 9000 或 443 | 服务器 C / 阿里云 OSS | S3 协议                    |


`**xjicloud.cors.allowed-origins`：** 填服务器 A 的 **前端域名**（如 `https://cloud.example.com`），**不是**后端地址。

### 3.3 服务器 C（MinIO，预生产）


| 方向  | 端口   | 来源                                 | 说明                                 |
| --- | ---- | ---------------------------------- | ---------------------------------- |
| 入站  | 9000 | 服务器 B 内网 IP                        | 后端 S3 SDK                          |
| 入站  | 9000 | **用户 PC 所在网段**（如 192.168.203.0/24） | 浏览器 presigned 直传（**不是**服务器 A 的 IP） |
| 入站  | 9001 | 运维 IP                              | MinIO 控制台（可选，勿公开）                  |


### 3.4 服务器 D（GPU Worker，预生产）


| 方向  | 端口         | 来源          | 说明              |
| --- | ---------- | ----------- | --------------- |
| 出站  | 8080       | 服务器 B       | 连接后端            |
| 出站  | 9000 或 443 | 服务器 C / OSS | presigned 下载/上传 |


---

## 4. 构建（在构建机或各服务器上执行）

```bash
git clone https://github.com/XJI1234/XJICloud.git
cd XJICloud
```

### 4.1 用户前端 + 管理面板

```bash
npm ci
npm run build:supersplat   # 可选，需 modules/supersplat 源码
npm run build
npm run build:admin
# 产物：dist/ 、admin/dist/
# 或：npm run build:all:cloud
```

### 4.2 后端

```bash
cd backend && mvn -DskipTests package
# 产物：backend/target/xjicloud-backend-1.0.0.jar
```

### 4.3 GPU Worker 镜像

```bash
docker build -t xjicloud/gpu-worker:latest gpu-worker/
# 生产：推送到阿里云 ACR，供 CCI 拉取
# docker tag xjicloud/gpu-worker:latest registry.cn-hangzhou.aliyuncs.com/your-ns/xjicloud-gpu-worker:latest
# docker push registry.cn-hangzhou.aliyuncs.com/your-ns/xjicloud-gpu-worker:latest
```

---

## 5. 分机部署步骤

以下 IP 为示例，请替换为实际内网地址：


| 角色      | 示例 IP       | 域名                                   |
| ------- | ----------- | ------------------------------------ |
| A 前端    | —           | `https://cloud.example.com`          |
| B 后端    | `10.0.1.20` | `api-internal.example.com`（可选内网 DNS） |
| C MinIO | `10.0.1.30` | —                                    |
| D GPU   | `10.0.1.40` | —                                    |


---

### 5.1 服务器 C — MinIO（预生产专用，Linux 原生安装）

生产环境**跳过本节**，直接使用 [§5.5 阿里云 OSS](#55-生产环境--阿里云-oss)。

以下在**服务器 C** 上以 **Linux 二进制** 安装 MinIO Server 与 `mc` 客户端（不使用 Docker）。示例适用于 **Ubuntu / Debian / CentOS / Alibaba Cloud Linux**，架构为 `amd64`。

#### 5.1.1 安装 MinIO 与 mc

```bash
# 在服务器 C 上执行
sudo useradd -r minio -s /sbin/nologin 2>/dev/null || true
sudo mkdir -p /data/minio /etc/minio
sudo chown -R minio:minio /data/minio

# 下载 server 与 client（若 dl.min.io 较慢，可在能访问外网的机器下载后 scp 到本机）
curl -fsSL https://dl.min.io/server/minio/release/linux-amd64/minio -o /tmp/minio
curl -fsSL https://dl.min.io/client/mc/release/linux-amd64/mc -o /tmp/mc
sudo install -m 0755 /tmp/minio /usr/local/bin/minio
sudo install -m 0755 /tmp/mc /usr/local/bin/mc
rm -f /tmp/minio /tmp/mc

minio --version
mc --version
```

#### 5.1.2 环境变量与 systemd 服务

```bash
# 凭证与数据目录（生产请使用强密码）
sudo tee /etc/default/minio >/dev/null <<'EOF'
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=请替换为强密码
MINIO_VOLUMES="/data/minio"
MINIO_OPTS="--console-address :9001"
# 社区版仅支持全局 CORS，见 Deploy.md §5.1.5
MINIO_API_CORS_ALLOW_ORIGIN="http://192.168.203.129"
EOF
sudo chmod 0600 /etc/default/minio
sudo chown minio:minio /etc/default/minio

sudo tee /etc/systemd/system/minio.service >/dev/null <<'EOF'
[Unit]
Description=MinIO Object Storage
Documentation=https://min.io/docs/minio/linux/index.html
After=network-online.target
Wants=network-online.target

[Service]
User=minio
Group=minio
EnvironmentFile=/etc/default/minio
ExecStart=/usr/local/bin/minio server $MINIO_OPTS $MINIO_VOLUMES
Restart=always
RestartSec=5
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now minio
sudo systemctl status minio --no-pager
```

验证 API 与控制台：

```bash
curl -I http://127.0.0.1:9000/minio/health/live
# 控制台（仅运维内网访问）：http://10.0.1.30:9001
```

#### 5.1.3 防火墙（若启用 firewalld）

```bash
# CentOS / Alibaba Cloud Linux 示例
sudo firewall-cmd --permanent --add-port=9000/tcp   # S3 API
sudo firewall-cmd --permanent --add-port=9001/tcp   # 控制台（可选，限制来源 IP）
sudo firewall-cmd --reload
```

放通范围见 [§3.3](#33-服务器-cminio预生产)：9000 需允许**整个内网网段 / 用户办公网**（浏览器所在 PC），**不是**只放通服务器 A、B 的 IP——直传请求从**用户电脑**发出，不会经过前端 Nginx。

#### 5.1.4 创建 bucket

```bash
# 将 MINIO_ROOT_PASSWORD 替换为 /etc/default/minio 中的实际密码
export MINIO_ROOT_USER=minioadmin
export MINIO_ROOT_PASSWORD='请替换为强密码'

mc alias set local http://127.0.0.1:9000 "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}"
mc mb --ignore-existing local/xjicloud
mc ls local
```

#### 5.1.5 CORS（社区版 MinIO：仅全局 CORS）

**社区版 vs 企业版：** 社区版 **不支持** bucket 级 `mc cors set` / `PutBucketCors`（会返回 NotImplemented）。浏览器直传必须在 **MinIO 全局 API CORS** 中配置，环境变量 `MINIO_API_CORS_ALLOW_ORIGIN` 或 `mc admin config set ... api cors_allow_origin`。

**为什么管理端 OSS 测试正常，但前端仍报 CORS / 网络错误？**


| 检测方式           | 谁发起 TCP 连接            | 是否跨域  | 需要什么                         |
| -------------- | --------------------- | ----- | ---------------------------- |
| 管理端 **OSS 测试** | 服务器 B → MinIO         | 否     | 防火墙放通 B；无需 CORS              |
| 用户 **图片上传**    | **用户 PC 浏览器** → MinIO | **是** | 防火墙放通 **用户 PC 网段** + 全局 CORS |


常见误区：在 MinIO 防火墙里只放行了 **服务器 A（129）和 B 的 IP**。页面虽由 A 托管，但 presigned PUT 是 **用户电脑** 直连 `192.168.203.130:9000`，源 IP 是用户 PC（例如 `192.168.203.50`），不是 129。连接被丢弃时，浏览器常同时报 `CORS` 和 `net::ERR_FAILED`。

---

**步骤 1：配置全局 CORS（服务器 C）**

方式 A — 写入 `/etc/default/minio`（推荐，与 systemd 一起生效）：

```bash
# 必须是完整 Origin：协议 + 主机 + 端口（非 80 端口不可省略）
# 多个 Origin 用英文逗号分隔，不要加空格
MINIO_API_CORS_ALLOW_ORIGIN="*"

sudo systemctl restart minio
```

控制台「全局 CORS」里填 `**http://192.168.203.129**`，不要只填 IP `192.168.203.129`（与浏览器 `Origin` 头不一致则无效）。

> 未设置时 MinIO 默认 `cors_allow_origin=*`，理论上允许所有 Origin。若你已改过全局 CORS 且填错，反而会导致失败；排查时可先设为 `*` 验证。

---

**步骤 2：防火墙放通用户 PC（不是只放 A/B）**

```bash
# 示例：整个内网段（按实际网段修改）
sudo firewall-cmd --permanent --add-rich-rule='rule family="ipv4" source address="192.168.203.0/24" port protocol="tcp" port="9000" accept'
sudo firewall-cmd --reload
```

---

**步骤 3：在后端 B 配置 API CORS（与 MinIO 无关，但 `/api/` 也需要）**

`deploy/config/application-prod.yml`：

```yaml
xjicloud:
  cors:
    allowed-origins: http://192.168.203.129
```

---

**步骤 4：验证（在「用户 PC」或任意浏览器所在机器上执行，不要只在服务器 C 本机测）**

```bash
# 4a. 网络是否可达（应返回 HTTP 403/405/200，不能超时）
curl -v --connect-timeout 3 http://192.168.203.130:9000/minio/health/live

# 4b. CORS 预检（必须有 Access-Control-Allow-Origin）
curl -i -X OPTIONS "http://192.168.203.130:9000/xjicloud/" \
  -H "Origin: http://192.168.203.129" \
  -H "Access-Control-Request-Method: PUT" \
  -H "Access-Control-Request-Headers: content-type"
```

4a 失败 → **防火墙 / 路由** 问题（与用户 PC 到 MinIO 不通）。  
4a 成功但 4b 无 `Access-Control-Allow-Origin` → **MinIO 全局 CORS** 未生效（检查 env、`mc admin config get`、是否 restart）。  
4a、4b 均成功但浏览器仍失败 → 检查浏览器地址栏 Origin 是否与配置 **完全一致**（`http` vs `https`、是否带端口）。

#### 5.1.6 后端对接

在**服务器 B** 的 `application-prod.yml` 中填写 C 的内网地址（见 [§5.2](#52-服务器-b--后端)）：

```yaml
xjicloud:
  oss:
    endpoint: http://10.0.1.30:9000
    path-style-access: true
    bucket: xjicloud
    access-key: minioadmin
    secret-key: 与 /etc/default/minio 中 MINIO_ROOT_PASSWORD 对应（root user 即 access key）
```

> **说明：** MinIO 原生安装与 Docker 版 API 完全兼容；后端与 Worker 仍通过 **presigned URL** 访问，无需改动应用代码。单机开发若仍用 Compose 内置 MinIO，见 [§7](#7-单机--开发环境可选)。

---

### 5.2 服务器 B — 后端

#### 依赖：PostgreSQL + Redis

可与 JAR 同机安装；生产推荐 **RDS + 云 Redis**，在 `application-prod.yml` 中填**内网地址**。

```bash
# PostgreSQL（同机示例）
sudo -u postgres psql <<'SQL'
CREATE USER xjicloud WITH PASSWORD 'your-password';
CREATE DATABASE xjicloud OWNER xjicloud;
SQL

# Redis（同机示例）
sudo systemctl enable --now redis
```

#### 配置

推荐交互向导（自动生成 JWT、Worker secret 与完整 `application-prod.yml`）：

```bash
chmod +x deploy/deploy-backend.sh
sudo ./deploy/deploy-backend.sh configure
```

高级用户可手动复制模板：

```bash
cp deploy/config/application-prod.yml.example deploy/config/application-prod.yml
vim deploy/config/application-prod.yml
```

**分机关键项：**

```yaml
xjicloud:
  cors:
    allowed-origins: https://cloud.example.com    # 服务器 A 的前端域名
  oss:
    endpoint: http://10.0.1.30:9000               # 服务器 C MinIO 内网地址
    path-style-access: true
    bucket: xjicloud
    access-key: minioadmin
    secret-key: 强密码
  worker:
    shared-secret: 与 Worker/CCI 环境变量一致的密钥
```

#### 一键部署

```bash
chmod +x deploy/deploy-backend.sh
sudo ./deploy/deploy-backend.sh
```

验证（在服务器 B 或 A 上）：

```bash
curl http://10.0.1.20:8080/actuator/health
```

详见 `[deploy/config/README.md](config/README.md)`、`[deploy/deploy-backend.sh](deploy/deploy-backend.sh)`。

---

### 5.3 服务器 A — 前端

仅部署静态资源；`**/api/` 反向代理到服务器 B**（用户浏览器仍访问同源 `/api`，无需改前端 `VITE_API_BASE_URL`）。

```bash
sudo mkdir -p /var/www/xjicloud
sudo cp -r dist /var/www/xjicloud/
sudo cp -r admin/dist /var/www/xjicloud/admin/

# 使用「分机版」Nginx 配置
sudo cp deploy/nginx-frontend.conf.example /etc/nginx/conf.d/xjicloud.conf
sudo vim /etc/nginx/conf.d/xjicloud.conf
# 修改：server_name、proxy_pass http://10.0.1.20:8080

sudo nginx -t && sudo systemctl reload nginx
```

HTTPS：

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d cloud.example.com
```

**访问地址：**

- 用户前端：`https://cloud.example.com/`
- 管理面板：`https://cloud.example.com/admin/`

---

### 5.4 服务器 D — GPU Worker（预生产）

```bash
# 在服务器 D 上，已安装 Docker + NVIDIA Container Toolkit（可选）
docker run -d --name xjicloud-worker --restart unless-stopped \
  --gpus all \
  -e XJICLOUD_BACKEND_URL=http://10.0.1.20:8080 \
  -e WORKER_SECRET='与后端 xjicloud.worker.shared-secret 一致' \
  -e WORKER_NAME=gpu-worker-1 \
  xjicloud/gpu-worker:latest
```

Worker 通过后端下发的 **presigned URL** 访问 OSS，通常**无需**在 Worker 上单独配置 OSS 凭证（除非改为直连 SDK）。

启动流程：等待 B `/actuator/health` → 注册 → 心跳 → 长轮询领任务 → mock 训练 → 回传模型。

---

### 5.5 生产环境 — 阿里云 OSS

生产**不再部署服务器 C（MinIO）**，在后端配置（或管理面板 **OSS 设置**）中改为：

```yaml
xjicloud:
  oss:
    endpoint: https://oss-cn-hangzhou.aliyuncs.com   # 与 Bucket 地域一致
    region: cn-hangzhou
    bucket: your-production-bucket
    access-key: RAM 子账号 AccessKey
    secret-key: RAM 子账号 SecretKey
    path-style-access: false
    presign-expiration-minutes: 120
```

**控制台配置：**

1. 创建 Bucket（建议私有读写）
2. **跨域 CORS**：来源 `https://cloud.example.com`，方法 `GET/PUT/HEAD`，Headers `*`
3. RAM 用户授予该 Bucket 的读写权限（`AliyunOSSFullAccess` 或自定义最小权限）
4. 服务器 B 安全组允许 **出站 443** 访问 `*.aliyuncs.com`

**验证：** 管理面板 → OSS 配置 → **测试连接**。

---

### 5.6 生产环境 — 阿里云 CCI 弹性容器实例

预生产服务器 D 的 Docker Worker，在生产替换为 **CCI**：有训练任务时启动实例，空闲时可停止以节省成本。

#### 5.6.1 准备镜像（ACR）

```bash
# 构建机
docker build -t xjicloud/gpu-worker:latest gpu-worker/
docker tag xjicloud/gpu-worker:latest \
  registry.cn-hangzhou.aliyuncs.com/YOUR_NAMESPACE/xjicloud-gpu-worker:latest
docker push registry.cn-hangzhou.aliyuncs.com/YOUR_NAMESPACE/xjicloud-gpu-worker:latest
```

#### 5.6.2 创建 CCI 实例（控制台要点）


| 配置项  | 建议值                                |
| ---- | ---------------------------------- |
| 镜像   | ACR 中 `xjicloud-gpu-worker:latest` |
| 规格   | 按 GPU 需求选择（如 GPU 计算型）              |
| 网络   | 与**服务器 B 同一 VPC**，分配私网 IP          |
| 重启策略 | 按需；任务结束后可手动/脚本停止实例                 |


**环境变量（必填）：**


| 变量                     | 示例                      | 说明             |
| ---------------------- | ----------------------- | -------------- |
| `XJICLOUD_BACKEND_URL` | `http://10.0.1.20:8080` | 后端**内网**地址（推荐） |
| `WORKER_SECRET`        | 与后端一致                   | Worker 注册密钥    |
| `WORKER_NAME`          | `cci-worker-01`         | 管理面板中显示的名称     |


> Worker 使用后端 API 返回的 presigned URL 读写 OSS，**生产一般无需**在 CCI 中配置 OSS AccessKey。

#### 5.6.3 按需启停


| 场景                | 操作                                                                      |
| ----------------- | ----------------------------------------------------------------------- |
| 有训练任务、队列积压        | 启动 1~N 个 CCI 实例                                                         |
| 队列清空、无 RUNNING 任务 | 停止 CCI 实例                                                               |
| 自动扩缩（进阶）          | 可通过阿里云函数计算 / 运维脚本监听 Admin 面板队列深度或 Redis `LLEN xjicloud:jobs` 触发 CCI API |


**安全组：** CCI 所在安全组需 **出站** 访问 B:8080；B 的安全组需 **入站** 允许 CCI 网段访问 8080。

#### 5.6.4 使用 CLI 启动示例（可选）

```bash
# 需安装 aliyun CLI 并配置凭证；参数以当前 CCI API 文档为准
# aliyun cci CreateContainerGroup --ContainerGroupName xjicloud-worker-1 ...
```

具体 API 字段随阿里云产品更新，请以 [容器计算服务 CCI 文档](https://help.aliyun.com/product/97658.html) 为准。

---

## 6. 配置检查清单（分机 + 生产）


| 检查项           | 位置                           | 预期                                           |
| ------------- | ---------------------------- | -------------------------------------------- |
| 前端 `/api/` 代理 | 服务器 A Nginx                  | `proxy_pass` 指向 B:8080                       |
| CORS          | 服务器 B `application-prod.yml` | 含 `https://cloud.example.com`                |
| OSS CORS      | MinIO / 阿里云 Bucket           | 允许前端域名 PUT/GET                               |
| OSS endpoint  | 服务器 B                        | 预生产：`http://C:9000`；生产：阿里云 OSS HTTPS         |
| Worker 密钥     | B 与 D/CCI                    | `worker.shared-secret` = `WORKER_SECRET`     |
| Worker 可达后端   | D/CCI → B                    | `curl http://10.0.1.20:8080/actuator/health` |
| SSE           | 服务器 A Nginx                  | `proxy_buffering off`                        |
| 浏览器直传         | 用户 → OSS                     | 开发者工具 Network 中 PUT 指向 C 或 `*.aliyuncs.com`  |


---

## 7. 单机 / 开发环境（可选）

本地或单机演示仍可使用 Docker Compose（Redis + MinIO + Backend + Worker 同机）：

```bash
cd deploy && docker compose up -d --build
npm run dev          # 用户前端 :5174
cd admin && npm run dev   # 管理面板 :5175
```

单机 Nginx（前后端同机）使用 `[deploy/nginx.conf.example](nginx.conf.example)`（`/api/` → `127.0.0.1:8080`）。

---

## 8. 管理控制面板

- 地址：`https://cloud.example.com/admin/`（部署在服务器 A）
- 登录接口：`POST /api/v1/admin/auth/login`（与用户前台 `/api/v1/auth/login` **不是同一套账号**）
- 账号存储在数据库表 `**admin_users`**，与 `users` 表（普通用户）分离

### 8.1 管理员账号与 yml 的关系

`application-prod.yml` 中的配置：

```yaml
xjicloud:
  admin:
    default-username: admin
    default-password: 你的密码
    sync-password-on-startup: false
```


| 行为                                   | 说明                                                    |
| ------------------------------------ | ----------------------------------------------------- |
| **首次启动**                             | 若 `admin_users` 中不存在 `default-username`，用 yml 密码创建管理员 |
| **再次修改 yml 密码**                      | 默认**不会**更新数据库；仍使用首次创建时的 BCrypt 哈希 → 登录 401            |
| `**sync-password-on-startup: true`** | 每次启动将 yml 中的 `default-password` 同步到**同名**管理员，改密后需重启后端 |


**改密后仍 401 的处理（任选其一）：**

```bash
# 方式 A：开启同步并重启（推荐）
# 在 application-prod.yml 设 sync-password-on-startup: true，然后：
sudo systemctl restart xjicloud-backend
# 登录成功后建议改回 false

# 方式 B：删除旧记录后重启（会按 yml 重新创建）
# PostgreSQL:
psql -U xjicloud -d xjicloud -c "DELETE FROM admin_users WHERE username = 'admin';"
sudo systemctl restart xjicloud-backend

# 方式 C：若未改过默认账号，直接试 admin / admin123
```

> **注意：** `spring.datasource.username/password` 是**数据库连接**凭证，不是管理面板登录账号。

- 可在面板中修改 OSS 配置（生产切到阿里云 OSS 时尤其方便），支持连接测试、Worker 与任务监控；首次参数来自安装向导，此后可在本页热更新

---

## 9. 验收清单

- [ ] 用户通过 **服务器 A 域名** 注册/登录
- [ ] 图片文件夹上传：浏览器直传 **MinIO/OSS** 成功（Network 可见 PUT）
- [ ] 训练任务入队；**服务器 D 或 CCI** 上 Worker 在管理面板显示 ONLINE
- [ ] SSE 训练进度正常刷新
- [ ] 完成后可下载 `model.ply`
- [ ] PLY/SPZ 模型上传与 Spark 查看器正常（文件在 **服务器 B** 本地盘）
- [ ] 生产：OSS 为阿里云、CCI 可按需启动并完成一次完整训练
- [ ] 管理后台 **OSS 对象存储** 可保存配置并测试连接成功
- [ ] `sudo ./deploy/deploy-backend.sh configure` 可交互生成完整 `application-prod.yml`

---

## 10. 故障排查


| 现象                    | 可能原因                                                    | 处理                                                         |
| --------------------- | ------------------------------------------------------- | ---------------------------------------------------------- |
| 前端 502 / API 失败       | A 无法访问 B:8080                                           | 检查 Nginx `proxy_pass`、B 安全组                                |
| CORS 错误               | B 未配置 A 的域名                                             | 修改 `xjicloud.cors.allowed-origins`                         |
| OSS PUT 失败 / 前端「网络错误」 | MinIO **全局 CORS** 填错；或防火墙只放了 A/B 服务器 IP、未放 **用户 PC 网段** | 见 [§5.1.5](#515-cors社区版-minio仅全局-cors)；在用户 PC 上 curl 4a/4b |
| 任务一直 QUEUED           | Worker/CCI 未运行                                          | 启动 D 或 CCI；检查 `WORKER_SECRET`                              |
| Worker OFFLINE        | 心跳超时                                                    | B 防火墙；`XJICLOUD_BACKEND_URL` 是否用内网地址                       |
| CCI 无法注册              | VPC 不通或 URL 错误                                          | CCI 与 B 同 VPC；健康检查 `/actuator/health`                      |
| SSE 无进度               | Nginx 缓冲                                                | A 上 `proxy_buffering off`                                  |
| 生产 OSS 失败             | RAM 权限 / endpoint 地域                                    | 管理面板测试连接；核对 `path-style-access: false`                     |
| 管理面板 401              | yml 改密未同步到 `admin_users`                                | 见 [§8.1](#81-管理员账号与-yml-的关系)                               |


---

## 11. 相关文件


| 文件                                                                                  | 说明                        |
| ----------------------------------------------------------------------------------- | ------------------------- |
| `[deploy/nginx-frontend.conf.example](nginx-frontend.conf.example)`                 | **分机**：前端 A，API 代理到后端 B   |
| `[deploy/nginx.conf.example](nginx.conf.example)`                                   | **同机**：前后端同一 Nginx        |
| `[deploy/deploy-backend.sh](deploy-backend.sh)`                                     | 后端 B 一键构建 + systemd       |
| `[deploy/config/application-prod.yml.example](config/application-prod.yml.example)` | 含 MinIO / 阿里云 OSS 注释模板    |
| `[deploy/config/README.md](config/README.md)`                                       | 后端配置说明                    |
| `[deploy/docker-compose.yml](docker-compose.yml)`                                   | 单机/开发 Compose             |
| `[deploy/env.example](env.example)`                                                 | Compose 环境变量              |
| `[gpu-worker/Dockerfile](../gpu-worker/Dockerfile)`                                 | Worker 镜像（预生产 D / 生产 CCI） |
| `[AGENT_CONTEXT.md](../AGENT_CONTEXT.md)`                                           | Agent 项目上下文               |
| `[deploy/k8s/README.md](k8s/README.md)`                                           | **K3s/K8s** 本地三节点与 ACK 上云 |

---

## 12. Kubernetes 部署（K3s / ACK）

本地 3 VM（192.168.203.133/134/135）可用 K3s 验证完整流水线，清单与脚本见 **[deploy/k8s/README.md](k8s/README.md)**。

```bash
# 133 本机：安装 K3s（推荐 --docker）、构建镜像、部署
sudo ./deploy/k8s/scripts/install-k3s-server.sh
sudo ./deploy/k8s/scripts/build-and-import-images.sh
cp deploy/k8s/secrets.example.yaml deploy/k8s/secrets.yaml
sudo ./deploy/k8s/scripts/deploy-local.sh
sudo ./deploy/k8s/scripts/validate-local.sh
```

134/135 加入 Agent 后执行 `label-nodes.sh`；生产 ACK 使用 `deploy/k8s/overlays/aliyun/`，GPU 算力见 `overlays/aliyun/CCI.md`。


