# 手机号验证注册与账户资料

> 本次移植的部署步骤见 [PHONE_REGISTRATION_DEPLOYMENT.md](./PHONE_REGISTRATION_DEPLOYMENT.md)，当前分支验证与审查见 [PHONE_REGISTRATION_REVIEW.md](./PHONE_REGISTRATION_REVIEW.md)。本文后半部分的日期与测试数量保留源分支历史记录。

范围：web2 与 Spring Boot 用户注册。保留密码登录和旧账号；不增加短信免密登录、找回密码、换绑手机号或邮箱验证。

## 用户流程

1. 登录页点击「注册」，填写中国大陆11位手机号（+86，表单不输入区号）。
2. 填图形验证码，点击「发送验证码」。发送成功后收起图形验证并聚焦短信码，注册不用再填图形码；倒计时结束后主动点「重新发送」才显示新图。更换手机号清除短信状态并恢复图形验证，失败发送刷新图形码。
3. 输入6位短信验证码，设置并确认密码，点击「验证并注册」。
4. 注册成功自动登录，跳到 /app/account。昵称、邮箱、单位/学校必填；个人用户可在单位填「个人用户」。
5. 保存后进入首页。以后使用手机号与密码登录，在「我的 → 账户信息」修改资料。

未完成资料的新账号，刷新或跳业务路由仍回账户页；后端也拒绝业务 API（403）。旧账号不强制补资料，原用户名和密码仍有效。邮箱只作为资料保存，不代表邮箱所有权已验证。

## 阿里云开通与配置

当前仅集成阿里云号码认证PNVS，官方Java SDK `com.aliyun:dypnsapi20170525:2.0.0`，HTTPS连接 `dypnsapi.aliyuncs.com`。验证码由阿里云生成并校验：发送使用 `##code##` 和6位数字规则，核验调用 `CheckSmsVerifyCode`，仅明确 `PASS` 可注册。Redis只存注册challenge、手机号哈希、限流、尝试次数和核验互斥状态，不再存验证码或其摘要，也不需要摘要密钥。

RAM需同时具备 `dypns:SendSmsVerifyCode` 和 `dypns:CheckSmsVerifyCode`。使用号码认证控制台赠送签名及匹配的赠送模板，模板应含验证码和有效分钟数变量（默认code/min）。发送与校验使用同一方案，默认 `xjicloud-register`，不要与别的业务或控制台测试共用。使用平台页面先获取challenge，控制台独立发送不能代替本平台注册流程。

生产统一配置在 `deploy/config/application-prod.yml.example` 对应的 `xjicloud.sms` 中，由向导生成实际YAML；不再加载额外 `backend.env`，旧文件应先迁移。SMS_ENABLED默认false；启用但缺配置会启动失败，关闭时拒绝发送/注册，旧账号密码登录继续。Compose开发模式可继续使用环境变量，但不支持普通短信供应商，须移除旧SMS_PROVIDER设置。连接3秒、读取5秒、SDK不重试、供应商AutoRetry=0、ReturnVerifyCode=false。API/日志不输出原始SDK异常、手机号、验证码或凭据。

具体配置、迁移、向导和升级命令见 [最新部署指南](./PHONE_REGISTRATION_DEPLOYMENT.md)，控制台准备见 [短信认证接入](./SMS_PERSONAL_SETUP.md)。

## 数据库与上线顺序

生产 ddl-auto=validate 时，先备份数据库，在维护窗口执行：

~~~bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f deploy/migrations/20261021_phone_registration.sql
~~~

DATABASE_URL 来自服务器配置，不要粘贴带密码的连接串。迁移新增 phone（唯一、可空）、email、organization、profile_completed_at。旧用户不把用户名自动当作已验证手机号；如果历史用户名恰好等于目标手机号，注册拒绝，需用原账号登录并联系管理员处理，避免接管账号。

顺序：数据库备份与迁移 → 后端配置和部署 → web2 部署 → 测试手机号真实联调 → 开放注册。回滚时先关闭 SMS_ENABLED，回退前后端版本，保留新增列与数据；不要在回滚代码时删除用户资料。

