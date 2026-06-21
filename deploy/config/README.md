# 后端一键部署 — 配置文件目录

部署前请将示例复制为实际配置（**勿提交含密钥的文件到 Git**）：

```bash
cd deploy/config
cp application-prod.yml.example application-prod.yml
cp backend.env.example backend.env          # 可选
cp deploy.conf.example deploy.conf          # 可选，自定义安装路径
```

编辑 `application-prod.yml`：数据库、Redis、OSS、JWT、Worker 密钥、CORS 等。

**PostgreSQL 首次部署**（15+ 默认限制 public schema 建表）：

```bash
sudo -u postgres psql -d xjicloud <<'SQL'
ALTER DATABASE xjicloud OWNER TO xjicloud;
GRANT ALL ON SCHEMA public TO xjicloud;
GRANT CREATE ON SCHEMA public TO xjicloud;
SQL
```

然后在仓库根目录执行：

```bash
chmod +x deploy/deploy-backend.sh
sudo ./deploy/deploy-backend.sh
```

## 文件说明

| 文件 | 必需 | 说明 |
|------|------|------|
| `application-prod.yml` | **是** | Spring Boot 生产配置，安装到 `/etc/xjicloud/` |
| `backend.env` | 否 | systemd `EnvironmentFile`，JVM 参数或 env 覆盖 |
| `deploy.conf` | 否 | 安装目录、服务名、JAR 名等 |

## 脚本选项

```bash
./deploy/deploy-backend.sh              # 构建 + 安装 + 启动 systemd
./deploy/deploy-backend.sh --skip-build   # 仅重新部署已有 target/*.jar
./deploy/deploy-backend.sh --no-systemd   # 不装 systemd，仅复制文件到 INSTALL_DIR
./deploy/deploy-backend.sh --help
```
