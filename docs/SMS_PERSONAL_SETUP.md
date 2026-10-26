# 个人短信认证：从控制台测试到云平台注册

> 源分支开发者的历史本机记录，`/home/li` 和未提交的 `start-*.sh` 不适用于当前Windows工作区。当前部署请使用 [手机号注册后端部署指南](./PHONE_REGISTRATION_DEPLOYMENT.md)。下文中的本机反馈与验证数量不是本次移植的验证结果。

适用本机：Windows + WSL Ubuntu-22.04，工程 /home/li/XJICloud，分支 li-chengtao。已完成阿里云个人实名、开通、充值、预置签名/模板选择、RAM授权及控制台真实短信测试。

## 一、已准备什么

后端支持 SMS_PROVIDER=aliyun-pnvs。复用既有注册验证码生成、Redis HMAC存储、限流及原子单次校验，保留密码登录、资料完善与老账号。发送采用官方支持的自定义验证码参数，不使用阿里云 CheckSmsVerifyCode 校验本平台生成的验证码。

本机配置文件 /home/li/.config/xjicloud/sms.env 在Git仓库外，权限600。已预填用户提供的签名“恒创联众”、模板编号“100001”、随机摘要密钥，默认关闭发送。实际签名、模板和变量以控制台所选配置为准。

本机 start-backend.sh 自动加载该文件。start-infra.sh 在Docker不可用时使用本机已有Redis，监听127.0.0.1:6379；此路径只为当前注册联调准备，不启动MinIO。所有 start-*.sh 均为忽略的本机便利文件，不提交。

## 二、打开Ubuntu

在 Windows PowerShell 或 Windows Terminal 中输入：

~~~powershell
wsl -d Ubuntu-22.04
~~~

下面所有命令都在打开后的Ubuntu终端执行：

~~~bash
cd /home/li/XJICloud
git branch --show-current
~~~

应显示 li-chengtao。不需要开另一台虚拟机，不要在Windows目录复制一套工程。

## 三、填写本机密钥

~~~bash
nano /home/li/.config/xjicloud/sms.env
~~~

修改以下三项：

~~~dotenv
SMS_ENABLED=true
ALIBABA_CLOUD_ACCESS_KEY_ID='填你的RAM用户AccessKey ID'
ALIBABA_CLOUD_ACCESS_KEY_SECRET='填对应的AccessKey Secret'
~~~

将引号内部的提示替换为真实值，保留半角单引号。不要在等号两侧加空格。使用RAM程序用户密钥，不使用主账号密钥。

核对下列配置；不要删除已经生成的 SMS_CODE_SECRET：

~~~dotenv
SMS_PROVIDER=aliyun-pnvs
SMS_SIGN_NAME='恒创联众'
SMS_TEMPLATE_CODE='100001'
SMS_CODE_VARIABLE=code
SMS_MINUTES_VARIABLE=min
SMS_DAILY_LIMIT=20
~~~

模板默认使用 code 与 min 两个变量；如实际变量名称不同，修改相应 VARIABLE 设置。分钟数由后端固定传5，与有效期一致。日上限20为本机联调预算上限，窗口从第一次请求起24小时计，正式上线需按预算调整。

nano保存：Ctrl+O，回车；退出：Ctrl+X。然后：

~~~bash
chmod 600 /home/li/.config/xjicloud/sms.env
~~~

不要把密钥贴到聊天、命令行参数、前端文件或Git。无需截图该文件，也无需用 cat 打印内容。

## 四、启动服务

若旧后端仍在运行，先到原后端终端 Ctrl+C。修改配置后必须重启后端，健康检查已经UP不代表读取了新配置。

Ubuntu终端A：

~~~bash
cd /home/li/XJICloud
./start-infra.sh
./start-backend.sh
~~~

看到 Redis PONG，后端随后出现 Started XjiCloudApplication。第一次构建可能需要下载新SDK；等启动完成，终端保持打开。配置不完整会启动失败，补齐配置后重试。

另开Ubuntu终端B：

~~~bash
cd /home/li/XJICloud
./start-frontend.sh
~~~

该终端也保持打开。若新开的是PowerShell，先执行 wsl -d Ubuntu-22.04，再输入以上命令。

在Windows浏览器访问：http://localhost:5176

后端健康检查：http://localhost:8080/actuator/health

正常返回 status=UP。MinIO未启动不影响注册和账户资料验收；图片训练/OSS上传需要另外启动MinIO，本次不依赖它。

## 五、实际验收注册

1. 网页若已有登录状态，先退出；在登录页点击注册。
2. 填一个没有在这个本地云平台注册过的11位大陆手机号。阿里云控制台测试不会创建平台账号。
3. 填图形验证码，点击发送；成功后收起图形验证，直接填写短信码和密码。只有主动重发或更换手机号才重新显示图形验证。
4. 等手机收到这次网页触发的6位短信。不要填写之前控制台测试的验证码。
5. 可先错填一次，确认注册被拒绝，再填写正确验证码。不要连续错填5次。
6. 设置并确认自己的密码（8至64字符），在5分钟内点击注册。
7. 确认自动登录并跳到账户信息页。
8. 填昵称、邮箱、单位/学校（个人可填“个人用户”），保存进入首页。
9. 退出，用该手机号与刚设置的密码登录。
10. 在阿里云号码认证控制台检查发送记录、运营商结果和用量；受理成功不等于送达，以手机收到和运营商回执为准。

不需要反复发很多条。本机限制每手机号60秒一次、24小时10次、来源IP每小时30次、全局24小时20次。测试错误验证码不需要再发送。

## 六、常见问题

- 短信注册尚未开通：SMS_ENABLED仍为false，或旧后端尚未重启。
- SMS configuration incomplete：RAM凭证、签名、模板或摘要密钥未填齐；不要发送完整配置。
- 短信发送暂不可用：查号码认证控制台发送记录，核对服务开启、余额、RAM的 dypns:SendSmsVerifyCode 权限、预置签名/模板配对。普通短信包不能抵扣。
- 发送频繁：等60秒再试；失败也占冷却/配额。不要通过清Redis绕过预算限制。
- 手机号无法注册：已有该手机号或同名旧用户名，先尝试登录；不要删除旧账号。
- 短信码错误：使用本网页最新一次短信，5分钟内填写。控制台生成的验证码不适用。
- Docker命令不存在：本机脚本已有Redis替代路径；注册无需MinIO。
- 网页打不开：确认两个终端仍在运行，使用5176端口，查看启动错误。
- 电脑关闭或WSL停止后：再次按第四节启动。各终端Ctrl+C停止相应服务。

阿里云控制台：https://dypns.console.aliyun.com/

完成后只需反馈网页能否打开、短信是否收到、能否注册/完善资料/退出重登及错误文字。不要提供AccessKey、短信码或密码。

## 七、Git与部署边界

本机 start-*.sh、短信配置和Redis数据不提交。可提交内容只有后端适配、测试、部署配置示例和说明。未经明确指令不推送，只能推 origin/li-chengtao。

本机通过不等于正式站点已上线。生产仍需负责人注入配置、按 PHONE_REGISTRATION.md 做数据库迁移和真实环境验收。
