#!/usr/bin/env python3
"""Review and validate the existing production YAML; never execute configuration values."""
import argparse
import copy
import getpass
import ipaddress
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import ssl
import subprocess
import sys
import tempfile
import time
from urllib.parse import parse_qs, urlsplit

if sys.version_info < (3, 8):
    sys.exit("需要Python 3.8+；可在deploy.conf中设置PYTHON_BIN为新版解释器的绝对路径")

try:
    import yaml
except ImportError:
    sys.exit("需要 Python3 + PyYAML：Ubuntu/Debian 安装 python3-yaml；RHEL/Alibaba Cloud Linux 安装 python3-pyyaml")


class ConfigError(ValueError):
    pass


class UniqueKeyLoader(yaml.SafeLoader):
    def construct_mapping(self, node, deep=False):
        result = {}
        for key_node, value_node in node.value:
            key = self.construct_object(key_node, deep=deep)
            if not isinstance(key, str) or key in result:
                raise ConfigError("YAML包含重复键、非字符串键或不支持的合并键")
            result[key] = self.construct_object(value_node, deep=deep)
        return result


def check_tree(value, active=None):
    active = set() if active is None else active
    if isinstance(value, (dict, list)):
        if id(value) in active:
            raise ConfigError("YAML不允许循环引用")
        active.add(id(value))
        for item in value.values() if isinstance(value, dict) else value:
            check_tree(item, active)
        active.remove(id(value))
    elif value is not None and type(value) not in (str, int, float, bool):
        raise ConfigError("YAML仅支持普通映射、数组和标量，不支持日期/自定义对象")


def load_yaml(path):
    try:
        if Path(path).stat().st_size > 1024 * 1024:
            raise ConfigError("配置文件超过1MiB")
        data = yaml.load(Path(path).read_text(encoding="utf-8"), Loader=UniqueKeyLoader)
        if not isinstance(data, dict):
            raise ConfigError("生产配置必须是单个YAML映射")
        check_tree(data)
        return data
    except (yaml.YAMLError, UnicodeError) as error:
        mark = getattr(error, "problem_mark", None)
        location = f"（第{mark.line + 1}行）" if mark else ""
        # A parser exception embeds source lines, which may contain passwords.
        raise ConfigError("YAML格式错误" + location) from None


def get_value(data, path, default=None):
    for key in path.split("."):
        if not isinstance(data, dict) or key not in data:
            return default
        data = data[key]
    return data


def set_value(data, path, value):
    parts = path.split(".")
    for key in parts[:-1]:
        data = data.setdefault(key, {})
        if not isinstance(data, dict):
            raise ConfigError("配置层级应为映射，请先修复父节点")
    data[parts[-1]] = value


def fields(data, prefix=""):
    for key, value in data.items():
        # Dotted keys cannot be represented unambiguously by this editor's field paths.
        if "." in key:
            raise ConfigError("请将带点的YAML键改写为嵌套映射后再编辑")
        path = f"{prefix}.{key}" if prefix else key
        if isinstance(value, dict) and value:
            yield from fields(value, path)
        else:
            yield path, value


def merge_defaults(defaults, existing):
    result = copy.deepcopy(existing)
    for key, value in defaults.items():
        if key not in result:
            result[key] = copy.deepcopy(value)
        elif isinstance(value, dict):
            if not isinstance(result[key], dict):
                raise ConfigError("配置结构与模板不符：需要嵌套映射")
            result[key] = merge_defaults(value, result[key])
    return result


TEMPLATE = Path(__file__).resolve().parent / "config/application-prod.yml.example"
SECRET = re.compile(r"password|secret|access[-_]?key|token|credential", re.I)
OPTIONAL_STRINGS = {"spring.data.redis.password", "xjicloud.sms.access-key-id",
                    "xjicloud.sms.access-key-secret", "xjicloud.sms.sign-name", "xjicloud.sms.template-code"}
NOTES = {
    "spring.jpa.hibernate.ddl-auto": "首次空库可用update；已完成SQL迁移的生产库建议validate。",
    "xjicloud.jwt.secret": "仅首次缺失时自动生成；修改原密钥会使已有会话失效。",
    "xjicloud.worker.shared-secret": "仅首次缺失时自动生成；修改后须同步所有Worker。",
    "xjicloud.admin.default-password": "用于创建初始管理员；已有账号仅在sync-password-on-startup=true时同步此密码。",
    "xjicloud.sms.enabled": "仅接入阿里云PNVS；启用后需RAM凭据、赠送签名/匹配模板，向导不发送短信。",
    "xjicloud.cors.allowed-origins": "*允许所有来源；生产建议填写前端Origin，多个值用逗号分隔。",
}


