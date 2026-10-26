# 内嵌隐私权政策

2026-10-27。登录页的“隐私权政策”打开 Flutter 本地内容页；正文随 App 打包，无需网络，不初始化 SDK，也不自动勾选同意。密码登录和短信注册页也保留阅读入口。中英文正文位于 `lib/shared/l10n/app_zh.arb` / `app_en.arb`，页面位于 Identity Presentation 的 `privacy_policy_page.dart`。

用户提供的运营主体为“东南大学玄境创新团队”，隐私联系邮箱为 `baokx@seu.edu.cn`。正文按实际功能说明账号、资料、文件、认证、数据保存、安全、用户权利、未成年人保护与政策更新；账号注销通过邮箱申请，未虚构自助注销接口。

## 官方依据

本次核对下列官方页面及其推荐条款，未把供应商面向开发者的合同直接改名为应用自身政策：

- [融合认证合规配置说明](https://help.aliyun.com/zh/pnvs/security-and-compliance/converged-certification-services-compliance-guide)：融合 SDK 名称、服务类型、设备名称/型号/系统版本，以及同意后初始化。
- [号码认证合规配置说明](https://help.aliyun.com/zh/pnvs/security-and-compliance/number-certification-service-compliance-guidelines)：号码认证的数据类型、网络权限与使用场景。
- [号码认证服务隐私权政策](https://terms.aliyun.com/legal-agreement/terms/suit_bu1_ali_cloud/suit_bu1_ali_cloud202112211045_86198.html)，官方页面更新日期 2026-04-10：参考其信息收集、共享、安全、权利、未成年人、保存、更新和联系结构，并披露其列出的移动、联通、电信 SDK 运营主体与数据字段。
- [短信认证 SDK 隐私权政策](https://terms.alicdn.com/legal-agreement/terms/privacy_policy_full/20230922101800634/20230922101800634.html)：作为短信服务关联政策参考，正文明确本 App 短信通过后端 PNVS 接口处理，未启用融合 SDK 短信节点。

运营商链接取自号码认证官方政策，并以固定枚举经 Domain port 交给原生 HTTPS 协议页打开。没有向 Presentation 暴露任意 URL 打开能力；本地正文始终可返回，供应商网页需要联网。

当前未启用 SDK 图形认证、短信认证或友盟风险节点，图形码来自玄境云后端。因此未把融合 SDK 可选能力的全部数据采集范围冒充当前实际功能。后续增加 SDK 节点、文件权限或服务处理方式时，应同步更新正文。
