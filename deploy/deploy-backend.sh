#!/usr/bin/env bash
# XJICloud 后端一键构建 + 部署（Linux）
# 使用前请在 deploy/config/ 准备好 application-prod.yml
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIG_SRC="$SCRIPT_DIR/config"
BACKEND_DIR="$REPO_ROOT/backend"

SKIP_BUILD=0
NO_SYSTEMD=0

usage() {
  cat <<'EOF'
用法: deploy-backend.sh [选项]

  读取 deploy/config/application-prod.yml（及可选 backend.env、deploy.conf），
  构建 Spring Boot JAR 并部署到本机（默认 systemd 服务）。

选项:
  --skip-build    跳过 Maven 构建，使用 backend/target/ 下已有 JAR
  --no-systemd    不注册 systemd，仅复制 JAR 与配置到 INSTALL_DIR
  -h, --help      显示帮助

准备配置:
  cp deploy/config/application-prod.yml.example deploy/config/application-prod.yml
  # 编辑 application-prod.yml 后执行:
  sudo ./deploy/deploy-backend.sh
EOF
}

log() { printf '[deploy-backend] %s\n' "$*"; }
die() { printf '[deploy-backend] 错误: %s\n' "$*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build) SKIP_BUILD=1; shift ;;
    --no-systemd) NO_SYSTEMD=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "未知参数: $1（使用 --help 查看）" ;;
  esac
done

# 可选 deploy.conf
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

[[ -f "$PROD_YML_SRC" ]] || die "缺少 $PROD_YML_SRC，请先: cp deploy/config/application-prod.yml.example deploy/config/application-prod.yml"

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

run_root() {
  if [[ "$(id -u)" -eq 0 ]]; then
    "$@"
  elif command -v sudo >/dev/null 2>&1; then
    sudo "$@"
  else
    die "需要 root 权限安装到 $INSTALL_DIR，请使用 sudo 运行"
  fi
}

log "创建系统用户与目录..."
run_root useradd -r -s /usr/sbin/nologin "$SERVICE_USER" 2>/dev/null || true
run_root mkdir -p "$INSTALL_DIR" "$CONFIG_DIR" "$DATA_DIR"
run_root chown -R "$SERVICE_USER:$SERVICE_USER" "$DATA_DIR"

log "安装 JAR → $INSTALL_DIR/$JAR_NAME"
run_root install -m 0644 -o root -g root "$JAR_SRC" "$INSTALL_DIR/$JAR_NAME"

log "安装配置 → $CONFIG_DIR/application-prod.yml"
run_root install -m 0640 -o root -g "$SERVICE_USER" "$PROD_YML_SRC" "$CONFIG_DIR/application-prod.yml"

if [[ -f "$BACKEND_ENV_SRC" ]]; then
  log "安装环境文件 → $CONFIG_DIR/backend.env"
  run_root install -m 0640 -o root -g "$SERVICE_USER" "$BACKEND_ENV_SRC" "$CONFIG_DIR/backend.env"
else
  log "未提供 backend.env，跳过"
fi

run_root chown -R root:"$SERVICE_USER" "$INSTALL_DIR" "$CONFIG_DIR"
run_root chmod 0750 "$INSTALL_DIR" "$CONFIG_DIR"

if [[ "$NO_SYSTEMD" -eq 1 ]]; then
  log "(--no-systemd) 文件已就位，未注册 systemd 服务"
  log "手动启动示例:"
  log "  sudo -u $SERVICE_USER $JAVA_BIN -jar $INSTALL_DIR/$JAR_NAME \\"
  log "    --spring.profiles.active=prod \\"
  log "    --spring.config.additional-location=file:$CONFIG_DIR/application-prod.yml"
  exit 0
fi

if ! command -v systemctl >/dev/null 2>&1; then
  die "未找到 systemctl，请使用 --no-systemd 或安装 systemd"
fi

UNIT_PATH="/etc/systemd/system/${SERVICE_NAME}.service"
log "写入 systemd 单元 → $UNIT_PATH"

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
  die "systemctl status 非 active，请检查配置与 PostgreSQL/Redis 是否可达"
fi

if command -v curl >/dev/null 2>&1; then
  if curl -sf "http://127.0.0.1:${SERVER_PORT}/actuator/health" >/dev/null; then
    log "健康检查通过: http://127.0.0.1:${SERVER_PORT}/actuator/health"
  else
    log "警告: 健康检查未通过，服务可能仍在启动或端口/依赖有误"
  fi
fi

run_root systemctl status "$SERVICE_NAME" --no-pager -l || true
log "部署完成"
