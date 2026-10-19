# 本地航线持久化契约

状态：用户已批准导入后自动保存、重启恢复列表；2026-10-19。

## 边界

本模块是网页本地网关的磁盘适配器，通过 SDK `routes` 的公开 `RouteLibrary` 校验原文件，通过 `DesktopUiGateway.invoke` 导入、选择和删除。SDK 航线库仍是会话内存，不修改其契约、分类、上传载荷或飞机行为。

默认保存到 `%LOCALAPPDATA%/Sky Command/routes`，非 Windows 回退用户目录 `.sky-command/routes`；`SKY_COMMAND_ROUTES_DIR` 可指定绝对目录。不得使用 Temp。KML/KMZ 原始字节按 SHA-256 存储，目录索引原子替换；文件名不参与磁盘路径拼接。

## 接口

`await createRouteStorage({ gateway, directory?, onError? })` 在返回前恢复列表，返回 `invoke(method,input)`、`snapshot()`、`close()`。

- `route.import`：复制输入，复用 SDK 完整安全校验；先写原文件和索引，再交原网关；写入失败不向会话导入，不伪报成功。SHA-256 相同复用首次文件名；同名不同内容分别保存。SDK 后续拒绝时回滚索引，原 SDK 回执保真。
- `route.remove`：成功后从索引移除，重启不恢复；SDK 拒绝（例如已分配）时索引不变。为保证落盘失败不改变内存，先提交索引，SDK 拒绝则回滚；回滚失败标为 degraded 并阻止后续磁盘变更。原始文件可保留为未引用文件，绝不扫描未引用文件自动恢复。
- 导入/删除串行执行；其他调用立即交原网关，不等待磁盘队列。关停 `close()` 等待已接收的航线变更完成。
- 启动仅从索引恢复，重新校验文件大小、摘要和 SDK 分类，选中最后成功恢复的航线；不恢复设备分配、上传/任务阶段或任何飞机命令。运行时 routeId 可以改变，索引不存设备或会话 ID。
- 索引 `schemaVersion:1`，每项含 `sha256,fileName,sizeBytes,importedAt`。索引不超过 4 MiB/4096 项，文件不超过 SDK 100 MiB 限制。不跟随索引、原文件或存储根的符号链接。
- 损坏、缺失或 SDK 拒绝的条目明确计数并保留，不自动清空或覆写索引；其余有效条目继续恢复，状态 degraded，阻止导入/删除以避免覆盖未恢复资料。飞控、图传和已有有效航线操作不变。
- `snapshot()` 仅返回 `phase,savedCount,restoredCount,failedCount`；不暴露本机路径或底层异常。网关快照附加 `routeStorage`，界面显示自动保存说明及恢复异常提示。

## 验证

用真实临时目录及真实 SDK 覆盖 KML/KMZ 字节保真、重建恢复、去重、同名不同内容、删除及拒删、校验失败、磁盘不可写、损坏/缺失/符号链接、并发和飞控调用不等待磁盘队列。所有验证归档；完成 web2 测试、构建和浏览器导入/刷新/重建/删除路径。真机网关启用前确认可安全重启。
