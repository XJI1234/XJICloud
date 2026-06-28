#!/usr/bin/env bash
# 构建 gpu-worker 并推送到阿里云 ACR
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
ACR_REGISTRY="${ACR_REGISTRY:?Set ACR_REGISTRY e.g. registry.cn-hangzhou.aliyuncs.com}"
ACR_NAMESPACE="${ACR_NAMESPACE:?Set ACR_NAMESPACE}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
IMAGE="${ACR_REGISTRY}/${ACR_NAMESPACE}/xjicloud-gpu-worker:${IMAGE_TAG}"

cd "${REPO_ROOT}"

echo "[acr] Building gpu-worker..."
docker build -t xjicloud/gpu-worker:latest gpu-worker/

echo "[acr] Tagging ${IMAGE}..."
docker tag xjicloud/gpu-worker:latest "${IMAGE}"

echo "[acr] Pushing (requires docker login ${ACR_REGISTRY})..."
docker push "${IMAGE}"

echo "[acr] Pushed: ${IMAGE}"
echo ""
echo "CCI environment variables:"
echo "  XJICLOUD_BACKEND_URL=http://<backend-internal>:8080"
echo "  WORKER_SECRET=<same as backend xjicloud.worker.shared-secret>"
echo "  WORKER_NAME=cci-worker-01"
