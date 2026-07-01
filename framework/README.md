# XJICloud Framework

基础设施运维框架：主从模式、配置中心、三种部署、ECI 管理、远程终端。

## 快速安装

`target/` 与 `*.jar` 已加入 `.gitignore`，**不会**随 git 同步。安装脚本会在本机执行 `mvn package` 后再部署到 `/opt/xjicloud-framework/`。

```bash
sudo chmod +x framework/install.sh

# 全新安装（可先于后端部署，Framework 独立可用）
sudo ./framework/install.sh install

# 保留配置，本地重新编译并升级 JAR
sudo ./framework/install.sh upgrade

# 交互选择 install / upgrade
sudo ./framework/install.sh
```

需已安装 Java 17+ 与 Maven 3.9+（安装脚本会检查）。

访问 Master 节点 `http://<ip>:9090`，默认 `admin` / `admin`（首次登录强制改密）。

### 独立于后端运行

Framework **无需后端** 即可使用：

- 配置中心（DB/Redis/OSS 等运行参数）
- 节点注册与监控
- SSH 远程终端
- 三种部署（Basic / Docker / K8s）
- ECI 手动创建

后端部署后，在配置中心「业务/后端」Tab 填写 `Backend Public URL`；ECI 自动扩缩与队列联动需后端在线。

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
