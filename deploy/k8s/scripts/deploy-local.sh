#!/usr/bin/env bash
# 一键部署 XJICloud 到本地 K3s
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
K8S_DIR="${REPO_ROOT}/deploy/k8s"

export KUBECONFIG="${KUBECONFIG:-/etc/rancher/k3s/k3s.yaml}"

if [[ ! -f "${K8S_DIR}/secrets.yaml" ]]; then
  cp "${K8S_DIR}/secrets.example.yaml" "${K8S_DIR}/secrets.yaml"
  echo "[deploy] Created secrets.yaml from example — edit before production use."
fi

echo "[deploy] Applying manifests..."
kubectl apply -k "${K8S_DIR}"

echo "[deploy] Waiting for rollouts..."
kubectl -n xjicloud rollout status deployment/redis --timeout=120s
kubectl -n xjicloud rollout status statefulset/postgres --timeout=180s
kubectl -n xjicloud rollout status statefulset/minio --timeout=180s
kubectl -n xjicloud rollout status deployment/backend --timeout=300s
kubectl -n xjicloud rollout status deployment/frontend --timeout=120s
kubectl -n xjicloud rollout status deployment/gpu-worker --timeout=120s

echo ""
kubectl -n xjicloud get pods -o wide
echo ""
echo "Access: http://192.168.203.133/"
echo "Admin:  http://192.168.203.133/admin/"
echo "MinIO:  http://192.168.203.133:30900  (三节点集群时亦可用 135:30900)"
