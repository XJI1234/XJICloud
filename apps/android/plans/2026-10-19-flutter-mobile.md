# XJICloud Flutter Mobile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 若明确选择委派，可使用 superpowers:subagent-driven-development；每个交付仍需验证与 Review。

**Goal:** 在 `apps/android` 建设适合 Phone/Pad 的 Flutter 客户端，复用现有后端并通过 Android Native3DGS SDK 完成普通模型浏览。

**Architecture:** 五个 BC 使用 Domain / Application / Infrastructure / Presentation 四层，唯一组合根负责装配。原生渲染由本地 Flutter 插件封装 Kotlin AAR 与 SurfaceView，网络与文件缓存由业务仓储管理；iOS 以后替换平台实现。

**Tech Stack:** 本机 Flutter 3.47.7、Dart 3.13.5、JDK 21、Android API 29+；Native3DGS AAR 0.2.2 / arm64 / Vulkan 1.1；Flutter SDK controller。go_router、dio、secure storage、系统文件选择、path_provider 按后续阶段需要引入。

**Spec:** [架构](../docs/ARCHITECTURE.md)、[UI](../docs/MOBILE_UI.md)、[API](../docs/API_REUSE.md)、[原生桥](../docs/NATIVE3DGS.md)、[环境证据](../docs/ENVIRONMENT.md)。2026-10-19 用户要求按计划实施，随后确认本轮先完成可构建 P0，无真机；后续阶段仍以真机验收为进入条件。

## Global Constraints

- 产品代码仅在 `apps/android`；不修改现有 backend/web2/admin/Spark/Rust。根知识库只在架构真正落地时同步事实。
- 本机保留已安装 Flutter 3.47.7 / Dart 3.13.5；原 3.38.9 基线来自另一台机器，不执行全局降级。Android minSdk 29；发布 arm64-v8a；Vulkan 1.1。
- 消费 SDK 使用 JDK 21、Java/Kotlin target 21；本机 Flutter 模板 Gradle 9.3.1 / AGP 9.1.0 / Kotlin 2.4.0 / compileSdk 36 / targetSdk 36 / NDK 28.2.13676358。AAR 消费兼容必须实际构建，SDK 源码编译 NDK 27 不与此混淆。
- SDK 固定 0.2.2 与 [NATIVE3DGS.md](../docs/NATIVE3DGS.md) 中的 ZIP/AAR hash；许可证随包保留。
- UI 浅色壳 + 暗画布、accent `#3d6b8a`；Phone/Pad 四 Tab + 中央上传按钮，断点 600/840dp。
- 不做航线、高级编辑、搜索索引、双屏、占位功能；截图在 PixelCopy 验证后单独开放。
- 首版前台上传、后台暂停、回前台查状态；图片签名过期/冷启动不能承诺无缝续传；训练在服务端继续。
- 不新增后端 API；用户 Bearer 与 OSS 客户端严格分离；不迁模型存储、不发明 refresh token。
- 所有路径以下均相对 `apps/android`；任务中的新目录、类、方法是拟创建契约，不是现有实现。

## 交付顺序与停止条件

| 阶段 | 任务 | 可审查交付 | 进入下阶段的证据 |
|---|---|---|---|
| P0 原生可行性 | 1 | Flutter 工程 + 独立本地 PLY/SPZ 原生测试页 | 兼容 arm64 真机首帧、触控、Surface/关闭；未通过则先解决桥接 |
| P1 UI/会话 | 2–3 | 主题、Phone/Pad 壳、登录注册、我的基础页 | 真实登录/验证码、401 清会话、响应式测试 |
| P2 工程/资产 | 4–5 | 工程管理、模型分片、下载/缓存 | 实际 API + OSS/本地盘协议验证、账号隔离 |
| P3 上传训练 | 6 | 图片上传、暂停恢复、任务观看 | 真实 OSS + mock Worker 流、complete 不确定分支 |
| P4 完整体验 | 7–8 | 云模型打开、最终 UI/生命周期/异常闭环 | 真机 E2E、审查、profile 数据与明确未覆盖项 |

