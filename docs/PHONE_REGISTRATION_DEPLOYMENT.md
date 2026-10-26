# 手机号注册：本次移植与后端部署指南

更新：2026-10-26。来源：`origin/li-chengtao` 的 `3684e015d53751863e858c66b639793ff611b180`。
本次在 `ceshi` 上只应用该提交的注册、短信验证、注册后资料完善及其测试、配置、迁移和文档，没有合并整个分支。
源分支其他任务执行、查看器、CI 和依赖锁文件变更不属于本次移植。

## 实现方法

web2 使用 Vue 3 + TypeScript，保持 Identity 限界上下文的 DDD 分层：

```text
LoginView / RegisterForm / AccountView
  → useAuthSession
  → auth.usecase（手机号、密码及资料规则）
  → AuthRepository port
  → http-auth.repository / mapper
  → Spring Boot /api/v1
```

Spring Boot 3.3.5、Java 17+，沿用 Spring Security、JPA、BCrypt 和用户 JWT。业务入口为 `AuthController` / `AuthService`，短信由 `RegistrationSmsService` 编排，`SmsSender` 隔离阿里云两种官方 SDK。

1. `GET /api/v1/auth/captcha` 获取图形码。`POST /api/v1/auth/registration-sms` 提交 `{phone,captchaKey,captchaCode}`，消费图形码后限流并发送短信，只返回 challenge ID、300秒有效期及60秒重发间隔。
2. 平台使用 `SecureRandom` 生成6位码；Redis只保存绑定用途、手机号和 challenge ID 的 HMAC-SHA256 摘要。Lua 脚本原子限流、替换旧 challenge、限制5次错误及成功后删除，防止重放和并发重复消费。
3. `POST /api/v1/auth/register` 提交 `{phone,password,smsChallengeId,smsCode}`。后端验证短信、检查手机号及历史用户名冲突、BCrypt存储密码，返回7天用户 JWT。手机号唯一索引防止并发重复建号。密码8至64字符，UTF-8不超过72字节。
4. 注册成功自动进入 `/app/account`。`GET/PUT /api/v1/account` 仅允许用户修改本人昵称、邮箱和单位；前端守卫与后端 Filter 都要求新用户补全资料。旧账号不强制补资料，仍用原用户名和密码登录；新账号使用手机号和密码。
5. 发送成功收起图形码；主动重发或更换手机号才恢复图形验证。注册提交不再次要求图形码。邮箱只是资料字段，尚未验证所有权。

本功能改变了注册契约；使用旧用户名注册请求的其他客户端会收到400。升级前应协调这些客户端，保留的旧账号登录接口不变。

## 生产依赖与配置

