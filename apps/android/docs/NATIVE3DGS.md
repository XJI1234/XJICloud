# Native3DGS Android SDK 0.2.2 接入规格

2026-10-20。已检查发布 ZIP、manifest、AAR Manifest 和 `javap` 公开接口；真实 AAR 已由本地 Flutter 插件消费并通过 arm64 APK 构建。PLY/SPZ、正式 Viewer 云/本机入口及生命周期已在 arm64 真机分项验证；完整结果、profile 证据与未覆盖项见 [真机矩阵](verification/android-device-matrix.md)。不将单设备结果外推到全 GPU/16KB 页。

## 1. 固定来源与核验

- [官方发布 ZIP](https://github.com/XJI1234/Native3DGSViewer/releases/download/v0.2.2/Native3DGS-SDK-0.2.2-Android-arm64-v8a.zip)
- ZIP SHA-256：`c0aa8d36a906437162ab99db10aeaa4ada91adab6caf6e041ca0af2992ff7948`
- `aar/native3dgs-sdk-release.aar` SHA-256：`f0cf100714d5fb15468c7e4cca126cef8007825ab7df63300af0e4438f25813c`
- 包 `manifest.json`：version=0.2.2，ABI=arm64-v8a，C API=2，NDK=27.2.12479018；**51 个清单文件校验全部通过**。此为下载内容一致性核验，不是独立签名背书。
- SDK guide：Android 10/API 29+，Vulkan 1.1，单模型 PLY/SPZ、SH 0–3；技术预览。30 FPS、跨 GPU、低内存、画质和热态不可当作已验收。
- AAR 已含 `jni/arm64-v8a/libgs_android_decoder.so` 与 `libc++_shared.so`，Manifest 带非导出的私有进程 `:native3dgs_decoder` Service。**使用 AAR 时不要再打包 ZIP 的 lib/ 副本**，不通过 pickFirst 盲目掩盖 C++ runtime 冲突。
- 本轮用 NDK `llvm-readelf -l` 检查实际 AAR：`libc++_shared.so` 的 LOAD 对齐为 `0x4000`，但 `libgs_android_decoder.so` 为 `0x1000`，**不满足 16KB ELF 对齐**。固定版本不修改二进制；宿主对 16KB 页环境在创建引擎前返回不支持。ZIP 对齐检查不能替代 ELF/设备验证；兼容该环境需要上游 SDK 修复后单独升级验收。
- AAR Kotlin consumer 示例使用 JDK/JVM 21、coroutines-android 1.9.0。生产工程显式使用 Java/Kotlin JVM target 21、minSdk 29；保留 Flutter 工程自身的 AGP/Gradle 基线后验证消费兼容性，不把 SDK 源码构建依赖强套给预编译 AAR。
- 本机 Flutter 3.47.7 工程使用 NDK 28.2.13676358；消费预编译 AAR 不自动要求重编为 NDK 27。只有重建 SDK/C ABI 时才按 SDK manifest 固定 NDK 27.2 与 CMake 3.22.1。

`apps/android/vendor/native3dgs/0.2.2/` 已保存 AAR、manifest、原文档与 licenses；`scripts/fetch-native3dgs.ps1` 固定上述 URL/hash 并逐清单验证。发行时保留上游/三方许可证和 NOTICE，并核对 SDK 本体的分发许可来源。

## 2. 本地 Flutter 插件边界

`packages/native3dgs_flutter` 只封装平台渲染能力，不包含 Spring API、JWT、OSS 或工程规则。

| 边界 | 职责 |
|---|---|
| Dart `Native3dgsView` | 原生视图承载、viewId、视图生命周期通知 |
| Dart Viewer Infrastructure | `ViewerEnginePort` 实现，map DTO/错误、会话 request token、能力判断 |
| MethodChannel | 创建/打开/取消/关闭、相机离散命令；只传 URI/标识和小型参数 |
| EventChannel | 约 10Hz 状态，包含宿主 sessionId/viewId、请求 ID、activeRequestId、phase、error、sceneCount |
| Kotlin PlatformView | `SurfaceView`、SurfaceHolder.Callback、输入手势所有权、Activity 生命周期 |
| AAR | ContentResolver、隔离解码 Service、JNI、Vulkan 渲染、GPU 排序 |

PlatformView 优先验证 Hybrid Composition 对 SurfaceView 的行为；Flutter 不绘制点云，Dart 通道不传模型字节。Overlay/裁切/层级/透明区域/手势要实测，不假设标准 `AndroidView` 包起来就完成。热手势在 Kotlin 侧采集/合并；若 Flutter 先识别，统一乘 devicePixelRatio 转实际像素，禁止两层重复消费或重复缩放。

定义平台协议时包含 protocolVersion=1、viewId、sessionId 和 operationId；返回 `Result` 对应错误而非 MethodChannel 原始异常。EventChannel 断订阅不得遗留 state collector。disposed 会话丢弃后续事件，避免切 Tab/账号后旧模型覆盖新状态。

## 3. 实际 API 与生命周期

`org.native3dgs.sdk.Native3dgsEngine`：

```kotlin
val engine = Native3dgsEngine(applicationContext)
val generation = engine.attach(surfaceView.holder.surface)
engine.resize(generation, surfaceView.width, surfaceView.height)
val requestId = engine.open(localContentUri) // suspend，在宿主 coroutine 中
// state.phase == Ready && state.activeRequestId == requestId 才算新模型呈现
// surfaceDestroyed: engine.detach(generation)
// 退出且停止一切命令后：后台 coroutine 中 engine.closeAndWait()
```

- SDK `open(Uri)` 是 suspend：返回 requestId 只代表解码/交付完成，GPU 可能仍 Uploading。`Ready` 才表示首帧，不靠 open 返回或下载 100% 推断。
- 挂起的 open 取消 coroutine；拿到 requestId 后用 `cancel(requestId)`。并行打开必须由 session 串行化/失效旧事务；新模型首帧前保留旧模型。
- 每个 Surface 的 attach 返回新代际；resize/detach 使用该代际，尺寸用实际像素。旧代际/重复 detach 会报错，不能吞错后宣称成功。
- 暂停/隐藏 Tab 解除 Surface 并停止输入；恢复后在有效 surfaceCreated 重新 attach/resize。Activity 销毁、切账号、退出 Viewer：停止命令、取消 open/collector，再异步 closeAndWait，不阻塞 UI 线程。
- 销毁期间不能创建无限 engine 实例以绕过驱动停滞；设置宿主 closing 状态，超时展示诊断且保留资源关闭任务，停止新命令。进程内泄漏/崩溃必须记录并修正。
- SDK phase：Empty/Loading/Uploading/Ready/Recovering/Failed/Stopping/Stopped。保存原始 error 数值与 errorOrigin 用于脱敏诊断；区分同步命令、状态、可确认的 decoder 错误。0.2.2 状态通道混合原生与解码错误的数字命名空间，无法确认来源时使用通用重试指引，不将重叠数值误判为 OOM/读取失败等具体原因。
- SDK 规范为 RUB；不照搬旧 Spark 翻转，不加 CSS/视图镜像。翻转由绝对布尔 `flipAxes(x,y,z)` 控制，不改变拖动方向。

相机 API 已核验：`orbit`、`pan`、`dolly`、`look`、`fly`、`fit`、`reset`、`fixedMode`、`freeMode`、`flipAxes`。AAR 还暴露 `setQuality` 与 `drainFrameSamples`，首版用户页不暴露调优/诊断面板。

0.2.2 公开接口没有背景色/clear-color 设置；实际不透明原生画布为白色，背后 Flutter 颜色不会改变它。保留固定版本和 vendor 边界，明确记录与暗画布设计的差异；满足暗画布需要另行升级 SDK 并重新验收。SDK 仅报告 Loading/Uploading/Ready，UI 显示“GPU 上传 / 等待首帧”，不伪造独立上传完成事件。

## 4. 文件、缓存与权限

- 本机文件使用 SAF `ACTION_OPEN_DOCUMENT`，按 provider 能力保存持久读取授权。不要求广域存储权限；非 seek provider 走受控临时副本。
- 云模型经 Bearer 仓储流式下载至应用私有目录；FileProvider 只向包内 decoder 所需权限开放，生成 `content://` URI。验证 decoder 私有进程确实可读，不能把 Android 私有文件路径伪装 URI。
- AAR 没有 HTTP 加载能力，禁止将 presigned/用户 token URL 直接交给 `open`。所有网络状态、取消和缓存归业务仓储。
- 首版协议允许最大 2GB 模型上传，但渲染能力不能按文件大小保证；按设备能力/磁盘预检，SDK OOM 则可取消/退出并显示错误，不继续反复尝试耗尽内存。
- 资源 lease 在解码/场景使用期间保护源文件；关闭/切换确认后释放，缓存清理跳过被占用文件。

## 5. 接入验收门槛

先在兼容 arm64 真机记录设备、Android、GPU、Vulkan、SDK/hash、样本大小/点数与屏幕像素；通过以下矩阵后才宣称原生桥完成：

1. 本机小型有效 PLY/SPZ → Ready，错误文件/不支持设备显示具体错误。
2. 手势与浮层：orbit/pan/pinch、固定/自由、fit/reset/Y 翻转；Flutter 工具栏、Sheet 点击不透传到画布。
3. 生命周期：旋转、切 Tab、全屏/退出、锁屏/解锁、后台/前台、Activity 重建、连续开关 20 次；只有有效 Surface 代际处理命令。
4. 事务：A 显示时开 B；B 失败/取消保留 A；B 立即被 C 取代，迟到 B 不覆盖 C；退出中状态到达不更新新页面。
5. 安全/异常：URI 撤权、云缓存被清理请求、decoder Service 被杀、低内存；无 ANR，无 token/URI 泄露。
6. 性能：profile/release 模式记录呈现帧间隔、RSS/PSS、GPU、加载时间和 10 分钟热态；不能把 `lastFrameMicros`（CPU 时间）或 GPU timer 倒数叫实际 FPS。

现有 AAR 仅 arm64，x86_64 模拟器只测试不创建 engine 的 UI/接口路径。不支持时明确 capability result，不临时编造 x86 库。截图使用 PixelCopy 另立验收；iOS 原生实现另立专项，不将 Android 验收外推到 iOS。
