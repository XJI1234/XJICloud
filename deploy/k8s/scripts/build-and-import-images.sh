#!/usr/bin/env bash
# 构建镜像并导入 K3s containerd（在 133 本机执行）
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "${REPO_ROOT}"

echo "[build] backend..."
docker build -t xjicloud/backend:local backend/

echo "[build] gpu-worker..."
docker build -t xjicloud/gpu-worker:local gpu-worker/

echo "[build] frontend (may take several minutes)..."
docker build -f deploy/k8s/frontend/Dockerfile -t xjicloud/frontend:local .

echo "[import] Loading images into K3s..."
docker save xjicloud/backend:local | /usr/local/bin/k3s ctr images import -
docker save xjicloud/gpu-worker:local | /usr/local/bin/k3s ctr images import -
docker save xjicloud/frontend:local | /usr/local/bin/k3s ctr images import -

echo "[build] Done. Images:"
/usr/local/bin/k3s ctr images ls | grep xjicloud || true
