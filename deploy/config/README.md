# 后端一键部署 — 配置文件目录

## 推荐：交互式安装

需要Python3.8+和PyYAML；详见[配置升级与校验指南](../../docs/BACKEND_DEPLOYMENT_CONFIG.md)。在仓库根目录执行，首次逐项填写数据库、Redis、OSS、JWT、Admin、PNVS短信等；已安装则逐项审阅服务器现有配置：

```bash
chmod +x deploy/deploy-backend.sh
sudo ./deploy/deploy-backend.sh
```

子命令：

| 命令 | 说明 |
|------|------|
| `configure` | 仅生成 `application-prod.yml` |
| `install` | 构建 + 安装 systemd（需已有 yml） |
| `upgrade` | 逐项显示服务器原值，回车沿用，提示新增项，校验/备份后更新 |
| `validate` | 只校验YAML和配置约束 |
| （无参数） | 已安装则upgrade，否则configure后install |

选项：`--skip-build`、`--no-systemd`、`--non-interactive`、`--update-config`（非交互upgrade时备份后整体应用本地YAML）、`--check-connections`（可选网络/认证检查，不发短信）。默认构建运行Maven测试。回车沿用、输入修改、!清空可选字符串；密钥遮蔽，错误输入重新提示，取消不写文件。

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
| `deploy.conf` | 否 | 自定义安装路径、服务名 |

## OSS 运行时配置

安装向导写入的 OSS 参数为**初始值**。部署后可在 **管理后台 → OSS 对象存储** 修改并测试连接（热更新，无需重启）。

## 短信认证

所有短信业务配置位于现有生产YAML的 `xjicloud.sms`，不创建独立backend.env。仅支持阿里云号码认证PNVS，需赠送签名/模板及RAM的SendSmsVerifyCode、CheckSmsVerifyCode权限；验证码由阿里云生成/核验，无本地摘要密钥。向导隐藏输入凭据，校验方案/变量/日限额，生成文件权限600，安装为640。默认关闭短信。

已有服务器若有backend.env，先把业务配置合并到YAML并将旧文件改名备份，再升级；交互升级逐项读取服务器YAML，全部沿用时保留原文件；无需本地配置。JVM参数迁入systemd drop-in。--update-config整体替换前会备份旧YAML，勿以默认值覆盖原生产密钥。详见 [短信部署指南](../../docs/PHONE_REGISTRATION_DEPLOYMENT.md)。

Linux部署回归：`bash -n deploy/deploy-backend.sh`；安装PyYAML后执行 `sudo python3 -B -m unittest discover -s deploy/tests -v`（安装权限测试需root；非root会跳过安装权限用例）。测试只使用临时目录和--no-systemd，不操作真实服务。
