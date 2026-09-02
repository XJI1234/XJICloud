#!/usr/bin/env bash
# POST 一笔 3DGS 标准档任务（与 3dgs-ECI-Shell手动跑通.md §6 标准质量档对齐）。
# 前置：eci-manager 已启动；OSS 上已有 3dgs/raw/<建筑名>.zip；.env 配好内网 OSS + presign=1440。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# shellcheck disable=SC1091
source scripts/load-env.sh

export BASE="${BASE:-$ECI_BASE}"
export GS_IMAGE="${GS_IMAGE:-crpi-urdof1biefqsi8uj-vpc.cn-hangzhou.personal.cr.aliyuncs.com/ylif4/3dgs:1.0.0}"
export BUILDING="${BUILDING:-新文科楼}"
export INSTANCE_TYPE="${INSTANCE_TYPE:-ecs.gn7i-c16g1.4xlarge}"
export IMAGE_SNAPSHOT_ID="${IMAGE_SNAPSHOT_ID:-imc-bp15yiuttnxf8fh9n9fy}"
export MAX_STEPS="${MAX_STEPS:-15000}"
export DATA_FACTOR="${DATA_FACTOR:-1}"
export PACKED="${PACKED:-1}"
export ANTIALIASED="${ANTIALIASED:-1}"
export MAX_LONG_EDGE="${MAX_LONG_EDGE:-2048}"
export MAX_RUNTIME_SEC="${MAX_RUNTIME_SEC:-21600}"
export ECI_MANAGER_ROOT="$ROOT"
# 与手动 Shell 标准档一致：默认不设 EXTRA_TRAIN_ARGS。限 densify 可：
#   export EXTRA_TRAIN_ARGS='--strategy.refine-stop-iter 8000'

BODY=$(python3 - <<'PY'
import base64
import json
import os
from pathlib import Path

root = Path(os.environ["ECI_MANAGER_ROOT"])
b64 = base64.b64encode((root / "scripts" / "3dgs-bootstrap.py").read_bytes()).decode("ascii")
building = os.environ["BUILDING"]

env = {
    "ROOT_DIR": "/data",
    "BUILDING": building,
    "MAX_STEPS": os.environ.get("MAX_STEPS", "15000"),
    "DATA_FACTOR": os.environ.get("DATA_FACTOR", "1"),
    "PACKED": os.environ.get("PACKED", "1"),
    "ANTIALIASED": os.environ.get("ANTIALIASED", "1"),
    "MAX_LONG_EDGE": os.environ.get("MAX_LONG_EDGE", "2048"),
    "EXPECTED_DRIVER_MAJOR": "550",
    "TORCH_FILE_SYSTEM_SHARING": "1",
}
extra = os.environ.get("EXTRA_TRAIN_ARGS", "").strip()
if extra:
    env["EXTRA_TRAIN_ARGS"] = extra

body = {
    "name": "3dgs-standard-" + building,
    "image": os.environ["GS_IMAGE"],
    "cpu": 16,
    "memory": 60,
    "instanceType": os.environ["INSTANCE_TYPE"],
    "gpu": 1,
    "restartPolicy": "Never",
    "maxRuntimeSec": int(os.environ.get("MAX_RUNTIME_SEC", "21600")),
    "imageSnapshotId": os.environ.get("IMAGE_SNAPSHOT_ID") or None,
    "command": ["/opt/3dgs/docker/entrypoint.sh"],
    "args": [
        "shell",
        "python3 -c 'import base64; exec(base64.b64decode(\"%s\").decode())'" % b64,
    ],
    "env": env,
    "storage": {
        "mode": "keys",
        "outputKey": "3dgs/exports/%s.ply" % building,
        "inputKeys": ["3dgs/raw/%s.zip" % building],
    },
}
if not body["imageSnapshotId"]:
    del body["imageSnapshotId"]
print(json.dumps(body, ensure_ascii=False))
PY
)

echo "=== POST 3DGS standard (${BUILDING}, MAX_STEPS=${MAX_STEPS}) ==="
curl -s -X POST "$BASE/api/v1/eci/tasks" \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $ECI_API_KEY" \
  -d "$BODY" | tee /tmp/eci_3dgs_standard_submit.json

python3 - <<'PY'
import json
import sys

data = json.load(open("/tmp/eci_3dgs_standard_submit.json"))
if not data.get("success"):
    print("submit failed:", data, file=sys.stderr)
    sys.exit(1)
task = data["data"]
print(f"TASK_ID={task['id']}")
print(f"INST_ID={task.get('instanceId')}")
print(f"status={task.get('status')}")
PY

TASK_ID=$(python3 -c "import json; print(json.load(open('/tmp/eci_3dgs_standard_submit.json'))['data']['id'])")

echo "=== polling (15s interval, up to ~50min) ==="
for i in $(seq 1 200); do
  curl -s "$BASE/api/v1/eci/tasks/$TASK_ID" \
    -H "X-Api-Key: $ECI_API_KEY" \
    | tee /tmp/eci_3dgs_standard_get.json >/dev/null
  python3 - <<'PY'
import json
d = json.load(open("/tmp/eci_3dgs_standard_get.json"))["data"]
print(d["status"], "|", d.get("failureReason") or "")
PY
  python3 - <<'PY'
import json, sys
d = json.load(open("/tmp/eci_3dgs_standard_get.json"))["data"]
sys.exit(0 if d["status"] in ("SUCCEEDED", "FAILED", "CANCELLED", "TIMEOUT") else 1)
PY
  if [[ $? -eq 0 ]]; then
    break
  fi
  sleep 15
done

python3 - <<'PY'
import json
d = json.load(open("/tmp/eci_3dgs_standard_get.json"))["data"]
print(json.dumps({"status": d["status"], "failureReason": d.get("failureReason")}, ensure_ascii=False, indent=2))
PY
