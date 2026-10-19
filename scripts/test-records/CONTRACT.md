# 长期测试档案契约

## 职责与边界

保存人工真机测试和自动化验证的可追溯证据，提供离线检索、标记和对比。默认根目录 `D:\XJI-Test-Records`，环境变量 `XJI_TEST_RECORDS_ROOT` 可覆盖。档案不进入 Git，不按容量或日期删除；不改变飞控命令、SDK 回执、照片目录、播放恢复或既有临时日志轮换。

## 接口与数据

- `createSession({ root?, kind, label, metadata? })` 创建日期/UUID 唯一目录，写入 `manifest.json`。返回 `record(kind, data)`（有界、异步、不等待磁盘）、`persist(kind, data)`（落盘成功才返回 true）、`flush()`、`finish(outcome, details)` 和 `directory`。
- 每条 `events.ndjson` 记录含 schemaVersion、sessionId、递增 sequence、电脑 receivedAtMs、kind、data。手机 timestampMillis/runId/sequence 原样保留，不把手机时钟与电脑时钟相减作为网络时延。
- 日期目录使用北京时间，会话 ID 使用明确带 Z 的 UTC 时间。元数据从 Git 根目录枚举文件，子目录执行不得丢失提交信息；个别文件不可读或 diff 过大时只标注该部分缺口，不清空其余版本证据。索引/报告使用串行原子替换，并对 Windows 短暂文件占用作有界异步重试。
- `manifest.json` 记录开始时间、类型、标签、电脑/运行时、代码提交、分支、工作区状态、已跟踪文件差异与未跟踪文件哈希。配置和密钥文件不收集。SDK 包版本和包哈希单独记录。APK/固件未知时写 unknown，不推断等同源码版本。
- `result.json` 记录结束时间、outcome、耗时、丢弃/写入失败计数。运行中写 `health.json`。缺少 result 的会话显示未正常结束/仍在运行，不判通过。
- `record` 最多保留 512 条且总量不超过 8 MiB（包括正在写入批次）；超限丢弃并持久记录缺口。单条超过 2 MiB 时拒绝，不截成貌似完整的事件。写入错误报告至 stderr 和 health；不能阻塞或改变控制调用结果。
- `persist` 失败返回 false。手机 diagnostic-ack 仍须等真正落盘成功，沿用原协议重试/去重，不把排队成功冒充落盘。
- 按键名移除凭据与原始照片/航线字节；二进制只记大小和 SHA-256。SDK 已脱敏诊断保留原始字段与回调含义。归档不自动拍照、录屏、起飞或重试命令。

## 真机接入

每次网关启动自动创建 live 会话；记录用户控制入口请求/返回（关联 operationId、电脑单调时钟耗时）、SDK 诊断批次、每次应用状态发布和图传诊断。原始诊断与归一化应用状态分开标记。GUI 操作结果不等于飞机动作成功；用户观察、推断、期望以 marker 明确分开。刷新/快照轮询不算飞行指令。记录器失败时控制入口继续工作并明确报告采集不完整。

不更改手机；APK 版本由独立只读采集或人工标记补充。无法取得固件/实际画面时声明未采集，不承诺仅靠日志测得相机到屏幕延迟。

## 自动化与访问

`node scripts/test-records/run.mjs --label <名称> -- <可执行文件> <参数...>` 可在任意仓库工作目录运行；保存 stdout/stderr、命令、退出码、信号和当前 Git 状态。真实执行仅一次；失败返回原退出码；归档失败即使测试成功也不能退出 0。命令执行不拼接 shell 字符串。web2 test/build 默认接入，其他工具使用此统一入口，约束写入 AGENTS。

Vitest 无自定义 reporter 时追加 default+json 报告；有自定义 reporter 保持原样。Gradle 包装器收集本次新生成的 JUnit XML，缓存任务不算新执行。比较需区分没有用例报告与零个测试，支持保留用例名、状态、耗时及失败详情。watch 会话保留输出，不能将每次重跑混称为独立验收。

`view.mjs list` 列举日期/版本/结果；`view.mjs show <目录>` 查看；`view.mjs mark <目录> <JSON>` 追加人工观察、期望、结论；`view.mjs attach <目录> <文件>` 复制截图/录像/报告并记哈希；`view.mjs compare <目录A> <目录B>` 输出版本差异、指令耗时统计、SDK 事件计数及人工观察。比较不能将缺少事件当作问题未发生。每个会话目录是独立可复制档案。

## 验证要求

真实临时目录覆盖创建重开、并发会话、串行顺序、超限/失败、敏感字段/二进制、原始回调保真、marker/附件和比较；子进程验证成功、失败、命令不存在、中文/空格路径和输出保留；网关接入验证记录失败不改变原调用结果及 SDK ack 落盘语义；完成 web2 全量测试和构建。真机未重启启用前不得宣称已采集新链路。