当前执行范围为可构建 P0；进度与证据见 [P0 验证记录](../docs/verification/p0.md)。自动化通过与真机验收分开标记；缺少真机时 Task 1 和 P0 不标整体完成，Task 2–8 保持未执行。每个任务通过后记录命令、设备/样本、结果、Review；只有无 High/Critical 才可标完成。提交建议按任务分开，执行前核对工作区，不夹带他人变更。

## Task 1：可构建基线与原生渲染纵向验证

**Files**

- Create: `pubspec.yaml`、`pubspec.lock`、`analysis_options.yaml`、`.gitignore`、`lib/main.dart`、`lib/app/engine_probe_page.dart`。
- Create by Flutter: `android/`；Modify: `android/app/build.gradle.kts`、`android/app/src/main/AndroidManifest.xml`。
- Create: `packages/native3dgs_flutter/pubspec.yaml`、`lib/native3dgs_view.dart`、`lib/src/native3dgs_controller.dart`、`lib/src/native3dgs_state.dart`（后三者在插件内）。
- Create under plugin `android/src/main/kotlin/com/xjicloud/native3dgs/`: `Native3dgsPlugin.kt`、`Native3dgsPlatformView.kt`、`EngineSession.kt`。
- Create: `scripts/fetch-native3dgs.ps1`、`vendor/native3dgs/0.2.2/` 的固定发布依赖和许可证。
- Test: `test/native3dgs_protocol_test.dart`、插件 `android/src/test/.../EngineSessionTest.kt`、`integration_test/engine_probe_test.dart`。

**Interfaces**

- 插件输出：`Native3dgsView`、`Native3dgsController`（open/cancel/close/camera + state stream）。所有实例显式 viewId/sessionId；初始化先返回 capability，不支持环境不创建 engine。
- native 接收 `content://` URI，返回 operationId/requestId；状态含 activeRequestId、phase、error、surfaceGeneration，失败保留上一个可用场景。
- 插件不依赖 domain、HTTP 或工程 ID；Task 7 再提供业务 port adapter。

- [x] **1. 建 Flutter 工程。** 在 repo 根执行 `flutter create --platforms=android --org com.xjicloud --project-name xjicloud_mobile apps/android`，保留已有文档；对比 create diff，不覆盖 AGENTS/README。生成后显式 minSdk=29、Java/Kotlin=21，提交 Flutter lockfile；插件为本地 path dependency，预览阶段 applicationId 为 `com.xjicloud.mobile`（最终发布标识在发布阶段核定）。
- [x] **2. 固定 SDK 消费。** 下载脚本验证 ZIP + manifest 每一文件 + AAR hash 后才落 vendor；插件通过单一 AAR 依赖加入，显式 coroutines-android 1.9.0。确认 AAR 自动合并非导出的 decoder Service，APK 中每个 .so 只有一份，检查 arm64 和 16KB 页兼容性，不用重复拷 lib/。
- [x] **3. 先写桥接失败测试。** 覆盖 disposed 忽略事件、旧 operationId 不覆盖新请求、重复 surfaceDestroyed 不重复 detach、open coroutine 取消与 requestId 取消的不同路径。运行 Dart 测试及 Kotlin test，应先因未实现而失败；不以 mock 测试声称引擎真实成功。
- [x] **4. 实现最小引擎页与宿主。** 仅本机打开/取消/关闭、orbit/pinch/fit；Hybrid Composition + SurfaceView，创建有效 Surface 才 attach，使用返回 generation resize/detach；后台 coroutine closeAndWait，热输入不跨 Dart 逐事件往返。例序严格采用 NATIVE3DGS §3，不自行发明 SDK 方法。
- [ ] **5. 构建并真机走路径。** `flutter analyze`、`flutter test`、`flutter build apk --debug --target-platform android-arm64`；`flutter run -d <arm64-device-id>` 打开有效 PLY/SPZ、取消第二模型、旋转/锁屏/切前后台/连续退出。再用 profile 模式查看触控与浮层合成。不兼容或无真机则记录 P0 未通过，不用模拟器/假数据替代。
- [x] **6. Review 并保存证据。** 检查无主线程 wait、FD/Surface/collector 泄漏、原始异常和旧事务污染；提交建议 `feat(android): establish Flutter native viewer bridge`。测试页留为开发入口，P4 不暴露到正式导航。

