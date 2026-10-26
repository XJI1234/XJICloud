# 后端一键部署：首次配置、逐项升级与校验

业务配置只有 `/etc/xjicloud/application-prod.yml`。仓库内的 `deploy/config/application-prod.yml` 是待安装配置，勿提交到Git。`backend_config.py` 是编辑/校验程序，不是另一份配置。

## 依赖与入口

Linux需要Java17+、Maven、Python3.8+及PyYAML。Ubuntu/Debian可安装 `python3 python3-yaml`；RHEL系安装相应Python和PyYAML包。若默认Python过旧，在 `deploy/config/deploy.conf` 设置 `PYTHON_BIN=/新版解释器的绝对路径`，并确保该解释器能导入yaml。缺少依赖会在写配置或替换产物前报错。

```bash
# 首次安装：逐项填写、确认保存，然后构建和安装
sudo bash deploy/deploy-backend.sh

# 已安装：读取服务器现有YAML，逐项升级配置，然后构建和更新
sudo bash deploy/deploy-backend.sh upgrade

# 只准备/修改仓库本地YAML，不安装
sudo bash deploy/deploy-backend.sh configure

# 只校验：优先读取本地YAML，不存在时读取已安装YAML
sudo bash deploy/deploy-backend.sh validate
```

无子命令时，检测到已安装YAML会自动进入upgrade；否则先configure再install。`upgrade`的原值始终来自服务器配置，不会拿仓库本地的旧配置覆盖服务器密钥。

## 如何逐项修改

每项显示完整配置键、`[原配置]`或`[新增]`及当前值。回车沿用；输入新值则修改；`!`清空可选字符串。密码、AccessKey、JWT、Worker密钥和含凭据的URL只显示“已设置，隐藏”，交互输入不回显。数字和布尔值按类型解析，不能把端口字符串或拼接文本写入YAML。

新增项也会逐项出现，允许接受模板默认值；启用短信后，空凭据、签名、模板必须补齐。JWT/Worker密钥仅在首次缺失时自动生成，已有密钥不会重新生成。管理员初始密码只用于建号，已有管理员改密须明确开启sync-password-on-startup或使用账号管理流程；修改Worker共享密钥须同步Worker。

未知配置字段、嵌套映射及数组保留并允许编辑。额外数组等复杂值若修改需使用单行JSON。编辑器使用嵌套YAML键；带点的键需先展开为嵌套映射。新增未知字段没有后端类型定义，编辑器只检查原类型，不替第三方配置臆造约束。

最后确认保存。取消、EOF、Ctrl+C或校验失败不覆盖原配置；交互upgrade尚未修改生产JAR。修改在私有临时目录中准备，通过校验后才安装；有改动时备份原YAML。全部沿用且没有新增/迁移项时，原文件字节、注释和密钥保持不变，不额外产生配置备份。发生修改时规范化YAML排版，不保留原注释，原始文件可从备份恢复。

## 保存与安装前的检查

- **格式/类型**：只允许单个YAML映射；拒绝重复键、循环引用、非法层级、对象标签和多文档。字符串经YAML库序列化并重新加载对比，确保中文、引号、反斜杠、井号和看似布尔/日期/数字的字符串不会变型。文件写入前即限制为600，原子替换并备份；生产安装为root持有、服务组只读640。
- **数据库/Redis/OSS/CORS**：PostgreSQL JDBC格式、数据库名、主机及端口、HTTP(S) Endpoint、Bucket命名、Region非空、Origin无路径和尾斜线。Redis密码允许留空；数据库、OSS、管理员必要凭据不得用示例占位符。
- **端口/目录/容量/超时**：端口1至65535，正整数范围，预签名有效期不超过7天；存储目录规范化后不能是根目录、公共系统目录或文件；请求容量不能小于单文件容量；长轮询超时小于心跳超时。
- **生产安全及短信**：禁止create/create-drop自动删表；旧prod配置内的spring.profiles.active=prod会迁移为启动参数激活；JWT至少32字节、Worker密钥至少24字符、管理员密码满足BCrypt长度限制；PNVS方案1至20字符、模板变量合法且不同、发送日限额为正整数，启用后必要字段非空。不支持在受管理字段中保留未解析的环境占位符。

已有 `backend.env` 必须先把业务项迁入YAML并将旧文件改名备份；脚本不会继续加载它。JVM参数应放systemd drop-in。所有安装/升级路径，包括 `--skip-build`、`--non-interactive`，均会在替换JAR前验证目标配置。数据目录和健康检查端口直接取自该YAML，避免和deploy.conf重复配置不一致。

## 可选连接检查与有效性边界

```bash
sudo bash deploy/deploy-backend.sh validate --check-connections
sudo bash deploy/deploy-backend.sh upgrade --check-connections
```

连接失败会停止保存/部署。检查包括PostgreSQL TCP端口（安装psql时额外执行SELECT 1验证账号/数据库）、Redis AUTH/PING、OSS网络/TLS、启用短信时PNVS网络/TLS。输出明确列出未验证项，错误详情不会原样输出凭据。

**网络可达不等于云凭据有效。** 未安装psql时不验证数据库账号；OSS Bucket/凭据、PNVS RAM授权/签名/模板须按实际环境验收。向导不会发送收费短信或消费用户验证码。生产账号和云权限验证步骤见[手机号注册部署指南](./PHONE_REGISTRATION_DEPLOYMENT.md)。

## 自动化与已验证产物

```bash
# 保留已安装YAML，无交互；仍校验配置
sudo bash deploy/deploy-backend.sh upgrade --non-interactive --skip-build

# 明确应用已准备好的本地YAML，验证后备份并整体替换
sudo bash deploy/deploy-backend.sh upgrade --non-interactive --update-config --skip-build
```

`--skip-build`仅用于已测试过的JAR。默认构建执行Maven测试；注册集成测试需要独立Redis和SMS_TEST_REDIS_PORT。非交互模式不代替逐项配置；首次引入新字段时，先运行交互upgrade或configure准备完整配置。

验证命令：`bash -n deploy/deploy-backend.sh` 和 `sudo python3 -B -m unittest discover -s deploy/tests -v`。临时安装测试需root，非root会明确跳过安装权限用例；测试只在临时目录操作，不写真实systemd或生产数据。

## 本次验证与审查（2026-10-26）

- WSL Ubuntu以root执行Shell语法检查及部署测试：25项通过，无跳过。覆盖首次一键安装、逐项升级、新增项、修改备份、全部沿用、取消/EOF及校验/连接失败阻止产物替换。
- 后端使用独立Redis执行 `mvn -B package`：42项通过，无跳过。包含注册接口集成测试及Spring实际绑定生产YAML的特殊字符密码、数字和布尔值检查。
- 自审范围为部署脚本、配置编辑器、模板、测试与文档；已修复备份写入、存储根路径别名及旧空Redis密码保持问题，无未修复High/Critical问题。
- 本次未执行生产部署、真实systemd启动或浏览器E2E；连接检查的云凭据/权限验证边界见上文。
