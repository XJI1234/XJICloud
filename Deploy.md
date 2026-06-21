# XJICloud 云平台部署指南

本文档说明如何在 **Linux（Ubuntu / CentOS / Alibaba Cloud Linux）** 上构建并部署 XJICloud 全栈：用户前端、管理面板、Spring Boot 后端、Redis 任务队列、S3 兼容 OSS、GPU 算力容器。

## 1. 架构概览

```
用户浏览器 ──► Nginx
                 ├── /              → Vue 用户前端 (dist/)
                 ├── /admin/        → Vue 管理面板 (admin/dist/)
                 ├── /supersplat/   → SuperSplat 子应用
                 └── /api/          → Spring Boot :8080
                                        ├── PostgreSQL / H2（元数据）
                                        ├── Redis（任务队列）
                                        └── OSS（图片数据集 + 训练产出）

GPU Worker 容器 ──► 注册/心跳/领任务 ──► 后端 /api/v1/worker/*
                 └── 从 OSS 下载图片 → mock 训练 → 上传 model.ply 至 OSS
```

## 2. 环境要求

| 组件 | 版本要求 |
|------|----------|
| 操作系统 | Ubuntu 22.04+ / CentOS 7+ / Alibaba Cloud Linux 3 |
| Java | 17+ |
| Maven | 3.9+ |
| Node.js | ≥ 18（用户前端）；SuperSplat 需 ≥ 20.19 |
| Docker | 24+（推荐 Compose 部署） |
| NVIDIA Container Toolkit | GPU Worker 需要（可选，无 GPU 时以 CPU mock 运行） |
| PostgreSQL | 14+（生产推荐） |
| Redis | 7+ |
| OSS | MinIO（自托管）或阿里云 OSS / 任意 S3 兼容存储 |

## 3. 获取代码

```bash
git clone https://github.com/XJI1234/XJICloud.git
cd XJICloud
```

## 4. 构建

### 4.1 用户前端

```bash
npm ci
# 若需高级编辑，先构建 SuperSplat 子应用（需 modules/supersplat 源码）
npm run build:supersplat   # 可选
npm run build
# 产物：dist/
```

### 4.2 管理面板

```bash
cd admin
npm ci
npm run build
# 产物：admin/dist/
cd ..
```

或根目录：

```bash
npm run build:admin
npm run build:all:cloud   # 含用户前端 + 管理面板（见 package.json）
```

### 4.3 后端

```bash
cd backend
mvn -DskipTests package
# 产物：backend/target/xjicloud-backend-1.0.0.jar
```

### 4.4 GPU Worker 镜像

```bash
docker build -t xjicloud/gpu-worker:latest gpu-worker/
```

## 5. 依赖服务

### 5.1 Redis

**Ubuntu / Debian：**

```bash
sudo apt update && sudo apt install -y redis-server
sudo systemctl enable --now redis-server
```

**CentOS / Alibaba Cloud Linux：**

```bash
sudo yum install -y redis
sudo systemctl enable --now redis
```

### 5.2 MinIO（开发 / 自托管 OSS）

```bash
docker run -d --name minio \
  -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  -v /data/minio:/data \
  minio/minio server /data --console-address ":9001"
```

创建 bucket：

```bash
docker run --rm --network host minio/mc alias set local http://127.0.0.1:9000 minioadmin minioadmin
docker run --rm --network host minio/mc mb local/xjicloud
```

### 5.3 OSS CORS（浏览器直传必须）

MinIO 示例（允许前端域名 PUT）：

```bash
cat > /tmp/cors.json <<'EOF'
[
  {
    "AllowedOrigin": ["https://your-domain.example", "http://127.0.0.1:5174"],
    "AllowedMethod": ["GET", "PUT", "HEAD"],
    "AllowedHeader": ["*"],
    "ExposeHeader": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
EOF
docker run --rm -v /tmp/cors.json:/cors.json --network host minio/mc \
  anonymous set-json /cors.json local/xjicloud
```

阿里云 OSS：在控制台 → Bucket → 跨域设置，允许 `PUT`/`GET`，来源填前端域名。

### 5.4 PostgreSQL（生产）

```bash
sudo -u postgres psql <<'SQL'
CREATE USER xjicloud WITH PASSWORD 'your-password';
CREATE DATABASE xjicloud OWNER xjicloud;
SQL
```

## 6. 后端配置

复制 [`deploy/application-prod.yml.example`](application-prod.yml.example) 为参考，或通过命令行传参：

```bash
sudo mkdir -p /data/xjicloud
sudo useradd -r -s /bin/false xjicloud || true
sudo chown xjicloud:xjicloud /data/xjicloud

java -jar backend/target/xjicloud-backend-1.0.0.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/xjicloud \
  --spring.datasource.username=xjicloud \
  --spring.datasource.password=your-password \
  --spring.data.redis.host=127.0.0.1 \
  --xjicloud.jwt.secret=至少32位的随机密钥 \
  --xjicloud.worker.shared-secret=worker共享密钥 \
  --xjicloud.storage.root=/data/xjicloud \
  --xjicloud.oss.endpoint=http://127.0.0.1:9000 \
  --xjicloud.oss.bucket=xjicloud \
  --xjicloud.oss.access-key=minioadmin \
  --xjicloud.oss.secret-key=minioadmin \
  --xjicloud.oss.path-style-access=true \
  --xjicloud.cors.allowed-origins=https://your-domain.example
```