def hidden(path, value):
    return bool(SECRET.search(path) or (isinstance(value, str) and re.search(r"password=|://[^/]*@", value, re.I)))


def hostname(value):
    try:
        ipaddress.ip_address(value)
        return True
    except ValueError:
        return bool(len(value) <= 253 and all(re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?", label)
                                              for label in value.rstrip(".").split(".")))


def url(value, schemes):
    try:
        parsed = urlsplit(value)
        if parsed.scheme not in schemes or not parsed.hostname or not hostname(parsed.hostname):
            raise ValueError()
        if parsed.username or parsed.password or parsed.fragment or (parsed.port is not None and not 1 <= parsed.port <= 65535):
            raise ValueError()
        return parsed
    except ValueError:
        raise ConfigError("地址协议、主机或端口无效；请勿在URL内放凭据") from None


def size_bytes(value):
    match = re.fullmatch(r"([1-9][0-9]*)(B|KB|MB|GB|TB)", value)
    if not match:
        raise ConfigError("容量须为正整数及B/KB/MB/GB/TB，例如2GB")
    size = int(match[1]) * 1024 ** ("B", "KB", "MB", "GB", "TB").index(match[2])
    if size > 9223372036854775807:
        raise ConfigError("容量超出Spring DataSize范围")
    return size


def validate_field(path, value, data, defaults):
    missing = object()
    expected = get_value(defaults, path, missing)
    if expected is missing:
        return  # Preserve additional Spring/user settings, including arrays, without inventing a schema.
    if value is None and path in OPTIONAL_STRINGS:
        value = ""
    if expected is None:
        expected = ""
    if type(expected) is bool:
        if type(value) is not bool:
            raise ConfigError("必须为true或false")
    elif type(expected) is int:
        maximum = 9223372036854775807 if path == "xjicloud.jwt.expiration-ms" else 2147483647
        if type(value) is not int or not 1 <= value <= maximum:
            raise ConfigError("必须为范围内的正整数")
        if path.endswith(".port") and value > 65535:
            raise ConfigError("端口必须在1至65535之间")
        if path == "xjicloud.oss.presign-expiration-minutes" and value > 10080:
            raise ConfigError("预签名有效期不得超过7天（10080分钟）")
    else:
        if not isinstance(value, str) or any(c in value for c in "\r\n\x00"):
            raise ConfigError("必须为单行字符串")
        if "${" in value:
            raise ConfigError("请填实际值；systemd不自动继承当前终端的环境变量")
        if path not in OPTIONAL_STRINGS and not value.strip():
            raise ConfigError("此项不能为空")
        if path.startswith("xjicloud.sms.") and path in OPTIONAL_STRINGS and get_value(data, "xjicloud.sms.enabled") and not value.strip():
            raise ConfigError("启用短信后此项必填")
        if value and (value.startswith(("change-me", "replace-with-")) or "xxxxx" in value):
            raise ConfigError("请替换示例占位值")
        if path == "spring.datasource.url":
            if not value.startswith("jdbc:postgresql://"):
                raise ConfigError("生产数据库须使用jdbc:postgresql://主机:端口/数据库")
            parsed = url(value[5:], {"postgresql"})
            if not parsed.path.strip("/") or any(k in parse_qs(parsed.query) for k in ("password", "user")):
                raise ConfigError("请指定数据库名，并在独立用户名/密码字段中填写凭据")
        if path == "spring.data.redis.host" and not hostname(value):
            raise ConfigError("Redis主机须为域名或IP，不包含协议/端口")
        if path == "xjicloud.oss.endpoint":
            if url(value, {"http", "https"}).query:
                raise ConfigError("OSS Endpoint不应包含查询参数")
        if path == "xjicloud.oss.bucket" and not re.fullmatch(r"[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]", value):
            raise ConfigError("Bucket须为3至63位小写字母、数字、点或短横线")
        if path == "xjicloud.cors.allowed-origins" and value != "*":
            for origin in value.split(","):
                parsed = url(origin.strip(), {"http", "https"})
                if parsed.path or parsed.query:
                    raise ConfigError("CORS须为逗号分隔的Origin（协议+域名+可选端口，不含路径/尾斜线）")
        if path == "xjicloud.storage.root":
            try:
                resolved = Path(value).resolve()
            except (RuntimeError, OSError):
                raise ConfigError("存储目录无法解析，请检查权限或符号链接循环") from None
            protected = {"/", "/etc", "/usr", "/bin", "/sbin", "/lib", "/lib64", "/dev", "/proc", "/sys",
                         "/run", "/boot", "/home", "/root", "/var", "/opt", "/tmp"}
            if not value.startswith("/") or str(resolved) in protected or (Path(value).exists() and not Path(value).is_dir()):
                raise ConfigError("存储须为独立的Linux绝对目录，不能是文件、根目录或系统公共目录")
        if path == "spring.jpa.hibernate.ddl-auto" and value not in ("none", "validate", "update"):
            raise ConfigError("生产部署仅允许none/validate/update，禁止自动删表")
        if path == "xjicloud.jwt.secret" and len(value.encode()) < 32:
            raise ConfigError("JWT密钥至少32字节")
        if path == "xjicloud.worker.shared-secret" and len(value) < 24:
            raise ConfigError("Worker共享密钥至少24字符；修改后须同步Worker")
        if path == "xjicloud.admin.default-password" and not (8 <= len(value) <= 64 and len(value.encode()) <= 72):
            raise ConfigError("管理员初始密码须为8至64字符且UTF-8不超过72字节")
        if path == "xjicloud.sms.scheme-name" and len(value) > 20:
            raise ConfigError("短信方案名称须为1至20字符")
        if path in ("xjicloud.sms.code-variable", "xjicloud.sms.minutes-variable"):
            if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{0,31}", value):
                raise ConfigError("模板变量须为字母开头、最多32位字母/数字/下划线")
            if path.endswith("minutes-variable") and value == get_value(data, "xjicloud.sms.code-variable"):
                raise ConfigError("两个短信模板变量不能相同")
        if path.startswith("spring.servlet.multipart."):
            size = size_bytes(value)
            if path.endswith("max-request-size") and size < size_bytes(get_value(data, "spring.servlet.multipart.max-file-size")):
                raise ConfigError("请求上限不能小于单文件上限")
    if path == "xjicloud.worker.poll-timeout-sec" and value >= get_value(data, "xjicloud.worker.heartbeat-timeout-sec"):
        raise ConfigError("长轮询超时须小于心跳超时")


