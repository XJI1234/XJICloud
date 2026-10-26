# 飞机位置与航向交付说明（2026-10-26）

## 内容与方案

采用最小完整方案，在网站现有航线 Cesium 地图增加只读飞机图层，复用手机 Relay 和本机网关。适配目标：Mini 4 Pro + RC-N2 + 普通安卓手机，MSDK 5.17.0。

显示所有有效定位飞机，当前任务飞机黄色高亮；点另一架只查看，不更改任务手机。详情包含经纬度、相对起飞点高度、机头航向和位置更新时间。超过 5 秒没有新定位采样变灰；断连保留最后位置，恢复新采样后恢复显示。电量更新和重复快照不会刷新旧位置；快照请求卡住也能标记过期。无航向用圆形标记，无坐标不创建标记。

当前明确为**平面定位**。SDK 未明确原始高度的垂直基准，不能把相对起飞点高度当作海拔/椭球高。没有自动跟随、轨迹、速度箭头、返航点或新增飞控命令。现有地图白模、静止按需绘制、航点进度和四机监控已保留。

## 分支与源码

三个仓库分别在 `feat/aircraft-map-20261026` 提交，不合并、不改写原分支：

- 网站：[XJICloud](https://github.com/XJI1234/XJICloud/tree/feat/aircraft-map-20261026)，基于 `codex/integrate-local-startup-20261016`。
- SDK/操作台源码：[Sky-Command](https://github.com/XJI1234/Sky-Command/tree/feat/aircraft-map-20261026)，同一集成分支基线。
- 手机：[MSDK-Relay](https://github.com/XJI1234/MSDK-Relay/tree/feat/aircraft-map-20261026)，基于原 main。

网站内置 SDK 0.2.19 的本地 tgz 和重建后的操作台。普通运行只需要 XJICloud；若要修改并重建操作台，需要相邻 Sky-Command。每仓库根目录的 TASK、DESIGN、PROGRESS 记录细节和待办；具体提交使用中文。

## 本机 PowerShell 启动

需要 Node.js 24.14+，本机已使用 Node 24.21。关闭旧版 Sky Command 桌面程序，避免占用 8080、19500、18080；网站控制端口为 8767。

```powershell
Set-Location -LiteralPath 'D:\engineer-project\msdk修改\XJICloud'
git switch feat/aircraft-map-20261026
corepack pnpm@9.15.0 install --frozen-lockfile
corepack pnpm@9.15.0 --filter @xjicloud/web2 dev
```

使用明确版本及 `--filter`，避免本机 PATH 里的另一版本 pnpm 被嵌套调用。打开 [网站](http://localhost:5176/)，登录后进入任务执行、航线；也可直接打开 [本机操作台](http://localhost:5176/sky-operator/index.html?workspace=routes) 检查地图。直接入口不验证云端登录流程。

电脑和手机接同一 WLAN，手机使用本次 Relay APK，通过既有自动发现连接本机网关。RC-N2 接安卓手机，再按既有流程连接飞机。没有手机定位时地图不显示模拟飞机；正式页面不包含模拟开关。底图依赖网络，局域网发现还依赖本机网络和防火墙的既有配置。

## APK

本机文件：`D:\engineer-project\msdk修改\交付\aircraft-map-20261026\MSDK-Relay-aircraft-debug.apk`。

- 体积：296679565 字节。
- SHA-256：`b50d6964f94c7754784082557f232cf79fcf8738860597d744514ffac12ff5e4`。
- arm64-v8a 调试包；本地配置存在 DJI API Key，但未验证安装、应用签名与 DJI 注册成功，不公开密钥。
- APK 不进 Git。若已有应用使用不同签名，先确认原应用数据及安装方式，不在本任务中自动卸载手机应用。

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath 'D:\engineer-project\msdk修改\交付\aircraft-map-20261026\MSDK-Relay-aircraft-debug.apk'
# 手机开启 USB 调试、电脑已有 adb，且确认签名兼容后：
adb install -r 'D:\engineer-project\msdk修改\交付\aircraft-map-20261026\MSDK-Relay-aircraft-debug.apk'
```

## 重建与模拟

操作台从相邻源码重建；`SKY_COMMAND_SOURCE_ROOT` 可覆盖源码目录。网站保留的地图性能来源为 `apps/web2/scripts/embedded-route-map.mjs`。构建记录在 `operator-build.json`，普通网站构建不重打操作台。

```powershell
Set-Location -LiteralPath 'D:\engineer-project\msdk修改\Sky-Command'
npm ci
Set-Location -LiteralPath '..\XJICloud'
node scripts/test-records/run.mjs --label sky-operator-build -- node apps/web2/scripts/build-sky-operator.mjs
# 另开一个 PowerShell，保持本机开发服务运行：
node scripts/test-records/run.mjs --label aircraft-browser-simulation -- node apps/web2/scripts/verify-aircraft-map.mjs
```

模拟需要相邻 `Sky-Command/sdk` 已 `npm ci`，以及本机 Chrome；可用环境变量 `AIRCRAFT_BROWSER_CHANNEL=msedge` 改用 Edge。模拟通过正式 v1 手机帧、真实本机网关及实际 Cesium 页面，使用 `map-sim-*` 三个专用设备 ID，不发送飞行/任务命令。脚本仅在测试响应中暴露 viewer 作断言，生产文件不增加测试入口。

手机重建使用 JDK17、Android SDK 35。此电脑使用目录联接 `D:\XJI-Aircraft-Workspace` 指向原工作区，避免 Windows JVM 中文路径类加载问题；不复制或替换源码。

```powershell
Set-Location -LiteralPath 'D:\XJI-Aircraft-Workspace\MSDK-Relay'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true -Djdk.net.unixdomain.tmpdir=D:/XJI-Test-Records/java-tmp -Djava.io.tmpdir=D:/XJI-Test-Records/java-tmp'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
New-Item -ItemType Directory -Force -Path 'D:\XJI-Test-Records\java-tmp' | Out-Null
& ./scripts/Verify-Recorded.ps1 'test' ':app:assembleDebug' '--no-daemon' '--console=plain'
```

其他电脑可使用自己的纯英文工作目录；DJI_API_KEY 只放本地 Gradle 私有配置。正式文档链接和路径按实际机器调整。

## 验证记录

档案根目录 `D:\XJI-Test-Records\2026-10-26`，含失败尝试，未删除旧证据。

| 验证 | 结果 | 记录时间/ID |
|---|---|---|
| 手机适配器/组装/应用单测与 APK | 通过，本次 23 份 XML | `08-44-36-776Z-96bdaeec` |
| 手机完整 Gradle test | 通过，本次 197 份 XML；现存 202 份 XML 合计 1510 项、0 失败 | `08-49-12-296Z-1243e793` |
| 定位/生产适配/工作流回归 | 154 项通过 | `09-20-43-940Z-1f66c75f` |
| SDK 基础测试 | 25 项通过 | `08-39-58-863Z-2cf2f543` |
| SDK 0.2.19 构建/打包 | 通过 | `09-21-20-428Z-1f96a578`、`09-22-24-797Z-c70e04c3` |
| 网站回归 | 54 组 253 项通过 | 最终 `10-24-50-061Z-cde99587` |
| 网站最终生产构建 | 通过，含类型与资源检查 | `10-24-50-061Z-757fb60c` |
| 浏览器模拟 | 18 项通过，截图和 JSON 已归档 | `09-48-58-041Z-e64fc76e-77a0-4094-96aa-2fc3c22b6ca7` |

浏览器覆盖：无航线多机显示、缺坐标不建点、平面高度、只读点击保留任务对象、移动、航向、相机旋转、航线实体清除隔离、其他遥测不刷新旧位置、过期恢复、缺航向圆点、断连保留、重连、选中高亮、工作区切换、快照卡住时过期，以及零飞控/任务命令。截图中的定位数据来自模拟手机；影像/白模不等于定位精度验收。

## 尚未通过及真机待办

全仓 Sky 测试执行过 2205 项，2200 通过、4 失败、1 跳过。相关定位测试已通过，完整门禁仍未通过：

1. 跨端第二轮航线收到 INTERRUPTED 后等待 failed 超时，单独复核仍失败；本次未改航线状态逻辑，尚未修复。
2. 有限集合基线摘要不匹配，包含本次新增的 5 个私有定位观察 Key，且移除这 5 项后仍有既有漂移。已保存对比证据 `09-45-07-d62674cb`；未擅自覆盖要求人工审阅的基线。
3. 既有 Electron preload 缺少 `photo-fetch-stop`，与 DesktopShell 清单不一致；网站使用自己的桥，此次未修改 Electron 入口。

设备事实摘要曾在全仓高并发运行中超时，单独复核 7 项已通过（`09-27-22-d56e327b`）。上述失败不作“全部通过”报告。

拿到飞机后需实测：Mini 4 Pro 固件与 MSDK Key 实际可用性、静止时定位回调频率、经纬度精度、机头磁航向与地图北向偏差、罗盘/GPS 异常、RC/飞机/手机断连恢复，以及高度基准。当前位置更新时间是本机接收时钟减手机采样年龄的估计，不能据此推导网络延迟；没有验证云端登录全流程或真实飞行。

## Code Review

自查数据有效性、白名单传递、会话隔离、重复采样新鲜度、空值兼容、点击只读、地图图层隔离、资源释放、源码重建、凭据和分支边界。修复了实际联调发现的快照层级、漏传导航字段、原航点进度丢失、图标初始方向和白模拾取问题。未发现需阻止本次定位功能交付的新增高风险问题；完整仓库失败和硬件验证缺口按上节保留，交付不代表获准直接飞行上线。
