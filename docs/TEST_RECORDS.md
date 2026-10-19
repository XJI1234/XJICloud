# 测试档案

2026-10-19 起，真机和自动化验证都保存长期档案。默认位置 **`D:\XJI-Test-Records`**，打开其中的 **`index.html`** 按日期、标签、结果或版本搜索。档案独立于源码和 Temp，无自动删除策略。可以整目录备份到其它磁盘；本地归档本身不等于异地备份。

## 自动化测试

`pnpm test:web2` 和 `pnpm build:web2` 已默认归档，保存完整 stdout/stderr、命令、退出码、提交及工作区差异。构建准备步骤也包含在同一记录中。Sky Command 与 SDK 的标准 test、类型检查和 build 入口也默认归档。本地三个仓库使用同一份记录实现；可通过 `XJI_TEST_RECORDS_RUNNER` 指定 Sky Command 使用的记录入口路径。

Vitest 一次性测试默认增加 JSON 报告，保留每条用例的状态、耗时和失败详情；已有自定义 reporter 时保持原配置。Gradle 入口收集本次产生的 JUnit XML；UP-TO-DATE 只表示使用缓存，不会冒充重新执行。程序真实执行一次，非零退出码保持；归档失败不会伪报通过。

其它仓库或工具在其原工作目录使用统一入口，例如：

```powershell
node D:/Desktop/XJICloud/scripts/test-records/run.mjs --label sky-photo-regression -- node node_modules/vitest/vitest.mjs run tests/photo-inbox-contract.test.ts
node D:/Desktop/XJICloud/scripts/test-records/run.mjs --label sdk-tests -- node node_modules/vitest/vitest.mjs run
```

Windows `.bat`、`.cmd` 需要系统 shell；统一入口不将参数拼成 shell 代码。Gradle 可通过手机仓库的 `scripts/Verify-Recorded.ps1`，它把参数原样传给现有 gradlew.bat。web2 watch 模式会保留整个会话的完整输出，但每次重跑不单独建立档案；请用一次性 test 命令作为验收记录。

## 真机测试

新网关启动自动创建 live 会话，保存用户操作入口与返回、应用状态发布、手机已脱敏 SDK 诊断及浏览器/媒体事件。手机诊断批次必须实际落盘后才确认，已有 `relay-events.ndjson` 镜像仍保留。不会自动发飞行命令、拍摄照片、录制视频或重试失败指令。

持续运行的旧网关可以用 `node scripts/test-records/observe.mjs` 进行只读旁路采集，避免立即重启；旁路只保存定时快照、之后到达的手机诊断和图传日志。**它不能捕获完整控制入口、两次轮询之间的短暂状态，也不证明 SDK 没回调。** manifest 中明确记录覆盖范围。完整接入在新网关启动后生效。

每次具体复测用人工标记补充测试名称、起始条件、期望和实际观察，结论与推断分开：

```powershell
node scripts/test-records/view.mjs mark '<会话目录>' '{"label":"降落复测1","expected":"确认后继续降落","observation":"飞机开始下降","inference":"原因尚未确认","environment":{"ground":"重复花纹","stick":"回中"}}'
node scripts/test-records/view.mjs attach '<会话目录>' 'D:/测试截图.png'
node scripts/test-records/view.mjs attach '<会话目录>' 'D:/测试录像.mp4'
```

录屏、截图、已安装 APK 版本与固件只有实际采集/补充后才属于证据。源码版本不等于已安装 APK 版本，未知字段保持 unknown。手机事件时间属于手机时钟；电脑 receivedAtMs 是接收时间，不相减推导网络延迟。电脑请求到回执耗时不等于飞机开始动作的耗时，也不等于相机到屏幕延迟。

## 访问与比较

```powershell
node scripts/test-records/view.mjs list
node scripts/test-records/view.mjs index
node scripts/test-records/view.mjs show '<会话目录>'
node scripts/test-records/view.mjs compare '<旧会话目录>' '<新会话目录>'
```

索引勾选两次测试即可直接比较归档摘要。compare 命令永久保存最新 JSON 对比结果到根目录 comparisons，含版本/环境、各操作次数与最小/平均/最大回执耗时、失败次数、SDK 事件数量、人工观察和自动化用例新增/移除/状态变化。每个会话的 report.html/summary.json 便于直接打开查看；正在运行的报告可用 show 或 index 刷新，不以旧静态报告代替当前状态。

## 目录与完整性

日期目录内每次会话都有唯一 ID：manifest.json、events.ndjson、health.json、result.json，以及自动化 stdout.log/stderr.log、人工 markers.ndjson、attachments/。JSON 均包含版本号或可由 manifest 的 schemaVersion 识别。

没有 result.json 表示未正常结束或仍在运行，不能当作通过。live 正常退出默认 unverified，需结合人工观察验收。高负载丢弃、过大事件、写入失败和不完整行都会标注，不能把证据缺口解释成“问题没有发生”。未跟踪文件记录哈希（大文件只记大小和缺口），已跟踪源码记录差异；凭据配置和二进制不自动拷入版本差异。