### systemd（可选）

```bash
sudo cp backend/target/xjicloud-backend-1.0.0.jar /opt/xjicloud/backend/
sudo cp deploy/systemd/xjicloud-backend.service /etc/systemd/system/
sudo cp deploy/env.example /etc/xjicloud/backend.env
# 编辑 /etc/xjicloud/backend.env
sudo systemctl daemon-reload
sudo systemctl enable --now xjicloud-backend
```

## 7. Nginx

```bash
sudo mkdir -p /var/www/xjicloud
sudo cp -r dist /var/www/xjicloud/
sudo cp -r admin/dist /var/www/xjicloud/admin/
sudo cp deploy/nginx.conf.example /etc/nginx/conf.d/xjicloud.conf
# 修改 server_name、root 路径
sudo nginx -t && sudo systemctl reload nginx
```

HTTPS 推荐使用 Certbot：

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.example
```

## 8. GPU 算力容器

### 8.1 安装 NVIDIA Container Toolkit（有 GPU 时）

参考 NVIDIA 官方文档安装 `nvidia-container-toolkit`，验证：

```bash
docker run --rm --gpus all nvidia/cuda:12.0.0-base-ubuntu22.04 nvidia-smi
```

### 8.2 启动 Worker

```bash
docker run -d --name xjicloud-worker --restart unless-stopped \
  --gpus all \
  -e XJICLOUD_BACKEND_URL=http://YOUR_SERVER_IP:8080 \
  -e WORKER_SECRET=与后端-xjicloud.worker.shared-secret-一致 \
  -e WORKER_NAME=gpu-worker-1 \
  -e OSS_ENDPOINT=http://YOUR_MINIO_OR_OSS:9000 \
  -e OSS_ACCESS_KEY=minioadmin \
  -e OSS_SECRET_KEY=minioadmin \
  xjicloud/gpu-worker:latest
```

Worker 启动后会：

1. 等待后端 `/actuator/health` 可达
2. `POST /api/v1/worker/register` 注册
3. 每 15s 心跳
4. 长轮询领取训练任务并 mock 训练

## 9. Docker Compose 一键部署（演示）

```bash
cd deploy
cp env.example .env
# 编辑 .env

docker compose up -d --build
# 后端：http://127.0.0.1:8080
# MinIO 控制台：http://127.0.0.1:9001
```

生产可叠加：

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

前端仍需单独构建并由 Nginx 提供静态文件（或使用 CDN）。

## 10. 管理控制面板

- 地址：`https://your-domain.example/admin/`
- 默认账号：`admin` / `admin123`（首次启动自动创建，**生产环境务必修改**）
- 功能：OSS 配置与连接测试、算力容器监控、训练任务重试/取消

也可在管理面板中修改 OSS 地址，无需重启后端（配置写入数据库并热加载）。

## 11. 验收清单

- [ ] 用户可注册/登录，创建项目
- [ ] **数据上传 → 图片数据集**：选择文件夹，归档上传至 OSS，显示进度
- [ ] 训练任务入队，GPU Worker 上线并在管理面板可见
- [ ] 用户前端 SSE 实时显示训练进度
- [ ] 任务完成后可下载 `model.ply`
- [ ] 管理面板可修改 OSS 配置并通过连接测试
- [ ] 原有 PLY/SPZ 模型上传与 Spark 查看器仍正常

## 12. 故障排查

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| 图片上传 OSS 失败 | Bucket CORS 未配置 | 见 §5.3 |
| 任务一直 QUEUED | Worker 未启动或密钥不匹配 | 检查 `WORKER_SECRET` 与 Worker 日志 |
| Worker 显示 OFFLINE | 心跳超时（默认 60s） | 检查网络、后端可达性 |
| SSE 无进度 | Nginx 缓冲 | 确认 `proxy_buffering off` |
| Redis 连接失败 | 地址/密码错误 | 检查 `spring.data.redis.*` |
| OSS 连接测试失败 | endpoint/bucket/密钥错误 | 管理面板 → OSS 配置 |

## 13. 开发环境快速启动

```bash
# 终端 1：Redis + MinIO
cd deploy && docker compose up redis minio minio-init -d

# 终端 2：后端
cd backend && mvn spring-boot:run

# 终端 3：GPU Worker
cd gpu-worker && docker build -t xjicloud/gpu-worker . && \
  docker run --rm -e XJICLOUD_BACKEND_URL=http://host.docker.internal:8080 \
  -e WORKER_SECRET=change-me-worker-secret-in-production xjicloud/gpu-worker

# 终端 4：用户前端
npm run dev

# 终端 5：管理面板
cd admin && npm run dev
```

## 14. 相关文件

| 文件 | 说明 |
|------|------|
| [`deploy/docker-compose.yml`](docker-compose.yml) | Redis + MinIO + Backend + GPU Worker |
| [`deploy/env.example`](env.example) | 环境变量模板 |
| [`deploy/nginx.conf.example`](nginx.conf.example) | Nginx 含 `/admin/` 与 SSE |
| [`backend/Dockerfile`](../backend/Dockerfile) | 后端镜像 |
| [`gpu-worker/Dockerfile`](../gpu-worker/Dockerfile) | 算力容器镜像 |
