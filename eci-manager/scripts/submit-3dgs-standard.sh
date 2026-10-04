#!/usr/bin/env bash
# POST 一笔 3DGS 标准档任务（步骤见 docs/3dgs-ECI使用说明.md）。
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
export IMAGE_SNAPSHOT_ID="${IMAGE_SNAPSHOT_ID:-imc-bp18oks2orrt3unoa173}"
export MAX_STEPS="${MAX_STEPS:-2000}"
export DATA_FACTOR="${DATA_FACTOR:-1}"
export PACKED="${PACKED:-1}"
export ANTIALIASED="${ANTIALIASED:-1}"
export MAX_LONG_EDGE="${MAX_LONG_EDGE:-2048}"
export MAX_RUNTIME_SEC="${MAX_RUNTIME_SEC:-21600}"
export SHM_SIZE="${SHM_SIZE:-8Gi}"
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
    "shmSize": os.environ.get("SHM_SIZE", "8Gi"),
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

POLL_MAX="${POLL_MAX:-1200}"   # 默认 1200×15s≈5h，够标准档 15k；短测可 POLL_MAX=200
POLL_SLEEP_SEC="${POLL_SLEEP_SEC:-15}"
echo "=== polling (${POLL_SLEEP_SEC}s interval, up to $((POLL_MAX * POLL_SLEEP_SEC / 60))min) ==="
# 注意：set -e 下不可用「python 非 0 退出」判断未终态，否则首轮 STARTING 就会整脚本退出
for i in $(seq 1 "$POLL_MAX"); do
  curl -s "$BASE/api/v1/eci/tasks/$TASK_ID" \
    -H "X-Api-Key: $ECI_API_KEY" \
    | tee /tmp/eci_3dgs_standard_get.json >/dev/null
  terminal=0
  python3 - <<'PY'
import json
d = json.load(open("/tmp/eci_3dgs_standard_get.json"))["data"]
tail = d.get("containerLogTail") or ""
print(d["status"], "|", d.get("failureReason") or "-", "| logTailChars=", len(tail))
open("/tmp/eci_3dgs_standard_terminal.flag", "w").write(
    "1" if d["status"] in ("SUCCEEDED", "FAILED", "CANCELLED") else "0"
)
PY
  if [[ "$(cat /tmp/eci_3dgs_standard_terminal.flag)" == "1" ]]; then
    break
  fi
  sleep "$POLL_SLEEP_SEC"
done

python3 - <<'PY'
import json
d = json.load(open("/tmp/eci_3dgs_standard_get.json"))["data"]
tail = d.get("containerLogTail") or ""
print(json.dumps({
    "status": d["status"],
    "failureReason": d.get("failureReason"),
    "containerLogTailLen": len(tail),
    "containerLogTailPreview": tail[-1500:] if tail else None,
}, ensure_ascii=False, indent=2))
PY