def validate_config(data, defaults=None):
    defaults = load_yaml(TEMPLATE) if defaults is None else defaults
    check_tree(data)
    effective = merge_defaults(defaults, data)
    for path, value in fields(effective):
        try:
            validate_field(path, value, effective, defaults)
        except ConfigError as error:
            raise ConfigError(f"{path}: {error}") from None
    if get_value(data, "spring.profiles.active") is not None:
        raise ConfigError("prod配置内不能设置spring.profiles.active，请使用configure迁移")
    sms = get_value(effective, "xjicloud.sms")
    if sms["code-variable"] == sms["minutes-variable"]:
        raise ConfigError("xjicloud.sms: 两个模板变量名不能相同")
    if sms["enabled"] and any(not (sms[key] or "").strip() for key in ("access-key-id", "access-key-secret", "sign-name", "template-code")):
        raise ConfigError("xjicloud.sms: 启用短信须填写RAM凭据、赠送签名和匹配模板")
    if get_value(effective, "xjicloud.sms.provider", "aliyun-pnvs") != "aliyun-pnvs":
        raise ConfigError("xjicloud.sms.provider: 仅支持aliyun-pnvs")
    if size_bytes(get_value(effective, "spring.servlet.multipart.max-request-size")) < size_bytes(get_value(effective, "spring.servlet.multipart.max-file-size")):
        raise ConfigError("spring.servlet.multipart: 请求上限不能小于单文件上限")
    if get_value(effective, "xjicloud.worker.poll-timeout-sec") >= get_value(effective, "xjicloud.worker.heartbeat-timeout-sec"):
        raise ConfigError("xjicloud.worker: 心跳超时须大于长轮询超时")
    return effective


