# XJICloud 移动客户端

状态：**方案与实施计划草案，尚未创建 Flutter 产品工程**。2026-10-19。

目录名沿用需求 `apps/android`，后续此处的 Flutter 工程同时容纳 Android 与 iOS；业务 Dart 代码只维护一份。当前交付是环境检查和设计计划，不是可运行客户端。

## 阅读顺序

1. [本地环境及验证记录](docs/ENVIRONMENT.md)
2. [产品范围与 DDD 架构](docs/ARCHITECTURE.md)
3. [Phone / Pad 页面与交互规格](docs/MOBILE_UI.md)
4. [后端复用与协议限制](docs/API_REUSE.md)
5. [Native3DGS 接入规格](docs/NATIVE3DGS.md)
6. [分阶段实施计划](plans/2026-10-19-flutter-mobile.md)

工程规则继续遵循 [TEAM_ENGINEERING](../../docs/TEAM_ENGINEERING.md)。Web2 是功能与视觉参考，禁止改动已弃用的 `apps/web`；本次不改后端、管理端、Spark 或 Rust。

## 首版范围

- 登录、注册、验证码、会话到期处理。
- 主页、工程增删改查、按账号保存最近 5 个工程。
- 图片多选/目录导入、逻辑归档、OSS 前台上传、服务端训练状态。
- 工程模型管理、PLY/SPZ 分片上传、鉴权下载与账号隔离缓存。
- 本机/云端普通模型查看：Android Native3DGS SDK 0.2.2。
- 我的：账号展示、语言、缓存管理、帮助、退出。

不迁移航线规划、SuperSplat 高级编辑、搜索索引、双屏、其他占位入口；不新增云端搜索、账户编辑、找回密码、推送或计费。

**已确认：** 首版前台上传，切后台暂停，返回核对状态后重试；服务端训练继续。数据集没有补签/续传 API，过期时需要重新提交，详见协议说明。

## 实施门槛

先验证原生渲染桥，再扩展业务界面。开发期 x86_64 模拟器只承担 Flutter UI 与 API 测试；SDK 渲染必须用 Android 10+、arm64、Vulkan 1.1 真机验收。未来 iOS 需 macOS/Xcode 和独立的 iOS 渲染实现；本包 Android AAR 不能直接用于 iOS。
