# XJICloud 移动客户端

状态：**P1 登录注册、加密会话与账号页已落地，正在收尾构建验收**。2026-10-19。P0 原生桥已由用户手动确认可浏览模型，完整真机矩阵仍待联合调试。证据见 [P0](docs/verification/p0.md) 与 [P1 验证记录](docs/verification/p1.md)。

目录名沿用需求 `apps/android`，iOS 平台尚未创建。当前普通入口提供登录、注册、验证码、账号、语言、帮助/许可证和退出；共享四目的地壳已验证，工程、上传训练及正式查看器业务将在后续阶段接入，不提供假入口。

工程已从独立工作区迁回当前仓库，后续直接在本目录维护。无需进入 `.codex/worktrees`。

## 启动产品入口与 P0 探针

本机使用 Flutter 3.47.7 / Dart 3.13.5 / JDK 21。进入本目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/fetch-native3dgs.ps1
flutter pub get
flutter analyze
flutter test
flutter build apk --debug --target-platform android-arm64 --dart-define=API_BASE_URL=https://cloud.xji.org.cn
flutter run -d <device-id> --dart-define=API_BASE_URL=https://cloud.xji.org.cn

# 独立原生模型探针，不需要登录或网络配置
flutter run -d <device-id> -t lib/main_probe.dart
flutter build apk --debug --target-platform android-arm64 -t lib/main_probe.dart
```

固定 SDK AAR、原文档与许可证已在 vendor 中；下载脚本可重建依赖并校验 ZIP、51 个 manifest 文件和 AAR。原生插件位于 `packages/native3dgs_flutter`，消费 AAR 的打包声明位于 Android app，避免在 Flutter library AAR 中重复嵌套本地 AAR。

`API_BASE_URL` 必须是 HTTPS origin，不含 `/api/v1`；缺失或不合法会显示配置错误。用户最初提供的 HTTPS 8080 端口实测不支持 TLS，已使用同域名可用的 443 HTTPS 认证入口。debug 另允许 loopback HTTP：`--dart-define=API_BASE_URL=http://127.0.0.1:8080` 配合 `adb reverse tcp:8080 tcp:8080`；release 不允许明文。测试账号从外部文件传入 integration test，禁止把该测试 APK 当交付包，普通构建只传 API 地址。

`main_probe.dart` 为“Native3DGS · P0 开发验证”：本机 SAF 文件选择、打开/取消/关闭、相机操作与会话状态。支持性检查在创建引擎前执行；x86_64 模拟器与当前 SDK 不兼容的 16KB 页环境会显示明确原因，不加载引擎。此页不调用云端接口，也不注册产品导航。探针与产品沿用相同 applicationId，安装会替换当前入口。

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
