#!/usr/bin/env bash
# 从 .env 加载配置并启动 eci-manager
# 用法: ./scripts/start.sh [mock|cloud]
#   mock  → SPRING_PROFILES_ACTIVE=dev
#   cloud → SPRING_PROFILES_ACTIVE=cloud,local
#   无参数则沿用 .env 里的 profile
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MODE="${1:-}"
case "$MODE" in
  "")
    ;;
  mock)
    export SPRING_PROFILES_ACTIVE=dev
    ;;
  cloud)
    export SPRING_PROFILES_ACTIVE=cloud,local
    ;;
  *)
    echo "usage: $0 [mock|cloud]" >&2
    exit 1
    ;;
esac

if [[ ! -f "$ROOT/.env" ]]; then
  echo "未找到 .env，请先: cp .env.example .env 并填写" >&2
  exit 1
fi

# shellcheck disable=SC1091
source "$ROOT/scripts/load-env.sh"

PORT="${SERVER_PORT:-8081}"
CONSOLE_URL="http://127.0.0.1:${PORT}/"

echo "profiles=${SPRING_PROFILES_ACTIVE:-dev} mock=${ECI_MOCK:-<profile>} region=${ECI_REGION_ID:-}"
echo "eci-manager → ${ECI_BASE}"
echo "console → ${CONSOLE_URL}"
echo "云凭证: ECI_ACCESS_KEY_ID=${ECI_ACCESS_KEY_ID:+已设置} ALIBABA_CLOUD_ACCESS_KEY_ID=${ALIBABA_CLOUD_ACCESS_KEY_ID:+已设置}"

exec mvn spring-boot:run