## API 契约

统一响应仍为 { success, message, data }：

- GET /api/v1/auth/captcha：现有图形验证码。
- POST /api/v1/auth/registration-sms：{phone,captchaKey,captchaCode}，返回 {challengeId,expiresInSeconds:300,retryAfterSeconds:60}。不会返回短信验证码。
- POST /api/v1/auth/register：{phone,password,smsChallengeId,smsCode}，返回现有用户会话并新增 profileRequired。旧的仅用户名/密码注册已关闭。
- POST /api/v1/auth/login：仍为 {username,password,...}，新账号 username 填手机号，旧账号填原用户名。响应新增 profileRequired。
- GET /api/v1/account：仅用户 JWT，返回本人 {userId,username,phone,displayName,email,organization,profileRequired}。
- PUT /api/v1/account：仅用户 JWT，提交 {displayName,email,organization}；身份来自登录用户，不接受修改其他用户、手机号或密码。

密码8至64字符且 UTF-8 长度不超过72字节（BCrypt 限制）。昵称最多128、邮箱254、单位200字符。管理员/Worker JWT 不可访问账户接口，三种 JWT 的 type 语义保持原样。

## 短信风控与存储

- 阿里云生成6位数字并在线核验；Redis存绑定手机号哈希的随机challenge和核验状态，成功后原子删除。
- 有效5分钟；每条challenge最多5次在线核验尝试；手机号不匹配直接拒绝且不调用阿里云；新短信成功受理后废弃前一条 challenge。
- 同手机号60秒间隔、24小时最多10次；同连接来源 IP 每小时30次；全局24小时默认1000次，可用 SMS_DAILY_LIMIT 调整。窗口从第一次请求起计算；失败和已注册手机号请求也占配额。
- Lua 原子判断并计数，防并发突破；多个后端须共享同一个Redis和相同短信方案。目前按现有**单节点 Redis**部署，脚本不支持 Redis Cluster 跨槽。
- 默认只按 socket 来源限流，不信任请求自行填写的 X-Forwarded-For。反向代理后的用户会共享代理 IP 配额；正式上线要由运维在可信边缘做逐客户端限流和短信预算告警。若需应用识别真实 IP，另行明确可信代理范围，不能直接放开任意转发头。
- 网络超时可能已经受理短信，因此失败仍保留60秒冷却和配额；页面只有确认成功才显示倒计时。用户遇到频繁提示后需等待再重试。
- 验证码先在 Redis 消费再写数据库；数据库失败时需重新获取，不允许已消费验证码重放。唯一索引阻止同手机号并发创建多个账号。
- 不输出验证码、手机号、密钥或 SDK 原始异常；未配置、阿里云拒绝、Redis 不可用均失败，不伪装发送成功。

## 验证与真实发送验收

~~~bash
pnpm test:web2
pnpm build:web2
cd services/backend
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -B test
# 独立、可清空的本机测试 Redis（不要填生产端口6379）
SMS_TEST_REDIS_PORT=16379 JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -B test
~~~

PhoneRegistrationIntegrationTest 使用 H2 内存数据库、真实独立 Redis 与测试短信替身。它会清理 xjicloud:registration-sms:* 测试键，**只能连接独立测试实例**，不可连正式 Redis；默认未设变量时跳过。覆盖注册/资料/登录、旧接口旁路、角色拒绝、篡改身份、过期、错误次数、重放、重发、冷却、失败关闭与原子并发消费。

RegistrationBrowserFixture 只在测试 classpath 下提供本机 127.0.0.1:18081 的独立内存后端，捕获短信到 target/registration-browser-code.txt（权限600）。不会打包进正式 JAR，不是日常启动方式，不可公开部署。浏览器验收用它跑真实注册/资料/登录流程，不代表真实阿里云送达已经验收。

