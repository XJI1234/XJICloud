#!/usr/bin/env bash
# XJICloud 后端交互式配置 + 构建 + 部署（Linux）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIG_SRC="$SCRIPT_DIR/config"
BACKEND_DIR="$REPO_ROOT/services/backend"

SKIP_BUILD=0
NO_SYSTEMD=0
NON_INTERACTIVE=0
UPDATE_CONFIG=0
CHECK_CONNECTIONS=0
COMMAND=""

usage() {
  cat <<'EOF'
用法: deploy-backend.sh [子命令] [选项]

子命令:
  configure   交互生成 deploy/config/application-prod.yml
  install     构建并安装到本机（需已有 application-prod.yml）
  upgrade     逐项审阅已安装配置，回车沿用；校验/备份后换JAR并重启
  validate    仅校验本地生产YAML（不存在时检查已安装配置）
  (无子命令)  已安装则upgrade，否则configure后install

选项:
  --skip-build         跳过 Maven 构建
  --no-systemd         不注册 systemd，仅复制 JAR 与配置
  --non-interactive    不提问；configure 需已有 yml，install 直接部署
  --update-config      非交互upgrade时备份并应用本地完整YAML
  --check-connections  附加数据库/Redis/OSS/PNVS网络检查（不发短信）
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

java_major_version() {
  local java_bin="$1"
  local ver major
  ver=$("$java_bin" -version 2>&1 | awk -F '"' '/version/ {print $2; exit}')
  major="${ver%%.*}"
  if [[ "$major" == "1" ]]; then
    major="${ver#*.}"
    major="${major%%.*}"
  fi
  printf '%s' "$major"
}

require_java17() {
  local java_bin="$1"
  local major
  command -v "$java_bin" >/dev/null 2>&1 || return 1
  major="$(java_major_version "$java_bin")"
  [[ "$major" =~ ^[0-9]+$ ]] && [[ "$major" -ge 17 ]]
}

find_java17() {
  local candidates=(
    "${JAVA_BIN:-}"
    "${JAVA_HOME:+$JAVA_HOME/bin/java}"
    java
    /usr/lib/jvm/java-17-openjdk-amd64/bin/java
    /usr/lib/jvm/java-17-openjdk/bin/java
    /usr/lib/jvm/temurin-17-jdk-amd64/bin/java
    /usr/lib/jvm/temurin-17-jdk/bin/java
  )
  local c
  for c in "${candidates[@]}"; do
    [[ -n "$c" ]] || continue
    if require_java17 "$c"; then
      printf '%s' "$c"
      return 0
    fi
  done
  return 1
}

java17_install_hint() {
  cat <<'EOF'

需要 Java 17 或更高版本（Spring Boot 3.3 要求）。

当前系统默认 java 版本过低。请安装 JDK 17 后重试，例如:

  # Alibaba Cloud Linux / CentOS / RHEL
  sudo yum install -y java-17-openjdk java-17-openjdk-devel
  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
  export PATH="$JAVA_HOME/bin:$PATH"

  # Ubuntu / Debian
  sudo apt update
  sudo apt install -y openjdk-17-jdk
  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
  export PATH="$JAVA_HOME/bin:$PATH"

验证: java -version   # 应显示 17 或 21

或在 deploy/config/deploy.conf 中指定:
  JAVA_BIN=/usr/lib/jvm/java-17-openjdk/bin/java
EOF
}

resolve_java_bin() {
  local found
  if require_java17 "${JAVA_BIN:-java}"; then
    printf '%s' "${JAVA_BIN:-java}"
    return 0
  fi
  if found="$(find_java17)"; then
    log "使用 Java 17+: $found（默认 java 版本过低）" >&2
    printf '%s' "$found"
    return 0
  fi
  java17_install_hint >&2
  die "未找到 Java 17+。请先安装 openjdk-17 并确认 java -version"
}

require_config_editor() {
  PYTHON_BIN="$(command -v "${PYTHON_BIN:-python3}")" || die "需要Python3.8+与PyYAML，请安装或设置PYTHON_BIN"
  "$PYTHON_BIN" -c 'import sys; sys.exit(0 if sys.version_info >= (3, 8) else 1)' || die "Python版本过低，需要3.8+，可在deploy.conf设置PYTHON_BIN"
  "$PYTHON_BIN" -c 'import yaml' 2>/dev/null || die "缺少PyYAML，请安装python3-yaml（RHEL使用python3-pyyaml），确保安装到所选解释器"
  [[ -f "$SCRIPT_DIR/backend_config.py" ]] || die "缺少deploy/backend_config.py，请完整更新deploy目录"
}

load_deploy_settings() {
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
}

validate_config_file() {
  local arguments=(validate --source "$1")
  [[ "$CHECK_CONNECTIONS" -eq 0 ]] || arguments+=(--check-connections)
  "$PYTHON_BIN" "$SCRIPT_DIR/backend_config.py" "${arguments[@]}"
}

do_configure() {
  require_config_editor
  local file="$CONFIG_SRC/application-prod.yml"
  if [[ "$NON_INTERACTIVE" -eq 1 ]]; then
    [[ -f "$file" ]] || die "非交互configure需要已有application-prod.yml"
    validate_config_file "$file"
    return
  fi
  local arguments=(configure --source "$file" --output "$file")
  [[ "$CHECK_CONNECTIONS" -eq 0 ]] || arguments+=(--check-connections)
  "$PYTHON_BIN" "$SCRIPT_DIR/backend_config.py" "${arguments[@]}" || die "配置未保存；取消、输入中断或校验未通过"
  log "下一步: sudo bash $0 install；数据库首次建库和配置说明见docs/PHONE_REGISTRATION_DEPLOYMENT.md"
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

  load_deploy_settings
  require_config_editor
  if run_root test -s "$CONFIG_DIR/backend.env"; then
    die "检测到旧 backend.env：请先将其中业务配置迁入 application-prod.yml，再将旧文件改名备份；新服务不再加载它"
  fi
  PROD_YML_SRC="$CONFIG_SRC/application-prod.yml"
  if [[ "$mode" == "upgrade" && "$NON_INTERACTIVE" -eq 0 ]]; then
    run_root test -f "$CONFIG_DIR/application-prod.yml" || die "升级需已有生产配置，首次部署请用install"
    # Drafts stay private and never replace the installed YAML until all checks pass.
    CONFIG_DRAFT_DIR="$(mktemp -d)"
    trap 'rm -f -- "$CONFIG_DRAFT_DIR/application-prod.yml"; rmdir -- "$CONFIG_DRAFT_DIR"' EXIT
    run_root "$PYTHON_BIN" "$SCRIPT_DIR/backend_config.py" configure --source "$CONFIG_DIR/application-prod.yml" --output "$CONFIG_DRAFT_DIR/application-prod.yml" || die "升级已取消，生产配置和JAR未更改"
    PROD_YML_SRC="$CONFIG_DRAFT_DIR/application-prod.yml"
    if run_root cmp -s "$PROD_YML_SRC" "$CONFIG_DIR/application-prod.yml"; then
      UPDATE_CONFIG=0
    else
      UPDATE_CONFIG=1
    fi
  elif [[ "$mode" == "upgrade" && "$UPDATE_CONFIG" -eq 0 ]]; then
    PROD_YML_SRC="$CONFIG_DIR/application-prod.yml"
  fi
  run_root test -f "$PROD_YML_SRC" || die "缺少生产配置，请先运行configure"
  # Reject malformed/invalid configuration before building or replacing any artifact.
  local validation=(validate --source "$PROD_YML_SRC")
  [[ "$CHECK_CONNECTIONS" -eq 0 ]] || validation+=(--check-connections)
  run_root "$PYTHON_BIN" "$SCRIPT_DIR/backend_config.py" "${validation[@]}"
  SERVER_PORT="$(run_root "$PYTHON_BIN" "$SCRIPT_DIR/backend_config.py" port --source "$PROD_YML_SRC")"
  DATA_DIR="$(run_root "$PYTHON_BIN" "$SCRIPT_DIR/backend_config.py" storage-root --source "$PROD_YML_SRC")"

  JAVA_BIN="$(resolve_java_bin)"
  export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$JAVA_BIN" 2>/dev/null || echo "$JAVA_BIN")")")}"
  log "Java: $JAVA_BIN ($("$JAVA_BIN" -version 2>&1 | head -1))"

  if [[ "$SKIP_BUILD" -eq 0 ]]; then
    if command -v mvn >/dev/null 2>&1; then
      MVN=(mvn)
    elif [[ -x "$BACKEND_DIR/mvnw" ]]; then
      MVN=("$BACKEND_DIR/mvnw")
    else
      die "未找到 mvn 或 services/backend/mvnw，请安装 Maven 3.9+ 或使用 --skip-build"
    fi
    log "开始 Maven 构建..."
    (cd "$BACKEND_DIR" && JAVA_HOME="$JAVA_HOME" PATH="$(dirname "$JAVA_BIN"):$PATH" "${MVN[@]}" -B package)
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

  if [[ "$mode" != "upgrade" || "$UPDATE_CONFIG" -eq 1 ]]; then
    if run_root test -f "$CONFIG_DIR/application-prod.yml"; then
      run_root cp -p "$CONFIG_DIR/application-prod.yml" "$CONFIG_DIR/application-prod.yml.bak.$(date +%Y%m%d%H%M%S%N)"
      log "已备份旧生产配置"
    fi
    log "安装配置 → $CONFIG_DIR/application-prod.yml"
    run_root install -m 0640 -o root -g "$SERVICE_USER" "$PROD_YML_SRC" "$CONFIG_DIR/application-prod.yml"
  else
    log "upgrade 模式：保留 $CONFIG_DIR/application-prod.yml"
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
    validate) COMMAND="validate"; shift ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    --no-systemd) NO_SYSTEMD=1; shift ;;
    --non-interactive) NON_INTERACTIVE=1; shift ;;
    --update-config) UPDATE_CONFIG=1; shift ;;
    --check-connections) CHECK_CONNECTIONS=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "未知参数: $1（使用 --help）" ;;
  esac
done

load_deploy_settings

case "$COMMAND" in
  validate)
    require_config_editor
    file="$CONFIG_SRC/application-prod.yml"
    [[ -f "$file" ]] || file="$CONFIG_DIR/application-prod.yml"
    validate_config_file "$file"
    ;;
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
    if run_root test -f "$CONFIG_DIR/application-prod.yml"; then
      do_install_or_upgrade upgrade
    else
      do_configure
      do_install_or_upgrade install
    fi
    ;;
esac
