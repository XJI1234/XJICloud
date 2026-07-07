#!/usr/bin/env bash
# XJICloud 后端交互式配置 + 构建 + 部署（Linux）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIG_SRC="$SCRIPT_DIR/config"
BACKEND_DIR="$REPO_ROOT/backend"

SKIP_BUILD=0
NO_SYSTEMD=0
NON_INTERACTIVE=0
COMMAND=""

usage() {
  cat <<'EOF'
用法: deploy-backend.sh [子命令] [选项]

子命令:
  configure   交互生成 deploy/config/application-prod.yml
  install     构建并安装到本机（需已有 application-prod.yml）
  upgrade     保留 /etc/xjicloud 配置，仅替换 JAR 并重启
  (无子命令)  交互 configure 后 install

选项:
  --skip-build         跳过 Maven 构建
  --no-systemd         不注册 systemd，仅复制 JAR 与配置
  --non-interactive    不提问；configure 需已有 yml，install 直接部署
  -h, --help           显示帮助

推荐首次部署:
  sudo ./deploy/deploy-backend.sh

手动准备配置（高级）:
  cp deploy/config/application-prod.yml.example deploy/config/application-prod.yml
  # 编辑后:
  sudo ./deploy/deploy-backend.sh install --non-interactive
EOF
}

log() { printf '[deploy-backend] %s\n' "$*"; }
die() { printf '[deploy-backend] 错误: %s\n' "$*" >&2; exit 1; }

prompt_with_default() {
  local label="$1"
  local default="$2"
  local value=""
  if [[ "$NON_INTERACTIVE" -eq 1 ]]; then
    printf '%s' "$default"
    return
  fi
  read -rp "$label [推荐: $default]: " value
  if [[ -z "$value" ]]; then
    value="$default"
  fi
  printf '%s' "$value"
}

prompt_required() {
  local label="$1"
  local value=""
  if [[ "$NON_INTERACTIVE" -eq 1 ]]; then
    die "非交互模式无法设置: $label"
  fi
  while [[ -z "$value" ]]; do
    read -rsp "$label: " value
    echo ""
  done
  printf '%s' "$value"
}

rand_hex() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex "${1:-16}"
  else
    head -c "$1" /dev/urandom | od -An -tx1 | tr -d ' \n'
  fi
}

rand_base64() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 32 | tr -d '\n'
  else
    rand_hex 32
  fi
}

yaml_quote() {
  local s="$1"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  printf '"%s"' "$s"
}

write_application_prod_yml() {
  local out="$CONFIG_SRC/application-prod.yml"
  mkdir -p "$CONFIG_SRC"
  cat >"$out" <<EOF
# 由 deploy-backend.sh configure 生成 — 勿提交到 Git
# 场景: ${DEPLOY_SCENARIO_LABEL}

spring:
  profiles:
    active: prod
  datasource:
    url: $(yaml_quote "$DB_URL")
    username: $(yaml_quote "$DB_USER")
    password: $(yaml_quote "$DB_PASSWORD")
  data:
    redis:
      host: $(yaml_quote "$REDIS_HOST")
      port: ${REDIS_PORT}
      password: $(yaml_quote "$REDIS_PASSWORD")
  servlet:
    multipart:
      max-file-size: 2GB
      max-request-size: 2GB
  jpa:
    hibernate:
      ddl-auto: update

server:
  port: ${SERVER_PORT}

xjicloud:
  jwt:
    secret: $(yaml_quote "$JWT_SECRET")
    expiration-ms: 86400000
  storage:
    root: $(yaml_quote "$STORAGE_ROOT")
  cors:
    allowed-origins: $(yaml_quote "$CORS_ORIGINS")
  oss:
    endpoint: $(yaml_quote "$OSS_ENDPOINT")
    region: $(yaml_quote "$OSS_REGION")
    bucket: $(yaml_quote "$OSS_BUCKET")
    access-key: $(yaml_quote "$OSS_ACCESS_KEY")
    secret-key: $(yaml_quote "$OSS_SECRET_KEY")
    path-style-access: ${OSS_PATH_STYLE}
    presign-expiration-minutes: 120
  redis:
    queue-key: xjicloud:jobs
  worker:
    shared-secret: $(yaml_quote "$WORKER_SECRET")
    heartbeat-timeout-sec: 60
    poll-timeout-sec: 25
  admin:
    default-username: $(yaml_quote "$ADMIN_USERNAME")
    default-password: $(yaml_quote "$ADMIN_PASSWORD")
    sync-password-on-startup: false
EOF
  chmod 600 "$out" 2>/dev/null || true
  log "已写入 $out"
}

