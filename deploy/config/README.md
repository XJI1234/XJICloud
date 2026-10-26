# 后端一键部署 — 配置文件目录

## 推荐：交互式安装

在仓库根目录执行（会引导填写数据库、Redis、OSS、JWT、Admin、阿里云PNVS短信认证等，并自动生成密钥）：

```bash
chmod +x deploy/deploy-backend.sh
sudo ./deploy/deploy-backend.sh
```

子命令：

| 命令 | 说明 |
|------|------|
| `configure` | 仅生成 `application-prod.yml` |
| `install` | 构建 + 安装 systemd（需已有 yml） |
| `upgrade` | 保留 `/etc/xjicloud` 配置，仅换 JAR |
| （无参数） | `configure` 后 `install` |

选项：`--skip-build`、`--no-systemd`、`--non-interactive`、`--update-config`（upgrade时备份后整体应用本地YAML）。默认构建运行Maven测试。

## 高级：手动配置

```bash
cd deploy/config
cp application-prod.yml.example application-prod.yml
# 编辑 application-prod.yml
sudo ../deploy-backend.sh install --non-interactive
```

**勿将含密钥的 `application-prod.yml` 提交到 Git。**

**PostgreSQL 首次部署**（15+ 默认限制 public schema 建表）：

```bash
sudo -u postgres psql <<'SQL'
CREATE USER xjicloud WITH PASSWORD '你的密码';
CREATE DATABASE xjicloud OWNER xjicloud;
\c xjicloud
GRANT ALL ON SCHEMA public TO xjicloud;
GRANT CREATE ON SCHEMA public TO xjicloud;
SQL
```

## 文件说明

| 文件 | 必需 | 说明 |
|------|------|------|
| `application-prod.yml` | **是** | 由向导生成或手动编辑，安装到 `/etc/xjicloud/` |
| `install-summary.txt` | 否 | 向导生成的非敏感摘要 |
| `deploy.conf` | 否 | 自定义安装路径、服务名 |

## OSS 运行时配置

安装向导写入的 OSS 参数为**初始值**。部署后可在 **管理后台 → OSS 对象存储** 修改并测试连接（热更新，无需重启）。

## 短信认证

所有短信业务配置位于现有生产YAML的 `xjicloud.sms`，不创建独立backend.env。仅支持阿里云号码认证PNVS，需赠送签名/模板及RAM的SendSmsVerifyCode、CheckSmsVerifyCode权限；验证码由阿里云生成/核验，无本地摘要密钥。向导隐藏输入凭据，校验方案/变量/日限额，生成文件权限600，安装为640。默认关闭短信。

已有服务器若有backend.env，先把业务配置合并到YAML并将旧文件改名备份，再升级；升级默认保留服务器YAML且无需本地配置。JVM参数迁入systemd drop-in。--update-config整体替换前会备份旧YAML，勿以默认值覆盖原生产密钥。详见 [短信部署指南](../../docs/PHONE_REGISTRATION_DEPLOYMENT.md)。

Linux部署回归：`bash -n deploy/deploy-backend.sh`；安装PyYAML后执行 `sudo python3 -B -m unittest discover -s deploy/tests -v`（安装权限测试需root；非root会跳过3项）。测试只使用临时目录和--no-systemd，不操作真实服务。