def parse_input(raw, reference):
    if isinstance(reference, bool):
        if raw.lower() in ("true", "y", "yes", "1"):
            return True
        if raw.lower() in ("false", "n", "no", "0"):
            return False
        raise ConfigError("请输入true/false或y/n")
    if type(reference) is int:
        if not re.fullmatch(r"[0-9]+", raw):
            raise ConfigError("请输入整数")
        return int(raw)
    if isinstance(reference, (dict, list, float)):
        try:
            value = json.loads(raw)
        except ValueError:
            raise ConfigError("请输入合法JSON（留空沿用）") from None
        if type(value) is not type(reference):
            raise ConfigError("请保持原值类型")
        return value
    return raw


def prompt_value(path, current, added, secret):
    shown = "[已设置，隐藏]" if secret and current else json.dumps(current, ensure_ascii=False)
    print(f"{'[新增]' if added else '[原配置]'} {path} = {shown}", flush=True)
    if path in NOTES:
        print(NOTES[path], flush=True)
    label = "新值（回车沿用；!清空）: "
    if secret and sys.stdin.isatty():
        return getpass.getpass(label)
    print(label, end="", flush=True)
    line = sys.stdin.readline()
    if not line:
        raise ConfigError("输入结束，原配置未更改")
    return line.rstrip("\r\n")


def review_config(defaults, existing, ask=prompt_value, report=print):
    existing = copy.deepcopy(existing)
    profiles = get_value(existing, "spring.profiles")
    if isinstance(profiles, dict) and profiles.get("active") == "prod":
        del profiles["active"]
        if not profiles:
            del existing["spring"]["profiles"]
        report("已迁移旧spring.profiles.active=prod；由启动参数激活prod")
    candidate = merge_defaults(defaults, existing)
    for path in ("xjicloud.jwt.secret", "xjicloud.worker.shared-secret"):
        if get_value(existing, path) is None:
            set_value(candidate, path, secrets.token_hex(32))
    for path in ("spring.datasource.password", "xjicloud.admin.default-password"):
        if get_value(existing, path) is None:
            set_value(candidate, path, "")
    existing_fields = dict(fields(existing))
    for path, current in list(fields(candidate)):
        reference = get_value(defaults, path, current)
        if reference is None:
            reference = ""
        if current is None and isinstance(reference, str) and path not in OPTIONAL_STRINGS:
            current = ""
        while True:
            raw = ask(path, current, path not in existing_fields, hidden(path, current))
            try:
                if raw == "!" and not isinstance(reference, str):
                    raise ConfigError("此类型不能清空，请输入原类型的值")
                value = current if raw == "" else ("" if raw == "!" else parse_input(raw, reference))
                validate_field(path, value, candidate, defaults)
                set_value(candidate, path, value)
                break
            except ConfigError as error:
                report(f"{path}: {error}")
    validate_config(candidate, defaults)
    return candidate


