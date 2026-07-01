#!/usr/bin/env bash
# XJICloud Framework installer — supports fresh install and in-place upgrade
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
INSTALL_DIR="/opt/xjicloud-framework"
CONFIG_DIR="/etc/xjicloud"
CONFIG_FILE="$CONFIG_DIR/framework.yml"
ENV_FILE="/etc/default/xjicloud-framework"
SERVICE_NAME="xjicloud-framework"
JAR_NAME="xjicloud-framework.jar"

usage() {
  cat <<'EOF'
用法:
  sudo ./install.sh              交互选择 install / upgrade
  sudo ./install.sh install      全新安装（生成新 secret，覆盖配置）
  sudo ./install.sh upgrade      升级 JAR（保留 framework.yml 与数据目录）

install: 适合首次部署，可在后端安装之前独立运行 Framework
upgrade: 仅替换 /opt/xjicloud-framework/*.jar 并重启服务
EOF
}

require_root() {
  if [[ $EUID -ne 0 ]]; then
    echo "请使用 root 运行: sudo $0"
    exit 1
  fi
}

require_build_tools() {
  command -v java >/dev/null || { echo "请先安装 Java 17+"; exit 1; }
  command -v mvn >/dev/null || { echo "请先安装 Maven 3.9+"; exit 1; }
}

# target/ 不纳入 git；安装/升级均在本地执行 mvn package
find_built_jar() {
  find "$ROOT/target" -maxdepth 1 -name 'xjicloud-framework-*.jar' ! -name '*.original' -type f 2>/dev/null | head -1
}

build_jar() {
  echo ">>> 本地构建 Framework（target/ 不在仓库中，需 Maven 编译）..." >&2
  cd "$ROOT"
  if ! mvn -q -DskipTests package; then
    echo "Maven 构建失败" >&2
    exit 1
  fi
  local built
  built="$(find_built_jar)"
  if [[ -z "$built" || ! -f "$built" ]]; then
    echo "构建失败：未找到 target/xjicloud-framework-*.jar" >&2
    exit 1
  fi
  echo ">>> 构建完成: $built" >&2
  printf '%s\n' "$built"
}

install_service_unit() {
  cp "$ROOT/systemd/xjicloud-framework.service" /etc/systemd/system/
  systemctl daemon-reload
  systemctl enable "$SERVICE_NAME"
}

do_upgrade() {
  require_root
  require_build_tools
  echo "=== XJICloud Framework 升级 ==="
  if [[ ! -f "$CONFIG_FILE" ]]; then
    echo "未找到 $CONFIG_FILE，请先执行 install 模式"
    exit 1
  fi
  local built
  built="$(build_jar)"
  mkdir -p "$INSTALL_DIR"
  if [[ -f "$INSTALL_DIR/$JAR_NAME" ]]; then
    cp "$INSTALL_DIR/$JAR_NAME" "$INSTALL_DIR/${JAR_NAME}.bak.$(date +%Y%m%d%H%M%S)"
  fi
  cp "$built" "$INSTALL_DIR/$JAR_NAME"
  install_service_unit
  systemctl restart "$SERVICE_NAME"
  echo ""
  echo "=== 升级完成 ==="
  echo "已保留 $CONFIG_FILE 与 /var/lib/xjicloud-framework 数据"
  systemctl --no-pager status "$SERVICE_NAME" || true
}

do_install() {
  require_root
  require_build_tools
  echo "=== XJICloud Framework 全新安装 ==="
  echo "说明: Framework 可先于后端部署，独立提供配置中心、节点管理与部署能力。"
  read -rp "模式 [master/slave] (默认 master): " MODE
  MODE="${MODE:-master}"
  MASTER_URL=""
  if [[ "$MODE" == "slave" ]]; then
    read -rp "Master 地址 (如 http://10.0.1.10:9090): " MASTER_URL
    [[ -n "$MASTER_URL" ]] || { echo "Slave 必须填写 Master 地址"; exit 1; }
  fi

  read -rp "admin 密码 (默认 admin): " ADMIN_PASS
  ADMIN_PASS="${ADMIN_PASS:-admin}"

  read -rp "Backend URL（可留空，后端未部署时直接回车）: " BACKEND_URL

  if [[ -f "$CONFIG_FILE" ]]; then
    read -rp "已存在配置文件，是否覆盖 secrets? [y/N]: " OVERWRITE
    if [[ "${OVERWRITE,,}" == "y" ]]; then
      AGENT_TOKEN="$(openssl rand -hex 16)"
      API_SECRET="$(openssl rand -hex 24)"
      BACKEND_SECRET="$(openssl rand -hex 24)"
      JWT_SECRET="$(openssl rand -hex 32)"
      ENC_KEY="$(openssl rand -hex 16)"
    else
      echo "保留现有 secret，仅更新 JAR 与 systemd"
      AGENT_TOKEN="$(grep 'agent-token:' "$CONFIG_FILE" | awk '{print $2}' || openssl rand -hex 16)"
      API_SECRET="$(grep 'api-secret:' "$CONFIG_FILE" | awk '{print $2}' || openssl rand -hex 24)"
      BACKEND_SECRET="$(grep 'backend-api-secret:' "$CONFIG_FILE" | awk '{print $2}' || openssl rand -hex 24)"
      JWT_SECRET="$(grep 'jwt-secret:' "$CONFIG_FILE" | awk '{print $2}' || openssl rand -hex 32)"
      ENC_KEY="$(grep 'encryption-key:' "$CONFIG_FILE" | awk '{print $2}' || openssl rand -hex 16)"
    fi
  else
    AGENT_TOKEN="$(openssl rand -hex 16)"
    API_SECRET="$(openssl rand -hex 24)"
    BACKEND_SECRET="$(openssl rand -hex 24)"
    JWT_SECRET="$(openssl rand -hex 32)"
    ENC_KEY="$(openssl rand -hex 16)"
  fi

  local built
  built="$(build_jar)"

  mkdir -p "$INSTALL_DIR" "$CONFIG_DIR" /var/lib/xjicloud-framework
  cp "$built" "$INSTALL_DIR/$JAR_NAME"

  cat > "$CONFIG_FILE" <<EOF
xjicloud:
  framework:
    mode: $MODE
    listen-port: 9090
    master-url: ${MASTER_URL:-http://127.0.0.1:9090}
    agent-token: $AGENT_TOKEN
    data-dir: /var/lib/xjicloud-framework
    backend-url: ${BACKEND_URL:-}
    backend-api-secret: $BACKEND_SECRET
    api-secret: $API_SECRET
    jwt-secret: $JWT_SECRET
    encryption-key: $ENC_KEY
    admin:
      default-username: admin
      default-password: $ADMIN_PASS
      force-password-change: true
    aliyun:
      auto-scale-enabled: true
      scale-down-delay-minutes: 5
EOF

  cat > "$ENV_FILE" <<EOF
JAVA_OPTS=-Xms256m -Xmx512m
EOF

  install_service_unit
  systemctl restart "$SERVICE_NAME"

  echo ""
  echo "=== 安装完成 ==="
  echo "管理面板: http://<本机IP>:9090  (admin / $ADMIN_PASS)"
  echo "Framework 可独立使用；后端部署后在配置中心填写 Backend Public URL"
  echo ""
  echo "写入后端 bootstrap 配置 (application-prod.yml):"
  echo "  xjicloud.framework.enabled: true"
  echo "  xjicloud.framework.master-url: http://<master-ip>:9090"
  echo "  xjicloud.framework.api-secret: $API_SECRET"
  echo "  xjicloud.framework.backend-api-secret: $BACKEND_SECRET"
  echo "请放通防火墙 TCP 9090"
  echo ""
  echo "后续升级请执行: sudo $0 upgrade"
}

MODE_ARG="${1:-}"

case "$MODE_ARG" in
  install) do_install ;;
  upgrade) do_upgrade ;;
  -h|--help|help) usage ;;
  "")
    require_root
    echo "选择操作:"
    echo "  1) install  全新安装"
    echo "  2) upgrade  保留配置升级 JAR"
    read -rp "请输入 [1/2] (默认 1): " CHOICE
    CHOICE="${CHOICE:-1}"
    if [[ "$CHOICE" == "2" ]]; then do_upgrade; else do_install; fi
    ;;
  *)
    echo "未知参数: $MODE_ARG"
    usage
    exit 1
    ;;
esac
