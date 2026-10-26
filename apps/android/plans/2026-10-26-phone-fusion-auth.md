# Android 手机号与融合认证实施计划

目标：Android 与 web2 共用手机号账号、JWT 和资料完善门槛；后端先验证，再接入用户提供的融合认证 SDK 1.2.16。

设计依据：用户明确要求两条路径，不可用时图形码 + 手机短信 + 密码注册；可用时阿里云认证后登录或创建账号。旧用户名密码登录保留。融合认证不信任客户端手机号，只接受服务端 VerifyWithFusionAuthToken 返回 PASS 的号码。阿里云 RAM AccessKey 直接复用 xjicloud.sms 的配置，不向 App 下发。两条路径按 users.phone 复用同一个 userId，不自动绑定历史同名但未核验的用户名。

接口：POST /api/v1/auth/fusion/init 返回 challengeId、authToken、expiresInSeconds；POST /api/v1/auth/fusion/login 接受 challengeId、verifyToken，返回现有 AuthResponse。初始化以服务器固定 schemeCode/packageName/packageSign 请求 GetFusionAuthToken（Android，900 秒）。Redis 保存短期 challenge、token 摘要防重放与原子来源/全局限流，不存手机号或 token 明文。失败关闭，不自动重试供应商调用。

新账号仍使用非空 BCrypt 随机不可知密码，不增加默认密码或第二张用户表；资料使用现有 GET/PUT /account。profileRequired 持久化进客户端会话，恢复/深链同样守卫；资料保存更新本地会话。是否提供一键账号主动设密码不扩展到本次范围。

执行与验收：

- [x] 后端先写适配/契约/账号复用与拒绝路径测试，观察失败。
- [x] 实现融合认证配置、SDK 适配、限流防重放及 Controller，复用现有用户建号与 JWT。
- [x] 独立 Redis + H2 执行 Maven package，含短信回归；真实 Java SDK 用 loopback 服务验证请求与响应，不发送收费短信。
- [x] 提供当前 APK applicationId 与证书 MD5，等待用户配置方案；云端配置不阻塞本地可独立验证的客户端工作。
- [x] Android 扩展 Identity port/use case/controller、会话、手机号注册与资料页，保持现有 DDD。
- [x] 接 SDK 原生边界，明确隐私同意、取消、不可用与超时降级；不使用 Demo 签名或密钥。
- [x] flutter analyze、flutter test、arm64 debug APK、原生测试；可用设备走用户路径，明确未覆盖运营商真实链路。
- [x] 对本次 diff 做安全/回归/DDD 自审并同步 API 与部署文档。

官方依据：https://help.aliyun.com/document_detail/362064.html 、https://help.aliyun.com/zh/pnvs/developer-reference/api-dypnsapi-2017-05-25-getfusionauthtoken 、https://help.aliyun.com/zh/pnvs/developer-reference/api-dypnsapi-2017-05-25-verifywithfusionauthtoken 。方案与签名仅由后端配置，用户提供 schemeCode 后再进行真实云端验收。

## 2026-10-27 无 SIM 平板修订

- [x] 登录页精简说明，内嵌双语隐私权政策与指定主体/联系邮箱；所有登录模式保留阅读入口。
- [x] 无 SIM 在后端融合初始化前转入短信注册，提示“未识别到本机号码”。
- [x] 图形验证码与输入框同排；输入和键盘重建复用相同图片数据；已注册手机号显示明确提示。
- [x] 应用名称更新为“玄境云”，启动图标来自 web2 指定图片，包含 Android 自适应图标。
- [x] Flutter analyze / 264 项测试、后端 79 项测试和 arm64 APK 构建通过；用户提供的 Android 16 无 SIM 平板已安装调试。
- [ ] 用户更新后端配置后，再验收真实短信、已有账号登录、SIM 运营商一键认证及新账号资料完善。上述本地/替身测试不替代这些待验收项。