write_install_summary() {
  local out="$CONFIG_SRC/install-summary.txt"
  cat >"$out" <<EOF
XJICloud 后端安装摘要
生成时间: $(date -Iseconds 2>/dev/null || date)
部署场景: ${DEPLOY_SCENARIO_LABEL}
配置文件: $CONFIG_SRC/application-prod.yml
安装目录: ${INSTALL_DIR:-/opt/xjicloud/backend}
数据目录: ${STORAGE_ROOT}
服务端口: ${SERVER_PORT}
CORS: ${CORS_ORIGINS}
OSS endpoint: ${OSS_ENDPOINT}
OSS bucket: ${OSS_BUCKET}
数据库: ${DB_URL}
Redis: ${REDIS_HOST}:${REDIS_PORT}

注意: 密钥未写入本摘要，请妥善保管 application-prod.yml
EOF
  chmod 600 "$out" 2>/dev/null || true
}

print_postgres_hint() {
  cat <<'EOF'

PostgreSQL 首次部署请确保数据库与用户已创建，并授予 public schema 权限:

  sudo -u postgres psql <<'SQL'
  CREATE USER xjicloud WITH PASSWORD '你的密码';
  CREATE DATABASE xjicloud OWNER xjicloud;
  \c xjicloud
  GRANT ALL ON SCHEMA public TO xjicloud;
  GRANT CREATE ON SCHEMA public TO xjicloud;
  SQL

EOF
}

do_configure() {
  if [[ "$NON_INTERACTIVE" -eq 1 && -f "$CONFIG_SRC/application-prod.yml" ]]; then
    log "非交互模式且已有 application-prod.yml，跳过 configure"
    return 0
  fi
  if [[ "$NON_INTERACTIVE" -eq 1 ]]; then
    die "非交互 configure 需要已存在的 deploy/config/application-prod.yml"
  fi

  echo ""
  echo "=== XJICloud 后端配置向导 ==="
  echo "1) 同机一体 / 预生产（PostgreSQL + Redis + MinIO 本机或内网）【推荐】"
  echo "2) 分机生产（RDS + 云 Redis + 阿里云 OSS）"
  local scenario
  scenario="$(prompt_with_default "请选择部署场景 [1/2]" "1")"

  if [[ "$scenario" == "2" ]]; then
    DEPLOY_SCENARIO_LABEL="分机生产（RDS + 阿里云 OSS）"
    DB_URL="$(prompt_with_default "PostgreSQL JDBC URL" "jdbc:postgresql://rm-xxxxx.pg.rds.aliyuncs.com:5432/xjicloud")"
    REDIS_HOST="$(prompt_with_default "Redis 主机" "r-xxxxx.redis.rds.aliyuncs.com")"
    OSS_ENDPOINT="$(prompt_with_default "OSS Endpoint" "https://oss-cn-hangzhou.aliyuncs.com")"
    OSS_REGION="$(prompt_with_default "OSS Region" "cn-hangzhou")"
    OSS_PATH_STYLE="false"
  else
    DEPLOY_SCENARIO_LABEL="同机一体 / 预生产"
    DB_URL="$(prompt_with_default "PostgreSQL JDBC URL" "jdbc:postgresql://127.0.0.1:5432/xjicloud")"
    REDIS_HOST="$(prompt_with_default "Redis 主机" "127.0.0.1")"
    OSS_ENDPOINT="$(prompt_with_default "OSS Endpoint（MinIO）" "http://127.0.0.1:9000")"
    OSS_REGION="$(prompt_with_default "OSS Region" "us-east-1")"
    OSS_PATH_STYLE="true"
  fi

  DB_USER="$(prompt_with_default "数据库用户名" "xjicloud")"
  DB_PASSWORD="$(prompt_with_default "数据库密码" "$(rand_hex 12)")"
  REDIS_PORT="$(prompt_with_default "Redis 端口" "6379")"
  REDIS_PASSWORD="$(prompt_with_default "Redis 密码（无则留空）" "")"
  OSS_BUCKET="$(prompt_with_default "OSS Bucket" "xjicloud")"
  OSS_ACCESS_KEY="$(prompt_with_default "OSS Access Key" "minioadmin")"
  OSS_SECRET_KEY="$(prompt_with_default "OSS Secret Key" "minioadmin")"
  CORS_ORIGINS="$(prompt_with_default "前端 CORS 域名（逗号分隔）" "https://your-domain.example")"
  STORAGE_ROOT="$(prompt_with_default "本地模型存储目录" "/data/xjicloud")"
  SERVER_PORT="$(prompt_with_default "后端监听端口" "8080")"
  ADMIN_USERNAME="$(prompt_with_default "Admin 用户名" "admin")"

  echo ""
  echo "Admin 初始密码（必填，用于管理后台登录）:"
  ADMIN_PASSWORD="$(prompt_required "Admin 密码")"

  JWT_SECRET="$(rand_base64)"
  WORKER_SECRET="$(rand_hex 24)"
  log "已自动生成 JWT secret 与 Worker shared-secret"

  if [[ -f "$CONFIG_SRC/application-prod.yml" ]]; then
    read -rp "已存在 application-prod.yml，是否覆盖? [y/N]: " overwrite
    if [[ "${overwrite,,}" != "y" ]]; then
      log "已取消覆盖"
      return 0
    fi
  fi

  write_application_prod_yml
  write_install_summary
  print_postgres_hint
  log "配置完成。下一步: sudo $0 install"
}