## Task 2：共享内核、主题与自适应底栏

**Files**

- Create: `lib/shared/domain/result.dart`、`domain_error.dart`。
- Create: `lib/app/{app.dart,app_container.dart,create_container.dart,router.dart}`。
- Create: `lib/presentation/theme/{app_theme.dart,app_tokens.dart}`、`shell/{mobile_shell.dart,adaptive_content.dart}`、`widgets/{app_button.dart,app_sheet.dart}`。
- Create: `lib/shared/l10n/{app_zh.arb,app_en.arb}`、`l10n.yaml`、`assets/brand/logo_nw.png`。
- Test: `test/architecture/layer_rules_test.dart`、`test/presentation/{mobile_shell_test.dart,adaptive_content_test.dart}`。

**Interfaces**

- `Result<T>` 为 sealed `Success<T>(T value)` / `Failure<T>(DomainError error)`，`DomainError` 为稳定 code + 可选脱敏 details。
- `AppContainer` 仅暴露 ports/use cases/controller factories，`createContainer` 是唯一组装点。
- `AdaptiveContent` 依宽度 `<600`、`600..839`、`>=840` 构建同状态不同布局。

先写这些有效断言，再实现对应状态和布局：

```dart
test('Result keeps failure distinct from an empty list', () {
  final Result<List<String>> result = Failure(DomainError('NETWORK'));
  expect(result, isA<Failure<List<String>>>());
});
```

- [ ] **1. 写并运行失败测试。** 上述 Result 用例；360/800/1280dp 与断点布局；四 Tab 顺序、中央上传动作不改变 Tab index、二次进入保留滚动；200% 字号无 overflow。`flutter test test/presentation test/architecture`，先验证失败原因。
- [ ] **2. 实现共享内核与壳。** ThemeExtension 使用 UI 文档精确 token；有状态路由分支管理四目的地，上传为独立 flow。创建 architecture import 门禁；不为主页/我的新增 BC。纯静态壳允许测试内容，正式路由不能把假数据作为生产功能。
- [ ] **3. 实现登录/上传/表单公共控件。** 复制透明 logo_nw，48dp 热区、SafeArea、键盘滚动、l10n、减少动画；不加入搜索/双屏/编辑占位。
- [ ] **4. 验证并 Review。** `flutter analyze`、`flutter test`、arm64 debug build；模拟器操作四 Tab、上传打开/返回、旋转与字体放大。审查内层依赖/重复容器、没有业务 IO 漏入 Widget。提交建议 `feat(android): add adaptive shell and shared design system`。

## Task 3：Identity 与我的基础页

**Files**

- Create under `lib/features/identity/`: `domain/{user_session.dart,captcha_challenge.dart,auth_repository.dart,session_store.dart}`、`application/{login.dart,register.dart,restore_session.dart}`、`infrastructure/{auth_dto.dart,auth_mapper.dart,http_auth_repository.dart,secure_session_store.dart}`、`presentation/{auth_controller.dart,login_page.dart}`。
- Create: `lib/shared/infrastructure/api_client.dart`、`lib/app/session_coordinator.dart`、`lib/presentation/pages/my_page.dart`。
- Test: `test/features/identity/{session_policy_test.dart,auth_use_cases_test.dart,auth_mapper_test.dart,login_page_test.dart}`、`integration_test/auth_flow_test.dart`。

