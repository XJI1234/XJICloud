#!/bin/bash
set -euo pipefail

echo "[entrypoint] XJICloud GPU Worker starting..."
echo "[entrypoint] BACKEND=${XJICLOUD_BACKEND_URL:-http://127.0.0.1:8080}"

cd /opt/xjicloud-worker
exec python3 worker_agent.py
