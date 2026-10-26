# Android 手机号注册与阿里云融合认证

2026-10-26。Android 复用 web2 的手机号注册、资料完善与用户 JWT；旧用户名密码登录仍保留。融合认证后端使用现有 PNVS Java SDK 2.0.0，Android 使用用户提供的原始 1.2.16 AAR。

## 用户路径

2026-10-27 修订：App 名称为“玄境云”，启动图标来自 web2 的 `logo_nw.png`。登录页“隐私权政策”打开应用内离线正文，主体与联系方式、官方参考依据见 [隐私政策维护说明](../apps/android/docs/PRIVACY_POLICY.md)。阅读政策不代表同意；只有主动勾选并发起一键认证才检查 SIM 并初始化 SDK。确定无 SIM 的设备在请求后端融合 token 前转短信注册，提示“未识别到本机号码”。验证码输入和图片保持同排，输入/键盘重建复用图片数据；已注册手机号返回明确的密码登录提示。

- 登录默认提供一键登录/注册。阅读数据用途并主动同意后才初始化 SDK；运营商授权页协议默认不勾选。App 不自行读取/信任本机明文手机号。
- SDK 不可用、无可用 SIM、用户选择其他号码或服务不可用时，回到手机号注册；用户取消则留在登录入口。原生会话最多 90 秒，取消、宿主销毁和完成都会释放 SDK。
- 手动注册：手机号 → 图形码 → 短信码 → 设置密码 → 自动登录 → 完善昵称、邮箱和单位。发送短信后收起已消费图形码，冷却结束不自动换图，主动重发或改号码才重新图形验证。
- 两条路径以同一 `users.phone` 查询并复用 userId，未核验的历史同名用户名不能自动绑定。新用户保留 `profileRequired` 门槛，客户端安全持久化、深链守卫和后端 Filter 同时校验。用户在“我的 → 账户资料”可编辑资料。
- 融合新账号使用随机不可知 BCrypt 密码，不提供默认密码；本次未新增密码设置/找回接口。因此一键新账号后续使用一键认证，不能直接凭一个未设置的密码在 web2 登录。短信注册的已有密码不变。

## 后端契约

所有路径前缀 `/api/v1`，统一 `{success,message,data}`。

| 接口 | 契约 |
| --- | --- |
| POST `/auth/fusion/init` | 无客户端包名/签名参数；返回 `challengeId,authToken,schemeCode,templateId,expiresInSeconds=900`；禁止缓存 |
| POST `/auth/fusion/login` | `{challengeId,verifyToken}`；返回现有 `AuthResponse`，包含 `profileRequired` |
| POST `/auth/registration-sms` | `{phone,captchaKey,captchaCode}`；返回 `challengeId,expiresInSeconds,retryAfterSeconds` |
| POST `/auth/register` | `{phone,password,smsChallengeId,smsCode}`；与 web2 相同 |
| GET/PUT `/account` | 用户 Bearer；只读取/修改本人昵称、邮箱、单位 |

融合初始化以服务端配置固定调用 `GetFusionAuthToken`，Android、方案、包名、签名、900 秒。仅下发短期 SDK token，RAM 凭据不进入 App。SDK `onVerifySuccess` 的第一个参数交给 `VerifyWithFusionAuthToken`，必须 `Code=OK`、`Success=true`、`Model.VerifyResult=PASS` 且 11 位大陆手机号才可登录/建号；初始化成功不代表登录成功。

Redis 使用 `xjicloud:fusion-auth:*`：900 秒随机 challenge、24 小时 token SHA-256 防重放、来源 IP 哈希与全局限流。init/login 各自每来源每小时 60 次、全局每天默认 1000 次，失败也计数。challenge 与 token 防重放原子消费，调用失败不恢复或自动重试。Redis 当前为单节点，多键 Lua 不支持跨槽 Redis Cluster。仅信任 socket peer，不信任客户端 `X-Forwarded-For`；生产代理后共享额度问题与短信相同。

## 创建方案与配置

当前测试 APK 的 applicationId 为 `com.xjicloud.mobile`，证书 MD5（小写、无冒号）为 `07fb12fb110de1fe5f9eb7625c81a6f3`。已核对实际 APK 和本地证书一致。release 仍沿用仓库原有 debug 测试签名；正式签名变更后须重新登记真实证书，不能把此签名当作正式发行验收。

用户已提供方案 Code `FA000000009797924267`，登录场景 `100001`。把以下配置合并进实际后端 `application-prod.yml` 的现有 `xjicloud` 节点，不重复创建顶层键：

```yaml
xjicloud:
  fusion-auth:
    enabled: true
    scheme-code: FA000000009797924267
    template-id: '100001'
    package-name: com.xjicloud.mobile
    package-sign: 07fb12fb110de1fe5f9eb7625c81a6f3
    daily-limit: 1000
```

RAM AccessKey 直接复用 `xjicloud.sms.access-key-id/access-key-secret`。使用同一个 RAM 用户增加 `dypns:GetFusionAuthToken`、`dypns:VerifyWithFusionAuthToken`，保留短信的 SendSmsVerifyCode/CheckSmsVerifyCode 权限。`sms.enabled` 可独立关闭，但手动注册需短信服务已配置。开启融合认证但缺少共享 RAM 凭据、方案或合法签名时启动失败；默认关闭，不影响旧密码登录。生产后端需 HTTPS 访问 `dypnsapi.aliyuncs.com`。

不需要将方案/Token/AccessKey 编译到 Flutter；App 从 init 动态获取短期 token。方案中本次只接运营商号码认证登录节点，不启用 Umeng 风险插件；配置中保留用户授权运营商协议。一键不可用由 App 自己转已有图形码+短信路径，不依赖 SDK Demo 的模拟节点。

## 验证命令与待验收

后端使用隔离的非 6379 Redis；集成测试会清理本功能测试前缀，不可连接生产 Redis：

```powershell
$env:SMS_TEST_REDIS_PORT='16389'
cd services/backend
mvn -B package
```

Android 执行 `flutter analyze`、`flutter test`、`flutter build apk --debug --target-platform android-arm64 --dart-define=API_BASE_URL=https://cloud.xji.org.cn`，宿主 Kotlin 测试 `gradlew.bat :app:testDebugUnitTest`。供应商 Java 测试使用真实 SDK 对 loopback 服务验证序列化/错误/超时，不发送收费短信。

真实验收需已部署配套后端和 arm64 SIM 真机：一键老账号 userId 复用、新账号资料完善、无 SIM/关闭蜂窝/用户换号降级、用户取消、协议阅读/拒绝、SDK 超时、后台/旋转/销毁、短信错码/过期/重发、退出和重启资料门槛。x86_64 模拟器不能证明运营商 SDK 成功（该 AAR 不含 x86_64 JNI）。本次未执行生产发布；构建或 mock 测试不等于运营商链路已通过。

官方依据：[准备工作](https://help.aliyun.com/document_detail/362064.html)、[Android SDK](https://help.aliyun.com/zh/pnvs/developer-reference/the-android-client-access-3)、[GetFusionAuthToken](https://help.aliyun.com/zh/pnvs/developer-reference/api-dypnsapi-2017-05-25-getfusionauthtoken)、[VerifyWithFusionAuthToken](https://help.aliyun.com/zh/pnvs/developer-reference/api-dypnsapi-2017-05-25-verifywithfusionauthtoken)、[SDK 隐私初始化](https://help.aliyun.com/zh/pnvs/security-and-compliance/converged-certification-services-compliance-guide)。
