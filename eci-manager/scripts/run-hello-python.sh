#!/usr/bin/env bash
# 普通 ECI 任务：跑一段 Python（无 storage）。依赖已启动的 eci-manager。
# Api-Key 从 .env 加载，勿把密钥写进本脚本。
#
# 用法：
#   # 终端 A
#   cd eci-manager && source ../.tools/env.sh && ./scripts/start.sh
#   # 终端 B
#   ./scripts/run-hello-python.sh
#
# 可选环境变量：
#   BASE          默认 http://127.0.0.1:8081
#   CLOUD_IMAGE   默认个人 ACR python_mirror:3.12（VPC）
#   HELLO_NAME    默认 hello-python
#   CANCEL_ON_FAIL=1  失败时自动 cancel
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# shellcheck disable=SC1091
source scripts/load-env.sh

export BASE="${BASE:-http://127.0.0.1:8081}"
export CLOUD_IMAGE="${CLOUD_IMAGE:-crpi-urdof1biefqsi8uj-vpc.cn-hangzhou.personal.cr.aliyuncs.com/ylif4/python_mirror:3.12}"
HELLO_NAME="${HELLO_NAME:-hello-python}"
OUT_DIR="${TMPDIR:-/tmp}"
TASK_JSON="${OUT_DIR}/eci_hello_task.json"
GET_JSON="${OUT_DIR}/eci_hello_get.json"

if [[ -z "${ECI_API_KEY:-}" ]]; then
  echo "ECI_API_KEY empty: check eci-manager/.env" >&2
  exit 1
fi

need_jq() {
  if ! command -v jq >/dev/null 2>&1; then
    echo "jq required (apt install jq)" >&2
    exit 1
  fi
}
need_jq

echo "== health =="
curl -sf "$BASE/actuator/health"
echo

echo "== submit $HELLO_NAME =="
echo "CLOUD_IMAGE=$CLOUD_IMAGE"
curl -s -X POST "$BASE/api/v1/eci/tasks" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $ECI_API_KEY" \
  -d "{
    \"name\": \"${HELLO_NAME}\",
    \"image\": \"${CLOUD_IMAGE}\",
    \"cpu\": 0.5,
    \"memory\": 1.0,
    \"restartPolicy\": \"Never\",
    \"maxRuntimeSec\": 600,
    \"command\": [\"python3\", \"-c\"],
    \"args\": [\"print('hello, world')\"]
  }" | tee "$TASK_JSON"
echo

if [[ "$(jq -r '.success' "$TASK_JSON")" != "true" ]]; then
  echo "submit failed" >&2
  jq . "$TASK_JSON" >&2 || true
  exit 1
fi

export TASK_ID
export INST_ID
TASK_ID=$(jq -r '.data.id' "$TASK_JSON")
INST_ID=$(jq -r '.data.instanceId' "$TASK_JSON")
echo "TASK_ID=$TASK_ID INST_ID=$INST_ID"

echo "== poll (5s x 60) =="
for i in $(seq 1 60); do
  curl -s "$BASE/api/v1/eci/tasks/$TASK_ID" -H "X-Api-Key: $ECI_API_KEY" | tee "$GET_JSON" >/dev/null
  TS=$(jq -r '.data.status' "$GET_JSON")
  IS=$(curl -s "$BASE/api/v1/eci/instances/$INST_ID" -H "X-Api-Key: $ECI_API_KEY" | jq -r '.data.status // empty')
  echo "try=$i task=$TS instance=$IS"
  case "$TS" in SUCCEEDED|FAILED|CANCELLED|TIMEOUT) break;; esac
  sleep 5
done

echo "== result =="
jq '{status:.data.status, failureReason:.data.failureReason, endedAt:.data.endedAt}' "$GET_JSON"

STATUS=$(jq -r '.data.status' "$GET_JSON")
if [[ "$STATUS" == "SUCCEEDED" ]]; then
  echo "PASS: status=SUCCEEDED"
  exit 0
fi

echo "FAIL: status=$STATUS" >&2
echo "== task/instance dump ==" >&2
jq '{status:.data.status, failureReason:.data.failureReason}' "$GET_JSON" >&2 || true
curl -s "$BASE/api/v1/eci/instances/$INST_ID" -H "X-Api-Key: $ECI_API_KEY" | jq . >&2 || true

if [[ "${CANCEL_ON_FAIL:-0}" == "1" ]]; then
  echo "== cancel ==" >&2
  curl -s -X POST "$BASE/api/v1/eci/tasks/$TASK_ID/cancel" -H "X-Api-Key: $ECI_API_KEY" | jq . >&2 || true
fi

echo "Tips: if ImagePull failed, try VPC mirrors, e.g." >&2
echo "  export CLOUD_IMAGE='alibaba-cloud-linux-3-registry-vpc.cn-hangzhou.cr.aliyuncs.com/alinux3/python:3.11.1'" >&2
echo "  export CLOUD_IMAGE='crpi-urdof1biefqsi8uj-vpc.cn-hangzhou.personal.cr.aliyuncs.com/ylif4/python_mirror:3.12'" >&2
exit 1
