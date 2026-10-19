# li-chengtao 模型查看器独立集成审查

审查日期：2026-10-18 至 2026-10-19。使用 `.agents/skills/open-code-review-delegate/SKILL.md`。
OCR 提供文件筛选及规则解析，代码审查由宿主 Agent 完成；未调用独立审查模型。

## 范围与集成结果

- 主分支基线：`origin/main = b381feb693ae2b1536cafdeed02bed1ee7b20c94`。
- li 分支提交：`7e59ca3`（设备连接）和 `74e867f43de6756295ea015f1ea8aadb7a2b5b21`（查看器）。
- 按用户要求，仅移植查看器提交；不包含设备连接、任务执行、Cesium/FLV、设备路由、容器绑定或后端 CORS/配置变更。
- 独立分支：`codex/integrate-li-viewer-only`，从上述 main 基线建立。未合入 main 或 ceshi。
- 查看器原提交涉及 47 个文件，OCR 选中 30 个代码/配置文件；补充审查测试、锁文件、旧组件删除和 SDK 包来源/校验值。
- SDK tarball SHA256 与 vendor 记录一致：`24459b44b904d4bba46576d887e15400a471a7767a59908516d8349d8b94f20e`。

## 发现与处理

### High / P1：部署示例缺少解码模块 MIME 配置（已修复）

- path: `apps/web2/src/features/viewer/presentation/components/Native3DGSViewer.vue`
- start_line: 36
- end_line: 38
- category: bug
- severity: high
- content: 新引擎从固定 `gs-assets/` 加载 Worker，Worker 再动态 import `decoder.mjs`。
  原有 Nginx 示例没有 `.mjs` 类型映射；Nginx 1.27 默认 mime.types 的 JavaScript 映射仅包含 js。
  在沿用该默认配置的站点，解码模块会按 application/octet-stream 返回，浏览器严格模块 MIME 校验将拒绝加载，
  导致模型无法打开。原有 SPA fallback 还会把缺失的 SDK 资源返回为 HTML/200。

处理：在两个 `deploy/nginx*.conf.example` 和 `deploy/k8s/frontend/nginx.conf` 增加独立的
`location ^~ /gs-assets/`，明确 js/mjs/wasm MIME 并使用 `try_files $uri =404`。
没有增加全站 COOP/COEP。配置指南及运维检查命令见 `docs/Deploy.md` §5.3.1。
修复依据为代码与 Nginx 默认 MIME 的静态核对；尚未在实际 Nginx 进程/线上 HTTPS 环境验收。

### 合并风险：只 cherry-pick 查看器仍有两处语言文件冲突（已处理）

完整 li 分支相对 main 可快进，但会同时带入设备功能，不能整体 merge。
在 main 上单独应用 `74e867f`，`en-US.ts` 和 `zh-CN.ts` 会发生内容冲突：
新增 nativeViewer 区块与父提交已有 missionOps 区块相邻。
独立分支仅保留 nativeViewer，去除冲突标记及设备文案；不能简单选择整份 theirs 文件。
其他补丁自动合入。已静态核对设备源码、路由、容器和后端不在独立分支差异中。

### 整分支观察项（已随设备提交排除）

完整 li 分支在本机 Windows 上 `pnpm build:web2` 失败：
`vite-plugin-static-copy` 对 Cesium 的 Windows 反斜杠路径无法匹配源目录。
该配置来自设备提交，不在查看器提交；独立集成结果的 Windows 生产构建通过。
不将此项列为独立查看器分支的未修复问题。

## 产品与部署边界

- 新查看器依赖 WebGPU、安全上下文和本地存储；原有 HTTP 公网 IP 访问方式需要改为 HTTPS。
- 旧相机配置不再直接应用，保留旧数据并提示自动取景；ASCII PLY 不受支持。这些是提交明确声明的能力变化。
- PLY/SPZ 文件存储位置不变，云模型仍经带 Bearer 的仓储下载，不使用 SDK 裸 URL 绕过鉴权。
- 缓存修订、取消下载/解码、迟到结果、关闭/卸载及截图资源释放已核对；未确认其他可复现的 High/Critical 代码缺陷。
- K8s 前端 Dockerfile 的旧 Node/旧 apps/web 路径是已有部署缺口，本轮没有扩展为镜像构建迁移。

## 验证

以下记录均来自用户要求停止其他测试之前的执行：

- 原 li 分支：`pnpm test:web2`，39 文件 / 148 条通过。
- 仅查看器集成结果：`pnpm install --frozen-lockfile` 通过。
- 仅查看器集成结果：`pnpm test:web2`，33 文件 / 116 条通过。
- 仅查看器集成结果：`pnpm build:web2` 通过。
- 仅查看器集成结果：`pnpm --filter @xjicloud/web2 build:viewer-demo` 通过。
- 上述 pnpm 执行使用本机 Node 24.9.0，产生 SDK 要求 >=24.14.0 的 engine 警告；不能据此声称最低版本验收。
- 浏览器：独立 demo 中生成的 4096 点 sphere.ply 真实解码后显示「可以浏览」，缩放、Y 翻转和自由模式按钮有响应；当时页面错误日志为空。
- 未完成浏览器 E2E：未覆盖真实账号工程模型、大模型、SPZ 完整路径、截图导出、生产 HTTPS/Nginx。
- 缩小范围前曾运行后端 `mvn -B test`：19 条中 8 条因本机 Java 25 与现有 Byte Buddy 不兼容而报错；
  该环境失败不作为查看器缺陷，独立分支不修改后端。

用户要求停止后，不再运行测试套件或构建。新增 Nginx 配置及开发代理仅做静态审查；
后续按用户新指令启动 dev、打开页面并检查后端代理，属于启动确认，不代表完整 E2E。

2026-10-19 启动确认：使用 Node 24.14.1 启动本独立工作区的 Vite dev，监听
`http://127.0.0.1:5176/`。本机忽略文件 `apps/web2/.env.development.local` 将
`DEV_API_PROXY_TARGET` 设为 `http://114.55.113.49`，IP 未写入共享默认配置。
首页返回 200；本地 `/api/v1/auth/captcha` 经代理返回 200、`success: true`。
已请求在 Codex 本地浏览器面板打开；没有进行登录、解答验证码或完整账号联调。

## Code Review

结论：查看器已独立提取，语言冲突已解决，确认的 High 部署缺陷已补配置。
未发现剩余已确认的 High/Critical；真实 Nginx 和登录后的云端用户路径仍待验收。
本报告不把编译通过等同线上功能验证。