完整基础设施、网络、PostgreSQL、OSS和systemd首次安装见 [Deploy.md §5.2](./Deploy.md#52-服务器-b--后端)。本节补充手机号注册的上线步骤，以Linux已有部署升级为例。

- JDK 17或21、Maven、PostgreSQL、单节点Redis；多个后端实例共享Redis与 `SMS_CODE_SECRET`。Lua多键操作不支持Redis Cluster跨槽。
- 保留现有数据库、JWT、Worker、OSS和本地模型目录配置。启动应用仍需要合法OSS配置；只测试注册时不要求OSS服务在线。PLY/SPZ仍存本地磁盘。
- 个人开发者选择 `aliyun-pnvs`，使用号码认证服务的短信认证、匹配的预置签名/模板及 `dypns:SendSmsVerifyCode` 权限。企业已有短信签名/模板可选 `aliyun-sms` 和 `dysms:SendSms`。两种产品的资源包不互通。
- 后端需能通过HTTPS连接对应的 `dypnsapi.aliyuncs.com` 或 `dysmsapi.aliyuncs.com`。

将 [backend.env.example](../deploy/config/backend.env.example) 中的短信项填入服务器 `/etc/xjicloud/backend.env`，保留该文件已有配置。示例为空值，不能直接用于启用短信：

```dotenv
SMS_ENABLED=false
SMS_PROVIDER=aliyun-pnvs
ALIBABA_CLOUD_ACCESS_KEY_ID=
ALIBABA_CLOUD_ACCESS_KEY_SECRET=
SMS_SIGN_NAME=
SMS_TEMPLATE_CODE=
SMS_CODE_VARIABLE=code
SMS_MINUTES_VARIABLE=min
SMS_CODE_SECRET=
SMS_DAILY_LIMIT=1000
```

`SMS_CODE_SECRET` 用服务器本地 `openssl rand -hex 32` 生成，至少32字符，不复用JWT或阿里云密钥。所有副本保持一致。密钥文件由root维护并限制访问，例如 `sudo chmod 600 /etc/xjicloud/backend.env`。systemd会读取该文件；直接用服务账号执行 `java -jar` 不会自动加载它。

配置齐全后设置 `SMS_ENABLED=true` 并重启。启用但配置缺失会启动失败；关闭时新注册和发短信返回503，旧账号登录可继续。生产不得运行 `RegistrationBrowserFixture`；它仅在测试classpath提供短信替身。

## 上线步骤

1. 在本次移植后的代码上构建并测试。独立测试Redis仅绑定localhost、使用非6379端口；集成测试会删除短信测试键，不能连接正式Redis：

   ```bash
   pnpm test:web2
   pnpm build:web2
   cd services/backend
   SMS_TEST_REDIS_PORT=16379 mvn -B package
   cd ../..
   ```

   未设置变量会跳过10项注册集成测试，不能把这类结果当成完整验证。产物为 `services/backend/target/xjicloud-backend-1.0.0.jar` 和 `apps/web2/dist/`。

2. 在维护窗口暂停注册和后端写入，备份数据库、当前JAR与配置，然后对已有 `users` 表执行增量迁移。下面的 `DATABASE_URL` 从服务器秘密配置读取，备份目录需事先建立并限制访问：

   ```bash
   pg_dump "$DATABASE_URL" --format=custom --file=/safe-backup/xjicloud-before-phone.dump
   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f deploy/migrations/20261021_phone_registration.sql
   ```

   SQL在事务内新增可空唯一 `phone`、`email`、`organization`、`profile_completed_at`，保留旧数据；它不负责建立完整空库。首次空库安装需先按基础部署流程建立其余表。生产迁移完成后使用 `spring.jpa.hibernate.ddl-auto: validate`；现有部署模板/向导可能设置 `update`，应显式核对。

3. 填好短信环境变量。已有systemd部署可在确认目标目录后安装已经测试过的JAR：

   ```bash
   sudo install -m 0644 services/backend/target/xjicloud-backend-1.0.0.jar /opt/xjicloud/backend/xjicloud-backend-1.0.0.jar
   sudo systemctl restart xjicloud-backend
   sudo systemctl is-active xjicloud-backend
   curl -fsS http://127.0.0.1:8080/actuator/health
   sudo journalctl -u xjicloud-backend -n 80 --no-pager
   ```

   健康检查需返回 `UP`。首次安装可使用 `deploy/deploy-backend.sh`；已有 `upgrade` 子命令仍要求仓库本机的 `deploy/config/application-prod.yml` 存在，并会在 `deploy/config/backend.env` 存在时覆盖服务器环境文件。脚本内部构建会跳过测试，因此先执行步骤1，使用 `--skip-build` 安装已验证产物。不要将默认示例误覆盖到现有生产密钥文件。

4. 按实际Nginx根目录发布整个 `apps/web2/dist/`，并保留 `/api` 到后端8080的同源代理。账号页需支持SPA history fallback。前端资源部署细节沿用 [Deploy.md §5.3](./Deploy.md#53-服务器-a--前端)。

5. 用指定测试手机号验收：图形码 → 收到本网页触发的真实短信 → 错码拒绝 → 正确注册 → 未完善资料不能访问业务 → 完善资料 → 退出 → 手机号密码登录；补验旧用户名登录、重复手机号及重发后旧码失效。检查阿里云受理、送达和用量。SDK受理成功不代表运营商已送达。

## 限流、回滚与未覆盖项

当前限制为同手机号60秒一次、24小时10次、socket来源IP每小时30次、全局24小时默认1000次，失败发送也计数。图形码还有来源IP每分钟20次限制。Nginx后的所有用户可能共享代理IP额度，正式站点需在可信边缘按客户端限流并配置预算告警；仅加Nginx限流不会改变后端共享IP的30次上限。若规模超过该限制，应另行实现可信代理IP解析，不能直接信任任意 `X-Forwarded-For`。

回滚：关闭短信注册、回退配套前后端产物并重启；保留新增列和用户数据。源版本的旧注册接口可能重新开放，维护窗口应由网关继续禁止 `POST /api/v1/auth/register`，直至决定是否恢复旧注册方式。

当前本机验证不等于生产发布。生产PostgreSQL迁移、正式域名代理、真实阿里云凭据/签名/模板与运营商送达需在部署环境验收。源分支文档中的 `/home/li`、`start-*.sh` 和历史测试数量属于原开发环境，本次验证记录见 [PHONE_REGISTRATION_REVIEW.md](./PHONE_REGISTRATION_REVIEW.md)。
