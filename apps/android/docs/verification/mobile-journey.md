# Android 用户路径验收

2026-10-20 验收记录。正式 Viewer、双账号设备集成与最终代码审查已完成；本文不代表所有服务、硬件及发行门槛已通过。详细分阶段证据见 [设备矩阵](android-device-matrix.md)、[P0](p0.md)、[P1](p1.md)、[最终审查](android-final-review.md)。

## 环境与证据边界

- Lenovo TB710FU，Android 16，SM8650Q / Adreno 750，arm64，4096-byte 页；普通 APK 仅配置 HTTPS API origin。
- Phone 布局在同一平板临时 432dp 窗口验证，不等同独立手机/GPU 硬件覆盖。Pad 使用原物理屏幕和密度。
- 操作只使用已授权测试账户，创建独有测试工程、模型和任务；最终只清理本轮精确 ID。凭据、JWT、签名 URL 和账号截图不入库。
- 每项区分真实用户操作、API 交叉核对、自动化和未覆盖项。源代码存在、构建成功或受控 mock 不能替代设备 E2E。

## 已观察的用户路径

| 路径 | 结果与限制 |
|---|---|
| 注册、验证码、登录、退出 | P1 在本机后端/模拟器实际走通；部署 HTTPS 登录通过。用户报告本机安装登录成功；Agent 最终普通 APK 再次实际登录部署服务，未在部署环境新建账户 |
| 冷启动、Home、语言、帮助、许可证 | 真机通过，P1 证据见设备矩阵 |
| 工程创建→编辑→设当前→主页/最近→重启→删除 | 真机与精确 ID API 核对通过；原有工程未改；另在隔离本机后端执行真实双账号设备旅程 |
| PLY 分片→后台暂停→强制停止→重启→GET 核对续传 | 71MB 传输样本通过，续传以服务端已收字节为准；不用于性能结论 |
| 鉴权下载→缓存逐项/全部清理→云端模型保留 | 真机及本地字节/API 核对通过 |
| 下载取消→重试→云模型删除后缓存失效 | 真机通过，无不完整 `.part` 提交 |
| 断网下载 | 错误与忙状态恢复；Wi-Fi 恢复后一次重试失败，重启后成功。未声称网络无缝恢复 |
| SAF 选择 0-byte 模型 | 真机拒绝、按钮恢复、无 `.part`；2GiB 上限由领域测试覆盖，未传输 2GiB |
| 图片目录 / 多选→四图预览→命名→OSS→入队 | 两种系统选择器均实际走通；四张 12MP 合成图只验证协议，不评估重建质量 |
| 图片上传立即 Home→返回 | API 确认暂停期间同 ID 为 UPLOADING，返回后同 ID QUEUED，无重复创建 |
| 图片上传→进程停止→重启→确认取消旧任务并重传 | 恢复草稿/旧 ID/签名丢失说明；确认后旧 ID CANCELLED、新 ID QUEUED，不自动补签 |
| 任务取消→终态记录删除 | 真机确认操作与 API 精确 ID 一致；取消不承诺 Worker 立即停止 |
| 无当前工程→本机 PLY/SPZ、工程模型行→正式 Viewer | 原生彩球/灰球首帧就绪，正式导航无开发探针入口 |
| 损坏 B、取消加载、切 Tab、全屏系统返回 | 原场景保留，主页无画布穿透，退出全屏恢复底栏 |
| 活动模型租约→清缓存→云修订更新→新首帧→关闭清理 | 活动 279000 字节受保护；更新成红球后旧修订回收；关闭后可清零 |
| Profile：双指、相机、旋转、锁屏、20 次 SAF 开关 | 真机操作通过；内存波动且终值高于起值，不能排除泄漏 |
| 262144-splat 样本连续 600 秒轨道浏览 | 实际 31816 次原生呈现，窗口平均 55.432–55.943 FPS；详见 profile 数据，非百万点/画质认证 |
| 原生生命周期集成测试 | 真机通过 1 项，真实 decoder；错误 B 保留 A、隐藏恢复、相机、20 次重开、退出及 22 次租约释放断言均成功 |

## 自动化与剩余验收

最终修复 `90d124e`：格式检查 173 个文件无变化，analyze 无问题，211 项 Flutter 测试及普通 arm64 debug 构建通过。未改原生代码，保留此前 27 插件 + 6 app JVM 测试通过证据。599/600/839/840dp、200% 字级、键盘、减少动画和表单返回由 widget 测试覆盖；合并模型元数据与带文件名操作语义的修复经过 RED→GREEN。整合审查发现隐藏 Tab 仍持有训练 watcher，新增五项真实 indexed-shell 拓扑回归先失败后通过，修复后独立限定复审关闭该 Important；无未解决 Critical/Important。Viewer Domain 跨 BC 聚合类型耦合为非阻塞后续项。

