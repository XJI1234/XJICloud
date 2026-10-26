# 手机号验证码注册：交付与验收入口

提交分支：li-chengtao。范围：web2、Spring Boot 用户注册、部署配置与数据库迁移。

## 解决的问题与最终行为

原先仅填写用户名和密码即可注册，现在注册必须验证大陆手机号。用户填写手机号及图形验证码，获取6位短信码并设置密码；注册成功自动登录，要求完善昵称、邮箱、单位或学校，再进入平台。后续继续使用手机号和密码登录，旧账号保留用户名和密码登录。

短信发送成功后收起图形验证并聚焦短信码，不再自动换图误导用户二次填写。倒计时结束后主动点“重新发送”才重新显示图形验证；修改手机号清除旧短信状态。

## 阿里云产品与安全边界

支持通过 SMS_PROVIDER 选择：

- aliyun-pnvs：号码认证服务下的短信认证，个人开发者使用预置签名和模板，无需提交企业短信资质。官方 SendSmsVerifyCode 支持传入平台生成的验证码，校验继续由本平台完成。
- aliyun-sms：原短信服务 SendSms，适用于已完成自有签名/模板审核报备的账号。

两种产品的套餐包不互通。验证码有效5分钟、最多5次错误、原子单次消费；同手机号60秒间隔、24小时10次，来源IP每小时30次，全局默认24小时1000次（可配置）。Redis保存HMAC摘要，不保存明文验证码；SDK和短信认证服务端重试均关闭。

默认关闭短信注册。密钥只通过后端环境注入；启用但配置缺失会拒绝启动。用户资料只能由本人修改，新用户未完善资料时前后端均阻止访问业务功能。邮箱仅作为资料保存，没有做邮箱所有权验证。

## 学长如何检查

1. 查看本提交完整diff，重点入口：
   - 前端注册页面：apps/web2/src/features/identity/presentation/components/RegisterForm.vue
   - 前端身份用例：apps/web2/src/features/identity/application/use-cases/auth.usecase.ts
   - 后端注册：services/backend/src/main/java/com/xjicloud/auth/AuthService.java
   - 短信与Redis状态：services/backend/src/main/java/com/xjicloud/auth/sms/
   - 配置示例：deploy/config/backend.env.example
2. 根据 docs/PHONE_REGISTRATION.md 配置后端。个人开发者选择 SMS_PROVIDER=aliyun-pnvs；不同环境自行注入RAM凭证、预置签名/模板和独立摘要密钥，不能直接复用本机秘密文件。
3. 生产 PostgreSQL 的 ddl-auto=validate 模式先备份并执行 deploy/migrations/20261021_phone_registration.sql；本机H2开发模式自动更新。
4. 启动Redis、后端和web2，用测试手机号走：图形码 → 短信 → 注册 → 完善资料 → 退出 → 手机号密码登录。
5. 补验错误/过期验证码、重发旧码失效、重复手机号、未完善资料访问限制，以及旧账号登录。

本机操作参考 docs/SMS_PERSONAL_SETUP.md，其中 /home/li 路径和 start-*.sh 属于李成涛机器的便利配置。脚本不随提交分发；其他机器按项目启动及部署文档操作。

## 已完成验证

- 2026-10-25：用户反馈本机真实短信注册成功。
- 前端最新验证：pnpm test:web2，41个文件、158项通过；pnpm build:web2通过，保留已有Cesium大chunk提示。
- 后端短信认证适配版本：独立Redis 16379与H2，mvn -B package，38项全部通过、无跳过。测试不发送收费短信。
- 使用真实阿里云SDK连接本机HTTP测试服务，验证新旧发送接口序列化与响应解析。
- Edge桌面1440px、手机390px操作检查：短信发送后不必二次图形验证即可提交注册、主动重发、更换手机号重置，无页面异常和横向溢出。该轮浏览器测试拦截API，不代表真实运营商验收。
- 注册首版浏览器联调覆盖资料门槛、保存、退出与密码登录，使用测试短信transport。
- 图形验证优化提交已用仓库 code-review / OCR 执行 preview、rule，并由宿主Agent审查生产代码、测试及说明，未发现需修复问题；整任务此前各阶段均做过自审。

## 正式上线仍需确认

- 生产数据库迁移、真实部署配置与正式域名下完整注册。
- 单节点Redis部署；Lua脚本不支持Redis Cluster跨槽。
- 反向代理后的socket IP会共享限流额度，需要可信边缘逐客户端限流和预算告警。
- 邮箱验证、短信免密登录、找回密码、手机号换绑不在本次任务范围。

## 提交内容

包含前后端实现、测试、SQL迁移、配置示例和说明；不包含实际AccessKey、密码、验证码、本机环境文件、start-*.sh、exe安装包或本机测试数据。未修改main、apps/web、packages/spark或rust，模型存储策略不变。
