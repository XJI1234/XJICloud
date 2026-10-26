# Android 客户端最终审查

日期：2026-10-20。范围：`cf49ecb..4d60d92` 的 Tasks 4–8，随后唯一修复范围 `4d60d92..90d124e`。各阶段均有实现者自审与独立规格/质量审查，最终整合由独立 reviewer 审查完整 diff，之后限定复审修复及回归。

## 结论

客户端代码通过；没有未解决 Critical/Important。服务与发行验收仍存在明确缺口，不能将此结论解释为全计划硬件/端到端验收通过。

| 发现 | 处置与证据 |
|---|---|
| Important：隐藏 indexed-shell Tab 的 ModalRoute 仍 current，训练 SSE/GET 未停止 | `90d124e` 合并 route-current 与继承 TickerMode 可见性；保留共享消费者引用计数。主页/工程两个位置，健康/降级及另一个可见消费者五项回归，修复前五项失败，修复后聚焦 13 项与全量 211 项通过；独立复审关闭 |
| Minor：Viewer Domain 嵌入 ModelAsset/TrainingOutputRef 外部 BC 聚合 | 明确保留为非阻塞架构后续，未发现运行时/安全缺陷。将来在 Application 边界转换最小身份数据，须保留现有来源、修订及租约契约；本轮不扩大重构 |
| 真机 mobile journey 保存/点击不稳定 | 收起真实 IME、等 inset 与布局稳定、确认 hit-test、点击前核对输入；保持原始成功断言，无盲目重试保存。随后真实编辑通过；夹具上传响应 ID 与实际列表 ID 不同，只修正外部配置并校验下载 SHA-256，最终完整旅程实际通过 |

## 验证

- 最终 Dart format：173 文件、0 改动；analyze 无问题；211 Flutter 测试通过；普通 API-only arm64 debug 构建成功。原生未改动，保留此前 27 插件 + 6 app JVM 测试通过证据，未声称本轮重跑。
- HA28L0L4 / TB710FU / Android 16 / Adreno 750 / 4KB：真实 SDK 生命周期集成 1 项通过；最终真实 HTTP/缓存/SDK 的双账号 mobile journey 1 项通过，约 45 秒测试体与 teardown，不含构建安装。双账号使用独立本机 H2/Redis，不代表部署双账号验证。
- 真实 profile、20 次开关、云修订与活动租约，以及工程/上传/取消/恢复等路径见 [设备矩阵](android-device-matrix.md)。语义与断点测试不替代物理读屏手势。
- 普通 APK SHA-256 `c5802e86d59436f9e581679674c740f862241b512b17cec8f852b5e138463b31`；六个 SO 全部 arm64，三份 notice 完整，ZIP 对齐通过；不等同 16KB ELF/加载或生产签名发行通过。

## 保留限制

部署 Worker 完成及有效训练产物 E2E、物理 TalkBack 手势/语音、预测返回手势、部分磁盘/低内存/权限/进程故障注入、独立手机/跨 GPU、16KB、签名发行及 iOS 未覆盖。固定 SDK 实际白画布偏离暗画布设计；内存波动不能排除泄漏。完整限制和失败经过见 [用户路径](mobile-journey.md)。既有 KGP 迁移警告/可升级依赖提示仍在，当前固定工具链已通过构建。