run_root() {
  if [[ "$(id -u)" -eq 0 ]]; then
    "$@"
  elif command -v sudo >/dev/null 2>&1; then
    sudo "$@"
  else
    die "需要 root 权限安装到 $INSTALL_DIR，请使用 sudo 运行"
  fi
}

do_install_or_upgrade() {
  local mode="${1:-install}"

  if [[ -f "$CONFIG_SRC/deploy.conf" ]]; then
    # shellcheck source=/dev/null
    source "$CONFIG_SRC/deploy.conf"
  fi

  INSTALL_DIR="${INSTALL_DIR:-/opt/xjicloud/backend}"
  CONFIG_DIR="${CONFIG_DIR:-/etc/xjicloud}"
  DATA_DIR="${DATA_DIR:-/data/xjicloud}"
  SERVICE_USER="${SERVICE_USER:-xjicloud}"
  SERVICE_NAME="${SERVICE_NAME:-xjicloud-backend}"
  JAR_NAME="${JAR_NAME:-xjicloud-backend-1.0.0.jar}"
  JAVA_BIN="${JAVA_BIN:-/usr/bin/java}"
  SERVER_PORT="${SERVER_PORT:-8080}"

  PROD_YML_SRC="$CONFIG_SRC/application-prod.yml"
  BACKEND_ENV_SRC="$CONFIG_SRC/backend.env"

  [[ -f "$PROD_YML_SRC" ]] || die "缺少 $PROD_YML_SRC，请先运行: sudo $0 configure"

  command -v "$JAVA_BIN" >/dev/null 2>&1 || die "未找到 Java: $JAVA_BIN（需要 Java 17+）"

  if [[ "$SKIP_BUILD" -eq 0 ]]; then
    if command -v mvn >/dev/null 2>&1; then
      MVN=(mvn)
    elif [[ -x "$BACKEND_DIR/mvnw" ]]; then
      MVN=("$BACKEND_DIR/mvnw")
    else
      die "未找到 mvn 或 backend/mvnw，请安装 Maven 3.9+ 或使用 --skip-build"
    fi
    log "开始 Maven 构建..."
    (cd "$BACKEND_DIR" && "${MVN[@]}" -DskipTests package)
  else
    log "跳过构建 (--skip-build)"
  fi

  JAR_SRC="$BACKEND_DIR/target/$JAR_NAME"
  [[ -f "$JAR_SRC" ]] || die "找不到 JAR: $JAR_SRC"

  log "创建系统用户与目录..."
  run_root useradd -r -s /usr/sbin/nologin "$SERVICE_USER" 2>/dev/null || true
  run_root mkdir -p "$INSTALL_DIR" "$CONFIG_DIR" "$DATA_DIR"
  run_root chown -R "$SERVICE_USER:$SERVICE_USER" "$DATA_DIR"

  if [[ "$mode" == "upgrade" && -f "$INSTALL_DIR/$JAR_NAME" ]]; then
    run_root cp "$INSTALL_DIR/$JAR_NAME" "$INSTALL_DIR/${JAR_NAME}.bak.$(date +%Y%m%d%H%M%S)"
    log "已备份旧 JAR"
  fi

  log "安装 JAR → $INSTALL_DIR/$JAR_NAME"
  run_root install -m 0644 -o root -g root "$JAR_SRC" "$INSTALL_DIR/$JAR_NAME"

  if [[ "$mode" != "upgrade" ]]; then
    log "安装配置 → $CONFIG_DIR/application-prod.yml"
    run_root install -m 0640 -o root -g "$SERVICE_USER" "$PROD_YML_SRC" "$CONFIG_DIR/application-prod.yml"
  else
    log "upgrade 模式：保留 $CONFIG_DIR/application-prod.yml"
  fi

  if [[ -f "$BACKEND_ENV_SRC" ]]; then
    log "安装环境文件 → $CONFIG_DIR/backend.env"
    run_root install -m 0640 -o root -g "$SERVICE_USER" "$BACKEND_ENV_SRC" "$CONFIG_DIR/backend.env"
  fi

  run_root chown -R root:"$SERVICE_USER" "$INSTALL_DIR" "$CONFIG_DIR"
  run_root chmod 0750 "$INSTALL_DIR" "$CONFIG_DIR"

  if [[ "$NO_SYSTEMD" -eq 1 ]]; then
    log "(--no-systemd) 文件已就位"
    log "手动启动: sudo -u $SERVICE_USER $JAVA_BIN -jar $INSTALL_DIR/$JAR_NAME --spring.profiles.active=prod --spring.config.additional-location=file:$CONFIG_DIR/application-prod.yml"
    return 0
  fi

  command -v systemctl >/dev/null 2>&1 || die "未找到 systemctl，请使用 --no-systemd"

  UNIT_PATH="/etc/systemd/system/${SERVICE_NAME}.service"
  log "写入 systemd → $UNIT_PATH"
  run_root tee "$UNIT_PATH" >/dev/null <<EOF
[Unit]
Description=XJICloud Spring Boot Backend
After=network.target redis.service postgresql.service
Wants=redis.service

[Service]
Type=simple
User=${SERVICE_USER}
Group=${SERVICE_USER}
WorkingDirectory=${INSTALL_DIR}
EnvironmentFile=-${CONFIG_DIR}/backend.env
ExecStart=${JAVA_BIN} -jar ${INSTALL_DIR}/${JAR_NAME} \\
  --spring.profiles.active=prod \\
  --spring.config.additional-location=file:${CONFIG_DIR}/application-prod.yml
Restart=on-failure
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF

  log "重载并启动 ${SERVICE_NAME}..."
  run_root systemctl daemon-reload
  run_root systemctl enable "$SERVICE_NAME"
  run_root systemctl restart "$SERVICE_NAME"

  sleep 2
  if run_root systemctl is-active --quiet "$SERVICE_NAME"; then
    log "服务已运行"
  else
    log "服务可能启动失败，最近日志:"
    run_root journalctl -u "$SERVICE_NAME" -n 30 --no-pager || true
    die "systemctl 非 active，请检查 PostgreSQL/Redis/OSS 配置"
  fi

  if command -v curl >/dev/null 2>&1; then
    if curl -sf "http://127.0.0.1:${SERVER_PORT}/actuator/health" >/dev/null; then
      log "健康检查通过: http://127.0.0.1:${SERVER_PORT}/actuator/health"
    else
      log "警告: 健康检查未通过，服务可能仍在启动"
    fi
  fi

  run_root systemctl status "$SERVICE_NAME" --no-pager -l || true
  log "部署完成 ($mode)"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    configure) COMMAND="configure"; shift ;;
    install) COMMAND="install"; shift ;;
    upgrade) COMMAND="upgrade"; shift ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    --no-systemd) NO_SYSTEMD=1; shift ;;
    --non-interactive) NON_INTERACTIVE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "未知参数: $1（使用 --help）" ;;
  esac
done

case "$COMMAND" in
  configure)
    do_configure
    ;;
  install)
    do_install_or_upgrade install
    ;;
  upgrade)
    do_install_or_upgrade upgrade
    ;;
  "")
    do_configure
    do_install_or_upgrade install
    ;;
esac
