#!/usr/bin/env bash
# 加载 eci-manager/.env（若存在），并映射阿里云 SDK 所需环境变量。
# 无 .env 时使用 Mock 默认值，便于 curl 联调。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ECI_ENV_FILE:-$ROOT/.env}"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
else
  export ECI_API_KEY="${ECI_API_KEY:-dev-eci-api-key-change-me}"
  export ECI_BASE="${ECI_BASE:-http://127.0.0.1:8081}"
fi

# 兼容旧名：ECI_ALIYUN_* → ALIBABA_CLOUD_*
if [[ -n "${ECI_ALIYUN_ACCESS_KEY_ID:-}" && -z "${ALIBABA_CLOUD_ACCESS_KEY_ID:-}" ]]; then
  export ALIBABA_CLOUD_ACCESS_KEY_ID="$ECI_ALIYUN_ACCESS_KEY_ID"
fi
if [[ -n "${ECI_ALIYUN_ACCESS_KEY_SECRET:-}" && -z "${ALIBABA_CLOUD_ACCESS_KEY_SECRET:-}" ]]; then
  export ALIBABA_CLOUD_ACCESS_KEY_SECRET="$ECI_ALIYUN_ACCESS_KEY_SECRET"
fi
if [[ -n "${ECI_ALIYUN_SECURITY_TOKEN:-}" && -z "${ALIBABA_CLOUD_SECURITY_TOKEN:-}" ]]; then
  export ALIBABA_CLOUD_SECURITY_TOKEN="$ECI_ALIYUN_SECURITY_TOKEN"
fi

export ECI_API_KEY="${ECI_API_KEY:-dev-eci-api-key-change-me}"
export ECI_BASE="${ECI_BASE:-http://127.0.0.1:8081}"
export BASE="${BASE:-$ECI_BASE}"
