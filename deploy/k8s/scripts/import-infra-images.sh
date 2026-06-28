#!/usr/bin/env bash
# 预拉取并导入 K3s 所需基础镜像（离线/受限网络环境）
set -euo pipefail

IMAGES=(
  rancher/mirrored-pause:3.6
  redis:7-alpine
  postgres:16-alpine
  minio/minio:latest
  minio/mc:latest
  busybox:1.36
)

for img in "${IMAGES[@]}"; do
  echo "[import] docker pull ${img}..."
  docker pull "${img}"
  echo "[import] k3s ctr import ${img}..."
  docker save "${img}" | /usr/local/bin/k3s ctr images import -
done

echo "[import] Done."
