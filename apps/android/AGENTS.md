# 移动客户端维护规则

先读根目录 AGENTS、`docs/TEAM_ENGINEERING.md`、`docs/AGENT_CONTEXT.md`，再读本目录 README 与设计文档。当前仅有计划，不能把计划中的目录或 API 当作已实现。

- 只改 `apps/android`；变更已落地的跨端架构时再同步根知识库。不为移动端重构 web2，不修改 `apps/web`、`packages/spark`、`rust/`。
- Flutter 的业务 Domain 为纯 Dart：禁止 Flutter、Dio、平台通道、文件系统、DTO。Application 只依赖 Domain 和 port；Infrastructure 实现 port 并映射外部错误。
- Presentation 仅调 controller/use case，不直接访问 HTTP、DTO、mapper 或平台通道。仓库装配仅在 `lib/app/create_container.dart`。
- API DTO 只在 Infrastructure；返回 sealed `Result<T>`，错误不能变成成功空数据，也不能作为原始异常穿入 UI。
- 原生 SDK 不属于 Domain；Kotlin Surface/引擎生命周期位于本地 Flutter 插件，Dart bridge 位于 Viewer Infrastructure，原生视图 Widget 位于插件视图边界。
- 图片数据集走 OSS；工程 PLY/SPZ 走后端本地盘。用户 Bearer 不能发送给 OSS 或写入查看器 URL、日志。
- 不臆造 refresh token、图片 URL 补签、用户训练重试、云端全局模型列表等接口。
- 浅色壳、强调色 `#3d6b8a`、暗 3D 画布；Phone/Pad 都保留四底栏入口和中央上传。禁止新增占位按钮。
- Android 渲染发布仅 arm64；模拟器测试必须明确标注渲染未覆盖，禁止用 mock 冒充 SDK 测试。
- 每阶段实际运行 `flutter analyze`、`flutter test`、`flutter build apk --debug --target-platform android-arm64`；本地插件另跑 Kotlin/仪器测试。UI 必须走用户操作路径或明确记录未做设备 E2E。
- Flutter 工程创建时提交 `pubspec.lock`；忽略 `.dart_tool/`、`build/`、`local.properties`、签名文件。不得把机器绝对路径写入工程配置。
- 完成前 review 本次 diff 的正确性、鉴权、恢复/取消竞争、资源释放、DDD 与测试。回复必须带 `## 验证`、`## Code Review`，High/Critical 先修。
