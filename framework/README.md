# XJICloud Framework

基础设施运维框架：主从模式、配置中心、三种部署、ECI 管理、远程终端。

## 快速安装

```bash
sudo chmod +x framework/install.sh
sudo ./framework/install.sh
```

访问 Master 节点 `http://<ip>:9090`，默认 `admin` / `admin`（首次登录强制改密）。

## CLI

```bash
./framework/cli.sh status
./framework/cli.sh mode set slave
./framework/cli.sh master set http://10.0.1.10:9090
```

## 后端集成

在 `deploy/config/application-prod.yml` 添加：

```yaml
xjicloud:
  framework:
    enabled: true
    master-url: http://10.0.1.10:9090
    api-secret: <install.sh 输出的 api-secret>
    backend-api-secret: <install.sh 输出的 backend-api-secret>
    config-poll-interval-sec: 60
```

后端从 Framework 拉取数据库/OSS/Redis 等运行参数。

## 开发

```bash
cd framework && mvn spring-boot:run
```

## 部署模式

| 模式 | 说明 |
|------|------|
| BASIC | SSH 编译 + systemd / nginx |
| DOCKER | docker compose prod |
| K8S | kubectl apply -k deploy/k8s/ |
