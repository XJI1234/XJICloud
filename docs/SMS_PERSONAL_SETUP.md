# 阿里云短信认证接入

当前实现仅使用号码认证PNVS，由阿里云生成并校验验证码，普通短信SendSms与本地HMAC验证码逻辑已删除。

1. 在阿里云完成实名、开通“号码认证服务 → 短信认证”，按需充值或购买短信认证套餐。普通短信服务资源包不通用。
2. 在短信认证控制台选择赠送签名和匹配的赠送模板；确认模板包含验证码与有效分钟数变量。以控制台实际分配为准，模板示例通常使用code/min，不能把模板编号当作普通短信模板ID。
3. 创建RAM程序用户，授予 `dypns:SendSmsVerifyCode`、`dypns:CheckSmsVerifyCode`，资源 `*`。勿使用主账号密钥。CSV凭据只在本机读取，不提交或输出。
4. 使用 `sudo bash deploy/deploy-backend.sh configure`，启用短信并按提示填写RAM凭据、签名、模板、方案、变量和日限额。所有业务配置集中写入 `deploy/config/application-prod.yml`，不创建独立短信环境文件，也不生成验证码摘要密钥。
5. 首次安装使用install；已有服务器先按 [部署指南](./PHONE_REGISTRATION_DEPLOYMENT.md) 迁移旧backend.env，再用upgrade。默认升级保留生产YAML，显式--update-config才会备份并整体替换。
6. 从实际注册页发送短信，用户输入短信码后后端调用CheckSmsVerifyCode。发送成功仅表示受理；核验必须为PASS。控制台测试短信不属于本平台注册challenge。

短信参数固定为6位数字、300秒有效、60秒重发间隔，重发覆盖旧码。平台保留发送额度、5次核验尝试及注册challenge单次消费。不要把验证码、密钥或带密码的连接串放入截图/聊天/前端，也不要公开完整配置或进程环境。

常见失败：Forbidden.NoPermission检查RAM授权；FUNCTION_NOT_OPENED检查账号开通；INVALID_PARAMETERS核对赠送签名/模板与变量；FREQUENCY_FAIL/BUSINESS_LIMIT_CONTROL等待限流窗口。SDK报错信息可能包含敏感数据，不应原样输出。

此前源分支的/home/li和start-*.sh、独立sms.env/HMAC密钥等做法已过时，不适用于当前部署。
