# Windows Flutter / Android 环境记录

检查日期：2026-10-19（Asia/Shanghai）。这是本机实测记录，路径仅作记录，不应复制进可提交的 local.properties/Gradle 配置。

## 1. 检测基线

| 组件 | 检测结果 |
|---|---|
| OS | Windows 11 x64 |
| Flutter | stable 3.38.9，revision `67323de285`；`D:\flutterSDK\flutter` |
| Dart | 3.10.8 |
| Flutter 镜像 | 已有 `https://storage.flutter-io.cn`、`https://pub.flutter-io.cn`，保留 |
| Android Studio JBR | JDK 21.0.9；`C:\Program Files\Android\Android Studio\jbr` |
| 系统 Java | 原有 Oracle JDK 25.0.2，未改全局 JAVA_HOME；Flutter 单独固定 JBR 21 |
| SDK | `C:\Users\28049\AppData\Local\Android\Sdk` |
| Platforms | android-35、36、36.1 |
| Build Tools | 34.0.0、35.0.0、36.1.0；实际构建可能按 AGP 自动补齐需要版本 |
| NDK | 25.1.8937393、28.2.13676358；AAR消费不需现在重建SDK源码 |
| Command-line tools | 20.0，已归入标准 SDK 目录 |
| Gradle 模板 | Gradle 8.14、AGP 8.11.1、Kotlin 2.2.20 |
| Emulator | 36.4.10.0；AEHD 2.2 可用 |
| AVD | Medium_Tablet / API35 / x86_64 / 2560×1600 / density320 / 2GB RAM |
| arm64 真机 | 本次未连接 |

保留现有 Flutter 版本，未执行 flutter upgrade。未来升级应单独验证插件/AGP/SDK兼容，不能在环境检查中顺带升级全部依赖。

## 2. 已执行的修复

1. 初始 doctor 报 cmdline-tools 缺失：原工具放在 `D:\cmdline-tools`。复制其 bin/lib/NOTICE/source.properties 到标准 SDK 的 `cmdline-tools\20.0`，保留原目录。
2. `flutter config --android-sdk <本机SDK> --jdk-dir <Android Studio jbr>`，避免 Flutter/Gradle 使用系统 JDK25。此为用户级 Flutter 配置；新开编辑器可能需要重启读取。
3. 设置用户级 `ANDROID_HOME` 为同一 SDK。未全局重排 PATH：原 `D:\platform-tools` adb 为36.0.0，标准 SDK 的为37.0.0；推荐用标准 SDK 的 platform-tools，外部 sdkmanager 使用显式 `--sdk_root`。
4. 运行 sdkmanager licenses，接受工具列出的 SDK 许可；复查 doctor 确认 `All Android licenses accepted`。
5. 构建发现 Gradle ZIP 截断：缓存 `gradle-8.14-all.zip` 仅6,109,202字节，无法解压。原文件改名为 `.corrupt-20261019` 保留；从官方源重新下载224,116,304字节完整包，校验官方 SHA-256 后替换单一缓存ZIP，没有删除整个Gradle缓存。

Gradle 8.14 all ZIP SHA-256：`efe9a3d147d948d7528a9887fa35abcf24ca1a43ad06439996490f77569b02d1`。

## 3. 实际验证与边界

环境探针放在 `%TEMP%\xjicloud_flutter_environment_probe_20261019`，不是正式客户端，不复制其计数器页面进产品；本仓库尚无 pubspec.yaml。

| 执行 | 实际结果 |
|---|---|
| `flutter --version` | Flutter 3.38.9 / Dart 3.10.8 |
| `flutter doctor -v` | Android toolchain 通过，JDK21、cmdline-tools、许可证均正常；仍有 Chrome 未安装提示 |
| `flutter emulators` / emulator `-accel-check` | 识别 Medium_Tablet；AEHD2.2 可用 |
| 临时工程 `flutter pub get` | 依赖解析/下载通过 |
| 临时工程 `flutter analyze` | No issues found |
| 临时工程 `flutter test` | 默认计数器 Widget 测试1项通过；仅证明测试运行环境，不是产品测试 |
| 临时工程 `flutter build apk --debug --target-platform android-arm64` | Gradle缓存修复后构建成功 |
| `flutter run -d emulator-5554 --no-resident` | x86_64 Tablet 安装启动成功，Flutter JNI 与 Impeller OpenGLES 初始化；不含3DGS运行 |
| 模拟器实际操作 | 启动→点击 Increment→UI语义树计数从0变1；完成后卸载探针并关闭本次启动的AVD |
| 临时工程加入真实AAR与Kotlin consumer再构建 | Java/Kotlin target21、minSdk29、coroutines1.9.0；arm64 debug APK成功，验证Flutter基线可编译消费SDK，未调用引擎运行 |

探针启动模拟器用于验证后，卸载探针并关闭本次启动的模拟器。SDK 包检查记录见 [NATIVE3DGS.md](NATIVE3DGS.md)。Native3DGS 的真实 Vulkan、触控/Surface内嵌、内存/热态、16KB页兼容均未设备验证；不能由Flutter计数器启动成功推导。

Chrome 缺失属于 Flutter Web 检查项；当前目标Android不依赖Chrome，已有Edge，无需为消除提示改全局浏览器配置。Windows不能完成iOS构建/签名/模拟器验证；后续需macOS与Xcode。

## 4. 后续开发命令

在正式 Flutter 工程创建后于 `apps/android` 使用：

```powershell
flutter doctor -v
flutter pub get
flutter analyze
flutter test
flutter build apk --debug --target-platform android-arm64
flutter devices
flutter run -d <arm64-device-id>
```

未接真机时可 `flutter emulators --launch Medium_Tablet` 测壳与API；不要在x86_64上强行加载arm64 AAR。真机连接后先用 `adb devices -l` 检查授权，再核对 Android版本/ABI/Vulkan。Flutter build命令的实际SDK路径由本机配置生成，禁止提交绝对路径/签名材料。

普通Flutter工程支持不代表后端已联调：正式用户流需后端、Redis、OSS、Worker可达。当前未做移动客户端业务E2E，也未做浏览器E2E；本次不修改web2，因此未运行pnpm test:web2。
