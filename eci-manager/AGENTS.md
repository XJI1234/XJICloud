# eci-manager Agent 规则

本文是 `eci-manager/` 的执行规则。详细架构、API、联调结论和历史上下文见 [AGENT_CONTEXT.md](AGENT_CONTEXT.md)；人类使用文档见 [README.md](README.md)、[docs/设计与使用说明.md](docs/设计与使用说明.md) 和 [docs/测试说明.md](docs/测试说明.md)。

## 项目边界

- 这是独立的 Spring Boot 3.3.5 / Java 17 服务，端口为 `8081`，通过阿里云 ECI OpenAPI 管理容器实例。
- 本任务范围默认限于 `eci-manager/`；不要修改 `backend/`，除非用户明确要求。
- 不要扩展当前非目标：训练队列、Worker 注册/心跳、自动扩缩、独立 Node/React 前端、业务回调、镜像注册表、批量提交、运行时 Provider 热切、以及 8081 多钥目录。
- 本地操作台（`src/main/resources/static/`，无构建步骤）已在范围内。不要把 Api-Key、AccessKey 或 Secret 写入静态文件、文档或日志。

## 必须保持的不变量

- 本地实例状态是权威状态；创建是异步受理，必须先落库再进入异步调云；关闭语义是删除实例。
- 一个任务只对应一个实例。任务成功必须同时满足云侧 `Succeeded` 和本地实例已 `TERMINATED`。
- 手动实例不进入任务生命周期自动删除；任务实例由生命周期 watcher 管理。
- `Failed` 和 `TIMEOUT` 的处理顺序固定为：抓取容器日志尾部 → 持久化失败状态和日志 → 可选宽限 → 删除实例。抓日志失败不能阻断删除。
- 当前钥按 resource 各一把并持久化热切；成对 AK 走 `STATIC_AK`。`.env` 只作初始化种子，Java 不直接读取 `ALIBABA_CLOUD_*`。

## 安全与配置

- 不要把长期 AccessKey、Secret、HTTP Api-Key、`application-local.yml` 或其他凭据写入 Git、日志、测试输出或本文档。
- GET、API 响应和日志不得暴露 Secret。不要把 AK/SK 注入 ECI 容器；任务输出存储使用预签名 URL。
- 真云 ECI 任务访问 OSS 必须使用对应地域的内网 endpoint；真云测试会产生费用，测试完成后必须删除或取消实例并确认无残留。
- 需要凭据的真云联调使用 `.env` 和 `scripts/load-env.sh`；优先使用现有脚本，不要在文档中写入真实密钥。

## 工作方式

- 修改前先读相关源码、测试和文档；实现进度与下一步以计划文件为准。
- 本地环境先执行 `source ../.tools/env.sh`。自动化测试使用 `mvn test`，默认 Mock、不调用云。
- 真云操作优先使用 `scripts/run-hello-python.sh`、`scripts/debug-oss-smoke.sh` 和 `scripts/submit-3dgs-standard.sh`，不要随意创建长期残留资源。
- 不要自动执行 `git commit` 或 `git push`，除非用户明确要求。
