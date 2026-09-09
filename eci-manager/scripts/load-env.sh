#!/usr/bin/env bash
# 加载 eci-manager/.env（若存在），并映射阿里云凭证到 ECI_ACCESS_KEY_* / ALIBABA_CLOUD_*。
# 无 .env 时使用 Mock 默认值，便于 curl 联调。
# ALIBABA_CLOUD_* 仅在 ECI_ACCESS_KEY_* 为空且无 SECURITY_TOKEN 时映射为 STATIC_AK 种子。
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
  export ALIBABA_CLOUD_ACCESS_KEY_ID="${ECI_ALIYUN_ACCESS_KEY_ID:-}"
fi
if [[ -n "${ECI_ALIYUN_ACCESS_KEY_SECRET:-}" && -z "${ALIBABA_CLOUD_ACCESS_KEY_SECRET:-}" ]]; then
  export ALIBABA_CLOUD_ACCESS_KEY_SECRET="${ECI_ALIYUN_ACCESS_KEY_SECRET:-}"
fi
if [[ -n "${ECI_ALIYUN_SECURITY_TOKEN:-}" && -z "${ALIBABA_CLOUD_SECURITY_TOKEN:-}" ]]; then
  export ALIBABA_CLOUD_SECURITY_TOKEN="${ECI_ALIYUN_SECURITY_TOKEN:-}"
fi

# 旧 .env 的 ALIBABA_CLOUD_* → ECI_ACCESS_KEY_*（STATIC_AK 种子）。STS token 存在则不映射。
if [ -z "${ALIBABA_CLOUD_SECURITY_TOKEN:-}" ]; then
  if [ -z "${ECI_ACCESS_KEY_ID:-}" ] && [ -n "${ALIBABA_CLOUD_ACCESS_KEY_ID:-}" ]; then
    export ECI_ACCESS_KEY_ID="${ALIBABA_CLOUD_ACCESS_KEY_ID:-}"
  fi
  if [ -z "${ECI_ACCESS_KEY_SECRET:-}" ] && [ -n "${ALIBABA_CLOUD_ACCESS_KEY_SECRET:-}" ]; then
    export ECI_ACCESS_KEY_SECRET="${ALIBABA_CLOUD_ACCESS_KEY_SECRET:-}"
  fi
fi

export ECI_API_KEY="${ECI_API_KEY:-dev-eci-api-key-change-me}"
export ECI_BASE="${ECI_BASE:-http://127.0.0.1:8081}"
export BASE="${BASE:-$ECI_BASE}"
