#!/usr/bin/env bash
# 提交 P1102 keys 模式任务并轮询终态
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
# shellcheck disable=SC1091
source scripts/load-env.sh
export BASE="${BASE:-http://127.0.0.1:8081}"
export IMAGE="${CLOUD_IMAGE:-crpi-urdof1biefqsi8uj-vpc.cn-hangzhou.personal.cr.aliyuncs.com/ylif4/python_mirror:3.12}"
export RUN_PY="$ROOT/test-images/p1102/run_in_container.py"

RESP=$(python3 - <<'PY'
import json, os, pathlib
code = pathlib.Path(os.environ["RUN_PY"]).read_text(encoding="utf-8")
# Strip shebang for -c
if code.startswith("#!"):
    code = "\n".join(code.splitlines()[1:])
body = {
    "name": "p1102-ab-pairs",
    "image": os.environ["IMAGE"],
    "command": ["python3"],
    "args": ["-c", code],
    "maxRuntimeSec": 600,
    "storage": {
        "mode": "keys",
        "outputKey": "eci-test/p1102/answer.txt",
        "inputKeys": ["eci-test/p1102/input.txt"],
    },
}
print(json.dumps(body))
PY
)

echo "$RESP" | curl -s -X POST "$BASE/api/v1/eci/tasks" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $ECI_API_KEY" \
  -d @- | tee /tmp/p1102-submit.json | python3 -m json.tool | head -50

TASK_ID=$(python3 -c "import json; print(json.load(open('/tmp/p1102-submit.json'))['data']['id'])")
echo "TASK_ID=$TASK_ID"

for _ in $(seq 1 36); do
  S=$(curl -s -H "X-Api-Key: $ECI_API_KEY" "$BASE/api/v1/eci/tasks/$TASK_ID")
  echo "$S" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(d['status'], '|', d.get('failureReason') or '')"
  echo "$S" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; raise SystemExit(0 if d['status'] in ('SUCCEEDED','FAILED','CANCELLED','TIMEOUT') else 1)" && break
  sleep 10
done

curl -s -H "X-Api-Key: $ECI_API_KEY" "$BASE/api/v1/eci/tasks/$TASK_ID" | python3 -m json.tool | head -60
