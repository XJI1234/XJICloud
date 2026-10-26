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

Spring Boot 3.3.5、Java 17+，沿用 Spring Security、JPA、BCrypt 和用户 JWT。业务入口为 `AuthController` / `AuthService`，短信由 `RegistrationSmsService` 编排，`SmsSender` 仅接入阿里云号码认证 PNVS 官方 SDK。

1. `GET /api/v1/auth/captcha` 获取图形码。`POST /api/v1/auth/registration-sms` 提交 `{phone,captchaKey,captchaCode}`，消费图形码后限流并发送短信，只返回 challenge ID、300秒有效期及60秒重发间隔。
2. `SendSmsVerifyCode` 传入 `{"code":"##code##","min":"5"}`、`CodeType=1`、`CodeLength=6`、`ValidTime=300`、`Interval=60`、`DuplicatePolicy=1`；阿里云生成验证码并发送。`ReturnVerifyCode=false`，不回传验证码，`AutoRetry=0` 禁止运营商换签名重试，SDK也不自动重试。
3. 注册提交通过 `CheckSmsVerifyCode` 在线校验；发送和校验使用同一方案（默认 `xjicloud-register`）、国家码86和对应 OutId。OutId仅用于关联请求，不依赖它做安全绑定。只有 `Code=OK`、`Success=true` 且 `Model.VerifyResult=PASS` 才可通过。Redis只存手机号SHA-256、尝试次数和核验锁，不生成/保存/比对验证码或验证码摘要。Lua负责手机号与challenge绑定、5次尝试、核验互斥、重发失效及成功后单次消费；过期或重发期间返回的旧PASS也会被拒绝。
4. `POST /api/v1/auth/register` 提交 `{phone,password,smsChallengeId,smsCode}`。后端验证短信、检查手机号及历史用户名冲突、BCrypt存储密码，返回7天用户 JWT。手机号唯一索引防止并发重复建号。密码8至64字符，UTF-8不超过72字节。
5. 注册成功自动进入 `/app/account`。`GET/PUT /api/v1/account` 仅允许用户修改本人昵称、邮箱和单位；前端守卫与后端 Filter 都要求新用户补全资料。旧账号不强制补资料，仍用原用户名和密码登录；新账号使用手机号和密码。
6. 发送成功收起图形码；主动重发或更换手机号才恢复图形验证。注册提交不再次要求图形码。邮箱只是资料字段，尚未验证所有权。

本功能改变了注册契约；使用旧用户名注册请求的其他客户端会收到400。升级前应协调这些客户端，保留的旧账号登录接口不变。

## 生产依赖与配置

