# Native3DGS 查看器接入与验收

更新：2026-10-13。工作区：/home/li/XJICloud；开发分支：li-chengtao。

## 范围与 Git 基线

本任务只重做 web2 的模型查看器，不继续 Sky Command / MSDK 任务。
已 fetch origin；接手时 origin/main 为 b381feb，origin/li-chengtao 与本地 HEAD 均为 7e59ca3。
main 已是个人分支的祖先，没有需要合入的新提交。

最终交付按用户要求整合为一个查看器功能提交，包含 SDK/demo 接入、加载与缓存修复、OCR 复审修复及回归测试。
该提交直接接在 7e59ca3 后，学长可只 cherry-pick 此查看器提交，无需合入此前的 MSDK 任务。
整合前的 c9cd47e、9da6a44、805f1fa 已保留在本机 refs/backup/li-chengtao/viewer-before-squash-20261013；该备份引用不推送。
只在 li-chengtao 提交。用户明确要求 push 后，才可推 origin/li-chengtao。不要推 main。
不要 reset/清空本地改动来追 main；先 fetch、查看差异，再决定是否合并。

接手时的未提交文件与二进制补丁另有本机备份；个人 Cursor 规则、任务执行交接资料与测试数据未混入查看器提交。
start-*.sh、本机 Node 运行时、生成的 SDK 资产和 exe 不提交。

## SDK 与功能

固定 @native3dgs/web@0.2.2-preview.3，沿用已有包，不在本次升级或改写 SDK。
用户最初描述的是 preview.2；接手时 SDK 和 Vue 模板已是 preview.3，来源及 SHA256 在 apps/web2/vendor/README.md。
按 SDK-guide.md、Vue-integration.md 和模板实际调用方式接入，使用公开 API。
Vue demo 与云页面复用 Native3DGSViewer；Domain 不引入 SDK、Vue 或 HTTP。

功能：本机打开/拖放、取消加载、关闭、固定/自由、适配、重置、Y 翻转、放大/缩小、PNG 截图。
云模型仍通过带 Bearer 的已有仓储下载，再以 File 交给 SDK；PLY/SPZ 仍使用原本的本地盘存储。
SDK Worker/WASM/许可证按 manifest 校验并完整部署，普通云页不添加全站隔离头。

本次修复：
- 云模型字节准备好后立即解码，不等待仅供提示使用的旧 viewer-config。
- 取消或选择另一模型时，同时取消旧配置请求，忽略过期结果。
- 下载向缓存层传递最新 updatedAt/sizeBytes，防止文件更新后还打开旧缓存。
- 无模型时收起视角工具；初始化和文件错误在画面中明确展示，初始化可重试。
- 文件拖入时提供明确提示，跨子元素拖动不会留下错误的拖放状态。

旧 viewer-config 缺少坐标系来源，仍自动适配，提示未应用旧视角；不写回或改动旧配置。
加载失败/取消保留旧场景，真正成功后才更新当前模型。

## 启动

需要 Java 17、Node 24.14+、pnpm 9、Docker Desktop 的 Ubuntu-22.04 集成。
本机便利脚本已修复：检查已运行服务、快速报告失败，并优先使用项目 .local 中的 Node 24.14.1。
这些脚本仅存在本机，不是 clone 后自动提供的仓库入口。

在 WSL 中启动基础设施：
```bash
cd /home/li/XJICloud
./start-infra.sh
```

后端单独一个终端，持续运行：
```bash
cd /home/li/XJICloud
./start-backend.sh
```

前端另一个终端，持续运行：
```bash
cd /home/li/XJICloud
./start-frontend.sh
```

云平台：http://localhost:5176；独立 Vue demo：http://localhost:5186。
不要把后端持续运行时的终端当作下一条前端命令已经执行。
标准仓库入口仍是 services/backend 中的 mvn spring-boot:run 与根目录 pnpm dev。

## 验证记录

使用项目内 Node 24.14.1：
- pnpm test:web2：148 条 / 39 文件通过，包括缓存修订、配置取消及路由迟到结果回归。
- pnpm build:web2：类型检查及生产构建通过。
- pnpm --filter @xjicloud/web2 build:viewer-demo：通过。