**Interfaces**

- `AuthRepository.login/register` → `Future<Result<UserSession>>`；`getCaptcha` → `Future<Result<CaptchaChallenge>>`；`needCaptcha(String username)` → `Future<Result<bool>>`。
- `SessionStore.read/write/clear` 管理 secure storage。`SessionCoordinator.invalidate` 幂等清登录会话并通知 workspace/传输/watcher/viewer。
- `UserSession` 含 userId/username/displayName/token/UTC expiresAt；`CaptchaChallenge` 含 key/图片内容，不加入服务端未返回的 expires 字段。

- [ ] **1. 固定 fixture 与失败测试。** 按真实 AuthResponse/CaptchaResponse 写 JSON：captchaKey/captchaImage；覆盖注册必验证码、登录 needCaptcha、错误验证码刷新、JWT exp 到期、网络失败不清 token、401 一次清理并失效迟到回调。`flutter test test/features/identity`。
- [ ] **2. 实现分层与网络。** JSON wrapper 只在 ApiClient 解包，error mapping 保留错误；API Bearer 拦截仅作用于 API origin，拒绝跨 origin 转发凭证。DTO 不传入 UI；安全存储 token，偏好中不写密码/token。
- [ ] **3. 实现白底表单与路由守卫。** UI 文档登录/注册/验证码/键盘避让/动画。注册成功返回登录；我的只展示 session 信息、语言/帮助/退出。清会话返回后无法系统返回到受保护页；保留受控站内目标路由。
- [ ] **4. 实际联调与 Review。** 后端 + Redis 启动后完成注册、登录、错误验证码、断网、过期/401、退出重登；Phone 与 Pad 均测。跑 analyze/test/build；未启动服务则明确此任务联调门槛未过。提交建议 `feat(android): reuse identity APIs with secure sessions`。

## Task 4：Project、主页与工程详情

**Files**

- Create under `lib/features/project/`: `domain/{project.dart,project_repository.dart,workspace_persistence.dart,recent_projects_policy.dart}`、`application/{load_projects.dart,mutate_project.dart,open_project.dart}`、`infrastructure/{project_mapper.dart,http_project_repository.dart,local_workspace_persistence.dart}`、`presentation/{workspace_controller.dart,home_page.dart,project_list_page.dart,project_detail_page.dart}`。
- Test: `test/features/project/{recent_projects_test.dart,project_use_cases_test.dart,project_mapper_test.dart,project_pages_test.dart}`。

**Interfaces**

- `ProjectRepository.list()` → `Future<Result<List<Project>>>`；create(name,description)、update(id,name,description) → `Future<Result<Project>>`；delete(id) → `Future<Result<void>>`。
- Workspace 保存 userId 下 activeProjectId 和最多 5 项 recent；`openProject` 先验证拥有/可用列表，删除成功后清理 workspace。主页只消费组合后的只读 VM。

- [ ] **1. 先写领域/用例失败测试。** 最近去重/移首/截断5项、跨账号隔离、删除当前工程清 active、删除失败不清本地状态；断网空列表与失败不同。UI 测试 Phone 详情跳转与 Pad 同一选择分栏。
- [ ] **2. 实现 CRUD 与 workspace。** 对接实际 GET/POST/PATCH/DELETE，不虚构单工程 GET。已有完整列表用于定位工程，刷新确认可用；无效本地最近 ID 移除或标不可用。传输固定原 projectId，不因 workspace 改变迁移归属。
- [ ] **3. 实现主页/详情与全部状态。** 名称/描述编辑，确认删除；模型/任务区由后续 BC controller 组合，尚未实现时开发分支不发布可点击空入口。200% 字号/Pad 窄窗保留选择。
- [ ] **4. 验证与 Review。** analyze/test/build；真实创建→编辑→设当前→最近→切账号→删除→重启检查；关注陈旧请求、越权错误显示与用户草稿。提交建议 `feat(android): add project workspace and responsive management`。