原生集成测试初次打开 4096-splat 模型至观察 Ready 为 415ms，20 次重开为 121–262ms；这是 debug 模式宿主观测，包含测试泵采样，不等同 GPU 精确阶段计时。普通 profile 开关数据中的 SAF 驱动时间另有含义，不能混用。

真实 TalkBack 服务绑定与绿色焦点框已确认，但 shell 注入 tap 会直接激活控件，不能验证真实手指探索/双击及语音。因此物理 TalkBack 手势/语音仍未验收，语义测试不替代它。Android 系统返回已有实际操作，全屏返回另有集成断言；预测返回的物理手势未单独覆盖。

`integration_test/mobile_journey_test.dart` 在 HA28L0L4 上最终实际通过 1 项（测试体及 teardown 约 45 秒，不含构建安装）。隔离本机 H2/Redis、两个实际注册账户、真实 HTTP/私有缓存和 Native3DGS：创建/编辑工程→返回实际工程列表→鉴权下载 binary Gaussian PLY→真实 SDK Ready→全屏/返回/关闭→清缓存→退出→另一账户看不到前一账号工程/最近/模型/缓存/场景→退出并重新登录→确认删除精确新建工程→保留夹具工程→退出。无 transport/storage/renderer mock；未在部署服务创建第二账户，不称部署双账号 E2E。

失败经过保留在本记录：初次 Flutter 调试连接未启动测试；随后真实 IME/布局异步变化导致编辑或点击失败，测试辅助改为收键盘、等待布局和 hit-test、点击前再核对输入，没有弱化成功断言或盲目重试保存。修复后编辑通过，但夹具配置误用了上传响应 ID；实际 API 列表 ID 不同。鉴权下载实际列表 ID 后 SHA-256 与 binary 夹具一致，仅修正外部测试配置，最终完整旅程通过。此经历不冒充已发现并修复产品保存/API 缺陷。

普通交付 APK：`build/app/outputs/flutter-apk/xjicloud-android-acceptance-arm64-debug.apk`，147047330 字节，SHA-256 `c5802e86d59436f9e581679674c740f862241b512b17cec8f852b5e138463b31`。六个 SO 均为 arm64，三份 SDK/NDK notice 完整，zipalign 通过，无集成测试入口；普通构建仅传部署 HTTPS API origin，不包含外部测试凭据。设备最终恢复此普通产品包，集成测试包不作交付。

复现命令（工作目录 `apps/android`；外部账号和 binary 模型夹具须提前通过真实 API 准备，模型身份取实际列表 ID）：

```powershell
dart format --output=none --set-exit-if-changed lib test integration_test packages/native3dgs_flutter/lib
flutter analyze
flutter test --reporter expanded
flutter test integration_test/viewer_lifecycle_test.dart -d HA28L0L4 --dart-define=API_BASE_URL=https://cloud.xji.org.cn --reporter expanded
# 隔离本机后端/Redis及外部配置；内容字段见测试文件头，不能提交该文件
adb -s HA28L0L4 reverse tcp:8080 tcp:8080
flutter test integration_test/mobile_journey_test.dart -d HA28L0L4 --dart-define-from-file=<external-test-config.json> --reporter expanded
adb -s HA28L0L4 reverse --remove tcp:8080
# 测试后必须重新构建普通入口，只传 API 地址
flutter build apk --debug --target-platform android-arm64 --dart-define=API_BASE_URL=https://cloud.xji.org.cn
```

## 已知服务与平台限制

- 部署 Worker 尚未观察领取/完成任务，不能宣称训练完成及真实产物下载 E2E 通过。当前 mock 源码生成 ASCII 三点占位 PLY，不是有效 Gaussian 模型；COMPLETED 不代表可渲染或重建质量。
- 部署无效 Bearer 实际返回 403；exp/401 清会话由受控测试覆盖，不能写成部署 401 已测。
- 模型 complete 非幂等，响应 ID 与实际列表 ID 可不同；客户端刷新后由用户选择实际行，未修改后端。
- 删除任务/工程只清记录、队列及本地模型；用户 API 不提供 OSS 对象物理删除。记录不可访问不代表 bucket 字节已删除。
- 固定 SDK decoder ELF 只支持 4KB 对齐，16KB 页环境明确拒绝；没有相应设备或跨 GPU 验证。
- 固定 SDK 无背景色公开接口，实际白画布与暗画布设计存在明确偏差，暗工具浮层不等同暗画布通过。
- 低内存、磁盘满、权限撤销、decoder 进程被杀等物理故障尚未逐项注入；错误策略测试与实际故障注入分开记录。
- iOS、PixelCopy 截图、持续后台上传、真实训练算法、生产签名发行均未覆盖。