Windows Edge 浏览器实跑：
- 真实服务登录页、验证码接口、后端 health UP。
- 4096 点二进制 PLY 和 SPZ 打开；全部视角按钮；PNG 文件签名正确。
- 关闭后重开；加载失败/取消保留旧模型；卸载离开查看器。
- 390px 布局没有横向溢出；WebGPU 缺失时显示明确提示及重试。
- 云端流程使用隔离浏览器上下文与 API 夹具：验证 Bearer 下载、旧配置停滞不阻塞、晚到配置提示及零写入。
- 生产 preview：非零坐标中心 PLY/SPZ、PNG 截图、Worker/WASM MIME、完整资产 SHA256 与许可证、路由卸载、SuperSplat 本机打开回归均通过；页面脚本错误为零。
- 缓存修订回归先确认测试失败，再修复后通过；旧 revision 必须重新下载，当前 revision 复用缓存。

浏览器测试使用真实 SDK、WASM 和 GPU，不以渲染 mock 替代。
云端 API 夹具不等同真实账号联调；现有真实账号、工程模型和大模型的用户验收仍待完成。
后台服务已启动；本次没有修改后端源码或数据库结构。

## Code Review

范围：SDK 接入、Vue 宿主生命周期、云模型下载/缓存/取消、界面错误态、资产部署与本地启动脚本。
核对 DDD 依赖、单一组合根、Bearer、取消后的迟到回调、截图资源释放和受保护目录。
修复旧配置阻塞及缓存失效缺口；剩余 High/Critical：无。
未提交生成资产、安装包、个人规则、启动脚本。只在用户明确要求后推 origin/li-chengtao。

## 2026-10-13 OCR 全任务复审

使用仓库技能 `.agents/skills/open-code-review-delegate/SKILL.md`：
`ocr delegate preview --from 7e59ca3 --to 9da6a44` → `ocr delegate rule <paths>` → Agent 审实际 diff。
OCR 只做确定性的文件筛选和规则解析，不是外部 LLM 或独立审查人。

原范围为 c9cd47e + 9da6a44，共 46 个改动文件；OCR 选中 31 个代码文件。
额外核对被默认排除的测试、文档、旧组件删除和 SDK 二进制包；tarball SHA256 与 vendor 记录一致，资产 manifest 的 5 个文件校验通过，许可证保留。
受保护目录 apps/web、packages/spark、rust、services/backend、apps/admin 的原任务 diff 为空；启动脚本、exe、生成 public 资产不在提交中。

发现并修复 1 项时序缺陷：LayerViewerView 的路由 modelId 列表请求只核对 pickerRequest，没有核对用户选择的生命周期。
用户在列表返回前取消/关闭，或移除 modelId 时，旧列表仍可能触发下载并重新打开旧目标；切到新 modelId 等待列表时，旧下载也未立即取消。
修复：每次 modelId 变化（包括移除）立即取消旧选择，列表完成时同时核对路由请求和 selection，忽略迟到结果。

验证：
- 新增 LayerViewerView.spec.ts 的 4 条回归测试在修复前全部失败，修复后通过。
- pnpm test:web2：148 条 / 39 文件通过；pnpm build:web2、build:viewer-demo 均通过。
- Edge 真实浏览器延迟云列表：关闭后保持空场景，移除 modelId 后保持本机模型，两种情况旧下载次数均为 0。
- 重新验证云配置挂起仍加载模型、晚到旧视角提示、取消/损坏文件保留旧场景、PLY/SPZ、视角/PNG、关闭重开、390px 无溢出及无 WebGPU 重试；页面脚本错误为零。
- diff --check 通过。生产构建仍只有现有 Cesium 大 chunk 提示。
- 真实账号工程、大模型/其他设备和线上 HTTPS/Nginx 部署仍未覆盖。当前普通 Nginx 示例没有新增 SDK MIME 配置，部署时需按 SDK 指南核对 mjs/wasm、404 和实际 HTTPS；未把 Vite preview 的通过当作线上验收。

复审修复原独立提交为 805f1fa；随后按用户明确指令，与前两个本地提交整合成一笔交付。
历史 OCR 范围保留作审查记录，当前实现与复审后的应用代码相同。复审后未处理的 High/Critical：无。