拿到正式配置后，由学长指定测试手机号，手动验收：图形码正确 → 收到真实短信 → 短信码正确注册 → 完善资料 → 退出再登录；另验错误/过期短信、重发旧码失效、重复手机号、冷却限制。检查控制台受理与送达记录、扣费及配额，不要在日志中打印验证码。本机个人短信认证真实注册已获用户反馈成功（2026-10-25）；生产 PostgreSQL 迁移、正式站点送达和预算仍待部署环境验收。

## 本次交付验证与 Code Review（2026-10-21）

- pnpm test:web2：40个测试文件、154项通过；包括 DDD 架构门禁、注册会话/守卫、短信接口契约和翻译错误态。
- pnpm build:web2：通过；已有 Cesium 大 chunk 提示保留。
- 独立 Redis 6.0.16 + H2 下执行 Maven test、package：32项全部通过、无跳过；其中10项注册集成测试、3项阿里云 SDK 适配/配置测试。
- Edge 浏览器点击输入走完：未登录跳转、错误图形码、短信发送与倒计时、错误短信码、注册、刷新与资料门槛、保存回首页、昵称菜单同步、退出与手机号密码登录。390px 手机注册页无横向溢出；短信不可用提示已验证。
- 浏览器测试只替换短信 transport；HTTP、JWT、JPA、Redis 均为实际实现。Windows → WSL 测试请求通过脚本转发到本机测试后端，不代表正式反向代理与运营商链路验收。
- 正式 JAR 检查：短信 Lua 资源存在，测试 Fixture、集成测试和验证码捕获文件均未打包。
- 自审范围：本次 identity/auth、配置、迁移和文档 diff。已修复短信不可用提示未进入实际 i18n 消息集的问题；检查了短信注册旁路、重复消费、并发限流、角色隔离、资料修改身份来源和敏感异常处理，未发现未修复的 High/Critical。
- 待部署验收：阿里云真实签名/模板/凭据与短信送达、PostgreSQL 生产迁移、可信代理后的客户端限流和预算。当前不能据此宣称短信已经上线。

## 个人短信认证适配验证（2026-10-25）

- 新增 aliyun-pnvs 发送适配及配置选择；原 aliyun-sms 接法保留。
- 使用独立Redis 16379与H2执行 Maven package：38项全部通过，无跳过；含10项注册集成测试。短信发送被测试替身替换，未发送收费短信。
- 两个真实阿里云SDK均通过本机HTTP测试服务验证请求序列化及响应解析；保留原SDK较新的共享依赖，避免因新增PNVS SDK降级。
- 正式JAR包含PNVS SDK及生产适配，不含测试Fixture。
- 真实本机启动脚本已验：Redis PONG，后端健康UP，图形验证码API、web2首页及前端API代理HTTP200；Windows侧localhost访问亦HTTP200。
- 未做浏览器E2E；用户自己的真实密钥尚未填写，云平台触发的真实短信及完整手机注册仍需按 SMS_PERSONAL_SETUP.md 验收。
- 自审检查发送失败关闭、SDK/服务端双重禁重试、配置选路、摘要状态与密钥边界，未发现未修复High/Critical。本机配置在仓库外，start-*.sh和Redis数据不提交。

## 图形验证码交互优化（2026-10-26）

- 首次短信发送成功后，收起已消费的图形验证码并聚焦短信码输入框；填写短信码和密码即可注册。
- 倒计时结束不自动换图，用户主动点击重新发送才加载新图；修改手机号清除旧短信状态并恢复图形验证。重发失败保留之前的短信challenge，由后端判断有效性。
- pnpm test:web2：41个文件、158项全部通过。pnpm build:web2通过，保留已有Cesium大chunk提示。
- Edge桌面1440px与手机390px实际点击、输入检查通过：无需二次图形码提交注册、重发重新验证、新短信清空旧输入、更换手机号重置，无页面异常及横向溢出。浏览器检查拦截所有API，未创建真实账号或发送收费短信。
- 本次diff自审通过：仅修改web2显示/交互和对应说明，后端图形码单次消费、短信限流及校验规则不变；未发现未修复High/Critical。
