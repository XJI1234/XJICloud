#!/bin/bash
# 全栈部署入口：连接 backend 轮询任务。独立测试（目录挂载发图收 PLY）请用 Dockerfile.test。
set -euo pipefail

echo "[entrypoint] XJICloud GPU Worker starting..."
echo "[entrypoint] BACKEND=${XJICLOUD_BACKEND_URL:-http://127.0.0.1:8080}"

if colmap -h >/dev/null 2>&1; then
    echo "[entrypoint] COLMAP CLI available"
else
    echo "[entrypoint] WARN: colmap not found"
fi

cd /opt/xjicloud-worker
exec python3 worker_agent.py