## Task 5：Model-asset 上传、下载与缓存

**Files**

- Create under `lib/features/model_asset/`: `domain/{model_asset.dart,model_revision.dart,model_asset_repository.dart,model_cache_port.dart,local_file_port.dart,model_policy.dart}`、`application/{upload_model.dart,download_model.dart,delete_model.dart,manage_cache.dart}`、`infrastructure/{model_mapper.dart,http_model_asset_repository.dart,file_model_cache.dart,android_local_file_adapter.dart}`、`presentation/{model_assets_controller.dart,model_list.dart,cache_page.dart}`。
- Test: `test/features/model_asset/{model_policy_test.dart,chunk_upload_test.dart,model_cache_test.dart,model_mapper_test.dart}`。

**Interfaces**

- 仓储：list(projectId)、createUploadSession、getUploadSession、putChunk、completeUpload、abortUpload、downloadToFile、delete；typed Result 与可取消/进度参数。
- `LocalModelRef` 描述内部资源标识、fileName/size，URI/路径通过 infra 解析。缓存 key=userId/modelId/updatedAt/sizeBytes，lease 防清理正在使用的文件。

- [ ] **1. 写失败测试。** PLY/SPZ 与2GB边界、0字节、chunk Content-Range 尾块、服务端 receivedBytes 优先、重传范围、来源变化、complete 幂等；缓存修订/用户不命中、下载取消不提交 `.part`、磁盘满、活动 lease 不可清理。
- [ ] **2. 实现本地文件与流。** 系统 SAF，无真实路径/不可 seek 时临时副本；记录授权/指纹，丢失授权可重选且身份校验。文件流读写，不全量跨 Dart/Kotlin；分片用服务端 chunkSizeBytes，恢复先 GET。
- [ ] **3. 实现 API/缓存/管理 UI。** Bearer 下载到私有缓存后原子提交；回前台列表刷新检查修订；删除云模型成功后失效缓存，清缓存只作用当前账号并显示释放字节。“我的”增加实际缓存页面。
- [ ] **4. 验证与 Review。** 实际上传/暂停/续传/complete丢响应/删除、下载取消、切账号、缓存命中/失效；analyze/test/build，审查文件路径/lease/取消竞争。提交建议 `feat(android): add resumable model assets and private cache`。

## Task 6：Dataset-training 与前台恢复

**Files**

- Create under `lib/features/dataset_training/`: `domain/{training_job.dart,dataset_manifest.dart,selected_image.dart,training_job_repository.dart,object_storage_port.dart,job_event_port.dart,upload_checkpoint_port.dart,dataset_archive_policy.dart,job_policy.dart}`、`application/{prepare_dataset.dart,submit_dataset.dart,resume_dataset.dart,watch_job.dart,mutate_job.dart}`、`infrastructure/{job_mapper.dart,http_training_job_repository.dart,oss_upload_adapter.dart,sse_job_event_adapter.dart,local_upload_checkpoint.dart}`、`presentation/{upload_controller.dart,upload_flow_page.dart,training_job_panel.dart}`。
- Test: `test/features/dataset_training/{dataset_archive_test.dart,submit_dataset_test.dart,resume_dataset_test.dart,sse_parser_test.dart,job_policy_test.dart}`、`integration_test/upload_training_flow_test.dart`。

**Interfaces**

- prepare 输入 `SelectedImage` 元数据列表，返回 `Result<DatasetManifest>`；manifest 和原始内容来源通过稳定标识关联。
- `TrainingJobRepository` 提供 create/complete/list/get/cancel/delete；没有用户 retry。ObjectStoragePort 仅签名 PUT，不携带 Bearer；JobEventPort 输出已映射 progress，不输出 raw SSE/DTO。
- 本地 checkpoint 存用户/工程/job/manifest/确认成功项；URL 仅内存，重启不能恢复 URL。应用生命周期协调 pause/resume，UI 本地 paused 与服务端 UPLOADING 分开。

