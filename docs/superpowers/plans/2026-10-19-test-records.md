# 长期测试档案实施计划

已确认设计：真机与自动化测试均归档到 `D:\XJI-Test-Records`，先契约后实现。接口、容量与失败语义以 `scripts/test-records/CONTRACT.md` 为准。

1. 在 `apps/web2/src/features/mission-ops/infrastructure/test-records.spec.ts` 添加真实文件系统与子进程测试，先运行看到缺少归档实现失败。
2. `scripts/test-records/archive.mjs` 实现会话、异步有界记录与持久化确认；`environment.mjs` 收集 Git/包版本证据，不将源码版本冒充已安装 APK。
3. `run.mjs` 透传执行参数/输出/退出码并保存结果；`view.mjs` 实现 list/show/mark/attach/compare，在中文和空格路径上验证。
4. `apps/web2/scripts/sky-test-recording.mjs` 将既有 SDK diagnosticSink、状态订阅、入口指令与播放记录接入归档；失败隔离，不改变飞行/照片/图传行为。
5. `apps/web2/package.json` 默认 test/build 使用记录入口；AGENTS 与工程手册规定其他仓库/工具测试通过统一入口运行，补充用户访问说明。
6. 用归档入口运行新增测试、web2 全量测试及构建，审查差异。启动只读旁路采集当前网关的状态与已有手机日志，避免为启用记录打断运行中的连接；旁路覆盖范围明确记入 manifest。新网关集成在下次启动自动启用。

自审：归档目录独立且不轮换；未知和失败不写成通过；电脑/手机时间域区分；二进制不进入日志；自动化非零退出码保留；结果索引由每个 manifest/result 重建，无共享索引竞争。
