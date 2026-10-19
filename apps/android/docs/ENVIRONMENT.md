# Windows Flutter / Android 环境记录

检查日期：2026-10-19（Asia/Shanghai）。本页记录当前开发机的实际结果；旧文档中的 D 盘 SDK、用户 28049 和 Flutter 3.38.9 属于另一台机器，不能作为本机验证证据。机器路径只用于诊断，不写入可提交的 Gradle 配置。

## 1. 当前基线

| 组件 | 本机检测结果 |
|---|---|
| OS | Windows 11 x64，10.0.26200.6584 |
| Flutter | stable 3.47.7，revision `abaf9c5237` |
| Dart | 3.13.5 |
| Flutter SDK | `C:\Users\21544\flutter\flutter` |
| Flutter 使用的 Java | Android Studio JBR 21.0.10 |
| 系统 Java | Eclipse Adoptium JDK 21.0.11；保留原 JAVA_HOME |
| Android SDK | `C:\Users\21544\AppData\Local\Android\Sdk` |
| 工程模板 | AGP 9.1.0 / Kotlin 2.4.0 / Gradle 9.3.1 |
| Android 目标 | minSdk 29，compileSdk/targetSdk 36；引擎发布仅 arm64-v8a |
| Flutter 默认 NDK | 28.2.13676358；消费预编译 AAR，不重编 SDK |
| Emulator | 36.5.10.0；`-accel-check` 确认 WHPX 可用 |
| AVD | 新建 XJICloud_API35，google_apis / x86_64，1080×2400，density 420，2GB RAM |
| 真机 | 尚未接入 Agent；用户已手动安装 P0 APK，报告可浏览模型，并授权 P1 构建后联合调试 |

保留现有 Flutter，不执行 upgrade/downgrade。旧计划的 3.38.9 基线已由本机基线替换，兼容性以本轮实际构建为准。SDK AAR 仍固定 0.2.2 与原 SHA-256，不随 Flutter 调整。

## 2. 本轮环境调整

1. 用户 PATH 添加 Flutter bin 和标准 SDK platform-tools；保留其他开发工具。已打开的编辑器/终端需重新启动，或在当前终端刷新 PATH。
2. `flutter config --android-sdk <本机 SDK> --jdk-dir <Android Studio jbr>` 固定 Flutter 的 JDK 21，不修改全局 JAVA_HOME。
3. 接受 SDK 工具列出的许可。复查 doctor 的 Android toolchain 通过，显示 `All Android licenses accepted`。
4. 原 command-line tools 11.0 无法理解现有 SDK XML4；安装 cmdline-tools/latest（23.0），保留 11.0。23.0 的 sdkmanager 是 Android CLI 兼容入口，包名建议使用 `platforms/android-36`、`ndk/28.2.13676358` 等斜杠形式。
5. 补齐工程所需 platform 36 和 NDK 28.2.13676358；不删除已有 NDK、Gradle 缓存或系统镜像。Gradle 使用同版本官方 -bin 分发包。
6. 既有 API34/36.1 镜像缺少 system.img，不能启动；另安装完整 API35 google_apis x86_64 镜像并新建 XJICloud_API35，冷启动确认 sys.boot_completed=1，页大小4096。保留原镜像目录。

本机还存在其他 adb；本次设备操作统一使用标准 SDK 的 platform-tools/adb.exe，不删除其他软件附带的 adb。

## 3. 验证证据

| 检查 | 结果 |
|---|---|
| `flutter doctor -v` | Flutter 与 Android toolchain 通过；Chrome 缺失与部分网络探测超时仍存在 |
| `emulator -accel-check` | WHPX 可用 |
| `adb devices -l` | 无真机 |
| Native3DGS 发布依赖 | 下载脚本校验 ZIP、manifest 每个文件、AAR；见原生规格 |
| 产品自动化、APK、模拟器 | 结果统一记录在 [P0 验证记录](verification/p0.md) |

Chrome 属于 Flutter Web 检查项，本轮 Android 目标不依赖 Chrome。网络 doctor 的 GitHub/Maven 首页探测超时不等同于构建依赖不可达；实际依赖获取和构建结果另行记录，不把 doctor 表述为全绿。

## 4. 开发命令

在 apps/android 中执行（首次打开新终端后应能找到 flutter）：

```powershell
flutter doctor -v
powershell -ExecutionPolicy Bypass -File scripts/fetch-native3dgs.ps1
flutter pub get
flutter analyze
flutter test
flutter build apk --debug --target-platform android-arm64
flutter devices
```

插件 Kotlin 测试、设备测试命令与记录见 [P0 验证记录](verification/p0.md)。local.properties、.dart_tool、build 和签名材料必须保持忽略。

## 5. 设备验证进展

初次 P0 交付时没有 Android 10+、arm64、Vulkan 1.1 真机。随后用户手动安装 APK 并确认模型浏览正常，已授权进入 P1；当前尚未将设备接入 Agent。模拟器证据仍只涵盖 Flutter UI、通道与明确拒绝不支持架构，不外推为 GPU/Surface、内存和热态验收。

P1 构建后连接真机，按 [原生接入验收矩阵](NATIVE3DGS.md) 补测尚未观察的项目。Windows 不承担 iOS 构建/签名验证；不修改 web2、backend、admin、Spark 或 Rust。
