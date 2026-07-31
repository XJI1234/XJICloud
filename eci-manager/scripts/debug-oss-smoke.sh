#!/usr/bin/env bash
# OSS/ECI 联调诊断：exit0 → storage 冒烟；依赖已启动的 eci-manager + .env
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# shellcheck disable=SC1091
source scripts/load-env.sh
export BASE="${BASE:-http://127.0.0.1:8081}"
export IMAGE="${CLOUD_IMAGE:-crpi-urdof1biefqsi8uj-vpc.cn-hangzhou.personal.cr.aliyuncs.com/ylif4/python_mirror:3.12}"

echo "== health =="
curl -sf "$BASE/actuator/health"
echo

echo "== 1) py-exit0 (no storage) =="
RESP0=$(curl -s -X POST "$BASE/api/v1/eci/tasks" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $ECI_API_KEY" \
  -d "{\"name\":\"py-exit0\",\"image\":\"$IMAGE\",\"command\":[\"python3\"],\"args\":[\"-c\",\"print(1)\"],\"maxRuntimeSec\":180}")
echo "$RESP0" | python3 -m json.tool | head -30
TID0=$(echo "$RESP0" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
for _ in $(seq 1 24); do
  S=$(curl -s -H "X-Api-Key: $ECI_API_KEY" "$BASE/api/v1/eci/tasks/$TID0")
  ST=$(echo "$S" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(d['status'], d.get('failureReason') or '')")
  echo "  poll $ST"
  echo "$S" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; raise SystemExit(0 if d['status'] in ('SUCCEEDED','FAILED','CANCELLED','TIMEOUT') else 1)" && break
  sleep 10
done

echo "== 2) oss-smoke-urllib (storage=test) =="
export PYCODE='import os,urllib.request; url=os.environ["XJI_OUTPUT_UPLOAD_URL"]; req=urllib.request.Request(url,data=b"eci-oss-smoke urllib ok\n",method="PUT",headers={"Content-Type":"text/plain"}); urllib.request.urlopen(req,timeout=120); print("ok",os.environ.get("XJI_OUTPUT_OSS_KEY"))'
RESP1=$(curl -s -X POST "$BASE/api/v1/eci/tasks" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $ECI_API_KEY" \
  -d "$(python3 - <<'PY'
import json, os
body = {
  "name": "oss-smoke-urllib",
  "image": os.environ["IMAGE"],
  "command": ["python3"],
  "args": ["-c", os.environ["PYCODE"]],
  "maxRuntimeSec": 600,
  "storage": {"mode": "test"},
}
print(json.dumps(body))
PY
)")
echo "$RESP1" | python3 -m json.tool | head -40
TID1=$(echo "$RESP1" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "TASK_ID=$TID1  expect OSS key eci-test/$TID1/output.txt"
for _ in $(seq 1 30); do
  S=$(curl -s -H "X-Api-Key: $ECI_API_KEY" "$BASE/api/v1/eci/tasks/$TID1")
  ST=$(echo "$S" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(d['status'], '|', d.get('failureReason') or '')")
  echo "  poll $ST"
  echo "$S" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; raise SystemExit(0 if d['status'] in ('SUCCEEDED','FAILED','CANCELLED','TIMEOUT') else 1)" && break
  sleep 10
done
echo "== done =="