def save_config(path, data):
    validate_config(data)
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.is_symlink():
        raise ConfigError("拒绝覆盖符号链接配置")
    descriptor, name = tempfile.mkstemp(prefix=".application-prod-", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as output:
            yaml.safe_dump(data, output, allow_unicode=True, sort_keys=False, default_flow_style=False)
            output.flush()
            os.fsync(output.fileno())
        if load_yaml(name) != data:
            raise ConfigError("YAML序列化后值发生变化，拒绝保存")
        if path.exists():
            backup = path.with_name(path.name + ".bak." + str(time.time_ns()))
            backup_descriptor = os.open(backup, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with path.open("rb") as source, os.fdopen(backup_descriptor, "wb") as target:
                shutil.copyfileobj(source, target)
        os.replace(name, path)
    finally:
        Path(name).unlink(missing_ok=True)


def check_connections(data):
    """Explicit optional network checks; never sends a paid SMS or claims cloud authorization."""
    def connect(label, host, port, tls=False):
        connection = None
        try:
            connection = socket.create_connection((host, port), timeout=5)
            if tls:
                connection = ssl.create_default_context().wrap_socket(connection, server_hostname=host)
            return connection
        except (OSError, ValueError):
            if connection is not None:
                connection.close()
            raise ConfigError(f"{label}: DNS/TCP/TLS连接失败；请核对地址、端口、证书与防火墙") from None
    db = url(get_value(data, "spring.datasource.url")[5:], {"postgresql"})
    with connect("PostgreSQL", db.hostname, db.port or 5432):
        pass
    if shutil.which("psql"):
        env = os.environ.copy()
        env.update(PGHOST=db.hostname, PGPORT=str(db.port or 5432), PGDATABASE=db.path.lstrip("/"),
                   PGUSER=get_value(data, "spring.datasource.username"), PGPASSWORD=get_value(data, "spring.datasource.password"),
                   PGCONNECT_TIMEOUT="5", PGSSLMODE=parse_qs(db.query).get("sslmode", ["prefer"])[0])
        try:
            result = subprocess.run(["psql", "-X", "-w", "-tAc", "SELECT 1"], env=env, capture_output=True, timeout=8)
        except subprocess.TimeoutExpired:
            raise ConfigError("PostgreSQL: 账号/数据库检查超时") from None
        if result.returncode or result.stdout.strip() != b"1":
            raise ConfigError("PostgreSQL: 账号/密码/数据库检查失败（错误详情已隐藏）")
        print("PostgreSQL账号与数据库检查通过")
    else:
        print("PostgreSQL端口可达；未安装psql，未验证数据库凭据")
    redis = get_value(data, "spring.data.redis")
    with connect("Redis", redis["host"], redis["port"], get_value(redis, "ssl.enabled", False)) as connection:
        stream = connection.makefile("rb")
        def command(*parts):
            encoded = [str(part).encode() for part in parts]
            connection.sendall(b"*" + str(len(parts)).encode() + b"\r\n" + b"".join(
                b"$" + str(len(p)).encode() + b"\r\n" + p + b"\r\n" for p in encoded))
            return stream.readline(4096).strip()
        try:
            if redis.get("password"):
                credentials = (redis["username"], redis["password"]) if redis.get("username") else (redis["password"],)
                if command("AUTH", *credentials) != b"+OK":
                    raise ConfigError("Redis: 认证失败")
            if command("PING") != b"+PONG":
                raise ConfigError("Redis: PING失败，请检查认证和服务")
        except OSError:
            raise ConfigError("Redis: 认证/PING连接失败") from None
        finally:
            stream.close()
    print("Redis认证/PING检查通过")
    oss = url(get_value(data, "xjicloud.oss.endpoint"), {"http", "https"})
    with connect("OSS", oss.hostname, oss.port or (443 if oss.scheme == "https" else 80), oss.scheme == "https"):
        pass
    print("OSS网络/TLS检查通过；未验证Bucket及云凭据")
    if get_value(data, "xjicloud.sms.enabled"):
        with connect("PNVS", "dypnsapi.aliyuncs.com", 443, True):
            pass
        print("PNVS网络/TLS检查通过；RAM授权、签名/模板仍需真实短信验收，本命令不发短信")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("configure", "validate", "port", "storage-root"))
    parser.add_argument("--source", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--check-connections", action="store_true")
    args = parser.parse_args()
    try:
        existing = load_yaml(args.source) if args.source and args.source.exists() else {}
        if args.command == "configure":
            candidate = review_config(load_yaml(TEMPLATE), existing)
            if args.check_connections:
                check_connections(candidate)
            print("配置校验通过。确认保存？[Y/n]: ", end="", flush=True)
            answer = sys.stdin.readline()
            if not answer:
                raise ConfigError("输入结束，原配置未更改")
            if answer.strip().lower() not in ("", "y", "yes"):
                print("已取消，原配置未更改")
                return 20
            if args.output is None:
                raise ConfigError("需要指定输出文件")
            if candidate == existing and args.source == args.output:
                if args.output.is_symlink():
                    raise ConfigError("拒绝保存符号链接配置")
                args.output.chmod(0o600)
                print("未修改，沿用原配置")
            elif candidate == existing:
                # Preserve bytes/comments when the user keeps every setting.
                descriptor = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
                with os.fdopen(descriptor, "wb") as output:
                    output.write(args.source.read_bytes())
                print("未修改，沿用原配置")
            else:
                save_config(args.output, candidate)
                print("配置已原子保存；旧输出文件如存在已备份（密钥未输出）")
        else:
            if not existing:
                raise ConfigError("找不到非空生产配置")
            effective = validate_config(existing)
            if args.command == "port":
                print(get_value(effective, "server.port"))
            elif args.command == "storage-root":
                print(get_value(effective, "xjicloud.storage.root"))
            else:
                if args.check_connections:
                    check_connections(effective)
                print("生产YAML格式、类型与字段约束校验通过")
        return 0
    except (ConfigError, OSError) as error:
        message = str(error) if isinstance(error, ConfigError) else "配置文件读写失败，请检查路径与权限"
        print("配置错误: " + message, file=sys.stderr)
        return 1
    except (KeyboardInterrupt, EOFError):
        print("已中止，原配置未更改", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