示例先写不变量测试，定义 helper fixture 于同文件，再实现 policy：

```dart
test('archive rejects more than four-digit image capacity', () {
  final images = List.generate(10000, (i) => SelectedImage(
    sourceId: '$i', name: '$i.jpg', relativeName: '$i.jpg',
    contentType: 'image/jpeg', sizeBytes: 1,
  ));
  expect(prepareDataset(images), isA<Failure<DatasetManifest>>());
});
```

- [ ] **1. 先写失败测试。** 1/9999/10000图边界、MIME/HEIC/空图、同名排序、manifest v1、manifest URL 去重；第N张失败不得 complete；签名403需重提；后台当前PUT未知项重传；complete超时GET若QUEUED不重发；create超时不自动新建；SSE半帧/UTF8/CRLF/多行/重复连接/401、GET与订阅间漏终态由定期查询补齐、迟到快照不覆盖新事件、终态事件后GET完整产物/错误、CANCELLED→RUNNING/COMPLETED校正。
- [ ] **2. 实现逻辑归档和上传。** 默认顺序传图（限制内存），逐字节总进度；manifest 只传一次，全部成功才 complete。最多按 1/2/4 秒退避重试可重放 PUT，401/403不无限重试；非幂等 POST 必须查询再协调。源图不 ZIP、不自动转码。
- [ ] **3. 实现暂停恢复与用户提示。** paused/hidden 取消当前发送，resumed 先查 job；来源/URL有效才同 job 继续，冷启动/过期走取消旧任务与重新提交确认。会话到期取消任务协调、清token，不能将旧回调写到新账号。系统文件选择的 inactive 不直接取消流程。
- [ ] **4. 实现任务 watcher/操作与 UI。** 前台立即GET并启SSE，健康流仍15秒GET校正，失败5秒可取消轮询；会话代际/观察序号屏蔽迟到结果。COMPLETED/FAILED事件后GET完整JobResponse成功才停止；CANCELLED停SSE但前台有订阅者时保留15秒GET，允许Worker回写后恢复观察；离页/后台停止，重进必查。多个页面共享一job watcher，取消/终态删除按真实policy。产物下载URL失效展示当前服务端限制，训练产物不伪装ModelAsset。
- [ ] **5. 实际联调与 Review。** 接真实后端/Redis/OSS/mock Worker，走选图→上传→入队→进度→完成→产物下载；后台/断网/权限失效/complete丢响应/取消均记录。跑analyze/test/build，审查OSS不带JWT、无签名日志、无重复任务。提交建议 `feat(android): add foreground dataset training workflow`。

## Task 7：把云/本机模型接入正式 Viewer

**Files**

- Create under `lib/features/viewer/`: `domain/{model_source.dart,viewer_capabilities.dart,model_source_port.dart,viewer_engine_port.dart}`、`application/{open_model.dart,viewer_session.dart}`、`infrastructure/{native3dgs_engine_adapter.dart,model_source_adapter.dart,viewer_state_mapper.dart}`、`presentation/{viewer_controller.dart,viewer_page.dart,viewer_tools.dart}`。
- Modify: `lib/app/create_container.dart`、正式路由，移除正式导航到 engine_probe_page 的入口。
- Test: `test/features/viewer/{viewer_session_test.dart,viewer_state_mapper_test.dart,viewer_page_test.dart}`、`integration_test/viewer_lifecycle_test.dart`。

**Interfaces**

- `ModelSourcePort.resolve(source)` → `Future<Result<LocalModelRef>>`，由组合根注入 Model-asset 下载/租用资源能力。
- `ViewerEnginePort` 负责能力、open/cancel/close/相机和映射状态；不能暴露 SDK class/Uri/PlatformException 给 Application。
- ViewerSession 管宿主 operation token 与资源 lease，下载/解码/上传/首帧分别显示，activeRequestId 匹配才切换活动场景。

