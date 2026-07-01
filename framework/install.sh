#!/usr/bin/env bash
# XJICloud Framework interactive installer
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
INSTALL_DIR="/opt/xjicloud-framework"
CONFIG_DIR="/etc/xjicloud"
CONFIG_FILE="$CONFIG_DIR/framework.yml"
ENV_FILE="/etc/default/xjicloud-framework"

if [[ $EUID -ne 0 ]]; then
  echo "请使用 root 运行: sudo $0"
  exit 1
fi

command -v java >/dev/null || { echo "请先安装 Java 17+"; exit 1; }
command -v mvn >/dev/null || { echo "请先安装 Maven 3.9+"; exit 1; }

echo "=== XJICloud Framework 安装 ==="
read -rp "模式 [master/slave] (默认 master): " MODE
MODE="${MODE:-master}"
MASTER_URL=""
if [[ "$MODE" == "slave" ]]; then
  read -rp "Master 地址 (如 http://10.0.1.10:9090): " MASTER_URL
fi

read -rp "admin 密码 (默认 admin): " ADMIN_PASS
ADMIN_PASS="${ADMIN_PASS:-admin}"

read -rp "PostgreSQL JDBC URL (可跳过): " DB_URL
read -rp "Redis 主机 (可跳过): " REDIS_HOST
read -rp "OSS endpoint (可跳过): " OSS_EP

read -rp "Backend URL (默认 http://127.0.0.1:8080): " BACKEND_URL
BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"

AGENT_TOKEN="$(openssl rand -hex 16)"
API_SECRET="$(openssl rand -hex 24)"
BACKEND_SECRET="$(openssl rand -hex 24)"
JWT_SECRET="$(openssl rand -hex 32)"
ENC_KEY="$(openssl rand -hex 16)"

echo "构建 Framework..."
cd "$ROOT"
mvn -q -DskipTests package

mkdir -p "$INSTALL_DIR" "$CONFIG_DIR" /var/lib/xjicloud-framework
cp target/xjicloud-framework-*.jar "$INSTALL_DIR/xjicloud-framework.jar"

cat > "$CONFIG_FILE" <<EOF
xjicloud:
  framework:
    mode: $MODE
    listen-port: 9090
    master-url: ${MASTER_URL:-http://127.0.0.1:9090}
    agent-token: $AGENT_TOKEN
    data-dir: /var/lib/xjicloud-framework
    backend-url: $BACKEND_URL
    backend-api-secret: $BACKEND_SECRET
    api-secret: $API_SECRET
    jwt-secret: $JWT_SECRET
    encryption-key: $ENC_KEY
    admin:
      default-username: admin
      default-password: $ADMIN_PASS
      force-password-change: true
EOF

cat > "$ENV_FILE" <<EOF
JAVA_OPTS=-Xms256m -Xmx512m
EOF

cp "$ROOT/systemd/xjicloud-framework.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable xjicloud-framework
systemctl restart xjicloud-framework

echo ""
echo "=== 安装完成 ==="
echo "管理面板: http://<本机IP>:9090  (admin / $ADMIN_PASS，首次登录需改密)"
echo "Framework API Secret (写入 backend application-prod.yml):"
echo "  xjicloud.framework.api-secret: $API_SECRET"
echo "  xjicloud.framework.master-url: http://<master-ip>:9090"
echo "Backend 轮询 Secret (写入 framework.yml backend-api-secret，已自动设置):"
echo "  $BACKEND_SECRET"
echo "请放通防火墙 TCP 9090"
if [[ -n "$DB_URL" ]]; then
  echo "安装后请在面板「配置中心」完善数据库/OSS 参数"
fi
