# eci-manager HTTP Api-Key 配置权威短 spec

> 状态：取代 2026-09-08 设计中的 C3 双钥方案。
> 本 spec 只约束 HTTP Api-Key；ECI/OSS 云当前钥的行为不变。

## 1. 权威来源

- HTTP 鉴权唯一以配置中的 `eci.api-key` 为准；`ECI_API_KEY` 仍只作为配置注入/初始化方式。
- 所有受保护的 REST API 继续使用 `X-Api-Key`。请求头与配置值精确匹配时通过，否则返回 `401`。
- 鉴权范围是应用内 `/api/v1/eci/**`；部署设置非根 `server.servlet.context-path` 时，过滤器仍须拦截该范围，不能因请求 URI 带部署前缀而跳过鉴权。
- HTTP Api-Key 不再运行时热切换。修改密钥必须修改部署配置并重启服务；服务运行期间不接受 API 轮换。
- HTTP Api-Key 不写入 `eci_runtime_config`，也不从该表恢复。服务日志、响应、静态控制台和文档不得包含密钥明文。

## 2. 撤回 C3 双钥能力

C3（当前钥 + 上一把钥及重叠窗口）撤回，不再作为本项目能力。以下接口必须删除，不得保留兼容别名或隐藏入口：

- `PUT /api/v1/eci/credentials/http-api-key`
- `POST /api/v1/eci/credentials/revoke-previous-api-key`

同时删除 HTTP 钥重叠配置 `eci.api-key-overlap-sec` 及其环境变量 `ECI_API_KEY_OVERLAP_SEC`，以及上一把钥、过期时间等对应的运行时落盘和恢复能力。`eci.api-key` 本身保留为服务配置项，但不应与旧运行时行合并或互相覆盖。

## 3. 升级与 H2 兼容边界

- 已有 H2 `eci_runtime_config` 中与 HTTP Api-Key 相关的旧行（当前、上一把、上一把过期时间）一律忽略；它们不能覆盖配置，也不能使旧钥继续获得鉴权。升级不需要为这些行提供迁移读取或新写入。
- 升级前必须把升级时仍在使用的**当前有效 HTTP Api-Key**回写部署配置（例如受保护的 `.env`/secret 注入对应的 `ECI_API_KEY` 或 `eci.api-key`），再停止旧版本并启动新版本。只回写当前钥，不回写已撤销或仅处于旧重叠窗口的钥。
- 回写值不得提交 Git、写入日志或放进静态控制台；升级后用该配置值验证 `X-Api-Key`，不要依赖 H2 旧行。
- 本次变更只影响 HTTP 鉴权。`eci_runtime_config` 中 `current.ECI_OPENAPI`、`current.OSS` 等云当前钥行、云客户端、`GET/PUT /api/v1/eci/runtime-credentials` 和其热切/落盘语义均保持不变；升级不得清空、重置或用 HTTP 配置覆盖云当前钥。

## 4. 控制台

- 删除“HTTP Api-Key”页签/面板，以及轮换、吊销上一把和对应状态展示、请求调用。
- 保留 Api-Key 输入闸门、会话内临时保存和向受保护 API 发送 `X-Api-Key` 的能力；页面不保存或展示密钥到服务端。
- 保留云当前钥的查看、测试和更新操作；这些操作仍按既有鉴权和脱敏约束执行。

## 5. 验收要点

1. 配置值可访问受保护 API；缺失或错误值返回 `401`。
   非根 context path 下相同规则仍成立。
2. H2 中存在不同的旧 HTTP 钥行时，鉴权仍只接受配置值。
3. 两个 C3 端点及重叠配置不再可用，且不会产生 HTTP 钥落盘行。
4. 升级前完成配置回写后，云当前钥及 `runtime-credentials` 行为不受影响。