完整基础设施、网络、PostgreSQL、OSS和systemd首次安装见 [Deploy.md §5.2](./Deploy.md#52-服务器-b--后端)。本节补充手机号注册的上线步骤，以Linux已有部署升级为例。

- JDK 17或21、Maven、PostgreSQL、单节点Redis；多个后端实例共享Redis与相同短信方案。Lua多键操作不支持Redis Cluster跨槽。
- 保留现有数据库、JWT、Worker、OSS和本地模型目录配置。启动应用需要合法OSS配置；只测试注册时不要求OSS服务在线。PLY/SPZ仍存本地磁盘。
- 仅使用“号码认证服务 → 短信认证”，开通服务并使用控制台赠送签名和匹配赠送模板。普通短信服务的签名/模板/资源包不通用。RAM最小策略：

  ```json
  {"Version":"1","Statement":[{"Effect":"Allow","Action":["dypns:SendSmsVerifyCode","dypns:CheckSmsVerifyCode"],"Resource":"*"}]}
  ```

- 只读系统策略 `AliyunDypnsReadOnlyAccess` 不覆盖上述调用。使用主账号/RAM管理员在 **权限管理 → 权限策略 → 创建权限策略 → 脚本编辑** 粘贴上述JSON并创建，再到 **身份管理 → 用户 → 对应用户 → 添加权限 → 自定义策略** 选中绑定；没有创建按钮时需由管理员操作。[官方自定义策略步骤](https://help.aliyun.com/zh/ram/user-guide/create-a-custom-policy)。
- 后端需能通过HTTPS连接 `dypnsapi.aliyuncs.com`。`Forbidden.NoPermission` 表示调用被拒绝，应检查对应RAM用户权限及账号服务状态；`FUNCTION_NOT_OPENED` 表示未开通功能。

短信配置统一放入现有 [application-prod.yml.example](../deploy/config/application-prod.yml.example) 对应的 `xjicloud.sms`，生产实际文件为 `/etc/xjicloud/application-prod.yml`。不再使用额外 `backend.env`，不再需要 `SMS_CODE_SECRET` 或选择短信供应商：

```yaml
xjicloud:
  sms:
    enabled: false
    scheme-name: xjicloud-register
    access-key-id: ""
    access-key-secret: ""
    sign-name: ""        # 控制台赠送签名
    template-code: ""    # 匹配的赠送模板CODE
    code-variable: code
    minutes-variable: min
    daily-limit: 1000
```

合并进现有 `xjicloud` 节点，勿重复创建根键。凭据、签名和模板填齐后设置 `enabled: true`，重启后端。启用但配置缺失会启动失败；关闭时新注册/发短信返回503，旧账号登录继续。短信方案1至20字符，专用于注册，勿与其他业务/测试共用；所有后端副本须一致。模板变量以控制台模板正文为准（如 `${code}`、`${min}`）；分钟数固定为5。密钥留在服务器配置中，勿提交到Git或发到聊天。向导生成文件权限600，安装后的配置root持有、服务组只读（640）。示例默认关闭短信，不能直接启用空配置。

一键向导逐项显示原配置、遮蔽凭据并提示新增项，校验后原子保存；支持短信开关、RAM凭据、签名/模板、方案/变量、全局限额。回车沿用不会轮换原密钥。详见[配置升级与校验指南](./BACKEND_DEPLOYMENT_CONFIG.md)。首次安装：

```bash
sudo bash deploy/deploy-backend.sh configure
sudo bash deploy/deploy-backend.sh install --non-interactive
```

已有服务器建议直接编辑 `/etc/xjicloud/application-prod.yml`，保留原JWT/数据库/Worker等设置。若原服务使用 `backend.env`，必须先把其中配置迁入YAML，并将旧文件改名备份；脚本检测到旧文件会在替换JAR前停止。旧文件中的 `JAVA_TOOL_OPTIONS` 等JVM参数应迁入单独的systemd drop-in，不属于业务YAML。检查自定义systemd drop-in和进程环境，移除遗留短信/数据库覆盖项。开发和Compose仍支持环境变量覆盖；`SMS_PROVIDER=aliyun-sms` 会明确启动失败，须移除。

交互升级默认逐项审阅服务器YAML；已准备好的无人值守升级可保留原文件，不要求仓库本地YAML：

```bash
sudo bash deploy/deploy-backend.sh upgrade --skip-build --non-interactive
```

需要无人值守应用仓库本地完整配置时才使用 `upgrade --update-config --skip-build --non-interactive`；该选项备份旧YAML后整体替换。先将服务器现有配置安全复制到本地配置位置再编辑，勿用向导默认值重建并覆盖生产密钥。安装/升级会重写systemd单元，统一只加载生产YAML。不要在profile文件内写 `spring.profiles.active`，启动命令已指定prod。

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

3. 完成上方YAML配置和旧环境文件迁移。已有systemd部署执行以下命令，安装已验证JAR并更新服务单元：

   ```bash
   sudo bash deploy/deploy-backend.sh upgrade --skip-build --non-interactive
   sudo systemctl is-active xjicloud-backend
   curl -fsS http://127.0.0.1:8080/actuator/health
   sudo journalctl -u xjicloud-backend -n 80 --no-pager
   ```

   健康检查需返回 `UP`。脚本默认Maven构建会执行测试，但注册集成测试仍要求独立Redis与 `SMS_TEST_REDIS_PORT`。先完成步骤1，再用 `--skip-build` 安装已验证产物。旧版HMAC challenge与新状态不兼容，升级时已发出的验证码需重新获取；旧验证码最多等待300秒自然过期，不必清空Redis或删除其他业务键。

4. 按实际Nginx根目录发布整个 `apps/web2/dist/`，并保留 `/api` 到后端8080的同源代理。账号页需支持SPA history fallback。前端资源部署细节沿用 [Deploy.md §5.3](./Deploy.md#53-服务器-a--前端)。

5. 用指定测试手机号验收：图形码 → 收到本网页触发的真实短信 → 错码拒绝 → 正确注册 → 未完善资料不能访问业务 → 完善资料 → 退出 → 手机号密码登录；补验旧用户名登录、重复手机号及重发后旧码失效。检查阿里云受理、送达和用量。SDK受理成功不代表运营商已送达。

## 限流、回滚与未覆盖项

当前限制为同手机号60秒一次、24小时10次、socket来源IP每小时30次、全局24小时默认1000次，失败发送也计数。图形码还有来源IP每分钟20次限制。Nginx后的所有用户可能共享代理IP额度，正式站点需在可信边缘按客户端限流并配置预算告警；仅加Nginx限流不会改变后端共享IP的30次上限。若规模超过该限制，应另行实现可信代理IP解析，不能直接信任任意 `X-Forwarded-For`。

回滚：关闭短信注册、回退配套前后端产物并重启；保留新增列和用户数据。源版本的旧注册接口可能重新开放，维护窗口应由网关继续禁止 `POST /api/v1/auth/register`，直至决定是否恢复旧注册方式。

核验网络超时或进程在核验中退出时会拒绝注册；核验锁最多保留challenge剩余有效期，可等待60秒后重新获取短信。阿里云可能已经消费验证码，因此不要自动重试或在异常时本地放行。

当前本机验证不等于生产发布。生产PostgreSQL迁移、正式域名代理、真实阿里云凭据/签名/模板与运营商送达需在部署环境验收。源分支文档中的 `/home/li`、`start-*.sh` 和历史测试数量属于原开发环境，本次验证记录见 [PHONE_REGISTRATION_REVIEW.md](./PHONE_REGISTRATION_REVIEW.md)。

## 官方接口依据

- [SendSmsVerifyCode](https://help.aliyun.com/zh/pnvs/developer-reference/api-dypnsapi-2017-05-25-sendsmsverifycode)：动态验证码占位符、CodeType、长度、有效期、重复策略、返回与重试参数。
- [CheckSmsVerifyCode](https://help.aliyun.com/zh/pnvs/developer-reference/api-dypnsapi-2017-05-25-checksmsverifycode)：方案一致性及以 Model.VerifyResult=PASS 判断核验成功。

- [PNVS接口返回码](https://help.aliyun.com/zh/pnvs/developer-reference/api-return-code)：isv.ValidateFail表示错码/过期/覆盖；biz.FREQUENCY和isv.BUSINESS_LIMIT_CONTROL表示限流。校验失败返回400，频控返回429，服务或权限故障返回503，原始供应商消息不会透传。

## 本次验证（2026-10-26）

Maven package 41项测试全部通过（包含12项独立Redis集成测试，无跳过），Bash语法及6项部署脚本测试通过。用户授权的本机CSV凭据已完成真实PNVS发送、用户收码后PASS核验、错误/失效码拒绝。密钥和短信码未写入仓库。未执行生产发布或本轮浏览器E2E，详细证据和限制见[Code Review记录](./PHONE_REGISTRATION_REVIEW.md)。