- [ ] **1. 先写事务失败测试。** A→B失败保留A；B→C迟到B不覆盖C；关闭/登出使迟到下载失效并释放lease；未知旧config用fit不写回；无项目也可打开本机；不支持平台给明确结果。
- [ ] **2. 实现云/本机统一入口。** 指定工程模型先仓储鉴权下载，再本地Uri；从工程模型行进入Viewer携modelId/source，未登录路由保护，不存带token深链。每个会话一个active模型，无Web高级编辑。
- [ ] **3. 完成手势/工具与全屏。** 固定/自由、适配/重置/Y翻转、缩放、取消/关闭；Phone工具Sheet，Pad浮层；隐藏Tab暂停Surface，重排不重复engine。先不显示未验证截图按钮。
- [ ] **4. 真机验证与 Review。** debug + profile完整生命周期矩阵，连续20次打开/关闭、服务端更新缓存失效、旋转/切Tab/锁屏、绘制浮层与手势。analyze/test/build，记录设备/样本/实际帧间隔/内存，提交建议 `feat(android): connect cloud assets to native viewer`。

## Task 8：Phone/Pad 收口与交付审查

**Files**

- Create: `integration_test/mobile_journey_test.dart`、`test/presentation/accessibility_test.dart`、`docs/verification/android-device-matrix.md`、`docs/verification/mobile-journey.md`。
- Modify: 本任务实际发现问题的 `lib/`/插件文件；同步 README/ENVIRONMENT 的实现状态。真正新增客户端落地后更新根 `docs/AGENT_CONTEXT.md` 与 README 的架构入口，只记已实现事实。

- [ ] **1. 自动化门禁。** `dart format --output=none --set-exit-if-changed lib test integration_test packages/native3dgs_flutter/lib`、`flutter analyze`、`flutter test`、插件Kotlin测试、arm64 debug APK；正式发布前再release签名构建和bundle验证，签名材料不入Git。
- [ ] **2. 用户路径。** 注册→登录→建工程→选图→前台上传→后台暂停→返回继续→训练→完成→下载/本地打开；另走模型分片→工程查看→缓存清理→退出→另一账号。Phone和Pad分别记录步骤/预期/实际，模拟器不替代arm64渲染。
- [ ] **3. 异常与无障碍。** 空工程、无模型、无权限、不支持Vulkan、ASCII/损坏PLY、断网、后端500、401、签名403、空间不足、低内存；200%字级/TalkBack/减少动画/预测返回/键盘/600与840断点。高危失败先修再重跑受影响门禁。
- [ ] **4. 发布能力核验。** SDK与Flutter native库的arm64/16KB页打包与加载，在相应设备实际验证；保留license。iOS、截图、持续后台上传、真实训练算法均按事实列未覆盖，不用Android结果外推。
- [ ] **5. 完成Review与交付。** 对照产品范围扫描未迁移功能和无效按钮，审查鉴权/旧请求/文件释放/DDD/测试；明确High/Critical=无或修复条目、设备与未覆盖项。提交建议 `test(android): verify phone tablet and native viewer journeys`。

## 可选后续专项（不计入首版完成）

- PixelCopy截图：真实Surface图像→PNG→系统保存/分享，失败/权限/清理覆盖后开放按钮。
- 后端可靠性：数据集URL补签、create/complete幂等、产物URL刷新、Worker取消确认；另立API契约与Maven/联调验证，不顺手修改。
- iOS：macOS/Xcode、iOS引擎/Metal适配、UIView/Flutter合成、文件授权与安全存储、签名和真机性能；共享业务契约不变。
- 持续后台上传：只有需求变更时增加Android前台服务/通知及iOS对应策略，不把它混进当前“前台暂停”方案。
