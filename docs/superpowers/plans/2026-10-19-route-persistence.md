# 导入航线自动保存实施计划

**Goal:** 导入航线自动保存本机，重启恢复列表，保留原始文件且不恢复设备任务。

**Architecture:** 网页网关外层持久化适配器，使用 SDK 公开 RouteLibrary 校验，SDK 网关维持业务所有权。磁盘原文件按摘要命名，index.json 是恢复的唯一依据。用户已批准此设计，当前会话直接实施，不提交其他未完成工作。

**Tech Stack:** Node.js fs/promises、现有 Sky Command SDK、Vitest、Edge/Playwright。

- [x] 先写 `apps/web2/scripts/sky-route-storage.CONTRACT.md`，定义故障和边界。
- [x] 新增 `route-storage.spec.ts`，用真实 SDK 和临时磁盘验证恢复、原文件、删除、失败隔离，先运行观察失败。
- [x] 实现 `sky-route-storage.mjs`，仅航线变更排队，索引原子替换，读取不完整时保护已有资料。
- [x] 接入 `sky-command-gateway.mjs`，在控制入口就绪前恢复；补充生命周期断言。
- [x] 更新 `sky-bridge.js` 和航线页说明，展示自动保存和恢复失败提示；更新架构文档。
- [x] 运行归档的 web2 全量测试、构建及隔离浏览器路径；审查 diff 的错误回滚、路径安全和非航线操作回归。
- [x] 获取当时安全状态后启用真实网关；用户确认已落地停桨后重启，真实保存状态 ready、手机重新连接。
